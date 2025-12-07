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
 */
class AutofocusCoordinator(
    private val context: Context,
    private val autofocusController: AutofocusController,
    private val motorManager: MotorControlManager,
    private val realSenseManager: RealSenseManager,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
    private val onMotorPositionCommand: (Int) -> Unit,
    private val onLogMessage: (String, String) -> Unit  // (category, message)
) {
    // Face detection processor for FACE_TRACKING mode
    private val faceDetectionProcessor = FaceDetectionProcessor(context, scope, onLogMessage)

    // Expose autofocus state
    val state: StateFlow<AutofocusState> = autofocusController.state
    val events: SharedFlow<AutofocusEvent> = autofocusController.events

    // Expose face detection state for UI
    val faceDetectionState: StateFlow<FaceDetectionState> = faceDetectionProcessor.faceDetectionState

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
        scope.launch {
            combine(
                motorManager.connectionState,
                realSenseManager.connectionState
            ) { motorState, realSenseState ->
                val motorReady = motorState is ConnectionState.Connected ||
                        motorState is ConnectionState.Active
                val realSenseReady = realSenseState is ConnectionState.Connected ||
                        realSenseState is ConnectionState.Active
                Pair(motorReady, realSenseReady)
            }.collect { (motorReady, realSenseReady) ->
                // Detect device reconnection and reset to MANUAL mode for safety
                // This ensures autofocus doesn't unexpectedly activate after device reconnection
                val motorReconnected = motorReady && !wasMotorReady
                val realSenseReconnected = realSenseReady && !wasRealSenseReady

                if (motorReconnected) {
                    resetFocusModeToManual("Motor dongle reconnected")
                }
                if (realSenseReconnected) {
                    resetFocusModeToManual("RealSense camera reconnected")
                }

                // Update tracking state
                wasMotorReady = motorReady
                wasRealSenseReady = realSenseReady

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
                autofocusController.setEnabled(settings.enabled)
                autofocusController.setFocusMode(settings.mode)
                autofocusController.updateConfiguration(
                    confidenceThreshold = settings.confidence,
                    enableSmoothing = settings.smoothing,
                    responseSpeed = settings.speed
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
                if (autofocusController.state.value.mode == FocusMode.FACE_TRACKING) {
                    val targetFace = faceState.defaultFocusTarget
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
                    }
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
