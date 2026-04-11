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

    // Reconnect motor if disconnected (unless user disconnected it)
    if (!motorConnected_ && !motorManualDc_) {
        motor_->connectDevice();
    }

    // Restart RealSense if disconnected (unless user disconnected it)
    if (!realSenseConnected_ && !realSenseManualDc_) {
        realsense_->start();
    }

    // Restart capture card if disconnected (unless user disconnected it)
    if (!captureCardConnected_ && !captureCardManualDc_) {
        captureCard_->start();
    }
}

// ── User-initiated device control ───────────────────────────────────

void DeviceCoordinator::disconnectMotor() {
    motorManualDc_ = true;
    motor_->disconnectDevice();
}

void DeviceCoordinator::reconnectMotor() {
    motorManualDc_ = false;
    motor_->connectDevice();
}

void DeviceCoordinator::restartMotor() {
    motorManualDc_ = false;
    motor_->disconnectDevice();
    QTimer::singleShot(kRestartDelayMs, this, [this]() {
        if (!motorManualDc_) motor_->connectDevice();
    });
}

void DeviceCoordinator::disconnectRealSense() {
    realSenseManualDc_ = true;
    realsense_->stop();
}

void DeviceCoordinator::reconnectRealSense() {
    realSenseManualDc_ = false;
    realsense_->start();
}

void DeviceCoordinator::restartRealSense() {
    realSenseManualDc_ = false;
    realsense_->stop();
    QTimer::singleShot(kRestartDelayMs, this, [this]() {
        if (!realSenseManualDc_) realsense_->start();
    });
}

void DeviceCoordinator::disconnectCaptureCard() {
    captureCardManualDc_ = true;
    captureCard_->stop();
}

void DeviceCoordinator::reconnectCaptureCard() {
    captureCardManualDc_ = false;
    captureCard_->start();
}

void DeviceCoordinator::restartCaptureCard() {
    captureCardManualDc_ = false;
    captureCard_->stop();
    QTimer::singleShot(kRestartDelayMs, this, [this]() {
        if (!captureCardManualDc_) captureCard_->start();
    });
}

} // namespace alice
