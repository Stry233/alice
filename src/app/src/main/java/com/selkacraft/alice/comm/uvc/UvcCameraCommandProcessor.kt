package com.selkacraft.alice.comm.uvc

import android.util.Log
import android.view.Surface
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor

/**
 * Processes camera commands in a serialized manner using actor pattern
 */
class UvcCameraCommandProcessor(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "UvcCameraCommandProcessor"
    }

    // Command handler interface
    interface CommandHandler {
        suspend fun handleStartPreview(surface: Surface)
        suspend fun handleStopPreview()
        suspend fun handleSetSurface(surface: Surface?)
        suspend fun handleChangeResolution(resolution: com.selkacraft.alice.comm.core.Resolution): Boolean
        suspend fun handlePerformUsbReset(resolution: com.selkacraft.alice.comm.core.Resolution): Boolean
    }

    private var commandHandler: CommandHandler? = null

    @OptIn(ObsoleteCoroutinesApi::class)
    private val commandActor = scope.actor<CameraCommand>(capacity = Channel.UNLIMITED) {
        for (command in channel) {
            val handler = commandHandler
            if (handler == null) {
                Log.w(TAG, "No command handler set, dropping command: $command")
                if (command is CameraCommand.ChangeResolution) {
                    command.callback(false)
                } else if (command is CameraCommand.PerformUsbReset) {
                    command.callback(false)
                }
                continue
            }

            try {
                when (command) {
                    is CameraCommand.StartPreview -> {
                        Log.d(TAG, "Processing StartPreview command")
                        handler.handleStartPreview(command.surface)
                    }
                    is CameraCommand.StopPreview -> {
                        Log.d(TAG, "Processing StopPreview command")
                        handler.handleStopPreview()
                    }
                    is CameraCommand.SetSurface -> {
                        Log.d(TAG, "Processing SetSurface command")
                        handler.handleSetSurface(command.surface)
                    }
                    is CameraCommand.ChangeResolution -> {
                        Log.d(TAG, "Processing ChangeResolution command")
                        val result = handler.handleChangeResolution(command.resolution)
                        command.callback(result)
                    }
                    is CameraCommand.PerformUsbReset -> {
                        Log.d(TAG, "Processing PerformUsbReset command")
                        val result = handler.handlePerformUsbReset(command.resolution)
                        command.callback(result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing camera command: $command", e)
                when (command) {
                    is CameraCommand.ChangeResolution -> command.callback(false)
                    is CameraCommand.PerformUsbReset -> command.callback(false)
                    else -> {}
                }
            }
        }
    }

    /**
     * Set the command handler
     */
    fun setCommandHandler(handler: CommandHandler) {
        commandHandler = handler
    }

    /**
     * Send a command for processing
     */
    suspend fun sendCommand(command: CameraCommand) {
        try {
            commandActor.send(command)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command: $command", e)
            when (command) {
                is CameraCommand.ChangeResolution -> command.callback(false)
                is CameraCommand.PerformUsbReset -> command.callback(false)
                else -> {}
            }
        }
    }

    /**
     * Close the command processor
     */
    fun close() {
        commandActor.close()
        commandHandler = null
    }
}