package com.selkacraft.alice.comm.autofocus

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Backward-compatible wrapper for the autofocus system.
 * Delegates all operations to AutofocusController.
 */
@Deprecated("Use AutofocusController directly for new code")
class AutofocusManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutofocusManager"
    }

    // Delegate to the new controller
    private val controller = AutofocusController(context, scope)

    // Expose state flows for backward compatibility
    val currentMapping: StateFlow<AutofocusMapping?> = controller.state.map { it.mapping }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val isEnabled: StateFlow<Boolean> = controller.state.map { it.isEnabled }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val validationResult: StateFlow<ValidationResult?> = controller.state.map { state ->
        state.mapping?.validate()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val lastFocusPosition: StateFlow<Int?> = controller.state.map { it.currentMotorPosition }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val focusMode: StateFlow<FocusMode> = controller.state.map { it.mode }
        .stateIn(scope, SharingStarted.Eagerly, FocusMode.MANUAL)

    val focusStats: StateFlow<FocusStatistics> = controller.state.map { it.stats }
        .stateIn(scope, SharingStarted.Eagerly, FocusStatistics())

    /**
     * Load a mapping file from URI
     */
    suspend fun loadMappingFromUri(uri: Uri): Result<ValidationResult> {
        return controller.loadMapping(uri).map {
            controller.state.value.mapping?.validate() ?: ValidationResult(
                isValid = false,
                errors = listOf("Failed to load mapping")
            )
        }
    }

    /**
     * Load a mapping from JSON string
     */
    suspend fun loadMappingFromJson(jsonString: String): Result<ValidationResult> {
        return try {
            val mapping = AutofocusMapping.fromJson(jsonString).getOrThrow()
            val tempFile = kotlin.io.path.createTempFile("mapping", ".json").toFile()
            tempFile.writeText(jsonString)
            val uri = Uri.fromFile(tempFile)
            loadMappingFromUri(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load a preset mapping
     */
    suspend fun loadPresetMapping(preset: MappingPreset): Result<ValidationResult> {
        return controller.loadPreset(preset).map {
            controller.state.value.mapping?.validate() ?: ValidationResult(
                isValid = false,
                errors = listOf("Failed to load preset")
            )
        }
    }

    /**
     * Clear the current mapping
     */
    fun clearMapping() {
        controller.clearMapping()
    }

    /**
     * Calculate motor position for given depth
     */
    fun calculateMotorPosition(depthMeters: Float, applySmoothing: Boolean = true): Int? {
        controller.updateConfiguration(enableSmoothing = applySmoothing)
        controller.processDepthData(depthMeters, 1.0f)
        return controller.getMotorPositionCommand()
    }

    /**
     * Set focus mode
     */
    fun setFocusMode(mode: FocusMode) {
        controller.setFocusMode(mode)
    }

    /**
     * Export current mapping to file
     */
    suspend fun exportMapping(fileName: String): Result<File> {
        val mapping = controller.state.value.mapping
            ?: return Result.failure(IllegalStateException("No mapping loaded"))

        return AutofocusMappingManager(context).exportMapping(mapping, fileName)
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        controller.destroy()
    }
}

/**
 * Focus modes
 */
enum class FocusMode {
    MANUAL,           // Manual focus control only
    SINGLE_AUTO,      // Single autofocus (tap to focus)
    CONTINUOUS_AUTO,  // Continuous autofocus
    FACE_TRACKING,    // Face detection based focus (future)
    OBJECT_TRACKING   // Object tracking focus (future)
}

/**
 * Mapping preset types
 */
enum class MappingPreset {
    LINEAR,       // Linear depth to motor mapping
    LOGARITHMIC,  // Logarithmic for better near-field
    PORTRAIT,     // Optimized for portraits
    LANDSCAPE,    // Optimized for landscapes
    MACRO         // Optimized for macro
}

/**
 * Focus operation statistics
 */
data class FocusStatistics(
    val totalFocusOperations: Int = 0,
    val lastDepth: Float = 0f,
    val lastPosition: Int = 0,
    val averageDepth: Float = 0f
)

/**
 * Exception for validation failures
 */
class ValidationException(val validationResult: ValidationResult) :
    Exception("Validation failed: ${validationResult.errors.joinToString(", ")}")