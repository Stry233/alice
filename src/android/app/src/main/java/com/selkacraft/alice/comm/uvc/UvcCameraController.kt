package com.selkacraft.alice.comm.uvc

import android.view.Surface
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.comm.core.Resolution
import kotlinx.coroutines.flow.StateFlow

/**
 * Simplified interface for UVC camera control operations
 * This interface is what other components should use to interact with the camera
 */
interface UvcCameraController {
    /**
     * Camera aspect ratio (width/height)
     */
    val cameraAspectRatio: StateFlow<Float?>

    /**
     * Preview active state
     */
    val isPreviewActive: StateFlow<Boolean>

    /**
     * Connection state
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Current camera surface
     */
    val currentCameraSurface: Surface?

    /**
     * Set the preview surface
     * @param surface Surface for camera preview, or null to stop preview
     */
    fun setSurface(surface: Surface?)

    /**
     * Change camera resolution
     * @param resolution New resolution to apply
     * @return true if successful
     */
    suspend fun changeResolution(resolution: Resolution): Boolean

    /**
     * Check if camera is connected
     */
    fun isConnected(): Boolean {
        val state = connectionState.value
        return state is ConnectionState.Connected || state is ConnectionState.Active
    }

    /**
     * Check if preview is active
     */
    fun isPreviewRunning(): Boolean {
        return isPreviewActive.value
    }

    /**
     * Register to start monitoring for devices
     */
    fun register()

    /**
     * Unregister and stop monitoring
     */
    fun unregister()
}

/**
 * Extension to convert UvcCameraManager to controller interface
 */
fun UvcCameraManager.asController(): UvcCameraController {
    val manager = this
    return object : UvcCameraController {
        override val cameraAspectRatio = manager.cameraAspectRatio
        override val isPreviewActive = manager.isPreviewActive
        override val connectionState = manager.connectionState
        override val currentCameraSurface = manager.currentCameraSurface

        override fun setSurface(surface: Surface?) = manager.setSurface(surface)
        override suspend fun changeResolution(resolution: Resolution) = manager.changeResolution(resolution)
        override fun register() = manager.register()
        override fun unregister() = manager.unregister()
    }
}