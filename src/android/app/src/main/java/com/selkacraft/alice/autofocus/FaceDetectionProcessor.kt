package com.selkacraft.alice.comm.autofocus

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
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
 * Processes frames for face detection using ML Kit.
 * Provides real-time face detection with tracking IDs and bounding boxes.
 */
class FaceDetectionProcessor(
    private val scope: CoroutineScope
) {
    private val tag = "FaceDetectionProcessor"

    // ML Kit Face Detector with accuracy optimized settings for stability
    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Use accurate mode for stability
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // Disabled for speed
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)   // Disabled for speed
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // Disabled for speed
        .setMinFaceSize(0.25f) // Minimum face size (25% of image) - larger for stability and fewer false positives
        .enableTracking() // Enable face tracking across frames
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(detectorOptions)

    // Face tracker for maintaining face identities and colors
    private val faceTracker = FaceTracker()

    // Smoothing for bounding boxes (exponential moving average)
    private val boundingBoxSmoothing = mutableMapOf<Int, Rect>()
    private val smoothingFactor = 0.15f // Lower = smoother, more stable (reduced from 0.3)

    // Face stability tracking - require faces to be detected for N frames before showing
    private val faceStabilityFrames = mutableMapOf<Int, Int>() // trackingId -> consecutive frames seen
    private val requiredStabilityFrames = 3 // Require 3 consecutive frames for new faces

    // Track last seen frame for each face to prevent ID flickering
    private val faceLastSeenFrame = mutableMapOf<Int, Long>()
    private val faceRetentionFrames = 5 // Keep face for 5 frames even if not detected

    // Processing state
    private val _faceDetectionState = MutableStateFlow(FaceDetectionState())
    val faceDetectionState: StateFlow<FaceDetectionState> = _faceDetectionState.asStateFlow()

    // Processing statistics
    private var frameCount = 0L
    private var totalProcessingTimeMs = 0L
    private var lastFrameTimestamp = 0L

    // Mutex to prevent concurrent processing
    private val processingMutex = Mutex()

    // Processing job
    private var processingJob: Job? = null

    /**
     * Process a bitmap frame for face detection.
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

                    // Create InputImage from bitmap
                    val inputImage = InputImage.fromBitmap(bitmap, 0)

                    // Detect faces using ML Kit
                    val faces = detector.process(inputImage).await()

                    // Process detected faces
                    val detectedFaces = processFaces(faces, bitmap.width, bitmap.height)

                    // Update statistics
                    val processingTime = System.currentTimeMillis() - startTime
                    updateStatistics(processingTime, detectedFaces.size)

                    // Calculate FPS
                    val currentTime = System.currentTimeMillis()
                    val fps = if (lastFrameTimestamp > 0) {
                        1000f / (currentTime - lastFrameTimestamp)
                    } else {
                        0f
                    }
                    lastFrameTimestamp = currentTime

                    // Update state
                    _faceDetectionState.value = _faceDetectionState.value.copy(
                        detectedFaces = detectedFaces,
                        isProcessing = false,
                        lastProcessedTimestamp = currentTime,
                        processingFps = fps
                    )

                    // Cleanup stale face tracking entries
                    faceTracker.cleanupStaleEntries()

                    Log.d(tag, "Processed frame: ${detectedFaces.size} faces, ${processingTime}ms, ${fps.toInt()} FPS")

                } catch (e: Exception) {
                    Log.e(tag, "Error processing frame", e)
                    _faceDetectionState.value = _faceDetectionState.value.copy(isProcessing = false)
                }
            }
        }
    }

    /**
     * Convert ML Kit faces to DetectedFace objects with tracking IDs and colors
     */
    private fun processFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int): List<DetectedFace> {
        // Track which faces are currently detected
        val currentFaceIds = faces.mapNotNull { it.trackingId }.toSet()

        // Update frame counter for currently detected faces
        currentFaceIds.forEach { id ->
            faceStabilityFrames[id] = (faceStabilityFrames[id] ?: 0) + 1
            faceLastSeenFrame[id] = frameCount
        }

        // Also check previously stable faces that might be temporarily occluded
        val previouslyStableFaces = faceStabilityFrames.filter { (id, frames) ->
            frames >= requiredStabilityFrames &&
            (frameCount - (faceLastSeenFrame[id] ?: 0)) < faceRetentionFrames
        }.keys

        // Remove stability data for faces that haven't been seen recently
        val allRelevantIds = currentFaceIds + previouslyStableFaces
        faceStabilityFrames.keys.retainAll(allRelevantIds)
        faceLastSeenFrame.keys.retainAll(allRelevantIds)
        boundingBoxSmoothing.keys.retainAll(allRelevantIds)

        // Only show faces that have been stable for required number of frames
        val stableFaceIds = faceStabilityFrames.filter { (_, frames) ->
            frames >= requiredStabilityFrames
        }.keys

        return faces.mapNotNull { face ->
            try {
                // Get tracking ID (ML Kit provides this when tracking is enabled)
                val trackingId = face.trackingId ?: return@mapNotNull null

                // Only show stable faces (detected for required consecutive frames)
                if (!stableFaceIds.contains(trackingId)) {
                    return@mapNotNull null
                }

                // Get raw bounding box from ML Kit
                val rawBoundingBox = face.boundingBox

                // Apply exponential moving average smoothing to bounding box
                val smoothedBoundingBox = boundingBoxSmoothing[trackingId]?.let { previousBox ->
                    Rect(
                        (previousBox.left * (1f - smoothingFactor) + rawBoundingBox.left * smoothingFactor).toInt(),
                        (previousBox.top * (1f - smoothingFactor) + rawBoundingBox.top * smoothingFactor).toInt(),
                        (previousBox.right * (1f - smoothingFactor) + rawBoundingBox.right * smoothingFactor).toInt(),
                        (previousBox.bottom * (1f - smoothingFactor) + rawBoundingBox.bottom * smoothingFactor).toInt()
                    )
                } ?: rawBoundingBox

                // Store smoothed box for next frame
                boundingBoxSmoothing[trackingId] = smoothedBoundingBox

                // Calculate normalized center point for autofocus using smoothed box
                val centerX = smoothedBoundingBox.centerX().toFloat() / imageWidth
                val centerY = smoothedBoundingBox.centerY().toFloat() / imageHeight
                val centerPoint = FocusPoint(centerX, centerY)

                // Get or assign color for this face
                val color = faceTracker.getColorForFace(trackingId)

                // Create DetectedFace object with smoothed bounding box
                DetectedFace(
                    trackingId = trackingId,
                    boundingBox = smoothedBoundingBox,
                    centerPoint = centerPoint,
                    confidence = 1.0f, // ML Kit doesn't provide confidence, so we use 1.0
                    color = color
                )
            } catch (e: Exception) {
                Log.e(tag, "Error processing face", e)
                null
            }
        }
    }

    /**
     * Update processing statistics
     */
    private fun updateStatistics(processingTimeMs: Long, faceCount: Int) {
        frameCount++
        totalProcessingTimeMs += processingTimeMs
    }

    /**
     * Select a face for autofocus by tapping on it
     */
    fun selectFaceAt(x: Float, y: Float, imageWidth: Int, imageHeight: Int) {
        val currentState = _faceDetectionState.value
        val tappedFace = currentState.detectedFaces.find { face ->
            face.contains(x, y, imageWidth, imageHeight)
        }

        if (tappedFace != null) {
            Log.d(tag, "Selected face: ${tappedFace.trackingId}")
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
     * Reset face tracking (clears all tracking data)
     */
    fun reset() {
        faceTracker.reset()
        _faceDetectionState.value = FaceDetectionState()
        frameCount = 0
        totalProcessingTimeMs = 0
        lastFrameTimestamp = 0
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
     * Cleanup resources
     */
    fun cleanup() {
        processingJob?.cancel()
        detector.close()
        faceTracker.reset()
    }
}
