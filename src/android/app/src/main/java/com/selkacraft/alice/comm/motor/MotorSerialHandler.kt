package com.selkacraft.alice.comm.motor

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Handles low-level serial communication with the motor controller
 */
class MotorSerialHandler(
    private val usbManager: UsbManager
) {
    companion object {
        private const val TAG = "MotorSerialHandler"
    }

    private val responseBuffer = StringBuilder()
    // Use buffered flows to prevent blocking in callbacks
    private val _responses = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 128,  // Buffer responses
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val responses: SharedFlow<String> = _responses.asSharedFlow()

    private val _errors = MutableSharedFlow<Exception>(
        replay = 0,
        extraBufferCapacity = 32,  // Buffer errors
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val errors: SharedFlow<Exception> = _errors.asSharedFlow()

    private var currentConnection: MotorConnection? = null

    /**
     * Serial data listener
     */
    private val serialListener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            try {
                handleSerialData(data)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling serial data", e)
            }
        }

        override fun onRunError(e: Exception) {
            Log.e(TAG, "Serial communication error", e)
            // Use tryEmit to avoid blocking
            val emitted = _errors.tryEmit(e)
            if (!emitted) {
                Log.w(TAG, "Failed to emit error (buffer full)")
            }
        }
    }

    /**
     * Open a connection to the motor controller
     */
    suspend fun openConnection(device: UsbDevice): MotorConnection = withContext(Dispatchers.IO) {
        Log.d(TAG, "Opening serial connection to: ${device.deviceName}")

        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw IllegalStateException("No driver found for motor controller")

        val connection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Failed to open device - check USB permissions")

        try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                MotorProtocol.BAUD_RATE,
                MotorProtocol.DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            port.dtr = true

            Log.d(TAG, "Serial port opened successfully")

            val ioManager = SerialInputOutputManager(port, serialListener)
            ioManager.start()

            Log.d(TAG, "Serial I/O manager started")

            val motorConnection = MotorConnection(connection, port, ioManager)
            currentConnection = motorConnection
            motorConnection
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    /**
     * Close the current connection
     */
    suspend fun closeConnection(connection: MotorConnection) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Closing serial connection")
        try {
            connection.ioManager.stop()
            connection.serialPort.close()
            connection.usbConnection.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection", e)
        } finally {
            if (currentConnection == connection) {
                currentConnection = null
            }
            responseBuffer.clear()
        }
    }

    /**
     * Send data to the motor controller
     */
    suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val connection = currentConnection
        if (connection == null) {
            Log.w(TAG, "No active connection for sending data")
            return@withContext false
        }

        try {
            connection.serialPort.write(data, 0) // 0 timeout for immediate write
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data", e)
            // Use tryEmit since we're in a suspend function but want non-blocking behavior
            val emitted = _errors.tryEmit(e)
            if (!emitted) {
                Log.w(TAG, "Failed to emit error (buffer full)")
            }
            false
        }
    }

    /**
     * Check if connection is healthy
     */
    fun isConnectionHealthy(): Boolean {
        return try {
            currentConnection?.serialPort?.dtr == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Handle incoming serial data
     */
    private fun handleSerialData(data: ByteArray) {
        val response = String(data)
        responseBuffer.append(response)

        // Process complete lines
        val lines = responseBuffer.split("\n")
        for (i in 0 until lines.size - 1) {
            val line = lines[i].trim()
            if (line.isNotEmpty()) {
                // Use tryEmit to avoid blocking
                val emitted = _responses.tryEmit(line)
                if (!emitted) {
                    Log.w(TAG, "Failed to emit response (buffer full), dropping: $line")
                }
            }
        }

        // Keep the incomplete line in buffer
        responseBuffer.clear()
        responseBuffer.append(lines.last())
    }

    /**
     * Clear any buffered data
     */
    fun clearBuffer() {
        responseBuffer.clear()
    }
}