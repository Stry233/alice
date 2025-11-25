package com.selkacraft.alice.comm.motor

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Processes motor commands with priority queue management
 */
class MotorCommandProcessor(
    private val scope: CoroutineScope,
    private val serialHandler: MotorSerialHandler
) {
    companion object {
        private const val TAG = "MotorCommandProcessor"
        private const val RESPONSE_TIMEOUT_MS = 100L
    }

    private var commandChannel = Channel<MotorCommand>(Channel.UNLIMITED)
    private var processorJob: Job? = null
    private val isProcessorActive = AtomicBoolean(false)

    // Priority buffers for command management
    private val highPriorityBuffer = mutableListOf<MotorCommand>()
    private val normalPriorityBuffer = mutableListOf<MotorCommand>()
    private val lowPriorityBuffer = mutableListOf<MotorCommand>()

    /**
     * Start the command processor
     */
    fun start() {
        if (isProcessorActive.get()) {
            Log.d(TAG, "Command processor already running")
            return
        }

        isProcessorActive.set(true)
        commandChannel = Channel(Channel.UNLIMITED)

        processorJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting command processor")
            try {
                processCommands()
            } catch (e: CancellationException) {
                Log.d(TAG, "Command processor cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Command processor error", e)
            } finally {
                Log.d(TAG, "Command processor stopped")
                isProcessorActive.set(false)
            }
        }
    }

    /**
     * Stop the command processor
     */
    fun stop() {
        Log.d(TAG, "Stopping command processor")
        isProcessorActive.set(false)
        processorJob?.cancel()
        processorJob = null
        commandChannel.close()
        clearBuffers()
    }

    /**
     * Send a command to the processor
     */
    suspend fun sendCommand(command: MotorCommand): Boolean {
        if (!isProcessorActive.get()) {
            Log.w(TAG, "Cannot send command - processor not active")
            return false
        }

        return try {
            commandChannel.send(command)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue command: ${command.command}", e)
            false
        }
    }

    /**
     * Process commands from the queue with priority handling
     */
    private suspend fun processCommands() {
        while (isProcessorActive.get()) {
            try {
                // Collect all pending commands
                collectPendingCommands()

                // Get next command by priority
                val command = getNextCommand() ?: commandChannel.receive()

                // Process the command
                processCommand(command)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isProcessorActive.get()) {
                    Log.e(TAG, "Error processing command", e)
                    delay(10) // Brief delay before continuing
                }
            }
        }
    }

    /**
     * Collect all pending commands from channel into priority buffers
     */
    private suspend fun collectPendingCommands() {
        while (true) {
            val result = commandChannel.tryReceive()
            if (result.isSuccess) {
                val command = result.getOrNull() ?: break
                addToBuffer(command)
            } else {
                break
            }
        }
    }

    /**
     * Add command to appropriate priority buffer
     */
    private fun addToBuffer(command: MotorCommand) {
        when (command.priority) {
            CommandPriority.HIGH -> {
                // For position commands, only keep the latest
                if (command.command.startsWith(MotorProtocol.CMD_POSITION)) {
                    highPriorityBuffer.removeAll { it.command.startsWith(MotorProtocol.CMD_POSITION) }
                }
                highPriorityBuffer.add(command)
            }
            CommandPriority.NORMAL -> normalPriorityBuffer.add(command)
            CommandPriority.LOW -> lowPriorityBuffer.add(command)
        }
    }

    /**
     * Get the next command from buffers by priority
     */
    private fun getNextCommand(): MotorCommand? {
        return when {
            highPriorityBuffer.isNotEmpty() -> highPriorityBuffer.removeAt(0)
            normalPriorityBuffer.isNotEmpty() -> normalPriorityBuffer.removeAt(0)
            lowPriorityBuffer.isNotEmpty() -> lowPriorityBuffer.removeAt(0)
            else -> null
        }
    }

    /**
     * Process a single command
     */
    private suspend fun processCommand(command: MotorCommand) {
        val data = MotorProtocol.formatCommand(command.command)
        val sent = serialHandler.sendData(data)

        if (!sent) {
            Log.e(TAG, "Failed to send command: ${command.command}")
            command.responseCallback?.invoke("ERROR: Failed to send")
            return
        }

        // Only wait for response if callback provided and not a position command
        if (command.responseCallback != null && !command.command.startsWith(MotorProtocol.CMD_POSITION)) {
            waitForResponse(command)
        }
    }

    /**
     * Wait for response with timeout
     */
    private suspend fun waitForResponse(command: MotorCommand) {
        try {
            withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
                serialHandler.responses.first { response ->
                    command.responseCallback?.invoke(response)
                    true
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Response timeout for command: ${command.command}")
            command.responseCallback?.invoke("ERROR: Timeout")
        }
    }

    /**
     * Clear all command buffers
     */
    private fun clearBuffers() {
        highPriorityBuffer.clear()
        normalPriorityBuffer.clear()
        lowPriorityBuffer.clear()
    }

    /**
     * Get buffer statistics for debugging
     */
    fun getBufferStats(): Map<String, Int> {
        return mapOf(
            "high" to highPriorityBuffer.size,
            "normal" to normalPriorityBuffer.size,
            "low" to lowPriorityBuffer.size
        )
    }
}