package com.selkacraft.alice.comm.core

import android.hardware.usb.UsbDevice

sealed class DeviceEvent {
    data class Discovered(val device: UsbDevice, val deviceType: DeviceType) : DeviceEvent()
    data class PermissionGranted(val device: UsbDevice) : DeviceEvent()
    data class PermissionDenied(val device: UsbDevice) : DeviceEvent()
    data class Connected(val device: UsbDevice, val deviceInfo: String) : DeviceEvent()
    data class ConnectionFailed(val device: UsbDevice, val error: Throwable) : DeviceEvent()
    data class Disconnected(val device: UsbDevice, val reason: String) : DeviceEvent()
    data class StateChanged(val device: UsbDevice, val newState: ConnectionState) : DeviceEvent()
    data class DataReceived(val device: UsbDevice, val data: Any) : DeviceEvent()
    data class Error(val device: UsbDevice?, val error: Throwable) : DeviceEvent()
}