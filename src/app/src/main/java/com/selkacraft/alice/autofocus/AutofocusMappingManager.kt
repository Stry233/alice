package com.selkacraft.alice.comm.autofocus

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages autofocus mapping files - loading, saving, and creating presets.
 * This class has a single responsibility: mapping file management.
 */
class AutofocusMappingManager(private val context: Context) {
    companion object {
        private const val TAG = "AutofocusMappingManager"
        private const val MAPPING_DIR = "autofocus_mappings"
        private const val CURRENT_MAPPING_FILE = "current_mapping.json"
    }

    private val mappingDirectory: File by lazy {
        File(context.filesDir, MAPPING_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Load mapping from a content URI.
     */
    suspend fun loadFromUri(uri: Uri, contentResolver: ContentResolver): Result<AutofocusMapping> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to load mapping from URI: $uri")

                val inputStream = contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Cannot open URI: $uri")
                    )

                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                Log.d(TAG, "Read JSON string of length: ${jsonString.length}")
                Log.v(TAG, "JSON content (first 500 chars): ${jsonString.take(500)}")

                val mappingResult = AutofocusMapping.fromJson(jsonString)

                if (mappingResult.isFailure) {
                    val error = mappingResult.exceptionOrNull()
                    Log.e(TAG, "Failed to parse JSON: ${error?.message}", error)
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid JSON format: ${error?.message}", error)
                    )
                }

                val mapping = mappingResult.getOrThrow()

                // Validate the mapping
                val validation = mapping.validate()
                if (!validation.isValid) {
                    Log.e(TAG, "Mapping validation failed: ${validation.errors.joinToString("; ")}")
                    if (validation.warnings.isNotEmpty()) {
                        Log.w(TAG, "Mapping warnings: ${validation.warnings.joinToString("; ")}")
                    }
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Mapping validation failed: ${validation.errors.joinToString("; ")}"
                        )
                    )
                }

                // Log warnings even if valid
                if (validation.warnings.isNotEmpty()) {
                    Log.w(TAG, "Mapping loaded with warnings: ${validation.warnings.joinToString("; ")}")
                }

                Log.i(TAG, "Successfully loaded mapping: ${mapping.name} with ${mapping.mappingPoints.size} points")
                Result.success(mapping)

            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading mapping from URI", e)
                Result.failure(e)
            }
        }

    /**
     * Save mapping for persistence
     */
    suspend fun saveMapping(mapping: AutofocusMapping): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(mappingDirectory, CURRENT_MAPPING_FILE)
                mapping.saveToFile(file)
                Log.d(TAG, "Mapping saved to: ${file.absolutePath}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save mapping", e)
                Result.failure(e)
            }
        }

    /**
     * Load previously saved mapping
     */
    suspend fun loadSavedMapping(): AutofocusMapping? =
        withContext(Dispatchers.IO) {
            try {
                val savedFile = File(mappingDirectory, CURRENT_MAPPING_FILE)
                if (savedFile.exists()) {
                    val result = AutofocusMapping.fromFile(savedFile)
                    if (result.isSuccess) {
                        val mapping = result.getOrThrow()
                        val validation = mapping.validate()
                        if (validation.isValid) {
                            Log.i(TAG, "Loaded saved mapping: ${mapping.name}")
                            return@withContext mapping
                        } else {
                            Log.e(TAG, "Saved mapping validation failed: ${validation.errors.joinToString("; ")}")
                        }
                    } else {
                        Log.e(TAG, "Failed to parse saved mapping", result.exceptionOrNull())
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load saved mapping", e)
                null
            }
        }

    /**
     * Clear saved mapping
     */
    fun clearSavedMapping() {
        val savedFile = File(mappingDirectory, CURRENT_MAPPING_FILE)
        if (savedFile.exists()) {
            savedFile.delete()
            Log.d(TAG, "Saved mapping cleared")
        }
    }

    /**
     * Create a preset mapping
     */
    fun createPreset(preset: MappingPreset): AutofocusMapping {
        return when (preset) {
            MappingPreset.LINEAR -> createLinearMapping()
            MappingPreset.LOGARITHMIC -> createLogarithmicMapping()
            MappingPreset.PORTRAIT -> createPortraitMapping()
            MappingPreset.LANDSCAPE -> createLandscapeMapping()
            MappingPreset.MACRO -> createMacroMapping()
        }
    }

    /**
     * Get available mapping files
     */
    suspend fun getAvailableMappings(): List<MappingFileInfo> =
        withContext(Dispatchers.IO) {
            val files = mappingDirectory.listFiles { file ->
                file.extension == "json" && file.name != CURRENT_MAPPING_FILE
            } ?: emptyArray()

            files.map { file ->
                try {
                    val mapping = AutofocusMapping.fromFile(file).getOrNull()
                    MappingFileInfo(
                        file = file,
                        name = mapping?.name ?: file.nameWithoutExtension,
                        description = mapping?.description ?: "",
                        createdAt = mapping?.createdAt ?: file.lastModified(),
                        pointCount = mapping?.mappingPoints?.size ?: 0,
                        isValid = mapping != null
                    )
                } catch (e: Exception) {
                    MappingFileInfo(
                        file = file,
                        name = file.nameWithoutExtension,
                        description = "Error loading file",
                        createdAt = file.lastModified(),
                        pointCount = 0,
                        isValid = false
                    )
                }
            }
        }

    /**
     * Export mapping to file
     */
    suspend fun exportMapping(mapping: AutofocusMapping, fileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(mappingDirectory, "$fileName.json")
                mapping.saveToFile(file)
                Log.i(TAG, "Mapping exported to: ${file.absolutePath}")
                Result.success(file)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export mapping", e)
                Result.failure(e)
            }
        }

    // Preset creation methods - Updated to use new metadata structure

    private fun createLinearMapping(): AutofocusMapping {
        return AutofocusMapping(
            name = "Linear Mapping",
            description = "Linear mapping from 0.2m to 5m",
            mappingPoints = listOf(
                MappingPoint(0.2f, 0),
                MappingPoint(0.5f, 1024),
                MappingPoint(1.0f, 2048),
                MappingPoint(2.0f, 3072),
                MappingPoint(5.0f, 4095)
            ),
            metadata = MappingMetadata(
                calibrationMethod = "linear",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun createLogarithmicMapping(): AutofocusMapping {
        val points = mutableListOf<MappingPoint>()
        val minDepth = 0.2f
        val maxDepth = 9.5f  // Slightly less than 10 to avoid floating point issues
        val numPoints = 10

        for (i in 0 until numPoints) {
            val t = i.toFloat() / (numPoints - 1)
            // Calculate depth with logarithmic distribution
            val depth = minDepth * kotlin.math.exp(t * kotlin.math.ln(maxDepth / minDepth))
            // Ensure depth is within valid range (floating point safety)
            val clampedDepth = depth.coerceIn(0.01f, 10.0f)
            val position = (t * 4095).toInt()
            points.add(MappingPoint(clampedDepth, position))
        }

        return AutofocusMapping(
            name = "Logarithmic Mapping",
            description = "Logarithmic distribution for better near-field resolution",
            mappingPoints = points,
            metadata = MappingMetadata(
                calibrationMethod = "logarithmic",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun createPortraitMapping(): AutofocusMapping {
        return AutofocusMapping(
            name = "Portrait Mapping",
            description = "Optimized for portrait photography (0.5m - 2m)",
            mappingPoints = listOf(
                MappingPoint(0.3f, 0),
                MappingPoint(0.5f, 1024),
                MappingPoint(0.8f, 2048),
                MappingPoint(1.2f, 2560),
                MappingPoint(1.5f, 3072),
                MappingPoint(2.0f, 3584),
                MappingPoint(3.0f, 4095)
            ),
            metadata = MappingMetadata(
                calibrationMethod = "portrait",
                environmentType = "indoor",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun createLandscapeMapping(): AutofocusMapping {
        return AutofocusMapping(
            name = "Landscape Mapping",
            description = "Optimized for landscape photography (2m - 10m)",
            mappingPoints = listOf(
                MappingPoint(0.5f, 0),
                MappingPoint(2.0f, 512),
                MappingPoint(4.0f, 1536),
                MappingPoint(6.0f, 2560),
                MappingPoint(8.0f, 3584),
                MappingPoint(10.0f, 4095)  // Max depth is 10m per validation
            ),
            metadata = MappingMetadata(
                calibrationMethod = "landscape",
                environmentType = "outdoor",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun createMacroMapping(): AutofocusMapping {
        return AutofocusMapping(
            name = "Macro Mapping",
            description = "Optimized for macro photography (5cm - 50cm)",
            mappingPoints = listOf(
                MappingPoint(0.05f, 0),
                MappingPoint(0.10f, 1024),
                MappingPoint(0.15f, 1536),
                MappingPoint(0.20f, 2048),
                MappingPoint(0.30f, 2816),
                MappingPoint(0.40f, 3456),
                MappingPoint(0.50f, 4095)
            ),
            metadata = MappingMetadata(
                calibrationMethod = "macro",
                environmentType = "indoor",
                createdAt = System.currentTimeMillis()
            )
        )
    }
}

/**
 * Information about a mapping file
 */
data class MappingFileInfo(
    val file: File,
    val name: String,
    val description: String,
    val createdAt: Long,
    val pointCount: Int,
    val isValid: Boolean
)