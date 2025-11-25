package com.selkacraft.alice.comm.autofocus

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Represents a single mapping point between depth and motor position
 */
@Serializable
data class MappingPoint(
    val depth: Float,        // Depth in meters
    val motorPosition: Int,  // Motor position (0-4095)
    val confidence: Float = 1.0f // Optional confidence weight for this point
) {
    fun isValid(): Boolean {
        return depth > 0 && depth <= 10.0f && // Reasonable depth range (0-10m)
                motorPosition in 0..4095 &&    // Valid motor range
                confidence in 0f..1f           // Valid confidence range
    }
}

/**
 * Metadata for an autofocus mapping, including camera/lens info and calibration details.
 */
@Serializable
data class MappingMetadata(
    val cameraModel: String = "",
    val lensModel: String = "",
    val focalLength: Float = 0f,
    val aperture: Float = 0f,
    val notes: String = "",
    val calibrationMethod: String = "manual",
    val environmentType: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Complete autofocus mapping configuration containing calibration points
 * and associated metadata for depth-to-motor position conversion.
 */
@Serializable
data class AutofocusMapping(
    val version: String = "1.0",
    val name: String,
    val description: String = "",
    val mappingPoints: List<MappingPoint>,
    val metadata: MappingMetadata = MappingMetadata()
) {
    // Computed property to maintain backward compatibility
    val createdAt: Long
        get() = metadata.createdAt

    /**
     * Validate the entire mapping
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check if we have enough points
        if (mappingPoints.isEmpty()) {
            errors.add("Mapping must contain at least one point")
        } else if (mappingPoints.size < 3) {
            warnings.add("Mapping has fewer than 3 points, interpolation may be limited")
        }

        // Check for duplicate depths
        val depths = mappingPoints.map { it.depth }
        if (depths.size != depths.toSet().size) {
            errors.add("Mapping contains duplicate depth values")
        }

        // Validate individual points
        mappingPoints.forEachIndexed { index, point ->
            if (!point.isValid()) {
                errors.add("Invalid mapping point at index $index")
            }
        }

        // Check if points are sorted by depth (warning only)
        if (mappingPoints.size > 1) {
            val sorted = mappingPoints.sortedBy { it.depth }
            if (mappingPoints != sorted) {
                warnings.add("Mapping points are not sorted by depth")
            }
        }

        // Check range coverage
        val minDepth = mappingPoints.minByOrNull { it.depth }?.depth ?: 0f
        val maxDepth = mappingPoints.maxByOrNull { it.depth }?.depth ?: 0f

        if (minDepth > 0.3f) {
            warnings.add("No mapping for close range (< 30cm)")
        }
        if (maxDepth < 3.0f) {
            warnings.add("No mapping for far range (> 3m)")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            statistics = MappingStatistics(
                pointCount = mappingPoints.size,
                minDepth = minDepth,
                maxDepth = maxDepth,
                depthRange = maxDepth - minDepth
            )
        )
    }

    /**
     * Get interpolated motor position for a given depth
     */
    fun getMotorPosition(depth: Float): Int? {
        if (mappingPoints.isEmpty()) return null

        val sortedPoints = mappingPoints.sortedBy { it.depth }

        // If depth is outside range, use nearest endpoint
        if (depth <= sortedPoints.first().depth) {
            return sortedPoints.first().motorPosition
        }
        if (depth >= sortedPoints.last().depth) {
            return sortedPoints.last().motorPosition
        }

        // Find surrounding points for interpolation
        var lowerPoint: MappingPoint? = null
        var upperPoint: MappingPoint? = null

        for (i in 0 until sortedPoints.size - 1) {
            if (depth >= sortedPoints[i].depth && depth <= sortedPoints[i + 1].depth) {
                lowerPoint = sortedPoints[i]
                upperPoint = sortedPoints[i + 1]
                break
            }
        }

        // Linear interpolation
        if (lowerPoint != null && upperPoint != null) {
            val ratio = (depth - lowerPoint.depth) / (upperPoint.depth - lowerPoint.depth)
            val interpolatedPosition = lowerPoint.motorPosition +
                    (ratio * (upperPoint.motorPosition - lowerPoint.motorPosition)).toInt()
            return interpolatedPosition.coerceIn(0, 4095)
        }

        return null
    }

    companion object {
        // Create Json instance with lenient parsing
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            coerceInputValues = true // This helps with type coercion (e.g., int to float)
            isLenient = true // Allow more flexible parsing
            allowSpecialFloatingPointValues = true // Allow NaN, Infinity
            explicitNulls = false // Don't require explicit nulls
        }

        /**
         * Load mapping from JSON string using manual parsing as fallback
         */
        fun fromJson(jsonString: String): Result<AutofocusMapping> {
            return try {
                // Try direct deserialization first
                val mapping = json.decodeFromString<AutofocusMapping>(jsonString)
                Result.success(mapping)
            } catch (e: Exception) {
                // If kotlinx.serialization fails, try manual parsing
                try {
                    val mapping = parseManually(jsonString)
                    Result.success(mapping)
                } catch (e2: Exception) {
                    Result.failure(e) // Return original error
                }
            }
        }

        /**
         * Manual JSON parsing as fallback when kotlinx.serialization fails
         */
        private fun parseManually(jsonString: String): AutofocusMapping {
            // Use org.json for manual parsing as fallback
            val jsonObject = org.json.JSONObject(jsonString)

            val name = jsonObject.getString("name")
            val description = jsonObject.optString("description", "")
            val version = jsonObject.optString("version", "1.0")

            // Parse mapping points
            val pointsArray = jsonObject.getJSONArray("mappingPoints")
            val mappingPoints = mutableListOf<MappingPoint>()

            for (i in 0 until pointsArray.length()) {
                val pointObj = pointsArray.getJSONObject(i)
                val depth = pointObj.getDouble("depth").toFloat()
                val motorPosition = pointObj.getInt("motorPosition")
                val confidence = pointObj.optDouble("confidence", 1.0).toFloat()

                mappingPoints.add(MappingPoint(depth, motorPosition, confidence))
            }

            // Parse metadata
            val metadata = if (jsonObject.has("metadata")) {
                val metaObj = jsonObject.getJSONObject("metadata")
                MappingMetadata(
                    cameraModel = metaObj.optString("cameraModel", ""),
                    lensModel = metaObj.optString("lensModel", ""),
                    focalLength = metaObj.optDouble("focalLength", 0.0).toFloat(),
                    aperture = metaObj.optDouble("aperture", 0.0).toFloat(),
                    notes = metaObj.optString("notes", ""),
                    calibrationMethod = metaObj.optString("calibrationMethod", "manual"),
                    environmentType = metaObj.optString("environmentType", ""),
                    createdAt = metaObj.optLong("createdAt", System.currentTimeMillis())
                )
            } else {
                MappingMetadata()
            }

            return AutofocusMapping(
                version = version,
                name = name,
                description = description,
                mappingPoints = mappingPoints,
                metadata = metadata
            )
        }

        /**
         * Load mapping from file
         */
        fun fromFile(file: File): Result<AutofocusMapping> {
            return try {
                if (!file.exists()) {
                    return Result.failure(IllegalArgumentException("File does not exist"))
                }
                val jsonString = file.readText()
                fromJson(jsonString)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Create a default mapping for testing
         */
        fun createDefault(): AutofocusMapping {
            return AutofocusMapping(
                name = "Default Linear Mapping",
                description = "Linear mapping from 0.2m to 5m",
                mappingPoints = listOf(
                    MappingPoint(0.2f, 0),      // 20cm - full near
                    MappingPoint(0.5f, 1024),   // 50cm
                    MappingPoint(1.0f, 2048),   // 1m - middle
                    MappingPoint(2.0f, 3072),   // 2m
                    MappingPoint(5.0f, 4095)    // 5m - full far
                ),
                metadata = MappingMetadata(
                    calibrationMethod = "linear",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Convert to JSON string
     */
    fun toJson(): String {
        return try {
            json.encodeToString<AutofocusMapping>(this)
        } catch (e: Exception) {
            // Fallback to manual JSON creation if kotlinx.serialization fails
            toJsonManually()
        }
    }

    /**
     * Manual JSON creation as fallback
     */
    private fun toJsonManually(): String {
        val jsonObject = org.json.JSONObject()
        jsonObject.put("version", version)
        jsonObject.put("name", name)
        jsonObject.put("description", description)

        // Add mapping points
        val pointsArray = org.json.JSONArray()
        for (point in mappingPoints) {
            val pointObj = org.json.JSONObject()
            pointObj.put("depth", point.depth.toDouble())
            pointObj.put("motorPosition", point.motorPosition)
            pointObj.put("confidence", point.confidence.toDouble())
            pointsArray.put(pointObj)
        }
        jsonObject.put("mappingPoints", pointsArray)

        // Add metadata
        val metaObj = org.json.JSONObject()
        metaObj.put("cameraModel", metadata.cameraModel)
        metaObj.put("lensModel", metadata.lensModel)
        metaObj.put("focalLength", metadata.focalLength.toDouble())
        metaObj.put("aperture", metadata.aperture.toDouble())
        metaObj.put("notes", metadata.notes)
        metaObj.put("calibrationMethod", metadata.calibrationMethod)
        metaObj.put("environmentType", metadata.environmentType)
        metaObj.put("createdAt", metadata.createdAt)
        jsonObject.put("metadata", metaObj)

        return jsonObject.toString(2) // Pretty print with indent of 2
    }

    /**
     * Save to file
     */
    fun saveToFile(file: File): Result<Unit> {
        return try {
            file.writeText(toJson())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Validation result for a mapping
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val statistics: MappingStatistics? = null
)

/**
 * Statistics about a mapping
 */
data class MappingStatistics(
    val pointCount: Int,
    val minDepth: Float,
    val maxDepth: Float,
    val depthRange: Float
)

/**
 * Interpolation methods for mapping
 */
enum class InterpolationMethod {
    LINEAR,        // Simple linear interpolation
    CUBIC_SPLINE,  // Smooth cubic spline (future)
    NEAREST        // Use nearest point (no interpolation)
}