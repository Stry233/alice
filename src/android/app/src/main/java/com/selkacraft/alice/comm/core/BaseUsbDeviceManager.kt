package com.selkacraft.alice.comm.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseUsbDeviceManager<T : DeviceConnection>(
    protected val context: Context,
    protected val deviceType: DeviceType,
    protected val scope: CoroutineScope,
    protected val coordinator: UsbDeviceCoordinator,
    protected val config: DeviceConfig = DeviceConfig()
) {
    companion object {
        private const val TAG = "BaseUsbDeviceManager"
    }

    data class DeviceConfig(
        val autoReconnect: Boolean = true,
        val maxReconnectAttempts: Int = 3,
        val reconnectDelayMs: Long = 2000,
        val minReconnectDelayMs: Long = 1000, // Minimum delay between attempts
        val connectionTimeoutMs: Long = 5000,
        val healthCheckIntervalMs: Long = 5000
    )

    // State management
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceEvents = MutableSharedFlow<DeviceEvent>()
    val deviceEvents: SharedFlow<DeviceEvent> = _deviceEvents.asSharedFlow()

    internal val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities: StateFlow<DeviceCapabilities?> = _capabilities.asStateFlow()

    // Connection management
    var currentDevice: UsbDevice? = null
        protected set
    protected var deviceConnection: T? = null
    private var reconnectJob: Job? = null
    private var healthCheckJob: Job? = null
    private val isDestroyed = AtomicBoolean(false)

    // Track connection attempts to prevent rapid reconnections
    private var lastConnectionAttemptTime = 0L
    private var consecutiveFailures = 0
    private val connectionMutex = Mutex()

    protected val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    init {
        // Monitor coordinator events for device disconnection
        scope.launch {
            coordinator.events.collect { event ->
                when (event) {
                    is DeviceEvent.Disconnected -> {
                        if (event.device.deviceId == currentDevice?.deviceId) {
                            Log.d(TAG, "${deviceType.displayName}: Device physically disconnected, handling immediately")
                            handlePhysicalDisconnection()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    // Abstract methods that subclasses must implement
    abstract suspend fun canHandleDevice(device: UsbDevice): Boolean
    abstract suspend fun openConnection(device: UsbDevice): T
    abstract suspend fun closeConnection(connection: T)
    abstract suspend fun queryCapabilities(connection: T): DeviceCapabilities
    abstract suspend fun performHealthCheck(connection: T): Boolean

    // Handle physical device disconnection
    private suspend fun handlePhysicalDisconnection() {
        Log.d(TAG, "${deviceType.displayName}: Handling physical disconnection - updating state immediately")

        // Update state immediately without waiting for mutex
        _connectionState.value = ConnectionState.Disconnected

        connectionMutex.withLock {
            if (currentDevice != null) {
                // Cancel all jobs immediately
                cancelJobs()

                // Clean up connection
                val device = currentDevice
                currentDevice = null
                deviceConnection = null
                _capabilities.value = null

                // Release device from coordinator
                device?.let {
                    coordinator.releaseDevice(it, deviceType)
                    emitEvent(DeviceEvent.Disconnected(it, "Device physically removed"))
                }
            }
        }
    }

    // Template method pattern for connection flow
    open suspend fun connect(device: UsbDevice): Boolean {
        if (isDestroyed.get()) return false

        // Verify this device is actually meant for this manager
        if (!canHandleDevice(device)) {
            Log.e(TAG, "${deviceType.displayName} manager cannot handle device ${device.deviceName}")
            return false
        }

        return connectionMutex.withLock {
            // Check if we're already connected to this device
            if (currentDevice?.deviceId == device.deviceId &&
                (connectionState.value is ConnectionState.Connected ||
                        connectionState.value is ConnectionState.Active)) {
                Log.d(TAG, "Already connected to device ${device.deviceName}")
                return@withLock true
            }

            // Check if we're connecting too soon after a previous attempt
            val now = System.currentTimeMillis()
            val timeSinceLastAttempt = now - lastConnectionAttemptTime
            if (timeSinceLastAttempt < config.minReconnectDelayMs) {
                Log.w(TAG, "Connection attempt too soon after previous attempt (${timeSinceLastAttempt}ms)")
                return@withLock false
            }
            lastConnectionAttemptTime = now

            withContext(Dispatchers.IO) {
                try {
                    // Cancel any ongoing reconnection attempts
                    reconnectJob?.cancel()
                    reconnectJob = null

                    updateState(ConnectionState.Connecting)

                    // Request device from coordinator
                    if (!coordinator.requestDevice(device, deviceType)) {
                        updateState(ConnectionState.Error("Device busy or conflicts detected", false))
                        return@withContext false
                    }

                    // Open device-specific connection
                    val connection = withTimeoutOrNull(config.connectionTimeoutMs) {
                        openConnection(device)
                    }

                    if (connection == null) {
                        coordinator.releaseDevice(device, deviceType)
                        updateState(ConnectionState.Error("Connection timeout", true))
                        return@withContext false
                    }

                    deviceConnection = connection
                    currentDevice = device
                    consecutiveFailures = 0 // Reset failure counter on successful connection

                    // Query capabilities
                    _capabilities.value = try {
                        queryCapabilities(connection)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to query capabilities", e)
                        null
                    }

                    // Log connection info
                    Log.i(TAG, "${deviceType.displayName} connected: ${connection.getDescription()}")

                    updateState(ConnectionState.Connected)
                    emitEvent(DeviceEvent.Connected(device, getDeviceInfo(device)))

                    // Start health monitoring
                    startHealthMonitoring()

                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed for ${deviceType.displayName}", e)
                    consecutiveFailures++
                    handleConnectionError(device, e)
                    false
                }
            }
        }
    }

    open suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            cancelJobs()

            // Capture current device for cleanup
            val device = currentDevice

            deviceConnection?.let { connection ->
                try {
                    Log.d(TAG, "Closing ${connection.getDescription()}")
                    closeConnection(connection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing connection", e)
                }
            }

            device?.let {
                coordinator.releaseDevice(it, deviceType)
                emitEvent(DeviceEvent.Disconnected(it, "User requested"))
            }

            deviceConnection = null
            currentDevice = null
            _capabilities.value = null
            consecutiveFailures = 0
            updateState(ConnectionState.Disconnected)
        }
    }

    private fun startHealthMonitoring() {
        if (config.healthCheckIntervalMs <= 0) return

        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive && !isDestroyed.get()) {
                delay(config.healthCheckIntervalMs)

                deviceConnection?.let { connection ->
                    try {
                        // Check connection validity first
                        if (!connection.isValid()) {
                            Log.w(TAG, "${connection.getDescription()} is no longer valid")
                            handleHealthCheckFailure()
                        } else if (!performHealthCheck(connection)) {
                            handleHealthCheckFailure()
                        } else {
                            // Health check passed, reset failure counter
                            consecutiveFailures = 0
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Health check failed with exception", e)
                        handleHealthCheckFailure()
                    }
                }
            }
        }
    }

    private suspend fun handleHealthCheckFailure() {
        // Simplified health check failure handling to avoid complex nested locking
        // Capture state once outside the lock to avoid TOCTOU issues
        val device = currentDevice
        if (device == null) {
            return  // No device connected, nothing to do
        }

        // Check if device is still physically connected (no lock needed for read-only check)
        val deviceStillConnected = usbManager.deviceList.values.any {
            it.deviceId == device.deviceId
        }

        if (!deviceStillConnected) {
            // Device is physically gone, disconnect immediately
            Log.w(TAG, "Device physically disconnected during health check")
            emitEvent(DeviceEvent.Error(device, Exception("Device disconnected")))
            disconnect()
            return
        }

        // Device still connected, increment failure counter
        consecutiveFailures++
        Log.w(TAG, "Health check failed but device still connected (failure $consecutiveFailures/${config.maxReconnectAttempts})")

        // Only attempt reconnect after reaching max consecutive failures
        if (consecutiveFailures >= config.maxReconnectAttempts && config.autoReconnect) {
            Log.w(TAG, "Too many health check failures, attempting reconnect")
            attemptReconnect(device)
        }
    }

    protected suspend fun attemptReconnect(device: UsbDevice) {
        // Cancel any existing reconnect job
        reconnectJob?.cancel()

        // Don't attempt reconnect if already reconnecting or destroyed
        if (connectionState.value is ConnectionState.Reconnecting || isDestroyed.get()) {
            return
        }

        // Reset consecutive failures for fresh reconnection attempt
        consecutiveFailures = 0

        reconnectJob = scope.launch {
            var attempt = 0

            while (attempt < config.maxReconnectAttempts && !isDestroyed.get()) {
                attempt++
                updateState(ConnectionState.Reconnecting(attempt, config.maxReconnectAttempts))

                // Shorter delay for reconnection attempts
                val delay = if (attempt == 1) 500L else config.reconnectDelayMs
                Log.d(TAG, "Waiting ${delay}ms before reconnection attempt $attempt")
                delay(delay)

                // Check if device is still physically connected
                val deviceStillConnected = usbManager.deviceList.values.any {
                    it.deviceId == device.deviceId
                }

                if (!deviceStillConnected) {
                    Log.w(TAG, "Device no longer physically connected, aborting reconnection")
                    updateState(ConnectionState.Disconnected)
                    break
                }

                // Clear the last connection attempt time to avoid throttling
                lastConnectionAttemptTime = 0

                if (connect(device)) {
                    Log.i(TAG, "Reconnected successfully after $attempt attempts")
                    consecutiveFailures = 0  // Reset on successful reconnection
                    return@launch
                }
            }

            updateState(ConnectionState.Error("Failed to reconnect after $attempt attempts", false))
            disconnect()
        }
    }

    private suspend fun handleConnectionError(device: UsbDevice, error: Exception) {
        coordinator.releaseDevice(device, deviceType)

        val isRetryable = when (error) {
            is SecurityException -> false
            is IllegalArgumentException -> false
            is IllegalStateException -> {
                // Don't retry if it's a device type mismatch
                val message = error.message ?: ""
                !(message.contains("cannot handle device") ||
                        message.contains("not a compatible") ||
                        message.contains("Cannot open RealSense device as UVC"))
            }
            else -> true
        }

        updateState(ConnectionState.Error(error.message ?: "Unknown error", isRetryable))
        emitEvent(DeviceEvent.Error(device, error))

        if (config.autoReconnect && isRetryable && consecutiveFailures < config.maxReconnectAttempts) {
            attemptReconnect(device)
        }
    }

    protected open fun getDeviceInfo(device: UsbDevice): String {
        return "${device.productName ?: device.deviceName} (${device.vendorId}:${device.productId})"
    }

    protected open suspend fun updateState(state: ConnectionState) {
        _connectionState.value = state
        currentDevice?.let { device ->
            emitEvent(DeviceEvent.StateChanged(device, state))
        }
    }

    protected suspend fun emitEvent(event: DeviceEvent) {
        _deviceEvents.emit(event)
    }

    private fun cancelJobs() {
        reconnectJob?.cancel()
        reconnectJob = null
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    open fun destroy() {
        if (isDestroyed.getAndSet(true)) return

        scope.launch {
            disconnect()
        }
    }
}