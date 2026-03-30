package com.selkacraft.alice.comm.motor

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.selkacraft.alice.comm.core.BaseUsbHandler

/**
 * Handles USB permissions and device detection for motor controllers
 */
class MotorUsbHandler(
    context: Context,
    usbManager: UsbManager
) : BaseUsbHandler(context, usbManager) {

    companion object {
        private const val TAG = "MotorUsbHandler"

        // Motor controller device identifiers
        const val TILTA_VENDOR_ID = 0x2FE3
        const val TILTA_PRODUCT_ID = 0x0100
    }

    // Reuse base class UsbEvent types directly
    // No need to define custom events since base events cover all our needs

    override fun getDeviceTypeTag(): String = "MOTOR"

    override fun getLogTag(): String = TAG

    override fun isTargetDevice(device: UsbDevice): Boolean {
        return isMotorController(device)
    }

    /**
     * Check if device is a motor controller
     */
    fun isMotorController(device: UsbDevice): Boolean {
        // Check for Tilta Nucleus Nano 2
        if (device.vendorId == TILTA_VENDOR_ID && device.productId == TILTA_PRODUCT_ID) {
            Log.d(TAG, "Found Tilta Nucleus Nano 2 motor controller")
            return true
        }

        // Check for CDC ACM devices
        if (device.deviceClass == 2 && device.deviceSubclass == 2) {
            Log.d(TAG, "Found CDC ACM device")
            return true
        }

        // Check interfaces for CDC
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 2 && iface.interfaceSubclass == 2) {
                Log.d(TAG, "Found CDC interface on device")
                return true
            }
        }

        return false
    }

    /**
     * Get list of attached motor controllers
     */
    fun getAttachedMotorControllers(): List<UsbDevice> {
        return getAttachedTargetDevices()
    }
}