package com.selkacraft.alice.coordination

import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.comm.motor.MotorControlManager
import com.selkacraft.alice.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Synchronizes settings changes with device hardware.
 * Monitors settings StateFlows and applies changes to appropriate devices.
 */
class SettingsSynchronizer(
    private val settingsManager: SettingsManager,
    private val deviceManager: DeviceCoordinationManager,
    private val scope: CoroutineScope,
    private val onLogMessage: (String, String) -> Unit  // (category, message)
) {
    private data class MotorSettings(
        val speed: Int,
        val acceleration: Int,
        val smoothing: Boolean,
        val reverse: Boolean,
        val offset: Int
    )

    private data class DepthSettings(
        val minDistance: Int,
        val maxDistance: Int,
        val smoothing: Float
    )

    /**
     * Initialize all settings synchronization logic
     */
    fun initialize() {
        monitorDevicePriorityChanges()
        monitorMotorSettings()
        monitorCameraSettings()
        monitorDepthSettings()
        monitorUsbBandwidthLimit()
    }

    /**
     * Monitor device priority changes
     */
    private fun monitorDevicePriorityChanges() {
        scope.launch {
            settingsManager.devicePriority.collect { priority ->
                val message = when (priority) {
                    "Camera" -> "Setting Camera as priority device"
                    "RealSense" -> "Setting RealSense as priority device"
                    "Motor" -> "Setting Motor as priority device"
                    "Balanced" -> "Using balanced device priority"
                    else -> "Unknown device priority: $priority"
                }
                onLogMessage("SYSTEM", message)
            }
        }
    }

    /**
     * Monitor motor settings and apply to motor controller
     */
    private fun monitorMotorSettings() {
        scope.launch {
            combine(
                settingsManager.motorSpeed,
                settingsManager.motorAcceleration,
                settingsManager.motorSmoothing,
                settingsManager.motorReverseDirection,
                settingsManager.motorCalibrationOffset
            ) { speed, accel, smooth, reverse, offset ->
                MotorSettings(speed, accel, smooth, reverse, offset)
            }.collect { settings ->
                applyMotorSettings(settings)
            }
        }
    }

    /**
     * Monitor camera settings
     */
    private fun monitorCameraSettings() {
        scope.launch {
            settingsManager.videoFormat.collect { format ->
                onLogMessage("CAMERA", "Video format changed to: $format")
            }
        }
    }

    /**
     * Monitor depth processing settings
     */
    private fun monitorDepthSettings() {
        scope.launch {
            combine(
                settingsManager.depthMinDistance,
                settingsManager.depthMaxDistance,
                settingsManager.depthFilterSmoothing
            ) { min, max, smoothing ->
                DepthSettings(min, max, smoothing)
            }.collect { settings ->
                applyDepthSettings(settings)
            }
        }
    }

    /**
     * Monitor USB bandwidth limit
     */
    private fun monitorUsbBandwidthLimit() {
        scope.launch {
            settingsManager.usbBandwidthLimit.collect { limit ->
                onLogMessage("SYSTEM", "USB bandwidth limit set to: $limit Mbps")
            }
        }
    }

    /**
     * Apply motor settings to the motor controller
     */
    private fun applyMotorSettings(settings: MotorSettings) {
        val motorState = deviceManager.motorConnectionState.value
        if (motorState is ConnectionState.Connected || motorState is ConnectionState.Active) {
            deviceManager.motorControlManager.sendCommand("SPEED ${settings.speed}")
            deviceManager.motorControlManager.sendCommand("ACCEL ${settings.acceleration}")

            if (settings.smoothing) {
                deviceManager.motorControlManager.sendCommand("SMOOTH ON")
            } else {
                deviceManager.motorControlManager.sendCommand("SMOOTH OFF")
            }

            onLogMessage("MOTOR", "Applied motor settings: speed=${settings.speed}, accel=${settings.acceleration}")
        }
    }

    /**
     * Apply depth settings (logged for now, actual application depends on RealSense implementation)
     */
    private fun applyDepthSettings(settings: DepthSettings) {
        onLogMessage(
            "REALSENSE",
            "Depth range: ${settings.minDistance}-${settings.maxDistance}mm, smoothing: ${settings.smoothing}"
        )
    }
}
