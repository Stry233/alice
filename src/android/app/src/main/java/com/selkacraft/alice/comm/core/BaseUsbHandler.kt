package com.selkacraft.alice.comm.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Base class for USB device handlers with common permission and broadcast handling
 */
abstract class BaseUsbHandler(
    protected val context: Context,
    protected val usbManager: UsbManager
) {
    companion object {
        private const val TAG = "BaseUsbHandler"
    }

    /**
     * Common USB events that all handlers share
     */
    sealed class UsbEvent {
        data class PermissionGranted(val device: UsbDevice) : UsbEvent()
        data class PermissionDenied(val device: UsbDevice) : UsbEvent()
        data class DeviceAttached(val device: UsbDevice) : UsbEvent()
        data class DeviceDetached(val device: UsbDevice) : UsbEvent()
    }

    // Event flow with buffer to prevent dropping events when using tryEmit
    private val _events = MutableSharedFlow<UsbEvent>(
        replay = 0,
        extraBufferCapacity = 64,  // Buffer up to 64 events
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<UsbEvent> = _events.asSharedFlow()

    // Registration state
    protected var isRegistered = false

    // Permission action - unique per device type
    protected val permissionAction: String
        get() = "com.selkacraft.alice.${getDeviceTypeTag()}_USB_PERMISSION"

    // Broadcast receiver for USB events
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                permissionAction -> handlePermissionResult(intent)
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> handleDeviceAttached(intent)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> handleDeviceDetached(intent)
            }
        }
    }

    /**
     * Abstract methods that subclasses must implement
     */
    protected abstract fun getDeviceTypeTag(): String
    protected abstract fun isTargetDevice(device: UsbDevice): Boolean
    protected abstract fun getLogTag(): String

    /**
     * Register USB broadcast receiver
     */
    open fun register() {
        if (isRegistered) {
            Log.d(getLogTag(), "Already registered")
            return
        }

        val filter = IntentFilter().apply {
            addAction(permissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        isRegistered = true
        Log.d(getLogTag(), "USB receiver registered")
    }

    /**
     * Unregister USB broadcast receiver
     */
    open fun unregister() {
        if (!isRegistered) return

        try {
            context.unregisterReceiver(usbReceiver)
            isRegistered = false
            Log.d(getLogTag(), "USB receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(getLogTag(), "Receiver already unregistered")
        }
    }

    /**
     * Request USB permission for device
     */
    open fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            Log.d(getLogTag(), "Already have permission for device")
            emitEvent(UsbEvent.PermissionGranted(device))
        } else {
            Log.d(getLogTag(), "Requesting permission for device: ${device.deviceName}")
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0,
                Intent(permissionAction),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    /**
     * Check if we have permission for device
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Get list of attached target devices
     */
    fun getAttachedTargetDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { isTargetDevice(it) }
    }

    /**
     * Handle permission result from broadcast
     */
    private fun handlePermissionResult(intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        device?.let {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.d(getLogTag(), "Permission ${if (granted) "granted" else "denied"} for ${it.deviceName}")

            emitEvent(
                if (granted) UsbEvent.PermissionGranted(it)
                else UsbEvent.PermissionDenied(it)
            )
        }
    }

    /**
     * Handle device attachment
     */
    private fun handleDeviceAttached(intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        device?.let {
            if (isTargetDevice(it)) {
                Log.d(getLogTag(), "${getDeviceTypeTag()} device attached: ${it.deviceName}")
                emitEvent(UsbEvent.DeviceAttached(it))
            }
        }
    }

    /**
     * Handle device detachment
     */
    private fun handleDeviceDetached(intent: Intent) {
        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        device?.let {
            // We emit detach event for any device since we might need to know
            // if our current device was detached
            Log.d(getLogTag(), "Device detached: ${it.deviceName}")
            emitEvent(UsbEvent.DeviceDetached(it))
        }
    }

    /**
     * Emit an event to the flow
     * Uses tryEmit to avoid blocking the main thread (called from BroadcastReceiver)
     */
    protected fun emitEvent(event: UsbEvent) {
        val emitted = _events.tryEmit(event)
        if (!emitted) {
            Log.w(getLogTag(), "Failed to emit event (buffer full): $event")
        }
    }
}