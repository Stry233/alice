package com.selkacraft.alice.comm.realsense

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 1D Kalman filter for depth tracking with adaptive noise estimation.
 * Based on industry-standard sensor fusion algorithms used in cinema autofocus systems.
 *
 * This filter provides optimal smoothing while minimizing lag, and can predict
 * the next depth value for proactive autofocus.
 */
class KalmanDepthFilter {

    // State variables
    private var x = 0f  // Estimated depth (mm)
    private var P = 1000f  // Estimation error covariance
    private var isInitialized = false

    // Filter parameters
    private var Q = 50f  // Process noise covariance (how much we expect depth to change)
    private var R = 100f  // Measurement noise covariance (sensor noise)

    // Adaptive parameters
    private val historySize = 10
    private val measurementHistory = mutableListOf<Float>()
    private var lastUpdateTime = 0L

    /**
     * Update filter with a new depth measurement
     * @param measurement New depth measurement in mm
     * @param timestamp Measurement timestamp in ms
     * @return Filtered depth in mm
     */
    fun update(measurement: Float, timestamp: Long = System.currentTimeMillis()): Float {
        if (!isInitialized) {
            // Initialize filter with first measurement
            x = measurement
            P = 1000f
            isInitialized = true
            lastUpdateTime = timestamp
            measurementHistory.add(measurement)
            return x
        }

        // Calculate time delta for prediction step
        val dt = if (lastUpdateTime > 0) {
            (timestamp - lastUpdateTime) / 1000f  // Convert to seconds
        } else {
            0.016f  // Assume ~60 FPS
        }.coerceIn(0.001f, 0.5f)  // Clamp to reasonable range

        lastUpdateTime = timestamp

        // Adaptive noise estimation based on measurement stability
        adaptNoiseParameters(measurement)

        // Prediction step
        // x_pred = x (assuming constant depth model)
        // P_pred = P + Q
        val P_pred = P + Q * dt  // Scale process noise by time

        // Update step
        // Innovation (measurement residual)
        val y = measurement - x

        // Innovation covariance
        val S = P_pred + R

        // Kalman gain
        val K = P_pred / S

        // Update state estimate
        x += K * y

        // Update error covariance
        P = (1f - K) * P_pred

        // Add to history
        measurementHistory.add(measurement)
        if (measurementHistory.size > historySize) {
            measurementHistory.removeAt(0)
        }

        return x
    }

    /**
     * Predict next depth value based on current state
     * Useful for proactive autofocus
     */
    fun predict(): Float {
        return x  // For constant velocity model, prediction is current estimate
        // Could be enhanced with velocity tracking for moving subjects
    }

    /**
     * Get current filtered depth without updating
     */
    fun getCurrentEstimate(): Float {
        return if (isInitialized) x else 0f
    }

    /**
     * Get uncertainty/confidence of current estimate
     * @return Confidence value 0-1 (higher is better)
     */
    fun getConfidence(): Float {
        if (!isInitialized) return 0f

        // Convert covariance to confidence
        // Lower P means higher confidence
        val maxP = 1000f
        return (1f - (P / maxP).coerceIn(0f, 1f))
    }

    /**
     * Adapt noise parameters based on measurement stability
     * More stable = trust measurements more (lower R)
     * More variation = expect more changes (higher Q)
     */
    private fun adaptNoiseParameters(measurement: Float) {
        if (measurementHistory.size < 3) return

        // Calculate measurement variance
        val mean = measurementHistory.average().toFloat()
        val variance = measurementHistory.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)

        // Adapt measurement noise based on recent stability
        // Stable measurements -> lower R (trust sensor more)
        // Noisy measurements -> higher R (trust sensor less)
        R = (50f + stdDev * 0.5f).coerceIn(20f, 500f)

        // Adapt process noise based on depth change rate
        val recentChange = abs(measurement - measurementHistory[measurementHistory.size - 1])

        // If depth is changing rapidly, increase Q to allow faster tracking
        // If depth is stable, decrease Q for more smoothing
        Q = when {
            recentChange > 100f -> 100f  // Fast motion
            recentChange > 50f -> 60f    // Moderate motion
            recentChange > 20f -> 40f    // Slow motion
            else -> 20f                   // Stable scene
        }
    }

    /**
     * Reset filter to initial state
     */
    fun reset() {
        x = 0f
        P = 1000f
        isInitialized = false
        lastUpdateTime = 0L
        measurementHistory.clear()
        Q = 50f
        R = 100f
    }

    /**
     * Check if filter is initialized
     */
    fun isReady(): Boolean = isInitialized
}

/**
 * Bilateral spatial filter for depth data
 * Provides edge-preserving smoothing to reduce noise while maintaining sharp boundaries
 */
class BilateralDepthFilter(
    private val spatialSigma: Float = 2.0f,
    private val rangeSigma: Float = 50f  // mm
) {

    /**
     * Apply bilateral filter to a region of depth data
     * @param depthData Depth values in mm
     * @param width Width of the depth region
     * @param height Height of the depth region
     * @param centerX X coordinate of center pixel
     * @param centerY Y coordinate of center pixel
     * @param radius Filter radius in pixels
     * @return Filtered depth value at center
     */
    fun filter(
        depthData: ShortArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        radius: Int = 2
    ): Float {
        val centerIdx = centerY * width + centerX
        if (centerIdx >= depthData.size) return 0f

        val centerDepth = (depthData[centerIdx].toInt() and 0xFFFF).toFloat()
        if (centerDepth <= 0) return 0f

        var weightedSum = 0f
        var weightSum = 0f

        // Iterate over neighborhood
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = centerX + dx
                val y = centerY + dy

                // Check bounds
                if (x < 0 || x >= width || y < 0 || y >= height) continue

                val idx = y * width + x
                if (idx >= depthData.size) continue

                val depth = (depthData[idx].toInt() and 0xFFFF).toFloat()
                if (depth <= 0) continue

                // Spatial weight (based on distance)
                val spatialDist = sqrt((dx * dx + dy * dy).toFloat())
                val spatialWeight = gaussian(spatialDist, spatialSigma)

                // Range weight (based on depth difference)
                val rangeDist = abs(depth - centerDepth)
                val rangeWeight = gaussian(rangeDist, rangeSigma)

                // Combined weight
                val weight = spatialWeight * rangeWeight

                weightedSum += depth * weight
                weightSum += weight
            }
        }

        return if (weightSum > 0) weightedSum / weightSum else centerDepth
    }

    /**
     * Gaussian weight function
     */
    private fun gaussian(distance: Float, sigma: Float): Float {
        return kotlin.math.exp(-(distance * distance) / (2f * sigma * sigma))
    }
}
