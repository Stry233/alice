package com.selkacraft.alice.comm.autofocus

import android.graphics.Rect
import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Tracks subjects (faces) across frames with Kalman filter-based prediction.
 * Provides smooth, stable tracking even during brief occlusions.
 *
 * Key features:
 * - Per-subject 2D Kalman filter for position/velocity estimation
 * - Predictive tracking during occlusion (up to PREDICTION_HOLD_FRAMES)
 * - Automatic ID persistence with IoU-based matching
 * - Face scoring for priority selection
 * - Color assignment for visualization
 */
class SubjectTracker {
    companion object {
        private const val TAG = "SubjectTracker"

        // Tracking parameters
        private const val IOU_MATCH_THRESHOLD = 0.3f  // Minimum IoU to consider a match
        private const val MAX_DISTANCE_THRESHOLD = 0.25f  // Max normalized distance for fallback matching
        private const val PREDICTION_HOLD_FRAMES = 5  // Hold prediction for ~0.1s at 30fps
        private const val STABILITY_FRAMES_REQUIRED = 2  // Frames before showing new subject

        // Scoring weights
        private const val SCORE_WEIGHT_SIZE = 0.3f
        private const val SCORE_WEIGHT_CENTER = 0.25f
        private const val SCORE_WEIGHT_EYES = 0.25f
        private const val SCORE_WEIGHT_STABILITY = 0.2f

        // Kalman filter parameters
        private const val PROCESS_NOISE = 0.01f  // How much we expect position to change
        private const val MEASUREMENT_NOISE = 0.05f  // How noisy our measurements are
    }

    // Color palette for tracked subjects
    private val colorPalette = listOf(
        Color(0xFF00FF00), // Green
        Color(0xFFFF00FF), // Magenta
        Color(0xFF00FFFF), // Cyan
        Color(0xFFFFFF00), // Yellow
        Color(0xFFFF8800), // Orange
        Color(0xFF8800FF), // Purple
        Color(0xFF0088FF), // Light Blue
        Color(0xFFFF0088), // Pink
    )

    // Active tracked subjects
    private val trackedSubjects = mutableMapOf<Int, TrackedSubject>()
    private var nextTrackingId = 1

    /**
     * Internal tracking state for a subject
     */
    private data class TrackedSubject(
        val trackingId: Int,
        val color: Color,
        val kalmanFilter: KalmanFilter2D,
        var lastBoundingBox: Rect,
        var lastLeftEye: EyePosition?,
        var lastRightEye: EyePosition?,
        var lastConfidence: Float,
        var framesSinceDetection: Int = 0,
        var stabilityFrames: Int = 0,
        var totalFramesTracked: Int = 0,
        var lastUpdateTime: Long = System.currentTimeMillis()
    )

    /**
     * 2D Kalman filter for tracking position with velocity estimation
     */
    private class KalmanFilter2D {
        // State: [x, y, vx, vy] (position and velocity)
        private var x = floatArrayOf(0f, 0f, 0f, 0f)

        // State covariance
        private var P = Array(4) { i -> FloatArray(4) { j -> if (i == j) 1f else 0f } }

        // Process noise
        private val Q = Array(4) { i -> FloatArray(4) { j ->
            when {
                i == j && i < 2 -> PROCESS_NOISE  // Position noise
                i == j -> PROCESS_NOISE * 2  // Velocity noise (higher)
                else -> 0f
            }
        }}

        // Measurement noise
        private val R = Array(2) { i -> FloatArray(2) { j -> if (i == j) MEASUREMENT_NOISE else 0f } }

        // State transition matrix (constant velocity model)
        private val dt = 1f / 30f  // Assume 30 fps
        private val F = arrayOf(
            floatArrayOf(1f, 0f, dt, 0f),
            floatArrayOf(0f, 1f, 0f, dt),
            floatArrayOf(0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, 0f, 1f)
        )

        // Measurement matrix (we only observe position)
        private val H = arrayOf(
            floatArrayOf(1f, 0f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f, 0f)
        )

        fun initialize(x: Float, y: Float) {
            this.x = floatArrayOf(x, y, 0f, 0f)
            // Reset covariance to high uncertainty
            P = Array(4) { i -> FloatArray(4) { j -> if (i == j) 1f else 0f } }
        }

        fun predict(): Pair<Float, Float> {
            // Predict state: x = F * x
            val newX = FloatArray(4)
            for (i in 0..3) {
                for (j in 0..3) {
                    newX[i] += F[i][j] * x[j]
                }
            }
            x = newX

            // Predict covariance: P = F * P * F' + Q
            val FP = Array(4) { FloatArray(4) }
            for (i in 0..3) {
                for (j in 0..3) {
                    for (k in 0..3) {
                        FP[i][j] += F[i][k] * P[k][j]
                    }
                }
            }
            val newP = Array(4) { FloatArray(4) }
            for (i in 0..3) {
                for (j in 0..3) {
                    for (k in 0..3) {
                        newP[i][j] += FP[i][k] * F[j][k]
                    }
                    newP[i][j] += Q[i][j]
                }
            }
            P = newP

            return Pair(x[0], x[1])
        }

        fun update(measuredX: Float, measuredY: Float): Pair<Float, Float> {
            val z = floatArrayOf(measuredX, measuredY)

            // Innovation: y = z - H * x
            val y = FloatArray(2)
            for (i in 0..1) {
                y[i] = z[i]
                for (j in 0..3) {
                    y[i] -= H[i][j] * x[j]
                }
            }

            // Innovation covariance: S = H * P * H' + R
            val HP = Array(2) { FloatArray(4) }
            for (i in 0..1) {
                for (j in 0..3) {
                    for (k in 0..3) {
                        HP[i][j] += H[i][k] * P[k][j]
                    }
                }
            }
            val S = Array(2) { FloatArray(2) }
            for (i in 0..1) {
                for (j in 0..1) {
                    for (k in 0..3) {
                        S[i][j] += HP[i][k] * H[j][k]
                    }
                    S[i][j] += R[i][j]
                }
            }

            // Kalman gain: K = P * H' * S^-1
            val PHt = Array(4) { FloatArray(2) }
            for (i in 0..3) {
                for (j in 0..1) {
                    for (k in 0..3) {
                        PHt[i][j] += P[i][k] * H[j][k]
                    }
                }
            }

            // Invert 2x2 matrix S
            val det = S[0][0] * S[1][1] - S[0][1] * S[1][0]
            if (kotlin.math.abs(det) < 1e-6f) {
                // Singular matrix, skip update
                return Pair(x[0], x[1])
            }
            val Sinv = arrayOf(
                floatArrayOf(S[1][1] / det, -S[0][1] / det),
                floatArrayOf(-S[1][0] / det, S[0][0] / det)
            )

            val K = Array(4) { FloatArray(2) }
            for (i in 0..3) {
                for (j in 0..1) {
                    for (k in 0..1) {
                        K[i][j] += PHt[i][k] * Sinv[k][j]
                    }
                }
            }

            // Update state: x = x + K * y
            for (i in 0..3) {
                for (j in 0..1) {
                    x[i] += K[i][j] * y[j]
                }
            }

            // Update covariance: P = (I - K * H) * P
            val KH = Array(4) { FloatArray(4) }
            for (i in 0..3) {
                for (j in 0..3) {
                    for (k in 0..1) {
                        KH[i][j] += K[i][k] * H[k][j]
                    }
                }
            }
            val IminusKH = Array(4) { i -> FloatArray(4) { j ->
                (if (i == j) 1f else 0f) - KH[i][j]
            }}
            val newP = Array(4) { FloatArray(4) }
            for (i in 0..3) {
                for (j in 0..3) {
                    for (k in 0..3) {
                        newP[i][j] += IminusKH[i][k] * P[k][j]
                    }
                }
            }
            P = newP

            return Pair(x[0], x[1])
        }

        fun getPosition(): Pair<Float, Float> = Pair(x[0], x[1])

        fun getVelocity(): Pair<Float, Float> = Pair(x[2], x[3])
    }

    /**
     * Update tracking with new detections.
     * Matches detections to existing tracks, creates new tracks, and removes stale ones.
     *
     * @param detections Raw face detections from ONNX model
     * @param imageWidth Width of the source image
     * @param imageHeight Height of the source image
     * @return List of tracked DetectedFace objects with persistent IDs
     */
    fun update(
        detections: List<RawFaceDetection>,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectedFace> {
        val currentTime = System.currentTimeMillis()

        // Step 1: Match detections to existing tracks using IoU
        val matches = matchDetectionsToTracks(detections, imageWidth, imageHeight)

        // Step 2: Update matched tracks
        for ((detection, trackingId) in matches) {
            val subject = trackedSubjects[trackingId] ?: continue
            updateTrackedSubject(subject, detection, imageWidth, imageHeight)
        }

        // Step 3: Create new tracks for unmatched detections
        val matchedDetectionIndices = matches.map { detections.indexOf(it.first) }.toSet()
        for ((index, detection) in detections.withIndex()) {
            if (index !in matchedDetectionIndices && trackedSubjects.size < MAX_TRACKED_FACES) {
                createNewTrack(detection, imageWidth, imageHeight)
            }
        }

        // Step 4: Update unmatched tracks (increment frames since detection)
        val matchedTrackIds = matches.map { it.second }.toSet()
        for ((trackingId, subject) in trackedSubjects) {
            if (trackingId !in matchedTrackIds) {
                subject.framesSinceDetection++
                // Predict position using Kalman filter
                subject.kalmanFilter.predict()
            }
        }

        // Step 5: Remove stale tracks
        val staleIds = trackedSubjects.filter { (_, subject) ->
            subject.framesSinceDetection > PREDICTION_HOLD_FRAMES
        }.keys
        staleIds.forEach { trackedSubjects.remove(it) }

        // Step 6: Build output list
        return buildOutputList(imageWidth, imageHeight)
    }

    /**
     * Match detections to existing tracks using IoU and distance
     */
    private fun matchDetectionsToTracks(
        detections: List<RawFaceDetection>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Pair<RawFaceDetection, Int>> {
        if (detections.isEmpty() || trackedSubjects.isEmpty()) {
            return emptyList()
        }

        val matches = mutableListOf<Pair<RawFaceDetection, Int>>()
        val usedDetections = mutableSetOf<Int>()
        val usedTracks = mutableSetOf<Int>()

        // Calculate IoU matrix
        val iouMatrix = mutableMapOf<Pair<Int, Int>, Float>()
        for ((detIdx, detection) in detections.withIndex()) {
            for ((trackId, subject) in trackedSubjects) {
                val iou = calculateIoU(detection.boundingBox, subject.lastBoundingBox)
                if (iou > IOU_MATCH_THRESHOLD) {
                    iouMatrix[Pair(detIdx, trackId)] = iou
                }
            }
        }

        // Greedy matching: pick best IoU matches first
        val sortedMatches = iouMatrix.entries.sortedByDescending { it.value }
        for ((pair, _) in sortedMatches) {
            val (detIdx, trackId) = pair
            if (detIdx !in usedDetections && trackId !in usedTracks) {
                matches.add(detections[detIdx] to trackId)
                usedDetections.add(detIdx)
                usedTracks.add(trackId)
            }
        }

        // Fallback: distance-based matching for unmatched
        for ((detIdx, detection) in detections.withIndex()) {
            if (detIdx in usedDetections) continue

            val detCenterX = detection.boundingBox.centerX().toFloat() / imageWidth
            val detCenterY = detection.boundingBox.centerY().toFloat() / imageHeight

            var bestMatch: Int? = null
            var bestDistance = MAX_DISTANCE_THRESHOLD

            for ((trackId, subject) in trackedSubjects) {
                if (trackId in usedTracks) continue

                val (predX, predY) = subject.kalmanFilter.getPosition()
                val distance = sqrt(
                    (detCenterX - predX) * (detCenterX - predX) +
                    (detCenterY - predY) * (detCenterY - predY)
                )

                if (distance < bestDistance) {
                    bestDistance = distance
                    bestMatch = trackId
                }
            }

            if (bestMatch != null) {
                matches.add(detection to bestMatch)
                usedDetections.add(detIdx)
                usedTracks.add(bestMatch)
            }
        }

        return matches
    }

    /**
     * Update a tracked subject with a new detection
     */
    private fun updateTrackedSubject(
        subject: TrackedSubject,
        detection: RawFaceDetection,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val centerX = detection.boundingBox.centerX().toFloat() / imageWidth
        val centerY = detection.boundingBox.centerY().toFloat() / imageHeight

        // Update Kalman filter
        subject.kalmanFilter.update(centerX, centerY)

        // Update subject state
        subject.lastBoundingBox = detection.boundingBox
        subject.lastConfidence = detection.confidence
        subject.framesSinceDetection = 0
        subject.stabilityFrames++
        subject.totalFramesTracked++
        subject.lastUpdateTime = System.currentTimeMillis()

        // Update eye positions from landmarks
        detection.landmarks?.let { landmarks ->
            if (landmarks.size >= 2) {
                subject.lastLeftEye = EyePosition.fromPixels(
                    landmarks[0].x, landmarks[0].y, imageWidth, imageHeight
                )
                subject.lastRightEye = EyePosition.fromPixels(
                    landmarks[1].x, landmarks[1].y, imageWidth, imageHeight
                )
            }
        }
    }

    /**
     * Create a new track for an unmatched detection
     */
    private fun createNewTrack(
        detection: RawFaceDetection,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val trackingId = nextTrackingId++
        val color = colorPalette[(trackingId - 1) % colorPalette.size]

        val centerX = detection.boundingBox.centerX().toFloat() / imageWidth
        val centerY = detection.boundingBox.centerY().toFloat() / imageHeight

        val kalmanFilter = KalmanFilter2D()
        kalmanFilter.initialize(centerX, centerY)

        var leftEye: EyePosition? = null
        var rightEye: EyePosition? = null
        detection.landmarks?.let { landmarks ->
            if (landmarks.size >= 2) {
                leftEye = EyePosition.fromPixels(landmarks[0].x, landmarks[0].y, imageWidth, imageHeight)
                rightEye = EyePosition.fromPixels(landmarks[1].x, landmarks[1].y, imageWidth, imageHeight)
            }
        }

        val subject = TrackedSubject(
            trackingId = trackingId,
            color = color,
            kalmanFilter = kalmanFilter,
            lastBoundingBox = detection.boundingBox,
            lastLeftEye = leftEye,
            lastRightEye = rightEye,
            lastConfidence = detection.confidence
        )

        trackedSubjects[trackingId] = subject
        Log.d(TAG, "Created new track: $trackingId")
    }

    /**
     * Build the output list of DetectedFace objects
     */
    private fun buildOutputList(imageWidth: Int, imageHeight: Int): List<DetectedFace> {
        return trackedSubjects.values
            .filter { it.stabilityFrames >= STABILITY_FRAMES_REQUIRED }
            .map { subject ->
                val (filteredX, filteredY) = subject.kalmanFilter.getPosition()

                // Determine tracking state
                val trackingState = when {
                    subject.framesSinceDetection == 0 && (subject.lastLeftEye != null || subject.lastRightEye != null) ->
                        TrackingState.EYE_LOCKED
                    subject.framesSinceDetection == 0 ->
                        TrackingState.FACE_ONLY
                    subject.framesSinceDetection <= PREDICTION_HOLD_FRAMES ->
                        TrackingState.PREDICTED
                    else ->
                        TrackingState.LOST
                }

                // Calculate score
                val score = calculateScore(subject, imageWidth, imageHeight)

                // Calculate focus point
                val focusPoint = when {
                    subject.lastLeftEye != null || subject.lastRightEye != null -> {
                        // Use nearest eye
                        val nearestEye = getNearestEye(subject.lastLeftEye, subject.lastRightEye, filteredX)
                        nearestEye?.let { FocusPoint(it.x, it.y) }
                            ?: FocusPoint(filteredX, filteredY)
                    }
                    else -> FocusPoint(filteredX, filteredY)
                }

                DetectedFace(
                    trackingId = subject.trackingId,
                    boundingBox = subject.lastBoundingBox,
                    centerPoint = FocusPoint(filteredX, filteredY),
                    confidence = subject.lastConfidence,
                    color = subject.color,
                    leftEye = subject.lastLeftEye,
                    rightEye = subject.lastRightEye,
                    trackingState = trackingState,
                    score = score,
                    focusPoint = focusPoint
                )
            }
            .sortedByDescending { it.score }
            .take(MAX_TRACKED_FACES)
    }

    /**
     * Calculate priority score for a subject
     */
    private fun calculateScore(subject: TrackedSubject, imageWidth: Int, imageHeight: Int): Float {
        // Size score: larger faces get higher priority
        val faceArea = subject.lastBoundingBox.width() * subject.lastBoundingBox.height()
        val imageArea = imageWidth * imageHeight
        val sizeScore = (faceArea.toFloat() / imageArea).coerceIn(0f, 1f) * 4  // Scale up

        // Center score: faces closer to center get higher priority
        val centerX = subject.lastBoundingBox.centerX().toFloat() / imageWidth
        val centerY = subject.lastBoundingBox.centerY().toFloat() / imageHeight
        val distFromCenter = sqrt((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f))
        val centerScore = 1f - (distFromCenter * 2).coerceIn(0f, 1f)

        // Eye score: faces with visible eyes get higher priority
        val eyeScore = when {
            subject.lastLeftEye != null && subject.lastRightEye != null -> 1f
            subject.lastLeftEye != null || subject.lastRightEye != null -> 0.7f
            else -> 0.3f
        }

        // Stability score: longer-tracked faces get higher priority
        val stabilityScore = (subject.totalFramesTracked.toFloat() / 100f).coerceIn(0f, 1f)

        return (
            sizeScore * SCORE_WEIGHT_SIZE +
            centerScore * SCORE_WEIGHT_CENTER +
            eyeScore * SCORE_WEIGHT_EYES +
            stabilityScore * SCORE_WEIGHT_STABILITY
        )
    }

    /**
     * Get the nearest eye (more centered in the frame)
     */
    private fun getNearestEye(leftEye: EyePosition?, rightEye: EyePosition?, faceX: Float): EyePosition? {
        if (leftEye == null && rightEye == null) return null
        if (leftEye == null) return rightEye
        if (rightEye == null) return leftEye

        // Return the eye closer to the face center X
        val leftDist = kotlin.math.abs(leftEye.x - faceX)
        val rightDist = kotlin.math.abs(rightEye.x - faceX)
        return if (leftDist <= rightDist) leftEye else rightEye
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
     * Reset all tracking
     */
    fun reset() {
        trackedSubjects.clear()
        nextTrackingId = 1
        Log.d(TAG, "Tracking reset")
    }

    /**
     * Get number of currently tracked subjects
     */
    fun getTrackedCount(): Int = trackedSubjects.size

    /**
     * Check if a specific subject is still being tracked
     */
    fun isTracking(trackingId: Int): Boolean = trackedSubjects.containsKey(trackingId)
}
