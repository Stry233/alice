package com.selkacraft.alice.comm.core

sealed class DeviceType(
    val id: String,
    val displayName: String,
    val priority: Int,
    val requiredBandwidth: Int // in Mbps
) {
    // RealSense has HIGHER priority than regular cameras
    object RealSense : DeviceType(
        id = "realsense",
        displayName = "RealSense Depth Camera",
        priority = 150,  // Higher than Camera
        requiredBandwidth = 400
    )

    object Camera : DeviceType(
        id = "camera",
        displayName = "UVC Camera",
        priority = 100,  // Lower than RealSense
        requiredBandwidth = 300
    )

    object Motor : DeviceType(
        id = "motor",
        displayName = "Motor Controller",
        priority = 50,
        requiredBandwidth = 10
    )

    // Easy to add new device types
    class Custom(
        id: String,
        displayName: String,
        priority: Int = 10,
        requiredBandwidth: Int = 50
    ) : DeviceType(id, displayName, priority, requiredBandwidth)
}