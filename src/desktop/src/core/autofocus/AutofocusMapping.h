#pragma once

#include <string>
#include <vector>
#include <optional>
#include <cstdint>

#include <nlohmann/json.hpp>

namespace alice {

// ── Data models ──────────────────────────────────────────────────────

struct MappingPoint {
    float depth = 0.0f;        // Depth in meters
    int motorPosition = 0;     // 0–4095
    float confidence = 1.0f;   // 0–1

    bool isValid() const {
        return depth > 0.0f && depth <= 10.0f
            && motorPosition >= 0 && motorPosition <= 4095
            && confidence >= 0.0f && confidence <= 1.0f;
    }
};

void to_json(nlohmann::json &j, const MappingPoint &p);
void from_json(const nlohmann::json &j, MappingPoint &p);

struct MappingMetadata {
    std::string cameraModel;
    std::string lensModel;
    float focalLength = 0.0f;
    float aperture = 0.0f;
    std::string notes;
    std::string calibrationMethod = "manual";
    std::string environmentType;
    int64_t createdAt = 0;
};

void to_json(nlohmann::json &j, const MappingMetadata &m);
void from_json(const nlohmann::json &j, MappingMetadata &m);

struct MappingStatistics {
    int pointCount = 0;
    float minDepth = 0.0f;
    float maxDepth = 0.0f;
    float depthRange = 0.0f;
};

struct ValidationResult {
    bool isValid = false;
    std::vector<std::string> errors;
    std::vector<std::string> warnings;
    MappingStatistics statistics;
};

// ── Presets ──────────────────────────────────────────────────────────

enum class MappingPreset {
    Linear,
    Logarithmic,
    Portrait,
    Landscape,
    Macro
};

// ── Main class ───────────────────────────────────────────────────────

/**
 * Complete autofocus mapping configuration.
 * Holds a named set of sorted depth-to-motor-position calibration points with linear
 * interpolation, full validation, JSON serialization, and a preset library (Linear,
 * Logarithmic, Portrait, Landscape, Macro).
 */
class AutofocusMapping {
public:
    AutofocusMapping() = default;
    AutofocusMapping(const std::string &name,
                     const std::vector<MappingPoint> &points,
                     const std::string &description = "",
                     const MappingMetadata &metadata = {});

    // ── Core API ─────────────────────────────────────────────────────

    /** Get interpolated motor position for a given depth. */
    std::optional<int> getMotorPosition(float depth) const;

    /**
     * Inverse of getMotorPosition — interpolate what depth the lens is
     * focused on when the motor sits at `motorPosition`. Used by the
     * LiDAR waveform overlay in OPS view to draw a "motor-implied focus
     * depth" reference line next to the live depth column.
     *
     * Returns nullopt if the mapping is empty; clamps to the endpoints
     * outside the calibrated range (same policy as getMotorPosition).
     */
    std::optional<float> getDepthForMotor(int motorPosition) const;

    /** Validate the entire mapping. */
    ValidationResult validate() const;

    // ── Serialization ────────────────────────────────────────────────

    /** Serialize to JSON string. */
    std::string toJson() const;

    /** Deserialize from JSON string. */
    static std::optional<AutofocusMapping> fromJson(const std::string &jsonStr);

    /** Load from file. */
    static std::optional<AutofocusMapping> fromFile(const std::string &path);

    /** Save to file. */
    bool saveToFile(const std::string &path) const;

    // ── Presets ──────────────────────────────────────────────────────

    /** Create a preset mapping. */
    static AutofocusMapping createPreset(MappingPreset preset);

    /** Create the default linear mapping (0.2–5 m). */
    static AutofocusMapping createDefault();

    // ── Accessors ────────────────────────────────────────────────────

    const std::string &name() const { return name_; }
    const std::string &description() const { return description_; }
    const std::string &version() const { return version_; }
    const std::vector<MappingPoint> &points() const { return points_; }
    const MappingMetadata &metadata() const { return metadata_; }

private:
    std::string version_ = "1.0";
    std::string name_;
    std::string description_;
    std::vector<MappingPoint> points_;
    MappingMetadata metadata_;
};

} // namespace alice
