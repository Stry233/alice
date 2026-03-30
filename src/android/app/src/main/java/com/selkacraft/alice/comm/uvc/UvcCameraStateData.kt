package com.selkacraft.alice.comm.uvc

import android.view.Surface
import com.selkacraft.alice.comm.core.DeviceStateData
import com.selkacraft.alice.comm.core.Resolution

/**
 * UVC Camera-specific state data
 */
data class UvcCameraStateData(
    val aspectRatio: Float? = null,
    val surface: Surface? = null,
    val pendingResolutionChange: Resolution? = null,
    val pendingCtrlBlock: com.serenegiant.usb.USBMonitor.UsbControlBlock? = null,
    val resolutionChangeCallback: ((Boolean) -> Unit)? = null
) : DeviceStateData {
    override fun copy(): DeviceStateData = copy()
}

/**
 * Device profile for optimization (UVC-specific feature)
 */
data class DeviceProfile(
    val vendorId: Int,
    val productId: Int,
    val lastSuccessfulResolution: Resolution?,
    val lastConnectionTime: Long,
    val averageConnectionTime: Long
)