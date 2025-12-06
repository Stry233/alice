package com.selkacraft.alice.comm.autofocus

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Main controller for the autofocus system.
 *
 * Orchestrates depth-to-focus mapping operations, supporting multiple focus modes:
 * - Manual: Direct motor position control
 * - Single Auto: One-shot focus based on current depth
 * - Continuous Auto: Continuous focus tracking
 * - Face Tracking: Focus on detected faces
 *
 * Consumes depth measurements from RealSense and converts them to motor positions
 * using calibrated [AutofocusMapping] data.
 */
class AutofocusController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutofocusController"
        /** Debounce interval for focus updates (~30 Hz) */
        private const val FOCUS_DEBOUNCE_MS = 33L
        /** Position smoothing factor (0-1, lower = more smoothing) */
        private const val FOCUS_SMOOTHING_FACTOR = 0.2f
        /** Default confidence threshold for depth measurements */
        private const val CONFIDENCE_THRESHOLD_DEFAULT = 0.7f
    }

    // Component instances
    private val mappingManager = AutofocusMappingManager(context)

    // State management
    private val _state = MutableStateFlow(AutofocusState())
    val state: StateFlow<AutofocusState> = _state.asStateFlow()

    // Events for UI feedback
    private val _events = MutableSharedFlow<AutofocusEvent>()
    val events: SharedFlow<AutofocusEvent> = _events.asSharedFlow()

    // Settings
    private var confidenceThreshold = CONFIDENCE_THRESHOLD_DEFAULT
    private var enableSmoothing = true
    private var responseSpeed = 50 // 0-100

    // Focus tracking
    private var continuousFocusJob: Job? = null
    private var singleFocusJob: Job? = null  // Track single focus operations
    private var faceFocusJob: Job? = null  // Track face tracking operations
    private var lastFocusTime = 0L
    private var lastMotorPosition: Int? = null

    init {
        // Load saved mapping on initialization
        scope.launch {
            mappingManager.loadSavedMapping()?.let { mapping ->
                setMapping(mapping)
            }
        }
    }

    /**
     * Set focus mode
     */
    fun setFocusMode(mode: FocusMode) {
        Log.d(TAG, "Setting focus mode to: $mode")

        // Clear any previous error when switching modes to allow fresh start
        _state.update { it.copy(mode = mode, error = null) }

        // Handle mode transitions
        when (mode) {
            FocusMode.MANUAL -> {
                stopContinuousFocus()
                stopSingleFocus()  // Cancel any ongoing single focus operation
                stopFaceTracking()  // Cancel face tracking
                _state.update { it.copy(isActivelyFocusing = false) }
            }
            FocusMode.SINGLE_AUTO -> {
                stopContinuousFocus()
                stopFaceTracking()
                // Don't stop single focus here - user might be switching from continuous to single
            }
            FocusMode.CONTINUOUS_AUTO -> {
                stopSingleFocus()  // Cancel any ongoing single focus operation
                stopFaceTracking()
                // Check both isEnabled and canActivate since setEnabled might have
                // been called before setFocusMode when both change together
                if (_state.value.isEnabled && _state.value.canActivate) {
                    startContinuousFocus()
                }
            }
            FocusMode.FACE_TRACKING -> {
                stopContinuousFocus()
                stopSingleFocus()
                // Check both isEnabled and canActivate since setEnabled might have
                // been called before setFocusMode when both change together
                if (_state.value.isEnabled && _state.value.canActivate) {
                    startFaceTracking()
                }
            }
            else -> {
                Log.w(TAG, "Unsupported focus mode: $mode")
            }
        }

        scope.launch {
            _events.emit(AutofocusEvent.ModeChanged(mode))
        }
    }

    /**
     * Enable or disable autofocus
     */
    fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting autofocus enabled: $enabled")

        _state.update { it.copy(isEnabled = enabled) }

        if (enabled && _state.value.canActivate) {
            when (_state.value.mode) {
                FocusMode.CONTINUOUS_AUTO -> startContinuousFocus()
                FocusMode.FACE_TRACKING -> startFaceTracking()
                else -> {}
            }
        } else if (!enabled) {
            stopContinuousFocus()
            stopSingleFocus()  // Cancel any ongoing single focus operation
            stopFaceTracking()  // Cancel face tracking
            _state.update { it.copy(isActivelyFocusing = false) }
        }
    }

    /**
     * Load mapping from URI
     */
    suspend fun loadMapping(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading mapping from URI: $uri")

            val mapping = mappingManager.loadFromUri(uri, context.contentResolver)
                .getOrElse {
                    _state.update { it.copy(error = AutofocusError.MappingInvalid) }
                    return@withContext Result.failure(it)
                }

            setMapping(mapping)
            _events.emit(AutofocusEvent.MappingLoaded(mapping.name))

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mapping", e)
            _state.update { it.copy(error = AutofocusError.MappingInvalid) }
            Result.failure(e)
        }
    }

    /**
     * Load a preset mapping
     */
    suspend fun loadPreset(preset: MappingPreset): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mapping = mappingManager.createPreset(preset)
            setMapping(mapping)
            _events.emit(AutofocusEvent.MappingLoaded(mapping.name))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preset", e)
            _state.update { it.copy(error = AutofocusError.MappingInvalid) }
            Result.failure(e)
        }
    }

    /**
     * Clear current mapping
     */
    fun clearMapping() {
        Log.d(TAG, "Clearing mapping")

        stopContinuousFocus()
        stopSingleFocus()
        stopFaceTracking()
        _state.update {
            it.copy(
                mapping = null,
                mode = FocusMode.MANUAL,
                isEnabled = false,
                isActivelyFocusing = false,
                error = null
            )
        }

        mappingManager.clearSavedMapping()

        scope.launch {
            _events.emit(AutofocusEvent.MappingCleared)
            _events.emit(AutofocusEvent.ModeChanged(FocusMode.MANUAL))
        }
    }

    /**
     * Update device readiness
     */
    fun updateDeviceReadiness(isMotorReady: Boolean, isDepthSensorReady: Boolean) {
        val wasReady = _state.value.canActivate

        _state.update {
            it.copy(
                isMotorReady = isMotorReady,
                isDepthSensorReady = isDepthSensorReady,
                error = if (!isMotorReady || !isDepthSensorReady) {
                    AutofocusError.DevicesNotReady
                } else if (it.error is AutofocusError.DevicesNotReady) {
                    null
                } else {
                    it.error
                }
            )
        }

        val isReady = _state.value.canActivate

        // Handle readiness transitions
        if (!wasReady && isReady) {
            Log.d(TAG, "Devices now ready for autofocus")
            if (_state.value.isEnabled) {
                when (_state.value.mode) {
                    FocusMode.CONTINUOUS_AUTO -> startContinuousFocus()
                    FocusMode.FACE_TRACKING -> startFaceTracking()
                    else -> {}
                }
            }
        } else if (wasReady && !isReady) {
            Log.d(TAG, "Devices no longer ready for autofocus")
            stopContinuousFocus()
            stopFaceTracking()
        }
    }

    /**
     * Process a tap for tap-to-focus
     */
    fun processTap(normalizedX: Float, normalizedY: Float) {
        if (!_state.value.canActivate) {
            Log.w(TAG, "Cannot process tap - autofocus not ready")
            return
        }

        val focusPoint = FocusPoint(normalizedX, normalizedY)
        _state.update { it.copy(focusPoint = focusPoint) }

        when (_state.value.mode) {
            FocusMode.SINGLE_AUTO -> {
                Log.d(TAG, "Triggering single autofocus at ($normalizedX, $normalizedY)")
                triggerSingleFocus(focusPoint)
            }
            FocusMode.CONTINUOUS_AUTO -> {
                Log.d(TAG, "Updating continuous focus target to ($normalizedX, $normalizedY)")
                // Continuous focus will automatically use the new focus point
            }
            else -> {
                Log.d(TAG, "Tap ignored in mode: ${_state.value.mode}")
            }
        }
    }

    /**
     * Process depth data from sensor
     */
    fun processDepthData(depth: Float, confidence: Float, x: Float = 0.5f, y: Float = 0.5f) {
        if (!_state.value.isActive) return

        _state.update {
            it.copy(
                currentDepth = depth,
                focusConfidence = confidence
            )
        }

        // Only process if confidence is sufficient
        if (confidence < confidenceThreshold) {
            return
        }

        // Check if this matches our focus point (if set)
        val focusPoint = _state.value.focusPoint
        if (focusPoint != null) {
            val distance = kotlin.math.sqrt(
                (x - focusPoint.x) * (x - focusPoint.x) +
                        (y - focusPoint.y) * (y - focusPoint.y)
            )
            // Only process if measurement is near our focus point
            if (distance > 0.1f) return
        }

        // For continuous mode, process immediately (with debouncing)
        if (_state.value.mode == FocusMode.CONTINUOUS_AUTO) {
            val now = System.currentTimeMillis()
            if (now - lastFocusTime >= FOCUS_DEBOUNCE_MS) {
                lastFocusTime = now
                calculateAndApplyFocus(depth)
            }
        }
    }

    /**
     * Get motor position command for current state
     */
    fun getMotorPositionCommand(): Int? {
        return _state.value.targetMotorPosition
    }

    /**
     * Update configuration
     */
    fun updateConfiguration(
        confidenceThreshold: Float? = null,
        enableSmoothing: Boolean? = null,
        responseSpeed: Int? = null
    ) {
        confidenceThreshold?.let { this.confidenceThreshold = it }
        enableSmoothing?.let { this.enableSmoothing = it }
        responseSpeed?.let { this.responseSpeed = it.coerceIn(0, 100) }
    }

    // Private methods

    private fun setMapping(mapping: AutofocusMapping) {
        val validation = mapping.validate()

        if (!validation.isValid) {
            Log.e(TAG, "Mapping validation failed: ${validation.errors.joinToString()}")
            _state.update { it.copy(error = AutofocusError.MappingInvalid) }
            return
        }

        _state.update {
            it.copy(
                mapping = mapping,
                error = null
            )
        }

        // Save for persistence
        scope.launch {
            mappingManager.saveMapping(mapping)
        }

        Log.i(TAG, "Mapping set: ${mapping.name} with ${mapping.mappingPoints.size} points")

        // Restart focus modes if enabled and ready after loading new mapping
        if (_state.value.isEnabled && _state.value.canActivate) {
            when (_state.value.mode) {
                FocusMode.CONTINUOUS_AUTO -> startContinuousFocus()
                FocusMode.FACE_TRACKING -> startFaceTracking()
                else -> {}
            }
        }
    }

    private fun startContinuousFocus() {
        if (continuousFocusJob?.isActive == true) return

        Log.d(TAG, "Starting continuous autofocus")

        continuousFocusJob = scope.launch {
            _events.emit(AutofocusEvent.FocusStarted)
            _state.update { it.copy(isActivelyFocusing = true) }

            // Continuous focus is driven by processDepthData calls
            // This job only exists to track the active state and handle cleanup
            // It suspends indefinitely until cancelled, avoiding wasteful polling
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    // Just keep the coroutine alive until cancelled
                    // processDepthData will be called from external depth updates
                    continuation.invokeOnCancellation {
                        _state.update { it.copy(isActivelyFocusing = false) }
                    }
                }
            } finally {
                _state.update { it.copy(isActivelyFocusing = false) }
            }
        }
    }

    private fun stopContinuousFocus() {
        continuousFocusJob?.cancel()
        continuousFocusJob = null
        _state.update { it.copy(isActivelyFocusing = false) }
    }

    private fun stopSingleFocus() {
        singleFocusJob?.cancel()
        singleFocusJob = null
        _state.update { it.copy(isActivelyFocusing = false) }
    }

    private fun triggerSingleFocus(focusPoint: FocusPoint) {
        // Cancel any existing single focus operation
        singleFocusJob?.cancel()

        singleFocusJob = scope.launch {
            try {
                _events.emit(AutofocusEvent.FocusStarted)
                _state.update { it.copy(isActivelyFocusing = true) }

                // Wait for depth data at the focus point
                delay(200) // Allow time for depth sensor to update

                // Check if still in single auto mode
                if (_state.value.mode != FocusMode.SINGLE_AUTO) {
                    Log.d(TAG, "Single focus cancelled - mode changed")
                    return@launch
                }

                val depth = _state.value.currentDepth
                val confidence = _state.value.focusConfidence

                if (depth > 0 && confidence >= confidenceThreshold) {
                    calculateAndApplyFocus(depth)
                    delay(500) // Hold focus briefly

                    // Check again if still in single auto mode
                    if (_state.value.mode == FocusMode.SINGLE_AUTO) {
                        _events.emit(AutofocusEvent.FocusAchieved)
                    }
                } else {
                    _events.emit(AutofocusEvent.FocusLost("Insufficient depth data"))
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Single focus cancelled")
                throw e  // Re-throw to propagate cancellation
            } finally {
                _state.update { it.copy(isActivelyFocusing = false) }
                singleFocusJob = null
            }
        }
    }

    private fun calculateAndApplyFocus(depth: Float) {
        // Don't apply focus if in manual mode
        if (_state.value.mode == FocusMode.MANUAL) {
            Log.d(TAG, "Skipping focus calculation - in manual mode")
            return
        }

        // Capture mapping reference early to prevent TOCTOU race condition
        // This ensures the mapping can't change to null mid-calculation
        val mapping = _state.value.mapping
        if (mapping == null) {
            Log.e(TAG, "Mapping is null during focus calculation")
            _state.update {
                it.copy(
                    error = AutofocusError.MappingInvalid,
                    isActivelyFocusing = false
                )
            }
            scope.launch {
                _events.emit(AutofocusEvent.Error(AutofocusError.MappingInvalid))
                _events.emit(AutofocusEvent.FocusLost("Mapping became invalid"))
            }
            stopContinuousFocus()
            stopSingleFocus()
            return
        }

        try {
            // Validate depth value with comprehensive checks
            if (!depth.isFinite() || depth <= 0f || depth > 10f) {
                Log.w(TAG, "Invalid depth value: $depth meters (must be finite and 0-10m)")
                return
            }

            // Use the captured mapping reference throughout
            val targetPosition = mapping.getMotorPosition(depth)
            if (targetPosition == null) {
                Log.w(TAG, "Could not calculate motor position for depth: $depth")
                _state.update {
                    it.copy(error = AutofocusError.CalculationError("Depth out of mapping range"))
                }
                return
            }

            // Validate motor position range with extra safety bounds
            if (targetPosition < 0 || targetPosition > 4095) {
                Log.e(TAG, "Calculated motor position out of range: $targetPosition")
                _state.update {
                    it.copy(error = AutofocusError.CalculationError("Motor position out of range"))
                }
                return
            }

            // Apply smoothing with safe arithmetic
            val finalPosition = if (enableSmoothing && lastMotorPosition != null) {
                // Safe calculation to prevent overflow
                val lastPos = lastMotorPosition!!.toFloat()
                val targetPos = targetPosition.toFloat()
                val smoothed = (lastPos * (1f - FOCUS_SMOOTHING_FACTOR) +
                        targetPos * FOCUS_SMOOTHING_FACTOR)

                // Ensure result is valid before converting to int
                if (!smoothed.isFinite()) {
                    Log.w(TAG, "Smoothing calculation resulted in non-finite value, using target")
                    targetPosition
                } else {
                    smoothed.toInt().coerceIn(0, 4095)
                }
            } else {
                targetPosition
            }

            lastMotorPosition = finalPosition

            // Update state atomically
            _state.update {
                it.copy(
                    targetMotorPosition = finalPosition,
                    currentMotorPosition = finalPosition,
                    error = null,  // Clear any previous errors
                    stats = it.stats.copy(
                        totalFocusOperations = it.stats.totalFocusOperations + 1,
                        lastDepth = depth,
                        lastPosition = finalPosition,
                        averageDepth = ((it.stats.averageDepth * it.stats.totalFocusOperations) + depth) /
                                (it.stats.totalFocusOperations + 1)
                    )
                )
            }

            Log.v(TAG, "Focus: depth=${String.format("%.2f", depth)}m -> motor=$finalPosition")

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating focus position", e)
            _state.update {
                it.copy(
                    error = AutofocusError.CalculationError(e.message ?: "Unknown error"),
                    isActivelyFocusing = false
                )
            }
            scope.launch {
                _events.emit(AutofocusEvent.Error(AutofocusError.CalculationError(e.message ?: "Unknown error")))
                _events.emit(AutofocusEvent.FocusLost("Calculation error: ${e.message}"))
            }
            stopContinuousFocus()
            stopSingleFocus()
        }
    }

    /**
     * Update face detection state for FACE_TRACKING mode
     */
    fun updateFaceDetectionState(faceDetectionState: FaceDetectionState) {
        _state.update { it.copy(faceDetectionState = faceDetectionState) }

        // In face tracking mode, automatically use detected faces for autofocus
        if (_state.value.mode == FocusMode.FACE_TRACKING &&
            _state.value.isActivelyFocusing &&
            _state.value.canActivate) {

            val targetFace = faceDetectionState.defaultFocusTarget
            if (targetFace != null) {
                // Use the face center point for depth measurement
                val depth = _state.value.currentDepth
                val confidence = _state.value.focusConfidence

                // Process depth data if confidence is sufficient
                if (depth > 0 && confidence >= confidenceThreshold) {
                    val now = System.currentTimeMillis()
                    if (now - lastFocusTime >= FOCUS_DEBOUNCE_MS) {
                        lastFocusTime = now
                        calculateAndApplyFocus(depth)
                    }
                }
            }
        }
    }

    /**
     * Select a face for autofocus in FACE_TRACKING mode
     */
    fun selectFaceForFocus(faceId: Int?) {
        if (_state.value.mode != FocusMode.FACE_TRACKING) {
            Log.w(TAG, "Can only select face in FACE_TRACKING mode")
            return
        }

        _state.update { currentState ->
            currentState.copy(
                faceDetectionState = currentState.faceDetectionState.copy(
                    selectedFaceId = faceId
                )
            )
        }

        Log.d(TAG, "Selected face for focus: $faceId")
    }

    /**
     * Process a tap in FACE_TRACKING mode to select a face
     */
    fun processFaceTap(normalizedX: Float, normalizedY: Float, imageWidth: Int, imageHeight: Int) {
        if (_state.value.mode != FocusMode.FACE_TRACKING) {
            return
        }

        val faceDetectionState = _state.value.faceDetectionState
        val tappedFace = faceDetectionState.detectedFaces.find { face ->
            face.contains(normalizedX, normalizedY, imageWidth, imageHeight)
        }

        if (tappedFace != null) {
            selectFaceForFocus(tappedFace.trackingId)
        } else {
            // Tap outside any face - clear selection
            selectFaceForFocus(null)
        }
    }

    private fun startFaceTracking() {
        if (faceFocusJob?.isActive == true) return

        Log.d(TAG, "Starting face tracking autofocus")

        faceFocusJob = scope.launch {
            _events.emit(AutofocusEvent.FocusStarted)
            _state.update { it.copy(isActivelyFocusing = true) }

            // Face tracking is driven by updateFaceDetectionState calls
            // This job only exists to track the active state and handle cleanup
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation {
                        _state.update { it.copy(isActivelyFocusing = false) }
                    }
                }
            } finally {
                _state.update { it.copy(isActivelyFocusing = false) }
            }
        }
    }

    private fun stopFaceTracking() {
        faceFocusJob?.cancel()
        faceFocusJob = null
        _state.update { it.copy(isActivelyFocusing = false) }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        stopContinuousFocus()
        stopSingleFocus()
        stopFaceTracking()
        clearMapping()
    }
}