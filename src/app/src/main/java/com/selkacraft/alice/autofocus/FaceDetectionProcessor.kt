package com.selkacraft.alice.comm.autofocus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Processes frames for face detection
 *
 * Architecture:
 * 1. Primary: ONNX YOLO-face detector for robust face detection at all distances
 * 2. Secondary: ML Kit for eye landmark refinement (when YOLO landmarks insufficient)
 * 3. SubjectTracker: Kalman filter-based tracking for smooth, predictive tracking
 *
 */
class FaceDetectionProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLogMessage: ((String, String) -> Unit)? = null
) {
    private val tag = "FaceDetectionProcessor"

    private fun log(message: String) {
        Log.d(tag, message)
        onLogMessage?.invoke("FACE_TRACKING", message)
    }

    // ONNX-based face detector (primary)
    private val onnxDetector = OnnxFaceDetector(context)
    private var onnxInitialized = false

    // ML Kit Face Detector (fallback and eye landmark refinement)
    private val mlKitOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // Enable landmarks for eyes
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)  // Lower threshold since ONNX is primary
        .enableTracking()
        .build()

    private val mlKitDetector: FaceDetector = FaceDetection.getClient(mlKitOptions)

    // Subject tracker with Kalman filter
    private val subjectTracker = SubjectTracker()

    // Processing state
    private val _faceDetectionState = MutableStateFlow(FaceDetectionState())
    val faceDetectionState: StateFlow<FaceDetectionState> = _faceDetectionState.asStateFlow()

    // Processing statistics
    private var frameCount = 0L
    private var totalProcessingTimeMs = 0L
    private var lastFrameTimestamp = 0L
    private var framesWithEyes = 0L

    // Mutex to prevent concurrent processing
    private val processingMutex = Mutex()

    // Processing job
    private var processingJob: Job? = null

    // Configuration
    private var useOnnxDetector = true  // Can be disabled for fallback
    private var refineEyesWithMlKit = true  // Use ML Kit for eye position refinement

    /**
     * Initialize the processor. Call before first use.
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            // Try to initialize ONNX detector
            if (onnxDetector.isModelAvailable()) {
                val result = onnxDetector.initialize()
                onnxInitialized = result.isSuccess
                if (onnxInitialized) {
                    Log.i(tag, "ONNX face detector initialized - using enhanced detection")
                } else {
                    Log.w(tag, "ONNX initialization failed, falling back to ML Kit")
                }
            } else {
                Log.w(tag, "ONNX model not found, using ML Kit fallback")
                onnxInitialized = false
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize face detection processor", e)
            Result.failure(e)
        }
    }

    /**
     * Check if ONNX detector is available and initialized
     */
    fun isOnnxAvailable(): Boolean = onnxInitialized

    /**
     * Process a bitmap frame for face detection with eye tracking.
     * Non-blocking - processes asynchronously and updates state flow.
     */
    fun processFrame(bitmap: Bitmap) {
        // Skip if already processing
        if (processingMutex.isLocked) {
            Log.d(tag, "Skipping frame - already processing")
            return
        }

        processingJob?.cancel()
        processingJob = scope.launch(Dispatchers.Default) {
            processingMutex.withLock {
                try {
                    _faceDetectionState.value = _faceDetectionState.value.copy(isProcessing = true)

                    val startTime = System.currentTimeMillis()
                    val imageWidth = bitmap.width
                    val imageHeight = bitmap.height

                    // Step 1: Detect faces
                    val rawDetections = if (onnxInitialized && useOnnxDetector) {
                        detectWithOnnx(bitmap)
                    } else {
                        detectWithMlKit(bitmap)
                    }

                    // Step 2: Refine eye positions with ML Kit if needed
                    val refinedDetections = if (refineEyesWithMlKit && onnxInitialized) {
                        refineEyePositionsWithMlKit(rawDetections, bitmap)
                    } else {
                        rawDetections
                    }

                    // Step 3: Track faces using Kalman filter
                    val trackedFaces = subjectTracker.update(
                        refinedDetections,
                        imageWidth,
                        imageHeight
                    )

                    // Update statistics
                    val processingTime = System.currentTimeMillis() - startTime
                    updateStatistics(processingTime, trackedFaces)

                    // Calculate FPS
                    val currentTime = System.currentTimeMillis()
                    val fps = if (lastFrameTimestamp > 0) {
                        1000f / (currentTime - lastFrameTimestamp)
                    } else {
                        0f
                    }
                    lastFrameTimestamp = currentTime

                    // Preserve selected face ID if still being tracked
                    val currentSelectedId = _faceDetectionState.value.selectedFaceId
                    val preservedSelectedId = if (currentSelectedId != null &&
                        trackedFaces.any { it.trackingId == currentSelectedId }
                    ) {
                        currentSelectedId
                    } else {
                        null
                    }

                    // Update state
                    _faceDetectionState.value = _faceDetectionState.value.copy(
                        detectedFaces = trackedFaces,
                        selectedFaceId = preservedSelectedId,
                        isProcessing = false,
                        lastProcessedTimestamp = currentTime,
                        processingFps = fps
                    )

                    Log.d(tag, "Processed frame: ${trackedFaces.size} faces, " +
                            "${trackedFaces.count { it.hasEyes }} with eyes, " +
                            "${processingTime}ms, ${fps.toInt()} FPS")

                } catch (e: Exception) {
                    Log.e(tag, "Error processing frame", e)
                    _faceDetectionState.value = _faceDetectionState.value.copy(isProcessing = false)
                }
            }
        }
    }

    /**
     * Detect faces using ONNX YOLO model
     */
    private suspend fun detectWithOnnx(bitmap: Bitmap): List<RawFaceDetection> {
        return onnxDetector.detect(bitmap)
    }

    /**
     * Detect faces using ML Kit (fallback)
     */
    private suspend fun detectWithMlKit(bitmap: Bitmap): List<RawFaceDetection> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = mlKitDetector.process(inputImage).await()

            faces.take(MAX_TRACKED_FACES).map { face ->
                val landmarks = mutableListOf<android.graphics.PointF>()

                // Extract eye landmarks
                face.getLandmark(FaceLandmark.LEFT_EYE)?.let { landmark ->
                    landmarks.add(landmark.position)
                }
                face.getLandmark(FaceLandmark.RIGHT_EYE)?.let { landmark ->
                    landmarks.add(landmark.position)
                }

                // Add nose and mouth if available (for consistency with YOLO output)
                face.getLandmark(FaceLandmark.NOSE_BASE)?.let { landmarks.add(it.position) }
                face.getLandmark(FaceLandmark.MOUTH_LEFT)?.let { landmarks.add(it.position) }
                face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.let { landmarks.add(it.position) }

                RawFaceDetection(
                    boundingBox = face.boundingBox,
                    confidence = 0.9f,  // ML Kit doesn't provide confidence
                    landmarks = if (landmarks.isNotEmpty()) landmarks else null
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "ML Kit detection failed", e)
            emptyList()
        }
    }

    /**
     * Refine eye positions using ML Kit landmarks.
     * YOLO provides good face detection but ML Kit may give more accurate eye positions.
     */
    private suspend fun refineEyePositionsWithMlKit(
        detections: List<RawFaceDetection>,
        bitmap: Bitmap
    ): List<RawFaceDetection> {
        if (detections.isEmpty()) return detections

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val mlKitFaces = mlKitDetector.process(inputImage).await()

            detections.map { detection ->
                // Find matching ML Kit face by IoU
                val matchingMlKitFace = findMatchingMlKitFace(detection.boundingBox, mlKitFaces)

                if (matchingMlKitFace != null) {
                    // Extract refined eye positions from ML Kit
                    val refinedLandmarks = mutableListOf<android.graphics.PointF>()

                    matchingMlKitFace.getLandmark(FaceLandmark.LEFT_EYE)?.let {
                        refinedLandmarks.add(it.position)
                    } ?: detection.landmarks?.getOrNull(0)?.let { refinedLandmarks.add(it) }

                    matchingMlKitFace.getLandmark(FaceLandmark.RIGHT_EYE)?.let {
                        refinedLandmarks.add(it.position)
                    } ?: detection.landmarks?.getOrNull(1)?.let { refinedLandmarks.add(it) }

                    // Keep other landmarks from YOLO
                    detection.landmarks?.getOrNull(2)?.let { refinedLandmarks.add(it) }
                    detection.landmarks?.getOrNull(3)?.let { refinedLandmarks.add(it) }
                    detection.landmarks?.getOrNull(4)?.let { refinedLandmarks.add(it) }

                    detection.copy(
                        landmarks = if (refinedLandmarks.isNotEmpty()) refinedLandmarks else detection.landmarks
                    )
                } else {
                    detection
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Eye refinement failed", e)
            detections
        }
    }

    /**
     * Find the ML Kit face that best matches a given bounding box
     */
    private fun findMatchingMlKitFace(targetBox: Rect, mlKitFaces: List<Face>): Face? {
        return mlKitFaces
            .map { face -> face to calculateIoU(targetBox, face.boundingBox) }
            .filter { (_, iou) -> iou > 0.3f }
            .maxByOrNull { (_, iou) -> iou }
            ?.first
    }

    /**
     * Calculate IoU between two rectangles
     */
    private fun calculateIoU(box1: Rect, box2: Rect): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)

        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea.toFloat() / unionArea else 0f
    }

    /**
     * Update processing statistics
     */
    private fun updateStatistics(processingTimeMs: Long, faces: List<DetectedFace>) {
        frameCount++
        totalProcessingTimeMs += processingTimeMs

        if (faces.any { it.hasEyes }) {
            framesWithEyes++
        }
    }

    /**
     * Select a face for autofocus by tapping on it
     */
    fun selectFaceAt(x: Float, y: Float, imageWidth: Int, imageHeight: Int) {
        val currentState = _faceDetectionState.value

        val tappedFace = currentState.detectedFaces.find { face ->
            face.contains(x, y, imageWidth, imageHeight)
        }

        if (tappedFace != null && tappedFace.trackingId != currentState.selectedFaceId) {
            log("Selected face ${tappedFace.trackingId}")
            _faceDetectionState.value = currentState.copy(
                selectedFaceId = tappedFace.trackingId
            )
        }
    }

    /**
     * Clear the selected face
     */
    fun clearSelectedFace() {
        _faceDetectionState.value = _faceDetectionState.value.copy(selectedFaceId = null)
    }

    /**
     * Set focus target preference (which eye to focus on)
     */
    fun setFocusTargetPreference(target: FocusTarget) {
        _faceDetectionState.value = _faceDetectionState.value.copy(
            focusTargetPreference = target
        )
    }

    /**
     * Reset face tracking (clears all tracking data)
     */
    fun reset() {
        subjectTracker.reset()
        _faceDetectionState.value = FaceDetectionState()
        frameCount = 0
        totalProcessingTimeMs = 0
        lastFrameTimestamp = 0
        framesWithEyes = 0
    }

    /**
     * Get average processing time in milliseconds
     */
    fun getAverageProcessingTime(): Float {
        return if (frameCount > 0) {
            totalProcessingTimeMs.toFloat() / frameCount
        } else {
            0f
        }
    }

    /**
     * Get eye detection rate (percentage of frames with eye detection)
     */
    fun getEyeDetectionRate(): Float {
        return if (frameCount > 0) {
            framesWithEyes.toFloat() / frameCount
        } else {
            0f
        }
    }

    /**
     * Get detection statistics
     */
    fun getStatistics(): FaceDetectionStatistics {
        return FaceDetectionStatistics(
            totalFramesProcessed = frameCount,
            totalFacesDetected = frameCount * _faceDetectionState.value.detectedFaces.size.toLong(),
            averageProcessingTimeMs = getAverageProcessingTime(),
            currentFps = _faceDetectionState.value.processingFps,
            maxFacesInFrame = MAX_TRACKED_FACES,
            eyeDetectionRate = getEyeDetectionRate()
        )
    }

    /**
     * Enable or disable ONNX detector (for testing/fallback)
     */
    fun setUseOnnxDetector(enabled: Boolean) {
        useOnnxDetector = enabled && onnxInitialized
    }

    /**
     * Enable or disable ML Kit eye refinement
     */
    fun setRefineEyesWithMlKit(enabled: Boolean) {
        refineEyesWithMlKit = enabled
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        processingJob?.cancel()
        mlKitDetector.close()
        onnxDetector.release()
        subjectTracker.reset()
    }
}

/**
 * Legacy constructor for backward compatibility.
 * New code should use the constructor with Context.
 */
@Deprecated("Use constructor with Context for ONNX support")
fun FaceDetectionProcessor(scope: CoroutineScope): FaceDetectionProcessor {
    throw IllegalStateException(
        "FaceDetectionProcessor now requires Context for ONNX support. " +
        "Use FaceDetectionProcessor(context, scope) instead."
    )
}
