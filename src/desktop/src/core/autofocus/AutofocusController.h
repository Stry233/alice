#pragma once

#include <QObject>
#include <QElapsedTimer>
#include <optional>
#include <memory>

#include "core/autofocus/AutofocusMapping.h"

namespace alice {

enum class FocusMode {
    Manual,
    SingleAuto,     // AF-S
    ContinuousAuto, // AF-C
    FaceTracking    // AF-F
};
Q_NAMESPACE

/**
 * Main autofocus controller.
 * Focus-mode state machine supporting Manual, AF-S, AF-C, and AF-F (face-tracking).
 * Maps depth measurements to motor positions via AutofocusMapping, handles tap-to-focus
 * triggers, and delivers debounced target positions to the motor controller.
 */
class AutofocusController : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool enabled READ isEnabled WRITE setEnabled NOTIFY enabledChanged)
    Q_PROPERTY(int focusMode READ focusModeInt WRITE setFocusModeInt NOTIFY modeChanged)
    Q_PROPERTY(bool activelyFocusing READ isActivelyFocusing NOTIFY stateChanged)
    Q_PROPERTY(float currentDepth READ currentDepth NOTIFY stateChanged)
    Q_PROPERTY(int targetMotorPosition READ targetMotorPosition NOTIFY targetPositionChanged)
    Q_PROPERTY(float focusConfidence READ focusConfidence NOTIFY stateChanged)
    Q_PROPERTY(bool canActivate READ canActivate NOTIFY stateChanged)

public:
    static constexpr int64_t kFocusDebounceMs = 33;   // ~30 Hz
    static constexpr float kDefaultSmoothingAlpha = 0.4f;
    static constexpr float kDefaultConfidenceThreshold = 0.7f;

    explicit AutofocusController(QObject *parent = nullptr);

    // State queries
    bool isEnabled() const { return enabled_; }
    FocusMode focusMode() const { return mode_; }
    int focusModeInt() const { return static_cast<int>(mode_); }
    bool isActivelyFocusing() const { return activelyFocusing_; }
    float currentDepth() const { return depth_; }
    int targetMotorPosition() const { return targetPosition_.value_or(-1); }
    float focusConfidence() const { return confidence_; }
    bool canActivate() const;
    bool hasMapping() const { return mapping_.has_value(); }
    const std::optional<AutofocusMapping> &currentMapping() const { return mapping_; }

public slots:
    void setEnabled(bool enabled);
    void setFocusMode(FocusMode mode);
    void setFocusModeInt(int mode) { setFocusMode(static_cast<FocusMode>(mode)); }

    /** Load a mapping. Validates before accepting. */
    bool loadMapping(const AutofocusMapping &mapping);
    bool loadMappingFromFile(const QString &path);
    bool loadPreset(MappingPreset preset);
    void clearMapping();

    /** Process depth data from RealSense. Core autofocus loop entry. */
    void processDepthData(float depthMeters, float confidence, float x = 0.5f, float y = 0.5f);

    /** Process a tap-to-focus at normalized coordinates. */
    void processTap(float normalizedX, float normalizedY);

    /** Update device readiness flags. */
    void updateDeviceReadiness(bool motorReady, bool depthSensorReady);

    /** Update face detection state (for FACE_TRACKING mode). */
    void updateFaceTarget(float depthMeters, float confidence);

    /** Configuration updates. */
    void setConfidenceThreshold(float threshold);
    void setSmoothingAlpha(float alpha);
    float smoothingAlpha() const { return smoothingAlpha_; }

signals:
    void enabledChanged(bool enabled);
    void modeChanged(FocusMode mode);
    void targetPositionChanged(int position);
    void stateChanged();

    // Events
    void focusStarted();
    void focusAchieved();
    void focusLost(const QString &reason);
    void mappingLoaded(const QString &name);
    void mappingCleared();
    void error(const QString &message);

private:
    void calculateAndApplyFocus(float depth);
    void startContinuousMode();
    void stopContinuousMode();

    // Mode state
    FocusMode mode_ = FocusMode::Manual;
    bool enabled_ = false;
    bool activelyFocusing_ = false;

    // Device readiness
    bool motorReady_ = false;
    bool depthSensorReady_ = false;

    // Mapping
    std::optional<AutofocusMapping> mapping_;

    // Focus state
    float depth_ = 0.0f;
    float confidence_ = 0.0f;
    std::optional<int> targetPosition_;
    std::optional<int> lastMotorPosition_;

    // Focus point
    float focusX_ = 0.5f;
    float focusY_ = 0.5f;

    // Settings
    float confidenceThreshold_ = kDefaultConfidenceThreshold;
    // EMA weight for the autofocus motor target. Higher values track
    // faster but amplify depth sensor noise. Range [0.05, 1.0].
    float smoothingAlpha_ = kDefaultSmoothingAlpha;

    // Timing
    QElapsedTimer debounceTimer_;
    bool debounceTimerStarted_ = false;
};

} // namespace alice
