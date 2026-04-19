package com.selkacraft.alice.comm.motor

import com.selkacraft.alice.comm.core.DeviceStateData

/**
 * Motor-specific state data
 */
data class MotorStateData(
    val position: Int = 2048,
    val isCalibrated: Boolean = false,
    val lastPositionCommand: String = "",
    val lastCommandTime: Long = 0L
) : DeviceStateData {
    override fun copy(): DeviceStateData = copy()

    fun shouldSendPositionCommand(newPosition: Int): Boolean {
        val now = System.currentTimeMillis()
        val command = "POS $newPosition"

        return !(command == lastPositionCommand && (now - lastCommandTime) < 5)
    }
}