package com.selkacraft.alice.comm.core

/**
 * Unified device state representation replacing multiple boolean flags
 * This sealed class hierarchy provides clear, mutually exclusive states
 */
sealed class DeviceState {
    // Core states common to all devices
    object Idle : DeviceState()
    object Connecting : DeviceState()
    object AwaitingPermission : DeviceState()
    object Connected : DeviceState()
    object Active : DeviceState()
    object Disconnecting : DeviceState()
    object PhysicallyDisconnected : DeviceState()
    data class Error(val message: String, val isRecoverable: Boolean = true) : DeviceState()

    // Utility functions
    val isOperational: Boolean
        get() = this is Connected || this is Active

    val isTransitioning: Boolean
        get() = this is Connecting || this is Disconnecting

    val isDisconnected: Boolean
        get() = this is Idle || this is PhysicallyDisconnected
}

/**
 * Device-specific state extensions
 */
sealed class CameraState : DeviceState() {
    object ChangingResolution : CameraState()
    object PreviewStarting : CameraState()
    object PreviewActive : CameraState()
    object PreviewStopping : CameraState()
}

sealed class MotorState : DeviceState() {
    object Calibrating : MotorState()
    data class Moving(val targetPosition: Int) : MotorState()
}

sealed class RealSenseState : DeviceState() {
    object PipelineActive : RealSenseState()
    object Streaming : RealSenseState()
}

/**
 * Base interface for device-specific data
 */
interface DeviceStateData {
    fun copy(): DeviceStateData
}