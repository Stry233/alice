package com.selkacraft.alice.comm.realsense

import android.graphics.Bitmap
import com.selkacraft.alice.comm.core.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Simplified interface for RealSense operations
 * This interface is what other components should use to interact with the RealSense system
 */
interface RealSenseController {
    /**
     * Current depth measurement at center point (in meters)
     */
    val centerDepth: StateFlow<Float>

    /**
     * Confidence of the depth measurement (0-1)
     */
    val depthConfidence: StateFlow<Float>

    /**
     * Current depth visualization bitmap
     */
    val depthBitmap: StateFlow<Bitmap?>

    /**
     * Current measurement position (normalized 0-1)
     */
    val measurementPosition: StateFlow<Pair<Float, Float>>

    /**
     * Connection state
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Set the measurement position for depth calculation
     * @param normalizedX X position (0-1)
     * @param normalizedY Y position (0-1)
     */
    fun setMeasurementPosition(normalizedX: Float, normalizedY: Float)

    /**
     * Check if RealSense is connected and active
     */
    fun isConnected(): Boolean {
        return connectionState.value is ConnectionState.Connected ||
                connectionState.value is ConnectionState.Active
    }

    /**
     * Check if actively capturing frames
     */
    fun isCapturing(): Boolean

    /**
     * Register to start monitoring for devices
     */
    fun register()

    /**
     * Unregister and stop monitoring
     */
    fun unregister()

    /**
     * Get stream configuration
     */
    fun getStreamConfig(): RealSenseStreamConfig

    /**
     * Update stream configuration (requires reconnection)
     */
    fun updateStreamConfig(config: RealSenseStreamConfig)
}

/**
 * Extension of RealSenseManager to implement the simplified interface
 */
fun RealSenseManager.asController(): RealSenseController {
    val manager = this
    return object : RealSenseController {
        override val centerDepth = manager.centerDepth
        override val depthConfidence = manager.depthConfidence
        override val depthBitmap = manager.depthBitmap
        override val measurementPosition = manager.measurementPosition
        override val connectionState = manager.connectionState

        override fun setMeasurementPosition(normalizedX: Float, normalizedY: Float) =
            manager.setMeasurementPosition(normalizedX, normalizedY)

        override fun isCapturing() = manager.isCapturing()
        override fun register() = manager.register()
        override fun unregister() = manager.unregister()
        override fun getStreamConfig() = manager.getStreamConfig()
        override fun updateStreamConfig(config: RealSenseStreamConfig) =
            manager.updateStreamConfig(config)
    }
}

/**
 * Quick depth range presets
 */
object DepthRangePresets {
    const val MACRO_MIN = 0.2f    // 20cm
    const val MACRO_MAX = 0.5f    // 50cm

    const val CLOSE_MIN = 0.3f    // 30cm
    const val CLOSE_MAX = 1.0f    // 1m

    const val MEDIUM_MIN = 0.5f   // 50cm
    const val MEDIUM_MAX = 3.0f   // 3m

    const val FAR_MIN = 1.0f      // 1m
    const val FAR_MAX = 10.0f     // 10m
}