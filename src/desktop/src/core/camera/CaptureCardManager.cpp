#include "core/camera/CaptureCardManager.h"
#include <QCameraDevice>
#include <QCameraFormat>
#include <QDateTime>
#include <cstdio>

namespace alice {

CaptureCardManager::CaptureCardManager(QObject *parent)
    : QObject(parent)
{
    frameTimeoutTimer_.setInterval(1000);
    connect(&frameTimeoutTimer_, &QTimer::timeout,
            this, &CaptureCardManager::checkFrameTimeout);
}

CaptureCardManager::~CaptureCardManager() {
    stop();
}

QStringList CaptureCardManager::availableDevices() const {
    QStringList names;
    for (const auto &dev : QMediaDevices::videoInputs()) {
        names.append(dev.description());
    }
    return names;
}

QCameraDevice CaptureCardManager::findCaptureCard() const {
    for (const auto &dev : QMediaDevices::videoInputs()) {
        QString desc = dev.description().toLower();
        if (desc.contains("realsense") || desc.contains("intel(r) rs"))
            continue;
        return dev;
    }
    return {};
}

QVariantList CaptureCardManager::availableFormats() const {
    QVariantList formats;
    auto device = findCaptureCard();
    if (device.isNull()) return formats;
    for (const auto &fmt : device.videoFormats()) {
        QVariantMap m;
        auto res = fmt.resolution();
        m["width"] = res.width();
        m["height"] = res.height();
        m["minFps"] = fmt.minFrameRate();
        m["maxFps"] = fmt.maxFrameRate();
        m["label"] = QString("%1x%2 @ %3fps").arg(res.width()).arg(res.height()).arg(fmt.maxFrameRate());
        formats.append(m);
    }
    return formats;
}

void CaptureCardManager::setCameraResolution(int w, int h, int fps) {
    requestedWidth_ = w;
    requestedHeight_ = h;
    requestedFps_ = fps;
    if (connected_) {
        stop();
        start();
    }
}

void CaptureCardManager::start() {
    auto device = findCaptureCard();
    if (device.isNull()) {
        return;
    }
    startDevice(device.description());
}

void CaptureCardManager::startDevice(const QString &deviceName) {
    stop();

    QCameraDevice target;
    for (const auto &dev : QMediaDevices::videoInputs()) {
        if (dev.description() == deviceName) {
            target = dev;
            break;
        }
    }
    if (target.isNull()) {
        emit error(QString("Device not found: %1").arg(deviceName));
        return;
    }

    camera_ = std::make_unique<QCamera>(target);
    session_ = std::make_unique<QMediaCaptureSession>();
    sink_ = std::make_unique<QVideoSink>();

    session_->setCamera(camera_.get());
    session_->setVideoSink(sink_.get());

    connect(sink_.get(), &QVideoSink::videoFrameChanged,
            this, &CaptureCardManager::onFrameChanged);

    connect(camera_.get(), &QCamera::errorOccurred,
            this, [this](QCamera::Error err, const QString &desc) {
        Q_UNUSED(err);
        emit error(QString("Camera error: %1").arg(desc));
        disconnectDevice();
    });

    connect(camera_.get(), &QCamera::activeChanged,
            this, [this](bool active) {
        if (active && !connected_) {
            connected_ = true;
            connectedSinceMs_ = QDateTime::currentMSecsSinceEpoch();
            consecutiveFailures_ = 0;
            lastFrameTime_.start();
            frameTimeoutTimer_.start();
            emit connectionChanged(true);
        } else if (!active && connected_) {
            disconnectDevice();
        }
    });

    // Apply requested resolution if set
    if (requestedWidth_ > 0 && requestedHeight_ > 0) {
        for (const auto &fmt : target.videoFormats()) {
            if (fmt.resolution().width() == requestedWidth_ &&
                fmt.resolution().height() == requestedHeight_) {
                camera_->setCameraFormat(fmt);
                break;
            }
        }
    }

    camera_->start();
}

void CaptureCardManager::stop() {
    frameTimeoutTimer_.stop();
    if (sink_) sink_->disconnect(this);
    if (camera_) {
        camera_->disconnect(this);
        camera_->stop();
    }
    camera_.reset();
    session_.reset();
    sink_.reset();
    consecutiveFailures_ = 0;
    if (connected_) {
        connected_ = false;
        lastDisconnectMs_ = QDateTime::currentMSecsSinceEpoch();
        connectedSinceMs_ = 0;
        emit connectionChanged(false);
    }
}

void CaptureCardManager::disconnectDevice() {
    if (!connected_) return;

    // Immediately stop receiving frames — this is the critical line
    // that prevents the event loop from being flooded
    if (sink_) sink_->disconnect(this);

    connected_ = false;
    lastDisconnectMs_ = QDateTime::currentMSecsSinceEpoch();
    connectedSinceMs_ = 0;
    frameTimeoutTimer_.stop();
    emit connectionChanged(false);

    // Defer heavy cleanup so we don't destroy objects mid-signal
    QTimer::singleShot(0, this, [this]() {
        if (camera_) {
            camera_->disconnect(this);
            camera_->stop();
        }
        camera_.reset();
        session_.reset();
        sink_.reset();
    });
}

void CaptureCardManager::onFrameChanged() {
    if (!sink_ || !connected_) return;
    QVideoFrame frame = sink_->videoFrame();
    if (!frame.isValid()) return;

    if (!frame.map(QVideoFrame::ReadOnly)) {
        consecutiveFailures_++;
        if (consecutiveFailures_ > 3) {
            emit error("Capture card: repeated frame failures");
            disconnectDevice();
        }
        return;
    }
    QImage img = frame.toImage();
    frame.unmap();

    if (!img.isNull() && img.width() > 0) {
        consecutiveFailures_ = 0;
        lastFrameTime_.restart();
        emit frameReady(img.convertToFormat(QImage::Format_RGB888));
    }
}

void CaptureCardManager::checkFrameTimeout() {
    if (!connected_) return;
    int elapsed = lastFrameTime_.elapsed();
    if (elapsed > kFrameTimeoutMs) {
        fprintf(stderr, "[CaptureCard] Frame timeout: %dms since last good frame\n", elapsed);
        emit error("Capture card frame timeout — device may be disconnected");
        disconnectDevice();
    }
}

} // namespace alice
