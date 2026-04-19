#include "core/realsense/RealSenseManager.h"

#include <librealsense2/rs.hpp>
#include <QDateTime>
#include <QElapsedTimer>
#include <array>
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

void RealSenseManager::setMinValidDepth(int mm) {
    QMutexLocker lock(&estimatorParamsMutex_);
    minValidDepth_ = std::max(100, mm);
    estimator_.setValidRange(minValidDepth_, maxValidDepth_);
}

void RealSenseManager::setMaxValidDepth(int mm) {
    QMutexLocker lock(&estimatorParamsMutex_);
    maxValidDepth_ = std::max(minValidDepth_ + 100, mm);
    estimator_.setValidRange(minValidDepth_, maxValidDepth_);
}

void RealSenseManager::setDepthSmoothing(float alpha) {
    QMutexLocker lock(&estimatorParamsMutex_);
    estimator_.setSmoothing(alpha);
}

void RealSenseManager::setMeasurementPosition(float x, float y) {
    const float cx = std::clamp(x, 0.0f, 1.0f);
    const float cy = std::clamp(y, 0.0f, 1.0f);
    {
        QMutexLocker lock(&positionMutex_);
        measureX_ = cx;
        measureY_ = cy;
    }
    measureXCached_.store(cx, std::memory_order_relaxed);
    measureYCached_.store(cy, std::memory_order_relaxed);
    // Sub-2% moves don't trip the estimator's auto-reset, so a slow
    // drag would otherwise EMA-blend old-position depth into the new.
    QMutexLocker lock(&estimatorParamsMutex_);
    estimator_.reset();
}

void RealSenseManager::setTrackedPosition(float x, float y) {
    const float cx = std::clamp(x, 0.0f, 1.0f);
    const float cy = std::clamp(y, 0.0f, 1.0f);
    {
        QMutexLocker lock(&positionMutex_);
        measureX_ = cx;
        measureY_ = cy;
    }
    measureXCached_.store(cx, std::memory_order_relaxed);
    measureYCached_.store(cy, std::memory_order_relaxed);
}

void RealSenseManager::jumpToPosition(float x, float y) {
    setMeasurementPosition(x, y);
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

    if (connected_.exchange(false)) {
        lastDisconnectMs_ = QDateTime::currentMSecsSinceEpoch();
        connectedSinceMs_ = 0;
        {
            QMutexLocker lock(&deviceInfoMutex_);
            activeSerial_.clear();
        }
        emit connectionChanged(false);
        emit availableDevicesChanged();
    }
}

QVariantList RealSenseManager::availableDevices() const {
    QVariantList out;
    QString activeCopy;
    {
        QMutexLocker lock(&deviceInfoMutex_);
        activeCopy = activeSerial_;
    }
    try {
        rs2::context ctx;
        auto devs = ctx.query_devices();
        for (uint32_t i = 0; i < devs.size(); ++i) {
            const auto &d = devs[i];
            if (!d.supports(RS2_CAMERA_INFO_SERIAL_NUMBER)) continue;
            const QString serial = QString::fromUtf8(d.get_info(RS2_CAMERA_INFO_SERIAL_NUMBER));
            QString name = "Intel RealSense";
            if (d.supports(RS2_CAMERA_INFO_NAME))
                name = QString::fromUtf8(d.get_info(RS2_CAMERA_INFO_NAME));
            QVariantMap m;
            m["id"]     = serial;
            m["name"]   = QString("%1 (%2)").arg(name, serial);
            m["active"] = connected_.load() && serial == activeCopy;
            out.append(m);
        }
    } catch (...) {
        // librealsense throws on context init for missing udev permissions
        // or detached USB. Return whatever we've enumerated so far —
        // typically an empty list, which the UI renders as "no selector
        // shown" rather than a broken dropdown.
    }
    return out;
}

void RealSenseManager::selectDevice(const QString &serial) {
    if (preferredSerial_ == serial) return;
    preferredSerial_ = serial;

    // Restart the pipeline so the new enable_device binding takes effect.
    // Only restart if we'd actually switch — empty serial on an unconnected
    // manager is a no-op.
    if (!serial.isEmpty() && activeSerial_ != serial && running_) {
        stop();
        start();
    }
    emit availableDevicesChanged();
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

// MEDIUM_DENSITY balances match density vs. accuracy — HIGH_ACCURACY
// starves the matcher of pixels past ~4 m; HIGH_DENSITY fabricates
// questionable matches that throw autofocus. The downstream pipeline
// (ROI median + confidence-weighted EMA) absorbs the mild extra noise.
static void applyDensityPreset(rs2::device &dev) {
    try {
        for (auto &sensor : dev.query_sensors()) {
            if (!sensor.is<rs2::depth_sensor>()) continue;
            auto ds = sensor.as<rs2::depth_sensor>();
            if (!ds.supports(RS2_OPTION_VISUAL_PRESET)) continue;
            ds.set_option(RS2_OPTION_VISUAL_PRESET,
                          RS2_RS400_VISUAL_PRESET_MEDIUM_DENSITY);
        }
    } catch (...) {}
}

// Try to set DEPTH_UNITS to 0.1 mm per Z16 unit. Trades max encodable
// depth (~6.55 m at 0.1 mm vs 65.5 m at 1 mm — fine for Alice's range)
// for sub-mm precision. Returns the scale the sensor actually reports
// so the estimator can trust it regardless of whether set_option took.
static float applySubMillimetrePrecision(rs2::device &dev) {
    float resultScale = 0.001f;
    try {
        for (auto &sensor : dev.query_sensors()) {
            if (!sensor.is<rs2::depth_sensor>()) continue;
            auto ds = sensor.as<rs2::depth_sensor>();
            if (ds.supports(RS2_OPTION_DEPTH_UNITS)) {
                try { ds.set_option(RS2_OPTION_DEPTH_UNITS, 0.0001f); }
                catch (...) {}
            }
            resultScale = ds.get_depth_scale();
        }
    } catch (...) {}
    return resultScale;
}

void RealSenseManager::captureLoop() {
    try {
        // Bind to preferred serial when present. Auto-pick fallback if
        // the preferred camera is unplugged so the app still comes up.
        if (!preferredSerial_.isEmpty()) {
            try {
                rs2::context ctx;
                auto devs = ctx.query_devices();
                for (uint32_t i = 0; i < devs.size(); ++i) {
                    const auto &d = devs[i];
                    if (d.supports(RS2_CAMERA_INFO_SERIAL_NUMBER)) {
                        const QString s = QString::fromUtf8(
                            d.get_info(RS2_CAMERA_INFO_SERIAL_NUMBER));
                        if (s == preferredSerial_) {
                            impl_->config.enable_device(preferredSerial_.toStdString());
                            break;
                        }
                    }
                }
            } catch (...) {}
        }

        impl_->config.enable_stream(RS2_STREAM_DEPTH, depthWidth_, depthHeight_, RS2_FORMAT_Z16, depthFps_);

        try {
            impl_->config.enable_stream(RS2_STREAM_COLOR, colorWidth_, colorHeight_, RS2_FORMAT_RGB8, colorFps_);
        } catch (...) {}

        auto profile = impl_->pipeline.start(impl_->config);
        impl_->pipelineStarted = true;
        connected_ = true;
        connectedSinceMs_ = QDateTime::currentMSecsSinceEpoch();
        lastInitFailed_ = false;

        // Sensor options have to be applied after pipeline.start() —
        // the device isn't live until then.
        try {
            auto dev0 = profile.get_device();
            applyDensityPreset(dev0);
            const float scale = applySubMillimetrePrecision(dev0);
            depthScaleM_.store(scale, std::memory_order_relaxed);
            {
                QMutexLocker lock(&estimatorParamsMutex_);
                estimator_.setDepthScale(scale);
            }
        } catch (...) {}

        try {
            auto dev = profile.get_device();
            QString name;
            if (dev.supports(RS2_CAMERA_INFO_NAME))
                name = QString::fromUtf8(dev.get_info(RS2_CAMERA_INFO_NAME));
            QString bus;
            if (dev.supports(RS2_CAMERA_INFO_USB_TYPE_DESCRIPTOR))
                bus = QString("USB %1").arg(QString::fromUtf8(
                    dev.get_info(RS2_CAMERA_INFO_USB_TYPE_DESCRIPTOR)));
            QString serial;
            if (dev.supports(RS2_CAMERA_INFO_SERIAL_NUMBER))
                serial = QString::fromUtf8(dev.get_info(RS2_CAMERA_INFO_SERIAL_NUMBER));
            {
                QMutexLocker lock(&deviceInfoMutex_);
                if (!name.isEmpty()) deviceName_ = name;
                if (!bus.isEmpty()) deviceBus_ = bus;
                activeSerial_ = serial;
            }
        } catch (...) {}

        emit connectionChanged(true);
        emit availableDevicesChanged();

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

    // Align depth to color so crosshair on RGB maps to correct depth pixel
    rs2::align alignToColor(RS2_STREAM_COLOR);

    // librealsense post-processing chain. Filters run in disparity
    // space (noise is uniform there, not in metric depth), hence the
    // depth→disparity → filter → disparity→depth sandwich.
    //
    // Temporal alpha = 1.0 disables per-pixel IIR smoothing: IIR mixes
    // prior frames into every pixel and makes scene transitions ramp
    // over 3-5 frames, defeating the discontinuity gate downstream.
    // HOLES_FILL stays enabled — that uses per-pixel history only
    // where the current frame has no reading.
    rs2::disparity_transform depth_to_disparity(true);
    rs2::disparity_transform disparity_to_depth(false);
    rs2::spatial_filter spatial;
    rs2::temporal_filter temporal;
    try {
        spatial.set_option(RS2_OPTION_FILTER_MAGNITUDE, 2);
        spatial.set_option(RS2_OPTION_FILTER_SMOOTH_ALPHA, 0.25f);
        spatial.set_option(RS2_OPTION_FILTER_SMOOTH_DELTA, 20);
        temporal.set_option(RS2_OPTION_FILTER_SMOOTH_ALPHA, 1.0f);
        temporal.set_option(RS2_OPTION_FILTER_SMOOTH_DELTA, 20);
        temporal.set_option(RS2_OPTION_HOLES_FILL, 3);
    } catch (...) {}

    // Frame throttling: skip frames if processing can't keep up
    int frameCount = 0;

    while (running_) {
        try {
            rs2::frameset frames = impl_->pipeline.wait_for_frames(1000);
            if (!running_) break;
            frameCount++;

            // Stamp for main-thread timeout watchdog
            lastFrameTimeMs_ = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();

            // Align before filtering so we filter once on the frame
            // we're about to sample, not on unused colour data.
            try { frames = alignToColor.process(frames); } catch (...) {}

            auto depthFrame = frames.get_depth_frame();
            if (depthFrame) {
                try {
                    rs2::frame f = depthFrame;
                    f = depth_to_disparity.process(f);
                    f = spatial.process(f);
                    f = temporal.process(f);
                    f = disparity_to_depth.process(f);
                    depthFrame = f.as<rs2::depth_frame>();
                } catch (...) {}
                int w = depthFrame.get_width();
                int h = depthFrame.get_height();
                auto data = reinterpret_cast<const uint16_t *>(depthFrame.get_data());

                float mx, my;
                {
                    QMutexLocker lock(&positionMutex_);
                    mx = measureX_;
                    my = measureY_;
                }
                DepthEstimator::Reading reading;
                {
                    QMutexLocker lock(&estimatorParamsMutex_);
                    reading = estimator_.process(data, w, h, mx, my);
                }
                if (reading.isValid) {
                    depth_ = reading.valueM;
                    confidence_ = reading.confidence;
                    emit depthChanged(reading.valueM, reading.confidence);
                    invalidStreak_ = 0;
                } else {
                    // After ~0.25 s of no usable reading, blank the UI
                    // so it can't keep painting the pre-drag depth.
                    if (++invalidStreak_ == kInvalidClearFrames) {
                        depth_ = 0.0f;
                        confidence_ = 0.0f;
                        emit depthChanged(0.0f, 0.0f);
                    }
                }

                // Cache for off-crosshair sampling (face tracker, etc.).
                // ~600 KiB memcpy, ~0.3 ms — within frame budget.
                {
                    QMutexLocker lock(&depthCacheMutex_);
                    const size_t sz = static_cast<size_t>(w) * h;
                    if (depthCache_.size() != sz) depthCache_.resize(sz);
                    std::memcpy(depthCache_.data(), data, sz * sizeof(uint16_t));
                    depthCacheW_ = w;
                    depthCacheH_ = h;
                }

                // Downsample to ~10 fps — the colormap is a diagnostic
                // preview, not a real-time feed.
                static constexpr int kColormapDownsample = 3;
                if (colormapEnabled_ && frameCount % kColormapDownsample == 0) {
                    QImage depthImage = colorizeDepth(data, w, h);
                    emit depthFrameReady(depthImage);
                }
            }

            // Color frame: emit every frame but avoid deep copy when possible
            try {
                auto colorFrame = frames.get_color_frame();
                if (colorFrame) {
                    int w = colorFrame.get_width();
                    int h = colorFrame.get_height();
                    auto data = reinterpret_cast<const uint8_t *>(colorFrame.get_data());
                    // Must copy — rs2 frame buffer is reused after this scope
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
    if (connected_.exchange(false)) {
        lastDisconnectMs_ = QDateTime::currentMSecsSinceEpoch();
        connectedSinceMs_ = 0;
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
        if (connected_.exchange(false)) {
            lastDisconnectMs_ = QDateTime::currentMSecsSinceEpoch();
            connectedSinceMs_ = 0;
        }
        emit error("RealSense frame timeout — device may be disconnected");
        emit connectionChanged(false);
        // The capture thread holds a shared_ptr to impl_ and will clean up
        // its own pipeline when it eventually unblocks and exits.
    }
}

float RealSenseManager::depthAt(float nx, float ny) const {
    QMutexLocker lock(&depthCacheMutex_);
    if (depthCache_.empty() || depthCacheW_ <= 0 || depthCacheH_ <= 0) return 0.0f;

    const float scale = depthScaleM_.load(std::memory_order_relaxed);
    const float mmPerUnit = scale * 1000.0f;
    const uint16_t minUnits = static_cast<uint16_t>(
        std::clamp(std::ceil(minValidDepth_ / mmPerUnit), 1.0f, 65535.0f));
    const uint16_t maxUnits = static_cast<uint16_t>(
        std::clamp(std::floor(maxValidDepth_ / mmPerUnit), 1.0f, 65535.0f));

    const int cx = std::clamp(static_cast<int>(nx * depthCacheW_), 0, depthCacheW_ - 1);
    const int cy = std::clamp(static_cast<int>(ny * depthCacheH_), 0, depthCacheH_ - 1);

    // 5×5 median to survive one or two hole pixels.
    constexpr int kRadius = 2;
    const int x0 = std::max(0, cx - kRadius);
    const int y0 = std::max(0, cy - kRadius);
    const int x1 = std::min(depthCacheW_ - 1, cx + kRadius);
    const int y1 = std::min(depthCacheH_ - 1, cy + kRadius);

    uint16_t samples[(2 * kRadius + 1) * (2 * kRadius + 1)];
    int n = 0;
    for (int yy = y0; yy <= y1; ++yy) {
        const uint16_t *row = depthCache_.data() + yy * depthCacheW_;
        for (int xx = x0; xx <= x1; ++xx) {
            const uint16_t d = row[xx];
            if (d >= minUnits && d <= maxUnits) {
                samples[n++] = d;
            }
        }
    }
    if (n == 0) return 0.0f;

    for (int i = 1; i < n; ++i) {
        uint16_t v = samples[i];
        int j = i - 1;
        while (j >= 0 && samples[j] > v) { samples[j + 1] = samples[j]; --j; }
        samples[j + 1] = v;
    }
    return samples[n / 2] * scale;
}

QVariantList RealSenseManager::depthHorizontalSlice(float ny, int samples) const {
    QVariantList out;
    if (samples <= 0) return out;
    out.reserve(samples);

    QMutexLocker lock(&depthCacheMutex_);
    if (depthCache_.empty() || depthCacheW_ <= 0 || depthCacheH_ <= 0) {
        // Keep the slot count stable so the QML layout doesn't collapse
        // on a transient camera glitch.
        for (int i = 0; i < samples; ++i) out.append(0.0f);
        return out;
    }

    const float scale = depthScaleM_.load(std::memory_order_relaxed);
    const float mmPerUnit = scale * 1000.0f;
    const uint16_t minUnits = static_cast<uint16_t>(
        std::clamp(std::ceil(minValidDepth_ / mmPerUnit), 1.0f, 65535.0f));
    const uint16_t maxUnits = static_cast<uint16_t>(
        std::clamp(std::floor(maxValidDepth_ / mmPerUnit), 1.0f, 65535.0f));

    const int baseY = std::clamp(static_cast<int>(ny * depthCacheH_), 0,
                                 depthCacheH_ - 1);

    // Probe three vertical rows per X so a lateral structure (person
    // left-of-frame, wall behind) produces a visible vertical spread
    // in the top-view plot instead of collapsing to a single flat line.
    // Returns 3 * samples depths, consecutive triplets sharing an X.
    const int rowOffset = std::max(2, depthCacheH_ / 32);
    const int rowYs[3] = {
        std::clamp(baseY - rowOffset, 0, depthCacheH_ - 1),
        baseY,
        std::clamp(baseY + rowOffset, 0, depthCacheH_ - 1),
    };

    for (int i = 0; i < samples; ++i) {
        const int cx = static_cast<int>(
            (static_cast<float>(i) / (samples - 1 > 0 ? samples - 1 : 1))
            * (depthCacheW_ - 1));

      for (int rowIdx = 0; rowIdx < 3; ++rowIdx) {
        const int cy = rowYs[rowIdx];

        uint16_t neighbours[9];
        int n = 0;
        for (int dy = -1; dy <= 1; ++dy) {
            const int y = cy + dy;
            if (y < 0 || y >= depthCacheH_) continue;
            for (int dx = -1; dx <= 1; ++dx) {
                const int x = cx + dx;
                if (x < 0 || x >= depthCacheW_) continue;
                const uint16_t d = depthCache_[y * depthCacheW_ + x];
                if (d >= minUnits && d <= maxUnits) {
                    neighbours[n++] = d;
                }
            }
        }

        if (n == 0) {
            out.append(0.0f);
            continue;
        }
        for (int k = 1; k < n; ++k) {
            uint16_t v = neighbours[k];
            int j = k - 1;
            while (j >= 0 && neighbours[j] > v) { neighbours[j + 1] = neighbours[j]; --j; }
            neighbours[j + 1] = v;
        }
        out.append(neighbours[n / 2] * scale);
      }
    }
    return out;
}

QImage RealSenseManager::colorizeDepth(const uint16_t *depthData, int width, int height) {
    // Depth-value → RGB LUT, rebuilt when DEPTH_UNITS changes. Single-
    // threaded (capture thread only) so a plain static is fine.
    static std::array<std::array<uint8_t, 3>, 65536> kDepthLut{};
    static float cachedScale = 0.0f;

    const float scale = depthScaleM_.load(std::memory_order_relaxed);
    if (scale != cachedScale) {
        constexpr int kMaxDisplayDepth = 5000;
        constexpr int kMinDisplay = kDefaultMinValidDepth;
        constexpr int kRangeMm = kMaxDisplayDepth - kMinDisplay;
        const float mmPerUnit = scale * 1000.0f;
        for (int d = 0; d < 65536; ++d) {
            if (d == 0) {
                kDepthLut[d] = {20, 20, 20};
                continue;
            }
            const int d_mm = static_cast<int>(d * mmPerUnit);
            int idx = ((d_mm - kMinDisplay) * 255) / kRangeMm;
            idx = std::clamp(idx, 0, 255);
            kDepthLut[d] = {kTurboR[idx], kTurboG[idx], kTurboB[idx]};
        }
        cachedScale = scale;
    }

    // Half-resolution output: the colormap is a visual preview, the
    // raw data used for autofocus is untouched.
    const int outW = width / 2;
    const int outH = height / 2;
    QImage img(outW, outH, QImage::Format_RGB888);

    for (int y = 0; y < outH; ++y) {
        uint8_t *line = img.scanLine(y);
        const uint16_t *srcRow = depthData + (y * 2) * width;
        for (int x = 0; x < outW; ++x) {
            const auto &rgb = kDepthLut[srcRow[x * 2]];
            line[x * 3 + 0] = rgb[0];
            line[x * 3 + 1] = rgb[1];
            line[x * 3 + 2] = rgb[2];
        }
    }
    return img;
}

} // namespace alice
