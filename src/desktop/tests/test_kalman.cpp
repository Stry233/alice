#include <gtest/gtest.h>
#include "core/realsense/KalmanFilter.h"
#include "core/autofocus/KalmanFilter2D.h"
#include <cmath>

using namespace alice;

// ── 1D Kalman Filter ─────────────────────────────────────────────────

TEST(KalmanFilter, InitialState) {
    KalmanFilter kf;
    EXPECT_FALSE(kf.isReady());
    EXPECT_FLOAT_EQ(kf.getCurrentEstimate(), 0.0f);
    EXPECT_FLOAT_EQ(kf.getConfidence(), 0.0f);
}

TEST(KalmanFilter, FirstMeasurement) {
    KalmanFilter kf;
    float result = kf.update(1000.0f, 100);
    EXPECT_FLOAT_EQ(result, 1000.0f);
    EXPECT_TRUE(kf.isReady());
}

TEST(KalmanFilter, ConvergesToStableValue) {
    KalmanFilter kf;
    // Feed 50 identical measurements — should converge to the value
    for (int i = 0; i < 50; ++i) {
        kf.update(2000.0f, i * 16);
    }
    EXPECT_NEAR(kf.getCurrentEstimate(), 2000.0f, 1.0f);
    EXPECT_GT(kf.getConfidence(), 0.9f);
}

TEST(KalmanFilter, TracksMovingTarget) {
    KalmanFilter kf;
    // Linearly increasing depth
    for (int i = 0; i < 30; ++i) {
        float depth = 1000.0f + i * 50.0f;
        kf.update(depth, i * 33);
    }
    // Adaptive filter smooths aggressively — estimate lags behind raw input
    // by design (cinema-grade smoothing prioritizes stability over speed)
    EXPECT_NEAR(kf.getCurrentEstimate(), 2450.0f, 500.0f);
}

TEST(KalmanFilter, SmoothsNoisyMeasurements) {
    KalmanFilter kf;
    // Noisy measurements around 1500
    float readings[] = {1500, 1520, 1480, 1510, 1490, 1505, 1495, 1510, 1500, 1505};
    for (int i = 0; i < 10; ++i) {
        kf.update(readings[i], i * 33);
    }
    // Filtered value should be close to 1500, within noise band
    EXPECT_NEAR(kf.getCurrentEstimate(), 1500.0f, 30.0f);
}

TEST(KalmanFilter, Reset) {
    KalmanFilter kf;
    kf.update(1000.0f, 0);
    EXPECT_TRUE(kf.isReady());
    kf.reset();
    EXPECT_FALSE(kf.isReady());
    EXPECT_FLOAT_EQ(kf.getCurrentEstimate(), 0.0f);
}

TEST(KalmanFilter, ConfidenceIncreasesWithStability) {
    KalmanFilter kf;
    float conf1 = 0, conf2 = 0;
    for (int i = 0; i < 5; ++i)
        kf.update(1000.0f, i * 33);
    conf1 = kf.getConfidence();

    for (int i = 5; i < 30; ++i)
        kf.update(1000.0f, i * 33);
    conf2 = kf.getConfidence();

    EXPECT_GT(conf2, conf1);
}

// ── 2D Kalman Filter ─────────────────────────────────────────────────

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

    // Feed measurements moving right at ~0.1 per frame
    for (int i = 0; i < 20; ++i) {
        float mx = 0.1f * i;
        kf.predict(1.0f / 30.0f);
        kf.update(mx, 0.0f);
    }

    float x, y;
    kf.getPosition(x, y);
    EXPECT_NEAR(x, 1.9f, 0.3f); // Should be near 0.1*19
    EXPECT_NEAR(y, 0.0f, 0.1f);

    float vx, vy;
    kf.getVelocity(vx, vy);
    EXPECT_GT(vx, 0.0f); // Should have positive x velocity
}

TEST(KalmanFilter2D, AutoInitOnUpdate) {
    KalmanFilter2D kf;
    kf.update(0.3f, 0.7f); // Should auto-initialize
    EXPECT_TRUE(kf.isInitialized());

    float x, y;
    kf.getPosition(x, y);
    EXPECT_FLOAT_EQ(x, 0.3f);
    EXPECT_FLOAT_EQ(y, 0.7f);
}
