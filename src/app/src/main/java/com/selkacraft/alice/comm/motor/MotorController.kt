package com.selkacraft.alice.comm.motor

import com.selkacraft.alice.comm.core.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Simplified interface for motor control operations
 * This interface is what other components should use to interact with the motor system
 */
interface MotorController {
    /**
     * Current motor position (0-4095)
     */
    val currentPosition: StateFlow<Int>

    /**
     * Motor calibration state
     */
    val isCalibrated: StateFlow<Boolean>

    /**
     * Connection state
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Set the motor position
     * @param position Target position (0-4095)
     */
    fun setPosition(position: Int)

    /**
     * Send a custom command to the motor
     * @param command Command string
     * @param callback Optional callback for response
     */
    fun sendCommand(command: String, callback: ((String) -> Unit)? = null)

    /**
     * Calibrate the motor
     */
    fun calibrate() {
        sendCommand("CALIBRATE")
    }

    /**
     * Request motor status
     */
    fun requestStatus(callback: ((String) -> Unit)? = null) {
        sendCommand("STATUS", callback)
    }

    /**
     * Check if motor is connected
     */
    fun isConnected(): Boolean {
        return connectionState.value is ConnectionState.Connected ||
                connectionState.value is ConnectionState.Active
    }

    /**
     * Register to start monitoring for devices
     */
    fun register()

    /**
     * Unregister and stop monitoring
     */
    fun unregister()
}

/**
 * Extension of MotorControlManager to implement the simplified interface
 */
fun MotorControlManager.asController(): MotorController {
    val manager = this
    return object : MotorController {
        override val currentPosition: StateFlow<Int> = manager.currentPosition
        override val isCalibrated: StateFlow<Boolean> = manager.isCalibrated
        override val connectionState: StateFlow<ConnectionState> = manager.connectionState

        override fun setPosition(position: Int) = manager.setPosition(position)
        override fun sendCommand(command: String, callback: ((String) -> Unit)?) =
            manager.sendCommand(command, callback)
        override fun register() = manager.register()
        override fun unregister() = manager.unregister()
    }
}

/**
 * Quick position presets for common focus points
 */
object MotorPositionPresets {
    const val NEAR_FOCUS = 0
    const val MID_FOCUS = 2048
    const val FAR_FOCUS = 4095

    const val MACRO = 512
    const val PORTRAIT = 1536
    const val LANDSCAPE = 3072
    const val INFINITY = 3840
}