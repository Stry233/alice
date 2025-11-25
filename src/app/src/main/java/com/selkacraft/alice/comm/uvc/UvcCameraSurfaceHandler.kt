package com.selkacraft.alice.comm.uvc

import android.util.Log
import android.view.Surface
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles surface management and preview operations for UVC camera
 */
class UvcCameraSurfaceHandler {
    companion object {
        private const val TAG = "UvcCameraSurfaceHandler"
        private const val PREVIEW_OPERATION_TIMEOUT_MS = 300L // Reduced from 500ms
        private const val PREVIEW_RESTART_DELAY_MS = 20L // Reduced from 50ms
    }

    private val operationMutex = Mutex()

    /**
     * Start camera preview on the given surface with optimized performance
     */
    suspend fun startPreview(
        camera: UVCCamera,
        surface: Surface,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                if (!surface.isValid) {
                    Log.w(TAG, "Cannot start preview - surface is invalid")
                    onError(IllegalStateException("Surface is invalid"))
                    return@withLock
                }

                Log.d(TAG, "Starting preview on surface")

                // Parallel operations for faster startup
                val startTime = System.currentTimeMillis()

                // Set the preview display
                camera.setPreviewDisplay(surface)

                // Start preview
                camera.startPreview()

                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Preview started successfully in ${elapsedTime}ms")

                onSuccess()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start preview: ${e.message}", e)
                onError(e)
            }
        }
    }

    /**
     * Stop camera preview with optimized timeout handling
     */
    suspend fun stopPreview(
        camera: UVCCamera?,
        isClosing: Boolean = false
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            if (camera == null) {
                Log.d(TAG, "No camera to stop preview")
                return@withLock
            }

            try {
                Log.d(TAG, "Stopping preview (isClosing: $isClosing)")

                // Use shorter timeout for faster response
                val timeout = if (isClosing) 100L else PREVIEW_OPERATION_TIMEOUT_MS

                withTimeoutOrNull(timeout) {
                    camera.stopPreview()
                } ?: Log.w(TAG, "stopPreview timed out after ${timeout}ms")

                Log.d(TAG, "Preview stopped")

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping preview (may be expected during lifecycle)", e)
                // Don't rethrow - this can happen during normal lifecycle
            }
        }
    }

    /**
     * Restart preview with new surface using optimized approach
     */
    suspend fun restartPreview(
        camera: UVCCamera,
        oldSurface: Surface?,
        newSurface: Surface,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            try {
                Log.d(TAG, "Restarting preview with new surface")

                // Parallel operations for faster restart
                if (oldSurface != null && oldSurface != newSurface) {
                    // Quick stop with minimal timeout
                    withTimeoutOrNull(100L) {
                        camera.stopPreview()
                    }

                    // Minimal delay between operations
                    if (PREVIEW_RESTART_DELAY_MS > 0) {
                        delay(PREVIEW_RESTART_DELAY_MS)
                    }
                }

                // Start preview on new surface
                if (newSurface.isValid) {
                    val startTime = System.currentTimeMillis()

                    camera.setPreviewDisplay(newSurface)
                    camera.startPreview()

                    val elapsedTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Preview restarted successfully in ${elapsedTime}ms")

                    onSuccess()
                } else {
                    Log.w(TAG, "Cannot restart preview - new surface is invalid")
                    onError(IllegalStateException("New surface is invalid"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart preview", e)
                onError(e)
            }
        }
    }

    /**
     * Quick preview state check without locks for performance
     */
    fun isValidForPreview(surface: Surface?): Boolean {
        return surface != null && surface.isValid
    }

    /**
     * Check if surface needs preview restart with quick evaluation
     */
    fun needsPreviewRestart(
        currentSurface: Surface?,
        newSurface: Surface?,
        isPreviewActive: Boolean
    ): Boolean {
        // Quick path evaluations
        if (currentSurface === newSurface) return false // Same object reference

        return when {
            // No surface change needed if both are null
            currentSurface == null && newSurface == null -> false

            // Need to stop if surface becomes null
            currentSurface != null && newSurface == null -> isPreviewActive

            // Need to start if surface becomes available
            currentSurface == null && newSurface != null -> true

            // Need to restart if surface changed
            currentSurface != newSurface -> true

            // No change needed
            else -> false
        }
    }
}