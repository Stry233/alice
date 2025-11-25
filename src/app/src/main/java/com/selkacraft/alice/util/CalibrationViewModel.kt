package com.selkacraft.alice.util

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selkacraft.alice.autofocus.CalibrationPoint
import com.selkacraft.alice.comm.autofocus.AutofocusMapping
import com.selkacraft.alice.comm.autofocus.MappingMetadata
import com.selkacraft.alice.comm.autofocus.MappingPoint
import com.selkacraft.alice.comm.core.ConnectionState
import com.selkacraft.alice.util.CameraViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for the calibration functionality integrated into Alice
 *
 * Unlike the standalone calibrator, this reuses Alice's existing device managers
 * through the CameraViewModel instead of creating its own.
 */
class CalibrationViewModel(
    application: Application,
    private val cameraViewModel: CameraViewModel
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "CalibrationViewModel"
        private const val MIN_POINTS_REQUIRED = 3
        private const val EXPORT_FOLDER = "AliceCalibration"
    }

    // Access to Alice's existing device managers through CameraViewModel
    val motorManager = cameraViewModel.motorControlManager
    val realSenseManager = cameraViewModel.realSenseManager
    val uvcCameraManager = cameraViewModel.uvcCameraManager

    // Connection states from Alice's managers
    val motorConnectionState: StateFlow<ConnectionState> = cameraViewModel.motorConnectionState
    val realSenseConnectionState: StateFlow<ConnectionState> = cameraViewModel.realSenseConnectionState
    val cameraConnectionState: StateFlow<ConnectionState> = cameraViewModel.cameraConnectionState

    // Device data streams from Alice's managers
    val motorPosition: StateFlow<Int> = cameraViewModel.motorPosition
    val depthValue: StateFlow<Float> = cameraViewModel.realSenseCenterDepth
    val depthConfidence: StateFlow<Float> = cameraViewModel.realSenseDepthConfidence
    val videoAspectRatio: StateFlow<Float?> = cameraViewModel.videoAspectRatio

    // Calibration data
    private val _calibrationPoints = MutableStateFlow<List<CalibrationPoint>>(emptyList())
    val calibrationPoints: StateFlow<List<CalibrationPoint>> = _calibrationPoints.asStateFlow()

    // UI state
    private val _isTestMode = MutableStateFlow(false)
    val isTestMode: StateFlow<Boolean> = _isTestMode.asStateFlow()

    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    // Computed states
    val canExport: StateFlow<Boolean> = calibrationPoints.map { points ->
        points.size >= MIN_POINTS_REQUIRED
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val currentMapping: StateFlow<AutofocusMapping?> = calibrationPoints.map { points ->
        if (points.size >= MIN_POINTS_REQUIRED) {
            createMappingFromPoints(points)
        } else null
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        Log.i(TAG, "Initializing CalibrationViewModel (integrated with Alice)")

        // Setup monitoring
        setupMonitoring()
    }

    private fun setupMonitoring() {
        // Monitor connection states
        viewModelScope.launch {
            motorConnectionState.collect { state ->
                Log.d(TAG, "Motor connection state: $state")
            }
        }

        viewModelScope.launch {
            realSenseConnectionState.collect { state ->
                Log.d(TAG, "RealSense connection state: $state")
            }
        }
    }

    /**
     * Record a calibration point at current depth and motor position
     */
    fun recordCalibrationPoint() {
        val currentDepth = depthValue.value
        val currentMotor = motorPosition.value
        val confidence = depthConfidence.value

        // Validate depth
        if (currentDepth <= 0) {
            Log.w(TAG, "Cannot record: No depth reading")
            return
        }

        if (currentDepth > 10.0f) {
            Log.w(TAG, "Cannot record: Depth out of range: ${currentDepth}m (max 10m)")
            return
        }

        // Check confidence threshold
        if (confidence < 0.5f) {
            Log.w(TAG, "Cannot record: Low confidence: ${(confidence * 100).toInt()}% (minimum 50% required)")
            return
        }

        // Check for duplicate depth values
        val existingPoints = _calibrationPoints.value
        val similarPoint = existingPoints.find { kotlin.math.abs(it.depth - currentDepth) < 0.05f }

        if (similarPoint != null) {
            // Update existing point with better confidence
            if (confidence > similarPoint.confidence) {
                Log.i(TAG, "Updating existing point at depth ${currentDepth}m with better confidence")
                _calibrationPoints.value = existingPoints.map { point ->
                    if (point.id == similarPoint.id) {
                        point.copy(
                            motorPosition = currentMotor,
                            confidence = confidence,
                            timestamp = System.currentTimeMillis()
                        )
                    } else point
                }
            } else {
                Log.w(TAG, "Point at similar depth already exists with better confidence")
            }
            return
        }

        // Add new point
        val newPoint = CalibrationPoint(
            depth = currentDepth,
            motorPosition = currentMotor,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )

        _calibrationPoints.value = (existingPoints + newPoint).sortedBy { it.depth }
        Log.i(TAG, "Recorded calibration point #${existingPoints.size + 1}: depth=${String.format("%.3f", currentDepth)}m, motor=$currentMotor, confidence=${(confidence * 100).toInt()}%")
    }

    /**
     * Delete a calibration point
     */
    fun deleteCalibrationPoint(point: CalibrationPoint) {
        _calibrationPoints.value = _calibrationPoints.value.filter { it != point }
        Log.i(TAG, "Deleted calibration point at depth=${point.depth}m")
    }

    /**
     * Update an existing calibration point with new values
     */
    fun updateCalibrationPoint(pointId: String, newDepth: Float, newMotorPosition: Int) {
        // Validate new values
        if (newDepth <= 0 || newDepth > 10.0f) {
            Log.w(TAG, "Cannot update: Depth out of range: ${newDepth}m")
            return
        }
        if (newMotorPosition < 0 || newMotorPosition > 4095) {
            Log.w(TAG, "Cannot update: Motor position out of range: $newMotorPosition")
            return
        }

        // Check if the new depth would conflict with another point
        val existingPoints = _calibrationPoints.value
        val conflictingPoint = existingPoints.find {
            it.id != pointId && kotlin.math.abs(it.depth - newDepth) < 0.05f
        }

        if (conflictingPoint != null) {
            Log.w(TAG, "Cannot update: Another point exists at similar depth")
            return
        }

        _calibrationPoints.value = existingPoints.map { point ->
            if (point.id == pointId) {
                point.copy(
                    depth = newDepth,
                    motorPosition = newMotorPosition,
                    timestamp = System.currentTimeMillis()
                )
            } else point
        }.sortedBy { it.depth }

        Log.i(TAG, "Updated calibration point: depth=${String.format("%.3f", newDepth)}m, motor=$newMotorPosition")
    }

    /**
     * Get a calibration point by ID
     */
    fun getCalibrationPointById(pointId: String): CalibrationPoint? {
        return _calibrationPoints.value.find { it.id == pointId }
    }

    /**
     * Clear all calibration points
     */
    fun clearAllPoints() {
        _calibrationPoints.value = emptyList()
        _isTestMode.value = false
        Log.i(TAG, "Cleared all calibration points")
    }

    /**
     * Toggle test mode
     */
    fun toggleTestMode() {
        val newMode = !_isTestMode.value
        _isTestMode.value = newMode

        if (newMode && currentMapping.value != null) {
            Log.i(TAG, "Entering test mode with ${calibrationPoints.value.size} points")
            startTestMode()
        } else {
            Log.i(TAG, "Exiting test mode")
            stopTestMode()
        }
    }

    /**
     * Set motor position (delegates to Alice's motor manager)
     */
    fun setMotorPosition(position: Int) {
        cameraViewModel.setMotorPosition(position)
    }

    /**
     * Set RealSense measurement position (delegates to Alice's RealSense manager)
     */
    fun setMeasurementPosition(x: Float, y: Float) {
        cameraViewModel.setRealSenseMeasurementPosition(x, y)
    }

    /**
     * Export calibration to file
     */
    fun exportCalibration(
        name: String,
        description: String,
        cameraModel: String = "",
        lensModel: String = ""
    ) {
        viewModelScope.launch {
            try {
                _exportStatus.value = ExportStatus.Exporting

                // Generate a safer filename
                val safeFileName = generateSafeFileName(name)

                val mapping = createMappingFromPoints(
                    _calibrationPoints.value,
                    name,
                    description,
                    cameraModel,
                    lensModel
                )

                val uri = saveToFile(mapping, safeFileName)
                _exportStatus.value = ExportStatus.Success(uri)

                Log.i(TAG, "Calibration exported successfully: $name")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export calibration", e)
                _exportStatus.value = ExportStatus.Error(e.message ?: "Export failed")
            }
        }
    }

    /**
     * Generate a safe filename from user input
     */
    private fun generateSafeFileName(name: String): String {
        // Remove or replace invalid characters for filenames
        val safeName = name
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(100) // Limit length

        // Add timestamp to ensure uniqueness
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        return "${safeName}_$timestamp.json"
    }

    /**
     * Reset export status
     */
    fun resetExportStatus() {
        _exportStatus.value = ExportStatus.Idle
    }

    private fun createMappingFromPoints(
        points: List<CalibrationPoint>,
        name: String = "Custom Calibration",
        description: String = "Created with Alice Calibration Tool",
        cameraModel: String = "",
        lensModel: String = ""
    ): AutofocusMapping {
        val mappingPoints = points.map { calibrationPoint ->
            MappingPoint(
                depth = calibrationPoint.depth,
                motorPosition = calibrationPoint.motorPosition,
                confidence = calibrationPoint.confidence
            )
        }

        return AutofocusMapping(
            name = name,
            description = description,
            mappingPoints = mappingPoints,
            metadata = MappingMetadata(
                cameraModel = cameraModel,
                lensModel = lensModel,
                calibrationMethod = "manual",
                notes = "Calibrated on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun saveToFile(mapping: AutofocusMapping, fileName: String): Uri {
        val context = getApplication<Application>()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$EXPORT_FOLDER")
            }

            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                ?: throw IllegalStateException("Failed to create file")

            resolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(mapping.toJson())
                }
            }

            uri
        } else {
            // Use external storage for older versions
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val exportDir = java.io.File(documentsDir, EXPORT_FOLDER)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = java.io.File(exportDir, fileName)
            file.writeText(mapping.toJson())
            Uri.fromFile(file)
        }
    }

    private fun startTestMode() {
        viewModelScope.launch {
            combine(
                depthValue,
                currentMapping
            ) { depth, mapping ->
                if (mapping != null && depth > 0) {
                    mapping.getMotorPosition(depth)
                } else null
            }.collect { targetPosition ->
                if (_isTestMode.value && targetPosition != null) {
                    // Auto-adjust motor to match depth
                    setMotorPosition(targetPosition)
                }
            }
        }
    }

    private fun stopTestMode() {
        // Test mode will stop automatically when _isTestMode becomes false
    }

    sealed class ExportStatus {
        object Idle : ExportStatus()
        object Exporting : ExportStatus()
        data class Success(val uri: Uri) : ExportStatus()
        data class Error(val message: String) : ExportStatus()
    }
}
