package com.selkacraft.alice.util

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selkacraft.alice.comm.UsbDeviceCoordinator
import com.selkacraft.alice.comm.autofocus.*
import com.selkacraft.alice.comm.core.*
import com.selkacraft.alice.comm.motor.MotorControlManager
import com.selkacraft.alice.comm.realsense.RealSenseManager
import com.selkacraft.alice.comm.uvc.UvcCameraManager
import com.selkacraft.alice.coordination.AutofocusCoordinator
import com.selkacraft.alice.coordination.DeviceCoordinationManager
import com.selkacraft.alice.coordination.SettingsSynchronizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.apache.commons.collections4.queue.CircularFifoQueue
import java.io.IOException
import android.hardware.usb.UsbManager
import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.lifecycle.application
import kotlinx.coroutines.Job

enum class CameraState { IDLE, CONNECTING, READY, ERROR }

/**
 * Central ViewModel for the Alice camera monitoring application.
 *
 * Manages and coordinates:
 * - UVC camera for video preview
 * - RealSense depth camera for distance measurement
 * - Motor controller for lens focus adjustment
 * - Autofocus system combining depth data with focus control
 * - Application-wide settings and logging
 *
 * Acts as the single source of truth for UI state, exposing reactive [StateFlow]s
 * for all device states, measurements, and configuration.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "CameraViewModel"
        private const val LOG_BUFFER_SIZE = 200
    }

    // Data classes for structured data
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val category: LogCategory,
        val message: String,
        val level: LogLevel = LogLevel.INFO,
        val isRaw: Boolean = false  // If true, skip formatting (for logo, ASCII art, etc.)
    )

    enum class LogCategory {
        SYSTEM, USB, CAMERA, MOTOR, REALSENSE, AUTOFOCUS, ERROR
    }

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    data class PerformanceMetrics(
        val fps: Int = 0,
        val cpuUsage: Float = 0f,
        val memoryUsage: Float = 0f,
        val usbBandwidth: Int = 0
    )

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

    // Initialize log management FIRST before anything that might use logging
    private val logBuffer = CircularFifoQueue<LogEntry>(LOG_BUFFER_SIZE)
    private val _logMessages = MutableStateFlow<List<LogEntry>>(emptyList())
    val logMessages: StateFlow<List<LogEntry>> = _logMessages.asStateFlow()

    // Settings Manager
    val settingsManager = SettingsManager(application)

    // Device Managers - initialized early for coordinator use
    private lateinit var deviceCoordinator: DeviceCoordinationManager
    private lateinit var autofocusCoordinator: AutofocusCoordinator
    private lateinit var settingsSynchronizer: SettingsSynchronizer

    // Direct access to managers (for backward compatibility)
    lateinit var uvcCameraManager: UvcCameraManager
        private set
    lateinit var motorControlManager: MotorControlManager
        private set
    lateinit var realSenseManager: RealSenseManager
        private set
    lateinit var usbCoordinator: UsbDeviceCoordinator
        private set

    // Core autofocus controller
    private lateinit var autofocusController: AutofocusController

    // Camera state for UI compatibility
    private val _cameraState = MutableStateFlow(CameraState.IDLE)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    // Performance metrics
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    // UI State - delegated to coordinators (lazy to allow initialization order)
    val coordinatorState: StateFlow<UsbDeviceCoordinator.CoordinatorState> by lazy { deviceCoordinator.coordinatorState }
    val cameraConnectionState: StateFlow<ConnectionState> by lazy { deviceCoordinator.cameraConnectionState }
    val motorConnectionState: StateFlow<ConnectionState> by lazy { deviceCoordinator.motorConnectionState }
    val realSenseConnectionState: StateFlow<ConnectionState> by lazy { deviceCoordinator.realSenseConnectionState }

    // Camera specific states
    val videoAspectRatio: StateFlow<Float?> by lazy { deviceCoordinator.cameraAspectRatio }
    val isPreviewActive: StateFlow<Boolean> by lazy { deviceCoordinator.isPreviewActive }

    // Motor specific states
    val motorPosition: StateFlow<Int> by lazy { deviceCoordinator.motorPosition }
    val isMotorCalibrated: StateFlow<Boolean> by lazy { deviceCoordinator.isMotorCalibrated }

    // RealSense specific states
    val realSenseCenterDepth: StateFlow<Float> by lazy { deviceCoordinator.realSenseCenterDepth }
    val realSenseDepthConfidence: StateFlow<Float> by lazy { deviceCoordinator.realSenseDepthConfidence }
    val realSenseDepthBitmap: StateFlow<Bitmap?> by lazy { deviceCoordinator.realSenseDepthBitmap }
    val realSenseColorBitmap: StateFlow<Bitmap?> by lazy { deviceCoordinator.realSenseColorBitmap }
    val realSenseMeasurementPosition: StateFlow<Pair<Float, Float>> by lazy { deviceCoordinator.realSenseMeasurementPosition }

    // Autofocus state from coordinator
    val autofocusState: StateFlow<AutofocusState> by lazy { autofocusCoordinator.state }

    // Face detection state from coordinator
    val faceDetectionState: StateFlow<FaceDetectionState> by lazy { autofocusCoordinator.faceDetectionState }

    // Backward compatibility state flows (lazy to avoid initialization order issues)
    val autofocusMapping: StateFlow<AutofocusMapping?> by lazy {
        autofocusState.map { it.mapping }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    }
    val autofocusEnabled: StateFlow<Boolean> by lazy {
        autofocusState.map { it.isEnabled }
            .stateIn(viewModelScope, SharingStarted.Lazily, false)
    }
    val autofocusMode: StateFlow<FocusMode> by lazy {
        autofocusState.map { it.mode }
            .stateIn(viewModelScope, SharingStarted.Lazily, FocusMode.MANUAL)
    }
    val autofocusValidation: StateFlow<ValidationResult?> by lazy {
        autofocusState.map { it.mapping?.validate() }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    }
    val autofocusStats: StateFlow<FocusStatistics> by lazy {
        autofocusState.map { it.stats }
            .stateIn(viewModelScope, SharingStarted.Lazily, FocusStatistics())
    }
    val isAutofocusActive: StateFlow<Boolean> by lazy {
        autofocusState.map { it.isActivelyFocusing }
            .stateIn(viewModelScope, SharingStarted.Lazily, false)
    }
    val autofocusTarget: StateFlow<Pair<Float, Float>?> by lazy {
        autofocusState.map {
            it.focusPoint?.let { point -> point.x to point.y }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }
    val lastAutofocusDepth: StateFlow<Float> by lazy {
        autofocusState.map { it.currentDepth }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0f)
    }

    // Camera supported resolutions
    private val _supportedCameraResolutions = MutableStateFlow<List<String>>(emptyList())
    val supportedCameraResolutions: StateFlow<List<String>> = _supportedCameraResolutions.asStateFlow()

    // Flag to track if resolution is changing
    private val _isChangingResolution = MutableStateFlow(false)
    val isChangingResolution: StateFlow<Boolean> = _isChangingResolution.asStateFlow()

    // Store the last valid surface for restoration
    private var lastValidSurface: Surface? = null

    // Filtered log messages based on verbosity setting
    val filteredLogMessages: StateFlow<List<LogEntry>> by lazy {
        combine(
            logMessages,
            settingsManager.logVerbosity
        ) { messages, verbosity ->
            val minLevel = when (verbosity) {
                "ERROR" -> LogLevel.ERROR
                "WARNING" -> LogLevel.WARNING
                "INFO" -> LogLevel.INFO
                "DEBUG" -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
            messages.filter { it.level.ordinal >= minLevel.ordinal }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    // Performance monitoring job
    private var performanceJob: Job? = null

    // USB Manager for system-level USB control
    private val usbManager: UsbManager by lazy {
        application.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    init {
        // Load initial logs first
        loadInitialLogs()

        // Initialize device managers with settings
        initializeManagers()

        // Create coordinators
        initializeCoordinators()

        // Setup observation after managers are initialized
        setupObservers()

        // Initialize coordinators (starts all monitoring jobs)
        autofocusCoordinator.initialize()
        settingsSynchronizer.initialize()

        log(LogCategory.SYSTEM, "CameraViewModel initialized with coordinators", LogLevel.INFO)
    }

    private fun initializeManagers() {
        // Initialize USB coordinator first
        usbCoordinator = UsbDeviceCoordinator(
            context = getApplication(),
            scope = viewModelScope
        )

        // Initialize camera manager with settings
        val cameraResolution = parseCameraResolution(settingsManager.getCameraResolution())
        uvcCameraManager = UvcCameraManager(
            context = getApplication(),
            coordinator = usbCoordinator,
            scope = viewModelScope,
            preferredResolution = cameraResolution,
            config = BaseUsbDeviceManager.DeviceConfig(
                autoReconnect = settingsManager.getAutoReconnect(),
                maxReconnectAttempts = 3,
                reconnectDelayMs = settingsManager.getReconnectDelay().toLong(),
                minReconnectDelayMs = 500,
                connectionTimeoutMs = 5000,
                healthCheckIntervalMs = 10000
            )
        )

        // Initialize motor manager with settings
        motorControlManager = MotorControlManager(
            context = getApplication(),
            coordinator = usbCoordinator,
            scope = viewModelScope,
            config = BaseUsbDeviceManager.DeviceConfig(
                autoReconnect = settingsManager.getAutoReconnect(),
                maxReconnectAttempts = 5,
                reconnectDelayMs = settingsManager.getReconnectDelay().toLong(),
                healthCheckIntervalMs = 3000
            )
        )

        // Initialize RealSense manager with settings
        realSenseManager = RealSenseManager(
            context = getApplication(),
            coordinator = usbCoordinator,
            scope = viewModelScope,
            config = BaseUsbDeviceManager.DeviceConfig(
                autoReconnect = settingsManager.getAutoReconnect(),
                maxReconnectAttempts = 3,
                reconnectDelayMs = settingsManager.getReconnectDelay().toLong(),
                healthCheckIntervalMs = 0
            )
        )
    }

    private fun initializeCoordinators() {
        // Create autofocus controller
        autofocusController = AutofocusController(
            context = getApplication(),
            scope = viewModelScope
        )

        // Create device coordination manager
        deviceCoordinator = DeviceCoordinationManager(
            coordinator = usbCoordinator,
            uvcCameraManager = uvcCameraManager,
            motorControlManager = motorControlManager,
            realSenseManager = realSenseManager,
            scope = viewModelScope,
            onDeviceEvent = { event, category ->
                handleDeviceEvent(event)
            }
        )

        // Create autofocus coordinator
        autofocusCoordinator = AutofocusCoordinator(
            context = application.applicationContext,
            autofocusController = autofocusController,
            motorManager = motorControlManager,
            realSenseManager = realSenseManager,
            settingsManager = settingsManager,
            scope = viewModelScope,
            onMotorPositionCommand = { position ->
                setMotorPosition(position, isManualControl = false)
            },
            onLogMessage = { category, message ->
                val logCategory = when (category) {
                    "AUTOFOCUS" -> LogCategory.AUTOFOCUS
                    "FACE_TRACKING" -> LogCategory.AUTOFOCUS
                    "MOTOR" -> LogCategory.MOTOR
                    "REALSENSE" -> LogCategory.REALSENSE
                    else -> LogCategory.SYSTEM
                }
                val logLevel = when {
                    message.contains("Error", ignoreCase = true) -> LogLevel.ERROR
                    message.contains("Warning", ignoreCase = true) ||
                    message.contains("lost", ignoreCase = true) -> LogLevel.WARNING
                    message.contains("started", ignoreCase = true) ||
                    message.contains("achieved", ignoreCase = true) ||
                    message.contains("Eye", ignoreCase = true) -> LogLevel.DEBUG
                    else -> LogLevel.INFO
                }
                log(logCategory, message, logLevel)
            }
        )

        // Create settings synchronizer
        settingsSynchronizer = SettingsSynchronizer(
            settingsManager = settingsManager,
            deviceManager = deviceCoordinator,
            scope = viewModelScope,
            onLogMessage = { category, message ->
                val logCategory = when (category) {
                    "MOTOR" -> LogCategory.MOTOR
                    "CAMERA" -> LogCategory.CAMERA
                    "REALSENSE" -> LogCategory.REALSENSE
                    "SYSTEM" -> LogCategory.SYSTEM
                    else -> LogCategory.SYSTEM
                }
                log(logCategory, message, LogLevel.INFO)
            }
        )
    }

    // Tap-to-focus handler - delegated to autofocus coordinator
    fun setRealSenseMeasurementPosition(normalizedX: Float, normalizedY: Float) {
        autofocusCoordinator.processTap(normalizedX, normalizedY)
    }

    // Select a face for autofocus in FACE_TRACKING mode
    fun selectFaceForFocus(normalizedX: Float, normalizedY: Float, imageWidth: Int, imageHeight: Int) {
        autofocusCoordinator.selectFaceForFocus(normalizedX, normalizedY, imageWidth, imageHeight)
    }

    // Load autofocus mapping from URI
    suspend fun loadAutofocusMapping(uri: Uri): Result<ValidationResult> {
        log(LogCategory.AUTOFOCUS, "Loading mapping from URI: $uri", LogLevel.INFO)

        return autofocusController.loadMapping(uri).fold(
            onSuccess = {
                val validation = autofocusState.value.mapping?.validate()
                if (validation?.isValid == true) {
                    settingsManager.setAutofocusEnabled(true)
                    autofocusState.value.mapping?.let { mapping ->
                        settingsManager.setAutofocusMappingName(mapping.name)
                        log(LogCategory.AUTOFOCUS,
                            "Successfully loaded mapping: ${mapping.name} with ${mapping.mappingPoints.size} points",
                            LogLevel.INFO)
                    }

                    // Log any warnings
                    validation.warnings.forEach { warning ->
                        log(LogCategory.AUTOFOCUS, "Warning: $warning", LogLevel.WARNING)
                    }

                    Result.success(validation)
                } else {
                    val errors = validation?.errors ?: listOf("Unknown validation error")
                    log(LogCategory.AUTOFOCUS,
                        "Mapping validation failed: ${errors.joinToString("; ")}",
                        LogLevel.ERROR)
                    Result.failure(IllegalArgumentException("Validation failed: ${errors.joinToString("; ")}"))
                }
            },
            onFailure = { error ->
                log(LogCategory.AUTOFOCUS,
                    "Failed to load mapping: ${error.message}",
                    LogLevel.ERROR)
                Result.failure(error)
            }
        )
    }

    // Load autofocus preset
    suspend fun loadAutofocusPreset(preset: MappingPreset): Result<ValidationResult> {
        log(LogCategory.AUTOFOCUS, "Loading preset: $preset", LogLevel.INFO)

        return autofocusController.loadPreset(preset).map {
            val validation = autofocusState.value.mapping?.validate()
            if (validation?.isValid == true) {
                settingsManager.setAutofocusEnabled(true)
                settingsManager.setAutofocusMappingPreset(preset.name)
            }
            validation ?: ValidationResult(false, listOf("Failed to load preset"))
        }
    }

    // Clear autofocus mapping
    fun clearAutofocusMapping() {
        autofocusController.clearMapping()
        settingsManager.setAutofocusEnabled(false)
        settingsManager.setAutofocusMappingName("")
        settingsManager.setAutofocusMode(FocusMode.MANUAL.name)
        log(LogCategory.AUTOFOCUS, "Mapping cleared, focus mode reset to MANUAL", LogLevel.INFO)
    }

    // Export current mapping
    suspend fun exportAutofocusMapping(fileName: String): Result<Unit> {
        val mapping = autofocusState.value.mapping
            ?: return Result.failure(IllegalStateException("No mapping loaded"))

        return AutofocusMappingManager(getApplication()).exportMapping(mapping, fileName).map { }
    }

    private fun setupObservers() {
        observeCoordinatorState()
        observeCameraConnectionState()
        observeCameraCapabilities()
    }

    // Observe camera capabilities and update supported resolutions
    private fun observeCameraCapabilities() {
        viewModelScope.launch {
            uvcCameraManager.capabilities.collect { capabilities ->
                val resolutions = capabilities?.supportedResolutions?.map {
                    "${it.width}x${it.height}"
                } ?: emptyList()

                _supportedCameraResolutions.value = resolutions

                if (resolutions.isNotEmpty()) {
                    log(LogCategory.CAMERA, "Camera supports ${resolutions.size} resolutions", LogLevel.INFO)

                    // Check if current resolution is supported
                    val currentResolution = settingsManager.getCameraResolution()
                    if (currentResolution !in resolutions) {
                        // Current resolution not supported, use the first available
                        val defaultResolution = resolutions.firstOrNull() ?: "1920x1080"
                        log(LogCategory.CAMERA, "Current resolution $currentResolution not supported, switching to $defaultResolution", LogLevel.WARNING)
                        settingsManager.setCameraResolution(defaultResolution)
                    }
                }
            }
        }
    }

    // Use the UvcCameraManager's built-in changeResolution method
    fun changeCameraResolution(resolution: String) {
        if (_isChangingResolution.value) {
            log(LogCategory.CAMERA, "Resolution change already in progress", LogLevel.WARNING)
            return
        }

        log(LogCategory.CAMERA, "Changing resolution to: $resolution", LogLevel.INFO)

        viewModelScope.launch {
            try {
                _isChangingResolution.value = true

                // Save the new resolution preference
                settingsManager.setCameraResolution(resolution)
                val newResolution = parseCameraResolution(resolution)

                // Save current surface for restoration (only needed if USB reset is required)
                val currentSurface = lastValidSurface

                // Use the camera manager's built-in resolution change method
                // This first tries a simple in-place change, then falls back to USB reset
                val success = uvcCameraManager.changeResolution(newResolution)

                if (success) {
                    log(LogCategory.CAMERA, "Resolution changed successfully to $resolution", LogLevel.INFO)

                    // Brief stabilization delay
                    delay(200)

                    // Restore surface if available and still valid (needed after USB reset fallback)
                    currentSurface?.let { surface ->
                        if (surface.isValid) {
                            try {
                                setSurface(surface)
                            } catch (e: IllegalStateException) {
                                log(LogCategory.CAMERA, "Surface became invalid during restoration: ${e.message}", LogLevel.WARNING)
                                lastValidSurface = null
                            }
                        } else {
                            lastValidSurface = null
                        }
                    }
                } else {
                    log(LogCategory.CAMERA, "Failed to change resolution to $resolution", LogLevel.ERROR)
                }

                _isChangingResolution.value = false

            } catch (e: Exception) {
                log(LogCategory.CAMERA, "Error changing resolution: ${e.message}", LogLevel.ERROR)
                _isChangingResolution.value = false
            }
        }
    }

    private fun handleDeviceEvent(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.Discovered -> {
                log(LogCategory.USB,
                    "Discovered ${event.deviceType.displayName}: ${event.device.deviceName} " +
                            "(${String.format("%04X:%04X", event.device.vendorId, event.device.productId)})",
                    LogLevel.INFO)
            }
            is DeviceEvent.Connected -> {
                log(getCategoryForDevice(event.device),
                    "Connected: ${event.deviceInfo}",
                    LogLevel.INFO)
            }
            is DeviceEvent.Disconnected -> {
                log(getCategoryForDevice(event.device),
                    "Disconnected: ${event.reason}",
                    LogLevel.WARNING)

                // Special handling for RealSense disconnection
                if (event.device?.deviceId == realSenseManager.currentDevice?.deviceId) {
                    log(LogCategory.REALSENSE,
                        "RealSense device removed, ensuring state is updated",
                        LogLevel.INFO)
                    viewModelScope.launch {
                        delay(50)
                        val currentState = realSenseConnectionState.value
                        if (currentState !is ConnectionState.Disconnected) {
                            log(LogCategory.REALSENSE,
                                "RealSense state not disconnected after device removal, forcing update",
                                LogLevel.WARNING)
                        }
                    }
                }
            }
            is DeviceEvent.Error -> {
                val deviceInfo = event.device?.let {
                    "${it.deviceName} (${String.format("%04X:%04X", it.vendorId, it.productId)})"
                } ?: "Unknown device"
                log(LogCategory.ERROR,
                    "Device error on $deviceInfo: ${event.error.message}",
                    LogLevel.ERROR)
            }
            is DeviceEvent.StateChanged -> {
                log(getCategoryForDevice(event.device),
                    "State changed to: ${event.newState::class.simpleName}",
                    LogLevel.DEBUG)
            }
            else -> {
                // Handle other events
            }
        }
    }

    private fun parseCameraResolution(resolutionString: String): Resolution {
        val parts = resolutionString.split("x")
        return if (parts.size == 2) {
            Resolution(parts[0].toIntOrNull() ?: 1920, parts[1].toIntOrNull() ?: 1080)
        } else {
            Resolution(1920, 1080)
        }
    }

    private fun observeCameraConnectionState() {
        viewModelScope.launch {
            combine(
                cameraConnectionState,
                isPreviewActive
            ) { connectionState, previewActive ->
                when {
                    previewActive -> CameraState.READY
                    connectionState is ConnectionState.Disconnected -> CameraState.IDLE
                    connectionState is ConnectionState.Connecting ||
                            connectionState is ConnectionState.AwaitingPermission -> CameraState.CONNECTING
                    connectionState is ConnectionState.Connected ||
                            connectionState is ConnectionState.Active -> CameraState.READY
                    connectionState is ConnectionState.Error -> CameraState.ERROR
                    connectionState is ConnectionState.Reconnecting -> CameraState.CONNECTING
                    else -> CameraState.IDLE
                }
            }.collect { state ->
                _cameraState.value = state
            }
        }
    }

    private fun loadInitialLogs() {
        val initialEntries = mutableListOf<LogEntry>()

        try {
            getApplication<Application>().assets.open("logo.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    initialEntries.add(LogEntry(
                        category = LogCategory.SYSTEM,
                        message = line,
                        level = LogLevel.INFO,
                        isRaw = true  // Don't format the logo
                    ))
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read ASCII art logo", e)
        }

        synchronized(logBuffer) {
            initialEntries.forEach { logBuffer.add(it) }
            _logMessages.value = logBuffer.toList()
        }
    }

    private fun observeCoordinatorState() {
        viewModelScope.launch {
            coordinatorState.collect { state ->
                state.conflicts.forEach { conflict ->
                    log(LogCategory.USB, "CONFLICT: $conflict", LogLevel.WARNING)
                }

                if (state.totalBandwidthUsed > 0) {
                    log(LogCategory.USB,
                        "USB Bandwidth: ${state.totalBandwidthUsed} Mbps used",
                        LogLevel.DEBUG)
                }

                if (state.conflicts.isNotEmpty()) {
                    usbCoordinator.clearConflicts()
                }
            }
        }
    }

    private fun getCategoryForDevice(device: UsbDevice?): LogCategory {
        return when {
            device == null -> LogCategory.USB
            uvcCameraManager.currentDevice?.deviceId == device.deviceId -> LogCategory.CAMERA
            motorControlManager.currentDevice?.deviceId == device.deviceId -> LogCategory.MOTOR
            realSenseManager.currentDevice?.deviceId == device.deviceId -> LogCategory.REALSENSE
            else -> LogCategory.USB
        }
    }

    private var isManagersRegistered = false

    fun register() {
        if (isManagersRegistered) {
            log(LogCategory.SYSTEM, "Device managers already registered, skipping", LogLevel.DEBUG)
            return
        }

        log(LogCategory.SYSTEM, "Starting device managers", LogLevel.INFO)
        isManagersRegistered = true

        deviceCoordinator.register()
        log(LogCategory.SYSTEM, "Alice is ready", LogLevel.INFO)
    }

    fun unregister() {
        if (!isManagersRegistered) {
            log(LogCategory.SYSTEM, "Device managers not registered, skipping unregister", LogLevel.DEBUG)
            return
        }

        log(LogCategory.SYSTEM, "Stopping device managers", LogLevel.INFO)
        isManagersRegistered = false

        deviceCoordinator.unregister()
    }

    fun isRegistered(): Boolean = isManagersRegistered

    fun setSurface(surface: Surface?) {
        val surfaceInfo = surface?.let { "Valid (${it.isValid}, hashCode=${it.hashCode()})" } ?: "null"
        log(LogCategory.CAMERA, "Setting camera surface: $surfaceInfo", LogLevel.DEBUG)

        // Validate surface before saving or using
        if (surface != null) {
            if (surface.isValid) {
                lastValidSurface = surface
            } else {
                log(LogCategory.CAMERA, "Provided surface is invalid, not saving", LogLevel.WARNING)
                // Clear invalid surface from storage
                if (lastValidSurface?.hashCode() == surface.hashCode()) {
                    lastValidSurface = null
                }
                return // Don't pass invalid surface to camera manager
            }
        } else {
            log(LogCategory.CAMERA, "Surface is null, clearing preview state", LogLevel.DEBUG)
        }

        // Check if camera manager is initialized and ready
        if (::uvcCameraManager.isInitialized) {
            try {
                uvcCameraManager.setSurface(surface)
            } catch (e: IllegalStateException) {
                log(LogCategory.CAMERA, "Failed to set surface: ${e.message}", LogLevel.ERROR)
                lastValidSurface = null
            }
        } else {
            log(LogCategory.CAMERA, "Camera manager not initialized, cannot set surface", LogLevel.WARNING)
        }
    }

    fun onPause() {
        log(LogCategory.SYSTEM, "onPause called", LogLevel.INFO)
    }

    fun setMotorPosition(position: Int, isManualControl: Boolean = false) {
        if (motorConnectionState.value is ConnectionState.Connected ||
            motorConnectionState.value is ConnectionState.Active) {

            // If this is manual control, switch to manual mode
            if (isManualControl) {
                if (autofocusState.value.mode != FocusMode.MANUAL) {
                    log(LogCategory.AUTOFOCUS,
                        "Manual motor control detected, switching to manual focus mode",
                        LogLevel.INFO)
                    settingsManager.setAutofocusMode(FocusMode.MANUAL.name)
                    settingsManager.setAutofocusEnabled(false)
                }
            }

            val offset = settingsManager.getMotorCalibrationOffset()
            val reverse = settingsManager.getMotorReverseDirection()

            // Apply offset with bounds checking to prevent overflow
            var adjustedPosition = (position + offset).coerceIn(0, 4095)

            // Apply reverse transformation with bounds checking
            if (reverse) {
                adjustedPosition = (4095 - adjustedPosition).coerceIn(0, 4095)
            }

            motorControlManager.setPosition(adjustedPosition)
        } else {
            log(LogCategory.MOTOR,
                "Cannot set position - motor not connected",
                LogLevel.WARNING)
        }
    }

    /**
     * Scan a specific motor address to test if a motor responds
     */
    fun scanMotorAddress(address: Int) {
        if (motorConnectionState.value is ConnectionState.Connected ||
            motorConnectionState.value is ConnectionState.Active) {
            log(LogCategory.MOTOR, "Scanning motor address: 0x${String.format("%04X", address)}", LogLevel.INFO)
            motorControlManager.scanAddress(address)
        } else {
            log(LogCategory.MOTOR, "Cannot scan address - motor not connected", LogLevel.WARNING)
        }
    }

    /**
     * Set the motor destination address in the firmware
     */
    fun setMotorDestination(address: Int) {
        if (motorConnectionState.value is ConnectionState.Connected ||
            motorConnectionState.value is ConnectionState.Active) {
            log(LogCategory.MOTOR, "Setting motor destination: 0x${String.format("%04X", address)}", LogLevel.INFO)
            motorControlManager.setDestination(address)
        } else {
            log(LogCategory.MOTOR, "Cannot set destination - motor not connected", LogLevel.WARNING)
        }
    }

    fun getDeviceCapabilities(deviceType: DeviceType): StateFlow<DeviceCapabilities?> {
        return deviceCoordinator.getDeviceCapabilities(deviceType)
    }

    fun getConnectedDevices(): List<UsbDeviceCoordinator.DeviceInfo> {
        return deviceCoordinator.getAllConnectedDevices()
    }

    fun getActiveDevices(): List<UsbDeviceCoordinator.DeviceInfo> {
        return deviceCoordinator.getAllActiveDevices()
    }

    fun isCameraActive(): Boolean {
        return deviceCoordinator.isDeviceTypeActive(DeviceType.Camera)
    }

    fun isMotorActive(): Boolean {
        return deviceCoordinator.isDeviceTypeActive(DeviceType.Motor)
    }

    fun isRealSenseActive(): Boolean {
        return deviceCoordinator.isDeviceTypeActive(DeviceType.RealSense)
    }

    fun getConnectedCameras(): List<UsbDeviceCoordinator.DeviceInfo> {
        return deviceCoordinator.getConnectedDevicesByType(DeviceType.Camera)
    }

    fun getConnectedMotors(): List<UsbDeviceCoordinator.DeviceInfo> {
        return deviceCoordinator.getConnectedDevicesByType(DeviceType.Motor)
    }

    fun getConnectedRealSenseDevices(): List<UsbDeviceCoordinator.DeviceInfo> {
        return deviceCoordinator.getConnectedDevicesByType(DeviceType.RealSense)
    }

    protected fun log(category: LogCategory, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            category = category,
            message = message,
            level = level
        )

        synchronized(logBuffer) {
            logBuffer.add(entry)
            _logMessages.value = logBuffer.toList()
        }

        val tag = "$TAG:${category.name}"
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        log(LogCategory.SYSTEM, "ViewModel clearing - releasing resources", LogLevel.WARNING)

        performanceJob?.cancel()

        // Destroy coordinators (which handle cleanup of managers)
        if (::autofocusCoordinator.isInitialized) {
            autofocusCoordinator.destroy()
        }
        if (::deviceCoordinator.isInitialized) {
            deviceCoordinator.destroy()
        }
    }
}

// Extension functions for UI convenience
fun ConnectionState.toDisplayString(): String = when (this) {
    is ConnectionState.Disconnected -> "Disconnected"
    is ConnectionState.Discovering -> "Discovering..."
    is ConnectionState.Connecting -> "Connecting..."
    is ConnectionState.AwaitingPermission -> "Awaiting Permission"
    is ConnectionState.Connected -> "Connected"
    is ConnectionState.Active -> "Active"
    is ConnectionState.Error -> "Error: $message"
    is ConnectionState.Reconnecting -> "Reconnecting ($attempt/$maxAttempts)"
}

fun ConnectionState.isOperational(): Boolean = when (this) {
    is ConnectionState.Connected, is ConnectionState.Active -> true
    else -> false
}