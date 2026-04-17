package com.selkacraft.alice.coordination

import android.content.Context
import com.selkacraft.alice.comm.autofocus.*
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.comm.motor.MotorControlManager
import com.selkacraft.alice.comm.realsense.RealSenseManager
import com.selkacraft.alice.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Coordinates autofocus operations between motor, RealSense depth sensor, and settings.
 * This class encapsulates all the integration logic for the autofocus system.
 *
 * The enhanced AF-F mode now includes:
 * - ONNX YOLO-based face detection for robust tracking at all distances
 * - Eye tracking for precise focus on subject's eyes (like Sony Real-time Eye AF)
 * - Kalman filter-based subject tracking for smooth, predictive focus
 * - Priority scoring for intelligent face selection
 */
class AutofocusCoordinator(
    private val context: Context,
    private val autofocusController: AutofocusController,
    private val motorManager: MotorControlManager,
    private val realSenseManager: RealSenseManager,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
    private val onMotorPositionCommand: (Int) -> Unit,
    private val onLogMessage: (String, String) -> Unit,  // (category, message)
    /** Optional external override for device readiness (e.g., from remote sync) */
    private val externalMotorReady: StateFlow<Boolean>? = null,
    private val externalRealSenseReady: StateFlow<Boolean>? = null
) {
    // Face detection processor for FACE_TRACKING mode with eye tracking support
    private val faceDetectionProcessor = FaceDetectionProcessor(context, scope, onLogMessage)

    // Expose autofocus state
    val state: StateFlow<AutofocusState> = autofocusController.state
    val events: SharedFlow<AutofocusEvent> = autofocusController.events

    // Expose face detection state for UI
    val faceDetectionState: StateFlow<FaceDetectionState> = faceDetectionProcessor.faceDetectionState

    // Primary-face handoff hysteresis. In AF-F we don't want the focus point
    // to flicker between two similarly-scored faces every frame, so we keep
    // the last-chosen face "sticky": a challenger only steals the primary
    // slot if its score beats the incumbent by at least 15 %. Resets on
    // manual tap selection and on leaving FACE_TRACKING mode.
    private var lastPrimaryFaceId: Int? = null
    private val PRIMARY_SCORE_HYSTERESIS = 1.15f

    private fun pickPrimaryWithHysteresis(state: FaceDetectionState): DetectedFace? {
        // If the user has manually tapped a face, honour that selection —
        // FaceDetectionState.defaultFocusTarget already does this but we
        // re-check explicitly so we can update lastPrimaryFaceId to match
        // and seamlessly take over after the manual selection is cleared.
        state.selectedFace?.let {
            lastPrimaryFaceId = it.trackingId
            return it
        }

        val faces = state.detectedFaces
        if (faces.isEmpty()) {
            lastPrimaryFaceId = null
            return null
        }

        val topScored = faces.maxByOrNull { it.score } ?: return null
        val incumbent = faces.find { it.trackingId == lastPrimaryFaceId }

        val primary = when {
            incumbent == null -> topScored
            incumbent.trackingId == topScored.trackingId -> topScored
            topScored.score >= incumbent.score * PRIMARY_SCORE_HYSTERESIS -> topScored
            else -> incumbent
        }
        lastPrimaryFaceId = primary.trackingId
        return primary
    }

    /**
     * Initialize all autofocus integration logic
     */
    fun initialize() {
        // Safety: Always start in MANUAL mode on app startup
        // This prevents dangerous autofocus activation without user confirmation
        resetFocusModeToManual("App startup")

        // Initialize enhanced face detection processor (ONNX + ML Kit)
        scope.launch {
            val result = faceDetectionProcessor.initialize()
            if (result.isSuccess) {
                if (faceDetectionProcessor.isOnnxAvailable()) {
                    onLogMessage("FACE_TRACKING", "YOLO Eye AF ready")
                } else {
                    onLogMessage("FACE_TRACKING", "ML Kit fallback (no YOLO model)")
                }
            } else {
                onLogMessage("FACE_TRACKING", "Face detector init failed")
            }
        }

        setupDeviceReadinessMonitoring()
        setupDepthDataProcessing()
        setupMotorCommandApplication()
        setupSettingsSynchronization()
        setupEventLogging()
        setupFaceDetectionProcessing()
    }

    /**
     * Reset focus mode to MANUAL for safety.
     * Called on app startup and device reconnection to prevent unexpected autofocus behavior.
     */
    private fun resetFocusModeToManual(reason: String) {
        val currentMode = settingsManager.autofocusMode.value
        if (currentMode != FocusMode.MANUAL.name) {
            settingsManager.setAutofocusMode(FocusMode.MANUAL.name)
            settingsManager.setAutofocusEnabled(false)
            onLogMessage("AUTOFOCUS", "Focus mode reset to MANUAL ($reason)")
        }
    }

    // Track previous device readiness state for reconnection detection
    private var wasMotorReady = false
    private var wasRealSenseReady = false

    /**
     * Monitor device connection states and update autofocus controller.
     * Also resets focus mode to MANUAL when core devices reconnect for safety.
     */
    private fun setupDeviceReadinessMonitoring() {
        // Combine local connection states with optional external (remote) readiness
        val motorReadyFlow: Flow<Boolean> = if (externalMotorReady != null) {
            combine(motorManager.connectionState, externalMotorReady) { localState, extReady ->
                (localState is ConnectionState.Connected || localState is ConnectionState.Active) || extReady
            }
        } else {
            motorManager.connectionState.map { it is ConnectionState.Connected || it is ConnectionState.Active }
        }

        val realSenseReadyFlow: Flow<Boolean> = if (externalRealSenseReady != null) {
            combine(realSenseManager.connectionState, externalRealSenseReady) { localState, extReady ->
                (localState is ConnectionState.Connected || localState is ConnectionState.Active) || extReady
            }
        } else {
            realSenseManager.connectionState.map { it is ConnectionState.Connected || it is ConnectionState.Active }
        }

        // Monitor LOCAL connection states for reconnection safety reset
        // (only reset to MANUAL when physical USB hardware reconnects, not remote sync)
        scope.launch {
            combine(
                motorManager.connectionState,
                realSenseManager.connectionState
            ) { motorState, rsState ->
                val motorLocal = motorState is ConnectionState.Connected || motorState is ConnectionState.Active
                val rsLocal = rsState is ConnectionState.Connected || rsState is ConnectionState.Active
                motorLocal to rsLocal
            }.collect { (motorLocal, rsLocal) ->
                if (motorLocal && !wasMotorReady) {
                    resetFocusModeToManual("Motor dongle reconnected")
                }
                if (rsLocal && !wasRealSenseReady) {
                    resetFocusModeToManual("RealSense camera reconnected")
                }
                wasMotorReady = motorLocal
                wasRealSenseReady = rsLocal
            }
        }

        // Monitor EFFECTIVE (local+remote) connection states for autofocus readiness
        scope.launch {
            combine(motorReadyFlow, realSenseReadyFlow) { motor, rs -> motor to rs }
                .collect { (motorReady, realSenseReady) ->
                    onLogMessage("AUTOFOCUS", "Device readiness update: motor=$motorReady, realsense=$realSenseReady")
                    autofocusController.updateDeviceReadiness(motorReady, realSenseReady)
                }
        }
    }

    /**
     * Monitor depth data from RealSense and feed to autofocus controller
     */
    private fun setupDepthDataProcessing() {
        scope.launch {
            combine(
                realSenseManager.centerDepth,
                realSenseManager.depthConfidence,
                realSenseManager.measurementPosition
            ) { depth, confidence, position ->
                Triple(depth, confidence, position)
            }.collect { (depth, confidence, position) ->
                if (depth > 0) {
                    autofocusController.processDepthData(
                        depth,
                        confidence,
                        position.first,
                        position.second
                    )
                }
            }
        }
    }

    /**
     * Apply motor position commands from autofocus controller
     */
    private fun setupMotorCommandApplication() {
        scope.launch {
            autofocusController.state.collect { state ->
                if (state.targetMotorPosition != null && state.isActivelyFocusing) {
                    onMotorPositionCommand(state.targetMotorPosition)
                }
            }
        }
    }

    /**
     * Sync settings changes with autofocus controller
     */
    private fun setupSettingsSynchronization() {
        scope.launch {
            combine(
                settingsManager.autofocusEnabled,
                settingsManager.autofocusMode,
                settingsManager.autofocusConfidenceThreshold,
                settingsManager.autofocusSmoothing,
                settingsManager.autofocusResponseSpeed
            ) { enabled, modeString, confidence, smoothing, speed ->
                data class AutofocusSettings(
                    val enabled: Boolean,
                    val mode: FocusMode,
                    val confidence: Float,
                    val smoothing: Boolean,
                    val speed: Int
                )
                val mode = try {
                    FocusMode.valueOf(modeString)
                } catch (e: Exception) {
                    FocusMode.MANUAL
                }
                AutofocusSettings(enabled, mode, confidence, smoothing, speed)
            }.collect { settings ->
                onLogMessage("AUTOFOCUS", "Settings sync: enabled=${settings.enabled}, mode=${settings.mode}, canActivate=${autofocusController.state.value.canActivate}, motorReady=${autofocusController.state.value.isMotorReady}, depthReady=${autofocusController.state.value.isDepthSensorReady}, mapping=${autofocusController.state.value.mapping?.name}")
                autofocusController.setEnabled(settings.enabled)
                autofocusController.setFocusMode(settings.mode)
                autofocusController.updateConfiguration(
                    confidenceThreshold = settings.confidence,
                    smoothingAlpha = if (settings.smoothing) null else 1.0f
                )
            }
        }
    }

    /**
     * Monitor autofocus events and forward to logging
     */
    private fun setupEventLogging() {
        scope.launch {
            autofocusController.events.collect { event ->
                val message = when (event) {
                    is AutofocusEvent.FocusStarted -> "Focus started"
                    is AutofocusEvent.FocusAchieved -> "Focus achieved"
                    is AutofocusEvent.FocusLost -> "Focus lost: ${event.reason}"
                    is AutofocusEvent.MappingLoaded -> "Mapping loaded: ${event.name}"
                    is AutofocusEvent.MappingCleared -> "Mapping cleared"
                    is AutofocusEvent.Error -> "Error: ${event.error.message}"
                    is AutofocusEvent.ModeChanged -> "Mode changed to: ${event.newMode}"
                }
                onLogMessage("AUTOFOCUS", message)
            }
        }
    }

    /**
     * Setup face detection processing pipeline with eye tracking.
     * Uses the enhanced focus point (eye position when available, face center otherwise).
     */
    private fun setupFaceDetectionProcessing() {
        // Process color frames for face detection
        scope.launch {
            realSenseManager.colorBitmap.collect { colorBitmap ->
                if (colorBitmap != null && autofocusController.state.value.mode == FocusMode.FACE_TRACKING) {
                    faceDetectionProcessor.processFrame(colorBitmap)
                }
            }
        }

        // Update autofocus controller with face detection state
        scope.launch {
            faceDetectionProcessor.faceDetectionState.collect { faceState ->
                autofocusController.updateFaceDetectionState(faceState)

                // Update RealSense measurement position based on selected face's focus point
                // This now uses eye position when available (DSLR-quality Eye AF).
                // pickPrimaryWithHysteresis keeps the primary "sticky" between
                // near-tied candidates so the measurement point doesn't flicker.
                if (autofocusController.state.value.mode == FocusMode.FACE_TRACKING) {
                    val targetFace = pickPrimaryWithHysteresis(faceState)
                    if (targetFace != null) {
                        // Use the focus point (eye when available, face center otherwise)
                        val focusPoint = targetFace.getFocusPointFor(faceState.focusTargetPreference)
                        realSenseManager.setMeasurementPosition(focusPoint.x, focusPoint.y)

                        // Log tracking state changes for debugging
                        val trackingInfo = when (targetFace.trackingState) {
                            TrackingState.EYE_LOCKED -> "Eye locked"
                            TrackingState.FACE_ONLY -> "Face tracking"
                            TrackingState.PREDICTED -> "Predicting position"
                            TrackingState.LOST -> "Subject lost"
                        }
                        // Only log state changes to avoid spam
                    }
                } else {
                    // Not in AF-F: drop the sticky primary so the next entry
                    // into the mode starts from a clean slate.
                    lastPrimaryFaceId = null
                }
            }
        }
    }

    /**
     * Process tap for autofocus
     */
    fun processTap(normalizedX: Float, normalizedY: Float, imageWidth: Int = 0, imageHeight: Int = 0) {
        val currentMode = autofocusController.state.value.mode

        if (currentMode == FocusMode.FACE_TRACKING && imageWidth > 0 && imageHeight > 0) {
            // In face tracking mode, check if tap is on a face
            autofocusController.processFaceTap(normalizedX, normalizedY, imageWidth, imageHeight)
            onLogMessage(
                "FACE_TRACKING",
                "Tap at (${String.format("%.2f", normalizedX)}, ${String.format("%.2f", normalizedY)})"
            )
        } else {
            // Normal tap-to-focus behavior
            // Update RealSense measurement position
            realSenseManager.setMeasurementPosition(normalizedX, normalizedY)

            // Process tap for autofocus
            autofocusController.processTap(normalizedX, normalizedY)

            onLogMessage(
                "AUTOFOCUS",
                "Tap at (${String.format("%.2f", normalizedX)}, ${String.format("%.2f", normalizedY)})"
            )
        }
    }

    /**
     * Select a face for autofocus in FACE_TRACKING mode
     */
    fun selectFaceForFocus(normalizedX: Float, normalizedY: Float, imageWidth: Int, imageHeight: Int) {
        faceDetectionProcessor.selectFaceAt(normalizedX, normalizedY, imageWidth, imageHeight)
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        faceDetectionProcessor.cleanup()
        autofocusController.destroy()
    }
}
