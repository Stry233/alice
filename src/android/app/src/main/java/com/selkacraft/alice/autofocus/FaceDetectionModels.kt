package com.selkacraft.alice.comm.autofocus

import android.graphics.Rect
import android.graphics.PointF
import androidx.compose.ui.graphics.Color

/**
 * Maximum number of faces to track simultaneously.
 * Limiting this improves performance and user experience.
 */
const val MAX_TRACKED_FACES = 4

/**
 * Represents an eye position with normalized coordinates
 */
data class EyePosition(
    val x: Float,  // Normalized 0-1
    val y: Float,  // Normalized 0-1
    val confidence: Float = 1.0f  // How confident we are in this eye detection
) {
    /**
     * Convert to pixel coordinates
     */
    fun toPixels(imageWidth: Int, imageHeight: Int): PointF {
        return PointF(x * imageWidth, y * imageHeight)
    }

    /**
     * Distance from another point (in normalized coordinates)
     */
    fun distanceTo(other: EyePosition): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        fun fromPixels(px: Float, py: Float, imageWidth: Int, imageHeight: Int): EyePosition {
            return EyePosition(
                x = px / imageWidth,
                y = py / imageHeight
            )
        }
    }
}

/**
 * Tracking state for a subject - indicates what we're currently tracking
 */
enum class TrackingState {
    /** Eye is clearly detected and locked - best quality focus */
    EYE_LOCKED,
    /** Face detected but eyes not visible (turned away, too far, etc.) - use face center */
    FACE_ONLY,
    /** Subject temporarily lost, using predicted position from Kalman filter */
    PREDICTED,
    /** Subject lost and prediction expired */
    LOST
}

/**
 * What part of the face we're focusing on
 */
enum class FocusTarget {
    LEFT_EYE,
    RIGHT_EYE,
    NEAREST_EYE,  // Automatically select the eye nearest to camera (larger in frame)
    FACE_CENTER   // Fallback when eyes not visible
}

/**
 * Represents a detected face with comprehensive tracking information
 * including eye positions for precise autofocus.
 */
data class DetectedFace(
    val trackingId: Int,           // Unique ID for this face across frames
    val boundingBox: Rect,         // Face bounding box in image coordinates
    val centerPoint: FocusPoint,   // Normalized face center point (0-1)
    val confidence: Float,         // Detection confidence (0-1)
    val color: Color,              // Assigned color for visualization
    val timestamp: Long = System.currentTimeMillis(),

    // Eye tracking additions
    val leftEye: EyePosition? = null,   // Left eye position (from subject's perspective)
    val rightEye: EyePosition? = null,  // Right eye position (from subject's perspective)
    val trackingState: TrackingState = TrackingState.FACE_ONLY,
    val score: Float = 0f,              // Priority score for auto-selection (higher = better)

    // The actual point we should focus on (eye or face center)
    val focusPoint: FocusPoint = centerPoint
) {
    /**
     * Check if this face has visible eyes
     */
    val hasEyes: Boolean
        get() = leftEye != null || rightEye != null

    /**
     * Check if both eyes are visible
     */
    val hasBothEyes: Boolean
        get() = leftEye != null && rightEye != null

    /**
     * Get the preferred eye for autofocus.
     * Prioritizes left eye (from subject's perspective), falling back to right eye.
     */
    val nearestEye: EyePosition?
        get() {
            // Prioritize left eye, fallback to right eye
            return leftEye ?: rightEye
        }

    /**
     * Get focus point based on target preference
     */
    fun getFocusPointFor(target: FocusTarget): FocusPoint {
        return when (target) {
            FocusTarget.LEFT_EYE -> leftEye?.let { FocusPoint(it.x, it.y) } ?: centerPoint
            FocusTarget.RIGHT_EYE -> rightEye?.let { FocusPoint(it.x, it.y) } ?: centerPoint
            FocusTarget.NEAREST_EYE -> nearestEye?.let { FocusPoint(it.x, it.y) } ?: centerPoint
            FocusTarget.FACE_CENTER -> centerPoint
        }
    }

    /**
     * Check if this face contains a given point in normalized coordinates.
     * Includes padding around the bounding box for easier tapping.
     */
    fun contains(x: Float, y: Float, imageWidth: Int, imageHeight: Int): Boolean {
        val pixelX = (x * imageWidth).toInt()
        val pixelY = (y * imageHeight).toInt()

        // Add 20% padding around the bounding box for easier tapping
        val paddingX = (boundingBox.width() * 0.2f).toInt()
        val paddingY = (boundingBox.height() * 0.2f).toInt()

        val expandedBox = Rect(
            boundingBox.left - paddingX,
            boundingBox.top - paddingY,
            boundingBox.right + paddingX,
            boundingBox.bottom + paddingY
        )

        return expandedBox.contains(pixelX, pixelY)
    }

    /**
     * Get normalized bounding box coordinates (0-1)
     */
    fun getNormalizedBounds(imageWidth: Int, imageHeight: Int): NormalizedRect {
        return NormalizedRect(
            left = boundingBox.left.toFloat() / imageWidth,
            top = boundingBox.top.toFloat() / imageHeight,
            right = boundingBox.right.toFloat() / imageWidth,
            bottom = boundingBox.bottom.toFloat() / imageHeight
        )
    }

    /**
     * Get the size of this face relative to the image (0-1)
     */
    fun getRelativeSize(imageWidth: Int, imageHeight: Int): Float {
        val faceArea = boundingBox.width() * boundingBox.height()
        val imageArea = imageWidth * imageHeight
        return faceArea.toFloat() / imageArea.toFloat()
    }
}

/**
 * Normalized rectangle for UI rendering (0-1 coordinates)
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Face detection state for the autofocus system
 */
data class FaceDetectionState(
    val detectedFaces: List<DetectedFace> = emptyList(),
    val selectedFaceId: Int? = null,
    val isProcessing: Boolean = false,
    val lastProcessedTimestamp: Long = 0,
    val processingFps: Float = 0f,
    val focusTargetPreference: FocusTarget = FocusTarget.NEAREST_EYE
) {
    /**
     * Get the currently selected face
     */
    val selectedFace: DetectedFace?
        get() = detectedFaces.find { it.trackingId == selectedFaceId }

    /**
     * Get the default focus target.
     * Priority: selected face > highest scored face
     */
    val defaultFocusTarget: DetectedFace?
        get() = selectedFace ?: detectedFaces.maxByOrNull { it.score }

    /**
     * Get the focus point we should use for autofocus
     */
    val activeFocusPoint: FocusPoint?
        get() = defaultFocusTarget?.getFocusPointFor(focusTargetPreference)

    /**
     * Check if face detection has any faces
     */
    val hasFaces: Boolean
        get() = detectedFaces.isNotEmpty()

    /**
     * Check if we have an eye-locked focus (best quality)
     */
    val hasEyeLock: Boolean
        get() = defaultFocusTarget?.trackingState == TrackingState.EYE_LOCKED

    /**
     * Get current tracking quality description
     */
    val trackingQuality: String
        get() = when (defaultFocusTarget?.trackingState) {
            TrackingState.EYE_LOCKED -> "Eye AF"
            TrackingState.FACE_ONLY -> "Face AF"
            TrackingState.PREDICTED -> "Tracking..."
            TrackingState.LOST -> "Searching..."
            null -> "No subject"
        }
}

/**
 * Statistics for face detection performance
 */
data class FaceDetectionStatistics(
    val totalFramesProcessed: Long = 0,
    val totalFacesDetected: Long = 0,
    val averageProcessingTimeMs: Float = 0f,
    val currentFps: Float = 0f,
    val maxFacesInFrame: Int = 0,
    val eyeDetectionRate: Float = 0f  // Percentage of frames with eye detection
)

/**
 * Raw detection from ONNX model before tracking/filtering
 */
data class RawFaceDetection(
    val boundingBox: Rect,
    val confidence: Float,
    val landmarks: List<PointF>? = null  // Optional 5-point landmarks from YOLO
)
