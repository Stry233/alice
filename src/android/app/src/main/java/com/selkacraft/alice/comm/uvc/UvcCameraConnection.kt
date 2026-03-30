package com.selkacraft.alice.comm.uvc

import com.selkacraft.alice.comm.core.DeviceConnection
import com.selkacraft.alice.comm.core.DeviceType
import com.selkacraft.alice.comm.core.Resolution
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera

/**
 * Represents an active connection to a UVC camera device
 */
data class UvcCameraConnection(
    val usbMonitor: USBMonitor,
    val camera: UVCCamera,
    val ctrlBlock: USBMonitor.UsbControlBlock,
    var negotiatedResolution: Resolution,
    val connectionId: Int,  // Keep existing connectionId for validation logic
    override val establishedAt: Long = System.currentTimeMillis()
) : DeviceConnection {

    override val deviceType: DeviceType = DeviceType.Camera

    override fun isValid(): Boolean {
        return try {
            // Check if camera has valid supported sizes (indicates camera is operational)
            // and control block has a valid file descriptor
            camera.supportedSizeList != null &&
                    ctrlBlock.fileDescriptor >= 0
        } catch (e: Exception) {
            false
        }
    }

    override fun getDescription(): String {
        val resolution = "${negotiatedResolution.width}x${negotiatedResolution.height}"
        return "UVC Camera (Resolution: $resolution, ID: $connectionId)"
    }
}

/**
 * Camera command types for serialized operations
 */
sealed class CameraCommand {
    data class StartPreview(val surface: android.view.Surface) : CameraCommand()
    object StopPreview : CameraCommand()
    data class SetSurface(val surface: android.view.Surface?) : CameraCommand()
    data class ChangeResolution(
        val resolution: Resolution,
        val callback: (Boolean) -> Unit
    ) : CameraCommand()
    data class PerformUsbReset(
        val resolution: Resolution,
        val callback: (Boolean) -> Unit
    ) : CameraCommand()
}

/**
 * UVC Camera capabilities
 */
data class UvcCameraCapabilities(
    val supportedResolutions: List<Resolution> = emptyList(),
    val supportedFormats: List<String> = listOf("MJPEG", "YUYV"),
    val maxDataRate: Int = 300,
    val requiresExclusiveAccess: Boolean = true
)