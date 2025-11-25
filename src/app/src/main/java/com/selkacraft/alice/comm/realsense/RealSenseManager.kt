package com.selkacraft.alice.comm.realsense

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import com.selkacraft.alice.comm.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages Intel RealSense depth camera communication and frame processing.
 *
 * Captures depth and color frames at 60 FPS, providing real-time depth
 * measurements for autofocus and visualization bitmaps for the UI.
 * Uses Kalman filtering for stable depth readings.
 */
class RealSenseManager(
    context: Context,
    coordinator: UsbDeviceCoordinator,
    scope: CoroutineScope,
    config: DeviceConfig = DeviceConfig(
        reconnectDelayMs = 2000,
        minReconnectDelayMs = 1000
    )
) : BaseUsbDeviceManager<RealSenseConnection>(
    context,
    DeviceType.RealSense,
    scope,
    coordinator,
    config
) {
    companion object {
        private const val TAG = "RealSenseManager"
    }

    // Component instances
    private val stateManager = RealSenseStateManager(scope)
    private val pipelineHandler = RealSensePipelineHandler(context)
    private val frameProcessor = RealSenseFrameProcessor(scope)
    private val usbHandler = RealSenseUsbHandler(
        context,
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    )

    // Configuration
    private var currentStreamConfig = RealSenseStreamConfig()

    // Connection control
    private val connectionMutex = Mutex()
    private var lastDisconnectionTime = 0L
    private var isDisconnecting = false
    private var resetJob: Job? = null

    // Track if manager is destroyed
    private var isDestroyed = false

    // Expose state flows from state manager
    val centerDepth: StateFlow<Float> = stateManager.centerDepth
    val depthConfidence: StateFlow<Float> = stateManager.depthConfidence
    val depthBitmap: StateFlow<Bitmap?> = stateManager.depthBitmap
    val colorBitmap: StateFlow<Bitmap?> = stateManager.colorBitmap  // For face detection
    val measurementPosition: StateFlow<Pair<Float, Float>> = stateManager.measurementPosition

    init {
        // Monitor USB events
        scope.launch {
            usbHandler.events.collect { event ->
                handleUsbEvent(event)
            }
        }

        // Monitor frame processor output
        scope.launch {
            frameProcessor.processedFrames.collect { frame ->
                handleProcessedFrame(frame)
            }
        }

        // Monitor frame processor errors
        scope.launch {
            frameProcessor.errors.collect { error ->
                handleFrameProcessorError(error)
            }
        }

        // Monitor connection state changes
        scope.launch {
            connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }

        // Monitor coordinator events for device disconnection
        scope.launch {
            coordinator.events.collect { event ->
                if (event is DeviceEvent.Disconnected &&
                    event.device?.deviceId == currentDevice?.deviceId) {
                    Log.d(TAG, "Device physically disconnected")
                    handlePhysicalDisconnection()
                }
            }
        }
    }

    override suspend fun canHandleDevice(device: UsbDevice): Boolean {
        // Check if this is a RealSense device
        val isRealSense = usbHandler.isRealSenseDevice(device)

        if (!isRealSense) {
            return false
        }

        // Reset disconnecting flag if needed
        if (isDisconnecting) {
            Log.d(TAG, "RealSense device detected while disconnecting, resetting state")
            resetJob?.cancel()
            isDisconnecting = false
            pipelineHandler.stopPipeline()
        }

        // Brief delay before reconnection
        val timeSinceDisconnection = System.currentTimeMillis() - lastDisconnectionTime
        if (timeSinceDisconnection < RealSenseParameters.RECONNECTION_MIN_DELAY_MS) {
            Log.d(TAG, "Waiting before reconnection ($timeSinceDisconnection ms)")
            delay(RealSenseParameters.RECONNECTION_MIN_DELAY_MS - timeSinceDisconnection)
        }

        return true
    }

    override suspend fun openConnection(device: UsbDevice): RealSenseConnection {
        return withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                Log.d(TAG, "Opening RealSense connection")

                // Reset flags
                isDisconnecting = false

                // Create pipeline using handler
                val connection = pipelineHandler.createPipeline(device, currentStreamConfig)
                    ?: throw IllegalStateException("Failed to create RealSense pipeline")

                // Update state manager with dimensions
                stateManager.updateFrameDimensions(
                    connection.depthWidth,
                    connection.depthHeight
                )

                connection
            }
        }
    }

    override suspend fun closeConnection(connection: RealSenseConnection) {
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                Log.d(TAG, "Closing RealSense connection")

                // Stop frame processing
                frameProcessor.stopCapture()

                // Stop pipeline
                pipelineHandler.stopPipeline()

                // Clear state
                stateManager.clearDepthData()

                // Set disconnection time
                lastDisconnectionTime = System.currentTimeMillis()

                // Reset flags
                isDisconnecting = false

                Log.d(TAG, "RealSense connection closed")
            }
        }
    }

    override suspend fun queryCapabilities(connection: RealSenseConnection): DeviceCapabilities {
        return DeviceCapabilities(
            supportedResolutions = listOf(
                Resolution(424, 240),
                Resolution(640, 480)
            ),
            maxDataRate = 400,
            requiresExclusiveAccess = true,
            customCapabilities = mapOf(
                "streamType" to "RGB+Depth",
                "depthScale" to connection.depthScale,
                "processingMode" to "Optimized"
            )
        )
    }

    override suspend fun performHealthCheck(connection: RealSenseConnection): Boolean {
        return stateManager.isCapturing.value &&
                pipelineHandler.isPipelineActive() &&
                !isDisconnecting
    }

    override suspend fun connect(device: UsbDevice): Boolean {
        val connected = super.connect(device)
        if (connected) {
            // Start frame capture after successful connection
            deviceConnection?.let { connection ->
                frameProcessor.startCapture(connection, stateManager.measurementPosition)
                stateManager.setCapturing(true)
                stateManager.setPipelineActive(true)
            }
            Log.d(TAG, "RealSense connected and capturing")
        }
        return connected
    }

    override suspend fun disconnect() {
        frameProcessor.stopCapture()
        stateManager.setCapturing(false)
        stateManager.setPipelineActive(false)
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

        // Reset flags
        isDisconnecting = false

        usbHandler.register()

        scope.launch {
            delay(500)
            coordinator.scanDevices()

            // Auto-connect to first available RealSense
            val devices = coordinator.getDevicesOfType(DeviceType.RealSense)
            devices.firstOrNull { !it.isInUse }?.let { deviceInfo ->
                requestPermissionAndConnect(deviceInfo.device)
            }
        }
    }

    /**
     * Unregister the manager
     */
    fun unregister() {
        resetJob?.cancel()

        scope.launch {
            disconnect()
            isDisconnecting = false
        }

        usbHandler.unregister()
    }

    /**
     * Check for available devices and attempt to connect if not already connected.
     * Called by coordinator when any USB permission is granted.
     */
    suspend fun checkAndConnectAvailableDevices() {
        if (isDestroyed || currentDevice != null) {
            return
        }

        Log.d(TAG, "Checking for available RealSense devices")
        coordinator.scanDevices()

        val devices = coordinator.getDevicesOfType(DeviceType.RealSense)
        devices.firstOrNull { !it.isInUse }?.let { deviceInfo ->
            Log.d(TAG, "Found unconnected RealSense: ${deviceInfo.device.deviceName}")
            requestPermissionAndConnect(deviceInfo.device)
        }
    }

    /**
     * Set measurement position for depth calculation
     */
    fun setMeasurementPosition(normalizedX: Float, normalizedY: Float) {
        stateManager.setMeasurementPosition(normalizedX, normalizedY)
    }

    /**
     * Get current stream configuration
     */
    fun getStreamConfig(): RealSenseStreamConfig = currentStreamConfig

    /**
     * Update stream configuration (requires reconnection)
     */
    fun updateStreamConfig(config: RealSenseStreamConfig) {
        currentStreamConfig = config
        // Would need to reconnect to apply new config
        scope.launch {
            currentDevice?.let { device ->
                disconnect()
                delay(500)
                connect(device)
            }
        }
    }

    /**
     * Check if actively capturing
     */
    fun isCapturing(): Boolean = stateManager.isCapturing.value

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
                    handlePhysicalDisconnection()
                }
            }
        }
    }

    /**
     * Handle processed frames from the frame processor
     */
    private suspend fun handleProcessedFrame(frame: RealSenseFrameProcessor.ProcessedFrame) {
        // Update depth measurement
        frame.depthMeasurement?.let { measurement ->
            stateManager.updateDepthMeasurement(measurement.depth, measurement.confidence)

            // Emit depth data event
            currentDevice?.let { device ->
                emitEvent(DeviceEvent.DataReceived(
                    device,
                    DepthData(measurement.depth, measurement.confidence)
                ))
            }
        }

        // Update bitmaps
        frame.colorBitmap?.let { bitmap ->
            // Update depth bitmap for visualization
            stateManager.updateDepthBitmap(bitmap)

            // Update color bitmap for face detection
            stateManager.updateColorBitmap(bitmap)

            // Update state to Active on first bitmap
            if (connectionState.value is ConnectionState.Connected) {
                updateState(ConnectionState.Active)
            }
        }
    }

    /**
     * Handle frame processor errors
     */
    private suspend fun handleFrameProcessorError(error: Exception) {
        Log.e(TAG, "Frame processor error: ${error.message}", error)

        if (error.message?.contains("Device disconnected") == true) {
            handlePhysicalDisconnection()
        } else if (!isDestroyed && config.autoReconnect) {
            emitEvent(DeviceEvent.Error(currentDevice, error))
            // Attempt reconnection
            currentDevice?.let { device ->
                disconnect()
                delay(config.reconnectDelayMs)
                connect(device)
            }
        }
    }

    /**
     * Handle connection state changes
     */
    private fun handleConnectionStateChange(state: ConnectionState) {
        Log.d(TAG, "Connection state changed to: ${state::class.simpleName}")

        when (state) {
            is ConnectionState.Connected -> {
                // Frame capture should start automatically in connect()
            }
            is ConnectionState.Active -> {
                // Fully operational
            }
            is ConnectionState.Disconnected,
            is ConnectionState.Error -> {
                frameProcessor.stopCapture()
                stateManager.clearDepthData()
                stateManager.setCapturing(false)
                stateManager.setPipelineActive(false)
                isDisconnecting = false
            }
            else -> {}
        }
    }

    /**
     * Handle physical device disconnection
     */
    private suspend fun handlePhysicalDisconnection() {
        isDisconnecting = true
        resetJob?.cancel()

        // Stop capture and cleanup
        frameProcessor.stopCapture()
        pipelineHandler.stopPipeline()

        // Update state
        updateState(ConnectionState.Disconnected)
        stateManager.reset()

        // Clean up device references
        lastDisconnectionTime = System.currentTimeMillis()
        currentDevice = null
        deviceConnection = null
        _capabilities.value = null

        // Reset flag after delay
        resetJob = scope.launch {
            delay(1000)
            isDisconnecting = false
            Log.d(TAG, "RealSense manager reset, ready for reconnection")
        }
    }

    override fun destroy() {
        isDestroyed = true
        resetJob?.cancel()

        // Clean up all components
        frameProcessor.destroy()
        stateManager.destroy()

        scope.launch {
            pipelineHandler.destroy()
        }

        unregister()
        isDisconnecting = false

        super.destroy()
    }
}