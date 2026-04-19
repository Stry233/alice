#include "ui/AppController.h"
#include "core/realsense/RealSenseManager.h"
#include "core/camera/CaptureCardManager.h"
#include "core/motor/MotorController.h"
#include "core/autofocus/FaceDetector.h"
#include "core/autofocus/SubjectTracker.h"
#include "core/autofocus/AutofocusController.h"
#include "core/coordination/DeviceCoordinator.h"
#include "core/coordination/SettingsManager.h"
#include "network/SyncServer.h"
#include "network/SyncProtocol.h"
#include "network/QrGenerator.h"

#include <QBuffer>
#include <QColorSpace>
#include <QCoreApplication>
#include <QDateTime>
#include <QDir>
#include <QFileInfo>
#include <QImageWriter>
#include <QtConcurrent/QtConcurrent>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcessEnvironment>
#include <QStandardPaths>
#include <QUrl>
#include <QVariantMap>
#include <limits>

namespace alice {

namespace {

// Encoding targets per stream type.
//
// The capture card is the "main view" the remote Android client uses to
// monitor the cinema camera, so it keeps native 1080p resolution, a
// SmoothTransformation downscale when the source is bigger (e.g. 4K
// capture), and a high JPEG quality default (the slider lets the user push
// further to visually-lossless 100 if bandwidth allows).
//
// The RealSense depth colormap and RGB color are rendered as small client
// overlays in Alice Android, so we clamp them to 640×480 and drop the
// quality default. At that size the compression artefacts are invisible to
// the user but the bandwidth savings are substantial (~8× smaller than
// full resolution) which keeps the overlay streams from stealing budget
// from the capture card.
constexpr int kCaptureMaxW = 1920;
constexpr int kCaptureMaxH = 1080;
constexpr int kOverlayMaxW = 640;
constexpr int kOverlayMaxH = 480;

QByteArray encodeFrameToJpeg(const QImage &frame, int maxW, int maxH, int quality) {
    QImage scaled = frame;
    if (frame.width() > maxW || frame.height() > maxH) {
        scaled = frame.scaled(maxW, maxH, Qt::KeepAspectRatio, Qt::SmoothTransformation);
    }
    // Force RGB888 so the JPEG encoder writes a straight RGB→YCbCr path.
    // Any other format (ARGB32, BGR, indexed…) causes libjpeg to do an
    // internal conversion that can introduce subtle colour shifts on the
    // receiving side — exactly the symptom the Android client was showing.
    if (scaled.format() != QImage::Format_RGB888) {
        scaled = scaled.convertToFormat(QImage::Format_RGB888);
    }
    // Force-tag as sRGB. HDMI capture cards typically provide frames with
    // a BT.709 colour space tag, which Qt preserves through toImage() and
    // convertToFormat(). libjpeg then embeds a BT.709 ICC profile in the
    // JPEG. On the PC side QPainter ignores ICC profiles and draws the
    // pixels as-is (which is what the user sees as "correct"), but on
    // Android the Compose render path honours the embedded profile and
    // colour-manages BT.709 → display, producing a slightly warmer/redder
    // image relative to the PC. By overwriting the tag with sRGB (without
    // touching pixel values) we make both sides interpret the bytes the
    // same way, and the visual match on both screens.
    scaled.setColorSpace(QColorSpace(QColorSpace::SRgb));

    QByteArray jpegData;
    QBuffer buffer(&jpegData);
    buffer.open(QIODevice::WriteOnly);
    QImageWriter writer(&buffer, "JPEG");
    writer.setQuality(quality);
    writer.setOptimizedWrite(true);  // tighter Huffman tables, no quality loss
    writer.write(scaled);
    buffer.close();
    return jpegData;
}

}  // namespace

AppController::AppController(QObject *parent)
    : QObject(parent)
    , realsense_(std::make_unique<RealSenseManager>())
    , captureCard_(std::make_unique<CaptureCardManager>())
    , motor_(std::make_unique<MotorController>())
    , faceDetector_(std::make_unique<FaceDetector>())
    , subjectTracker_(std::make_unique<SubjectTracker>())
    , autofocus_(std::make_unique<AutofocusController>())
    , coordinator_(std::make_unique<DeviceCoordinator>(motor_.get(), realsense_.get(), captureCard_.get()))
    , settings_(std::make_unique<SettingsManager>())
    , syncServer_(std::make_unique<SyncServer>())
{
    // Depth data → autofocus
    connect(realsense_.get(), &RealSenseManager::depthChanged,
            this, &AppController::onDepthChanged);

    // Video frames
    connect(realsense_.get(), &RealSenseManager::colorFrameReady,
            this, &AppController::onColorFrame);
    connect(realsense_.get(), &RealSenseManager::depthFrameReady,
            this, &AppController::onDepthFrame);

    // Capture card frames and errors (connection state handled by coordinator)
    connect(captureCard_.get(), &CaptureCardManager::frameReady,
            this, &AppController::onCaptureFrame);
    connect(captureCard_.get(), &CaptureCardManager::error,
            this, [this](const QString &msg) { log("CAMERA", "ERROR: " + msg); });

    // Motor position feedback and errors
    connect(motor_.get(), &MotorController::positionChanged,
            this, &AppController::motorPositionChanged);
    connect(motor_.get(), &MotorController::error,
            this, [this](const QString &msg) { log("MOTOR", "ERROR: " + msg); });
    connect(motor_.get(), &MotorController::ready,
            this, [this]() { log("MOTOR", "Device ready"); });

    // RealSense errors
    connect(realsense_.get(), &RealSenseManager::error,
            this, [this](const QString &msg) { log("REALSENSE", "ERROR: " + msg); });

    // Device coordination → autofocus readiness
    connect(coordinator_.get(), &DeviceCoordinator::motorConnectionChanged,
            this, [this](bool connected) {
        autofocus_->updateDeviceReadiness(connected, realsense_->isConnected());
        emit deviceStateChanged();
        if (connected) {
            // Restore last motor position
            int lastPos = settings_->motorLastPosition();
            if (lastPos > 0 && lastPos <= 4095) {
                motor_->setPosition(lastPos);
                log("MOTOR", QString("Restored position to %1").arg(lastPos));
            }
        }
        log("MOTOR", connected ? "Connected" : "Disconnected");
    });
    connect(coordinator_.get(), &DeviceCoordinator::realSenseConnectionChanged,
            this, [this](bool connected) {
        autofocus_->updateDeviceReadiness(motor_->isConnected(), connected);
        if (!connected) {
            colorFrame_ = QImage();
            depthFrame_ = QImage();
            currentDepth_ = 0.0f;
            currentConfidence_ = 0.0f;
            streamColor_ = false;
            streamDepth_ = false;
            emit colorFrameChanged();
            emit depthFrameChanged();
            emit depthChanged(0, 0);
        }
        emit deviceStateChanged();
        notifyRemoteDeviceStates();
        log("REALSENSE", connected ? "Connected" : "Disconnected");
    });
    connect(coordinator_.get(), &DeviceCoordinator::captureCardConnectionChanged,
            this, [this](bool connected) {
        if (!connected) {
            captureFrame_ = QImage();
            streamCapture_ = false;
            emit captureFrameChanged();
        }
        emit deviceStateChanged();
        notifyRemoteDeviceStates();
        log("CAMERA", connected ? "Capture card connected" : "Capture card disconnected");
    });

    // Sync server → UI state
    connect(syncServer_.get(), &SyncServer::clientConnected,
            this, [this]() {
        log("NETWORK", "Android client connected");
        emit syncStateChanged();
    });
    connect(syncServer_.get(), &SyncServer::clientDisconnected,
            this, [this]() {
        streamColor_ = false;
        streamDepth_ = false;
        streamCapture_ = false;
        log("NETWORK", "Android client disconnected");
        emit syncStateChanged();
    });
    connect(syncServer_.get(), &SyncServer::runningChanged,
            this, &AppController::syncStateChanged);

    // Autofocus → motor
    connect(autofocus_.get(), &AutofocusController::targetPositionChanged,
            this, &AppController::onTargetPositionChanged);
    connect(autofocus_.get(), &AutofocusController::stateChanged,
            this, &AppController::autofocusStateChanged);

    // Entering AF-F should start from a clean tracker slate — otherwise a
    // ghost track left over from a previous session can snap onto an
    // unrelated face on the very first detection pass.
    connect(autofocus_.get(), &AutofocusController::modeChanged,
            this, [this](FocusMode mode) {
        if (mode == FocusMode::FaceTracking) {
            subjectTracker_->reset();
            lastPrimaryId_ = -1;
            if (!lastTrackedFaces_.empty()) {
                lastTrackedFaces_.clear();
                emit trackedFacesChanged();
                broadcastTrackedFaces();
            }
        }
    });

    // Sync server messages
    connect(syncServer_.get(), &SyncServer::messageReceived,
            this, &AppController::onSyncMessage);

    // State broadcast timer (10 Hz to sync clients)
    stateTimer_.setInterval(SyncProtocolConstants::kStateUpdateIntervalMs);
    connect(&stateTimer_, &QTimer::timeout, this, &AppController::broadcastState);
}

AppController::~AppController() {
    // Disconnect ALL signals from hardware to prevent callbacks during destruction
    realsense_->disconnect(this);
    captureCard_->disconnect(this);
    motor_->disconnect(this);
    coordinator_->disconnect(this);
    syncServer_->disconnect(this);

    stateTimer_.stop();
    syncServer_->stop();
    coordinator_->stop();
    captureCard_->stop();
    realsense_->stop();

    // Give detached threads time to notice running_=false and exit
    QThread::msleep(100);
}

void AppController::initialize() {
    // ASCII art banner
    const QStringList banner = {
        "       d8888 888 d8b",
        "      d88888 888 Y8P",
        "     d88P888 888",
        "    d88P 888 888 888  .d8888b .d88b.",
        "   d88P  888 888 888 d88P\"   d8P  Y8b",
        "  d88P   888 888 888 888     88888888",
        " d8888888888 888 888 Y88b.   Y8b.",
        "d88P     888 888 888  \"Y8888P \"Y8888",
        "",
        "-------------------------------------",
    };
    for (const auto &line : banner)
        logBuffer_.append(line);
    emit logChanged();

    log("SYSTEM", QString("%1 v%2 starting...").arg(ALICE_APP_NAME, ALICE_APP_VERSION));

    // Apply settings to motor
    motor_->setOffset(settings_->motorOffset());
    motor_->setReversed(settings_->motorReverse());

    // Apply settings to autofocus
    autofocus_->setConfidenceThreshold(settings_->confidenceThreshold());
    autofocus_->setSmoothingAlpha(settings_->smoothingAlpha());

    // Apply settings to depth sensor
    realsense_->setMinValidDepth(settings_->depthMinDistance());
    realsense_->setMaxValidDepth(settings_->depthMaxDistance());

    // Restore saved resolution settings
    realsense_->setStreamConfig(
        settings_->depthResW(), settings_->depthResH(), settings_->depthResFps(),
        settings_->colorResW(), settings_->colorResH(), settings_->colorResFps());
    if (settings_->captureResW() > 0) {
        captureCard_->setCameraResolution(
            settings_->captureResW(), settings_->captureResH(), settings_->captureResFps());
    }

    // Restore transmission quality
    streamQualityDepth_ = settings_->txQualityDepth();
    streamQualityColor_ = streamQualityDepth_;
    streamQualityCapture_ = settings_->txQualityCapture();
    int savedFps = settings_->txMaxFps();
    if (savedFps > 0) {
        minFrameIntervalMs_ = std::max(static_cast<int64_t>(16), static_cast<int64_t>(1000 / savedFps));
    }

    // Restore last motor position
    int lastPos = settings_->motorLastPosition();
    if (lastPos > 0) {
        motor_->setPosition(lastPos);
    }

    // Load the YOLO face detection model. Search order:
    //   1. $ALICE_FACE_MODEL environment variable (explicit override)
    //   2. <binary dir>/models/yolov11s-face.onnx   (preferred — larger, more accurate)
    //   3. <binary dir>/models/yolo-face.onnx       (nano fallback)
    //   4. /usr/share/alice-studio/models/<same filenames>
    // Missing file → AF-F becomes a no-op, everything else keeps working.
    {
        const QString binModels = QCoreApplication::applicationDirPath() + "/models";
        const QString sysModels = QStringLiteral("/usr/share/alice-studio/models");

        QStringList candidates;
        const auto envPath = QProcessEnvironment::systemEnvironment()
                                 .value("ALICE_FACE_MODEL");
        if (!envPath.isEmpty()) candidates << envPath;
        candidates << (binModels + "/yolov11s-face.onnx");
        candidates << (binModels + "/yolo-face.onnx");
        candidates << (sysModels + "/yolov11s-face.onnx");
        candidates << (sysModels + "/yolo-face.onnx");

        QString chosen;
        for (const auto &path : candidates) {
            if (QFileInfo::exists(path)) {
                chosen = path;
                break;
            }
        }
        if (chosen.isEmpty()) {
            log("AUTOFOCUS", "WARN: no yolo-face*.onnx found — face tracking disabled");
        } else if (faceDetector_->loadModel(chosen)) {
            log("AUTOFOCUS", QString("Face detector loaded: %1 (EP=%2)")
                                 .arg(QFileInfo(chosen).fileName(),
                                      faceDetector_->executionProvider()));
        } else {
            log("AUTOFOCUS", QString("ERROR: failed to load face model at %1").arg(chosen));
        }
    }

    // Restore the last calibration mapping if one was cached. Done after
    // autofocus_ has its settings applied but before device discovery
    // starts, so by the time a depth reading is published the mapping is
    // already active.
    loadMappingCache();

    // Start device discovery (coordinator manages all three devices)
    coordinator_->start();

    log("SYSTEM", "Device discovery started");
}

// ── Device state ─────────────────────────────────────────────────────

bool AppController::motorConnected() const { return motor_->isConnected(); }
bool AppController::realSenseConnected() const { return realsense_->isConnected(); }
bool AppController::captureCardConnected() const { return captureCard_->isConnected(); }
int AppController::motorPosition() const { return motor_->currentPosition(); }

qint64 AppController::motorConnectedSinceMs() const { return motor_->connectedSinceMs(); }
qint64 AppController::motorLastDisconnectMs() const { return motor_->lastDisconnectMs(); }
qint64 AppController::realSenseConnectedSinceMs() const { return realsense_->connectedSinceMs(); }
qint64 AppController::realSenseLastDisconnectMs() const { return realsense_->lastDisconnectMs(); }
qint64 AppController::captureCardConnectedSinceMs() const { return captureCard_->connectedSinceMs(); }
qint64 AppController::captureCardLastDisconnectMs() const { return captureCard_->lastDisconnectMs(); }

// Device identity getters — populated from the actual hardware managers.
// Each falls back to a sensible generic label when the manager hasn't yet
// seen a device so the popover never shows an empty string.
QString AppController::motorDeviceName() const {
    QString name = motor_ ? motor_->deviceDescription() : QString();
    return name.isEmpty() ? QStringLiteral("Motor Dongle") : name;
}
QString AppController::motorDeviceAddress() const {
    QString port = motor_ ? motor_->devicePortName() : QString();
    return port.isEmpty() ? QStringLiteral("—") : port;
}
QString AppController::realSenseDeviceName() const {
    QString name = realsense_ ? realsense_->deviceName() : QString();
    return name.isEmpty() ? QStringLiteral("Depth Camera") : name;
}
QString AppController::realSenseDeviceAddress() const {
    QString bus = realsense_ ? realsense_->deviceBus() : QString();
    return bus.isEmpty() ? QStringLiteral("USB") : bus;
}
QString AppController::captureCardDeviceName() const {
    QString name = captureCard_ ? captureCard_->deviceDescription() : QString();
    return name.isEmpty() ? QStringLiteral("Capture Card") : name;
}
QString AppController::captureCardDeviceAddress() const {
    return QStringLiteral("UVC");
}

// ── User-initiated device control ────────────────────────────────────

void AppController::restartMotor() {
    log("MOTOR", "Restart requested by user");
    coordinator_->restartMotor();
}
void AppController::disconnectMotor() {
    log("MOTOR", "Disconnect requested by user");
    coordinator_->disconnectMotor();
}
void AppController::reconnectMotor() {
    log("MOTOR", "Reconnect requested by user");
    coordinator_->reconnectMotor();
}

void AppController::restartDepth() {
    log("REALSENSE", "Restart requested by user");
    coordinator_->restartRealSense();
}
void AppController::disconnectDepth() {
    log("REALSENSE", "Disconnect requested by user");
    coordinator_->disconnectRealSense();
}
void AppController::reconnectDepth() {
    log("REALSENSE", "Reconnect requested by user");
    coordinator_->reconnectRealSense();
}

void AppController::restartCam() {
    log("CAMERA", "Restart requested by user");
    coordinator_->restartCaptureCard();
}
void AppController::disconnectCam() {
    log("CAMERA", "Disconnect requested by user");
    coordinator_->disconnectCaptureCard();
}
void AppController::reconnectCam() {
    log("CAMERA", "Reconnect requested by user");
    coordinator_->reconnectCaptureCard();
}

// ── Runtime-adjustable settings ─────────────────────────────────────

void AppController::setAfConfidenceThreshold(float v) {
    autofocus_->setConfidenceThreshold(v);
    settings_->setConfidenceThreshold(v);
    broadcastSettings();
}

void AppController::setAfSmoothingAlpha(float v) {
    autofocus_->setSmoothingAlpha(v);
    settings_->setSmoothingAlpha(v);
    broadcastSettings();
}

void AppController::setMotorReversed(bool v) {
    motor_->setReversed(v);
    settings_->setMotorReverse(v);
    broadcastSettings();
}

void AppController::setMotorOffset(int v) {
    motor_->setOffset(v);
    settings_->setMotorOffset(v);
    broadcastSettings();
}

float AppController::afConfidenceThreshold() const { return settings_->confidenceThreshold(); }
float AppController::afSmoothingAlpha() const { return settings_->smoothingAlpha(); }
bool AppController::motorReversed() const { return settings_->motorReverse(); }
int AppController::motorOffset() const { return settings_->motorOffset(); }

void AppController::broadcastSettings() {
    if (!syncServer_->hasClient()) return;
    lastSettingsBroadcastMs_ = QDateTime::currentMSecsSinceEpoch();
    QJsonObject payload;
    payload["confidenceThreshold"] = settings_->confidenceThreshold();
    payload["smoothingAlpha"] = settings_->smoothingAlpha();
    payload["motorReverse"] = settings_->motorReverse();
    payload["motorOffset"] = settings_->motorOffset();
    syncServer_->broadcast(SyncMessage::settingsSync(payload));
}

// ── Depth sensor tuning ─────────────────────────────────────────────

void AppController::setDepthMinDistance(int mm) {
    realsense_->setMinValidDepth(mm);
    settings_->setDepthMinDistance(mm);
}
void AppController::setDepthMaxDistance(int mm) {
    realsense_->setMaxValidDepth(mm);
    settings_->setDepthMaxDistance(mm);
}
void AppController::setDepthSmoothing(float sliderValue) {
    // The QML slider ranges 10-500 for historical reasons (the old Kalman
    // took measurement-noise R directly). The new DepthEstimator takes an
    // EMA alpha in [0.02, 1.0]. Map inversely so "higher slider = smoother"
    // still reads the same way to the user:
    //   10  → alpha 1.00 (raw, no smoothing)
    //   100 → alpha 0.80 (light smoothing — default)
    //   500 → alpha 0.02 (heavy smoothing)
    const float t = std::clamp((sliderValue - 10.0f) / 490.0f, 0.0f, 1.0f);
    const float alpha = std::max(0.02f, 1.0f - t * 0.98f);
    realsense_->setDepthSmoothing(alpha);
    depthSmoothingSliderValue_ = sliderValue;
}
int AppController::depthMinDistance() const { return settings_->depthMinDistance(); }
int AppController::depthMaxDistance() const { return settings_->depthMaxDistance(); }
float AppController::depthSmoothingValue() const { return depthSmoothingSliderValue_; }

void AppController::resetAllSettings() {
    settings_->resetAllSettings();
    // Re-apply defaults to running components
    autofocus_->setConfidenceThreshold(settings_->confidenceThreshold());
    autofocus_->setSmoothingAlpha(settings_->smoothingAlpha());
    motor_->setOffset(settings_->motorOffset());
    motor_->setReversed(settings_->motorReverse());
    realsense_->setMinValidDepth(settings_->depthMinDistance());
    realsense_->setMaxValidDepth(settings_->depthMaxDistance());
    // Go through our own setter so the slider<->alpha mapping stays in sync
    setDepthSmoothing(100.0f);
    log("SYSTEM", "All settings reset to defaults");
    emit autofocusStateChanged();
    emit deviceStateChanged();
}

// ── Depth ────────────────────────────────────────────────────────────

float AppController::depth() const { return currentDepth_; }
float AppController::depthConfidence() const { return currentConfidence_; }
float AppController::measureX() const { return realsense_->measureX(); }
float AppController::measureY() const { return realsense_->measureY(); }

// ── Autofocus ────────────────────────────────────────────────────────

bool AppController::autofocusEnabled() const { return autofocus_->isEnabled(); }
void AppController::setAutofocusEnabled(bool enabled) {
    autofocus_->setEnabled(enabled);
    broadcastModeChange();
}
int AppController::focusMode() const { return autofocus_->focusModeInt(); }
void AppController::setFocusMode(int mode) {
    autofocus_->setFocusModeInt(mode);
    broadcastModeChange();
}
bool AppController::activelyFocusing() const { return autofocus_->isActivelyFocusing(); }
bool AppController::hasMapping() const { return autofocus_->hasMapping(); }
int AppController::targetMotorPosition() const { return autofocus_->targetMotorPosition(); }
QString AppController::mappingName() const {
    const auto &m = autofocus_->currentMapping();
    return m ? QString::fromStdString(m->name()) : "";
}

QVariantList AppController::mappingPoints() const {
    QVariantList result;
    const auto &m = autofocus_->currentMapping();
    if (!m) return result;
    for (const auto &pt : m->points()) {
        QVariantMap p;
        p["depth"] = pt.depth;
        p["motorPosition"] = pt.motorPosition;
        p["confidence"] = pt.confidence;
        result.append(p);
    }
    return result;
}

void AppController::loadMappingFromFile(const QString &pathOrUrl) {
    // QML's FileDialog delivers selectedFile as a URL ("file:///…"), but the
    // core AutofocusMapping::fromFile uses std::ifstream which can't open a
    // URI. Normalise to a local filesystem path here.
    QString localPath = pathOrUrl;
    const QUrl url(pathOrUrl);
    if (url.isLocalFile()) {
        localPath = url.toLocalFile();
    }

    if (localPath.isEmpty()) {
        log("AUTOFOCUS", "ERROR: loadMappingFromFile called with empty path");
        return;
    }

    if (autofocus_->loadMappingFromFile(localPath)) {
        log("AUTOFOCUS", QString("Mapping loaded from %1").arg(localPath));
        broadcastCurrentMapping();
        saveMappingCache();
    } else {
        log("AUTOFOCUS", QString("ERROR: Failed to load mapping from %1").arg(localPath));
    }
}

void AppController::loadPreset(int presetIndex) {
    autofocus_->loadPreset(static_cast<MappingPreset>(presetIndex));
    log("AUTOFOCUS", "Preset loaded");
    broadcastCurrentMapping();
    saveMappingCache();
}

void AppController::clearMapping() {
    autofocus_->clearMapping();
    log("AUTOFOCUS", "Mapping cleared");
    saveMappingCache(); // removes the cache file when mapping is null
    if (syncServer_->hasClient()) {
        auto msg = SyncMessage::calibrationSync("CLEAR");
        syncServer_->broadcast(msg);
    }
}

bool AppController::saveMappingToFile(const QString &pathOrUrl,
                                      const QVariantList &points,
                                      const QString &name) {
    // Normalise file:// URLs from the QML FileDialog, same as
    // loadMappingFromFile() — std::ofstream can't open a URI.
    QString localPath = pathOrUrl;
    const QUrl url(pathOrUrl);
    if (url.isLocalFile()) {
        localPath = url.toLocalFile();
    }

    if (localPath.isEmpty()) {
        log("AUTOFOCUS", "ERROR: saveMappingToFile called with empty path");
        return false;
    }
    if (points.size() < 2) {
        log("AUTOFOCUS", "ERROR: Need at least 2 calibration points to export a mapping");
        return false;
    }

    std::vector<MappingPoint> mappingPoints;
    mappingPoints.reserve(points.size());
    for (const auto &entry : points) {
        const QVariantMap m = entry.toMap();
        MappingPoint p;
        p.depth         = static_cast<float>(m.value("depth").toDouble());
        p.motorPosition = m.value("motorPosition").toInt();
        p.confidence    = static_cast<float>(m.value("confidence", 1.0).toDouble());
        if (!p.isValid()) {
            log("AUTOFOCUS", QString("ERROR: Invalid calibration point at index %1")
                             .arg(mappingPoints.size()));
            return false;
        }
        mappingPoints.push_back(p);
    }

    MappingMetadata metadata;
    metadata.calibrationMethod = "manual";
    metadata.createdAt = QDateTime::currentSecsSinceEpoch();

    AutofocusMapping mapping(name.toStdString(), mappingPoints,
                             /*description=*/"Exported from Alice Studio",
                             metadata);

    if (!mapping.saveToFile(localPath.toStdString())) {
        log("AUTOFOCUS", QString("ERROR: Failed to write mapping to %1").arg(localPath));
        return false;
    }
    log("AUTOFOCUS", QString("Mapping exported to %1 (%2 points)")
                     .arg(localPath).arg(mappingPoints.size()));
    return true;
}

// ── Motor control ────────────────────────────────────────────────────

void AppController::setMotorPosition(int position) {
    motor_->setPosition(position);
    settings_->setMotorLastPosition(position);
}

void AppController::setMotorDestination(int address) {
    motor_->setDestinationAddress(address);
    log("MOTOR", QString("Destination set to 0x%1").arg(address, 4, 16, QChar('0')).toUpper());
}

void AppController::scanMotorAddress(int address) {
    motor_->scanAddress(address);
    log("MOTOR", QString("Scanning address 0x%1").arg(address, 4, 16, QChar('0')).toUpper());
}

// ── Depth measurement ────────────────────────────────────────────────

void AppController::setMeasurementPosition(float x, float y) {
    realsense_->setMeasurementPosition(x, y);
    emit measurePositionChanged();
    // Push crosshair update to the sync peer immediately so the remote
    // Android client's crosshair follows without waiting up to 100 ms for
    // the next periodic state broadcast.
    if (syncServer_->hasClient()) {
        syncServer_->broadcast(SyncMessage::measurePosition(x, y));
    }
}

void AppController::jumpToMeasurementPosition(float x, float y) {
    // Teleport: clear the estimator first so the new position is sampled
    // without any carryover from the old crosshair's depth.
    realsense_->jumpToPosition(x, y);
    emit measurePositionChanged();
    if (syncServer_->hasClient()) {
        syncServer_->broadcast(SyncMessage::measurePosition(x, y));
    }
}

void AppController::processTap(float x, float y) {
    // processTap is triggered by an explicit user tap — always a teleport.
    jumpToMeasurementPosition(x, y);
    autofocus_->processTap(x, y);
}

// ── Face tracking ────────────────────────────────────────────────────

void AppController::selectFace(int trackingId) {
    subjectTracker_->selectFace(trackingId);
    // Broadcast right away so the sync peer picks up the new selection
    // without waiting for the next detection cycle.
    broadcastTrackedFaces();
}

// Serialize a single tracked face into a QVariantMap. Both the QML
// overlay (`trackedFaces()`) and the wire broadcast (`broadcastTrackedFaces()`)
// emit the same geometry fields — only the QML path needs the extra
// `selected` + `color` hints, since the wire message carries the active
// face id at the top level instead. Keeping the field list in one place
// makes sure the two consumers never drift apart.
QVariantMap AppController::serializeFaceEntry(const TrackedFace &face,
                                              double invW, double invH,
                                              int selectedId,
                                              bool includeUIFields) const
{
    QVariantMap m;
    m["id"] = face.trackingId;
    m["x"] = face.boundingBox.x() * invW;
    m["y"] = face.boundingBox.y() * invH;
    m["w"] = face.boundingBox.width()  * invW;
    m["h"] = face.boundingBox.height() * invH;
    m["centerX"] = face.center.x();
    m["centerY"] = face.center.y();
    m["confidence"] = static_cast<double>(face.confidence);
    m["state"] = static_cast<int>(face.state);
    if (includeUIFields) {
        m["selected"] = (face.trackingId == selectedId);
        m["color"] = face.color.name();
    }
    if (face.leftEye)  { m["leftEyeX"]  = face.leftEye->x();  m["leftEyeY"]  = face.leftEye->y(); }
    if (face.rightEye) { m["rightEyeX"] = face.rightEye->x(); m["rightEyeY"] = face.rightEye->y(); }
    return m;
}

static std::pair<double, double> faceFrameInverses(int width, int height) {
    return { (width  > 0) ? 1.0 / width  : 1.0,
             (height > 0) ? 1.0 / height : 1.0 };
}

QVariantList AppController::trackedFaces() const {
    QVariantList out;
    out.reserve(static_cast<int>(lastTrackedFaces_.size()));
    const int selectedId = subjectTracker_->selectedFaceId();
    const auto [invW, invH] = faceFrameInverses(faceFrameWidth_, faceFrameHeight_);

    for (const auto &face : lastTrackedFaces_) {
        out.append(serializeFaceEntry(face, invW, invH, selectedId, /*includeUIFields=*/true));
    }
    return out;
}

void AppController::broadcastTrackedFaces() {
    if (!syncServer_->hasClient()) return;

    // Throttle to minFrameIntervalMs_ — same budget as the video streams,
    // so a 30 fps detection cadence sends at most ~30 messages/sec.
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (now - lastFaceBroadcastMs_ < minFrameIntervalMs_) return;
    lastFaceBroadcastMs_ = now;

    const auto [invW, invH] = faceFrameInverses(faceFrameWidth_, faceFrameHeight_);

    QJsonArray faces;
    for (const auto &face : lastTrackedFaces_) {
        const auto entry = serializeFaceEntry(face, invW, invH, /*selectedId=*/-1,
                                              /*includeUIFields=*/false);
        faces.append(QJsonObject::fromVariantMap(entry));
    }

    syncServer_->broadcast(SyncMessage::faceTracking(
        faces, faceFrameWidth_, faceFrameHeight_,
        subjectTracker_->selectedFaceId()));
}

// ── Resolution / transmission quality ────────────────────────────────

QVariantList AppController::realSenseDepthModes() const { return realsense_->availableDepthModes(); }
QVariantList AppController::realSenseColorModes() const { return realsense_->availableColorModes(); }
QVariantList AppController::captureCardFormats() const { return captureCard_->availableFormats(); }

void AppController::setRealSenseResolution(int dw, int dh, int df, int cw, int ch, int cf) {
    realsense_->setStreamConfig(dw, dh, df, cw, ch, cf);
    settings_->setDepthResolution(dw, dh, df);
    settings_->setColorResolution(cw, ch, cf);
    log("REALSENSE", QString("Resolution changed to %1x%2@%3 + %4x%5@%6").arg(dw).arg(dh).arg(df).arg(cw).arg(ch).arg(cf));
}

void AppController::setCaptureCardResolution(int w, int h, int fps) {
    captureCard_->setCameraResolution(w, h, fps);
    settings_->setCaptureResolution(w, h, fps);
    log("CAMERA", QString("Resolution changed to %1x%2@%3").arg(w).arg(h).arg(fps));
}

int AppController::txQualityDepth() const { return streamQualityDepth_; }
void AppController::setTxQualityDepth(int q) {
    streamQualityDepth_ = std::clamp(q, 10, 100);
    streamQualityColor_ = streamQualityDepth_;
    settings_->setTxQualityDepth(streamQualityDepth_);
    emit txSettingsChanged();
}

int AppController::txQualityCapture() const { return streamQualityCapture_; }
void AppController::setTxQualityCapture(int q) {
    streamQualityCapture_ = std::clamp(q, 10, 100);
    settings_->setTxQualityCapture(streamQualityCapture_);
    emit txSettingsChanged();
}

int AppController::txMaxFps() const { return static_cast<int>(1000 / minFrameIntervalMs_); }
void AppController::setTxMaxFps(int fps) {
    int clamped = std::clamp(fps, 5, 60);
    minFrameIntervalMs_ = std::max(static_cast<int64_t>(16), static_cast<int64_t>(1000 / clamped));
    settings_->setTxMaxFps(clamped);
    emit txSettingsChanged();
}

// ── Network sync ─────────────────────────────────────────────────────

bool AppController::syncServerRunning() const { return syncServer_->isRunning(); }
bool AppController::syncClientConnected() const { return syncServer_->hasClient(); }
QString AppController::syncQrPayload() const { return syncServer_->qrPayload(); }

void AppController::startSyncServer(int port) {
    syncServer_->start(port);
    stateTimer_.start();
    // Pre-generate QR code image
    qrCodeImage_ = generateQrCode();
    log("NETWORK", QString("Sync server started on port %1").arg(port));
    emit syncStateChanged();
}

void AppController::stopSyncServer() {
    stateTimer_.stop();
    syncServer_->stop();
    log("NETWORK", "Sync server stopped");
    emit syncStateChanged();
}

QImage AppController::generateQrCode() const {
    QrGenerator gen;
    return gen.generate(syncQrPayload());
}

QImage AppController::qrCodeImage() const {
    return qrCodeImage_;
}

// ── Logs ─────────────────────────────────────────────────────────────

QStringList AppController::logMessages() const { return logBuffer_; }

// ── Private slots ────────────────────────────────────────────────────

void AppController::onDepthChanged(float depthM, float confidence) {
    // Store depth directly so the UI always sees it, regardless of autofocus state
    currentDepth_ = depthM;
    currentConfidence_ = confidence;

    // Forward to autofocus together with the actual measurement coordinate
    // the RealSense used for this reading. AF-C needs this to match the
    // tapped focus point; AF-F follows the face and the controller handles
    // that mode specially.
    float mx = 0.5f, my = 0.5f;
    realsense_->getMeasurementPosition(mx, my);
    autofocus_->processDepthData(depthM, confidence, mx, my);
    emit depthChanged(depthM, confidence);
}

void AppController::onColorFrame(const QImage &frame) {
    colorFrame_ = frame;

    // Run face detection in AF-F mode. We:
    //   1. Run ONNX YOLO face detection on the color frame
    //   2. Pass raw detections through the Kalman-filtered SubjectTracker
    //   3. Cache the result for QML overlay + sync broadcast
    //   4. Retarget the RealSense measurement position at the primary face
    //      — the NEXT depth frame will be sampled at that position and
    //      flow through onDepthChanged → autofocus->processDepthData.
    if (autofocus_->focusMode() == FocusMode::FaceTracking && faceDetector_->isReady()) {
        // Throttle the full AF-F pipeline to 10 Hz. Everything inside
        // this block — ONNX inference, SubjectTracker histograms, per-
        // face depthAt() sampling, primary-face selection, QML overlay
        // rebuild and network broadcast — runs on the UI thread. At the
        // 30 Hz color-stream rate it saturates one core and starves the
        // QML compositor, so the capture-card preview drops below 1 FPS
        // while aggregate CPU looks moderate on a multi-core box (one
        // pinned core is 10 % of total usage).
        //
        // 10 Hz matches cinema-industry face-tracking rates (ARRI HI-5,
        // Preston LR3) — a subject doesn't move meaningfully in 100 ms
        // and the intermediate frames still push colorFrameChanged so
        // the live preview stays smooth.
        const qint64 now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastFaceDetectMs_ < kFaceDetectIntervalMs) {
            emit colorFrameChanged();
            if (streamColor_ && now - lastColorSendMs_ >= minFrameIntervalMs_) {
                lastColorSendMs_ = now;
                sendFrameToClient(kFrameTypeColor, frame, streamQualityColor_,
                                  kOverlayMaxW, kOverlayMaxH);
                logStreamThrottled(colorStreamCount_, kStreamLogInterval, "color", frame);
            }
            return;
        }
        lastFaceDetectMs_ = now;

        faceFrameWidth_ = frame.width();
        faceFrameHeight_ = frame.height();
        auto detections = faceDetector_->detect(frame);
        auto tracked = subjectTracker_->update(
            detections, frame.width(), frame.height(), frame);

        // Sample depth at every tracked face's focus point. This drives
        // depth-gated primary selection below (closest face wins, which is
        // what a cinema operator wants in >90% of shots) and keeps all
        // candidates in sync with the post-rebalance size scoring.
        std::vector<float> faceDepths(tracked.size(), 0.0f);
        for (size_t i = 0; i < tracked.size(); ++i) {
            const QPointF fp = tracked[i].focusPoint();
            faceDepths[i] = realsense_->depthAt(
                static_cast<float>(fp.x()), static_cast<float>(fp.y()));
        }

        // Pick primary face:
        //   1. If the user has manually selected a face and it's still
        //      tracked, that wins.
        //   2. Otherwise prefer the face with the smallest valid depth
        //      (closest to camera), with hysteresis so the primary only
        //      flips when a new candidate is meaningfully closer.
        //   3. If no face has valid depth, fall back to score-based pick
        //      with the existing 1.15x score hysteresis.
        const TrackedFace *primary = nullptr;
        const int selectedId = subjectTracker_->selectedFaceId();
        if (selectedId >= 0) {
            for (const auto &f : tracked) {
                if (f.trackingId == selectedId) { primary = &f; break; }
            }
        }
        if (!primary && !tracked.empty()) {
            int currentIdx = -1;
            for (size_t i = 0; i < tracked.size(); ++i) {
                if (tracked[i].trackingId == lastPrimaryId_) {
                    currentIdx = static_cast<int>(i);
                    break;
                }
            }

            // Phase A: depth-gated closest-face pick.
            int closestIdx = -1;
            float closestDepth = std::numeric_limits<float>::max();
            for (size_t i = 0; i < tracked.size(); ++i) {
                if (faceDepths[i] > 0.2f && faceDepths[i] < 10.0f
                    && faceDepths[i] < closestDepth) {
                    closestDepth = faceDepths[i];
                    closestIdx = static_cast<int>(i);
                }
            }

            if (closestIdx >= 0) {
                // Apply depth hysteresis — keep the current primary unless
                // the new candidate is noticeably closer.
                if (currentIdx >= 0 && currentIdx != closestIdx
                    && faceDepths[currentIdx] > 0.2f
                    && faceDepths[currentIdx] < closestDepth * kDepthHysteresis) {
                    primary = &tracked[currentIdx];
                } else {
                    primary = &tracked[closestIdx];
                }
            } else {
                // Phase B (fallback): no valid depth on any face — use the
                // score-based pick with hysteresis.
                const TrackedFace *topScored = &tracked.front();
                const TrackedFace *lastPrimary =
                    (currentIdx >= 0) ? &tracked[currentIdx] : nullptr;
                if (lastPrimary && topScored->trackingId != lastPrimary->trackingId
                    && topScored->score < lastPrimary->score * kPrimaryHysteresis) {
                    primary = lastPrimary;
                } else {
                    primary = topScored;
                }
            }

            lastPrimaryId_ = primary->trackingId;
        } else if (!primary) {
            lastPrimaryId_ = -1;
        }

        if (primary) {
            const QPointF fp = primary->focusPoint();
            realsense_->setMeasurementPosition(
                static_cast<float>(fp.x()),
                static_cast<float>(fp.y()));
            // Keep focusX_/focusY_ in AutofocusController in sync so any
            // external observer sees the current face position.
            autofocus_->processTap(static_cast<float>(fp.x()),
                                   static_cast<float>(fp.y()));
        }

        lastTrackedFaces_ = std::move(tracked);
        emit trackedFacesChanged();
        broadcastTrackedFaces();
    } else if (!lastTrackedFaces_.empty()) {
        // Leaving face-tracking mode: clear any lingering overlay state.
        lastTrackedFaces_.clear();
        emit trackedFacesChanged();
        broadcastTrackedFaces();
    }

    emit colorFrameChanged();

    // Stream to remote client (throttled). RealSense color is an overlay on
    // the Android client, so clamp to kOverlayMaxW×kOverlayMaxH and use the
    // lower overlay quality.
    if (streamColor_) {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastColorSendMs_ >= minFrameIntervalMs_) {
            lastColorSendMs_ = now;
            sendFrameToClient(kFrameTypeColor, frame, streamQualityColor_,
                              kOverlayMaxW, kOverlayMaxH);
            logStreamThrottled(colorStreamCount_, kStreamLogInterval, "color", frame);
        }
    }
}

void AppController::onDepthFrame(const QImage &frame) {
    depthFrame_ = frame;
    emit depthFrameChanged();

    // Depth colormap is an overlay — aggressive downscale + low quality.
    if (streamDepth_ && !depthEncodeBusy_) {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastDepthSendMs_ >= minFrameIntervalMs_) {
            lastDepthSendMs_ = now;
            sendFrameToClientAsync(kFrameTypeDepth, frame, streamQualityDepth_,
                                   kOverlayMaxW, kOverlayMaxH, depthEncodeBusy_);
            logStreamThrottled(depthStreamCount_, kStreamLogInterval, "depth", frame);
        }
    }
}

void AppController::onCaptureFrame(const QImage &frame) {
    captureFrame_ = frame;
    emit captureFrameChanged();

    // Capture card is the main-view monitoring feed. Keep it at native 1080p
    // (downscaled only for 4K+ sources) with the high-quality JPEG encoder.
    if (streamCapture_ && !captureEncodeBusy_) {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastCaptureSendMs_ >= minFrameIntervalMs_) {
            lastCaptureSendMs_ = now;
            sendFrameToClientAsync(kFrameTypeCapture, frame, streamQualityCapture_,
                                   kCaptureMaxW, kCaptureMaxH, captureEncodeBusy_);
            logStreamThrottled(captureStreamCount_, kCaptureLogInterval, "capture", frame);
        }
    }
}

void AppController::logStreamThrottled(int &counter, int every,
                                       const char *label, const QImage &frame) {
    if (++counter % every == 0) {
        fprintf(stderr, "[STREAM] Sent %d %s frames (%dx%d)\n",
                counter, label, frame.width(), frame.height());
    }
}

void AppController::onTargetPositionChanged(int position) {
    motor_->setPosition(position);
}

void AppController::onSyncMessage(const SyncMessage &message) {
    switch (message.type) {
    case SyncMessageType::MotorCommand: {
        int pos = message.payload["position"].toInt();
        motor_->setPosition(pos);
        log("NETWORK", QString("Remote motor command: %1").arg(pos));
        break;
    }
    case SyncMessageType::ModeChange: {
        // Suppress echoes: when WE broadcast a MODE_CHANGE, the Android
        // client may echo it back after its own 200 ms window. Ignore
        // inbound mode changes that arrive within our suppression period.
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastModeBroadcastMs_ < kSyncEchoSuppressMs) break;

        QString mode = message.payload["mode"].toString();
        int modeInt = 0;
        if (mode == "SINGLE_AUTO") modeInt = 1;
        else if (mode == "CONTINUOUS_AUTO") modeInt = 2;
        else if (mode == "FACE_TRACKING") modeInt = 3;
        // Derive enabled from mode (matches the Android convention:
        // any non-manual mode is "enabled"). Don't use the payload's
        // `enabled` field because Android may send it as false when
        // the user hasn't explicitly toggled AF on the phone side.
        bool enabled = modeInt > 0;
        autofocus_->setFocusModeInt(modeInt);
        autofocus_->setEnabled(enabled);
        emit autofocusStateChanged();
        log("NETWORK", QString("Remote mode change: %1").arg(mode));
        break;
    }
    case SyncMessageType::CalibrationSync: {
        QString action = message.payload["action"].toString();
        if (action == "CLEAR") {
            autofocus_->clearMapping();
            emit autofocusStateChanged();
            saveMappingCache();
            log("NETWORK", "Mapping cleared by remote");
        } else if (action == "PUSH") {
            QJsonObject mappingObj = message.payload["mapping"].toObject();
            if (!mappingObj.isEmpty()) {
                QJsonDocument doc(mappingObj);
                std::string jsonStr = doc.toJson(QJsonDocument::Compact).toStdString();
                auto mapping = AutofocusMapping::fromJson(jsonStr);
                if (mapping) {
                    autofocus_->loadMapping(*mapping);
                    emit autofocusStateChanged();
                    saveMappingCache();
                    log("NETWORK", QString("Loaded mapping from remote sync: %1")
                        .arg(QString::fromStdString(mapping->name())));
                } else {
                    log("NETWORK", "Failed to parse remote mapping data");
                }
            }
        }
        break;
    }
    case SyncMessageType::StreamControl: {
        streamColor_ = message.payload["color"].toBool(false);
        streamDepth_ = message.payload["depth"].toBool(false);
        streamCapture_ = message.payload["capture"].toBool(false);
        updateDepthColormapGate();
        log("NETWORK", QString("Stream control: color=%1 depth=%2 capture=%3")
            .arg(streamColor_).arg(streamDepth_).arg(streamCapture_));
        break;
    }
    case SyncMessageType::MeasurePosition: {
        float x = static_cast<float>(message.payload["x"].toDouble());
        float y = static_cast<float>(message.payload["y"].toDouble());
        realsense_->setMeasurementPosition(x, y);
        emit measurePositionChanged();
        autofocus_->processTap(x, y);
        break;
    }
    case SyncMessageType::SettingsSync: {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastSettingsBroadcastMs_ < kSyncEchoSuppressMs) break;
        auto p = message.payload;
        if (p.contains("confidenceThreshold")) {
            float v = static_cast<float>(p["confidenceThreshold"].toDouble());
            autofocus_->setConfidenceThreshold(v);
            settings_->setConfidenceThreshold(v);
        }
        if (p.contains("smoothingAlpha")) {
            float v = static_cast<float>(p["smoothingAlpha"].toDouble());
            autofocus_->setSmoothingAlpha(v);
            settings_->setSmoothingAlpha(v);
        }
        if (p.contains("motorReverse")) {
            bool v = p["motorReverse"].toBool();
            motor_->setReversed(v);
            settings_->setMotorReverse(v);
        }
        if (p.contains("motorOffset")) {
            int v = p["motorOffset"].toInt();
            motor_->setOffset(v);
            settings_->setMotorOffset(v);
        }
        log("NETWORK", "Settings synced from remote");
        break;
    }
    default:
        break;
    }
}

void AppController::broadcastState() {
    if (!syncServer_->hasClient()) return;

    QString modeStr = "MANUAL";
    switch (autofocus_->focusMode()) {
    case FocusMode::SingleAuto:     modeStr = "SINGLE_AUTO"; break;
    case FocusMode::ContinuousAuto: modeStr = "CONTINUOUS_AUTO"; break;
    case FocusMode::FaceTracking:   modeStr = "FACE_TRACKING"; break;
    default: break;
    }

    // Build state update with measurement position for remote crosshair sync
    auto msg = SyncMessage::stateUpdate(
        motor_->currentPosition(), depth(), depthConfidence(),
        modeStr, autofocusEnabled(), activelyFocusing(), 0,
        motorConnected(), realSenseConnected());
    // Add measurement position and camera state
    float mx = 0.5f, my = 0.5f;
    realsense_->getMeasurementPosition(mx, my);
    msg.payload["measureX"] = static_cast<double>(mx);
    msg.payload["measureY"] = static_cast<double>(my);
    // Add camera connection state to connectionStates
    QJsonObject connStates = msg.payload["connectionStates"].toObject();
    connStates["camera"] = captureCard_->isConnected() ? "Active" : "Disconnected";
    msg.payload["connectionStates"] = connStates;
    syncServer_->broadcast(msg);
}

void AppController::notifyRemoteDeviceStates() {
    if (!syncServer_->hasClient()) return;
    // Force an immediate state broadcast so Android sees the device change
    broadcastState();
}

void AppController::updateDepthColormapGate() {
    // The depth colormap is expensive (~1–5 ms per frame). Only enable it
    // when someone is actually going to consume the output: either the CFG
    // depth preview is visible, or a remote sync client has asked for the
    // depth stream. Otherwise the capture loop skips the whole colorize
    // step.
    const bool wanted = showDepthOverlay_ || streamDepth_;
    realsense_->setColormapEnabled(wanted);
}

void AppController::broadcastModeChange() {
    if (!syncServer_->hasClient()) return;
    lastModeBroadcastMs_ = QDateTime::currentMSecsSinceEpoch();

    QString modeStr = "MANUAL";
    switch (autofocus_->focusMode()) {
    case FocusMode::SingleAuto:     modeStr = "SINGLE_AUTO"; break;
    case FocusMode::ContinuousAuto: modeStr = "CONTINUOUS_AUTO"; break;
    case FocusMode::FaceTracking:   modeStr = "FACE_TRACKING"; break;
    default: break;
    }

    auto msg = SyncMessage::modeChange(modeStr, autofocus_->isEnabled(),
                                       settings_->confidenceThreshold(),
                                       autofocus_->smoothingAlpha() < 1.0f,
                                       static_cast<int>(autofocus_->smoothingAlpha() * 100));
    syncServer_->broadcast(msg);
}

void AppController::broadcastCurrentMapping() {
    if (!syncServer_->hasClient()) return;
    const auto &mapping = autofocus_->currentMapping();
    if (!mapping) return;

    std::string jsonStr = mapping->toJson();
    QJsonDocument doc = QJsonDocument::fromJson(QByteArray::fromStdString(jsonStr));
    if (doc.isNull()) return;

    auto msg = SyncMessage::calibrationSync("PUSH", doc.object());
    syncServer_->broadcast(msg);
    log("NETWORK", QString("Synced mapping to client: %1")
        .arg(QString::fromStdString(mapping->name())));
}

// ── Calibration mapping persistence ────────────────────────────────────
//
// Studio caches the currently-loaded mapping to disk so it can auto-restore
// on the next launch. The cache is written by whatever path produced the
// mapping — local file load, preset, or a remote CalibrationSync/PUSH —
// because every path ultimately calls AutofocusController::loadMapping()
// and those callers invoke saveMappingCache() after a successful load.

QString AppController::mappingCachePath() {
    const QString dir = QStandardPaths::writableLocation(
        QStandardPaths::AppLocalDataLocation);
    if (dir.isEmpty()) return {};
    QDir().mkpath(dir);
    return dir + "/mapping_cache.json";
}

void AppController::saveMappingCache() {
    const QString path = mappingCachePath();
    if (path.isEmpty()) return;
    const auto &mapping = autofocus_->currentMapping();
    if (!mapping) {
        QFile::remove(path); // cache stale → nothing to restore
        return;
    }
    QFile f(path);
    if (!f.open(QIODevice::WriteOnly | QIODevice::Truncate)) return;
    f.write(QByteArray::fromStdString(mapping->toJson()));
}

void AppController::loadMappingCache() {
    const QString path = mappingCachePath();
    if (path.isEmpty() || !QFile::exists(path)) return;
    QFile f(path);
    if (!f.open(QIODevice::ReadOnly)) return;
    const auto data = f.readAll();
    auto mapping = AutofocusMapping::fromJson(data.toStdString());
    if (!mapping) {
        log("AUTOFOCUS", "Cached mapping could not be parsed — ignoring");
        QFile::remove(path);
        return;
    }
    if (autofocus_->loadMapping(*mapping)) {
        log("AUTOFOCUS", QString("Restored cached mapping: %1 (%2 points)")
            .arg(QString::fromStdString(mapping->name()))
            .arg(mapping->points().size()));
    }
}

void AppController::sendFrameToClient(uint8_t frameType, const QImage &frame,
                                      int quality, int maxW, int maxH) {
    if (!syncServer_->hasClient()) return;

    // Synchronous encode path — only used for the small RealSense color
    // overlay, where the encode cost at 640×480 is a couple of milliseconds
    // on the main thread. Capture card and depth colormap use the async
    // path below.
    QByteArray jpegData = encodeFrameToJpeg(frame, maxW, maxH, quality);
    if (jpegData.isEmpty()) return;

    QByteArray msg;
    msg.reserve(5 + jpegData.size());
    msg.append(static_cast<char>(frameType));
    uint32_t ts = static_cast<uint32_t>(QDateTime::currentMSecsSinceEpoch() & 0xFFFFFFFF);
    msg.append(reinterpret_cast<const char*>(&ts), 4);
    msg.append(jpegData);

    syncServer_->sendBinaryMessage(msg);
}

void AppController::sendFrameToClientAsync(uint8_t frameType, const QImage &frame,
                                           int quality, int maxW, int maxH,
                                           std::atomic<bool> &busyFlag) {
    if (!syncServer_->hasClient()) return;

    busyFlag = true;
    QImage copy = frame.copy();
    (void)QtConcurrent::run([this, copy, quality, frameType, maxW, maxH, &busyFlag]() mutable {
        QByteArray jpegData = encodeFrameToJpeg(copy, maxW, maxH, quality);

        if (!jpegData.isEmpty() && syncServer_->hasClient()) {
            QByteArray msg;
            msg.reserve(5 + jpegData.size());
            msg.append(static_cast<char>(frameType));
            uint32_t ts = static_cast<uint32_t>(QDateTime::currentMSecsSinceEpoch() & 0xFFFFFFFF);
            msg.append(reinterpret_cast<const char*>(&ts), 4);
            msg.append(jpegData);

            QMetaObject::invokeMethod(syncServer_.get(), [this, msg]() {
                syncServer_->sendBinaryMessage(msg);
            }, Qt::QueuedConnection);
        }
        busyFlag = false;
    });
}

void AppController::log(const QString &category, const QString &message) {
    QString timestamp = QDateTime::currentDateTime().toString("HH:mm:ss.zzz");
    QString entry = QString("[%1] [%2] %3").arg(timestamp, category, message);

    // Always print to stderr for terminal debugging
    fprintf(stderr, "[%s] [%s] %s\n",
            timestamp.toUtf8().constData(),
            category.toUtf8().constData(),
            message.toUtf8().constData());

    logBuffer_.append(entry);
    if (logBuffer_.size() > kMaxLogEntries) {
        logBuffer_.removeFirst();
    }
    emit logChanged();
}

} // namespace alice
