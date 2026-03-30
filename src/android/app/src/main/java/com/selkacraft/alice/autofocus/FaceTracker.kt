package com.selkacraft.alice.comm.autofocus

import androidx.compose.ui.graphics.Color
import android.graphics.Rect
import kotlin.math.sqrt

/**
 * Legacy face tracker - assigns persistent colors to each face.
 *
 * @deprecated This class is superseded by [SubjectTracker] which provides:
 * - Kalman filter-based position tracking and prediction
 * - Better occlusion handling with position prediction
 * - Face scoring for priority selection
 * - Eye position tracking integration
 *
 * Use [SubjectTracker] for new code. This class is kept for backward compatibility.
 */
@Deprecated(
    message = "Use SubjectTracker instead for enhanced tracking with Kalman filter and eye support",
    replaceWith = ReplaceWith("SubjectTracker")
)
class FaceTracker {

    // Color palette for face bounding boxes (distinct colors for easy differentiation)
    private val colorPalette = listOf(
        Color(0xFF00FF00), // Green
        Color(0xFFFF00FF), // Magenta
        Color(0xFF00FFFF), // Cyan
        Color(0xFFFFFF00), // Yellow
        Color(0xFFFF8800), // Orange
        Color(0xFF8800FF), // Purple
        Color(0xFF0088FF), // Light Blue
        Color(0xFFFF0088), // Pink
        Color(0xFF88FF00), // Lime
        Color(0xFF0088FF)  // Sky Blue
    )

    // Map of tracking ID to assigned color
    private val faceColors = mutableMapOf<Int, Color>()

    // Track last seen timestamp for each face ID (for timeout/cleanup)
    private val lastSeenTimestamp = mutableMapOf<Int, Long>()

    // Timeout for face tracking (ms) - if not seen for this long, remove from tracking
    private val faceTrackingTimeout = 5000L // 5 seconds

    // Next color index to assign
    private var nextColorIndex = 0

    /**
     * Get or assign a color for a face tracking ID
     */
    fun getColorForFace(trackingId: Int): Color {
        // Update last seen timestamp
        lastSeenTimestamp[trackingId] = System.currentTimeMillis()

        // Return existing color or assign new one
        return faceColors.getOrPut(trackingId) {
            val color = colorPalette[nextColorIndex % colorPalette.size]
            nextColorIndex++
            color
        }
    }

    /**
     * Clean up faces that haven't been seen recently
     */
    fun cleanupStaleEntries() {
        val currentTime = System.currentTimeMillis()
        val staleIds = lastSeenTimestamp.filter { (_, timestamp) ->
            currentTime - timestamp > faceTrackingTimeout
        }.keys

        staleIds.forEach { id ->
            faceColors.remove(id)
            lastSeenTimestamp.remove(id)
        }
    }

    /**
     * Reset all tracking (clear all face IDs and colors)
     */
    fun reset() {
        faceColors.clear()
        lastSeenTimestamp.clear()
        nextColorIndex = 0
    }

    /**
     * Get number of currently tracked faces
     */
    fun getTrackedFaceCount(): Int = faceColors.size

    /**
     * Fallback: Find closest matching face by position when ML Kit tracking ID is unavailable
     * This is used when ML Kit doesn't provide a tracking ID or loses tracking
     */
    fun findClosestMatchingFace(
        newFace: Rect,
        previousFaces: List<DetectedFace>
    ): DetectedFace? {
        if (previousFaces.isEmpty()) return null

        val newCenter = Pair(
            newFace.centerX().toFloat(),
            newFace.centerY().toFloat()
        )

        // Find face with minimum distance to new face center
        return previousFaces.minByOrNull { face ->
            val prevCenter = Pair(
                face.boundingBox.centerX().toFloat(),
                face.boundingBox.centerY().toFloat()
            )
            euclideanDistance(newCenter, prevCenter)
        }?.let { closestFace ->
            // Only consider it a match if distance is reasonable (within 100 pixels)
            val distance = euclideanDistance(
                newCenter,
                Pair(
                    closestFace.boundingBox.centerX().toFloat(),
                    closestFace.boundingBox.centerY().toFloat()
                )
            )
            if (distance < 100f) closestFace else null
        }
    }

    /**
     * Calculate Euclidean distance between two points
     */
    private fun euclideanDistance(p1: Pair<Float, Float>, p2: Pair<Float, Float>): Float {
        val dx = p1.first - p2.first
        val dy = p1.second - p2.second
        return sqrt(dx * dx + dy * dy)
    }
}
