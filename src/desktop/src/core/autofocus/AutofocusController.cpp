#include "core/autofocus/AutofocusController.h"
#include <cmath>
#include <algorithm>

namespace alice {

AutofocusController::AutofocusController(QObject *parent)
    : QObject(parent)
{
}

bool AutofocusController::canActivate() const {
    return mapping_.has_value() && motorReady_ && depthSensorReady_;
}

void AutofocusController::setEnabled(bool enabled) {
    if (enabled_ == enabled) return;
    enabled_ = enabled;
    emit enabledChanged(enabled);

    if (enabled && canActivate()) {
        if (mode_ == FocusMode::ContinuousAuto || mode_ == FocusMode::FaceTracking) {
            startContinuousMode();
        }
    } else if (!enabled) {
        stopContinuousMode();
    }
    emit stateChanged();
}

void AutofocusController::setFocusMode(FocusMode mode) {
    if (mode_ == mode) return;

    stopContinuousMode();
    mode_ = mode;
    emit modeChanged(mode);

    if (enabled_ && canActivate()) {
        if (mode == FocusMode::ContinuousAuto || mode == FocusMode::FaceTracking) {
            startContinuousMode();
        }
    }
    emit stateChanged();
}

bool AutofocusController::loadMapping(const AutofocusMapping &mapping) {
    auto validation = mapping.validate();
    if (!validation.isValid) {
        emit error("Mapping validation failed");
        return false;
    }
    mapping_ = mapping;
    emit mappingLoaded(QString::fromStdString(mapping.name()));
    emit stateChanged();

    if (enabled_ && canActivate()) {
        if (mode_ == FocusMode::ContinuousAuto || mode_ == FocusMode::FaceTracking) {
            startContinuousMode();
        }
    }
    return true;
}

bool AutofocusController::loadMappingFromFile(const QString &path) {
    auto mapping = AutofocusMapping::fromFile(path.toStdString());
    if (!mapping) {
        emit error("Failed to load mapping from file");
        return false;
    }
    return loadMapping(*mapping);
}

bool AutofocusController::loadPreset(MappingPreset preset) {
    return loadMapping(AutofocusMapping::createPreset(preset));
}

void AutofocusController::clearMapping() {
    stopContinuousMode();
    mapping_.reset();
    targetPosition_.reset();
    lastMotorPosition_.reset();
    mode_ = FocusMode::Manual;
    enabled_ = false;
    activelyFocusing_ = false;
    emit mappingCleared();
    emit modeChanged(FocusMode::Manual);
    emit enabledChanged(false);
    emit stateChanged();
}

void AutofocusController::processDepthData(float depthMeters, float confidence,
                                            float x, float y) {
    // Only process when active
    if (!enabled_ || mode_ == FocusMode::Manual || !canActivate()) return;

    depth_ = depthMeters;
    confidence_ = confidence;

    if (confidence < confidenceThreshold_) return;

    // Focus point proximity check for AF-C only. In AF-F the measurement
    // position is retargeted at the detected face each frame, so the
    // measured depth is already the face depth and we must not reject it
    // just because the face isn't near the tapped focus point.
    if (mode_ == FocusMode::ContinuousAuto) {
        const float dist = std::sqrt((x - focusX_) * (x - focusX_) +
                                     (y - focusY_) * (y - focusY_));
        if (dist > 0.1f) return;
    }

    // Debounce (33ms = ~30Hz)
    if (mode_ == FocusMode::ContinuousAuto || mode_ == FocusMode::FaceTracking) {
        if (debounceTimerStarted_ && debounceTimer_.elapsed() < kFocusDebounceMs) {
            return;
        }
        debounceTimer_.start();
        debounceTimerStarted_ = true;
        calculateAndApplyFocus(depthMeters);
    }
}

void AutofocusController::processTap(float normalizedX, float normalizedY) {
    if (!canActivate()) return;
    focusX_ = std::clamp(normalizedX, 0.0f, 1.0f);
    focusY_ = std::clamp(normalizedY, 0.0f, 1.0f);

    if (mode_ == FocusMode::SingleAuto) {
        // Single focus: use current depth at the tapped point
        if (depth_ > 0 && confidence_ >= confidenceThreshold_) {
            emit focusStarted();
            activelyFocusing_ = true;
            calculateAndApplyFocus(depth_);
            emit focusAchieved();
            activelyFocusing_ = false;
            emit stateChanged();
        } else {
            emit focusLost("Insufficient depth data");
        }
    }
}

void AutofocusController::updateDeviceReadiness(bool motorReady, bool depthSensorReady) {
    bool wasReady = canActivate();
    motorReady_ = motorReady;
    depthSensorReady_ = depthSensorReady;
    bool nowReady = canActivate();

    if (!wasReady && nowReady && enabled_) {
        if (mode_ == FocusMode::ContinuousAuto || mode_ == FocusMode::FaceTracking) {
            startContinuousMode();
        }
    } else if (wasReady && !nowReady) {
        stopContinuousMode();
    }
    emit stateChanged();
}

void AutofocusController::updateFaceTarget(float depthMeters, float confidence) {
    if (mode_ != FocusMode::FaceTracking || !activelyFocusing_ || !canActivate()) return;

    if (depthMeters > 0 && confidence >= confidenceThreshold_) {
        if (debounceTimerStarted_ && debounceTimer_.elapsed() < kFocusDebounceMs) {
            return;
        }
        debounceTimer_.start();
        debounceTimerStarted_ = true;
        calculateAndApplyFocus(depthMeters);
    }
}

void AutofocusController::setConfidenceThreshold(float threshold) {
    confidenceThreshold_ = std::clamp(threshold, 0.0f, 1.0f);
}

void AutofocusController::setSmoothingEnabled(bool enabled) {
    smoothingEnabled_ = enabled;
}

void AutofocusController::setResponseSpeed(int speed) {
    responseSpeed_ = std::clamp(speed, 0, 100);
}

// ── Private ──────────────────────────────────────────────────────────

void AutofocusController::calculateAndApplyFocus(float depth) {
    if (mode_ == FocusMode::Manual) return;
    if (!mapping_) {
        emit error("No mapping loaded");
        stopContinuousMode();
        return;
    }

    // Validate depth
    if (!std::isfinite(depth) || depth <= 0.0f || depth > 10.0f) return;

    auto motorPos = mapping_->getMotorPosition(depth);
    if (!motorPos) {
        emit error("Depth out of mapping range");
        return;
    }
    if (*motorPos < 0 || *motorPos > 4095) {
        emit error("Motor position out of range");
        return;
    }

    int finalPos = *motorPos;

    // Exponential moving average smoothing
    if (smoothingEnabled_ && lastMotorPosition_.has_value()) {
        float smoothed = static_cast<float>(*lastMotorPosition_) * (1.0f - kSmoothingAlpha)
                       + static_cast<float>(finalPos) * kSmoothingAlpha;
        if (std::isfinite(smoothed)) {
            finalPos = std::clamp(static_cast<int>(smoothed), 0, 4095);
        }
    }

    lastMotorPosition_ = finalPos;
    targetPosition_ = finalPos;
    depth_ = depth;

    emit targetPositionChanged(finalPos);
    emit stateChanged();
}

void AutofocusController::startContinuousMode() {
    if (activelyFocusing_) return;
    activelyFocusing_ = true;
    emit focusStarted();
    emit stateChanged();
}

void AutofocusController::stopContinuousMode() {
    if (!activelyFocusing_) return;
    activelyFocusing_ = false;
    emit stateChanged();
}

} // namespace alice
