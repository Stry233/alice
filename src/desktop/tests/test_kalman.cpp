#include <gtest/gtest.h>
#include "core/realsense/DepthEstimator.h"
#include "core/autofocus/KalmanFilter2D.h"
#include <cstdint>
#include <vector>

using namespace alice;

// ── DepthEstimator ───────────────────────────────────────────────────
//
// Industry-grade single-point depth estimator: ROI median + confidence-
// weighted EMA with discontinuity detection and position-aware reset.

namespace {

// Build a synthetic W×H depth frame filled with a uniform value. Useful
// for the stable-tracking and baseline tests.
std::vector<uint16_t> makeUniformFrame(int w, int h, uint16_t value) {
    return std::vector<uint16_t>(static_cast<size_t>(w) * h, value);
}

// Build a frame with a specific rectangular region at one depth and the
// background at another. Simulates an object over a wall.
std::vector<uint16_t> makeTwoDepthFrame(int w, int h,
                                        uint16_t background, uint16_t patch,
                                        int px, int py, int pw, int ph) {
    std::vector<uint16_t> frame(static_cast<size_t>(w) * h, background);
    for (int y = py; y < py + ph && y < h; ++y) {
        for (int x = px; x < px + pw && x < w; ++x) {
            frame[y * w + x] = patch;
        }
    }
    return frame;
}

} // namespace

TEST(DepthEstimator, StableReadingOnUniformFrame) {
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 1500);

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(r.isValid);
    EXPECT_NEAR(r.valueM, 1.5f, 0.01f);
    EXPECT_GT(r.confidence, 0.8f); // clean frame — high confidence
}

TEST(DepthEstimator, SurvivesCenterPixelHole) {
    // The bilateral approach failed here: if the center pixel was 0
    // (common at 4 m+), it bailed out early. The median-of-valid-pixels
    // approach must succeed as long as enough neighbours are valid.
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 4000);
    const int cx = 320, cy = 240;
    frame[cy * 640 + cx] = 0;         // hole at exact center
    frame[(cy + 1) * 640 + cx] = 0;   // another hole
    frame[cy * 640 + (cx - 1)] = 0;   // and another

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(r.isValid);
    EXPECT_NEAR(r.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, RejectsFrameWithTooFewValidPixels) {
    // If almost every pixel in the ROI is a hole, the reading is
    // untrustworthy and must be marked invalid (not silently made up).
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 0);
    // Plant only a handful of valid pixels — below the kMinValidPixels
    // threshold (8).
    frame[240 * 640 + 320] = 3000;
    frame[240 * 640 + 321] = 3000;
    frame[241 * 640 + 320] = 3000;

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_FALSE(r.isValid);
}

TEST(DepthEstimator, RejectsOutOfRangePixels) {
    DepthEstimator est;
    est.setValidRange(200, 5000);
    auto frame = makeUniformFrame(640, 480, 8000); // beyond max

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_FALSE(r.isValid);
}

TEST(DepthEstimator, PositionChangeResetsState) {
    // This is the PRIMARY bug the new estimator fixes: when the target
    // moves to a new spatial location, the temporal state must be
    // discarded. Otherwise the filter holds the old position's depth and
    // fights every reading at the new position.
    DepthEstimator est;

    // Converge on a 500 mm surface at (0.2, 0.5).
    auto near = makeUniformFrame(640, 480, 500);
    for (int i = 0; i < 30; ++i) {
        est.process(near.data(), 640, 480, 0.2f, 0.5f);
    }
    auto beforeMove = est.process(near.data(), 640, 480, 0.2f, 0.5f);
    EXPECT_NEAR(beforeMove.valueM, 0.5f, 0.02f);

    // Move target to (0.8, 0.5) where depth is 4000 mm. The very first
    // reading after the move must snap to the new value, not carry any
    // weight from the old 500 mm state.
    auto far = makeUniformFrame(640, 480, 4000);
    auto afterMove = est.process(far.data(), 640, 480, 0.8f, 0.5f);
    EXPECT_TRUE(afterMove.isValid);
    EXPECT_NEAR(afterMove.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, DiscontinuitySnapsAtFixedPosition) {
    // Crosshair stays at same pixel but the scene changes (object
    // removed, reveals far wall). Must snap without smoothing delay.
    DepthEstimator est;

    auto close = makeUniformFrame(640, 480, 800);
    for (int i = 0; i < 20; ++i) {
        est.process(close.data(), 640, 480, 0.5f, 0.5f);
    }

    auto far = makeUniformFrame(640, 480, 4500);
    auto snap = est.process(far.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(snap.isValid);
    EXPECT_NEAR(snap.valueM, 4.5f, 0.05f);
}

TEST(DepthEstimator, SmoothsNoisyMeasurements) {
    // Normal tracking within the noise band must be smoothed.
    DepthEstimator est;
    est.setSmoothing(0.30f);

    const uint16_t noisy[] = {1500, 1520, 1480, 1510, 1490, 1505, 1495, 1510};
    for (uint16_t d : noisy) {
        auto frame = makeUniformFrame(640, 480, d);
        est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    }
    auto r = est.process(makeUniformFrame(640, 480, 1500).data(),
                         640, 480, 0.5f, 0.5f);
    EXPECT_NEAR(r.valueM, 1.5f, 0.02f);
}

TEST(DepthEstimator, StaleFramesIncrementWhenSensorGoesDark) {
    // If the sensor stops producing valid data at this point, staleFrames
    // grows each call. The UI can use this to show "waiting..." state.
    DepthEstimator est;
    auto good = makeUniformFrame(640, 480, 2000);
    est.process(good.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_EQ(est.process(good.data(), 640, 480, 0.5f, 0.5f).staleFrames, 0);

    auto dark = makeUniformFrame(640, 480, 0);
    auto r1 = est.process(dark.data(), 640, 480, 0.5f, 0.5f);
    auto r2 = est.process(dark.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_FALSE(r1.isValid);
    EXPECT_FALSE(r2.isValid);
    EXPECT_GE(r2.staleFrames, 2);
}

TEST(DepthEstimator, Reset) {
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 1500);
    est.process(frame.data(), 640, 480, 0.5f, 0.5f);

    est.reset();
    auto frame2 = makeUniformFrame(640, 480, 4000);
    auto r = est.process(frame2.data(), 640, 480, 0.5f, 0.5f);
    // After reset, the very first reading IS the state — no blending
    // against the old 1500 mm.
    EXPECT_NEAR(r.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, LowConfidenceForPatchyROI) {
    // A split-depth ROI (object edge) should produce a lower confidence
    // than a clean uniform ROI, even if both yield a valid reading.
    DepthEstimator estA, estB;

    auto clean = makeUniformFrame(640, 480, 2000);
    auto confA = estA.process(clean.data(), 640, 480, 0.5f, 0.5f).confidence;

    // Left half of the ROI is at 2000 mm, right half at 4500 mm. This
    // creates a real depth discontinuity inside the ROI, producing a
    // wide IQR and thus low tightness. A clean uniform ROI should score
    // meaningfully higher.
    auto split = makeTwoDepthFrame(640, 480, 2000, 4500,
                                   /*px=*/320, /*py=*/240 - 3,
                                   /*pw=*/4, /*ph=*/7);
    auto confB = estB.process(split.data(), 640, 480, 0.5f, 0.5f).confidence;
    EXPECT_GT(confA, confB);
}

// ── 2D Kalman Filter (face-tracking — unchanged) ──────────────────────

TEST(KalmanFilter2D, InitialState) {
    KalmanFilter2D kf;
    EXPECT_FALSE(kf.isInitialized());
}

TEST(KalmanFilter2D, Initialize) {
    KalmanFilter2D kf;
    kf.initialize(0.5f, 0.5f);
    EXPECT_TRUE(kf.isInitialized());

    float x, y;
    kf.getPosition(x, y);
    EXPECT_FLOAT_EQ(x, 0.5f);
    EXPECT_FLOAT_EQ(y, 0.5f);
}

TEST(KalmanFilter2D, PredictConstantVelocity) {
    KalmanFilter2D kf;
    kf.initialize(0.0f, 0.0f);

    for (int i = 0; i < 20; ++i) {
        float mx = 0.1f * i;
        kf.predict(1.0f / 30.0f);
        kf.update(mx, 0.0f);
    }

    float x, y;
    kf.getPosition(x, y);
    EXPECT_NEAR(x, 1.9f, 0.3f);
    EXPECT_NEAR(y, 0.0f, 0.1f);

    float vx, vy;
    kf.getVelocity(vx, vy);
    EXPECT_GT(vx, 0.0f);
}

TEST(KalmanFilter2D, AutoInitOnUpdate) {
    KalmanFilter2D kf;
    kf.update(0.3f, 0.7f);
    EXPECT_TRUE(kf.isInitialized());

    float x, y;
    kf.getPosition(x, y);
    EXPECT_FLOAT_EQ(x, 0.3f);
    EXPECT_FLOAT_EQ(y, 0.7f);
}
