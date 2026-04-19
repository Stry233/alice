#pragma once

#include <array>
#include <cmath>

namespace alice {

/**
 * 2D Kalman filter for face tracking with constant-velocity model.
 * Ported from SubjectTracker.kt inner KalmanFilter2D class.
 *
 * State vector: [x, y, vx, vy]
 * Measurement vector: [x, y]
 */
class KalmanFilter2D {
public:
    KalmanFilter2D();

    /** Initialize the filter with the first observed position. */
    void initialize(float x, float y);

    /** Predict the next state (call before update each frame). */
    void predict(float dt = 1.0f / 30.0f);

    /** Update state with a new measurement. */
    void update(float measX, float measY);

    /** Get current estimated position. */
    void getPosition(float &x, float &y) const;

    /** Get current estimated velocity. */
    void getVelocity(float &vx, float &vy) const;

    /** Check if filter has been initialized. */
    bool isInitialized() const { return initialized_; }

private:
    // State [x, y, vx, vy]
    std::array<float, 4> state_{};

    // 4x4 error covariance matrix (stored row-major)
    std::array<float, 16> P_{};

    // Process noise
    static constexpr float kProcessNoisePosVar = 0.01f;
    static constexpr float kProcessNoiseVelVar = 0.02f;

    // Measurement noise
    static constexpr float kMeasurementNoiseVar = 0.05f;

    bool initialized_ = false;

    // Helper: 4x4 matrix multiply C = A * B (row-major)
    static void mat4Mul(const float *A, const float *B, float *C);
    // Helper: 4x4 matrix transpose
    static void mat4Transpose(const float *A, float *AT);
    // Helper: 2x2 matrix inverse
    static bool mat2Inv(const float *A, float *Ainv);
};

} // namespace alice
