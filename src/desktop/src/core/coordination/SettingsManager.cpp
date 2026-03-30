#include "core/coordination/SettingsManager.h"

namespace alice {

// Keys matching Android SettingsManager.kt
namespace keys {
    constexpr auto kAfEnabled    = "autofocus/enabled";
    constexpr auto kAfMode       = "autofocus/mode";
    constexpr auto kAfConfidence = "autofocus/confidenceThreshold";
    constexpr auto kAfSmoothing  = "autofocus/smoothing";
    constexpr auto kAfSpeed      = "autofocus/responseSpeed";

    constexpr auto kMotorSpeed     = "motor/speed";
    constexpr auto kMotorReverse   = "motor/reverse";
    constexpr auto kMotorOffset    = "motor/offset";
    constexpr auto kMotorDestAddr  = "motor/destinationAddress";

    constexpr auto kDepthConfidence = "depth/confidenceThreshold";
    constexpr auto kDepthMin        = "depth/minDistance";
    constexpr auto kDepthMax        = "depth/maxDistance";

    constexpr auto kSyncPort       = "network/syncPort";
    constexpr auto kLogVerbosity   = "system/logVerbosity";
    constexpr auto kAutoReconnect  = "system/autoReconnect";

    // Video resolution
    constexpr auto kDepthResW      = "video/depthWidth";
    constexpr auto kDepthResH      = "video/depthHeight";
    constexpr auto kDepthResFps    = "video/depthFps";
    constexpr auto kColorResW      = "video/colorWidth";
    constexpr auto kColorResH      = "video/colorHeight";
    constexpr auto kColorResFps    = "video/colorFps";
    constexpr auto kCaptureResW    = "video/captureWidth";
    constexpr auto kCaptureResH    = "video/captureHeight";
    constexpr auto kCaptureResFps  = "video/captureFps";

    // Transmission quality
    constexpr auto kTxQualityDepth   = "network/txQualityDepth";
    constexpr auto kTxQualityCapture = "network/txQualityCapture";
    constexpr auto kTxMaxFps         = "network/txMaxFps";

    // Motor last position
    constexpr auto kMotorLastPos   = "motor/lastPosition";
}

SettingsManager::SettingsManager(QObject *parent)
    : QObject(parent)
    , settings_("SelkaCraft", "Alice")
{
}

// ── Autofocus ────────────────────────────────────────────────────────

bool SettingsManager::autofocusEnabled() const { return settings_.value(keys::kAfEnabled, false).toBool(); }
void SettingsManager::setAutofocusEnabled(bool v) { settings_.setValue(keys::kAfEnabled, v); emit autofocusEnabledChanged(); }

QString SettingsManager::autofocusMode() const { return settings_.value(keys::kAfMode, "MANUAL").toString(); }
void SettingsManager::setAutofocusMode(const QString &v) { settings_.setValue(keys::kAfMode, v); emit autofocusModeChanged(); }

float SettingsManager::confidenceThreshold() const { return settings_.value(keys::kAfConfidence, 0.7f).toFloat(); }
void SettingsManager::setConfidenceThreshold(float v) { settings_.setValue(keys::kAfConfidence, v); emit confidenceThresholdChanged(); }

bool SettingsManager::smoothingEnabled() const { return settings_.value(keys::kAfSmoothing, true).toBool(); }
void SettingsManager::setSmoothingEnabled(bool v) { settings_.setValue(keys::kAfSmoothing, v); emit smoothingEnabledChanged(); }

int SettingsManager::responseSpeed() const { return settings_.value(keys::kAfSpeed, 50).toInt(); }
void SettingsManager::setResponseSpeed(int v) { settings_.setValue(keys::kAfSpeed, v); emit responseSpeedChanged(); }

// ── Motor ────────────────────────────────────────────────────────────

int SettingsManager::motorSpeed() const { return settings_.value(keys::kMotorSpeed, 50).toInt(); }
void SettingsManager::setMotorSpeed(int v) { settings_.setValue(keys::kMotorSpeed, v); emit motorSpeedChanged(); }

bool SettingsManager::motorReverse() const { return settings_.value(keys::kMotorReverse, false).toBool(); }
void SettingsManager::setMotorReverse(bool v) { settings_.setValue(keys::kMotorReverse, v); emit motorReverseChanged(); }

int SettingsManager::motorOffset() const { return settings_.value(keys::kMotorOffset, 0).toInt(); }
void SettingsManager::setMotorOffset(int v) { settings_.setValue(keys::kMotorOffset, v); emit motorOffsetChanged(); }

int SettingsManager::motorDestAddress() const { return settings_.value(keys::kMotorDestAddr, 0xFFFF).toInt(); }
void SettingsManager::setMotorDestAddress(int v) { settings_.setValue(keys::kMotorDestAddr, v); emit motorDestAddressChanged(); }

// ── Depth ────────────────────────────────────────────────────────────

float SettingsManager::depthConfidenceThreshold() const { return settings_.value(keys::kDepthConfidence, 0.7f).toFloat(); }
void SettingsManager::setDepthConfidenceThreshold(float v) { settings_.setValue(keys::kDepthConfidence, v); emit depthConfidenceThresholdChanged(); }

int SettingsManager::depthMinDistance() const { return settings_.value(keys::kDepthMin, 200).toInt(); }
void SettingsManager::setDepthMinDistance(int v) { settings_.setValue(keys::kDepthMin, v); emit depthMinDistanceChanged(); }

int SettingsManager::depthMaxDistance() const { return settings_.value(keys::kDepthMax, 5000).toInt(); }
void SettingsManager::setDepthMaxDistance(int v) { settings_.setValue(keys::kDepthMax, v); emit depthMaxDistanceChanged(); }

// ── Network ──────────────────────────────────────────────────────────

int SettingsManager::syncPort() const { return settings_.value(keys::kSyncPort, 8765).toInt(); }
void SettingsManager::setSyncPort(int v) { settings_.setValue(keys::kSyncPort, v); emit syncPortChanged(); }

// ── System ───────────────────────────────────────────────────────────

QString SettingsManager::logVerbosity() const { return settings_.value(keys::kLogVerbosity, "INFO").toString(); }
void SettingsManager::setLogVerbosity(const QString &v) { settings_.setValue(keys::kLogVerbosity, v); }

bool SettingsManager::autoReconnect() const { return settings_.value(keys::kAutoReconnect, true).toBool(); }
void SettingsManager::setAutoReconnect(bool v) { settings_.setValue(keys::kAutoReconnect, v); }

// ── Video resolution ─────────────────────────────────────────────────

int SettingsManager::depthResW() const { return settings_.value(keys::kDepthResW, 640).toInt(); }
int SettingsManager::depthResH() const { return settings_.value(keys::kDepthResH, 480).toInt(); }
int SettingsManager::depthResFps() const { return settings_.value(keys::kDepthResFps, 30).toInt(); }
void SettingsManager::setDepthResolution(int w, int h, int fps) {
    settings_.setValue(keys::kDepthResW, w);
    settings_.setValue(keys::kDepthResH, h);
    settings_.setValue(keys::kDepthResFps, fps);
}

int SettingsManager::colorResW() const { return settings_.value(keys::kColorResW, 640).toInt(); }
int SettingsManager::colorResH() const { return settings_.value(keys::kColorResH, 480).toInt(); }
int SettingsManager::colorResFps() const { return settings_.value(keys::kColorResFps, 30).toInt(); }
void SettingsManager::setColorResolution(int w, int h, int fps) {
    settings_.setValue(keys::kColorResW, w);
    settings_.setValue(keys::kColorResH, h);
    settings_.setValue(keys::kColorResFps, fps);
}

int SettingsManager::captureResW() const { return settings_.value(keys::kCaptureResW, 0).toInt(); }
int SettingsManager::captureResH() const { return settings_.value(keys::kCaptureResH, 0).toInt(); }
int SettingsManager::captureResFps() const { return settings_.value(keys::kCaptureResFps, 0).toInt(); }
void SettingsManager::setCaptureResolution(int w, int h, int fps) {
    settings_.setValue(keys::kCaptureResW, w);
    settings_.setValue(keys::kCaptureResH, h);
    settings_.setValue(keys::kCaptureResFps, fps);
}

// ── Transmission quality ─────────────────────────────────────────────

int SettingsManager::txQualityDepth() const { return settings_.value(keys::kTxQualityDepth, 85).toInt(); }
void SettingsManager::setTxQualityDepth(int v) { settings_.setValue(keys::kTxQualityDepth, v); }
int SettingsManager::txQualityCapture() const { return settings_.value(keys::kTxQualityCapture, 80).toInt(); }
void SettingsManager::setTxQualityCapture(int v) { settings_.setValue(keys::kTxQualityCapture, v); }
int SettingsManager::txMaxFps() const { return settings_.value(keys::kTxMaxFps, 30).toInt(); }
void SettingsManager::setTxMaxFps(int v) { settings_.setValue(keys::kTxMaxFps, v); }

// ── Motor last position ──────────────────────────────────────────────

int SettingsManager::motorLastPosition() const { return settings_.value(keys::kMotorLastPos, 0).toInt(); }
void SettingsManager::setMotorLastPosition(int v) { settings_.setValue(keys::kMotorLastPos, v); }

// ── Resets ───────────────────────────────────────────────────────────

void SettingsManager::resetAutofocusSettings() {
    settings_.remove("autofocus");
    emit autofocusEnabledChanged(); emit autofocusModeChanged();
    emit confidenceThresholdChanged(); emit smoothingEnabledChanged();
    emit responseSpeedChanged();
}

void SettingsManager::resetMotorSettings() {
    settings_.remove("motor");
    emit motorSpeedChanged(); emit motorReverseChanged();
    emit motorOffsetChanged(); emit motorDestAddressChanged();
}

void SettingsManager::resetDepthSettings() {
    settings_.remove("depth");
    emit depthConfidenceThresholdChanged(); emit depthMinDistanceChanged();
    emit depthMaxDistanceChanged();
}

void SettingsManager::resetAllSettings() {
    settings_.clear();
    resetAutofocusSettings();
    resetMotorSettings();
    resetDepthSettings();
}

} // namespace alice
