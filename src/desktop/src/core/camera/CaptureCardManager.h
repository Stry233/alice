#pragma once

#include <QObject>
#include <QCamera>
#include <QMediaCaptureSession>
#include <QVideoSink>
#include <QVideoFrame>
#include <QImage>
#include <QMediaDevices>
#include <QVariantList>
#include <QVariantMap>
#include <QTimer>
#include <QElapsedTimer>
#include <memory>

namespace alice {

/**
 * Capture card manager using Qt Multimedia.
 * Discovers and streams from V4L2 video devices (e.g., GENKI ShadowCast, Elgato).
 * Provides the RGB feed from the cinema camera (BMPCC, etc.) via HDMI capture.
 */
class CaptureCardManager : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool connected READ isConnected NOTIFY connectionChanged)
    Q_PROPERTY(qint64 connectedSinceMs READ connectedSinceMs NOTIFY connectionChanged)
    Q_PROPERTY(qint64 lastDisconnectMs READ lastDisconnectMs NOTIFY connectionChanged)

public:
    explicit CaptureCardManager(QObject *parent = nullptr);
    ~CaptureCardManager() override;

    bool isConnected() const { return connected_; }
    qint64 connectedSinceMs() const { return connectedSinceMs_; }
    qint64 lastDisconnectMs() const { return lastDisconnectMs_; }
    /** Human-readable name of the currently-selected capture device. */
    QString deviceDescription() const { return deviceDescription_; }

    /**
     * Enumerate video-input devices the OS sees, minus anything that
     * looks like a RealSense RGB stream (those are managed separately).
     * Each entry is a QVariantMap with:
     *   "id"     — QString, stable QCameraDevice::id() (works across
     *              replug on the same host)
     *   "name"   — QString, human-readable description
     *   "active" — bool, true if this entry is the live capture source
     *
     * Kept as Q_INVOKABLE so QML can bind to it for the device selector
     * in the badge popover.
     */
    Q_INVOKABLE QVariantList availableDevices() const;
    Q_INVOKABLE QVariantList availableFormats() const;
    void setCameraResolution(int width, int height, int fps);

    /**
     * Persist a preferred capture-device id (QCameraDevice::id()). On
     * the next discovery tick the coordinator will auto-switch to it if
     * present; if it vanishes we fall back to the first non-RealSense
     * input. Pass an empty string to clear the preference.
     */
    Q_INVOKABLE void selectDevice(const QString &id);
    QString preferredDeviceId() const { return preferredDeviceId_; }

public slots:
    void start();
    void startDevice(const QString &deviceName);
    void stop();

signals:
    void connectionChanged(bool connected);
    void frameReady(const QImage &frame);
    void error(const QString &message);
    /** Enumeration or active-device changed — UI re-reads the list. */
    void availableDevicesChanged();

private slots:
    void onFrameChanged();
    void checkFrameTimeout();

private:
    void disconnectDevice();
    QCameraDevice findCaptureCard() const;

    std::unique_ptr<QCamera> camera_;
    std::unique_ptr<QMediaCaptureSession> session_;
    std::unique_ptr<QVideoSink> sink_;
    bool connected_ = false;

    // Connection lifecycle timestamps (epoch ms; 0 = never)
    qint64 connectedSinceMs_ = 0;
    qint64 lastDisconnectMs_ = 0;

    // Cached device description + stable id of the currently-open
    // capture source — used for the popover and for the "active" flag
    // in availableDevices().
    QString deviceDescription_;
    QString activeDeviceId_;

    // User preferred QCameraDevice::id(). Preferred over auto-pick when
    // present; persisted via SettingsManager.
    QString preferredDeviceId_;

    // Configurable resolution (0 = use device default)
    int requestedWidth_ = 0;
    int requestedHeight_ = 0;
    int requestedFps_ = 0;

    // Frame timeout and failure detection
    QTimer frameTimeoutTimer_;
    QElapsedTimer lastFrameTime_;
    int consecutiveFailures_ = 0;
    static constexpr int kFrameTimeoutMs = 3000;

    // One-shot diagnostic so we log the capture card's source colour space
    // exactly once per connection, not 30 times a second.
    bool loggedFrameInfo_ = false;
};

} // namespace alice
