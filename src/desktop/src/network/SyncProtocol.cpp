#include "network/SyncProtocol.h"
#include <QDateTime>

namespace alice {

namespace {
    QString typeToString(SyncMessageType type) {
        switch (type) {
        case SyncMessageType::StateUpdate:     return "STATE_UPDATE";
        case SyncMessageType::MotorCommand:    return "MOTOR_COMMAND";
        case SyncMessageType::ModeChange:      return "MODE_CHANGE";
        case SyncMessageType::CalibrationSync: return "CALIBRATION_SYNC";
        case SyncMessageType::Heartbeat:       return "HEARTBEAT";
        case SyncMessageType::Authenticate:    return "AUTHENTICATE";
        case SyncMessageType::StreamControl:   return "STREAM_CONTROL";
        }
        return "UNKNOWN";
    }

    SyncMessageType stringToType(const QString &str) {
        if (str == "STATE_UPDATE")     return SyncMessageType::StateUpdate;
        if (str == "MOTOR_COMMAND")    return SyncMessageType::MotorCommand;
        if (str == "MODE_CHANGE")      return SyncMessageType::ModeChange;
        if (str == "CALIBRATION_SYNC") return SyncMessageType::CalibrationSync;
        if (str == "HEARTBEAT")        return SyncMessageType::Heartbeat;
        if (str == "AUTHENTICATE")     return SyncMessageType::Authenticate;
        if (str == "STREAM_CONTROL")   return SyncMessageType::StreamControl;
        return SyncMessageType::Heartbeat;
    }
}

QByteArray SyncMessage::serialize() const {
    QJsonObject obj;
    obj["type"] = typeToString(type);
    obj["timestamp"] = timestamp > 0 ? timestamp : QDateTime::currentMSecsSinceEpoch();
    obj["sender"] = sender;
    obj["payload"] = payload;
    return QJsonDocument(obj).toJson(QJsonDocument::Compact);
}

std::optional<SyncMessage> SyncMessage::deserialize(const QByteArray &data) {
    QJsonParseError err;
    auto doc = QJsonDocument::fromJson(data, &err);
    if (err.error != QJsonParseError::NoError || !doc.isObject()) return std::nullopt;

    auto obj = doc.object();
    SyncMessage msg;
    msg.type = stringToType(obj["type"].toString());
    msg.timestamp = obj["timestamp"].toVariant().toLongLong();
    msg.sender = obj["sender"].toString();
    msg.payload = obj["payload"].toObject();
    return msg;
}

SyncMessage SyncMessage::stateUpdate(int motorPosition, float depth, float confidence,
                                      const QString &focusMode, bool enabled,
                                      bool activelyFocusing, int facesDetected,
                                      bool motorConnected, bool realSenseConnected) {
    SyncMessage msg;
    msg.type = SyncMessageType::StateUpdate;
    msg.sender = "desktop";
    msg.payload = QJsonObject{
        {"motorPosition", motorPosition},
        {"depth", static_cast<double>(depth)},
        {"depthConfidence", static_cast<double>(confidence)},
        {"focusMode", focusMode},
        {"isEnabled", enabled},
        {"isActivelyFocusing", activelyFocusing},
        {"facesDetected", facesDetected},
        {"connectionStates", QJsonObject{
            {"motor", motorConnected ? "Active" : "Disconnected"},
            {"realSense", realSenseConnected ? "Active" : "Disconnected"}
        }}
    };
    return msg;
}

SyncMessage SyncMessage::motorCommand(int position, const QString &source, bool highPriority) {
    SyncMessage msg;
    msg.type = SyncMessageType::MotorCommand;
    msg.payload = QJsonObject{
        {"position", position},
        {"source", source},
        {"priority", highPriority ? "HIGH" : "NORMAL"}
    };
    return msg;
}

SyncMessage SyncMessage::modeChange(const QString &mode, bool enabled,
                                     float confidenceThreshold, bool smoothing, int responseSpeed) {
    SyncMessage msg;
    msg.type = SyncMessageType::ModeChange;
    msg.payload = QJsonObject{
        {"mode", mode},
        {"enabled", enabled},
        {"settings", QJsonObject{
            {"confidenceThreshold", static_cast<double>(confidenceThreshold)},
            {"smoothing", smoothing},
            {"responseSpeed", responseSpeed}
        }}
    };
    return msg;
}

SyncMessage SyncMessage::calibrationSync(const QString &action, const QJsonObject &mappingJson) {
    SyncMessage msg;
    msg.type = SyncMessageType::CalibrationSync;
    msg.payload = QJsonObject{
        {"action", action},
        {"mapping", mappingJson}
    };
    return msg;
}

SyncMessage SyncMessage::heartbeat(int64_t uptime, const QString &deviceInfo) {
    SyncMessage msg;
    msg.type = SyncMessageType::Heartbeat;
    msg.payload = QJsonObject{
        {"uptime", static_cast<qint64>(uptime)},
        {"deviceInfo", deviceInfo}
    };
    return msg;
}

SyncMessage SyncMessage::authenticate(const QString &token) {
    SyncMessage msg;
    msg.type = SyncMessageType::Authenticate;
    msg.payload = QJsonObject{{"token", token}};
    return msg;
}

SyncMessage SyncMessage::streamControl(bool color, bool depth, bool capture) {
    SyncMessage msg;
    msg.type = SyncMessageType::StreamControl;
    msg.payload = QJsonObject{
        {"color", color},
        {"depth", depth},
        {"capture", capture}
    };
    return msg;
}

} // namespace alice
