package com.selkacraft.alice.comm.realsense

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Handles depth calculation, ROI processing, and adaptive filtering.
 * Uses Kalman filter for temporal smoothing and bilateral filter for spatial noise reduction.
 */
class RealSenseDepthCalculator {
    companion object {
        private const val TAG = "RealSenseDepthCalculator"

        // Adaptive ROI parameters
        private const val MIN_ROI_SIZE = 8
        private const val MAX_ROI_SIZE = 24
        private const val DEFAULT_ROI_SIZE = 16
    }

    private val kalmanFilter = KalmanDepthFilter()
    private val bilateralFilter = BilateralDepthFilter()
    private var currentROI = MeasurementROI(0.5f, 0.5f, DEFAULT_ROI_SIZE)
    private var enableSpatialFiltering = true
    private var enableAdaptiveROI = true

    /**
     * Calculate depth at specified normalized position with comprehensive validation
     */
    fun calculateDepth(
        depthFrame: ShortArray,
        width: Int,
        height: Int,
        normalizedX: Float,
        normalizedY: Float
    ): DepthMeasurement {
        // Validate input parameters
        if (depthFrame.isEmpty() || width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid input: frame empty or dimensions invalid (w=$width, h=$height)")
            return DepthMeasurement(0f, 0f, normalizedX, normalizedY)
        }

        // Validate normalized coordinates
        if (!normalizedX.isFinite() || !normalizedY.isFinite() ||
            normalizedX < 0f || normalizedX > 1f ||
            normalizedY < 0f || normalizedY > 1f) {
            Log.w(TAG, "Invalid normalized coordinates: ($normalizedX, $normalizedY)")
            return DepthMeasurement(0f, 0f, 0.5f, 0.5f)
        }

        // Validate expected frame size
        val expectedSize = width * height
        if (depthFrame.size < expectedSize) {
            Log.w(TAG, "Frame size mismatch: got ${depthFrame.size}, expected $expectedSize")
            return DepthMeasurement(0f, 0f, normalizedX, normalizedY)
        }

        // Map normalized position to depth frame coordinates
        val depthX = (width * normalizedX).toInt().coerceIn(0, width - 1)
        val depthY = (height * normalizedY).toInt().coerceIn(0, height - 1)

        // Update ROI with adaptive sizing if enabled
        if (enableAdaptiveROI) {
            updateAdaptiveROI(normalizedX, normalizedY, depthFrame, width, height, depthX, depthY)
        } else {
            currentROI = MeasurementROI(normalizedX, normalizedY, DEFAULT_ROI_SIZE)
        }

        // Apply bilateral spatial filtering first to reduce noise
        val centerDepthMm = if (enableSpatialFiltering) {
            bilateralFilter.filter(depthFrame, width, height, depthX, depthY, radius = 3)
        } else {
            // Fall back to median filtering
            val roiValues = extractROIValues(depthFrame, width, height, depthX, depthY)
            if (roiValues.isEmpty()) {
                // No valid values, return last known depth
                val lastDepth = kalmanFilter.getCurrentEstimate() / 1000f
                return DepthMeasurement(lastDepth, 0f, normalizedX, normalizedY)
            }
            roiValues.sort()
            roiValues[roiValues.size / 2]
        }

        // Validate depth value
        if (!centerDepthMm.isFinite() || centerDepthMm < 0) {
            Log.w(TAG, "Invalid center depth: $centerDepthMm")
            val lastDepth = kalmanFilter.getCurrentEstimate() / 1000f
            return DepthMeasurement(lastDepth, 0f, normalizedX, normalizedY)
        }

        // Apply Kalman temporal filtering for smooth tracking
        val filteredDepthMm = kalmanFilter.update(centerDepthMm)

        // Calculate confidence from Kalman filter and measurement quality
        val roiValues = extractROIValues(depthFrame, width, height, depthX, depthY)
        val measurementConfidence = calculateConfidence(roiValues, currentROI.size)
        val kalmanConfidence = kalmanFilter.getConfidence()
        val confidence = (measurementConfidence * 0.6f + kalmanConfidence * 0.4f).coerceIn(0f, 1f)

        // Convert to meters with validation
        val depthMeters = filteredDepthMm / 1000f
        if (!depthMeters.isFinite()) {
            Log.w(TAG, "Depth calculation resulted in non-finite value")
            return DepthMeasurement(0f, 0f, normalizedX, normalizedY)
        }

        // Log.v(TAG, "Depth at ($normalizedX, $normalizedY): ${String.format("%.2f", depthMeters)}m")

        return DepthMeasurement(depthMeters, confidence, normalizedX, normalizedY)
    }

    /**
     * Extract depth values from ROI
     */
    private fun extractROIValues(
        depthFrame: ShortArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int
    ): FloatArray {
        val values = mutableListOf<Float>()
        val roiSize = currentROI.size
        val halfSize = roiSize / 2

        for (dy in -halfSize..halfSize) {
            for (dx in -halfSize..halfSize) {
                val x = (centerX + dx).coerceIn(0, width - 1)
                val y = (centerY + dy).coerceIn(0, height - 1)
                val idx = y * width + x

                if (idx < depthFrame.size) {
                    val depthMm = depthFrame[idx].toInt() and 0xFFFF

                    // Validate depth value
                    if (depthMm in RealSenseParameters.MIN_VALID_DEPTH..RealSenseParameters.MAX_VALID_DEPTH) {
                        values.add(depthMm.toFloat())
                    }
                }
            }
        }

        return values.toFloatArray()
    }

    /**
     * Calculate confidence based on ROI statistics
     */
    private fun calculateConfidence(values: FloatArray, roiSize: Int): Float {
        if (values.isEmpty()) return 0f

        // Valid ratio component
        val maxPossibleValues = roiSize * roiSize
        val validRatio = values.size.toFloat() / maxPossibleValues

        // Stability component (based on variance)
        val stability = if (values.size > 1) {
            val mean = values.average().toFloat()
            val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
            1f / (1f + sqrt(variance) / 100f)
        } else {
            0.5f
        }

        // Combine components
        val confidence = (validRatio * 0.5f + stability * 0.5f).coerceIn(0f, 1f)

        return confidence
    }

    /**
     * Update ROI size based on scene stability
     * More stable = larger ROI (more averaging, less noise)
     * Less stable = smaller ROI (faster response)
     */
    private fun updateAdaptiveROI(
        normalizedX: Float,
        normalizedY: Float,
        depthFrame: ShortArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int
    ) {
        // Sample depth variance in current region
        val testROI = DEFAULT_ROI_SIZE
        val halfSize = testROI / 2

        val values = mutableListOf<Float>()
        for (dy in -halfSize..halfSize step 2) {  // Sample every 2 pixels for speed
            for (dx in -halfSize..halfSize step 2) {
                val x = (centerX + dx).coerceIn(0, width - 1)
                val y = (centerY + dy).coerceIn(0, height - 1)
                val idx = y * width + x

                if (idx < depthFrame.size) {
                    val depthMm = depthFrame[idx].toInt() and 0xFFFF
                    if (depthMm in RealSenseParameters.MIN_VALID_DEPTH..RealSenseParameters.MAX_VALID_DEPTH) {
                        values.add(depthMm.toFloat())
                    }
                }
            }
        }

        // Calculate stability metric (coefficient of variation)
        val roiSize = if (values.size > 2) {
            val mean = values.average().toFloat()
            val stdDev = sqrt(values.map { (it - mean) * (it - mean) }.average().toFloat())
            val coefficientOfVariation = if (mean > 0) stdDev / mean else 1f

            // Map CV to ROI size
            // Low CV (stable) -> larger ROI
            // High CV (unstable) -> smaller ROI
            when {
                coefficientOfVariation < 0.02f -> MAX_ROI_SIZE  // Very stable
                coefficientOfVariation < 0.05f -> DEFAULT_ROI_SIZE + 4  // Stable
                coefficientOfVariation < 0.1f -> DEFAULT_ROI_SIZE  // Normal
                coefficientOfVariation < 0.2f -> DEFAULT_ROI_SIZE - 4  // Unstable
                else -> MIN_ROI_SIZE  // Very unstable
            }
        } else {
            DEFAULT_ROI_SIZE  // Not enough data, use default
        }

        currentROI = MeasurementROI(normalizedX, normalizedY, roiSize)
    }

    /**
     * Enable or disable spatial filtering
     */
    fun setSpatialFilteringEnabled(enabled: Boolean) {
        enableSpatialFiltering = enabled
        Log.d(TAG, "Spatial filtering ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Enable or disable adaptive ROI sizing
     */
    fun setAdaptiveROIEnabled(enabled: Boolean) {
        enableAdaptiveROI = enabled
        Log.d(TAG, "Adaptive ROI ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get predicted depth for next frame (proactive autofocus)
     */
    fun getPredictedDepth(): Float {
        return kalmanFilter.predict() / 1000f  // Convert to meters
    }

    /**
     * Reset the calculator
     */
    fun reset() {
        kalmanFilter.reset()
        currentROI = MeasurementROI(0.5f, 0.5f, DEFAULT_ROI_SIZE)
        Log.d(TAG, "Depth calculator reset")
    }

}