package com.selkacraft.alice.comm.realsense

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.intel.realsense.librealsense.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Handles RealSense pipeline creation, configuration, and lifecycle
 */
class RealSensePipelineHandler(
    private val context: Context
) {
    companion object {
        private const val TAG = "RealSensePipelineHandler"

        // Global RsContext management
        private var globalRsContext: RsContext? = null
        private var isRsContextInitialized = false
        private val contextMutex = Mutex()
    }

    private var activePipeline: Pipeline? = null
    private var isPipelineRunning = false
    private val pipelineMutex = Mutex()

    /**
     * Initialize RealSense context if needed
     */
    suspend fun initializeContext(): Boolean = withContext(Dispatchers.IO) {
        contextMutex.withLock {
            if (!isRsContextInitialized) {
                try {
                    withContext(Dispatchers.Main) {
                        RsContext.init(context)
                    }
                    isRsContextInitialized = true
                    Log.d(TAG, "RsContext initialized")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize RsContext", e)
                    return@withContext false
                }
            }

            // Create new context if needed
            if (globalRsContext == null) {
                globalRsContext = RsContext()
                Log.d(TAG, "Created new RsContext")
            }

            true
        }
    }

    /**
     * Create and start a RealSense pipeline with cancellation support
     */
    suspend fun createPipeline(
        device: UsbDevice,
        config: RealSenseStreamConfig
    ): RealSenseConnection? = withContext(Dispatchers.IO) {
        pipelineMutex.withLock {
            try {
                // Check for cancellation before heavy operations
                coroutineContext.ensureActive()

                // Ensure context is initialized
                if (!initializeContext()) {
                    Log.e(TAG, "Failed to initialize context")
                    return@withContext null
                }

                // Check for cancellation
                coroutineContext.ensureActive()

                // Clean up any existing pipeline
                cleanupPipelineInternal()

                val rsContext = globalRsContext ?: run {
                    Log.e(TAG, "RsContext is null")
                    return@withContext null
                }

                // Wait for device enumeration with cancellation check
                delay(RealSenseParameters.DEVICE_ENUMERATION_DELAY_MS)
                coroutineContext.ensureActive()

                // Query devices with retry logic
                val rsDevice = queryDeviceWithRetry(rsContext) ?: run {
                    Log.e(TAG, "No RealSense devices found")
                    return@withContext null
                }

                // Check for cancellation
                coroutineContext.ensureActive()

                // Get depth scale and serial number
                val depthScale = getDepthScale(rsDevice)
                val serialNumber = getDeviceSerialNumber(rsDevice)
                Log.d(TAG, "Device serial number: $serialNumber")

                // Check for cancellation before starting pipeline
                coroutineContext.ensureActive()

                // Try to configure and start pipeline with fallback chain
                val result = configurePipelineWithFallbacks(
                    config,
                    depthScale,
                    serialNumber
                )

                if (result != null) {
                    activePipeline = result.first
                    isPipelineRunning = true
                    Log.d(TAG, "Pipeline created successfully")
                }

                result?.second

            } catch (e: CancellationException) {
                Log.d(TAG, "Pipeline creation cancelled")
                cleanupPipelineInternal()
                throw e  // Re-throw to propagate cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create pipeline", e)
                cleanupPipelineInternal()
                null
            }
        }
    }

    /**
     * Stop the active pipeline
     */
    suspend fun stopPipeline() = withContext(Dispatchers.IO) {
        pipelineMutex.withLock {
            cleanupPipelineInternal()
        }
    }

    /**
     * Check if pipeline is running
     */
    fun isPipelineActive(): Boolean = isPipelineRunning

    /**
     * Get the active pipeline for frame capture
     */
    fun getActivePipeline(): Pipeline? = activePipeline

    /**
     * Clean up all resources
     */
    suspend fun destroy() {
        stopPipeline()

        contextMutex.withLock {
            // Note: We keep the global context for potential reuse
            // It will be cleaned up when the app terminates
            Log.d(TAG, "Pipeline handler destroyed (context kept for reuse)")
        }
    }

    /**
     * Query device with retry logic
     */
    private suspend fun queryDeviceWithRetry(rsContext: RsContext): Device? {
        var retries = 0
        val maxRetries = 3

        while (retries < maxRetries) {
            val devices = rsContext.queryDevices()
            val deviceCount = devices?.deviceCount ?: 0

            if (deviceCount > 0) {
                return devices?.createDevice(0)
            }

            Log.d(TAG, "No devices found on attempt ${retries + 1}, waiting...")
            delay(200)
            retries++
        }

        return null
    }

    /**
     * Get depth scale from device
     */
    private fun getDepthScale(device: Device): Float {
        return try {
            val sensors = device.querySensors()
            if (sensors != null && sensors.isNotEmpty()) {
                val depthSensor = sensors[0]
                depthSensor?.getValue(Option.DEPTH_UNITS) ?: 0.001f
            } else {
                0.001f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get depth scale: ${e.message}")
            0.001f // Default 1mm
        }
    }

    /**
     * Get device serial number for explicit device targeting
     */
    private fun getDeviceSerialNumber(device: Device): String? {
        return try {
            device.getInfo(CameraInfo.SERIAL_NUMBER)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get device serial number: ${e.message}")
            null
        }
    }

    /**
     * Fallback configuration for pipeline startup
     * Each entry represents a progressively more conservative configuration
     */
    private data class FallbackConfig(
        val width: Int,
        val height: Int,
        val fps: Int,
        val depthOnly: Boolean,
        val description: String
    )

    /**
     * Get fallback configurations to try, starting from the requested config
     * and progressively becoming more conservative.
     * Includes standard D435 supported resolutions for maximum compatibility.
     */
    private fun getFallbackConfigs(streamConfig: RealSenseStreamConfig): List<FallbackConfig> {
        val configs = mutableListOf<FallbackConfig>()

        // Requested config with both streams
        if (streamConfig.enableColor && streamConfig.enableDepth) {
            configs.add(FallbackConfig(
                streamConfig.depthWidth, streamConfig.depthHeight, streamConfig.fps,
                depthOnly = false, description = "both streams ${streamConfig.depthWidth}x${streamConfig.depthHeight} @ ${streamConfig.fps}fps"
            ))

            // Try 30fps if originally requested higher
            if (streamConfig.fps > 30) {
                configs.add(FallbackConfig(
                    streamConfig.depthWidth, streamConfig.depthHeight, 30,
                    depthOnly = false, description = "both streams ${streamConfig.depthWidth}x${streamConfig.depthHeight} @ 30fps"
                ))
            }
        }

        // Depth-only fallbacks (more likely to succeed due to lower bandwidth)
        if (streamConfig.enableDepth) {
            // Original resolution at original fps
            configs.add(FallbackConfig(
                streamConfig.depthWidth, streamConfig.depthHeight, streamConfig.fps,
                depthOnly = true, description = "depth only ${streamConfig.depthWidth}x${streamConfig.depthHeight} @ ${streamConfig.fps}fps"
            ))

            // 30fps depth-only at original resolution
            if (streamConfig.fps > 30) {
                configs.add(FallbackConfig(
                    streamConfig.depthWidth, streamConfig.depthHeight, 30,
                    depthOnly = true, description = "depth only ${streamConfig.depthWidth}x${streamConfig.depthHeight} @ 30fps"
                ))
            }

            // Standard D435 resolutions - these are known to work
            // 640x480 @ 30fps - very common supported mode
            configs.add(FallbackConfig(
                640, 480, 30,
                depthOnly = true, description = "depth only 640x480 @ 30fps (standard)"
            ))

            // 640x480 @ 15fps - lower bandwidth
            configs.add(FallbackConfig(
                640, 480, 15,
                depthOnly = true, description = "depth only 640x480 @ 15fps"
            ))

            // 640x480 @ 6fps - minimum bandwidth
            configs.add(FallbackConfig(
                640, 480, 6,
                depthOnly = true, description = "depth only 640x480 @ 6fps (minimum)"
            ))

            // 848x480 @ 30fps - another standard D435 mode
            configs.add(FallbackConfig(
                848, 480, 30,
                depthOnly = true, description = "depth only 848x480 @ 30fps (standard)"
            ))

            // 480x270 @ 30fps - low resolution fallback
            configs.add(FallbackConfig(
                480, 270, 30,
                depthOnly = true, description = "depth only 480x270 @ 30fps"
            ))

            // 424x240 @ 30fps
            configs.add(FallbackConfig(
                424, 240, 30,
                depthOnly = true, description = "depth only 424x240 @ 30fps"
            ))

            // 424x240 @ 6fps - ultimate fallback
            configs.add(FallbackConfig(
                424, 240, 6,
                depthOnly = true, description = "depth only 424x240 @ 6fps (ultimate fallback)"
            ))
        }

        return configs.distinctBy { "${it.width}x${it.height}@${it.fps}-${it.depthOnly}" }
    }

    /**
     * Configure and start pipeline with fallback chain.
     * Creates a fresh Pipeline for each attempt to avoid state corruption.
     * Returns Pair of (Pipeline, RealSenseConnection) on success, null on failure.
     */
    private suspend fun configurePipelineWithFallbacks(
        streamConfig: RealSenseStreamConfig,
        depthScale: Float,
        serialNumber: String?
    ): Pair<Pipeline, RealSenseConnection>? {
        val fallbackConfigs = getFallbackConfigs(streamConfig)

        for ((index, fallback) in fallbackConfigs.withIndex()) {
            // Check for cancellation before each attempt
            coroutineContext.ensureActive()

            Log.d(TAG, "Attempting pipeline config ${index + 1}/${fallbackConfigs.size}: ${fallback.description}")

            // Create fresh Pipeline and Config for each attempt
            // This is crucial - reusing a failed Pipeline can cause issues
            val pipeline = Pipeline()
            val attemptConfig = Config()

            try {
                // Enable specific device if we have serial number
                if (serialNumber != null) {
                    attemptConfig.enableDevice(serialNumber)
                    Log.d(TAG, "Enabled device with serial: $serialNumber")
                }

                // Configure streams based on fallback
                if (!fallback.depthOnly && streamConfig.enableColor) {
                    attemptConfig.enableStream(
                        StreamType.COLOR, -1,
                        fallback.width,
                        fallback.height,
                        StreamFormat.RGB8,
                        fallback.fps
                    )
                }

                attemptConfig.enableStream(
                    StreamType.DEPTH, -1,
                    fallback.width,
                    fallback.height,
                    StreamFormat.Z16,
                    fallback.fps
                )

                // Check for cancellation before starting
                coroutineContext.ensureActive()

                // Try to start pipeline
                pipeline.start(attemptConfig)
                Log.d(TAG, "Pipeline started successfully with: ${fallback.description}")

                // Wait for pipeline to stabilize
                delay(RealSenseParameters.PIPELINE_STABILIZATION_DELAY_MS)
                coroutineContext.ensureActive()

                val connection = RealSenseConnection(
                    pipeline = pipeline,
                    colorWidth = if (fallback.depthOnly) 0 else fallback.width,
                    colorHeight = if (fallback.depthOnly) 0 else fallback.height,
                    depthWidth = fallback.width,
                    depthHeight = fallback.height,
                    depthScale = depthScale
                )

                return Pair(pipeline, connection)

            } catch (e: CancellationException) {
                Log.d(TAG, "Pipeline configuration cancelled")
                // Clean up this attempt's pipeline
                try { pipeline.close() } catch (_: Exception) {}
                throw e  // Re-throw to propagate cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Config failed (${fallback.description}): ${e.message}")

                // Clean up this attempt's pipeline before trying next config
                try { pipeline.close() } catch (_: Exception) {}

                // Brief delay before next attempt to let USB settle
                if (index < fallbackConfigs.size - 1) {
                    delay(300)
                }

                // Continue to next fallback
            }
        }

        Log.e(TAG, "All ${fallbackConfigs.size} pipeline configurations failed")
        return null
    }

    /**
     * Internal pipeline cleanup with robust error handling
     */
    private fun cleanupPipelineInternal() {
        Log.d(TAG, "Cleaning up pipeline (running: $isPipelineRunning)")

        val pipeline = activePipeline
        if (pipeline == null) {
            Log.d(TAG, "No active pipeline to clean up")
            isPipelineRunning = false
            return
        }

        // Stop pipeline with retry logic for robustness
        if (isPipelineRunning) {
            var stopAttempts = 0
            while (stopAttempts < 2) {
                try {
                    pipeline.stop()
                    Log.d(TAG, "Pipeline stopped successfully")
                    break
                } catch (e: Exception) {
                    stopAttempts++
                    if (stopAttempts == 1) {
                        // First attempt failed, brief wait before retry
                        Thread.sleep(100)
                        Log.d(TAG, "Retrying pipeline stop after first failure")
                    } else {
                        Log.w(TAG, "Pipeline stop failed after retries: ${e.message}")
                    }
                }
            }
        }

        // Close pipeline with error handling
        try {
            pipeline.close()
            Log.d(TAG, "Pipeline closed successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Pipeline close failed (may already be closed): ${e.message}")
            // Not critical if close fails - pipeline will be garbage collected
        }

        // Always clear state, even if cleanup partially failed
        activePipeline = null
        isPipelineRunning = false
        Log.d(TAG, "Pipeline cleanup complete")
    }
}