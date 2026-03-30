package com.selkacraft.alice.comm.autofocus

import android.graphics.Rect
import androidx.compose.ui.graphics.Color

/**
 * Represents a detected face with tracking information
 */
data class DetectedFace(
    val trackingId: Int,           // Unique ID for this face across frames
    val boundingBox: Rect,         // Face bounding box in image coordinates
    val centerPoint: FocusPoint,   // Normalized center point (0-1)
    val confidence: Float,         // Detection confidence (0-1)
    val color: Color,              // Assigned color for visualization
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if this face contains a given point in normalized coordinates
     */
    fun contains(x: Float, y: Float, imageWidth: Int, imageHeight: Int): Boolean {
        val pixelX = (x * imageWidth).toInt()
        val pixelY = (y * imageHeight).toInt()
        return boundingBox.contains(pixelX, pixelY)
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
    val processingFps: Float = 0f
) {
    /**
     * Get the currently selected face
     */
    val selectedFace: DetectedFace?
        get() = detectedFaces.find { it.trackingId == selectedFaceId }

    /**
     * Get the default focus target (selected face, or first face if none selected)
     */
    val defaultFocusTarget: DetectedFace?
        get() = selectedFace ?: detectedFaces.firstOrNull()

    /**
     * Check if face detection has any faces
     */
    val hasFaces: Boolean
        get() = detectedFaces.isNotEmpty()
}

/**
 * Statistics for face detection performance
 */
data class FaceDetectionStatistics(
    val totalFramesProcessed: Long = 0,
    val totalFacesDetected: Long = 0,
    val averageProcessingTimeMs: Float = 0f,
    val currentFps: Float = 0f,
    val maxFacesInFrame: Int = 0
)
