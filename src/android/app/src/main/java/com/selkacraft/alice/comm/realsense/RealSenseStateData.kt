package com.selkacraft.alice.comm.realsense

import android.graphics.Bitmap
import com.selkacraft.alice.comm.core.DeviceStateData

/**
 * RealSense-specific state data
 */
data class RealSenseStateData(
    val centerDepth: Float = 0f,
    val depthConfidence: Float = 0f,
    val depthBitmap: Bitmap? = null,
    val colorBitmap: Bitmap? = null,  // Raw color frame for face detection
    val measurementPosition: Pair<Float, Float> = Pair(0.5f, 0.5f),
    val depthWidth: Int = 0,
    val depthHeight: Int = 0,
    val colorWidth: Int = 0,
    val colorHeight: Int = 0
) : DeviceStateData {
    override fun copy(): DeviceStateData = copy()

    fun getMeasurementPixelPosition(): Pair<Int, Int> {
        val (normX, normY) = measurementPosition
        return Pair(
            (depthWidth * normX).toInt().coerceIn(0, depthWidth - 1),
            (depthHeight * normY).toInt().coerceIn(0, depthHeight - 1)
        )
    }
}