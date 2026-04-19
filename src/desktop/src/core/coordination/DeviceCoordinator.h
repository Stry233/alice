#pragma once

#include <QObject>
#include <QTimer>

namespace alice {

class RealSenseManager;
class MotorController;
class CaptureCardManager;

/**
 * Coordinates sequential device discovery and connection.
 * Ported from DeviceCoordinationManager.kt.
 *
 * Connection order: Motor → RealSense (matching Android for consistency).
 */
class DeviceCoordinator : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool motorConnected READ isMotorConnected NOTIFY motorConnectionChanged)
    Q_PROPERTY(bool realSenseConnected READ isRealSenseConnected NOTIFY realSenseConnectionChanged)
    Q_PROPERTY(bool captureCardConnected READ isCaptureCardConnected NOTIFY captureCardConnectionChanged)

public:
    static constexpr int kConnectionTimeoutMs = 5000;
    static constexpr int kPostConnectionDelayMs = 500;
    static constexpr int kHealthCheckIntervalMs = 5000;

    explicit DeviceCoordinator(MotorController *motor, RealSenseManager *realsense,
                               CaptureCardManager *captureCard,
                               QObject *parent = nullptr);

    bool isMotorConnected() const { return motorConnected_; }
    bool isRealSenseConnected() const { return realSenseConnected_; }
    bool isCaptureCardConnected() const { return captureCardConnected_; }

public slots:
    /** Begin sequential device discovery and connection. */
    void start();

    /** Stop all devices. */
    void stop();

signals:
    void motorConnectionChanged(bool connected);
    void realSenseConnectionChanged(bool connected);
    void captureCardConnectionChanged(bool connected);
    void allDevicesReady();
    void error(const QString &message);

private slots:
    void onMotorConnectionChanged(bool connected);
    void onRealSenseConnectionChanged(bool connected);
    void onCaptureCardConnectionChanged(bool connected);
    void healthCheck();

private:
    MotorController *motor_;
    RealSenseManager *realsense_;
    CaptureCardManager *captureCard_;
    QTimer healthCheckTimer_;

    bool motorConnected_ = false;
    bool realSenseConnected_ = false;
    bool captureCardConnected_ = false;
    bool started_ = false;
};

} // namespace alice
