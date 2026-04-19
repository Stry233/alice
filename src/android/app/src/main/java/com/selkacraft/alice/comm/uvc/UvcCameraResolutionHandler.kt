package com.selkacraft.alice.comm.uvc

import android.util.Log
import com.selkacraft.alice.comm.core.Resolution
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.Size

/**
 * Handles resolution configuration and changes for UVC camera
 */
class UvcCameraResolutionHandler {
    companion object {
        private const val TAG = "UvcCameraResolutionHandler"

        // Common resolutions to try (ordered by popularity/performance)
        private val COMMON_RESOLUTIONS = listOf(
            Resolution(1920, 1080), // Full HD
            Resolution(1280, 720),  // HD
            Resolution(640, 480)    // VGA
        )
    }

    // Cache for supported resolutions to avoid repeated queries
    private var cachedSupportedSizes: List<Size>? = null
    private var lastCameraHash: Int = 0

    /**
     * Configure optimal resolution for the camera with caching
     */
    fun configureOptimalResolution(
        camera: UVCCamera,
        targetResolution: Resolution
    ): Resolution {
        // Get supported sizes with caching
        val cameraHash = camera.hashCode()
        val supportedSizes = if (cameraHash == lastCameraHash && cachedSupportedSizes != null) {
            Log.d(TAG, "Using cached supported sizes")
            cachedSupportedSizes!!
        } else {
            try {
                val sizes = camera.supportedSizeList ?: emptyList()
                cachedSupportedSizes = sizes
                lastCameraHash = cameraHash
                sizes
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get supported sizes", e)
                emptyList()
            }
        }

        if (supportedSizes.isEmpty()) {
            Log.w(TAG, "No supported resolutions found, using fallback")
            return if (trySetResolutionFast(camera, targetResolution)) {
                targetResolution
            } else {
                Resolution(640, 480) // Fallback
            }
        }

        Log.d(TAG, "Checking ${supportedSizes.size} supported resolutions for target ${targetResolution.width}x${targetResolution.height}")

        // Fast path: Try exact match first
        val exactMatch = supportedSizes.find {
            it.width == targetResolution.width && it.height == targetResolution.height
        }

        if (exactMatch != null && trySetResolutionFast(camera, targetResolution)) {
            Log.d(TAG, "Exact resolution match found and set: ${targetResolution.width}x${targetResolution.height}")
            return targetResolution
        }

        // Try common resolutions for better performance
        for (res in COMMON_RESOLUTIONS) {
            if (res == targetResolution) continue // Already tried

            val matchingSize = supportedSizes.find {
                it.width == res.width && it.height == res.height
            }
            if (matchingSize != null && trySetResolutionFast(camera, res)) {
                Log.d(TAG, "Set common resolution: ${res.width}x${res.height}")
                return res
            }
        }

        // Fall back to finding closest by area (optimized)
        val targetArea = targetResolution.width * targetResolution.height
        val bestSize = supportedSizes.minByOrNull {
            kotlin.math.abs((it.width * it.height) - targetArea)
        }

        if (bestSize != null && trySetResolution(camera, bestSize)) {
            Log.d(TAG, "Set closest resolution: ${bestSize.width}x${bestSize.height}")
            return Resolution(bestSize.width, bestSize.height)
        }

        throw IllegalStateException("Could not set any supported resolution")
    }

    /**
     * Fast resolution setting - try MJPEG only first
     */
    private fun trySetResolutionFast(camera: UVCCamera, resolution: Resolution): Boolean {
        return try {
            // Most cameras support MJPEG, try it first for speed
            camera.setPreviewSize(
                resolution.width,
                resolution.height,
                UVCCamera.FRAME_FORMAT_MJPEG,
                1.0f
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Try to set a specific resolution with fallback
     */
    fun trySetResolution(camera: UVCCamera, resolution: Resolution): Boolean {
        // Try MJPEG first (faster)
        if (trySetResolutionFast(camera, resolution)) {
            return true
        }

        // Fall back to YUYV if needed
        return try {
            camera.setPreviewSize(
                resolution.width,
                resolution.height,
                UVCCamera.FRAME_FORMAT_YUYV,
                1.0f
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Try to set a specific resolution from Size object
     */
    private fun trySetResolution(camera: UVCCamera, size: Size): Boolean {
        return trySetResolution(camera, Resolution(size.width, size.height))
    }

    /**
     * Get list of supported resolutions with caching
     */
    fun getSupportedResolutions(camera: UVCCamera): List<Resolution> {
        val cameraHash = camera.hashCode()

        // Use cache if available
        if (cameraHash == lastCameraHash && cachedSupportedSizes != null) {
            return cachedSupportedSizes!!.map { Resolution(it.width, it.height) }
        }

        return try {
            val sizes = camera.supportedSizeList ?: emptyList()
            cachedSupportedSizes = sizes
            lastCameraHash = cameraHash
            sizes.map { Resolution(it.width, it.height) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get supported resolutions", e)
            emptyList()
        }
    }

    /**
     * Quick validation if a resolution is supported
     */
    fun isResolutionSupported(camera: UVCCamera, resolution: Resolution): Boolean {
        val supported = getSupportedResolutions(camera)
        return supported.any {
            it.width == resolution.width && it.height == resolution.height
        }
    }

    /**
     * Clear cache when camera changes
     */
    fun clearCache() {
        cachedSupportedSizes = null
        lastCameraHash = 0
    }
}