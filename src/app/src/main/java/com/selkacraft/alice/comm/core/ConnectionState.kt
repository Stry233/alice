package com.selkacraft.alice.comm.core

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Discovering : ConnectionState()
    object Connecting : ConnectionState()
    object AwaitingPermission : ConnectionState()
    object Connected : ConnectionState()
    object Active : ConnectionState() // Device is connected and actively being used
    data class Error(val message: String, val isRetryable: Boolean = true) : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
}