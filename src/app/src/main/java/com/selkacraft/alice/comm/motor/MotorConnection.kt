package com.selkacraft.alice.comm.motor

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.selkacraft.alice.comm.core.DeviceConnection
import com.selkacraft.alice.comm.core.DeviceType

/**
 * Represents an active connection to a motor controller device
 */
data class MotorConnection(
    val usbConnection: UsbDeviceConnection,
    val serialPort: UsbSerialPort,
    val ioManager: SerialInputOutputManager,
    override val establishedAt: Long = System.currentTimeMillis()
) : DeviceConnection {

    override val deviceType: DeviceType = DeviceType.Motor

    override fun isValid(): Boolean {
        return try {
            // Check if serial port is open and USB connection has valid file descriptor
            serialPort.isOpen &&
                    usbConnection.fileDescriptor >= 0
        } catch (e: Exception) {
            false
        }
    }

    override fun getDescription(): String {
        return "Motor Controller (Serial Port: ${if (isValid()) "Open" else "Closed"})"
    }
}

/**
 * Motor command with priority support for queue management
 */
data class MotorCommand(
    val command: String,
    val responseCallback: ((String) -> Unit)? = null,
    val priority: CommandPriority = CommandPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Command priority levels for queue management
 */
enum class CommandPriority {
    HIGH,    // Position updates - processed immediately
    NORMAL,  // Status queries - processed in order
    LOW      // Non-critical commands - processed when idle
}

/**
 * Motor controller capabilities
 */
data class MotorCapabilities(
    val minPosition: Int = 0,
    val maxPosition: Int = 4095,
    val hasEncoder: Boolean = true,
    val supportedCommands: List<String> = listOf("POS", "STATUS", "HELP")
)