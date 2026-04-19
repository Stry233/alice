#include "core/realsense/RealSenseManager.h"

#include <librealsense2/rs.hpp>
#include <QElapsedTimer>
#include <cmath>
#include <algorithm>
#include <numeric>

namespace alice {

// Turbo colormap LUT (Google AI, perceptually-uniform)
static const uint8_t kTurboR[] = {48,49,50,51,52,54,55,56,57,59,60,61,63,64,66,67,69,70,72,74,75,77,79,80,82,84,86,88,90,92,93,95,97,99,101,103,105,107,109,111,113,115,117,119,121,123,125,128,130,132,134,136,138,140,142,144,146,148,150,152,154,156,158,160,162,164,166,168,170,172,174,176,177,179,181,183,185,187,189,190,192,194,196,198,199,201,203,204,206,208,209,211,213,214,216,217,219,220,222,223,225,226,228,229,231,232,233,235,236,237,239,240,241,242,244,245,246,247,248,249,250,251,252,253,254,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,254,253,252,251,250,249,248,247,246,245,243,242,241,239,238,236,235,233,232,230,228,227,225,223,221,219,218,216,214,212,210,208,206,204,202,199,197,195,193,191,189,186,184,182,179,177,175,172,170,168,165,163,160,158,155,153,150,148,145,143,140,138,135,133,130,128,125,122,120,117,115,112,110,107,105,102,100,97,95,92,90,87,85,82,80,78,75,73,71,68,66,64,62,60,58,55,53,51,50,48,46,44,42,41,39,38,36,35,33,32,31,30};
static const uint8_t kTurboG[] = {18,18,19,19,20,20,21,22,22,23,24,25,25,26,27,28,29,30,31,32,33,34,36,37,38,39,41,42,44,45,47,48,50,51,53,55,56,58,60,61,63,65,67,69,70,72,74,76,78,80,82,84,86,88,90,92,93,95,97,99,101,103,105,107,109,111,113,115,117,119,121,123,125,126,128,130,132,134,136,137,139,141,143,145,146,148,150,151,153,155,156,158,160,161,163,164,166,167,169,170,172,173,175,176,177,179,180,181,183,184,185,186,188,189,190,191,192,193,194,195,196,197,198,199,200,200,201,202,203,203,204,205,205,206,207,207,208,208,209,209,210,210,211,211,211,212,212,212,212,213,213,213,213,213,213,213,213,214,214,214,213,213,213,213,213,213,213,212,212,212,212,211,211,210,210,209,209,208,208,207,206,206,205,204,203,203,202,201,200,199,198,197,196,195,194,193,192,191,189,188,187,186,185,183,182,181,179,178,177,175,174,172,171,170,168,167,165,164,162,161,159,158,156,155,153,151,150,148,147,145,143,142,140,138,137,135,133,131,130,128,126,125,123,121,119,118,116,114,112,111,109,107,106,104,102,101};
static const uint8_t kTurboB[] = {178,179,181,183,184,186,188,190,191,193,195,196,198,200,201,203,204,206,207,209,210,211,213,214,215,216,217,218,219,220,221,222,223,224,224,225,225,226,226,227,227,227,228,228,228,228,228,228,228,228,228,228,228,227,227,227,226,226,225,225,224,224,223,222,222,221,220,219,218,217,216,215,214,213,212,211,210,209,207,206,205,203,202,200,199,197,196,194,193,191,189,188,186,184,183,181,179,177,175,174,172,170,168,166,164,162,160,158,156,154,152,150,148,146,144,142,140,138,136,134,132,130,128,126,124,122,119,117,115,113,111,109,107,105,103,101,99,97,95,94,92,90,88,86,84,83,81,79,78,76,74,73,71,69,68,66,65,63,62,60,59,58,56,55,54,53,51,50,49,48,47,46,45,44,43,42,41,40,39,38,38,37,36,35,35,34,33,33,32,32,31,31,30,30,30,29,29,29,28,28,28,28,28,27,27,27,27,27,27,27,27,27,27,28,28,28,28,28,29,29,29,29,30,30,31,31,31,32,32,33,33,34,34,35,36,36,37,38,38,39,40,41,41,42,43,44,45,46,47,47,48,49,50,51,52,53};

struct RealSenseManager::Impl {
    rs2::pipeline pipeline;
    rs2::config config;
    bool pipelineStarted = false;
};

RealSenseManager::RealSenseManager(QObject *parent)
    : QObject(parent)
    , impl_(std::make_shared<Impl>())
{
    frameTimeoutTimer_.setInterval(1000);
    connect(&frameTimeoutTimer_, &QTimer::timeout,
            this, &RealSenseManager::checkFrameTimeout);
}

RealSenseManager::~RealSenseManager() {
    stop();
}

void RealSenseManager::setMeasurementPosition(float x, float y) {
    QMutexLocker lock(&positionMutex_);
    measureX_ = std::clamp(x, 0.0f, 1.0f);
    measureY_ = std::clamp(y, 0.0f, 1.0f);
}

void RealSenseManager::getMeasurementPosition(float &x, float &y) const {
    QMutexLocker lock(&positionMutex_);
    x = measureX_;
    y = measureY_;
}

void RealSenseManager::start() {
    // If already running, stop first
    if (running_) {
        stop();
    }

    // Detach any leftover thread from a previous run
    if (captureThread_.joinable()) {
        captureThread_.detach();
    }

    // Each start gets a fresh pipeline (old one may be held by a detached thread)
    impl_ = std::make_shared<Impl>();

    running_ = true;
    lastFrameTimeMs_ = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    // Capture impl by value (shared_ptr) so detached threads keep the pipeline alive
    auto implCopy = impl_;
    captureThread_ = std::thread([this, implCopy]() { captureLoop(); });
    frameTimeoutTimer_.start();
}

void RealSenseManager::stop() {
    frameTimeoutTimer_.stop();
    running_ = false;

    // Detach the capture thread — never block the main thread waiting for it.
    // The thread holds a shared_ptr to its own Impl, so the pipeline stays
    // alive until the thread exits and releases it naturally.
    if (captureThread_.joinable()) {
        captureThread_.detach();
    }

    if (connected_) {
        connected_ = false;
        emit connectionChanged(false);
    }
}

QVariantList RealSenseManager::availableDepthModes() const {
    QVariantList modes;
    try {
        rs2::context ctx;
        auto devices = ctx.query_devices();
        if (devices.size() == 0) return modes;
        auto dev = devices[0];
        for (auto &sensor : dev.query_sensors()) {
            for (auto &profile : sensor.get_stream_profiles()) {
                if (profile.stream_type() != RS2_STREAM_DEPTH) continue;
                if (profile.format() != RS2_FORMAT_Z16) continue; // Only Z16 depth
                auto vp = profile.as<rs2::video_stream_profile>();

                // Validate: can this config actually resolve on this device?
                rs2::config testCfg;
                testCfg.enable_stream(RS2_STREAM_DEPTH, vp.width(), vp.height(), RS2_FORMAT_Z16, vp.fps());
                rs2::pipeline testPipe(ctx);
                bool valid = false;
                try { valid = testCfg.can_resolve(testPipe); } catch (...) {}
                if (!valid) continue;

                QVariantMap m;
                m["width"] = vp.width();
                m["height"] = vp.height();
                m["fps"] = vp.fps();
                m["label"] = QString("%1x%2 @ %3fps").arg(vp.width()).arg(vp.height()).arg(vp.fps());
                bool dup = false;
                for (const auto &existing : modes) {
                    auto em = existing.toMap();
                    if (em["width"] == m["width"] && em["height"] == m["height"] && em["fps"] == m["fps"]) {
                        dup = true; break;
                    }
                }
                if (!dup) modes.append(m);
            }
        }
    } catch (...) {}
    return modes;
}

QVariantList RealSenseManager::availableColorModes() const {
    QVariantList modes;
    try {
        rs2::context ctx;
        auto devices = ctx.query_devices();
        if (devices.size() == 0) return modes;
        auto dev = devices[0];
        for (auto &sensor : dev.query_sensors()) {
            for (auto &profile : sensor.get_stream_profiles()) {
                if (profile.stream_type() != RS2_STREAM_COLOR) continue;
                if (profile.format() != RS2_FORMAT_RGB8) continue; // Only RGB8 color
                auto vp = profile.as<rs2::video_stream_profile>();

                rs2::config testCfg;
                testCfg.enable_stream(RS2_STREAM_COLOR, vp.width(), vp.height(), RS2_FORMAT_RGB8, vp.fps());
                rs2::pipeline testPipe(ctx);
                bool valid = false;
                try { valid = testCfg.can_resolve(testPipe); } catch (...) {}
                if (!valid) continue;

                QVariantMap m;
                m["width"] = vp.width();
                m["height"] = vp.height();
                m["fps"] = vp.fps();
                m["label"] = QString("%1x%2 @ %3fps").arg(vp.width()).arg(vp.height()).arg(vp.fps());
                bool dup = false;
                for (const auto &existing : modes) {
                    auto em = existing.toMap();
                    if (em["width"] == m["width"] && em["height"] == m["height"] && em["fps"] == m["fps"]) {
                        dup = true; break;
                    }
                }
                if (!dup) modes.append(m);
            }
        }
    } catch (...) {}
    return modes;
}

void RealSenseManager::setStreamConfig(int dw, int dh, int df, int cw, int ch, int cf) {
    depthWidth_ = dw; depthHeight_ = dh; depthFps_ = df;
    colorWidth_ = cw; colorHeight_ = ch; colorFps_ = cf;
    // Hot-apply: restart if running
    if (connected_ || running_) {
        stop();
        start();
    }
}

void RealSenseManager::captureLoop() {
    try {
        impl_->config.enable_stream(RS2_STREAM_DEPTH, depthWidth_, depthHeight_, RS2_FORMAT_Z16, depthFps_);

        try {
            impl_->config.enable_stream(RS2_STREAM_COLOR, colorWidth_, colorHeight_, RS2_FORMAT_RGB8, colorFps_);
        } catch (...) {}

        impl_->pipeline.start(impl_->config);
        impl_->pipelineStarted = true;
        connected_ = true;
        lastInitFailed_ = false;
        emit connectionChanged(true);

    } catch (const rs2::error &e) {
        // Only emit the error the first time init fails (avoid log spam
        // when the health check keeps retrying with no device connected)
        if (!lastInitFailed_) {
            lastInitFailed_ = true;
            emit error(QString("RealSense init failed: %1").arg(e.what()));
        }
        running_ = false;
        return;
    }

    while (running_) {
        try {
            rs2::frameset frames = impl_->pipeline.wait_for_frames(1000);
            if (!running_) break;

            // Stamp for main-thread timeout watchdog
            lastFrameTimeMs_ = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();

            auto depthFrame = frames.get_depth_frame();
            if (depthFrame) {
                int w = depthFrame.get_width();
                int h = depthFrame.get_height();
                auto data = reinterpret_cast<const uint16_t *>(depthFrame.get_data());

                float depth = calculateDepth(data, w, h);
                if (depth > 0.0f) {
                    depth_ = depth;
                    confidence_ = kalmanFilter_.getConfidence();
                    emit depthChanged(depth, confidence_);
                }

                QImage depthImage = colorizeDepth(data, w, h);
                emit depthFrameReady(depthImage);
            }

            try {
                auto colorFrame = frames.get_color_frame();
                if (colorFrame) {
                    int w = colorFrame.get_width();
                    int h = colorFrame.get_height();
                    auto data = reinterpret_cast<const uint8_t *>(colorFrame.get_data());
                    QImage img(data, w, h, w * 3, QImage::Format_RGB888);
                    emit colorFrameReady(img.copy());
                }
            } catch (...) {}

        } catch (const rs2::error &e) {
            if (!running_) break;
            QString what = QString::fromLatin1(e.what());
            emit error(QString("RealSense device lost: %1").arg(what));
            break;
        }
    }

    // Clean up pipeline on thread exit
    if (impl_->pipelineStarted) {
        try { impl_->pipeline.stop(); } catch (...) {}
        impl_->pipelineStarted = false;
    }

    // Signal disconnection and mark not running so health check can restart
    running_ = false;
    if (connected_) {
        connected_ = false;
        emit connectionChanged(false);
    }
}

void RealSenseManager::checkFrameTimeout() {
    if (!connected_) return;
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                   std::chrono::steady_clock::now().time_since_epoch()).count();
    if (now - lastFrameTimeMs_ > kFrameTimeoutMs) {
        // Signal disconnect immediately — no blocking calls here
        frameTimeoutTimer_.stop();
        running_ = false;  // Tells capture thread to exit
        connected_ = false;
        emit error("RealSense frame timeout — device may be disconnected");
        emit connectionChanged(false);
        // The capture thread holds a shared_ptr to impl_ and will clean up
        // its own pipeline when it eventually unblocks and exits.
    }
}

float RealSenseManager::calculateDepth(const uint16_t *depthData, int width, int height) {
    float mx, my;
    {
        QMutexLocker lock(&positionMutex_);
        mx = measureX_;
        my = measureY_;
    }

    int centerX = static_cast<int>(mx * width);
    int centerY = static_cast<int>(my * height);
    centerX = std::clamp(centerX, roiSize_, width - roiSize_ - 1);
    centerY = std::clamp(centerY, roiSize_, height - roiSize_ - 1);

    float filteredDepthMm = bilateralFilter_.filter(
        depthData, width, height, centerX, centerY, 2);

    if (filteredDepthMm < kMinValidDepth || filteredDepthMm > kMaxValidDepth) {
        return 0.0f;
    }

    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                   std::chrono::steady_clock::now().time_since_epoch()).count();
    float kalmanDepthMm = kalmanFilter_.update(filteredDepthMm, now);

    return kalmanDepthMm / 1000.0f;
}

QImage RealSenseManager::colorizeDepth(const uint16_t *depthData, int width, int height) {
    QImage img(width, height, QImage::Format_RGB888);

    for (int y = 0; y < height; ++y) {
        auto *line = img.scanLine(y);
        for (int x = 0; x < width; ++x) {
            uint16_t d = depthData[y * width + x];
            if (d == 0) {
                line[x * 3 + 0] = 20;
                line[x * 3 + 1] = 20;
                line[x * 3 + 2] = 20;
                continue;
            }
            // Map 200–5000mm to 0–255 LUT index
            float normalized = std::clamp(
                static_cast<float>(d - kMinValidDepth) / (5000.0f - kMinValidDepth),
                0.0f, 1.0f);
            int idx = static_cast<int>(normalized * 255);
            line[x * 3 + 0] = kTurboR[idx];
            line[x * 3 + 1] = kTurboG[idx];
            line[x * 3 + 2] = kTurboB[idx];
        }
    }
    return img;
}

} // namespace alice
