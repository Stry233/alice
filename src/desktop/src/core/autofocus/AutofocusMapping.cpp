#include "core/autofocus/AutofocusMapping.h"

#include <algorithm>
#include <fstream>
#include <cmath>
#include <chrono>

namespace alice {

// ── JSON helpers ─────────────────────────────────────────────────────

void to_json(nlohmann::json &j, const MappingPoint &p) {
    j = {{"depth", p.depth}, {"motorPosition", p.motorPosition}, {"confidence", p.confidence}};
}
void from_json(const nlohmann::json &j, MappingPoint &p) {
    j.at("depth").get_to(p.depth);
    j.at("motorPosition").get_to(p.motorPosition);
    p.confidence = j.value("confidence", 1.0f);
}

void to_json(nlohmann::json &j, const MappingMetadata &m) {
    j = {
        {"cameraModel", m.cameraModel}, {"lensModel", m.lensModel},
        {"focalLength", m.focalLength}, {"aperture", m.aperture},
        {"notes", m.notes}, {"calibrationMethod", m.calibrationMethod},
        {"environmentType", m.environmentType}, {"createdAt", m.createdAt}
    };
}
void from_json(const nlohmann::json &j, MappingMetadata &m) {
    m.cameraModel       = j.value("cameraModel", "");
    m.lensModel         = j.value("lensModel", "");
    m.focalLength       = j.value("focalLength", 0.0f);
    m.aperture          = j.value("aperture", 0.0f);
    m.notes             = j.value("notes", "");
    m.calibrationMethod = j.value("calibrationMethod", "manual");
    m.environmentType   = j.value("environmentType", "");
    m.createdAt         = j.value("createdAt", static_cast<int64_t>(0));
}

// ── AutofocusMapping ─────────────────────────────────────────────────

AutofocusMapping::AutofocusMapping(const std::string &name,
                                   const std::vector<MappingPoint> &points,
                                   const std::string &description,
                                   const MappingMetadata &metadata)
    : name_(name), description_(description), points_(points), metadata_(metadata) {}

std::optional<int> AutofocusMapping::getMotorPosition(float depth) const {
    if (points_.empty()) return std::nullopt;

    auto sorted = points_;
    std::sort(sorted.begin(), sorted.end(),
              [](const MappingPoint &a, const MappingPoint &b) { return a.depth < b.depth; });

    if (depth <= sorted.front().depth) return sorted.front().motorPosition;
    if (depth >= sorted.back().depth)  return sorted.back().motorPosition;

    // Find surrounding points for linear interpolation. `validate()` rejects
    // duplicate depths, but it's caller-optional — a mapping loaded from a
    // malformed JSON file or mutated after the fact could still arrive here
    // with consecutive points at the same depth. Guard the divisor so we
    // never divide by (near-)zero; fall back to the low endpoint instead.
    static constexpr float kDepthSpanEpsilon = 1e-6f;
    for (size_t i = 0; i + 1 < sorted.size(); ++i) {
        if (depth >= sorted[i].depth && depth <= sorted[i + 1].depth) {
            const auto &lo = sorted[i];
            const auto &hi = sorted[i + 1];
            const float span = hi.depth - lo.depth;
            if (span <= kDepthSpanEpsilon) {
                return std::clamp(lo.motorPosition, 0, 4095);
            }
            const float ratio = (depth - lo.depth) / span;
            int pos = lo.motorPosition
                    + static_cast<int>(ratio * (hi.motorPosition - lo.motorPosition));
            return std::clamp(pos, 0, 4095);
        }
    }
    return std::nullopt;
}

ValidationResult AutofocusMapping::validate() const {
    ValidationResult result;
    result.isValid = true;

    if (points_.empty()) {
        result.errors.push_back("Mapping must contain at least one point");
        result.isValid = false;
    } else if (points_.size() < 3) {
        result.warnings.push_back("Mapping has fewer than 3 points, interpolation may be limited");
    }

    // Duplicate depths
    {
        std::vector<float> depths;
        depths.reserve(points_.size());
        for (const auto &p : points_) depths.push_back(p.depth);
        std::sort(depths.begin(), depths.end());
        if (std::adjacent_find(depths.begin(), depths.end()) != depths.end()) {
            result.errors.push_back("Mapping contains duplicate depth values");
            result.isValid = false;
        }
    }

    // Individual point validation
    for (size_t i = 0; i < points_.size(); ++i) {
        if (!points_[i].isValid()) {
            result.errors.push_back("Invalid mapping point at index " + std::to_string(i));
            result.isValid = false;
        }
    }

    // Range coverage
    float minD = 1e9f, maxD = -1e9f;
    for (const auto &p : points_) {
        minD = std::min(minD, p.depth);
        maxD = std::max(maxD, p.depth);
    }
    if (points_.empty()) { minD = 0; maxD = 0; }

    if (minD > 0.3f) result.warnings.push_back("No mapping for close range (< 30cm)");
    if (maxD < 3.0f)  result.warnings.push_back("No mapping for far range (> 3m)");

    result.statistics = {static_cast<int>(points_.size()), minD, maxD, maxD - minD};
    return result;
}

// ── Serialization ────────────────────────────────────────────────────

std::string AutofocusMapping::toJson() const {
    nlohmann::json j;
    j["version"]       = version_;
    j["name"]          = name_;
    j["description"]   = description_;
    j["mappingPoints"]  = points_;
    j["metadata"]       = metadata_;
    return j.dump(2);
}

std::optional<AutofocusMapping> AutofocusMapping::fromJson(const std::string &jsonStr) {
    try {
        auto j = nlohmann::json::parse(jsonStr);
        AutofocusMapping m;
        m.version_     = j.value("version", "1.0");
        m.name_        = j.at("name").get<std::string>();
        m.description_ = j.value("description", "");
        m.points_      = j.at("mappingPoints").get<std::vector<MappingPoint>>();
        if (j.contains("metadata")) j.at("metadata").get_to(m.metadata_);
        return m;
    } catch (...) {
        return std::nullopt;
    }
}

std::optional<AutofocusMapping> AutofocusMapping::fromFile(const std::string &path) {
    std::ifstream ifs(path);
    if (!ifs.is_open()) return std::nullopt;
    std::string contents((std::istreambuf_iterator<char>(ifs)),
                          std::istreambuf_iterator<char>());
    return fromJson(contents);
}

bool AutofocusMapping::saveToFile(const std::string &path) const {
    std::ofstream ofs(path);
    if (!ofs.is_open()) return false;
    ofs << toJson();
    return ofs.good();
}

// ── Presets ──────────────────────────────────────────────────────────

AutofocusMapping AutofocusMapping::createDefault() {
    return AutofocusMapping(
        "Default Linear Mapping",
        {
            {0.2f, 0,    1.0f},
            {0.5f, 1024, 1.0f},
            {1.0f, 2048, 1.0f},
            {2.0f, 3072, 1.0f},
            {5.0f, 4095, 1.0f}
        },
        "Linear mapping from 0.2m to 5m",
        MappingMetadata{"", "", 0, 0, "", "linear", "", 0}
    );
}

AutofocusMapping AutofocusMapping::createPreset(MappingPreset preset) {
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                   std::chrono::system_clock::now().time_since_epoch()).count();

    auto makeMeta = [&](const std::string &method) -> MappingMetadata {
        return {"", "", 0, 0, "", method, "", now};
    };

    switch (preset) {
    case MappingPreset::Linear:
        return AutofocusMapping("Linear",
            {{0.2f,0,1},{0.5f,1024,1},{1.0f,2048,1},{2.0f,3072,1},{5.0f,4095,1}},
            "Linear mapping from 0.2m to 5m", makeMeta("linear"));

    case MappingPreset::Logarithmic: {
        // 10 log-spaced points from 0.2 to 9.5 m
        std::vector<MappingPoint> pts;
        constexpr float minD = 0.2f, maxD = 9.5f;
        constexpr int n = 10;
        for (int i = 0; i < n; ++i) {
            float t = static_cast<float>(i) / (n - 1);
            float depth = minD * std::exp(t * std::log(maxD / minD));
            int pos = static_cast<int>(t * 4095);
            pts.push_back({depth, pos, 1.0f});
        }
        return AutofocusMapping("Logarithmic", pts,
            "Logarithmic mapping 0.2–9.5m", makeMeta("logarithmic"));
    }

    case MappingPreset::Portrait:
        return AutofocusMapping("Portrait",
            {{0.3f,0,1},{0.5f,585,1},{0.8f,1170,1},{1.0f,1755,1},
             {1.5f,2340,1},{2.0f,2925,1},{3.0f,4095,1}},
            "Portrait range 0.3–3m", makeMeta("portrait"));

    case MappingPreset::Landscape:
        return AutofocusMapping("Landscape",
            {{0.5f,0,1},{1.0f,819,1},{2.0f,1638,1},
             {5.0f,2457,1},{8.0f,3276,1},{10.0f,4095,1}},
            "Landscape range 0.5–10m", makeMeta("landscape"));

    case MappingPreset::Macro:
        return AutofocusMapping("Macro",
            {{0.05f,0,1},{0.08f,682,1},{0.12f,1365,1},{0.18f,2048,1},
             {0.25f,2730,1},{0.35f,3413,1},{0.50f,4095,1}},
            "Macro range 5–50cm", makeMeta("macro"));
    }
    return createDefault();
}

} // namespace alice
