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
#include <QCoreApplication>
#include <QDateTime>
#include <QDir>
#include <QFileInfo>
#include <QtConcurrent/QtConcurrent>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcessEnvironment>
#include <QUrl>
#include <QVariantMap>
#include <limits>

namespace alice {

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
    motor_->setSmoothingEnabled(settings_->smoothingEnabled());

    // Apply settings to autofocus
    autofocus_->setConfidenceThreshold(settings_->confidenceThreshold());
    autofocus_->setSmoothingEnabled(settings_->smoothingEnabled());
    autofocus_->setResponseSpeed(settings_->responseSpeed());

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

// ── Depth ────────────────────────────────────────────────────────────

float AppController::depth() const { return currentDepth_; }
float AppController::depthConfidence() const { return currentConfidence_; }
float AppController::measureX() const { float x=0.5f, y=0.5f; realsense_->getMeasurementPosition(x, y); return x; }
float AppController::measureY() const { float x=0.5f, y=0.5f; realsense_->getMeasurementPosition(x, y); return y; }

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
    } else {
        log("AUTOFOCUS", QString("ERROR: Failed to load mapping from %1").arg(localPath));
    }
}

void AppController::loadPreset(int presetIndex) {
    autofocus_->loadPreset(static_cast<MappingPreset>(presetIndex));
    log("AUTOFOCUS", "Preset loaded");
    broadcastCurrentMapping();
}

void AppController::clearMapping() {
    autofocus_->clearMapping();
    log("AUTOFOCUS", "Mapping cleared");
    if (syncServer_->hasClient()) {
        auto msg = SyncMessage::calibrationSync("CLEAR");
        syncServer_->broadcast(msg);
    }
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

void AppController::processTap(float x, float y) {
    setMeasurementPosition(x, y);
    autofocus_->processTap(x, y);
}

// ── Face tracking ────────────────────────────────────────────────────

void AppController::selectFace(int trackingId) {
    subjectTracker_->selectFace(trackingId);
    // Broadcast right away so the sync peer picks up the new selection
    // without waiting for the next detection cycle.
    broadcastTrackedFaces();
}

QVariantList AppController::trackedFaces() const {
    QVariantList out;
    out.reserve(static_cast<int>(lastTrackedFaces_.size()));
    const int selectedId = subjectTracker_->selectedFaceId();
    const double invW = (faceFrameWidth_  > 0) ? 1.0 / faceFrameWidth_  : 1.0;
    const double invH = (faceFrameHeight_ > 0) ? 1.0 / faceFrameHeight_ : 1.0;

    for (const auto &face : lastTrackedFaces_) {
        QVariantMap m;
        m["id"] = face.trackingId;
        // Normalised bbox so QML can lay it out over any preview size.
        m["x"] = face.boundingBox.x() * invW;
        m["y"] = face.boundingBox.y() * invH;
        m["w"] = face.boundingBox.width()  * invW;
        m["h"] = face.boundingBox.height() * invH;
        m["centerX"] = face.center.x();
        m["centerY"] = face.center.y();
        m["confidence"] = face.confidence;
        m["selected"] = (face.trackingId == selectedId);
        m["state"] = static_cast<int>(face.state);
        m["color"] = face.color.name();
        if (face.leftEye)  { m["leftEyeX"]  = face.leftEye->x();  m["leftEyeY"]  = face.leftEye->y(); }
        if (face.rightEye) { m["rightEyeX"] = face.rightEye->x(); m["rightEyeY"] = face.rightEye->y(); }
        out.append(m);
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

    const double invW = (faceFrameWidth_  > 0) ? 1.0 / faceFrameWidth_  : 1.0;
    const double invH = (faceFrameHeight_ > 0) ? 1.0 / faceFrameHeight_ : 1.0;

    QJsonArray faces;
    for (const auto &face : lastTrackedFaces_) {
        QJsonObject o;
        o["id"] = face.trackingId;
        o["x"] = face.boundingBox.x() * invW;
        o["y"] = face.boundingBox.y() * invH;
        o["w"] = face.boundingBox.width()  * invW;
        o["h"] = face.boundingBox.height() * invH;
        o["centerX"] = face.center.x();
        o["centerY"] = face.center.y();
        o["confidence"] = static_cast<double>(face.confidence);
        o["state"] = static_cast<int>(face.state);
        if (face.leftEye) {
            o["leftEyeX"] = face.leftEye->x();
            o["leftEyeY"] = face.leftEye->y();
        }
        if (face.rightEye) {
            o["rightEyeX"] = face.rightEye->x();
            o["rightEyeY"] = face.rightEye->y();
        }
        faces.append(o);
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

    // Stream to remote client (throttled)
    static int colorStreamCount = 0;
    if (streamColor_) {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastColorSendMs_ >= minFrameIntervalMs_) {
            lastColorSendMs_ = now;
            sendFrameToClient(kFrameTypeColor, frame, streamQualityColor_);
            if (++colorStreamCount % 60 == 0) {
                fprintf(stderr, "[STREAM] Sent %d color frames (%dx%d)\n",
                        colorStreamCount, frame.width(), frame.height());
            }
        }
    }
}

void AppController::onDepthFrame(const QImage &frame) {
    depthFrame_ = frame;
    emit depthFrameChanged();

    static int depthStreamCount = 0;
    if (streamDepth_ && !depthEncodeBusy_) {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastDepthSendMs_ >= minFrameIntervalMs_) {
            lastDepthSendMs_ = now;
            sendFrameToClientAsync(kFrameTypeDepth, frame, streamQualityDepth_, depthEncodeBusy_);
            if (++depthStreamCount % 60 == 0) {
                fprintf(stderr, "[STREAM] Sent %d depth frames (%dx%d)\n",
                        depthStreamCount, frame.width(), frame.height());
            }
        }
    }
}

void AppController::onCaptureFrame(const QImage &frame) {
    captureFrame_ = frame;
    emit captureFrameChanged();

    static int captureStreamCount = 0;
    if (streamCapture_ && !captureEncodeBusy_) {
        auto now = QDateTime::currentMSecsSinceEpoch();
        if (now - lastCaptureSendMs_ >= minFrameIntervalMs_) {
            lastCaptureSendMs_ = now;
            captureEncodeBusy_ = true;

            // Encode JPEG off the main thread to avoid blocking the event loop
            QImage copy = frame.copy();
            int quality = streamQualityCapture_;
            QtConcurrent::run([this, copy, quality]() {
                QImage scaled = copy;
                if (copy.width() > 960) {
                    scaled = copy.scaled(960, 540, Qt::KeepAspectRatio, Qt::FastTransformation);
                }

                QByteArray jpegData;
                QBuffer buffer(&jpegData);
                buffer.open(QIODevice::WriteOnly);
                scaled.save(&buffer, "JPEG", quality);
                buffer.close();

                if (!jpegData.isEmpty() && syncServer_->hasClient()) {
                    QByteArray msg;
                    msg.reserve(5 + jpegData.size());
                    msg.append(static_cast<char>(kFrameTypeCapture));
                    uint32_t ts = static_cast<uint32_t>(QDateTime::currentMSecsSinceEpoch() & 0xFFFFFFFF);
                    msg.append(reinterpret_cast<const char*>(&ts), 4);
                    msg.append(jpegData);

                    QMetaObject::invokeMethod(syncServer_.get(), [this, msg]() {
                        syncServer_->sendBinaryMessage(msg);
                    }, Qt::QueuedConnection);

                    if (++captureStreamCount % 30 == 0) {
                        fprintf(stderr, "[STREAM] Sent %d capture frames (jpeg=%d bytes)\n",
                                captureStreamCount, (int)jpegData.size());
                    }
                }
                captureEncodeBusy_ = false;
            });
        }
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
        QString mode = message.payload["mode"].toString();
        bool enabled = message.payload["enabled"].toBool();
        int modeInt = 0;
        if (mode == "SINGLE_AUTO") modeInt = 1;
        else if (mode == "CONTINUOUS_AUTO") modeInt = 2;
        else if (mode == "FACE_TRACKING") modeInt = 3;
        // Apply directly to controller — don't call setFocusMode/setAutofocusEnabled
        // which would re-broadcast and create an infinite loop
        autofocus_->setFocusModeInt(modeInt);
        autofocus_->setEnabled(enabled);
        emit autofocusStateChanged();
        log("NETWORK", QString("Remote mode change: %1, enabled=%2").arg(mode).arg(enabled));
        break;
    }
    case SyncMessageType::CalibrationSync: {
        QString action = message.payload["action"].toString();
        if (action == "CLEAR") {
            autofocus_->clearMapping();
            emit autofocusStateChanged();
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
        // Remote client is moving the crosshair — apply and let processTap
        // handle autofocus mode dispatch. Pass the `remote` flag so
        // setMeasurementPosition does not echo the update back to sender.
        float x = static_cast<float>(message.payload["x"].toDouble());
        float y = static_cast<float>(message.payload["y"].toDouble());
        realsense_->setMeasurementPosition(x, y);
        emit measurePositionChanged();
        autofocus_->processTap(x, y);
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

    QString modeStr = "MANUAL";
    switch (autofocus_->focusMode()) {
    case FocusMode::SingleAuto:     modeStr = "SINGLE_AUTO"; break;
    case FocusMode::ContinuousAuto: modeStr = "CONTINUOUS_AUTO"; break;
    case FocusMode::FaceTracking:   modeStr = "FACE_TRACKING"; break;
    default: break;
    }

    auto msg = SyncMessage::modeChange(modeStr, autofocus_->isEnabled(), 0.7f, true, 50);
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

void AppController::sendFrameToClient(uint8_t frameType, const QImage &frame, int quality) {
    if (!syncServer_->hasClient()) return;

    // Small frames (color overlay) — synchronous encoding is fast enough at
    // -O3. Capture card frames and depth colormap use the async path below
    // to keep the main event loop responsive.
    QImage scaled = frame;
    if (frame.width() > 960) {
        scaled = frame.scaled(960, 540, Qt::KeepAspectRatio, Qt::FastTransformation);
    }

    QByteArray jpegData;
    QBuffer buffer(&jpegData);
    buffer.open(QIODevice::WriteOnly);
    scaled.save(&buffer, "JPEG", quality);
    buffer.close();

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
                                           int quality, std::atomic<bool> &busyFlag) {
    if (!syncServer_->hasClient()) return;

    busyFlag = true;
    QImage copy = frame.copy();
    (void)QtConcurrent::run([this, copy, quality, frameType, &busyFlag]() mutable {
        QImage scaled = copy;
        if (copy.width() > 960) {
            scaled = copy.scaled(960, 540, Qt::KeepAspectRatio, Qt::FastTransformation);
        }

        QByteArray jpegData;
        QBuffer buffer(&jpegData);
        buffer.open(QIODevice::WriteOnly);
        scaled.save(&buffer, "JPEG", quality);
        buffer.close();

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
