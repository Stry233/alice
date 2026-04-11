#pragma once

#include <QObject>
#include <QImage>
#include <QStringList>
#include <QTimer>
#include <QVariantList>
#include <memory>
#include <vector>

#include "core/autofocus/SubjectTracker.h"

namespace alice {

class RealSenseManager;
class MotorController;
class CaptureCardManager;
class FaceDetector;
class SubjectTracker;
class AutofocusController;
class DeviceCoordinator;
class SettingsManager;
class SyncServer;

/**
 * Central QML ↔ C++ bridge. Analogous to CameraViewModel in Android.
 * Orchestrates all hardware, autofocus, and network components.
 * Exposed to QML as the "alice" context property.
 */
class AppController : public QObject {
    Q_OBJECT

    // Device state
    Q_PROPERTY(bool motorConnected READ motorConnected NOTIFY deviceStateChanged)
    Q_PROPERTY(bool realSenseConnected READ realSenseConnected NOTIFY deviceStateChanged)
    Q_PROPERTY(bool captureCardConnected READ captureCardConnected NOTIFY deviceStateChanged)
    Q_PROPERTY(int motorPosition READ motorPosition NOTIFY motorPositionChanged)

    // Connection lifecycle timestamps (epoch ms; 0 = never)
    Q_PROPERTY(qint64 motorConnectedSinceMs READ motorConnectedSinceMs NOTIFY deviceStateChanged)
    Q_PROPERTY(qint64 motorLastDisconnectMs READ motorLastDisconnectMs NOTIFY deviceStateChanged)
    Q_PROPERTY(qint64 realSenseConnectedSinceMs READ realSenseConnectedSinceMs NOTIFY deviceStateChanged)
    Q_PROPERTY(qint64 realSenseLastDisconnectMs READ realSenseLastDisconnectMs NOTIFY deviceStateChanged)
    Q_PROPERTY(qint64 captureCardConnectedSinceMs READ captureCardConnectedSinceMs NOTIFY deviceStateChanged)
    Q_PROPERTY(qint64 captureCardLastDisconnectMs READ captureCardLastDisconnectMs NOTIFY deviceStateChanged)

    // Depth
    Q_PROPERTY(float depth READ depth NOTIFY depthChanged)
    Q_PROPERTY(float depthConfidence READ depthConfidence NOTIFY depthChanged)
    Q_PROPERTY(float measureX READ measureX NOTIFY measurePositionChanged)
    Q_PROPERTY(float measureY READ measureY NOTIFY measurePositionChanged)

    // Autofocus
    Q_PROPERTY(bool autofocusEnabled READ autofocusEnabled WRITE setAutofocusEnabled NOTIFY autofocusStateChanged)
    Q_PROPERTY(int focusMode READ focusMode WRITE setFocusMode NOTIFY autofocusStateChanged)
    Q_PROPERTY(bool activelyFocusing READ activelyFocusing NOTIFY autofocusStateChanged)
    Q_PROPERTY(bool hasMapping READ hasMapping NOTIFY autofocusStateChanged)
    Q_PROPERTY(int targetMotorPosition READ targetMotorPosition NOTIFY autofocusStateChanged)
    Q_PROPERTY(QString mappingName READ mappingName NOTIFY autofocusStateChanged)

    // Face tracking (reactive binding for QML overlay)
    Q_PROPERTY(QVariantList trackedFacesList READ trackedFaces NOTIFY trackedFacesChanged)

    // Network
    Q_PROPERTY(bool syncServerRunning READ syncServerRunning NOTIFY syncStateChanged)
    Q_PROPERTY(bool syncClientConnected READ syncClientConnected NOTIFY syncStateChanged)
    Q_PROPERTY(QString syncQrPayload READ syncQrPayload NOTIFY syncStateChanged)
    Q_PROPERTY(QImage qrCodeImage READ qrCodeImage NOTIFY syncStateChanged)

    // UI state
    Q_PROPERTY(bool showDepthOverlay READ showDepthOverlay WRITE setShowDepthOverlay NOTIFY showDepthOverlayChanged)

    // Resolution modes
    Q_PROPERTY(QVariantList realSenseDepthModes READ realSenseDepthModes NOTIFY deviceStateChanged)
    Q_PROPERTY(QVariantList realSenseColorModes READ realSenseColorModes NOTIFY deviceStateChanged)
    Q_PROPERTY(QVariantList captureCardFormats READ captureCardFormats NOTIFY deviceStateChanged)

    // Transmission quality (instant apply)
    Q_PROPERTY(int txQualityDepth READ txQualityDepth WRITE setTxQualityDepth NOTIFY txSettingsChanged)
    Q_PROPERTY(int txQualityCapture READ txQualityCapture WRITE setTxQualityCapture NOTIFY txSettingsChanged)
    Q_PROPERTY(int txMaxFps READ txMaxFps WRITE setTxMaxFps NOTIFY txSettingsChanged)

    // Logs
    Q_PROPERTY(QStringList logMessages READ logMessages NOTIFY logChanged)

    // Video frames (exposed as QImage providers)
    Q_PROPERTY(QImage colorFrame READ colorFrame NOTIFY colorFrameChanged)
    Q_PROPERTY(QImage depthFrame READ depthFrame NOTIFY depthFrameChanged)
    Q_PROPERTY(QImage captureFrame READ captureFrame NOTIFY captureFrameChanged)

public:
    explicit AppController(QObject *parent = nullptr);
    ~AppController() override;

    /** Initialize hardware and networking. Call after QML is loaded. */
    Q_INVOKABLE void initialize();

    // ── Device state ─────────────────────────────────────────────────
    bool motorConnected() const;
    bool realSenseConnected() const;
    bool captureCardConnected() const;
    int motorPosition() const;

    qint64 motorConnectedSinceMs() const;
    qint64 motorLastDisconnectMs() const;
    qint64 realSenseConnectedSinceMs() const;
    qint64 realSenseLastDisconnectMs() const;
    qint64 captureCardConnectedSinceMs() const;
    qint64 captureCardLastDisconnectMs() const;

    // ── User-initiated device control ────────────────────────────────
    Q_INVOKABLE void restartMotor();
    Q_INVOKABLE void disconnectMotor();
    Q_INVOKABLE void reconnectMotor();

    Q_INVOKABLE void restartDepth();
    Q_INVOKABLE void disconnectDepth();
    Q_INVOKABLE void reconnectDepth();

    Q_INVOKABLE void restartCam();
    Q_INVOKABLE void disconnectCam();
    Q_INVOKABLE void reconnectCam();

    // ── Depth ────────────────────────────────────────────────────────
    float depth() const;
    float depthConfidence() const;
    float measureX() const;
    float measureY() const;

    // ── Autofocus ────────────────────────────────────────────────────
    bool autofocusEnabled() const;
    void setAutofocusEnabled(bool enabled);
    int focusMode() const;
    void setFocusMode(int mode);
    bool activelyFocusing() const;
    bool hasMapping() const;
    int targetMotorPosition() const;

    QString mappingName() const;
    Q_INVOKABLE QVariantList mappingPoints() const;
    Q_INVOKABLE void loadMappingFromFile(const QString &path);
    Q_INVOKABLE void loadPreset(int presetIndex);
    Q_INVOKABLE void clearMapping();

    // ── Motor control ────────────────────────────────────────────────
    Q_INVOKABLE void setMotorPosition(int position);
    Q_INVOKABLE void setMotorDestination(int address);
    Q_INVOKABLE void scanMotorAddress(int address);

    // ── Depth measurement ────────────────────────────────────────────
    Q_INVOKABLE void setMeasurementPosition(float x, float y);
    Q_INVOKABLE void processTap(float x, float y);

    // ── Face tracking ────────────────────────────────────────────────
    Q_INVOKABLE void selectFace(int trackingId);
    Q_INVOKABLE QVariantList trackedFaces() const;
    // Resolution of the frame the last detection ran against (for QML to
    // map pixel-space bboxes into preview coordinates). Returns 0,0 until
    // the first color frame arrives.
    Q_INVOKABLE int faceFrameWidth() const { return faceFrameWidth_; }
    Q_INVOKABLE int faceFrameHeight() const { return faceFrameHeight_; }

    // ── Resolution / transmission quality ─────────────────────────────
    QVariantList realSenseDepthModes() const;
    QVariantList realSenseColorModes() const;
    QVariantList captureCardFormats() const;

    Q_INVOKABLE void setRealSenseResolution(int depthW, int depthH, int depthFps, int colorW, int colorH, int colorFps);
    Q_INVOKABLE void setCaptureCardResolution(int w, int h, int fps);

    int txQualityDepth() const;
    void setTxQualityDepth(int q);
    int txQualityCapture() const;
    void setTxQualityCapture(int q);
    int txMaxFps() const;
    void setTxMaxFps(int fps);

    // ── Network sync ─────────────────────────────────────────────────
    bool syncServerRunning() const;
    bool syncClientConnected() const;
    QString syncQrPayload() const;
    Q_INVOKABLE void startSyncServer(int port = 8765);
    Q_INVOKABLE void stopSyncServer();
    Q_INVOKABLE QImage generateQrCode() const;
    QImage qrCodeImage() const;

    // ── Logs ─────────────────────────────────────────────────────────
    QStringList logMessages() const;

    // ── Video frames ─────────────────────────────────────────────────
    QImage colorFrame() const { return colorFrame_; }
    QImage depthFrame() const { return depthFrame_; }
    QImage captureFrame() const { return captureFrame_; }

    // ── UI state ─────────────────────────────────────────────────────
    bool showDepthOverlay() const { return showDepthOverlay_; }
    void setShowDepthOverlay(bool v) {
        if (showDepthOverlay_ != v) {
            showDepthOverlay_ = v;
            updateDepthColormapGate();
            emit showDepthOverlayChanged();
        }
    }

signals:
    void showDepthOverlayChanged();
    void deviceStateChanged();
    void motorPositionChanged(int position);
    void depthChanged(float depth, float confidence);
    void measurePositionChanged();
    void autofocusStateChanged();
    void syncStateChanged();
    void logChanged();
    void colorFrameChanged();
    void depthFrameChanged();
    void captureFrameChanged();
    void txSettingsChanged();
    void trackedFacesChanged();

private slots:
    void onDepthChanged(float depth, float confidence);
    void onColorFrame(const QImage &frame);
    void onDepthFrame(const QImage &frame);
    void onCaptureFrame(const QImage &frame);
    void onTargetPositionChanged(int position);
    void onSyncMessage(const struct SyncMessage &message);
    void broadcastState();

private:
    void log(const QString &category, const QString &message);
    void notifyRemoteDeviceStates();
    void broadcastModeChange();
    void broadcastCurrentMapping();
    void broadcastTrackedFaces();
    void updateDepthColormapGate();

    // Core components
    std::unique_ptr<RealSenseManager> realsense_;
    std::unique_ptr<CaptureCardManager> captureCard_;
    std::unique_ptr<MotorController> motor_;
    std::unique_ptr<FaceDetector> faceDetector_;
    std::unique_ptr<SubjectTracker> subjectTracker_;
    std::unique_ptr<AutofocusController> autofocus_;
    std::unique_ptr<DeviceCoordinator> coordinator_;
    std::unique_ptr<SettingsManager> settings_;
    std::unique_ptr<SyncServer> syncServer_;

    // State broadcast timer
    QTimer stateTimer_;

    // Log buffer (circular, 200 entries)
    QStringList logBuffer_;
    static constexpr int kMaxLogEntries = 200;

    // Frame cache
    QImage colorFrame_;
    QImage depthFrame_;
    QImage captureFrame_;
    QImage qrCodeImage_;

    // Depth readings (stored directly from RealSense, independent of autofocus state)
    float currentDepth_ = 0.0f;
    float currentConfidence_ = 0.0f;

    // Cached tracked faces from the most recent detection pass (also pushed
    // to the sync peer for cross-device overlay).
    std::vector<TrackedFace> lastTrackedFaces_;
    int faceFrameWidth_ = 0;
    int faceFrameHeight_ = 0;
    qint64 lastFaceBroadcastMs_ = 0;
    // Auto-selected primary face id for hysteresis — an incoming detection
    // only steals primary from the current one if its score beats this
    // value times the hysteresis multiplier (or gets closer in depth by
    // the depth hysteresis multiplier).
    int lastPrimaryId_ = -1;
    static constexpr float kPrimaryHysteresis = 1.15f;
    static constexpr float kDepthHysteresis   = 1.10f;

    // UI state
    bool showDepthOverlay_ = false;

    // Stream control state
    bool streamColor_ = false;
    bool streamDepth_ = false;
    bool streamCapture_ = false;

    // Configurable JPEG quality (0-100)
    int streamQualityDepth_ = 85;
    int streamQualityColor_ = 85;
    int streamQualityCapture_ = 80;

    // Frame type constants
    static constexpr uint8_t kFrameTypeColor = 0x01;
    static constexpr uint8_t kFrameTypeDepth = 0x02;
    static constexpr uint8_t kFrameTypeCapture = 0x03;

    // Frame throttling: track last send time per stream
    int64_t lastColorSendMs_ = 0;
    int64_t lastDepthSendMs_ = 0;
    int64_t lastCaptureSendMs_ = 0;
    int64_t minFrameIntervalMs_ = 33; // ~30fps max per stream

    // Busy flags: skip frames while a previous encode for the same stream
    // is still in flight, so we never pile up work on the thread pool.
    std::atomic<bool> captureEncodeBusy_{false};
    std::atomic<bool> depthEncodeBusy_{false};

    void sendFrameToClient(uint8_t frameType, const QImage &frame, int quality);
    void sendFrameToClientAsync(uint8_t frameType, const QImage &frame, int quality,
                                std::atomic<bool> &busyFlag);
};

} // namespace alice
