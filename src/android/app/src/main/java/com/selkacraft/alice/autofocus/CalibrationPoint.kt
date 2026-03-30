package com.selkacraft.alice.autofocus

import java.util.UUID

/**
 * Represents a single calibration point mapping depth to motor position
 */
data class CalibrationPoint(
    val id: String = UUID.randomUUID().toString(),
    val depth: Float,           // Depth in meters
    val motorPosition: Int,     // Motor position (0-4095)
    val confidence: Float,      // Confidence level (0-1)
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get formatted depth string
     */
    fun getFormattedDepth(): String {
        return String.format("%.3f m", depth)
    }

    /**
     * Get formatted confidence percentage
     */
    fun getFormattedConfidence(): String {
        return String.format("%.0f%%", confidence * 100)
    }

    /**
     * Check if this point is valid for calibration
     */
    fun isValid(): Boolean {
        return depth > 0 && depth <= 10.0f &&
                motorPosition in 0..4095 &&
                confidence > 0.5f
    }
}