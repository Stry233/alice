#include "core/motor/MotorController.h"
#include <algorithm>
#include <QDateTime>
#include <QFileInfo>
#include <QVariantMap>

namespace alice {

MotorController::MotorController(QObject *parent)
    : QObject(parent)
{
    reconnectTimer_.setInterval(2000);
    reconnectTimer_.setSingleShot(true);
    connect(&reconnectTimer_, &QTimer::timeout, this, &MotorController::connectDevice);
}

MotorController::~MotorController() {
    disconnectDevice();
}

// Does this serial port look like an Alice motor dongle? We trust the
// VID:PID first and fall back to CDC-ACM heuristics so the same logic
// works across vendors of the nRF52840 reference board.
static bool looksLikeMotorDongle(const QSerialPortInfo &info) {
    if (info.vendorIdentifier() == MotorController::kVendorId &&
        info.productIdentifier() == MotorController::kProductId) {
        return true;
    }
    return info.description().contains("nRF", Qt::CaseInsensitive)
        || info.description().contains("CDC", Qt::CaseInsensitive)
        || info.portName().contains("ttyACM");
}

void MotorController::connectDevice() {
    if (connected_) return;

    QSet<QString> attempted;
    const auto ports = QSerialPortInfo::availablePorts();

    // 1. Honour the user's preferred port first — that's the whole point
    //    of the selection dropdown. If it's present but can't be opened
    //    (busy, permission denied) we fall through to auto-pick so the
    //    app still comes up on *some* dongle rather than sitting idle.
    if (!preferredPortName_.isEmpty()) {
        for (const auto &info : ports) {
            if (info.portName() == preferredPortName_) {
                attempted.insert(info.portName());
                if (openPort(info)) return;
                break;
            }
        }
    }

    // 2. Known VID:PID match — the normal "just plug it in" path.
    for (const auto &info : ports) {
        if (attempted.contains(info.portName())) continue;
        if (info.vendorIdentifier() == kVendorId && info.productIdentifier() == kProductId) {
            attempted.insert(info.portName());
            if (openPort(info)) return;
        }
    }

    // 3. CDC-ACM heuristic — catches unknown reference boards.
    for (const auto &info : ports) {
        if (attempted.contains(info.portName())) continue;
        if (info.description().contains("nRF", Qt::CaseInsensitive) ||
            info.description().contains("CDC", Qt::CaseInsensitive) ||
            info.portName().contains("ttyACM")) {
            if (openPort(info)) return;
        }
    }

    if (!lastScanFailed_) {
        emit error("Motor dongle not found");
        lastScanFailed_ = true;
    }
    reconnectTimer_.start();
}

QVariantList MotorController::availableDevices() const {
    QVariantList out;
    for (const auto &info : QSerialPortInfo::availablePorts()) {
        if (!looksLikeMotorDongle(info)) continue;

        QVariantMap m;
        m["id"]     = info.portName();
        // Description is often generic ("nRF52 Connect SDK USB CDC ACM
        // sample") — append the port so multiple identical dongles are
        // distinguishable at a glance in the UI list.
        const QString desc = info.description().isEmpty()
                             ? QStringLiteral("Serial device")
                             : info.description();
        m["name"]   = QString("%1 (%2)").arg(desc, info.portName());
        m["active"] = connected_.load() && info.portName() == activePortName_;
        out.append(m);
    }
    return out;
}

void MotorController::selectDevice(const QString &portName) {
    if (preferredPortName_ == portName) return;
    preferredPortName_ = portName;

    // If we're already on the preferred port, nothing to do. Otherwise
    // close the current one so the next connectDevice() (either ours via
    // reconnectTimer_ or a caller-driven one) picks it up.
    const bool wantSwitch = !portName.isEmpty() && connected_ && devicePortName_ != portName;
    if (wantSwitch) {
        disconnectDevice();
        connectDevice();
    }
    emit availableDevicesChanged();
}

void MotorController::disconnectDevice() {
    reconnectTimer_.stop();
    if (serial_ && serial_->isOpen()) {
        serial_->close();
    }
    serial_.reset();
    bool wasConnected = connected_.exchange(false);
    if (wasConnected) {
        lastDisconnectMs_ = QDateTime::currentMSecsSinceEpoch();
        connectedSinceMs_ = 0;
    }
    // Deliberately keep deviceDescription_ populated so the popover can
    // still show "last seen as …" until a new device is plugged in.
    emit connectionChanged(false);
    emit availableDevicesChanged();
}

bool MotorController::openPort(const QSerialPortInfo &portInfo) {
    serial_ = std::make_unique<QSerialPort>(portInfo);
    serial_->setBaudRate(MotorProtocol::kBaudRate);
    serial_->setDataBits(QSerialPort::Data8);
    serial_->setStopBits(QSerialPort::OneStop);
    serial_->setParity(QSerialPort::NoParity);
    serial_->setFlowControl(QSerialPort::NoFlowControl);

    if (!serial_->open(QIODevice::ReadWrite)) {
        QString hint;
        QFileInfo fi(portInfo.systemLocation());
        if (fi.exists() && !fi.isWritable()) {
            hint = " — permission denied. Run: sudo usermod -aG dialout $USER (then re-login)";
        }
        emit error(QString("Failed to open %1: %2%3")
                       .arg(portInfo.portName(), serial_->errorString(), hint));
        serial_.reset();
        return false;
    }

    serial_->setDataTerminalReady(true);

    connect(serial_.get(), &QSerialPort::readyRead,
            this, &MotorController::onReadyRead);
    connect(serial_.get(), &QSerialPort::errorOccurred,
            this, &MotorController::onSerialError);

    connected_ = true;
    connectedSinceMs_ = QDateTime::currentMSecsSinceEpoch();
    lastScanFailed_ = false;
    lineBuffer_.clear();

    // Capture the actual device description so the UI popover can show a
    // real name instead of a hardcoded "nRF52840" fallback.
    deviceDescription_ = portInfo.description();
    if (deviceDescription_.isEmpty())
        deviceDescription_ = portInfo.portName();
    devicePortName_ = portInfo.systemLocation();
    activePortName_ = portInfo.portName();

    emit connectionChanged(true);
    emit availableDevicesChanged();
    return true;
}

void MotorController::setPosition(int position) {
    if (!connected_ || !serial_) return;

    int transformed = applyTransform(position);

    auto cmd = MotorProtocol::formatPositionCommand(transformed);
    serial_->write(cmd.c_str(), static_cast<qint64>(cmd.size()));
    position_ = transformed;
    emit positionChanged(transformed);
}

void MotorController::sendCommand(const QString &command) {
    if (!connected_ || !serial_) return;
    auto cmd = MotorProtocol::formatCommand(command.toStdString());
    serial_->write(cmd.c_str(), static_cast<qint64>(cmd.size()));
}

void MotorController::setDestinationAddress(int address) {
    if (!connected_ || !serial_) return;
    auto cmd = MotorProtocol::formatDestCommand(address);
    serial_->write(cmd.c_str(), static_cast<qint64>(cmd.size()));
}

void MotorController::scanAddress(int address) {
    if (!connected_ || !serial_) return;
    auto cmd = MotorProtocol::formatScanCommand(address);
    serial_->write(cmd.c_str(), static_cast<qint64>(cmd.size()));
}

void MotorController::queryStatus() {
    sendCommand("STATUS");
}

void MotorController::onReadyRead() {
    if (!serial_) return;
    QByteArray data = serial_->readAll();
    lineBuffer_.append(QString::fromUtf8(data));

    // Defensive cap: the protocol is strictly line-based (newline-terminated
    // ASCII), so any buffer that grows past a few KB without hitting '\n' is
    // almost certainly a firmware glitch streaming binary garbage. Drop all
    // but the trailing 1 KB so a misbehaving dongle can't OOM the process.
    if (lineBuffer_.size() > kMaxLineBufferBytes) {
        lineBuffer_ = lineBuffer_.right(kLineBufferTrimTail);
    }

    int newlineIdx;
    while ((newlineIdx = lineBuffer_.indexOf('\n')) >= 0) {
        QString line = lineBuffer_.left(newlineIdx).trimmed();
        lineBuffer_.remove(0, newlineIdx + 1);
        if (!line.isEmpty()) {
            processLine(line);
        }
    }
}

void MotorController::processLine(const QString &line) {
    emit responseReceived(line);

    auto response = MotorProtocol::parseResponse(line.toStdString());

    if (auto *pos = std::get_if<MotorPositionResponse>(&response)) {
        position_ = pos->position;
        emit positionChanged(pos->position);
    } else if (auto *status = std::get_if<MotorStatusResponse>(&response)) {
        position_ = status->position;
        emit positionChanged(status->position);
    } else if (auto *dest = std::get_if<MotorDestResponse>(&response)) {
        destAddress_ = dest->address;
        emit destinationChanged(dest->address);
    } else if (auto *scan = std::get_if<MotorScanResponse>(&response)) {
        destAddress_ = scan->address;
        emit destinationChanged(scan->address);
    } else if (std::holds_alternative<MotorReadyResponse>(response)) {
        emit ready();
    } else if (auto *err = std::get_if<MotorErrorResponse>(&response)) {
        emit error(QString::fromStdString(err->message));
    }
}

void MotorController::onSerialError(QSerialPort::SerialPortError err) {
    if (err == QSerialPort::NoError) return;
    if (err == QSerialPort::ResourceError) {
        // Device disconnected
        disconnectDevice();
        reconnectTimer_.start();
    }
    emit error(QString("Serial error: %1").arg(serial_ ? serial_->errorString() : "unknown"));
}

int MotorController::applyTransform(int position) const {
    // nRF52840 motor dongle uses a 12-bit encoder (0..kMaxPosition=4095).
    // `reversed_` is a per-install setting for rigs where the physical lens
    // rotation direction is inverted — we mirror about the midpoint so the
    // caller-facing API stays "0 = near, max = far" regardless.
    int pos = std::clamp(position + offset_, MotorProtocol::kMinPosition,
                         MotorProtocol::kMaxPosition);
    if (reversed_) pos = MotorProtocol::kMaxPosition - pos;
    return pos;
}

} // namespace alice
