package com.selkacraft.alice.comm.uvc

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import com.selkacraft.alice.comm.core.*
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages UVC camera connections by orchestrating specialized components
 */
class UvcCameraManager(
    context: Context,
    coordinator: UsbDeviceCoordinator,
    scope: CoroutineScope,
    private var preferredResolution: Resolution = Resolution(1920, 1080),
    config: DeviceConfig = DeviceConfig()
) : BaseUsbDeviceManager<UvcCameraConnection>(
    context,
    DeviceType.Camera,
    scope,
    coordinator,
    config
), UvcCameraCommandProcessor.CommandHandler {

    companion object {
        private const val TAG = "UvcCameraManager"
        private const val MIN_RECONNECT_DELAY_MS = 1000L // Reduced from 3000ms
        private const val USB_INTERFACE_RELEASE_DELAY_MS = 200L // Reduced from 500ms
        private const val USB_DEVICE_RESET_DELAY_MS = 500L // Reduced from 1000ms
        private const val FAST_PATH_DELAY_MS = 50L // Fast path for known good devices
        private const val CONNECTION_CACHE_TIMEOUT_MS = 5000L // Cache connection params
    }

    // Component instances
    private val usbHandler = UvcCameraUsbHandler(context)
    private val stateManager = UvcCameraStateManager()
    private val surfaceHandler = UvcCameraSurfaceHandler()
    private val resolutionHandler = UvcCameraResolutionHandler()
    private val commandProcessor = UvcCameraCommandProcessor(scope)

    // Expose state flows
    val cameraAspectRatio: StateFlow<Float?> = stateManager.cameraAspectRatio
    val isPreviewActive: StateFlow<Boolean> = stateManager.isPreviewActive
    val currentCameraSurface: Surface? get() = stateManager.currentSurface

    // Synchronization
    private val cameraOperationMutex = Mutex()
    private var lastConnectionAttemptTime = 0L
    private val connectionAttemptMutex = Mutex()

    private var lastValidSurface: Surface? = null
    private var isManagersRegistered = false

    init {
        // Set command handler
        commandProcessor.setCommandHandler(this)

        // Monitor USB events
        scope.launch {
            usbHandler.events.collect { event ->
                handleUsbEvent(event)
            }
        }

        // Monitor connection state changes
        scope.launch {
            connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }

        // Monitor preview state to sync with connection state
        scope.launch {
            isPreviewActive.collect { active ->
                if (active && connectionState.value !is ConnectionState.Active) {
                    updateState(ConnectionState.Active)
                }
            }
        }
    }

    override suspend fun canHandleDevice(device: UsbDevice): Boolean {
        return usbHandler.canHandleDevice(device)
    }

    override suspend fun openConnection(device: UsbDevice): UvcCameraConnection {
        Log.d(TAG, "Opening connection to device: ${device.deviceName}")

        // Start timing for performance tracking
        stateManager.startConnectionTiming()

        val ctrlBlock = stateManager.pendingCtrlBlock
            ?: usbHandler.getControlBlock(device)
            ?: throw IllegalStateException("No control block available for device")

        // Fast path for known devices
        val deviceProfile = stateManager.getDeviceProfile(device.vendorId, device.productId)
        val useFastPath = deviceProfile != null && !stateManager.isChangingResolution.value

        // Adaptive throttling based on device history
        if (!useFastPath) {
            connectionAttemptMutex.withLock {
                val now = System.currentTimeMillis()
                val timeSinceLastAttempt = now - lastConnectionAttemptTime
                val requiredDelay = if (stateManager.isChangingResolution.value) {
                    FAST_PATH_DELAY_MS // Minimal delay during resolution change
                } else if (deviceProfile != null) {
                    // Use historical average connection time as guide
                    (deviceProfile.averageConnectionTime / 2).coerceIn(FAST_PATH_DELAY_MS, MIN_RECONNECT_DELAY_MS)
                } else {
                    MIN_RECONNECT_DELAY_MS
                }

                if (timeSinceLastAttempt < requiredDelay) {
                    val waitTime = requiredDelay - timeSinceLastAttempt
                    Log.d(TAG, "Waiting ${waitTime}ms before attempting connection")
                    delay(waitTime)
                }
                lastConnectionAttemptTime = System.currentTimeMillis()
            }
        }

        val camera = UVCCamera()
        val connectionId = stateManager.generateConnectionId()

        try {
            // Open camera with optimized retry logic
            var opened = false
            var lastError: Exception? = null

            if (useFastPath) {
                // Fast path - try once with minimal delay
                try {
                    camera.open(ctrlBlock)
                    Log.d(TAG, "Camera opened via fast path")
                    opened = true
                } catch (e: Exception) {
                    Log.d(TAG, "Fast path failed, falling back to normal path")
                    lastError = e
                }
            }

            if (!opened) {
                // Normal path with retries
                var openAttempts = 0
                val maxAttempts = if (useFastPath) 2 else 3 // Fewer retries if fast path failed

                while (openAttempts < maxAttempts && !opened) {
                    try {
                        if (openAttempts > 0) {
                            // Minimal delay between retries
                            delay(USB_INTERFACE_RELEASE_DELAY_MS * openAttempts)
                        }

                        camera.open(ctrlBlock)
                        Log.d(TAG, "Camera opened successfully on attempt ${openAttempts + 1}")
                        opened = true
                        stateManager.setCameraValid(connectionId)
                    } catch (e: Exception) {
                        lastError = e
                        openAttempts++
                        Log.e(TAG, "Failed to open camera on attempt $openAttempts: ${e.message}")

                        // Fail fast for incompatible devices
                        if (e.message?.contains("err=-99") == true ||
                            e.message?.contains("err=-5") == true) {
                            throw IllegalStateException("Device is not a compatible UVC camera", e)
                        }
                    }
                }
            }

            if (!opened) {
                throw lastError ?: IllegalStateException("Failed to open camera")
            }

            stateManager.setCameraValid(connectionId)

            // Configure resolution (prefer cached resolution for fast path)
            val targetResolution = when {
                stateManager.pendingResolutionChange != null -> stateManager.pendingResolutionChange!!
                deviceProfile?.lastSuccessfulResolution != null -> deviceProfile.lastSuccessfulResolution
                else -> preferredResolution
            }

            val negotiatedRes = if (useFastPath && deviceProfile?.lastSuccessfulResolution == targetResolution) {
                // Skip resolution negotiation if we know it works
                if (resolutionHandler.trySetResolution(camera, targetResolution)) {
                    Log.d(TAG, "Applied cached resolution via fast path")
                    targetResolution
                } else {
                    resolutionHandler.configureOptimalResolution(camera, targetResolution)
                }
            } else {
                resolutionHandler.configureOptimalResolution(camera, targetResolution)
            }

            Log.d(TAG, "Negotiated resolution: ${negotiatedRes.width}x${negotiatedRes.height}")

            // Save successful configuration for future fast path
            stateManager.saveDeviceProfile(device.vendorId, device.productId, negotiatedRes)

            // Update aspect ratio immediately for UI
            stateManager.updateAspectRatio(negotiatedRes)

            return UvcCameraConnection(
                usbMonitor = usbHandler.usbMonitor,
                camera = camera,
                ctrlBlock = ctrlBlock,
                negotiatedResolution = negotiatedRes,
                connectionId = connectionId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            stateManager.setCameraInvalid()
            stateManager.clearAspectRatio()
            try {
                camera.destroy()
            } catch (destroyError: Exception) {
                Log.e(TAG, "Error destroying camera after failed open", destroyError)
            }
            throw e
        }
    }

    override suspend fun closeConnection(connection: UvcCameraConnection) {
        withContext(Dispatchers.IO) {
            cameraOperationMutex.withLock {
                if (stateManager.isChangingResolution.value) {
                    Log.d(TAG, "Skipping full cleanup during resolution change")
                    return@withLock
                }

                Log.d(TAG, "Closing camera connection ${connection.connectionId}")
                stateManager.setClosing(true)

                // Stop preview if active
                if (!stateManager.isPhysicallyDisconnected.value && stateManager.isCameraValid.value && stateManager.isPreviewActive.value) {
                    surfaceHandler.stopPreview(connection.camera, true)
                }

                // Clear states
                stateManager.setPreviewActive(false)
                stateManager.clearAspectRatio()

                // Close camera if not physically disconnected
                if (!stateManager.isPhysicallyDisconnected.value && stateManager.isCameraValid.value) {
                    try {
                        connection.camera.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing camera", e)
                    }

                    try {
                        connection.camera.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error destroying camera", e)
                    }
                } else {
                    Log.d(TAG, "Skipping camera close/destroy - device disconnected or camera invalid")
                }

                // Clean up control block
                currentDevice?.let { device ->
                    usbHandler.activeControlBlocks.remove(device.deviceName)
                }

                // Reset state
                stateManager.setCameraInvalid()
                stateManager.setClosing(false)
            }
        }
    }

    override suspend fun queryCapabilities(connection: UvcCameraConnection): DeviceCapabilities {
        val supportedResolutions = if (stateManager.isCameraValid.value) {
            resolutionHandler.getSupportedResolutions(connection.camera)
        } else {
            emptyList()
        }

        return DeviceCapabilities(
            supportedResolutions = supportedResolutions,
            supportedFormats = listOf("MJPEG", "YUYV"),
            maxDataRate = 300,
            requiresExclusiveAccess = true
        )
    }

    override suspend fun performHealthCheck(connection: UvcCameraConnection): Boolean {
        // Simple health check - just verify the connection has basic validity
        return try {
            // Don't fail health check during state transitions
            val currentState = stateManager.deviceState.value
            when (currentState) {
                is DeviceState.Disconnecting,
                is DeviceState.PhysicallyDisconnected,
                is DeviceState.Idle -> {
                    // Don't run health checks when disconnecting/disconnected
                    true
                }
                is CameraState.ChangingResolution -> {
                    // Always pass during resolution changes
                    true
                }
                else -> {
                    // Basic connection validity check
                    connection.connectionId == stateManager.currentConnectionId &&
                            connection.ctrlBlock.fileDescriptor >= 0
                }
            }
        } catch (e: Exception) {
            // Don't fail on exceptions during health check
            true
        }
    }

    override suspend fun connect(device: UsbDevice): Boolean {
        Log.d(TAG, "Connecting to device: ${device.deviceName}")

        if (!stateManager.isChangingResolution.value) {
            when (val currentState = connectionState.value) {
                is ConnectionState.Connecting -> {
                    Log.w(TAG, "Already connecting, skipping duplicate connection attempt")
                    return false
                }
                is ConnectionState.Connected,
                is ConnectionState.Active -> {
                    if (currentDevice?.deviceId == device.deviceId) {
                        Log.w(TAG, "Already connected to this device")
                        return true
                    }
                }
                else -> {}
            }
        }

        stateManager.setConnecting(true)
        val connected = try {
            super.connect(device)
        } finally {
            stateManager.setConnecting(false)
        }

        if (connected) {
            Log.d(TAG, "Successfully connected to camera")

            // Check if we have a surface waiting
            val surface = stateManager.currentSurface
            if (surface != null && surface.isValid && stateManager.isCameraValid.value) {
                Log.d(TAG, "Surface available after connection, requesting preview start")
                commandProcessor.sendCommand(CameraCommand.StartPreview(surface))
            }
        } else {
            Log.e(TAG, "Failed to connect to camera")
        }

        return connected
    }

    /**
     * Override disconnect to properly clean up and allow reconnection
     */
    override suspend fun disconnect() {
        if (stateManager.isChangingResolution.value) {
            Log.d(TAG, "Skipping disconnect during resolution change")
            return
        }

        try {
            // Stop preview if active
            if (stateManager.isPreviewActive.value && stateManager.isCameraValid.value) {
                commandProcessor.sendCommand(CameraCommand.StopPreview)
                delay(50)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stop preview command", e)
        }

        // Clear surface reference to allow clean reconnection
        lastValidSurface = null
        stateManager.setSurface(null)

        // Reset state manager to allow reconnection
        stateManager.reset()

        super.disconnect()
    }

    /**
     * Set the camera surface for preview
     */
    fun setSurface(surface: Surface?) {
        val surfaceInfo = surface?.let { "Valid (${it.isValid})" } ?: "null"
        Log.d(TAG, "setSurface called: $surfaceInfo")

        stateManager.setSurface(surface)

        scope.launch {
            try {
                commandProcessor.sendCommand(CameraCommand.SetSurface(surface))
            } catch (e: Exception) {
                Log.e(TAG, "Error sending set surface command", e)
            }
        }
    }

    /**
     * Change camera resolution.
     * First attempts a simple in-place resolution change. If that fails,
     * falls back to a full USB reset cycle.
     */
    suspend fun changeResolution(newResolution: Resolution): Boolean {
        return withContext(Dispatchers.IO) {
            if (!stateManager.isCameraValid.value || deviceConnection == null) {
                Log.w(TAG, "Cannot change resolution - camera not connected or invalid")
                return@withContext false
            }

            // First, try simple in-place resolution change (faster, no reconnection needed)
            Log.d(TAG, "Attempting simple resolution change to ${newResolution.width}x${newResolution.height}")
            val simpleDeferred = CompletableDeferred<Boolean>()

            try {
                commandProcessor.sendCommand(CameraCommand.ChangeResolution(newResolution) { result ->
                    simpleDeferred.complete(result)
                })

                val simpleResult = withTimeoutOrNull(5000) {
                    simpleDeferred.await()
                } ?: false

                if (simpleResult) {
                    Log.d(TAG, "Simple resolution change succeeded")
                    preferredResolution = newResolution
                    return@withContext true
                }

                // Simple change failed, fall back to USB reset
                Log.d(TAG, "Simple resolution change failed, falling back to USB reset")
                val resetDeferred = CompletableDeferred<Boolean>()

                commandProcessor.sendCommand(CameraCommand.PerformUsbReset(newResolution) { result ->
                    resetDeferred.complete(result)
                })

                withTimeoutOrNull(15000) {
                    resetDeferred.await()
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change resolution", e)
                false
            }
        }
    }

    /**
     * Register to start monitoring for devices
     */
    fun register() {
        if (isManagersRegistered) {
            Log.d(TAG, "Device managers already registered, skipping")
            return
        }

        Log.d(TAG, "Registering USB monitor")
        isManagersRegistered = true

        // Reset state if currently idle or disconnected
        val currentState = stateManager.deviceState.value
        if (currentState is DeviceState.Idle ||
            currentState is DeviceState.PhysicallyDisconnected ||
            currentState is DeviceState.Error) {
            stateManager.reset()
        }

        usbHandler.register()

        // Parallel device scanning and connection
        scope.launch {
            // Short delay to let USB monitor initialize
            delay(FAST_PATH_DELAY_MS)

            // Scan devices in parallel with checking for cached profiles
            val scanJob = async { coordinator.scanDevices() }

            // Check if we have a known good device profile
            val cachedProfile = stateManager.lastSuccessfulDeviceKey?.let { key ->
                val parts = key.split(":")
                if (parts.size == 2) {
                    val vendorId = parts[0].toIntOrNull()
                    val productId = parts[1].toIntOrNull()
                    if (vendorId != null && productId != null) {
                        stateManager.getDeviceProfile(vendorId, productId)
                    } else null
                } else null
            }

            // Wait for scan to complete
            val devices = scanJob.await()

            // Prioritize cached device if available
            val cameras = coordinator.getDevicesOfType(DeviceType.Camera)
            val targetCamera = if (cachedProfile != null) {
                cameras.find {
                    !it.isInUse &&
                            it.device.vendorId == cachedProfile.vendorId &&
                            it.device.productId == cachedProfile.productId
                } ?: cameras.firstOrNull { !it.isInUse }
            } else {
                cameras.firstOrNull { !it.isInUse }
            }

            targetCamera?.let {
                Log.d(TAG, "Found available camera, attempting fast connection")

                // Minimal delay for auto-connect
                val connectionDelay = if (cachedProfile != null) FAST_PATH_DELAY_MS else 200L
                delay(connectionDelay)

                if (!stateManager.isConnecting.value && currentDevice == null) {
                    requestPermissionAndConnect(it.device)
                }
            }
        }
    }

    /**
     * Unregister and stop monitoring
     */
    fun unregister() {
        if (!isManagersRegistered) {
            Log.d(TAG, "Device managers not registered, skipping unregister")
            return
        }

        Log.d(TAG, "Unregistering USB monitor")
        isManagersRegistered = false

        // Clear state but don't mark as invalid to allow re-registration
        stateManager.setSurface(null)
        stateManager.updateState(DeviceState.Idle)  // Set to Idle instead of invalid

        usbHandler.unregister()
        scope.launch {
            disconnect()
        }
    }

    /**
     * Check for available devices and attempt to connect if not already connected.
     * Called by coordinator when any USB permission is granted.
     */
    suspend fun checkAndConnectAvailableDevices() {
        if (!isManagersRegistered || currentDevice != null || stateManager.isConnecting.value) {
            return
        }

        Log.d(TAG, "Checking for available UVC cameras")
        coordinator.scanDevices()

        val cameras = coordinator.getDevicesOfType(DeviceType.Camera)
        cameras.firstOrNull { !it.isInUse }?.let { deviceInfo ->
            Log.d(TAG, "Found unconnected UVC camera: ${deviceInfo.device.deviceName}")
            requestPermissionAndConnect(deviceInfo.device)
        }
    }

    private suspend fun requestPermissionAndConnect(device: UsbDevice) {
        Log.d(TAG, "Requesting permission for device: ${device.deviceName}")
        updateState(ConnectionState.AwaitingPermission)
        usbHandler.requestPermission(device)
    }

    private suspend fun handleUsbEvent(event: UvcCameraUsbHandler.UsbEvent) {
        when (event) {
            is UvcCameraUsbHandler.UsbEvent.DeviceAttached -> {
                emitEvent(DeviceEvent.Discovered(event.device, deviceType))

                if (
                    stateManager.isChangingResolution.value
                    && stateManager.pendingResolutionChange != null
                ) {
                    Log.d(TAG, "Device reattached during resolution change")
                    // Minimal delay for resolution change
                    delay(FAST_PATH_DELAY_MS)
                    requestPermissionAndConnect(event.device)
                } else if (
                    config.autoReconnect
                    && currentDevice == null
                    && !stateManager.isClosing.value
                    && !stateManager.isConnecting.value
                ) {
                    // Fast reconnect for known devices
                    val isKnownDevice = stateManager.hasDeviceProfile(event.device.vendorId, event.device.productId)

                    connectionAttemptMutex.withLock {
                        val now = System.currentTimeMillis()
                        val requiredDelay = if (isKnownDevice) FAST_PATH_DELAY_MS else MIN_RECONNECT_DELAY_MS

                        if (now - lastConnectionAttemptTime >= requiredDelay) {
                            lastConnectionAttemptTime = now
                            if (isKnownDevice) {
                                Log.d(TAG, "Fast reconnect for known device")
                            }
                            requestPermissionAndConnect(event.device)
                        } else {
                            Log.d(TAG, "Skipping reconnect - too soon (${now - lastConnectionAttemptTime}ms < ${requiredDelay}ms)")
                        }
                    }
                } else {
                    // Log why auto-reconnect didn't trigger
                    Log.d(TAG, "Auto-reconnect not triggered - autoReconnect=${config.autoReconnect}, " +
                            "currentDevice=${currentDevice?.deviceName}, " +
                            "isClosing=${stateManager.isClosing.value}, " +
                            "isConnecting=${stateManager.isConnecting.value}")
                }
            }

            is UvcCameraUsbHandler.UsbEvent.DeviceConnected -> {
                if (stateManager.isChangingResolution.value) {
                    Log.d(TAG, "Device reconnected during resolution change")
                    handleUsbConnect(event.device, event.ctrlBlock)
                } else if (!stateManager.isConnecting.value && currentDevice?.deviceId != event.device.deviceId) {
                    handleUsbConnect(event.device, event.ctrlBlock)
                }
            }

            is UvcCameraUsbHandler.UsbEvent.DeviceDisconnected -> {
                if (stateManager.isChangingResolution.value) {
                    Log.d(TAG, "Expected disconnect during resolution change")
                } else if (event.device.deviceId == currentDevice?.deviceId) {
                    Log.d(TAG, "Current device disconnected")

                    // Mark as idle to allow reconnection
                    stateManager.updateState(DeviceState.Idle)

                    // Clean disconnect to allow reconnection
                    disconnectImmediately()
                }
            }

            is UvcCameraUsbHandler.UsbEvent.DeviceDetached -> {
                if (!stateManager.isChangingResolution.value
                    && event.device.deviceId == currentDevice?.deviceId) {
                    Log.d(TAG, "Current device detached, cleaning up state")
                    // Device physically removed, clean up state completely
                    stateManager.updateState(DeviceState.Idle)
                    disconnectImmediately()
                }
                coordinator.onDeviceDisconnected(event.device)
            }

            is UvcCameraUsbHandler.UsbEvent.PermissionCancelled -> {
                Log.d(TAG, "Permission cancelled for device: ${event.device.deviceName}")
                stateManager.setConnecting(false)
                stateManager.pendingCtrlBlock = null

                if (stateManager.isChangingResolution.value) {
                    stateManager.completeResolutionChange(false)
                }

                emitEvent(DeviceEvent.PermissionDenied(event.device))
                updateState(ConnectionState.Error("Permission denied", false))
            }

            is UvcCameraUsbHandler.UsbEvent.PermissionGranted -> {
                // Permission granted is handled internally by USBMonitor
                // The DeviceConnected event will be triggered after permission is granted
                Log.d(TAG, "Permission granted for device: ${event.device.deviceName}")
                // Trigger global rescan so other device managers can connect their devices
                coordinator.rescanAndConnectAllDevices()
            }

            is UvcCameraUsbHandler.UsbEvent.PermissionDenied -> {
                Log.d(TAG, "Permission denied for device: ${event.device.deviceName}")
                stateManager.setConnecting(false)
                stateManager.pendingCtrlBlock = null

                if (stateManager.isChangingResolution.value) {
                    stateManager.completeResolutionChange(false)
                }

                emitEvent(DeviceEvent.PermissionDenied(event.device))
                updateState(ConnectionState.Error("Permission denied", false))
            }

            is UvcCameraUsbHandler.UsbEvent.ConnectionError -> {
                Log.e(TAG, "Connection error for device: ${event.device?.deviceName}, error: ${event.error.message}")
                stateManager.setConnecting(false)
                stateManager.pendingCtrlBlock = null

                if (stateManager.isChangingResolution.value) {
                    stateManager.completeResolutionChange(false)
                }

                // This handles SecurityException from USBMonitor when accessing serial number
                // on some devices. The connection failed but the app should continue.
                event.device?.let {
                    emitEvent(DeviceEvent.ConnectionFailed(it, event.error))
                }
                updateState(ConnectionState.Error("Connection failed: ${event.error.message}", true))
            }
        }
    }

    private suspend fun handleUsbConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        Log.d(TAG, "handleUsbConnect called")

        if (!stateManager.isChangingResolution.value) {
            if (stateManager.isConnecting.value) {
                Log.d(TAG, "Already connecting, ignoring duplicate")
                return
            }

            if (currentDevice != null && currentDevice?.deviceId == device.deviceId) {
                Log.d(TAG, "Already connected to this device")
                return
            }
        }

        stateManager.pendingCtrlBlock = ctrlBlock
        connect(device)
    }

    private suspend fun handleConnectionStateChange(state: ConnectionState) {
        Log.d(TAG, "Connection state changed to: ${state::class.simpleName}")

        when (state) {
            is ConnectionState.Connected -> {
                if (stateManager.isChangingResolution.value && stateManager.pendingResolutionChange != null) {
                    Log.d(TAG, "Connected after resolution change")
                } else {
                    val surface = stateManager.currentSurface
                    if (surface != null
                        && surface.isValid
                        && stateManager.isCameraValid.value) {
                        Log.d(TAG, "Connected and surface available, requesting preview start")
                        commandProcessor.sendCommand(CameraCommand.StartPreview(surface))
                    }
                }
            }

            is ConnectionState.Active -> {
                if (stateManager.isChangingResolution.value && stateManager.pendingResolutionChange != null) {
                    Log.d(TAG, "Resolution change completed successfully")
                    stateManager.completeResolutionChange(true)
                }
            }

            is ConnectionState.Disconnected -> {
                if (!stateManager.isChangingResolution.value) {
                    Log.d(TAG, "Disconnected state, clearing camera state")
                    stateManager.clearAspectRatio()
                    stateManager.setPreviewActive(false)

                    if (
                        stateManager.isCameraValid.value
                        && !stateManager.isClosing.value
                        && !stateManager.isPhysicallyDisconnected.value
                    ) {
                        commandProcessor.sendCommand(CameraCommand.StopPreview)
                    }
                }
            }

            is ConnectionState.Error -> {
                if (stateManager.isChangingResolution.value) {
                    stateManager.completeResolutionChange(false)
                }

                stateManager.clearAspectRatio()
                stateManager.setPreviewActive(false)

                if (
                    stateManager.isCameraValid.value
                    && !stateManager.isClosing.value
                    && !stateManager.isPhysicallyDisconnected.value
                ) {
                    commandProcessor.sendCommand(CameraCommand.StopPreview)
                }
            }

            else -> {}
        }
    }

    private suspend fun disconnectImmediately() {
        withContext(Dispatchers.IO) {
            // Update state to disconnected (not idle yet)
            updateState(ConnectionState.Disconnected)

            val device = currentDevice
            currentDevice = null
            deviceConnection = null
            _capabilities.value = null

            // Clear surface and state
            lastValidSurface = null
            stateManager.setSurface(null)

            device?.let {
                coordinator.releaseDevice(it, deviceType)
                emitEvent(DeviceEvent.Disconnected(it, "Device physically removed"))
            }

            // Now set to idle to allow reconnection
            stateManager.updateState(DeviceState.Idle)
        }
    }

    // Command handler implementations
    override suspend fun handleStartPreview(surface: Surface) {
        withContext(Dispatchers.IO) {
            cameraOperationMutex.withLock {
                val connection = deviceConnection
                if (connection == null
                    || !stateManager.isCameraValid.value) {
                    Log.w(TAG, "Cannot start preview - no connection or camera invalid")
                    return@withLock
                }

                if (connection.connectionId != stateManager.currentConnectionId) {
                    Log.w(TAG, "Connection mismatch - ignoring preview start")
                    return@withLock
                }

                surfaceHandler.startPreview(
                    connection.camera,
                    surface,
                    onSuccess = {
                        stateManager.setPreviewActive(true)
                        val res = connection.negotiatedResolution
                        stateManager.updateAspectRatio(res)
                        // Launch coroutine to call suspend functions
                        scope.launch {
                            updateState(ConnectionState.Active)
                        }
                        Log.d(TAG, "Preview started successfully at ${res.width}x${res.height}")
                    },
                    onError = { e ->
                        stateManager.setPreviewActive(false)
                        if (e.message?.contains("native") == true || e is UnsatisfiedLinkError) {
                            stateManager.setCameraInvalid()
                        }
                        // Launch coroutine to call suspend functions
                        scope.launch {
                            emitEvent(DeviceEvent.Error(currentDevice, e))
                        }
                    }
                )
            }
        }
    }

    override suspend fun handleStopPreview() {
        withContext(Dispatchers.IO) {
            cameraOperationMutex.withLock {
                Log.d(TAG, "handleStopPreview called")
                stateManager.setPreviewActive(false)

                if (stateManager.isCameraValid.value
                    && !stateManager.isClosing.value) {
                    surfaceHandler.stopPreview(
                        deviceConnection?.camera,
                        stateManager.isClosing.value)
                }

                if (connectionState.value is ConnectionState.Active) {
                    updateState(ConnectionState.Connected)
                }
            }
        }
    }

    override suspend fun handleSetSurface(surface: Surface?) {
        Log.d(TAG, "handleSetSurface: ${surface?.let { "valid=${it.isValid}" } ?: "null"}")

        // Store the surface in state manager first
        stateManager.setSurface(surface)

        when {
            surface == null || !surface.isValid -> {
                // Only stop preview if we're not in a transitional state
                if (!stateManager.isChangingResolution.value &&
                    !stateManager.isConnecting.value) {
                    Log.d(TAG, "Surface is null or invalid, stopping preview")
                    handleStopPreview()
                }
            }
            else -> {
                when (val state = connectionState.value) {
                    is ConnectionState.Connected,
                    is ConnectionState.Active -> {
                        if (stateManager.isCameraValid.value) {
                            Log.d(TAG, "Surface is valid and camera is ready, starting preview")
                            handleStartPreview(surface)
                        } else {
                            Log.d(TAG, "Surface is valid but camera is not valid yet, waiting...")
                        }
                    }
                    else -> {
                        Log.d(TAG, "Surface is valid but camera not ready (state: ${state::class.simpleName})")
                    }
                }
            }
        }
    }

    override suspend fun handleChangeResolution(resolution: Resolution): Boolean {
        return withContext(Dispatchers.IO) {
            cameraOperationMutex.withLock {
                val connection = deviceConnection
                if (connection == null || !stateManager.isCameraValid.value) {
                    Log.w(TAG, "Cannot change resolution - no valid connection")
                    return@withLock false
                }

                try {
                    Log.d(TAG, "Attempting simple resolution change to ${resolution.width}x${resolution.height}")

                    val wasPreviewActive = stateManager.isPreviewActive.value
                    val surface = stateManager.currentSurface

                    if (wasPreviewActive) {
                        connection.camera.stopPreview()
                        stateManager.setPreviewActive(false)
                        delay(100)
                    }

                    val success = resolutionHandler.trySetResolution(connection.camera, resolution)

                    if (success) {
                        connection.negotiatedResolution = resolution
                        preferredResolution = resolution
                        stateManager.updateAspectRatio(resolution)

                        Log.d(TAG, "Simple resolution change successful")

                        if (wasPreviewActive && surface != null && surface.isValid) {
                            connection.camera.setPreviewDisplay(surface)
                            connection.camera.startPreview()
                            stateManager.setPreviewActive(true)
                            updateState(ConnectionState.Active)
                        }

                        _capabilities.value = queryCapabilities(connection)
                        return@withLock true
                    } else {
                        Log.d(TAG, "Simple resolution change failed")

                        if (wasPreviewActive && surface != null && surface.isValid) {
                            connection.camera.setPreviewDisplay(surface)
                            connection.camera.startPreview()
                            stateManager.setPreviewActive(true)
                        }

                        return@withLock false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during simple resolution change", e)
                    return@withLock false
                }
            }
        }
    }

    override suspend fun handlePerformUsbReset(resolution: Resolution): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Performing USB reset for resolution change to ${resolution.width}x${resolution.height}")

            val device = currentDevice
            if (device == null) {
                Log.e(TAG, "No device to reset")
                return@withContext false
            }

            val surface = stateManager.currentSurface
            val wasPreviewActive = stateManager.isPreviewActive.value

            stateManager.startResolutionChange(resolution) { success ->
                Log.d(TAG, "Resolution change callback: $success")
            }
            preferredResolution = resolution

            try {
                // Parallel cleanup operations
                val cleanupJobs = mutableListOf<Job>()

                // Stop preview if active
                if (wasPreviewActive) {
                    cleanupJobs.add(launch {
                        Log.d(TAG, "Stopping preview for USB reset")
                        deviceConnection?.camera?.stopPreview()
                        stateManager.setPreviewActive(false)
                    })
                }

                // Clear aspect ratio in parallel
                cleanupJobs.add(launch {
                    stateManager.clearAspectRatio()
                })

                // Wait for cleanup to complete
                cleanupJobs.joinAll()

                // Close and destroy camera
                deviceConnection?.camera?.let { camera ->
                    try {
                        camera.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing camera", e)
                    }

                    try {
                        camera.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error destroying camera", e)
                    }
                }

                // Quick state reset
                usbHandler.clearControlBlocks()
                stateManager.pendingCtrlBlock = null
                stateManager.setCameraInvalid()
                deviceConnection = null

                // Release device
                coordinator.releaseDevice(device, deviceType)
                updateState(ConnectionState.Disconnected)
                currentDevice = null

                // Minimal USB stabilization delay
                Log.d(TAG, "USB stabilization...")
                delay(USB_DEVICE_RESET_DELAY_MS)

                // Quick re-register
                Log.d(TAG, "Re-registering USB monitor")
                usbHandler.unregister()
                delay(FAST_PATH_DELAY_MS)
                usbHandler.register()

                // Fast device rediscovery with timeout
                Log.d(TAG, "Waiting for device rediscovery...")
                val rediscoveryTimeout = 5000L // 5 seconds total
                val startTime = System.currentTimeMillis()

                while (
                    (System.currentTimeMillis() - startTime) < rediscoveryTimeout
                    && stateManager.isChangingResolution.value) {
                    val devices = coordinator.scanDevices()
                    val cameraDevices = devices[DeviceType.Camera] ?: emptyList()

                    if (cameraDevices.isNotEmpty()) {
                        Log.d(TAG, "Found camera device, attempting reconnection")
                        val targetDevice = cameraDevices.first()

                        requestPermissionAndConnect(targetDevice)

                        // Fast connection wait
                        var connectionWaitTime = 0
                        while (connectionWaitTime < 3000 && currentDevice == null) {
                            delay(50)
                            connectionWaitTime += 50
                        }

                        if (currentDevice != null && stateManager.isCameraValid.value) {
                            Log.d(TAG, "Successfully reconnected with new resolution")

                            if (wasPreviewActive && surface != null && surface.isValid) {
                                Log.d(TAG, "Restoring preview")
                                commandProcessor.sendCommand(CameraCommand.StartPreview(surface))
                            }

                            stateManager.completeResolutionChange(true)
                            return@withContext true
                        }
                    }

                    delay(200) // Shorter polling interval
                }

                Log.e(TAG, "Failed to reconnect after USB reset")
                stateManager.completeResolutionChange(false)
                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Error during USB reset", e)
                stateManager.completeResolutionChange(false)
                return@withContext false
            }
        }
    }

    override fun destroy() {
        stateManager.setCameraInvalid()
        stateManager.setClosing(true)
        stateManager.clearAspectRatio()
        stateManager.setPreviewActive(false)

        usbHandler.clearControlBlocks()
        commandProcessor.close()
        unregister()
        super.destroy()
    }
}