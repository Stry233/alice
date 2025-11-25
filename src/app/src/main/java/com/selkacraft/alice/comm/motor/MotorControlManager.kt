package com.selkacraft.alice.comm.motor

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import com.selkacraft.alice.comm.core.*
import com.selkacraft.alice.util.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages communication with the focus motor controller over USB serial.
 *
 * Provides position control (0-4095 range) for adjusting lens focus,
 * along with calibration and status monitoring. Communicates via IEEE 802.15.4
 * wireless protocol to the motor hardware.
 */
class MotorControlManager(
    context: Context,
    coordinator: UsbDeviceCoordinator,
    scope: CoroutineScope,
    config: DeviceConfig = DeviceConfig()
) : BaseUsbDeviceManager<MotorConnection>(
    context,
    DeviceType.Motor,
    scope,
    coordinator,
    config
) {
    companion object {
        private const val TAG = "MotorControlManager"
    }

    // Component instances
    private val stateManager = MotorStateManager(context, scope)
    private val serialHandler = MotorSerialHandler(context.getSystemService(Context.USB_SERVICE) as UsbManager)
    private val commandProcessor = MotorCommandProcessor(scope, serialHandler)
    private val usbHandler = MotorUsbHandler(context, context.getSystemService(Context.USB_SERVICE) as UsbManager)
    private val settingsManager = SettingsManager(context)

    // Expose state flows
    val currentPosition: StateFlow<Int> = stateManager.currentPosition
    val isCalibrated: StateFlow<Boolean> = stateManager.isCalibrated

    // Track if manager is destroyed
    private var isDestroyed = false

    init {
        // Monitor USB events
        scope.launch {
            usbHandler.events.collect { event ->
                handleUsbEvent(event)
            }
        }

        // Monitor serial responses
        scope.launch {
            serialHandler.responses.collect { response ->
                handleSerialResponse(response)
            }
        }

        // Monitor serial errors
        scope.launch {
            serialHandler.errors.collect { error ->
                handleSerialError(error)
            }
        }

        // Monitor connection state changes
        scope.launch {
            connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }
    }

    override suspend fun canHandleDevice(device: UsbDevice): Boolean {
        return usbHandler.isMotorController(device)
    }

    override suspend fun openConnection(device: UsbDevice): MotorConnection {
        Log.d(TAG, "Opening connection to motor controller: ${device.deviceName}")
        return serialHandler.openConnection(device)
    }

    override suspend fun closeConnection(connection: MotorConnection) {
        Log.d(TAG, "Closing motor connection")
        commandProcessor.stop()
        serialHandler.closeConnection(connection)
    }

    override suspend fun queryCapabilities(connection: MotorConnection): DeviceCapabilities {
        return DeviceCapabilities(
            supportedCommands = MotorCapabilities().supportedCommands,
            customCapabilities = mapOf(
                "minPosition" to MotorCapabilities().minPosition,
                "maxPosition" to MotorCapabilities().maxPosition,
                "hasEncoder" to MotorCapabilities().hasEncoder
            )
        )
    }

    override suspend fun performHealthCheck(connection: MotorConnection): Boolean {
        return serialHandler.isConnectionHealthy()
    }

    override suspend fun connect(device: UsbDevice): Boolean {
        val connected = super.connect(device)
        if (connected) {
            commandProcessor.start()
            // Send initial status request
            sendCommand(MotorProtocol.CMD_STATUS, priority = CommandPriority.LOW)
            Log.d(TAG, "Motor controller connected and initialized")
        }
        return connected
    }

    override suspend fun disconnect() {
        commandProcessor.stop()
        super.disconnect()
    }

    /**
     * Register the manager to start monitoring for devices
     */
    fun register() {
        if (isDestroyed) {
            Log.w(TAG, "Cannot register - manager is destroyed")
            return
        }

        usbHandler.register()

        scope.launch {
            coordinator.scanDevices()

            // Auto-connect to first available motor
            val motors = coordinator.getDevicesOfType(DeviceType.Motor)
            motors.firstOrNull { !it.isInUse }?.let { deviceInfo ->
                requestPermissionAndConnect(deviceInfo.device)
            }
        }
    }

    /**
     * Unregister the manager
     */
    fun unregister() {
        usbHandler.unregister()
        commandProcessor.stop()
    }

    /**
     * Check for available devices and attempt to connect if not already connected.
     * Called by coordinator when any USB permission is granted.
     */
    suspend fun checkAndConnectAvailableDevices() {
        if (isDestroyed || currentDevice != null) {
            return
        }

        Log.d(TAG, "Checking for available motor controllers")
        coordinator.scanDevices()

        val motors = coordinator.getDevicesOfType(DeviceType.Motor)
        motors.firstOrNull { !it.isInUse }?.let { deviceInfo ->
            Log.d(TAG, "Found unconnected motor controller: ${deviceInfo.device.deviceName}")
            requestPermissionAndConnect(deviceInfo.device)
        }
    }

    /**
     * Send a command to the motor controller
     */
    fun sendCommand(
        command: String,
        callback: ((String) -> Unit)? = null,
        priority: CommandPriority = CommandPriority.NORMAL
    ) {
        if (isDestroyed || deviceConnection == null) {
            Log.w(TAG, "Cannot send command '$command' - not connected or destroyed")
            return
        }

        scope.launch {
            val motorCommand = MotorCommand(command, callback, priority)
            commandProcessor.sendCommand(motorCommand)
        }
    }

    /**
     * Set motor position
     */
    fun setPosition(position: Int) {
        val clampedPosition = MotorProtocol.clampPosition(position)

        // Check if we should send this command (deduplication)
        if (!stateManager.shouldSendPositionCommand(clampedPosition)) {
            return
        }

        // Send position command with high priority
        sendCommand("${MotorProtocol.CMD_POSITION} $clampedPosition", priority = CommandPriority.HIGH)

        // Update position immediately for UI responsiveness
        stateManager.updatePosition(clampedPosition)
    }

    /**
     * Scan a specific address to test if a motor responds at that address
     * Used for motor discovery via binary search
     */
    fun scanAddress(address: Int) {
        val command = MotorProtocol.formatScanCommand(address)
        Log.d(TAG, "Scanning address: 0x${MotorProtocol.formatAddressHex(address)}")
        sendCommand(command, priority = CommandPriority.HIGH)
    }

    /**
     * Set the destination address for motor communication
     * This tells the firmware which IEEE 802.15.4 address to send packets to
     */
    fun setDestination(address: Int) {
        val command = MotorProtocol.formatDestCommand(address)
        Log.d(TAG, "Setting destination address: 0x${MotorProtocol.formatAddressHex(address)}")
        sendCommand(command, priority = CommandPriority.HIGH)
    }

    /**
     * Get the current destination address from the firmware
     */
    fun getDestination(callback: ((Int?) -> Unit)? = null) {
        sendCommand(MotorProtocol.CMD_GETDEST, callback = { response ->
            val parsed = MotorProtocol.parseResponse(response)
            if (parsed is MotorResponse.Destination) {
                callback?.invoke(parsed.address)
            } else {
                callback?.invoke(null)
            }
        }, priority = CommandPriority.NORMAL)
    }

    /**
     * Request permission and connect to device
     */
    private suspend fun requestPermissionAndConnect(device: UsbDevice) {
        if (usbHandler.hasPermission(device)) {
            connect(device)
        } else {
            updateState(ConnectionState.AwaitingPermission)
            usbHandler.requestPermission(device)
        }
    }

    /**
     * Handle USB events from the USB handler
     * Updated to use BaseUsbHandler.UsbEvent types
     */
    private suspend fun handleUsbEvent(event: BaseUsbHandler.UsbEvent) {
        when (event) {
            is BaseUsbHandler.UsbEvent.PermissionGranted -> {
                emitEvent(DeviceEvent.PermissionGranted(event.device))
                connect(event.device)
                // Trigger global rescan so other device managers can connect their devices
                coordinator.rescanAndConnectAllDevices()
            }
            is BaseUsbHandler.UsbEvent.PermissionDenied -> {
                emitEvent(DeviceEvent.PermissionDenied(event.device))
                updateState(ConnectionState.Error("Permission denied", false))
            }
            is BaseUsbHandler.UsbEvent.DeviceAttached -> {
                coordinator.scanDevices()
                if (config.autoReconnect && currentDevice == null) {
                    requestPermissionAndConnect(event.device)
                }
            }
            is BaseUsbHandler.UsbEvent.DeviceDetached -> {
                if (event.device.deviceId == currentDevice?.deviceId) {
                    disconnect()
                    coordinator.onDeviceDisconnected(event.device)
                }
            }
        }
    }

    /**
     * Handle serial responses
     */
    private suspend fun handleSerialResponse(response: String) {
        val parsed = MotorProtocol.parseResponse(response)

        when (parsed) {
            is MotorResponse.Position -> {
                stateManager.updatePosition(parsed.position)
                Log.d(TAG, "Motor position updated: ${parsed.position}")
            }
            is MotorResponse.Status -> {
                stateManager.updatePosition(parsed.position)
                Log.d(TAG, "Motor status: ${parsed.fullStatus}")
            }
            is MotorResponse.Calibrated -> {
                stateManager.setCalibrated(true)
            }
            is MotorResponse.Ready -> {
                Log.d(TAG, "Motor is ready")
                if (connectionState.value !is ConnectionState.Active) {
                    updateState(ConnectionState.Active)
                    // Send saved destination address to firmware on connect
                    val savedDestination = settingsManager.getMotorDestinationAddress()
                    if (settingsManager.getMotorDestinationDiscovered()) {
                        Log.d(TAG, "Setting saved destination: 0x${MotorProtocol.formatAddressHex(savedDestination)}")
                        setDestination(savedDestination)
                    }
                }
            }
            is MotorResponse.Destination -> {
                Log.d(TAG, "Motor destination: 0x${MotorProtocol.formatAddressHex(parsed.address)}")
            }
            is MotorResponse.ScanComplete -> {
                Log.d(TAG, "Scan complete for address: 0x${MotorProtocol.formatAddressHex(parsed.address)}")
            }
            is MotorResponse.Error -> {
                Log.e(TAG, "Motor error: ${parsed.message}")
                emitEvent(DeviceEvent.Error(currentDevice, Exception("Motor error: ${parsed.message}")))
            }
            is MotorResponse.Unknown -> {
                Log.v(TAG, "Unknown response: ${parsed.raw}")
            }
        }
    }

    /**
     * Handle serial communication errors
     */
    private suspend fun handleSerialError(error: Exception) {
        Log.e(TAG, "Serial error: ${error.message}", error)

        // Check if device is still connected
        val deviceStillConnected = currentDevice?.let { device ->
            usbHandler.getAttachedMotorControllers().any { it.deviceId == device.deviceId }
        } ?: false

        if (!isDestroyed && config.autoReconnect && deviceStillConnected) {
            emitEvent(DeviceEvent.Error(currentDevice, error))
            currentDevice?.let { device ->
                disconnect()
                delay(config.reconnectDelayMs)
                connect(device)
            }
        } else {
            disconnect()
        }
    }

    /**
     * Handle connection state changes
     */
    private fun handleConnectionStateChange(state: ConnectionState) {
        Log.d(TAG, "Connection state changed to: $state")

        when (state) {
            is ConnectionState.Connected -> {
                // Connection established, processor should be started in connect()
            }
            is ConnectionState.Active -> {
                // Fully operational
            }
            is ConnectionState.Disconnected -> {
                commandProcessor.stop()
            }
            is ConnectionState.Error -> {
                commandProcessor.stop()
            }
            else -> {}
        }
    }

    override fun destroy() {
        isDestroyed = true
        commandProcessor.stop()
        stateManager.destroy()
        unregister()
        super.destroy()
    }
}