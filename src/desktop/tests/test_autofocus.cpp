#include <gtest/gtest.h>
#include "core/autofocus/AutofocusMapping.h"
#include <cmath>

using namespace alice;

// ── Interpolation ────────────────────────────────────────────────────

TEST(AutofocusMapping, EmptyMappingReturnsNullopt) {
    AutofocusMapping m("empty", {});
    EXPECT_EQ(m.getMotorPosition(1.0f), std::nullopt);
}

TEST(AutofocusMapping, SinglePoint) {
    AutofocusMapping m("single", {{1.0f, 2048, 1.0f}});
    // Below, at, and above should all return 2048
    EXPECT_EQ(m.getMotorPosition(0.5f), 2048);
    EXPECT_EQ(m.getMotorPosition(1.0f), 2048);
    EXPECT_EQ(m.getMotorPosition(5.0f), 2048);
}

TEST(AutofocusMapping, LinearInterpolation) {
    AutofocusMapping m("linear", {
        {0.0f, 0, 1.0f},   // intentionally at 0 for math simplicity
        {1.0f, 1000, 1.0f},
        {2.0f, 2000, 1.0f}
    });
    // Exact matches
    EXPECT_EQ(m.getMotorPosition(1.0f), 1000);
    // Midpoints
    EXPECT_EQ(m.getMotorPosition(0.5f), 500);
    EXPECT_EQ(m.getMotorPosition(1.5f), 1500);
}

TEST(AutofocusMapping, ClampsToBounds) {
    AutofocusMapping m("bounded", {
        {1.0f, 100, 1.0f},
        {5.0f, 4000, 1.0f}
    });
    EXPECT_EQ(m.getMotorPosition(0.1f), 100);  // Below min
    EXPECT_EQ(m.getMotorPosition(10.0f), 4000); // Above max
}

TEST(AutofocusMapping, ClampMotorTo4095) {
    // Interpolation should never exceed 4095
    AutofocusMapping m("high", {
        {1.0f, 3000, 1.0f},
        {2.0f, 5000, 1.0f} // invalid but tests clamping
    });
    auto pos = m.getMotorPosition(1.5f);
    EXPECT_TRUE(pos.has_value());
    EXPECT_LE(*pos, 4095);
}

TEST(AutofocusMapping, UnsortedPointsStillWork) {
    AutofocusMapping m("unsorted", {
        {3.0f, 3000, 1.0f},
        {1.0f, 1000, 1.0f},
        {2.0f, 2000, 1.0f}
    });
    EXPECT_EQ(m.getMotorPosition(1.5f), 1500);
}

// ── Default preset ───────────────────────────────────────────────────

TEST(AutofocusMapping, DefaultPreset) {
    auto m = AutofocusMapping::createDefault();
    EXPECT_EQ(m.points().size(), 5u);
    EXPECT_EQ(m.getMotorPosition(0.2f), 0);
    EXPECT_EQ(m.getMotorPosition(5.0f), 4095);
}

// ── All presets ──────────────────────────────────────────────────────

TEST(AutofocusMapping, PresetsAreValid) {
    auto presets = {
        MappingPreset::Linear, MappingPreset::Logarithmic,
        MappingPreset::Portrait, MappingPreset::Landscape,
        MappingPreset::Macro
    };
    for (auto p : presets) {
        auto m = AutofocusMapping::createPreset(p);
        auto v = m.validate();
        EXPECT_TRUE(v.isValid) << "Preset failed validation: " << m.name();
        EXPECT_GE(m.points().size(), 5u);
    }
}

// ── Validation ───────────────────────────────────────────────────────

TEST(AutofocusMapping, ValidationEmpty) {
    AutofocusMapping m("empty", {});
    auto v = m.validate();
    EXPECT_FALSE(v.isValid);
    EXPECT_FALSE(v.errors.empty());
}

TEST(AutofocusMapping, ValidationDuplicateDepths) {
    AutofocusMapping m("dup", {
        {1.0f, 100, 1.0f},
        {1.0f, 200, 1.0f}
    });
    auto v = m.validate();
    EXPECT_FALSE(v.isValid);
}

TEST(AutofocusMapping, ValidationWarningFewPoints) {
    AutofocusMapping m("few", {
        {1.0f, 1000, 1.0f},
        {2.0f, 2000, 1.0f}
    });
    auto v = m.validate();
    EXPECT_TRUE(v.isValid); // valid but with warnings
    EXPECT_FALSE(v.warnings.empty());
}

// ── JSON round-trip ──────────────────────────────────────────────────

TEST(AutofocusMapping, JsonRoundTrip) {
    auto original = AutofocusMapping::createDefault();
    std::string json = original.toJson();

    auto loaded = AutofocusMapping::fromJson(json);
    ASSERT_TRUE(loaded.has_value());
    EXPECT_EQ(loaded->name(), original.name());
    EXPECT_EQ(loaded->points().size(), original.points().size());

    // Verify interpolation produces identical results
    for (float d = 0.2f; d <= 5.0f; d += 0.5f) {
        EXPECT_EQ(loaded->getMotorPosition(d), original.getMotorPosition(d));
    }
}

TEST(AutofocusMapping, JsonInvalidReturnsNullopt) {
    EXPECT_EQ(AutofocusMapping::fromJson("not json"), std::nullopt);
    EXPECT_EQ(AutofocusMapping::fromJson("{}"), std::nullopt); // missing "name"
}
