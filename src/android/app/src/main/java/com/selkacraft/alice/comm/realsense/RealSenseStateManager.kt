package com.selkacraft.alice.comm.realsense

import android.graphics.Bitmap
import android.util.Log
import com.selkacraft.alice.comm.core.GenericStateManager
import com.selkacraft.alice.comm.core.DeviceState
import com.selkacraft.alice.comm.core.RealSenseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * Simplified RealSense state manager using generic state management
 */
class RealSenseStateManager(
    scope: CoroutineScope
) : GenericStateManager<RealSenseStateData>(scope, "RealSenseStateManager") {

    companion object {
        private const val DEFAULT_MEASUREMENT_X = 0.5f
        private const val DEFAULT_MEASUREMENT_Y = 0.5f
    }

    // Expose specific state properties as flows for compatibility
    val centerDepth: StateFlow<Float> = deviceData.map {
        it?.centerDepth ?: 0f
    }.stateIn(scope, SharingStarted.Eagerly, 0f)

    val depthConfidence: StateFlow<Float> = deviceData.map {
        it?.depthConfidence ?: 0f
    }.stateIn(scope, SharingStarted.Eagerly, 0f)

    val depthBitmap: StateFlow<Bitmap?> = deviceData.map {
        it?.depthBitmap
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val colorBitmap: StateFlow<Bitmap?> = deviceData.map {
        it?.colorBitmap
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val measurementPosition: StateFlow<Pair<Float, Float>> = deviceData.map {
        it?.measurementPosition ?: Pair(DEFAULT_MEASUREMENT_X, DEFAULT_MEASUREMENT_Y)
    }.stateIn(scope, SharingStarted.Eagerly, Pair(DEFAULT_MEASUREMENT_X, DEFAULT_MEASUREMENT_Y))

    // Pipeline active state derived from device state
    val isPipelineActive: StateFlow<Boolean> = deviceState.map {
        it is RealSenseState.PipelineActive || it is RealSenseState.Streaming
    }.stateIn(scope, SharingStarted.Eagerly, false)

    // Alias for compatibility
    val isCapturing: StateFlow<Boolean> = deviceState.map {
        it is DeviceState.Active || it is RealSenseState.Streaming
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        setData(createDefaultData())
    }

    override fun createDefaultData(): RealSenseStateData {
        return RealSenseStateData()
    }

    /**
     * Update depth measurement
     */
    fun updateDepthMeasurement(depth: Float, confidence: Float) {
        updateData { current ->
            (current ?: createDefaultData()).copy(
                centerDepth = depth,
                depthConfidence = confidence
            )
        }
        // Log.v(tag, "Depth: ${String.format("%.2f", depth)}m (${String.format("%.0f", confidence * 100)}%)")
    }

    /**
     * Update depth bitmap
     */
    fun updateDepthBitmap(bitmap: Bitmap?) {
        updateData { current ->
            (current ?: createDefaultData()).copy(depthBitmap = bitmap)
        }
        // bitmap?.let { Log.v(tag, "Bitmap: ${it.width}x${it.height}") }
    }

    /**
     * Update color bitmap for face detection
     */
    fun updateColorBitmap(bitmap: Bitmap?) {
        updateData { current ->
            (current ?: createDefaultData()).copy(
                colorBitmap = bitmap,
                colorWidth = bitmap?.width ?: 0,
                colorHeight = bitmap?.height ?: 0
            )
        }
        // bitmap?.let { Log.v(tag, "Color bitmap: ${it.width}x${it.height}") }
    }

    /**
     * Set measurement position (normalized)
     */
    fun setMeasurementPosition(normalizedX: Float, normalizedY: Float) {
        val position = Pair(
            normalizedX.coerceIn(0f, 1f),
            normalizedY.coerceIn(0f, 1f)
        )
        updateData { current ->
            (current ?: createDefaultData()).copy(measurementPosition = position)
        }
        Log.d(tag, "Measurement position: (${String.format("%.2f", position.first)}, ${String.format("%.2f", position.second)})")
    }

    /**
     * Get pixel position from normalized coordinates
     */
    fun getMeasurementPixelPosition(): Pair<Int, Int> {
        return deviceData.value?.getMeasurementPixelPosition() ?: Pair(0, 0)
    }

    /**
     * Update frame dimensions
     */
    fun updateFrameDimensions(width: Int, height: Int) {
        updateData { current ->
            (current ?: createDefaultData()).copy(
                depthWidth = width,
                depthHeight = height
            )
        }
        Log.d(tag, "Frame dimensions: ${width}x${height}")
    }

    /**
     * Set capturing state
     */
    fun setCapturing(capturing: Boolean) {
        updateState(if (capturing) DeviceState.Active else DeviceState.Connected)
        Log.d(tag, "Capturing: $capturing")
    }

    /**
     * Set pipeline active state
     */
    fun setPipelineActive(active: Boolean) {
        if (active) {
            updateState(RealSenseState.PipelineActive)
        } else if (deviceState.value is RealSenseState.PipelineActive) {
            updateState(DeviceState.Connected)
        }
        Log.d(tag, "Pipeline active: $active")
    }

    /**
     * Clear depth data
     */
    fun clearDepthData() {
        Log.d(tag, "Clearing depth data")
        setData(createDefaultData())
    }

    override fun onPhysicalDisconnection() {
        clearDepthData()
    }

    override fun onReset() {
        clearDepthData()
    }

    override fun onDestroy() {
        // Free bitmap memory
        updateData { current ->
            current?.copy(depthBitmap = null, colorBitmap = null)
        }
    }
}