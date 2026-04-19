package com.selkacraft.alice.comm.realsense

import com.intel.realsense.librealsense.Pipeline
import com.selkacraft.alice.comm.core.DeviceConnection
import com.selkacraft.alice.comm.core.DeviceType

/**
 * Represents an active connection to a RealSense device
 */
data class RealSenseConnection(
    val pipeline: Pipeline,
    val colorWidth: Int,
    val colorHeight: Int,
    val depthWidth: Int,
    val depthHeight: Int,
    val depthScale: Float,
    override val establishedAt: Long = System.currentTimeMillis()
) : DeviceConnection {

    override val deviceType: DeviceType = DeviceType.RealSense

    override fun isValid(): Boolean {
        return try {
            // Pipeline validity is determined by whether it's not null
            // and hasn't thrown an exception when accessed
            // In practice, if the pipeline was closed or invalid,
            // accessing it would throw an exception
            pipeline != null && depthScale > 0
        } catch (e: Exception) {
            false
        }
    }

    override fun getDescription(): String {
        val colorRes = if (colorWidth > 0 && colorHeight > 0) {
            "${colorWidth}x${colorHeight}"
        } else {
            "N/A"
        }
        val depthRes = "${depthWidth}x${depthHeight}"
        return "RealSense (Color: $colorRes, Depth: $depthRes, Scale: ${depthScale}m)"
    }
}

/**
 * Configuration for RealSense streams.
 */
data class RealSenseStreamConfig(
    val colorWidth: Int = 424,
    val colorHeight: Int = 240,
    val depthWidth: Int = 424,
    val depthHeight: Int = 240,
    val fps: Int = 60,
    val enableColor: Boolean = true,
    val enableDepth: Boolean = true
)

/**
 * Depth measurement result
 */
data class DepthMeasurement(
    val depth: Float,        // Depth in meters
    val confidence: Float,   // Confidence 0-1
    val x: Float,           // Normalized X position (0-1)
    val y: Float            // Normalized Y position (0-1)
)

/**
 * ROI for depth measurement
 */
data class MeasurementROI(
    val centerX: Float,     // Normalized center X (0-1)
    val centerY: Float,     // Normalized center Y (0-1)
    val size: Int          // ROI size in pixels
)

/**
 * Depth data for event emission
 */
data class DepthData(
    val depth: Float,
    val confidence: Float
)

/**
 * RealSense processing parameters for depth capture and autofocus.
 */
object RealSenseParameters {
    // Depth range parameters (in millimeters)
    const val MIN_VALID_DEPTH = 200
    const val MAX_VALID_DEPTH = 10000
    const val NEAR_RANGE_THRESHOLD = 2000
    const val FAR_RANGE_THRESHOLD = 5000

    // Processing parameters
    /** Bitmap update interval for UI display (~15 FPS) */
    const val BITMAP_UPDATE_INTERVAL_MS = 66L

    // Temporal filter alpha values (used as fallback)
    const val NEAR_TEMPORAL_ALPHA = 0.8f
    const val MID_TEMPORAL_ALPHA = 0.6f
    const val FAR_TEMPORAL_ALPHA = 0.4f

    // Connection parameters
    const val DEVICE_ENUMERATION_DELAY_MS = 300L
    const val PIPELINE_STABILIZATION_DELAY_MS = 300L
    const val RECONNECTION_MIN_DELAY_MS = 500L
    const val MAX_CONSECUTIVE_ERRORS = 3
    /** Frame wait timeout (should exceed frame period at target FPS) */
    const val FRAME_WAIT_TIMEOUT_MS = 100
}