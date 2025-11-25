package com.selkacraft.alice.comm.autofocus

import kotlinx.coroutines.flow.StateFlow

/**
 * Unified state model for the autofocus system.
 * This is the single source of truth for all autofocus-related state.
 */
data class AutofocusState(
    // Core state
    val isEnabled: Boolean = false,
    val mode: FocusMode = FocusMode.MANUAL,
    val mapping: AutofocusMapping? = null,

    // Device readiness
    val isMotorReady: Boolean = false,
    val isDepthSensorReady: Boolean = false,

    // Focus tracking
    val currentDepth: Float = 0f,
    val currentMotorPosition: Int? = null,
    val targetMotorPosition: Int? = null,
    val focusConfidence: Float = 0f,

    // Focus point
    val focusPoint: FocusPoint? = null,
    val isActivelyFocusing: Boolean = false,

    // Face detection state (for FACE_TRACKING mode)
    val faceDetectionState: FaceDetectionState = FaceDetectionState(),

    // Error state
    val error: AutofocusError? = null,

    // Statistics
    val stats: FocusStatistics = FocusStatistics()
) {
    /**
     * Check if autofocus can be activated
     */
    val canActivate: Boolean
        get() = mapping != null &&
                isMotorReady &&
                isDepthSensorReady &&
                error == null

    /**
     * Check if autofocus is currently active (enabled and can activate)
     */
    val isActive: Boolean
        get() = isEnabled && canActivate && mode != FocusMode.MANUAL

    /**
     * Get human-readable status message
     */
    val statusMessage: String
        get() = when {
            error != null -> error.message
            !isMotorReady -> "Motor not connected"
            !isDepthSensorReady -> "Depth sensor not connected"
            mapping == null -> "No mapping loaded"
            mode == FocusMode.MANUAL -> "Manual focus"
            !isEnabled -> "Autofocus disabled"
            isActivelyFocusing -> when(mode) {
                FocusMode.CONTINUOUS_AUTO -> "Continuous AF active"
                FocusMode.SINGLE_AUTO -> "Single AF active"
                FocusMode.FACE_TRACKING -> {
                    val faceCount = faceDetectionState.detectedFaces.size
                    if (faceCount > 0) "Tracking $faceCount face(s)" else "Face tracking active"
                }
                else -> "AF active"
            }
            else -> "Autofocus ready"
        }
}

/**
 * Represents a focus point in normalized coordinates
 */
data class FocusPoint(
    val x: Float,  // 0.0 to 1.0
    val y: Float,  // 0.0 to 1.0
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        require(x in 0f..1f) { "X must be between 0 and 1" }
        require(y in 0f..1f) { "Y must be between 0 and 1" }
    }
}

/**
 * Autofocus error states
 */
sealed class AutofocusError(val message: String) {
    object MappingInvalid : AutofocusError("Mapping validation failed")
    object DevicesNotReady : AutofocusError("Required devices not connected")
    data class CalculationError(val details: String) : AutofocusError("Focus calculation error: $details")
    data class MotorError(val details: String) : AutofocusError("Motor control error: $details")
    data class DepthError(val details: String) : AutofocusError("Depth sensor error: $details")
}

/**
 * Autofocus events for UI feedback
 */
sealed class AutofocusEvent {
    object FocusStarted : AutofocusEvent()
    object FocusAchieved : AutofocusEvent()
    data class FocusLost(val reason: String) : AutofocusEvent()
    data class MappingLoaded(val name: String) : AutofocusEvent()
    object MappingCleared : AutofocusEvent()
    data class Error(val error: AutofocusError) : AutofocusEvent()
    data class ModeChanged(val newMode: FocusMode) : AutofocusEvent()
}