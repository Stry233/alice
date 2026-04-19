package com.selkacraft.alice.comm.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for connecting to an Alice desktop sync server.
 * Manages authentication, reconnection, and message routing.
 */
class SyncClient(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SyncClient"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RECONNECT_DELAY_MS = 1000L
        const val FRAME_TYPE_COLOR: Byte = 0x01
        const val FRAME_TYPE_DEPTH: Byte = 0x02
        const val FRAME_TYPE_CAPTURE: Byte = 0x03
        private const val FRAME_HEADER_SIZE = 5
    }

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _messages = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<SyncMessage> = _messages.asSharedFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val _remoteColorBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteColorBitmap: StateFlow<Bitmap?> = _remoteColorBitmap.asStateFlow()

    private val _remoteDepthBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteDepthBitmap: StateFlow<Bitmap?> = _remoteDepthBitmap.asStateFlow()

    private val _remoteCaptureFrame = MutableStateFlow<Bitmap?>(null)
    val remoteCaptureFrame: StateFlow<Bitmap?> = _remoteCaptureFrame.asStateFlow()

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var token: String = ""
    private var serverUrl: String = ""
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var intentionalDisconnect = false
    private var pendingStreamRequest_ = true
    private var pendingStreamColor_ = true
    private var pendingStreamDepth_ = true
    private var pendingStreamCapture_ = true

    fun connect(ip: String, port: Int, sessionToken: String) {
        disconnect()
        intentionalDisconnect = false

        token = sessionToken
        serverUrl = "ws://$ip:$port"

        Log.d(TAG, "Connecting to $serverUrl")

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, authenticating...")
                val authMsg = SyncMessage.authenticate(token)
                webSocket.send(authMsg.serialize())
                // Request video streams immediately after auth (before events dispatch)
                if (pendingStreamRequest_) {
                    val streamMsg = SyncMessage.streamControl(
                        pendingStreamColor_, pendingStreamDepth_, pendingStreamCapture_
                    )
                    webSocket.send(streamMsg.serialize())
                    Log.d(TAG, "Sent stream control: color=${pendingStreamColor_} depth=${pendingStreamDepth_} capture=${pendingStreamCapture_}")
                }
                _connected.value = true
                reconnectAttempts = 0
                scope.launch { _events.emit(SyncEvent.Connected) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = SyncMessage.deserialize(text)
                if (msg != null) {
                    scope.launch { _messages.emit(msg) }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size < FRAME_HEADER_SIZE) return

                val frameType = bytes[0]
                val jpegData = bytes.substring(FRAME_HEADER_SIZE).toByteArray()

                val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                if (bitmap == null) {
                    Log.w(TAG, "Failed to decode JPEG frame type=$frameType size=${jpegData.size}")
                    return
                }

                when (frameType) {
                    FRAME_TYPE_COLOR -> _remoteColorBitmap.value = bitmap
                    FRAME_TYPE_DEPTH -> _remoteDepthBitmap.value = bitmap
                    FRAME_TYPE_CAPTURE -> {
                        Log.d(TAG, "Capture frame received: ${bitmap.width}x${bitmap.height}, jpeg=${jpegData.size}")
                        _remoteCaptureFrame.value = bitmap
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket CLOSED: code=$code reason=$reason")
                _connected.value = false
                _remoteColorBitmap.value = null
                _remoteDepthBitmap.value = null
                _remoteCaptureFrame.value = null
                scope.launch { _events.emit(SyncEvent.Disconnected(reason)) }
                if (!intentionalDisconnect) {
                    attemptReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket FAILURE: ${t.message}")
                _connected.value = false
                _remoteColorBitmap.value = null
                _remoteDepthBitmap.value = null
                _remoteCaptureFrame.value = null
                scope.launch { _events.emit(SyncEvent.Error(t.message ?: "Connection failed")) }
                if (!intentionalDisconnect) {
                    attemptReconnect()
                }
            }
        })
    }

    fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        _connected.value = false
        _remoteColorBitmap.value = null
        _remoteDepthBitmap.value = null
        _remoteCaptureFrame.value = null
    }

    fun send(message: SyncMessage) {
        if (_connected.value) {
            webSocket?.send(message.serialize())
        }
    }

    fun sendMotorCommand(position: Int, source: String = "manual") {
        send(SyncMessage.motorCommand(position, source))
    }

    fun sendModeChange(mode: String, enabled: Boolean,
                       confidenceThreshold: Float, smoothing: Boolean, responseSpeed: Int) {
        send(SyncMessage.modeChange(mode, enabled, confidenceThreshold, smoothing, responseSpeed))
    }

    fun clearRemoteBitmaps(color: Boolean = true, depth: Boolean = true, capture: Boolean = true) {
        if (color) _remoteColorBitmap.value = null
        if (depth) _remoteDepthBitmap.value = null
        if (capture) _remoteCaptureFrame.value = null
    }

    fun sendStreamControl(color: Boolean, depth: Boolean, capture: Boolean) {
        pendingStreamColor_ = color
        pendingStreamDepth_ = depth
        pendingStreamCapture_ = capture
        pendingStreamRequest_ = true
        send(SyncMessage.streamControl(color, depth, capture))
    }

    private fun attemptReconnect() {
        if (intentionalDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "Max reconnect attempts reached")
            scope.launch { _events.emit(SyncEvent.Error("Max reconnect attempts reached")) }
            return
        }

        val delay = BASE_RECONNECT_DELAY_MS * (1 shl reconnectAttempts)
        reconnectAttempts++

        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")

        reconnectJob = scope.launch {
            delay(delay)
            if (serverUrl.isNotEmpty() && !intentionalDisconnect) {
                connect(
                    serverUrl.removePrefix("ws://").substringBefore(":"),
                    serverUrl.substringAfterLast(":").toIntOrNull() ?: SyncConstants.DEFAULT_PORT,
                    token
                )
            }
        }
    }
}

sealed class SyncEvent {
    object Connected : SyncEvent()
    data class Disconnected(val reason: String) : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}
