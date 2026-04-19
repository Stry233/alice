#include "core/realsense/KalmanFilter.h"

namespace alice {

KalmanFilter::KalmanFilter() {
    history_.reserve(kHistorySize);
}

float KalmanFilter::update(float measurement, int64_t timestampMs) {
    if (timestampMs == 0) {
        // Use a simple incrementing counter if no timestamp provided
        timestampMs = lastUpdateTime_ + 16; // ~60fps
    }

    if (!initialized_) {
        x_ = measurement;
        P_ = 1000.0f;
        initialized_ = true;
        lastUpdateTime_ = timestampMs;
        history_.push_back(measurement);
        return x_;
    }

    // Calculate time delta
    float dt = (lastUpdateTime_ > 0)
        ? static_cast<float>(timestampMs - lastUpdateTime_) / 1000.0f
        : 0.016f;
    dt = std::clamp(dt, 0.001f, 0.5f);
    lastUpdateTime_ = timestampMs;

    // Adaptive noise estimation
    adaptNoiseParameters(measurement);

    // Prediction step (constant depth model)
    float P_pred = P_ + Q_ * dt;

    // Innovation (measurement residual)
    float y = measurement - x_;

    // Innovation covariance
    float S = P_pred + R_;

    // Kalman gain
    float K = P_pred / S;

    // Update state estimate
    x_ += K * y;

    // Update error covariance
    P_ = (1.0f - K) * P_pred;

    // Add to history
    history_.push_back(measurement);
    if (static_cast<int>(history_.size()) > kHistorySize) {
        history_.erase(history_.begin());
    }

    return x_;
}

float KalmanFilter::predict() const {
    return x_;
}

float KalmanFilter::getCurrentEstimate() const {
    return initialized_ ? x_ : 0.0f;
}

float KalmanFilter::getConfidence() const {
    if (!initialized_) return 0.0f;
    constexpr float maxP = 1000.0f;
    return std::clamp(1.0f - (P_ / maxP), 0.0f, 1.0f);
}

void KalmanFilter::reset() {
    x_ = 0.0f;
    P_ = 1000.0f;
    initialized_ = false;
    lastUpdateTime_ = 0;
    history_.clear();
    Q_ = 50.0f;
    R_ = 100.0f;
}

bool KalmanFilter::isReady() const {
    return initialized_;
}

void KalmanFilter::adaptNoiseParameters(float measurement) {
    if (history_.size() < 3) return;

    // Calculate measurement variance
    float mean = std::accumulate(history_.begin(), history_.end(), 0.0f)
                 / static_cast<float>(history_.size());
    float variance = 0.0f;
    for (float v : history_) {
        float diff = v - mean;
        variance += diff * diff;
    }
    variance /= static_cast<float>(history_.size());
    float stdDev = std::sqrt(variance);

    // Adapt measurement noise (R) based on recent stability
    R_ = std::clamp(50.0f + stdDev * 0.5f, 20.0f, 500.0f);

    // Adapt process noise (Q) based on depth change rate
    float recentChange = std::abs(measurement - history_.back());

    if (recentChange > 100.0f) {
        Q_ = 100.0f;  // Fast motion
    } else if (recentChange > 50.0f) {
        Q_ = 60.0f;   // Moderate motion
    } else if (recentChange > 20.0f) {
        Q_ = 40.0f;   // Slow motion
    } else {
        Q_ = 20.0f;   // Stable scene
    }
}

} // namespace alice
