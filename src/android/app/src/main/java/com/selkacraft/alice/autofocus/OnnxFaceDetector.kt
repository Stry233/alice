package com.selkacraft.alice.comm.autofocus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * ONNX-based face detector supporting multiple YOLO variants.
 *
 * Supports models from https://huggingface.co/deepghs/yolo-face including:
 * - YOLOv8n-face, YOLOv10n-face, YOLOv11n-face (and larger variants)
 *
 * The detector auto-detects the output format and handles:
 * - Detection-only models (bounding boxes only) - eyes provided by ML Kit
 * - Detection + landmark models (bounding boxes + 5-point landmarks)
 *
 * For detection-only models, ML Kit provides eye positions as a refinement step.
 *
 * Model file should be placed at: assets/yolo-face.onnx
 */
class OnnxFaceDetector(
    private val context: Context
) {
    companion object {
        private const val TAG = "OnnxFaceDetector"

        // Model configuration
        private const val MODEL_FILENAME = "yolo-face.onnx"  // Generic name - user provides model
        private const val INPUT_SIZE = 640  // YOLO standard input size
        private const val CONFIDENCE_THRESHOLD = 0.45f  // Slightly lower for far faces
        private const val IOU_THRESHOLD = 0.45f  // For NMS

        // Output format detection
        private const val OUTPUT_VALUES_DETECTION_ONLY = 5  // x, y, w, h, conf (or x1,y1,x2,y2,conf)
        private const val OUTPUT_VALUES_WITH_CLASS = 6  // + class
        private const val OUTPUT_VALUES_WITH_LANDMARKS = 16  // + class + 10 landmarks
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    // Detected output format
    private var outputValuesPerBox = OUTPUT_VALUES_WITH_CLASS
    private var hasLandmarks = false
    private var outputIsTransposed = false  // Some models output [features, boxes] vs [boxes, features]
    private var usesCornerFormat = false  // x1,y1,x2,y2 vs cx,cy,w,h

    // Input tensor name (varies by model)
    private var inputTensorName = "images"

    // Reusable buffers for efficiency
    private var inputBuffer: FloatBuffer? = null
    private var pixelBuffer: IntArray? = null

    /**
     * Initialize the ONNX runtime and load the model.
     * Call this before using detect().
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "Initializing ONNX face detector...")

            // Create ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load model from assets
            val modelBytes = context.assets.open(MODEL_FILENAME).use { it.readBytes() }

            // Create session options for optimization
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // NNAPI can be enabled for hardware acceleration on supported devices
                // Uncomment if needed: addNnapi()
            }

            // Create inference session
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)

            // Detect input tensor name
            ortSession?.inputNames?.firstOrNull()?.let {
                inputTensorName = it
                Log.d(TAG, "Input tensor name: $inputTensorName")
            }

            // Detect output format by examining output metadata
            detectOutputFormat()

            // Pre-allocate buffers
            val bufferSize = 3 * INPUT_SIZE * INPUT_SIZE
            inputBuffer = FloatBuffer.allocate(bufferSize)
            pixelBuffer = IntArray(INPUT_SIZE * INPUT_SIZE)

            isInitialized = true
            Log.i(TAG, "ONNX face detector initialized: landmarks=$hasLandmarks, " +
                    "transposed=$outputIsTransposed, cornerFormat=$usesCornerFormat")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX face detector", e)
            Result.failure(e)
        }
    }

    /**
     * Detect output format from model metadata
     */
    private fun detectOutputFormat() {
        val session = ortSession ?: return
        val outputInfo = session.outputInfo

        outputInfo.forEach { (name, info) ->
            val shape = info.info.toString()
            Log.d(TAG, "Output '$name': $shape")
        }

        // Get first output shape
        val firstOutput = outputInfo.values.firstOrNull()
        val shapeStr = firstOutput?.info?.toString() ?: ""

        // Parse shape - common formats:
        // [1, 5, 8400] or [1, 8400, 5] for detection-only
        // [1, 6, 8400] or [1, 8400, 6] for detection + class
        // [1, 16, 8400] or [1, 8400, 16] for detection + class + landmarks
        // [1, N, 6] for post-NMS output

        // Try to infer from shape
        when {
            shapeStr.contains("16") -> {
                hasLandmarks = true
                outputValuesPerBox = OUTPUT_VALUES_WITH_LANDMARKS
            }
            shapeStr.contains("6") -> {
                hasLandmarks = false
                outputValuesPerBox = OUTPUT_VALUES_WITH_CLASS
            }
            shapeStr.contains("5") -> {
                hasLandmarks = false
                outputValuesPerBox = OUTPUT_VALUES_DETECTION_ONLY
            }
        }

        // Detect if transposed (features first vs boxes first)
        // If second dim is small (5, 6, 16), it's [batch, boxes, features]
        // If third dim is small, it's [batch, features, boxes]
        // This is a heuristic - may need adjustment for specific models
        outputIsTransposed = shapeStr.matches(Regex(".*\\[1,\\s*(5|6|16|84),\\s*\\d{3,}\\].*"))

        Log.d(TAG, "Detected format: values=$outputValuesPerBox, landmarks=$hasLandmarks, transposed=$outputIsTransposed")
    }

    /**
     * Check if the model file exists in assets
     */
    fun isModelAvailable(): Boolean {
        return try {
            context.assets.open(MODEL_FILENAME).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect faces in the given bitmap.
     * Returns a list of raw detections with bounding boxes (and landmarks if model supports).
     */
    suspend fun detect(bitmap: Bitmap): List<RawFaceDetection> = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.w(TAG, "Detector not initialized, returning empty results")
            return@withContext emptyList()
        }

        try {
            val startTime = System.currentTimeMillis()

            // Preprocess: resize and normalize
            val inputTensor = preprocessImage(bitmap)

            // Run inference
            val outputs = ortSession?.run(mapOf(inputTensorName to inputTensor))
                ?: return@withContext emptyList()

            // Get output tensor
            val outputTensor = outputs[0] as? OnnxTensor
                ?: return@withContext emptyList()

            // Get shape for processing
            val shape = outputTensor.info.shape
            Log.v(TAG, "Output shape: ${shape.contentToString()}")

            val outputData = outputTensor.floatBuffer

            // Post-process based on detected format
            val detections = postprocessOutput(
                outputData,
                shape,
                bitmap.width,
                bitmap.height
            )

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Detection completed: ${detections.size} faces in ${processingTime}ms")

            // Cleanup
            inputTensor.close()
            outputs.close()

            detections

        } catch (e: Exception) {
            Log.e(TAG, "Error during face detection", e)
            emptyList()
        }
    }

    /**
     * Preprocess bitmap for YOLO input.
     * Resizes to INPUT_SIZE x INPUT_SIZE and normalizes to [0, 1].
     */
    private fun preprocessImage(bitmap: Bitmap): OnnxTensor {
        val buffer = inputBuffer ?: FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        buffer.rewind()

        // Resize bitmap to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Get pixels
        val pixels = pixelBuffer ?: IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Convert to float tensor in CHW format (Channel, Height, Width)
        // Normalize to [0, 1]
        val channelSize = INPUT_SIZE * INPUT_SIZE

        // Red channel
        for (i in 0 until channelSize) {
            buffer.put(((pixels[i] shr 16) and 0xFF) / 255f)
        }
        // Green channel
        for (i in 0 until channelSize) {
            buffer.put(((pixels[i] shr 8) and 0xFF) / 255f)
        }
        // Blue channel
        for (i in 0 until channelSize) {
            buffer.put((pixels[i] and 0xFF) / 255f)
        }

        buffer.rewind()

        // Cleanup resized bitmap if it's different from original
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }

        // Create tensor with shape [1, 3, INPUT_SIZE, INPUT_SIZE]
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(ortEnvironment, buffer, shape)
    }

    /**
     * Post-process YOLO output to extract face detections.
     * Handles both transposed and non-transposed output formats.
     */
    private fun postprocessOutput(
        output: FloatBuffer,
        shape: LongArray,
        originalWidth: Int,
        originalHeight: Int
    ): List<RawFaceDetection> {
        output.rewind()

        // Determine dimensions
        val numElements = output.remaining()

        // Infer format from actual output shape
        val (numBoxes, valuesPerBox) = when {
            shape.size == 3 && shape[2] > shape[1] -> {
                // [1, features, boxes] - transposed
                Pair(shape[2].toInt(), shape[1].toInt())
            }
            shape.size == 3 -> {
                // [1, boxes, features]
                Pair(shape[1].toInt(), shape[2].toInt())
            }
            shape.size == 2 -> {
                // [boxes, features]
                Pair(shape[0].toInt(), shape[1].toInt())
            }
            else -> {
                // Fallback: guess based on values per box
                val guessedValuesPerBox = outputValuesPerBox
                Pair(numElements / guessedValuesPerBox, guessedValuesPerBox)
            }
        }

        Log.v(TAG, "Processing $numBoxes boxes with $valuesPerBox values each")

        // Scale factors
        val scaleX = originalWidth.toFloat() / INPUT_SIZE
        val scaleY = originalHeight.toFloat() / INPUT_SIZE

        val rawDetections = mutableListOf<Pair<RawFaceDetection, Float>>()

        // Determine if output is transposed
        val isTransposed = shape.size == 3 && shape[2] > shape[1]

        for (i in 0 until numBoxes) {
            try {
                // Get values based on layout
                val values = if (isTransposed) {
                    // Transposed: read column-wise
                    FloatArray(valuesPerBox) { j ->
                        output.get(j * numBoxes + i)
                    }
                } else {
                    // Normal: read row-wise
                    FloatArray(valuesPerBox) { j ->
                        output.get(i * valuesPerBox + j)
                    }
                }

                // Parse based on format
                val (box, confidence, landmarks) = parseDetection(values, valuesPerBox, scaleX, scaleY, originalWidth, originalHeight)

                // Skip low confidence
                if (confidence < CONFIDENCE_THRESHOLD) continue

                // Skip invalid boxes
                if (box.right <= box.left || box.bottom <= box.top) continue

                val detection = RawFaceDetection(
                    boundingBox = box,
                    confidence = confidence,
                    landmarks = landmarks
                )

                rawDetections.add(detection to confidence)

            } catch (e: Exception) {
                // Skip malformed detections
                continue
            }
        }

        // Apply Non-Maximum Suppression
        val nmsResults = applyNMS(rawDetections, IOU_THRESHOLD)

        // Limit to MAX_TRACKED_FACES
        return nmsResults.take(MAX_TRACKED_FACES)
    }

    /**
     * Parse a single detection from values array
     */
    private fun parseDetection(
        values: FloatArray,
        valuesPerBox: Int,
        scaleX: Float,
        scaleY: Float,
        originalWidth: Int,
        originalHeight: Int
    ): Triple<Rect, Float, List<PointF>?> {

        val (x1, y1, x2, y2, confidence) = when {
            // Standard YOLO format: cx, cy, w, h, conf, ...
            valuesPerBox >= 5 -> {
                val cx = values[0]
                val cy = values[1]
                val w = values[2]
                val h = values[3]

                // Check if it looks like center format or corner format
                val isCornerFormat = w > 2 && h > 2 && cx < w && cy < h

                if (isCornerFormat) {
                    // x1, y1, x2, y2 format
                    arrayOf(
                        (values[0] * scaleX),
                        (values[1] * scaleY),
                        (values[2] * scaleX),
                        (values[3] * scaleY),
                        values[4]
                    )
                } else {
                    // cx, cy, w, h format - convert to corners
                    arrayOf(
                        ((cx - w / 2) * scaleX),
                        ((cy - h / 2) * scaleY),
                        ((cx + w / 2) * scaleX),
                        ((cy + h / 2) * scaleY),
                        values[4]
                    )
                }
            }
            else -> return Triple(Rect(), 0f, null)
        }

        val box = Rect(
            x1.toInt().coerceIn(0, originalWidth),
            y1.toInt().coerceIn(0, originalHeight),
            x2.toInt().coerceIn(0, originalWidth),
            y2.toInt().coerceIn(0, originalHeight)
        )

        // Parse landmarks if present (starting at index 6)
        val landmarks: List<PointF>? = if (valuesPerBox >= 16) {
            (0 until 5).map { lm ->
                PointF(
                    values[6 + lm * 2] * scaleX,
                    values[7 + lm * 2] * scaleY
                )
            }
        } else {
            null
        }

        return Triple(box, confidence, landmarks)
    }

    /**
     * Apply Non-Maximum Suppression to remove overlapping detections.
     */
    private fun applyNMS(
        detections: List<Pair<RawFaceDetection, Float>>,
        iouThreshold: Float
    ): List<RawFaceDetection> {
        if (detections.isEmpty()) return emptyList()

        // Sort by confidence (descending)
        val sorted = detections.sortedByDescending { it.second }
        val selected = mutableListOf<RawFaceDetection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue

            val current = sorted[i].first
            selected.add(current)

            // Suppress overlapping detections
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue

                val other = sorted[j].first
                val iou = calculateIoU(current.boundingBox, other.boundingBox)

                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return selected
    }

    /**
     * Calculate Intersection over Union (IoU) between two rectangles.
     */
    private fun calculateIoU(box1: Rect, box2: Rect): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }

        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea.toFloat() / unionArea else 0f
    }

    /**
     * Check if the model provides landmark detection
     */
    fun hasLandmarkSupport(): Boolean = hasLandmarks

    /**
     * Release resources.
     */
    fun release() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
            ortSession = null
            ortEnvironment = null
            inputBuffer = null
            pixelBuffer = null
            isInitialized = false
            Log.d(TAG, "ONNX face detector released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ONNX face detector", e)
        }
    }

    /**
     * Check if detector is ready to use.
     */
    fun isReady(): Boolean = isInitialized
}
