package com.selkacraft.alice.comm.core

import android.hardware.usb.UsbDevice
import android.util.Log

class DeviceRegistry {
    private val handlers = mutableMapOf<DeviceType, BaseUsbDeviceManager<*>>()
    private val matchers = mutableListOf<DeviceMatcher>()

    companion object {
        private const val TAG = "DeviceRegistry"
    }

    interface DeviceMatcher {
        fun matches(device: UsbDevice): DeviceType?
    }

    class VendorProductMatcher(
        private val vendorId: Int,
        private val productIds: Set<Int>,
        private val deviceType: DeviceType
    ) : DeviceMatcher {
        override fun matches(device: UsbDevice): DeviceType? {
            return if (device.vendorId == vendorId && productIds.contains(device.productId)) {
                Log.d(TAG, "VendorProductMatcher matched device ${device.deviceName} as ${deviceType.displayName}")
                deviceType
            } else null
        }
    }

    class InterfaceClassMatcher(
        private val interfaceClass: Int,
        private val deviceType: DeviceType
    ) : DeviceMatcher {
        override fun matches(device: UsbDevice): DeviceType? {
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == interfaceClass) {
                    Log.d(TAG, "InterfaceClassMatcher matched device ${device.deviceName} as ${deviceType.displayName}")
                    return deviceType
                }
            }
            return null
        }
    }

    fun register(deviceType: DeviceType, handler: BaseUsbDeviceManager<*>) {
        handlers[deviceType] = handler
        Log.d(TAG, "Registered handler for ${deviceType.displayName}")
    }

    fun unregister(deviceType: DeviceType) {
        handlers.remove(deviceType)
        Log.d(TAG, "Unregistered handler for ${deviceType.displayName}")
    }

    fun addMatcher(matcher: DeviceMatcher) {
        matchers.add(matcher)
        Log.d(TAG, "Added matcher: ${matcher::class.simpleName}")
    }

    fun clearMatchers() {
        matchers.clear()
        Log.d(TAG, "Cleared all matchers")
    }

    fun getHandler(deviceType: DeviceType): BaseUsbDeviceManager<*>? {
        return handlers[deviceType]
    }

    fun getAllHandlers(): Map<DeviceType, BaseUsbDeviceManager<*>> {
        return handlers.toMap()
    }

    suspend fun identifyDevice(device: UsbDevice): DeviceType? {
        Log.d(TAG, "Identifying device: ${device.deviceName} (${device.vendorId}:${device.productId})")

        // IMPORTANT: Check handlers first in priority order
        // This ensures RealSense handler gets first chance to identify Intel devices
        val sortedHandlers = handlers.entries.sortedByDescending { it.key.priority }

        for ((type, handler) in sortedHandlers) {
            try {
                if (handler.canHandleDevice(device)) {
                    Log.d(TAG, "Device identified as ${type.displayName} by handler")
                    return type
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if ${type.displayName} handler can handle device", e)
            }
        }

        // Then check matchers (for devices without specific handlers)
        matchers.forEach { matcher ->
            matcher.matches(device)?.let { deviceType ->
                Log.d(TAG, "Device identified as ${deviceType.displayName} by matcher")
                return deviceType
            }
        }

        Log.d(TAG, "Device not identified by any handler or matcher")
        return null
    }
}