package com.selkacraft.alice.comm.uvc

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.selkacraft.alice.comm.core.BaseUsbHandler
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles USB monitoring and device lifecycle for UVC cameras
 * Extends BaseUsbHandler.UsbEvent and adds UVC-specific events
 */
class UvcCameraUsbHandler(
    private val context: Context
) {
    companion object {
        private const val TAG = "UvcCameraUsbHandler"

        // Known RealSense device IDs to exclude
        private val REALSENSE_PRODUCT_IDS = setOf(
            0x0B07, 0x0B3A, 0x0B5C, 0x0B64, 0x0AD1, 0x0AD2, 0x0B52,
            0x0B3D, 0x0AFE, 0x0B5B
        )

        // Store the original default exception handler
        private var originalExceptionHandler: Thread.UncaughtExceptionHandler? = null
        private var exceptionHandlerInstalled = false
    }

    /**
     * Extended USB events that include base events plus UVC-specific ones
     */
    sealed class UsbEvent {
        // Reuse base event types for consistency
        data class PermissionGranted(val device: UsbDevice) : UsbEvent()
        data class PermissionDenied(val device: UsbDevice) : UsbEvent()
        data class DeviceAttached(val device: UsbDevice) : UsbEvent()
        data class DeviceDetached(val device: UsbDevice) : UsbEvent()

        // UVC-specific events
        data class DeviceConnected(
            val device: UsbDevice,
            val ctrlBlock: USBMonitor.UsbControlBlock
        ) : UsbEvent()
        data class DeviceDisconnected(val device: UsbDevice) : UsbEvent()
        data class PermissionCancelled(val device: UsbDevice) : UsbEvent()
        data class ConnectionError(val device: UsbDevice?, val error: Throwable) : UsbEvent()
    }

    private val _events = MutableSharedFlow<UsbEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<UsbEvent> = _events.asSharedFlow()

    private var isRegistered = false
    val activeControlBlocks = ConcurrentHashMap<String, USBMonitor.UsbControlBlock>()

    // Track pending permission requests to handle SecurityException
    private val pendingDevices = ConcurrentHashMap<String, UsbDevice>()

    val usbMonitor: USBMonitor by lazy {
        USBMonitor(context.applicationContext, usbMonitorListener)
    }

    private val usbMonitorListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            device?.let {
                if (canHandleDevice(it)) {
                    Log.d(TAG, "UVC device attached: ${it.deviceName}")
                    emitEvent(UsbEvent.DeviceAttached(it))
                }
            }
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            device?.let { usbDevice ->
                // Remove from pending since connection succeeded
                pendingDevices.remove(usbDevice.deviceName)

                ctrlBlock?.let { block ->
                    Log.d(TAG, "UVC device connected: ${usbDevice.deviceName}")
                    activeControlBlocks[usbDevice.deviceName] = block

                    // Emit both the connected event and permission granted for consistency
                    emitEvent(UsbEvent.DeviceConnected(usbDevice, block))
                    emitEvent(UsbEvent.PermissionGranted(usbDevice))
                }
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            device?.let {
                Log.d(TAG, "UVC device disconnected: ${it.deviceName}")
                activeControlBlocks.remove(it.deviceName)
                pendingDevices.remove(it.deviceName)
                emitEvent(UsbEvent.DeviceDisconnected(it))
            }
        }

        override fun onDettach(device: UsbDevice?) {
            device?.let {
                Log.d(TAG, "UVC device detached: ${it.deviceName}")
                activeControlBlocks.remove(it.deviceName)
                pendingDevices.remove(it.deviceName)
                emitEvent(UsbEvent.DeviceDetached(it))
            }
        }

        override fun onCancel(device: UsbDevice?) {
            device?.let {
                Log.d(TAG, "Permission cancelled for device: ${it.deviceName}")
                activeControlBlocks.remove(it.deviceName)
                pendingDevices.remove(it.deviceName)

                // Emit both cancelled and denied for consistency with base pattern
                emitEvent(UsbEvent.PermissionCancelled(it))
                emitEvent(UsbEvent.PermissionDenied(it))
            }
        }
    }

    /**
     * Install a global exception handler to catch SecurityException from USBMonitor thread.
     * This handles the case where UsbDevice.getSerialNumber() throws SecurityException
     * on some devices even after USB permission is granted.
     */
    private fun installExceptionHandler() {
        if (exceptionHandlerInstalled) return

        synchronized(Companion::class.java) {
            if (exceptionHandlerInstalled) return

            originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                // Check if this is the USBMonitor SecurityException we're trying to handle
                if (thread.name.contains("USBMonitor", ignoreCase = true) &&
                    throwable is SecurityException &&
                    throwable.message?.contains("permission to access device", ignoreCase = true) == true
                ) {
                    Log.e(TAG, "Caught SecurityException from USBMonitor thread: ${throwable.message}")

                    // Find the pending device that caused this and emit an error event
                    val pendingDevice = pendingDevices.values.firstOrNull()
                    if (pendingDevice != null) {
                        pendingDevices.remove(pendingDevice.deviceName)
                        emitEvent(UsbEvent.ConnectionError(pendingDevice, throwable))
                        emitEvent(UsbEvent.PermissionDenied(pendingDevice))
                    } else {
                        emitEvent(UsbEvent.ConnectionError(null, throwable))
                    }

                    // Don't propagate - we've handled it
                    Log.w(TAG, "SecurityException handled - device connection failed but app continues")
                } else {
                    // Not our exception, pass to original handler
                    originalExceptionHandler?.uncaughtException(thread, throwable)
                        ?: throw throwable
                }
            }

            exceptionHandlerInstalled = true
            Log.d(TAG, "USB SecurityException handler installed")
        }
    }

    /**
     * Check if device is a UVC camera (excluding RealSense)
     */
    fun canHandleDevice(device: UsbDevice): Boolean {
        // Skip Intel devices (RealSense)
        if (device.vendorId == 0x8086) {
            Log.d(TAG, "Skipping Intel device (potential RealSense): ${device.deviceName}")
            return false
        }

        // Skip known RealSense product IDs
        if (REALSENSE_PRODUCT_IDS.contains(device.productId)) {
            Log.d(TAG, "Skipping RealSense device by product ID: ${device.productId}")
            return false
        }

        // Check for USB Video Class
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 14) { // USB Video Class
                Log.d(TAG, "Found UVC interface on device: ${device.deviceName}")
                return true
            }
        }
        return false
    }

    /**
     * Request USB permission for device
     */
    fun requestPermission(device: UsbDevice) {
        Log.d(TAG, "Requesting permission for device: ${device.deviceName}")
        // Track this device as pending permission
        pendingDevices[device.deviceName] = device
        usbMonitor.requestPermission(device)
    }

    /**
     * Check if we have permission for device
     */
    fun hasPermission(device: UsbDevice): Boolean {
        // USBMonitor handles permissions internally
        // We check if we have an active control block for this device
        return activeControlBlocks.containsKey(device.deviceName)
    }

    /**
     * Register USB monitor
     */
    fun register() {
        if (!isRegistered) {
            Log.d(TAG, "Registering USB monitor")
            installExceptionHandler()
            isRegistered = true
            usbMonitor.register()
        }
    }

    /**
     * Unregister USB monitor
     */
    fun unregister() {
        if (isRegistered) {
            Log.d(TAG, "Unregistering USB monitor")
            isRegistered = false
            activeControlBlocks.clear()
            pendingDevices.clear()
            usbMonitor.unregister()
        }
    }

    /**
     * Get control block for device
     */
    fun getControlBlock(device: UsbDevice): USBMonitor.UsbControlBlock? {
        return activeControlBlocks[device.deviceName]
    }

    /**
     * Clear control blocks
     */
    fun clearControlBlocks() {
        activeControlBlocks.clear()
        pendingDevices.clear()
    }

    /**
     * Emit an event to the flow (non-blocking)
     */
    private fun emitEvent(event: UsbEvent) {
        val emitted = _events.tryEmit(event)
        if (!emitted) {
            Log.w(TAG, "Failed to emit event (buffer full): $event")
        }
    }
}