package com.selkacraft.alice.comm.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Proxy that sends motor commands to the desktop over the network
 * instead of directly to the USB serial dongle.
 *
 * Used when the Android device is operating as a remote controller
 * and the desktop PC has the physical motor dongle connected.
 */
class RemoteMotorProxy(
    private val syncClient: SyncClient,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "RemoteMotorProxy"
    }

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _isRemoteMode = MutableStateFlow(false)
    val isRemoteMode: StateFlow<Boolean> = _isRemoteMode.asStateFlow()

    init {
        // Listen for state updates from desktop to track remote motor position
        scope.launch {
            syncClient.messages.collect { message ->
                if (message.type == "STATE_UPDATE") {
                    val pos = message.payload["motorPosition"]
                    if (pos != null) {
                        try {
                            _position.value = pos.toString().toDouble().toInt()
                        } catch (_: NumberFormatException) {}
                    }
                }
            }
        }
    }

    /**
     * Enable or disable remote mode.
     * When enabled, motor commands are sent over the network.
     */
    fun setRemoteMode(enabled: Boolean) {
        _isRemoteMode.value = enabled
        Log.d(TAG, "Remote motor mode: $enabled")
    }

    /**
     * Set motor position via the network.
     * Only works when connected to the desktop sync server.
     */
    fun setPosition(position: Int, source: String = "manual") {
        if (!syncClient.connected.value) {
            Log.w(TAG, "Cannot send motor command - not connected")
            return
        }

        val clamped = position.coerceIn(0, 4095)
        syncClient.sendMotorCommand(clamped, source)
        _position.value = clamped
    }

    /**
     * Send a mode change to the desktop.
     */
    fun sendModeChange(mode: String, enabled: Boolean,
                       confidenceThreshold: Float, smoothing: Boolean, responseSpeed: Int) {
        syncClient.sendModeChange(mode, enabled, confidenceThreshold, smoothing, responseSpeed)
    }
}
