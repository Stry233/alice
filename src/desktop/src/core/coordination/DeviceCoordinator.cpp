#include "core/coordination/DeviceCoordinator.h"
#include "core/motor/MotorController.h"
#include "core/realsense/RealSenseManager.h"
#include "core/camera/CaptureCardManager.h"

#include <QTimer>

namespace alice {

DeviceCoordinator::DeviceCoordinator(MotorController *motor, RealSenseManager *realsense,
                                     CaptureCardManager *captureCard,
                                     QObject *parent)
    : QObject(parent)
    , motor_(motor)
    , realsense_(realsense)
    , captureCard_(captureCard)
{
    connect(motor_, &MotorController::connectionChanged,
            this, &DeviceCoordinator::onMotorConnectionChanged);
    connect(realsense_, &RealSenseManager::connectionChanged,
            this, &DeviceCoordinator::onRealSenseConnectionChanged);
    connect(captureCard_, &CaptureCardManager::connectionChanged,
            this, &DeviceCoordinator::onCaptureCardConnectionChanged);

    healthCheckTimer_.setInterval(kHealthCheckIntervalMs);
    connect(&healthCheckTimer_, &QTimer::timeout, this, &DeviceCoordinator::healthCheck);
}

void DeviceCoordinator::start() {
    if (started_) return;
    started_ = true;

    // Step 1: Connect motor (lowest bandwidth)
    motor_->connectDevice();

    // After delay, start RealSense and capture card regardless of motor result
    QTimer::singleShot(kPostConnectionDelayMs, this, [this]() {
        realsense_->start();
        captureCard_->start();
    });

    healthCheckTimer_.start();
}

void DeviceCoordinator::stop() {
    started_ = false;
    healthCheckTimer_.stop();
    motor_->disconnectDevice();
    realsense_->stop();
    captureCard_->stop();
}

void DeviceCoordinator::onMotorConnectionChanged(bool connected) {
    motorConnected_ = connected;
    emit motorConnectionChanged(connected);

    if (motorConnected_ && realSenseConnected_) {
        emit allDevicesReady();
    }
}

void DeviceCoordinator::onRealSenseConnectionChanged(bool connected) {
    realSenseConnected_ = connected;
    emit realSenseConnectionChanged(connected);

    if (motorConnected_ && realSenseConnected_) {
        emit allDevicesReady();
    }
}

void DeviceCoordinator::onCaptureCardConnectionChanged(bool connected) {
    captureCardConnected_ = connected;
    emit captureCardConnectionChanged(connected);
}

void DeviceCoordinator::healthCheck() {
    if (!started_) return;

    // Reconnect motor if disconnected
    if (!motorConnected_) {
        motor_->connectDevice();
    }

    // Restart RealSense if disconnected
    if (!realSenseConnected_) {
        realsense_->start();
    }

    // Restart capture card if disconnected
    if (!captureCardConnected_) {
        captureCard_->start();
    }
}

} // namespace alice
