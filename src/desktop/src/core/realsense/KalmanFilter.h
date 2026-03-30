#pragma once

#include <vector>
#include <cmath>
#include <cstdint>
#include <algorithm>
#include <numeric>

namespace alice {

/**
 * 1D Kalman filter for depth tracking with adaptive noise estimation.
 * Ported from KalmanDepthFilter.kt — preserves all parameters exactly.
 */
class KalmanFilter {
public:
    KalmanFilter();

    /**
     * Update filter with a new depth measurement.
     * @param measurement New depth measurement in mm
     * @param timestampMs Measurement timestamp in milliseconds
     * @return Filtered depth in mm
     */
    float update(float measurement, int64_t timestampMs = 0);

    /** Predict next depth value (constant model — returns current estimate). */
    float predict() const;

    /** Get current filtered depth without updating. */
    float getCurrentEstimate() const;

    /**
     * Get uncertainty/confidence of current estimate.
     * @return Confidence value 0–1 (higher is better)
     */
    float getConfidence() const;

    /** Reset filter to initial state. */
    void reset();

    /** Check if filter has been initialized with at least one measurement. */
    bool isReady() const;

private:
    void adaptNoiseParameters(float measurement);

    // State
    float x_ = 0.0f;        // Estimated depth (mm)
    float P_ = 1000.0f;     // Estimation error covariance
    bool initialized_ = false;

    // Filter parameters
    float Q_ = 50.0f;       // Process noise covariance
    float R_ = 100.0f;      // Measurement noise covariance

    // Adaptive parameters
    static constexpr int kHistorySize = 10;
    std::vector<float> history_;
    int64_t lastUpdateTime_ = 0;
};

} // namespace alice
