package com.selkacraft.alice.comm

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.selkacraft.alice.comm.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsbDeviceCoordinator(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    companion object {
        private const val TAG = "UsbDeviceCoordinator"
        private const val MAX_USB_BANDWIDTH_MBPS = 3200 // USB 3.0 practical limit
    }

    data class DeviceInfo(
        val device: UsbDevice,
        val type: DeviceType,
        val isConnected: Boolean = false,
        val isInUse: Boolean = false,
        val capabilities: DeviceCapabilities? = null
    )

    data class CoordinatorState(
        val connectedDevices: Map<String, DeviceInfo> = emptyMap(),
        val activeDevices: Map<String, DeviceInfo> = emptyMap(),
        val conflicts: List<String> = emptyList(),
        val totalBandwidthUsed: Int = 0
    )

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val deviceMutex = Mutex()
    private val registry = DeviceRegistry()

    private val _state = MutableStateFlow(CoordinatorState())
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DeviceEvent>()
    val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    init {
        // Set up standard matchers
        setupStandardMatchers()

        // Monitor device events from all registered handlers
        scope.launch {
            registry.getAllHandlers().forEach { (_, handler) ->
                handler.deviceEvents.collect { event ->
                    _events.emit(event)

                    // Update active status when device state changes
                    when (event) {
                        is DeviceEvent.StateChanged -> {
                            if (event.newState is ConnectionState.Active ||
                                event.newState is ConnectionState.Connected) {
                                // Mark device as active when it becomes connected/active
                                event.device?.let { device ->
                                    updateDeviceActiveStatus(device, true)
                                }
                            }
                        }
                        is DeviceEvent.Connected -> {
                            // Also mark as active on connection
                            event.device?.let { device ->
                                updateDeviceActiveStatus(device, true)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun setupStandardMatchers() {
        // IMPORTANT: RealSense matchers MUST come before UVC matchers
        // This ensures RealSense devices are identified correctly first

        // Clear any existing matchers first
        registry.clearMatchers()

        // RealSense cameras - HIGH PRIORITY
        registry.addMatcher(DeviceRegistry.VendorProductMatcher(
            0x8086,
            setOf(0x0B07, 0x0B3A, 0x0B5C, 0x0B64, 0x0AD1, 0x0AD2, 0x0B52,
                0x0B3D, 0x0AFE, 0x0B5B),
            DeviceType.RealSense
        ))

        // Additional Intel vendor check for any RealSense device
        registry.addMatcher(object : DeviceRegistry.DeviceMatcher {
            override fun matches(device: UsbDevice): DeviceType? {
                if (device.vendorId == 0x8086) {
                    // Check if device name contains RealSense
                    val deviceName = device.productName?.lowercase() ?: device.deviceName.lowercase()
                    if (deviceName.contains("realsense") || deviceName.contains("intel realsense")) {
                        return DeviceType.RealSense
                    }
                }
                return null
            }
        })

        // UVC cameras - LOWER PRIORITY (after RealSense)
        registry.addMatcher(object : DeviceRegistry.DeviceMatcher {
            override fun matches(device: UsbDevice): DeviceType? {
                // Skip Intel devices entirely for UVC matching
                if (device.vendorId == 0x8086) {
                    return null
                }

                // Check for UVC interface
                for (i in 0 until device.interfaceCount) {
                    if (device.getInterface(i).interfaceClass == 14) {
                        return DeviceType.Camera
                    }
                }
                return null
            }
        })

        // Motor controllers
        registry.addMatcher(DeviceRegistry.VendorProductMatcher(
            0x2FE3,
            setOf(0x0100),
            DeviceType.Motor
        ))
    }

    fun registerDeviceManager(deviceType: DeviceType, manager: BaseUsbDeviceManager<*>) {
        registry.register(deviceType, manager)

        // Re-scan devices when a new manager is registered
        scope.launch {
            scanDevices()
        }
    }

    suspend fun scanDevices(): Map<DeviceType, List<UsbDevice>> {
        return deviceMutex.withLock {
            val devices = usbManager.deviceList.values
            val categorizedDevices = mutableMapOf<DeviceType, MutableList<UsbDevice>>()

            devices.forEach { device ->
                registry.identifyDevice(device)?.let { deviceType ->
                    categorizedDevices.getOrPut(deviceType) { mutableListOf() }.add(device)
                }
            }

            updateState { currentState ->
                val newConnectedDevices = mutableMapOf<String, DeviceInfo>()

                categorizedDevices.forEach { (type, deviceList) ->
                    deviceList.forEach { device ->
                        val key = "${type.id}_${device.deviceId}"
                        // Preserve active state if device was already active
                        val wasActive = currentState.activeDevices.containsKey(key)
                        newConnectedDevices[key] = DeviceInfo(
                            device = device,
                            type = type,
                            isConnected = true,
                            isInUse = wasActive || (currentState.connectedDevices[key]?.isInUse ?: false),
                            capabilities = currentState.connectedDevices[key]?.capabilities
                        )

                        // If device was active, keep it in active devices
                        if (wasActive) {
                            Log.d(TAG, "Preserving active state for device: $key")
                        }
                    }
                }

                currentState.copy(connectedDevices = newConnectedDevices)
            }

            categorizedDevices
        }
    }

    suspend fun requestDevice(device: UsbDevice, type: DeviceType): Boolean {
        return deviceMutex.withLock {
            val key = "${type.id}_${device.deviceId}"
            val deviceInfo = _state.value.connectedDevices[key]

            if (deviceInfo == null) {
                Log.e(TAG, "Device not found in connected devices: $key")
                return@withLock false
            }

            if (deviceInfo.isInUse) {
                // Device already in use - but this might be a reconnection
                // Check if it's the same device type requesting it again
                Log.d(TAG, "Device already marked as in use: $key, allowing re-acquisition")
            }

            // Check for conflicts
            val conflictReasons = checkConflicts(device, type)
            if (conflictReasons.isNotEmpty()) {
                conflictReasons.forEach { addConflict(it) }
                return@withLock false
            }

            updateState { currentState ->
                val updatedConnected = currentState.connectedDevices.toMutableMap()
                val updatedActive = currentState.activeDevices.toMutableMap()

                updatedConnected[key] = deviceInfo.copy(isInUse = true)
                updatedActive[key] = deviceInfo.copy(isInUse = true)

                currentState.copy(
                    connectedDevices = updatedConnected,
                    activeDevices = updatedActive,
                    totalBandwidthUsed = calculateTotalBandwidth(updatedActive.values)
                )
            }

            Log.i(TAG, "Device acquired: $key")
            true
        }
    }

    suspend fun releaseDevice(device: UsbDevice, type: DeviceType) {
        deviceMutex.withLock {
            val key = "${type.id}_${device.deviceId}"

            updateState { currentState ->
                val updatedConnected = currentState.connectedDevices.toMutableMap()
                val updatedActive = currentState.activeDevices.toMutableMap()

                updatedConnected[key]?.let {
                    updatedConnected[key] = it.copy(isInUse = false)
                }
                updatedActive.remove(key)

                currentState.copy(
                    connectedDevices = updatedConnected,
                    activeDevices = updatedActive,
                    totalBandwidthUsed = calculateTotalBandwidth(updatedActive.values)
                )
            }

            Log.i(TAG, "Device released: $key")
        }
    }

    private fun checkConflicts(device: UsbDevice, type: DeviceType): List<String> {
        val conflicts = mutableListOf<String>()
        val activeDevices = _state.value.activeDevices

        // Type-specific conflict rules
        when (type) {
            is DeviceType.Camera -> {
                if (activeDevices.any { it.value.type is DeviceType.Camera }) {
                    conflicts.add("Only one UVC camera can be active at a time")
                }
            }
            is DeviceType.RealSense -> {
                if (activeDevices.any { it.value.type is DeviceType.RealSense }) {
                    // Allow re-acquisition of the same device
                    val existingDevice = activeDevices.values.find { it.type is DeviceType.RealSense }
                    if (existingDevice?.device?.deviceId != device.deviceId) {
                        conflicts.add("Only one RealSense camera can be active at a time")
                    }
                }
            }
            else -> {
                // Custom conflict rules can be added here
            }
        }

        // Bandwidth check
        val currentBandwidth = calculateTotalBandwidth(activeDevices.values)
        val requiredBandwidth = type.requiredBandwidth

        if (currentBandwidth + requiredBandwidth > MAX_USB_BANDWIDTH_MBPS) {
            conflicts.add("Insufficient USB bandwidth (need ${requiredBandwidth}Mbps, have ${MAX_USB_BANDWIDTH_MBPS - currentBandwidth}Mbps available)")
        }

        return conflicts
    }

    private fun calculateTotalBandwidth(devices: Collection<DeviceInfo>): Int {
        return devices.sumOf { it.type.requiredBandwidth }
    }

    private fun addConflict(message: String) {
        updateState { currentState ->
            currentState.copy(
                conflicts = (currentState.conflicts + message).takeLast(10)
            )
        }
        Log.w(TAG, "Conflict: $message")
    }

    fun clearConflicts() {
        updateState { it.copy(conflicts = emptyList()) }
    }

    suspend fun onDeviceDisconnected(device: UsbDevice) {
        deviceMutex.withLock {
            val keysToRemove = _state.value.connectedDevices.entries
                .filter { it.value.device.deviceId == device.deviceId }
                .map { it.key }

            updateState { currentState ->
                val updatedConnected = currentState.connectedDevices.toMutableMap()
                val updatedActive = currentState.activeDevices.toMutableMap()

                keysToRemove.forEach { key ->
                    updatedConnected.remove(key)
                    updatedActive.remove(key)
                }

                currentState.copy(
                    connectedDevices = updatedConnected,
                    activeDevices = updatedActive,
                    totalBandwidthUsed = calculateTotalBandwidth(updatedActive.values)
                )
            }

            Log.i(TAG, "Device disconnected: ${device.deviceName}")
        }
    }

    // New method to update device active status
    private suspend fun updateDeviceActiveStatus(device: UsbDevice, isActive: Boolean) {
        deviceMutex.withLock {
            // Find the device in connected devices
            val deviceEntry = _state.value.connectedDevices.entries.find {
                it.value.device.deviceId == device.deviceId
            }

            deviceEntry?.let { entry ->
                val key = entry.key
                val deviceInfo = entry.value

                Log.d(TAG, "Updating device active status: $key to $isActive")

                updateState { currentState ->
                    val updatedActive = currentState.activeDevices.toMutableMap()

                    if (isActive) {
                        // Add to active devices
                        updatedActive[key] = deviceInfo.copy(isInUse = true)
                    } else {
                        // Remove from active devices
                        updatedActive.remove(key)
                    }

                    currentState.copy(
                        activeDevices = updatedActive,
                        totalBandwidthUsed = calculateTotalBandwidth(updatedActive.values)
                    )
                }
            }
        }
    }

    fun getDevicesOfType(type: DeviceType): List<DeviceInfo> {
        return _state.value.connectedDevices.values.filter { it.type == type }
    }

    fun isDeviceTypeActive(type: DeviceType): Boolean {
        val isActive = _state.value.activeDevices.any { it.value.type == type }
        // Log.d(TAG, "Checking if ${type.displayName} is active: $isActive (active devices: ${_state.value.activeDevices.keys})")
        return isActive
    }

    private fun updateState(update: (CoordinatorState) -> CoordinatorState) {
        _state.update { currentState ->
            update(currentState)
        }
    }

    /**
     * Rescan all devices and attempt to connect to any that are not yet connected.
     * This is called when any USB permission is granted to ensure all devices get connected.
     */
    suspend fun rescanAndConnectAllDevices() {
        Log.i(TAG, "Rescanning all devices after permission grant")

        // First, scan to update device list
        val devices = scanDevices()

        // For each device type that has a registered handler, check for devices needing connection
        devices.forEach { (deviceType, deviceList) ->
            deviceList.forEach { device ->
                val key = "${deviceType.id}_${device.deviceId}"
                val deviceInfo = _state.value.connectedDevices[key]

                // If device is not currently in use and not actively being managed,
                // the handler should pick it up
                if (deviceInfo != null && !deviceInfo.isInUse) {
                    Log.d(TAG, "Found device not in use: ${deviceType.displayName} - ${device.deviceName}")
                    // The device will be picked up by the registered handler's auto-connect logic
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}