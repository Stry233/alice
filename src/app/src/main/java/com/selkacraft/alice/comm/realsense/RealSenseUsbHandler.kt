package com.selkacraft.alice.comm.realsense

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.selkacraft.alice.comm.core.BaseUsbHandler

/**
 * Handles USB permissions and device detection for RealSense cameras
 */
class RealSenseUsbHandler(
    context: Context,
    usbManager: UsbManager
) : BaseUsbHandler(context, usbManager) {

    companion object {
        private const val TAG = "RealSenseUsbHandler"

        // Intel RealSense identifiers
        const val INTEL_VENDOR_ID = 0x8086
        private val REALSENSE_PRODUCT_IDS = setOf(
            0x0B07, 0x0B3A, 0x0B5C, 0x0B64, 0x0AD1,

            0x0AD2, 0x0B52, 0x0B3D, 0x0AFE, 0x0B5B
        )
    }

    // Reuse base class UsbEvent types directly
    // No need to define custom events since base events cover all our needs

    override fun getDeviceTypeTag(): String = "REALSENSE"

    override fun getLogTag(): String = TAG

    override fun isTargetDevice(device: UsbDevice): Boolean {
        return isRealSenseDevice(device)
    }

    /**
     * Check if device is a RealSense camera
     */
    fun isRealSenseDevice(device: UsbDevice): Boolean {
        // Check vendor and product IDs
        if (device.vendorId == INTEL_VENDOR_ID &&
            REALSENSE_PRODUCT_IDS.contains(device.productId)) {
            Log.d(TAG, "Found RealSense device: ${device.productName}")
            return true
        }

        // Check device name as fallback
        val deviceName = device.productName?.lowercase() ?: device.deviceName.lowercase()
        if (device.vendorId == INTEL_VENDOR_ID &&
            (deviceName.contains("realsense") || deviceName.contains("intel realsense"))) {
            Log.d(TAG, "Found RealSense device by name: $deviceName")
            return true
        }

        return false
    }

    /**
     * Get list of attached RealSense devices
     */
    fun getAttachedRealSenseDevices(): List<UsbDevice> {
        return getAttachedTargetDevices()
    }
}