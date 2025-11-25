package com.selkacraft.alice.coordination

import android.content.Context
import android.hardware.usb.UsbDevice
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import com.selkacraft.alice.comm.core.*
import com.selkacraft.alice.comm.motor.MotorControlManager
import com.selkacraft.alice.comm.realsense.RealSenseManager
import com.selkacraft.alice.comm.uvc.UvcCameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages device registration, connection coordination, and event forwarding.
 * Provides a unified interface for all USB device operations.
 *
 * IMPORTANT: Device connection order matters for USB bandwidth allocation.
 * UVC camera must connect first as it's the primary display device.
 * RealSense can fall back to lower bandwidth modes if needed.
 *
 * Uses state-based sequencing: waits for each device to finish initializing
 * before starting the next one, preventing USB bandwidth contention.
 */
class DeviceCoordinationManager(
    val coordinator: UsbDeviceCoordinator,
    val uvcCameraManager: UvcCameraManager,
    val motorControlManager: MotorControlManager,
    val realSenseManager: RealSenseManager,
    private val scope: CoroutineScope,
    private val onDeviceEvent: (DeviceEvent, LogCategory) -> Unit
) {
    companion object {
        private const val TAG = "DeviceCoordinationManager"

        // Timeout for waiting for device to reach connected state
        private const val DEVICE_CONNECTION_TIMEOUT_MS = 5000L
        // Minimum delay after a device connects before starting next
        private const val POST_CONNECTION_DELAY_MS = 500L
    }
    // Expose individual device states
    val cameraConnectionState: StateFlow<ConnectionState> = uvcCameraManager.connectionState
    val motorConnectionState: StateFlow<ConnectionState> = motorControlManager.connectionState
    val realSenseConnectionState: StateFlow<ConnectionState> = realSenseManager.connectionState
    val coordinatorState: StateFlow<UsbDeviceCoordinator.CoordinatorState> = coordinator.state

    // Expose device-specific states
    val cameraAspectRatio: StateFlow<Float?> = uvcCameraManager.cameraAspectRatio
    val isPreviewActive: StateFlow<Boolean> = uvcCameraManager.isPreviewActive
    val motorPosition: StateFlow<Int> = motorControlManager.currentPosition
    val isMotorCalibrated: StateFlow<Boolean> = motorControlManager.isCalibrated
    val realSenseCenterDepth: StateFlow<Float> = realSenseManager.centerDepth
    val realSenseDepthConfidence: StateFlow<Float> = realSenseManager.depthConfidence
    val realSenseDepthBitmap: StateFlow<android.graphics.Bitmap?> = realSenseManager.depthBitmap
    val realSenseColorBitmap: StateFlow<android.graphics.Bitmap?> = realSenseManager.colorBitmap
    val realSenseMeasurementPosition: StateFlow<Pair<Float, Float>> = realSenseManager.measurementPosition

    // Capabilities
    val cameraCapabilities: StateFlow<DeviceCapabilities?> = uvcCameraManager.capabilities

    // Combined device events
    val allDeviceEvents: Flow<DeviceEvent> = merge(
        coordinator.events,
        uvcCameraManager.deviceEvents,
        motorControlManager.deviceEvents,
        realSenseManager.deviceEvents
    )

    private var isRegistered = false

    init {
        // Register device managers with coordinator
        coordinator.registerDeviceManager(DeviceType.Camera, uvcCameraManager)
        coordinator.registerDeviceManager(DeviceType.Motor, motorControlManager)
        coordinator.registerDeviceManager(DeviceType.RealSense, realSenseManager)

        // Monitor all device events
        scope.launch {
            allDeviceEvents.collect { event ->
                val category = getLogCategoryForEvent(event)
                onDeviceEvent(event, category)

                // When any USB permission is granted, check if other devices can now connect
                if (event is DeviceEvent.PermissionGranted) {
                    checkAndConnectAllAvailableDevices()
                }
            }
        }
    }

    /**
     * Check all device managers for available devices and attempt to connect them.
     * Called when any USB permission is granted to ensure all devices get connected.
     *
     * Uses state-based sequencing: waits for each device to finish connecting
     * before starting the next one to prevent USB bandwidth contention.
     */
    private fun checkAndConnectAllAvailableDevices() {
        scope.launch {
            // Small delay to allow the current device to complete its connection
            kotlinx.coroutines.delay(500)

            android.util.Log.d(TAG, "Checking and connecting available devices (state-based)")

            // 1. Motor controller first - low bandwidth, won't affect others
            motorControlManager.checkAndConnectAvailableDevices()
            waitForDeviceConnection(motorControlManager.connectionState, "Motor")

            // 2. UVC camera second - primary display device, needs priority
            uvcCameraManager.checkAndConnectAvailableDevices()
            waitForDeviceConnection(uvcCameraManager.connectionState, "UVC")

            // 3. RealSense last - can fall back to lower bandwidth modes if needed
            realSenseManager.checkAndConnectAvailableDevices()

            android.util.Log.d(TAG, "Device connection sequence complete")
        }
    }

    /**
     * Register all device managers to start monitoring for devices.
     *
     * Uses state-based sequencing: waits for each device to finish initializing
     * (reach Connected/Active state or timeout) before starting the next one.
     * This prevents USB bandwidth contention during simultaneous initialization.
     */
    fun register() {
        if (isRegistered) return
        isRegistered = true

        android.util.Log.d(TAG, "Registering device managers (state-based sequencing)")

        scope.launch {
            // 1. Motor controller first - low bandwidth, fast to connect
            android.util.Log.d(TAG, "Step 1: Registering motor controller")
            motorControlManager.register()
            waitForDeviceConnection(motorControlManager.connectionState, "Motor")

            // 2. UVC camera second - primary display, needs bandwidth priority
            android.util.Log.d(TAG, "Step 2: Registering UVC camera")
            uvcCameraManager.register()
            waitForDeviceConnection(uvcCameraManager.connectionState, "UVC")

            // 3. RealSense last - has fallback modes for lower bandwidth
            android.util.Log.d(TAG, "Step 3: Registering RealSense")
            realSenseManager.register()

            android.util.Log.d(TAG, "All device managers registered")
        }
    }

    /**
     * Wait for a device to reach Connected/Active state, or timeout.
     * This ensures the device has finished its initialization before we proceed.
     */
    private suspend fun waitForDeviceConnection(
        connectionState: StateFlow<ConnectionState>,
        deviceName: String
    ) {
        val result = withTimeoutOrNull(DEVICE_CONNECTION_TIMEOUT_MS) {
            connectionState.first { state ->
                when (state) {
                    is ConnectionState.Connected,
                    is ConnectionState.Active -> {
                        android.util.Log.d(TAG, "$deviceName reached connected state")
                        true
                    }
                    is ConnectionState.Error -> {
                        android.util.Log.d(TAG, "$deviceName connection failed, proceeding")
                        true // Don't block on error
                    }
                    is ConnectionState.Disconnected -> {
                        // Device not present or not connecting - don't wait
                        if (connectionState.value is ConnectionState.Disconnected) {
                            android.util.Log.d(TAG, "$deviceName not present, proceeding")
                            true
                        } else {
                            false
                        }
                    }
                    else -> false // Keep waiting
                }
            }
        }

        if (result == null) {
            android.util.Log.d(TAG, "$deviceName connection timed out, proceeding")
        }

        // Small delay after connection to let USB settle
        kotlinx.coroutines.delay(POST_CONNECTION_DELAY_MS)
    }

    /**
     * Unregister all device managers
     */
    fun unregister() {
        if (!isRegistered) return
        isRegistered = false

        uvcCameraManager.unregister()
        motorControlManager.unregister()
        realSenseManager.unregister()
    }

    /**
     * Check if managers are registered
     */
    fun isRegistered(): Boolean = isRegistered

    /**
     * Get device capabilities by type
     */
    fun getDeviceCapabilities(deviceType: DeviceType): StateFlow<DeviceCapabilities?> {
        return when (deviceType) {
            DeviceType.Camera -> uvcCameraManager.capabilities
            DeviceType.Motor -> motorControlManager.capabilities
            DeviceType.RealSense -> realSenseManager.capabilities
            else -> MutableStateFlow(null)
        }
    }

    /**
     * Check device activity status
     */
    fun isDeviceTypeActive(type: DeviceType): Boolean {
        return coordinator.isDeviceTypeActive(type)
    }

    /**
     * Get connected devices by type
     */
    fun getConnectedDevicesByType(type: DeviceType): List<UsbDeviceCoordinator.DeviceInfo> {
        return coordinator.getDevicesOfType(type)
    }

    /**
     * Get all connected devices
     */
    fun getAllConnectedDevices(): List<UsbDeviceCoordinator.DeviceInfo> {
        return coordinatorState.value.connectedDevices.values.toList()
    }

    /**
     * Get all active devices
     */
    fun getAllActiveDevices(): List<UsbDeviceCoordinator.DeviceInfo> {
        return coordinatorState.value.activeDevices.values.toList()
    }

    /**
     * Determine log category for device event
     */
    private fun getLogCategoryForEvent(event: DeviceEvent): LogCategory {
        val device = when (event) {
            is DeviceEvent.Discovered -> event.device
            is DeviceEvent.PermissionGranted -> event.device
            is DeviceEvent.PermissionDenied -> event.device
            is DeviceEvent.Connected -> event.device
            is DeviceEvent.ConnectionFailed -> event.device
            is DeviceEvent.Disconnected -> event.device
            is DeviceEvent.StateChanged -> event.device
            is DeviceEvent.DataReceived -> event.device
            is DeviceEvent.Error -> event.device
        }

        return when {
            device == null -> LogCategory.USB
            isDeviceOfType(device, uvcCameraManager) -> LogCategory.CAMERA
            isDeviceOfType(device, motorControlManager) -> LogCategory.MOTOR
            isDeviceOfType(device, realSenseManager) -> LogCategory.REALSENSE
            else -> LogCategory.USB
        }
    }

    /**
     * Check if device belongs to a specific manager
     */
    private fun isDeviceOfType(device: UsbDevice, manager: BaseUsbDeviceManager<*>): Boolean {
        return manager.currentDevice?.deviceId == device.deviceId
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        unregister()
        uvcCameraManager.destroy()
        motorControlManager.destroy()
        realSenseManager.destroy()
        coordinator.destroy()
    }

    /**
     * Log categories for device events
     */
    enum class LogCategory {
        SYSTEM, USB, CAMERA, MOTOR, REALSENSE, AUTOFOCUS, ERROR
    }
}
