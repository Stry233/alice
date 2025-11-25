package com.selkacraft.alice.comm.core

data class DeviceCapabilities(
    val supportedResolutions: List<Resolution> = emptyList(),
    val supportedFormats: List<String> = emptyList(),
    val supportedCommands: List<String> = emptyList(),
    val maxDataRate: Int = 0, // in Mbps
    val requiresExclusiveAccess: Boolean = false,
    val customCapabilities: Map<String, Any> = emptyMap()
)

data class Resolution(val width: Int, val height: Int) {
    override fun toString() = "${width}x${height}"
}