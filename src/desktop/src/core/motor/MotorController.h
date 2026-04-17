#pragma once

#include <QObject>
#include <QSerialPort>
#include <QSerialPortInfo>
#include <QTimer>
#include <QString>
#include <QVariantList>
#include <memory>
#include <atomic>

#include "core/motor/MotorProtocol.h"

namespace alice {

/**
 * Motor controller for CDC-ACM serial communication with nRF52840 dongle.
 *
 * Discovers the dongle by VID/PID or CDC-ACM port name, opens a serial
 * connection at 115200 baud, and translates high-level setPosition() calls
 * into the wire protocol (with optional offset, reversal, and EMA smoothing).
 * Auto-reconnects on device yank via a 2-second retry timer.
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

    /**
     * Enumerate candidate motor-dongle serial ports currently visible to
     * the OS. Each entry is a QVariantMap with:
     *   "id"     — QString, stable portName (e.g. "ttyACM0", "COM3"),
     *              used as the selection key
     *   "name"   — QString, human-readable (description + port)
     *   "active" — bool, true if this entry is the currently-connected dongle
     *
     * Only ports that look like Alice dongles are included — matching
     * VID:PID first, then falling back to CDC-ACM heuristics. Ports that
     * definitely aren't ours (Bluetooth modems, random serial cables)
     * are filtered out so the selection dropdown stays on-topic.
     */
    Q_INVOKABLE QVariantList availableDevices() const;

    /**
     * Persist a preferred port-name. On the next discovery tick the
     * coordinator will auto-switch to it if present. If nothing's
     * connected yet, the preference is consulted on the next
     * connectDevice(). Pass an empty string to clear the preference
     * (revert to auto-pick).
     */
    Q_INVOKABLE void selectDevice(const QString &portName);

    /** Read the currently-stored preference; empty if auto-pick. */
    QString preferredDeviceId() const { return preferredPortName_; }

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
    /**
     * Emitted when either the enumeration of candidate dongles changes
     * (plug / unplug) or the active one changes. UI re-reads
     * availableDevices() and refreshes the selection dropdown.
     */
    void availableDevicesChanged();

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
    // Full system location (e.g. "/dev/ttyACM0", "\\\\.\\COM3") — surfaced
    // as the popover's "Address" field.
    QString devicePortName_;
    // Short port name (e.g. "ttyACM0", "COM3") — matches what
    // QSerialPortInfo::portName() returns, used as the list id so the
    // "active" flag in availableDevices() stays consistent with
    // selectDevice(id) calls.
    QString activePortName_;

    // User-selected preferred port. On discovery, we try this first; when
    // empty we fall back to VID:PID scan + CDC-ACM heuristic. Persisted
    // via SettingsManager and restored at startup by AppController.
    QString preferredPortName_;

    // Connection lifecycle timestamps (epoch ms; 0 = never)
    std::atomic<qint64> connectedSinceMs_{0};
    std::atomic<qint64> lastDisconnectMs_{0};

    // Transform
    int offset_ = 0;
    bool reversed_ = false;

    // Suppress repeated "not found" errors — only emit on first failure
    bool lastScanFailed_ = false;

    // Defensive bounds on lineBuffer_ so a broken dongle can't OOM the app.
    static constexpr int kMaxLineBufferBytes   = 8 * 1024;
    static constexpr int kLineBufferTrimTail   = 1 * 1024;
};

} // namespace alice
