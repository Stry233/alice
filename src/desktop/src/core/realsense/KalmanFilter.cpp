#include "core/realsense/KalmanFilter.h"

namespace alice {

KalmanFilter::KalmanFilter() = default;

void KalmanFilter::pushHistory(float value) {
    history_[historyHead_] = value;
    historyHead_ = (historyHead_ + 1) % kHistorySize;
    if (historyCount_ < kHistorySize) ++historyCount_;
}

float KalmanFilter::lastHistory() const {
    // Most recently inserted value — the slot one before the head,
    // wrapping around. Caller must ensure historyCount_ > 0.
    const int idx = (historyHead_ - 1 + kHistorySize) % kHistorySize;
    return history_[idx];
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
        pushHistory(measurement);
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

    // Add to history (O(1) ring-buffer insert)
    pushHistory(measurement);

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
    historyCount_ = 0;
    historyHead_ = 0;
    Q_ = 50.0f;
    R_ = 100.0f;
}

bool KalmanFilter::isReady() const {
    return initialized_;
}

void KalmanFilter::adaptNoiseParameters(float measurement) {
    if (historyCount_ < 3) return;

    // Mean and variance are order-invariant, so we can just iterate the
    // valid slots of the ring buffer in index order without reconstructing
    // the chronological sequence.
    float sum = 0.0f;
    for (int i = 0; i < historyCount_; ++i) sum += history_[i];
    const float invN = 1.0f / static_cast<float>(historyCount_);
    const float mean = sum * invN;

    float variance = 0.0f;
    for (int i = 0; i < historyCount_; ++i) {
        const float diff = history_[i] - mean;
        variance += diff * diff;
    }
    variance *= invN;
    const float stdDev = std::sqrt(variance);

    // Adapt measurement noise (R) based on recent stability
    R_ = std::clamp(50.0f + stdDev * 0.5f, 20.0f, 500.0f);

    // Adapt process noise (Q) based on depth change rate
    const float recentChange = std::abs(measurement - lastHistory());

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
