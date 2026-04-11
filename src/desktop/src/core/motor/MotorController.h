#pragma once

#include <QObject>
#include <QSerialPort>
#include <QSerialPortInfo>
#include <QTimer>
#include <QString>
#include <memory>
#include <atomic>

#include "core/motor/MotorProtocol.h"

namespace alice {

/**
 * Motor controller for CDC-ACM serial communication with nRF52840 dongle.
 * Ported from MotorControlManager.kt + MotorSerialHandler.kt.
 */
class MotorController : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool connected READ isConnected NOTIFY connectionChanged)
    Q_PROPERTY(int position READ currentPosition NOTIFY positionChanged)
    Q_PROPERTY(int destinationAddress READ destinationAddress NOTIFY destinationChanged)
    Q_PROPERTY(qint64 connectedSinceMs READ connectedSinceMs NOTIFY connectionChanged)
    Q_PROPERTY(qint64 lastDisconnectMs READ lastDisconnectMs NOTIFY connectionChanged)

public:
    // Known USB identifiers for nRF52840 dongle
    static constexpr quint16 kVendorId  = 0x2FE3;
    static constexpr quint16 kProductId = 0x0100;

    explicit MotorController(QObject *parent = nullptr);
    ~MotorController() override;

    bool isConnected() const { return connected_; }
    int currentPosition() const { return position_; }
    int destinationAddress() const { return destAddress_; }
    qint64 connectedSinceMs() const { return connectedSinceMs_.load(); }
    qint64 lastDisconnectMs() const { return lastDisconnectMs_.load(); }
    /** Human-readable name of the detected serial device, if any. */
    QString deviceDescription() const { return deviceDescription_; }
    /** Short bus identifier (e.g. "/dev/ttyACM0") of the active port, if any. */
    QString devicePortName() const { return devicePortName_; }

public slots:
    /** Discover and connect to the motor dongle. */
    void connectDevice();

    /** Disconnect from the motor dongle. */
    void disconnectDevice();

    /**
     * Set motor position (0–4095).
     * Applies smoothing if enabled.
     */
    void setPosition(int position);

    /** Send a raw command string. */
    void sendCommand(const QString &command);

    /** Set destination address (0x0000–0xFFFF). */
    void setDestinationAddress(int address);

    /** Scan/test a specific motor address. */
    void scanAddress(int address);

    /** Query current status. */
    void queryStatus();

    /** Enable/disable exponential smoothing (alpha=0.2). */
    void setSmoothingEnabled(bool enabled) { smoothingEnabled_ = enabled; }

    /** Set motor position offset (applied before sending). */
    void setOffset(int offset) { offset_ = offset; }

    /** Set reverse direction. */
    void setReversed(bool reversed) { reversed_ = reversed; }

signals:
    void connectionChanged(bool connected);
    void positionChanged(int position);
    void destinationChanged(int address);
    void responseReceived(const QString &response);
    void error(const QString &message);
    void ready();

private slots:
    void onReadyRead();
    void onSerialError(QSerialPort::SerialPortError err);

private:
    bool openPort(const QSerialPortInfo &portInfo);
    void processLine(const QString &line);
    int applyTransform(int position) const;

    std::unique_ptr<QSerialPort> serial_;
    QTimer reconnectTimer_;
    QString lineBuffer_;

    std::atomic<bool> connected_{false};
    std::atomic<int> position_{0};
    int destAddress_ = 0xFFFF;

    // Populated when openPort succeeds; cleared on disconnect. Used by the
    // UI to show the actual detected device name in the Motor popover.
    QString deviceDescription_;
    QString devicePortName_;

    // Connection lifecycle timestamps (epoch ms; 0 = never)
    std::atomic<qint64> connectedSinceMs_{0};
    std::atomic<qint64> lastDisconnectMs_{0};

    // Smoothing
    bool smoothingEnabled_ = true;
    float smoothedPosition_ = 0.0f;
    bool hasPreviousPosition_ = false;
    static constexpr float kSmoothingAlpha = 0.2f;

    // Transform
    int offset_ = 0;
    bool reversed_ = false;

    // Suppress repeated "not found" errors — only emit on first failure
    bool lastScanFailed_ = false;
};

} // namespace alice
