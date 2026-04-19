package com.selkacraft.alice.comm.core

/**
 * Common interface for all device connections.
 * Provides a contract for connection validation and information.
 */
interface DeviceConnection {
    /**
     * Check if this connection is still valid and operational.
     * Each device type implements its own validation logic.
     */
    fun isValid(): Boolean

    /**
     * Get the type of device this connection represents
     */
    val deviceType: DeviceType

    /**
     * Get a human-readable description of this connection
     */
    fun getDescription(): String = "${deviceType.displayName} Connection"

    /**
     * Connection establishment timestamp
     */
    val establishedAt: Long
        get() = System.currentTimeMillis()

    /**
     * Get connection duration in milliseconds
     */
    fun getConnectionDuration(): Long = System.currentTimeMillis() - establishedAt
}

/**
 * Optional connection metadata that can be attached to any connection
 */
data class ConnectionMetadata(
    val id: String = generateId(),
    val createdAt: Long = System.currentTimeMillis(),
    val attributes: Map<String, Any> = emptyMap()
) {
    companion object {
        private var counter = 0

        @Synchronized
        private fun generateId(): String {
            counter++
            return "${System.currentTimeMillis()}_$counter"
        }
    }
}