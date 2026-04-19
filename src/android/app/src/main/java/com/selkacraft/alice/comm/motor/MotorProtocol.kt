package com.selkacraft.alice.comm.motor

import android.util.Log

/**
 * Motor controller protocol definitions and response parsing
 */
object MotorProtocol {
    private const val TAG = "MotorProtocol"

    // Serial communication parameters
    const val BAUD_RATE = 115200
    const val DATA_BITS = 8
    const val STOP_BITS = 1
    const val PARITY_NONE = 0

    // Command constants
    const val CMD_POSITION = "POS"
    const val CMD_STATUS = "STATUS"
    const val CMD_HELP = "HELP"
    const val CMD_CALIBRATE = "CALIBRATE"
    const val CMD_DEST = "DEST"         // Set destination address
    const val CMD_SCAN = "SCAN"         // Test a specific address
    const val CMD_GETDEST = "GETDEST"   // Get current destination

    // Response prefixes
    const val RESP_OK_POSITION = "OK:POS="
    const val RESP_OK_DEST = "OK:DEST="
    const val RESP_OK_SCAN = "OK:SCAN="
    const val RESP_ERROR = "ERROR:"
    const val RESP_READY = "Ready"
    const val RESP_CALIBRATED = "CALIBRATED"

    // Response patterns
    private val POSITION_PATTERN = Regex("""OK:POS=(\d+)""")
    private val DEST_PATTERN = Regex("""OK:DEST=([0-9A-Fa-f]{4})""")
    private val SCAN_PATTERN = Regex("""OK:SCAN=([0-9A-Fa-f]{4})""")
    private val STATUS_PATTERN = Regex("""Current=(\d+)""")

    /**
     * Format a command for sending to the motor controller
     */
    fun formatCommand(command: String): ByteArray {
        return "$command\r\n".toByteArray()
    }

    /**
     * Format a destination address command
     * @param address 16-bit address (0x0000-0xFFFF)
     */
    fun formatDestCommand(address: Int): String {
        val high = (address shr 8) and 0xFF
        val low = address and 0xFF
        return "$CMD_DEST $high $low"
    }

    /**
     * Format a scan command to test a specific address
     * @param address 16-bit address (0x0000-0xFFFF)
     */
    fun formatScanCommand(address: Int): String {
        val high = (address shr 8) and 0xFF
        val low = address and 0xFF
        return "$CMD_SCAN $high $low"
    }

    /**
     * Parse a hex address string (e.g., "FFFF") to Int
     */
    fun parseHexAddress(hexString: String): Int? {
        return try {
            hexString.toInt(16)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Format an address as a hex string for display
     */
    fun formatAddressHex(address: Int): String {
        return String.format("%04X", address and 0xFFFF)
    }

    /**
     * Parse a response from the motor controller
     */
    fun parseResponse(response: String): MotorResponse {
        val trimmed = response.trim()

        return when {
            trimmed.startsWith(RESP_OK_POSITION) -> {
                val position = POSITION_PATTERN.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
                if (position != null) {
                    MotorResponse.Position(position)
                } else {
                    MotorResponse.Unknown(trimmed)
                }
            }

            trimmed.startsWith(RESP_OK_DEST) -> {
                val addressHex = DEST_PATTERN.find(trimmed)?.groupValues?.get(1)
                val address = addressHex?.let { parseHexAddress(it) }
                if (address != null) {
                    MotorResponse.Destination(address)
                } else {
                    MotorResponse.Unknown(trimmed)
                }
            }

            trimmed.startsWith(RESP_OK_SCAN) -> {
                val addressHex = SCAN_PATTERN.find(trimmed)?.groupValues?.get(1)
                val address = addressHex?.let { parseHexAddress(it) }
                if (address != null) {
                    MotorResponse.ScanComplete(address)
                } else {
                    MotorResponse.Unknown(trimmed)
                }
            }

            trimmed.contains("Current=") -> {
                val position = STATUS_PATTERN.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
                if (position != null) {
                    MotorResponse.Status(position, trimmed)
                } else {
                    MotorResponse.Unknown(trimmed)
                }
            }

            trimmed.contains(RESP_CALIBRATED) -> {
                MotorResponse.Calibrated
            }

            trimmed.startsWith(RESP_ERROR) -> {
                val errorMessage = trimmed.substringAfter(RESP_ERROR)
                MotorResponse.Error(errorMessage)
            }

            trimmed.contains(RESP_READY) -> {
                MotorResponse.Ready
            }

            else -> {
                MotorResponse.Unknown(trimmed)
            }
        }
    }

    /**
     * Validate if a position value is within valid range
     */
    fun isValidPosition(position: Int): Boolean {
        return position in 0..4095
    }

    /**
     * Clamp a position value to valid range
     */
    fun clampPosition(position: Int): Int {
        return position.coerceIn(0, 4095)
    }
}

/**
 * Represents different types of responses from the motor controller
 */
sealed class MotorResponse {
    data class Position(val position: Int) : MotorResponse()
    data class Status(val position: Int, val fullStatus: String) : MotorResponse()
    data class Destination(val address: Int) : MotorResponse()
    data class ScanComplete(val address: Int) : MotorResponse()
    object Calibrated : MotorResponse()
    object Ready : MotorResponse()
    data class Error(val message: String) : MotorResponse()
    data class Unknown(val raw: String) : MotorResponse()
}