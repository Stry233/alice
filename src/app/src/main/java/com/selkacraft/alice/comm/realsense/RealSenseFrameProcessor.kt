package com.selkacraft.alice.comm.realsense

import android.graphics.Bitmap
import android.util.Log
import com.intel.realsense.librealsense.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Handles frame capture and processing from RealSense pipeline
 */
class RealSenseFrameProcessor(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "RealSenseFrameProcessor"
        /** Frame capture runs at the camera's native frame rate (no artificial delay) */
        private const val CAPTURE_DELAY_MS = 0L
    }

    data class ProcessedFrame(
        val depthMeasurement: DepthMeasurement? = null,
        val colorBitmap: Bitmap? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val depthCalculator = RealSenseDepthCalculator()

    // Frame processing state
    private var captureJob: Job? = null
    private var isCapturing = false
    private var processFrameCounter = 0L
    private var lastBitmapUpdate = 0L

    // Cached frame data with thread-safety
    @Volatile
    private var cachedDepthFrame: ShortArray? = null
    @Volatile
    private var cachedDepthWidth = 0
    @Volatile
    private var cachedDepthHeight = 0

    // Output channel for processed frames
    private val _processedFrames = MutableSharedFlow<ProcessedFrame>()
    val processedFrames: SharedFlow<ProcessedFrame> = _processedFrames.asSharedFlow()

    // Error channel
    private val _errors = MutableSharedFlow<Exception>()
    val errors: SharedFlow<Exception> = _errors.asSharedFlow()

    /**
     * Start capturing frames from pipeline
     */
    fun startCapture(
        connection: RealSenseConnection,
        measurementPosition: StateFlow<Pair<Float, Float>>
    ) {
        if (isCapturing) {
            Log.d(TAG, "Already capturing")
            return
        }

        // Update cached dimensions
        cachedDepthWidth = connection.depthWidth
        cachedDepthHeight = connection.depthHeight

        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting frame capture")
            isCapturing = true
            var errorCount = 0
            var nullFrameCount = 0  // Track consecutive null frames
            var hasReceivedFirstFrame = false  // Track if we've received at least one frame

            while (isActive && isCapturing) {
                try {
                    // Capture and process frame
                    val frames = captureFrame(connection.pipeline)

                    if (frames != null) {
                        // Successful frame capture - reset counters
                        errorCount = 0
                        nullFrameCount = 0
                        hasReceivedFirstFrame = true  // Mark that we've seen at least one frame

                        val processed = processFrames(
                            frames,
                            connection,
                            measurementPosition.value
                        )

                        // Emit processed frame
                        _processedFrames.emit(processed)

                        frames.close()
                    } else {
                        // Null frame (timeout)
                        // Only count null frames AFTER we've received the first successful frame
                        // This avoids disconnecting during the camera's startup/warmup period
                        if (hasReceivedFirstFrame) {
                            nullFrameCount++
                            if (nullFrameCount >= RealSenseParameters.MAX_CONSECUTIVE_ERRORS) {
                                Log.e(TAG, "Too many consecutive null frames ($nullFrameCount), device likely disconnected")
                                _errors.emit(Exception("Device disconnected - no frames received"))
                                break
                            }
                            Log.v(TAG, "Null frame count: $nullFrameCount/${RealSenseParameters.MAX_CONSECUTIVE_ERRORS}")
                        } else {
                            Log.v(TAG, "Waiting for first frame (warmup)")
                        }
                    }

                    // Small delay to prevent tight loop and allow other coroutines to run
                    // but not enough to limit frame rate
                    if (CAPTURE_DELAY_MS > 0) {
                        delay(CAPTURE_DELAY_MS)
                    } else {
                        yield()  // Cooperative cancellation point
                    }

                } catch (e: CancellationException) {
                    Log.d(TAG, "Frame capture cancelled")
                    throw e  // Re-throw to stop the coroutine
                } catch (e: Exception) {
                    if (handleCaptureError(e, errorCount)) {
                        errorCount++
                        if (errorCount >= RealSenseParameters.MAX_CONSECUTIVE_ERRORS) {
                            Log.e(TAG, "Too many consecutive errors, stopping capture")
                            _errors.emit(Exception("Device disconnected"))
                            break
                        }
                    } else {
                        // Non-recoverable error
                        _errors.emit(e)
                        break
                    }
                }
            }

            Log.d(TAG, "Frame capture ended")
            isCapturing = false
        }
    }

    /**
     * Stop capturing frames
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping frame capture")
        isCapturing = false
        captureJob?.cancel()
        captureJob = null
        clearCachedData()
    }

    /**
     * Check if actively capturing
     */
    fun isCapturing(): Boolean = isCapturing

    /**
     * Capture a frame from the pipeline
     */
    private fun captureFrame(pipeline: Pipeline): FrameSet? {
        return try {
            pipeline.waitForFrames(RealSenseParameters.FRAME_WAIT_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.v(TAG, "Frame wait timeout or error: ${e.message}")
            null
        }
    }

    /**
     * Process captured frames
     */
    private suspend fun processFrames(
        frames: FrameSet,
        connection: RealSenseConnection,
        measurementPosition: Pair<Float, Float>
    ): ProcessedFrame = withContext(Dispatchers.Default) {

        processFrameCounter++
        var depthMeasurement: DepthMeasurement? = null
        var outputBitmap: Bitmap? = null

        val depthFrame = frames.first(StreamType.DEPTH)
        var depthBuffer: ByteArray? = null

        if (depthFrame != null) {
            depthBuffer = ByteArray(depthFrame.dataSize)
            depthFrame.getData(depthBuffer)
            depthFrame.close()

            depthMeasurement = processDepthFrame(
                depthBuffer,
                connection,
                measurementPosition
            )
        }

        // Process bitmap periodically
        val now = System.currentTimeMillis()
        if (now - lastBitmapUpdate > RealSenseParameters.BITMAP_UPDATE_INTERVAL_MS) {
            // Try color frame first (if available)
            val colorFrame = frames.first(StreamType.COLOR)
            if (colorFrame != null && connection.colorWidth > 0 && connection.colorHeight > 0) {
                val colorBuffer = ByteArray(colorFrame.dataSize)
                colorFrame.getData(colorBuffer)
                colorFrame.close()

                outputBitmap = processColorFrame(
                    colorBuffer,
                    connection.colorWidth,
                    connection.colorHeight
                )
                lastBitmapUpdate = now
            } else if (depthBuffer != null && connection.depthWidth > 0 && connection.depthHeight > 0) {
                // No color stream - create depth visualization bitmap
                outputBitmap = createDepthVisualizationBitmap(
                    depthBuffer,
                    connection.depthWidth,
                    connection.depthHeight
                )
                lastBitmapUpdate = now
            }
        }

        ProcessedFrame(depthMeasurement, outputBitmap)
    }

    /**
     * Process depth frame data with thread-safe array handling
     */
    private fun processDepthFrame(
        buffer: ByteArray,
        connection: RealSenseConnection,
        measurementPosition: Pair<Float, Float>
    ): DepthMeasurement? {
        // Validate buffer size to prevent out-of-bounds access
        if (buffer.isEmpty() || buffer.size % 2 != 0) {
            Log.w(TAG, "Invalid buffer size: ${buffer.size}, must be even and non-empty")
            return null
        }

        val expectedSize = buffer.size / 2

        // Capture local reference for thread-safety
        var depthArray = cachedDepthFrame

        // Recreate array if size doesn't match or if null
        if (depthArray == null || depthArray.size != expectedSize) {
            depthArray = ShortArray(expectedSize)
            cachedDepthFrame = depthArray
        }

        // Convert byte array to short array with bounds checking
        try {
            for (i in depthArray.indices) {
                val idx = i * 2
                // Double-check bounds to prevent crashes
                if (idx + 1 >= buffer.size) {
                    Log.w(TAG, "Buffer index out of bounds at $idx, stopping conversion")
                    break
                }
                depthArray[i] = ((buffer[idx + 1].toInt() and 0xFF) shl 8 or
                        (buffer[idx].toInt() and 0xFF)).toShort()
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e(TAG, "Array index out of bounds during depth conversion", e)
            return null
        }

        // Calculate depth at measurement position
        val (normX, normY) = measurementPosition
        return try {
            depthCalculator.calculateDepth(
                depthArray,
                cachedDepthWidth,
                cachedDepthHeight,
                normX,
                normY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating depth", e)
            null
        }
    }

    /**
     * Process color frame to create bitmap
     */
    private fun processColorFrame(
        buffer: ByteArray,
        width: Int,
        height: Int
    ): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val pixels = IntArray(width * height)

            // Fast RGB conversion
            var pixelIdx = 0
            var bufferIdx = 0
            while (pixelIdx < pixels.size && bufferIdx + 2 < buffer.size) {
                val r = buffer[bufferIdx++].toInt() and 0xFF
                val g = buffer[bufferIdx++].toInt() and 0xFF
                val b = buffer[bufferIdx++].toInt() and 0xFF

                pixels[pixelIdx] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
                pixelIdx++
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap: ${e.message}")
            null
        }
    }

    /**
     * Create a depth visualization bitmap from depth data (for depth-only mode)
     * Uses a blue-to-red colormap for depth visualization
     */
    private fun createDepthVisualizationBitmap(
        buffer: ByteArray,
        width: Int,
        height: Int
    ): Bitmap? {
        return try {
            if (buffer.size < width * height * 2) {
                Log.w(TAG, "Buffer too small for depth visualization")
                return null
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val pixels = IntArray(width * height)

            // Find min/max for normalization (excluding zeros)
            var minDepth = Int.MAX_VALUE
            var maxDepth = 0
            for (i in 0 until width * height) {
                val idx = i * 2
                if (idx + 1 >= buffer.size) break
                val depth = ((buffer[idx + 1].toInt() and 0xFF) shl 8) or (buffer[idx].toInt() and 0xFF)
                if (depth > 0) {
                    if (depth < minDepth) minDepth = depth
                    if (depth > maxDepth) maxDepth = depth
                }
            }

            // Default range if no valid depths found
            if (minDepth == Int.MAX_VALUE || maxDepth == 0) {
                minDepth = 0
                maxDepth = 10000  // 10 meters in mm
            }

            val range = (maxDepth - minDepth).coerceAtLeast(1)

            // Create colormap visualization
            for (i in 0 until width * height) {
                val idx = i * 2
                if (idx + 1 >= buffer.size) break

                val depth = ((buffer[idx + 1].toInt() and 0xFF) shl 8) or (buffer[idx].toInt() and 0xFF)

                val color = if (depth == 0) {
                    // No depth data - dark gray
                    0xFF202020.toInt()
                } else {
                    // Normalize to 0-1 range
                    val normalized = ((depth - minDepth).toFloat() / range).coerceIn(0f, 1f)

                    // Blue (close) to Red (far) colormap
                    val r: Int
                    val g: Int
                    val b: Int

                    when {
                        normalized < 0.25f -> {
                            // Blue to Cyan
                            val t = normalized / 0.25f
                            r = 0
                            g = (255 * t).toInt()
                            b = 255
                        }
                        normalized < 0.5f -> {
                            // Cyan to Green
                            val t = (normalized - 0.25f) / 0.25f
                            r = 0
                            g = 255
                            b = (255 * (1 - t)).toInt()
                        }
                        normalized < 0.75f -> {
                            // Green to Yellow
                            val t = (normalized - 0.5f) / 0.25f
                            r = (255 * t).toInt()
                            g = 255
                            b = 0
                        }
                        else -> {
                            // Yellow to Red
                            val t = (normalized - 0.75f) / 0.25f
                            r = 255
                            g = (255 * (1 - t)).toInt()
                            b = 0
                        }
                    }

                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }

                pixels[i] = color
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error creating depth visualization: ${e.message}")
            null
        }
    }

    /**
     * Handle capture errors
     */
    private fun handleCaptureError(error: Exception, currentErrorCount: Int): Boolean {
        val message = error.message ?: ""

        // Check if this is a recoverable error
        return when {
            message.contains("Frame didn't arrive") -> {
                Log.d(TAG, "Frame timeout (error ${currentErrorCount + 1})")
                true
            }
            message.contains("device disconnected") -> {
                Log.d(TAG, "Device disconnected (error ${currentErrorCount + 1})")
                true
            }
            else -> {
                Log.e(TAG, "Capture error: $message", error)
                false
            }
        }
    }

    /**
     * Clear cached data
     */
    private fun clearCachedData() {
        cachedDepthFrame = null
        cachedDepthWidth = 0
        cachedDepthHeight = 0
        processFrameCounter = 0
        lastBitmapUpdate = 0
        depthCalculator.reset()
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        stopCapture()
        clearCachedData()
    }
}