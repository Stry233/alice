#pragma once

#include <QString>
#include <QJsonObject>
#include <QJsonDocument>
#include <QJsonArray>
#include <cstdint>
#include <optional>

namespace alice {

/**
 * Network sync protocol message types and serialization.
 * Used for bidirectional state sync between PC host and Android remote.
 */
enum class SyncMessageType {
    StateUpdate,      // Periodic state snapshot (10 Hz or on-change)
    MotorCommand,     // Direct motor position command
    ModeChange,       // Focus mode / settings change
    CalibrationSync,  // Push/pull calibration mappings
    Heartbeat,        // Keep-alive with device info
    Authenticate,     // Initial handshake with token
    StreamControl,    // Enable/disable video stream channels
    MeasurePosition,  // Measurement crosshair position {x, y} (0..1 normalized)
    FaceTracking      // Face bounding-box / tracker state broadcast
};

struct SyncMessage {
    SyncMessageType type;
    int64_t timestamp = 0;
    QString sender;       // "desktop" or "android"
    QJsonObject payload;

    QByteArray serialize() const;
    static std::optional<SyncMessage> deserialize(const QByteArray &data);

    // Factory methods for common messages
    static SyncMessage stateUpdate(int motorPosition, float depth, float confidence,
                                   const QString &focusMode, bool enabled,
                                   bool activelyFocusing, int facesDetected,
                                   bool motorConnected, bool realSenseConnected);

    static SyncMessage motorCommand(int position, const QString &source, bool highPriority = false);
    static SyncMessage modeChange(const QString &mode, bool enabled,
                                   float confidenceThreshold, bool smoothing, int responseSpeed);
    static SyncMessage calibrationSync(const QString &action, const QJsonObject &mappingJson = {});
    static SyncMessage heartbeat(int64_t uptime, const QString &deviceInfo = "");
    static SyncMessage authenticate(const QString &token);
    static SyncMessage streamControl(bool color, bool depth, bool capture);
    static SyncMessage measurePosition(float x, float y);
    // `faces` carries the already-prepared JSON array for the "faces" field.
    // `frameWidth`/`frameHeight` are the pixel dimensions of the source image.
    static SyncMessage faceTracking(const QJsonArray &faces,
                                    int frameWidth, int frameHeight,
                                    int selectedId);
};

namespace SyncProtocolConstants {
    constexpr int kDefaultPort = 8765;
    constexpr int kStateUpdateIntervalMs = 100;  // 10 Hz
    constexpr int kHeartbeatIntervalMs = 5000;
    constexpr int kConnectionTimeoutMs = 10000;
}

} // namespace alice
