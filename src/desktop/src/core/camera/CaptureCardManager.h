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

    Q_INVOKABLE QStringList availableDevices() const;
    Q_INVOKABLE QVariantList availableFormats() const;
    void setCameraResolution(int width, int height, int fps);

public slots:
    void start();
    void startDevice(const QString &deviceName);
    void stop();

signals:
    void connectionChanged(bool connected);
    void frameReady(const QImage &frame);
    void error(const QString &message);

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

    // Configurable resolution (0 = use device default)
    int requestedWidth_ = 0;
    int requestedHeight_ = 0;
    int requestedFps_ = 0;

    // Frame timeout and failure detection
    QTimer frameTimeoutTimer_;
    QElapsedTimer lastFrameTime_;
    int consecutiveFailures_ = 0;
    static constexpr int kFrameTimeoutMs = 3000;
};

} // namespace alice
