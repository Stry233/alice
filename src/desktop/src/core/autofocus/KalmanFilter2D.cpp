#include "core/autofocus/KalmanFilter2D.h"
#include <cstring>

namespace alice {

KalmanFilter2D::KalmanFilter2D() {
    state_.fill(0.0f);
    P_.fill(0.0f);
    // Initial covariance: moderate uncertainty
    P_[0]  = 1.0f;  // P[0][0]
    P_[5]  = 1.0f;  // P[1][1]
    P_[10] = 1.0f;  // P[2][2]
    P_[15] = 1.0f;  // P[3][3]
}

void KalmanFilter2D::initialize(float x, float y) {
    state_ = {x, y, 0.0f, 0.0f};
    P_.fill(0.0f);
    P_[0]  = 1.0f;
    P_[5]  = 1.0f;
    P_[10] = 1.0f;
    P_[15] = 1.0f;
    initialized_ = true;
}

void KalmanFilter2D::predict(float dt) {
    if (!initialized_) return;

    // State transition matrix F:
    // [1 0 dt 0]
    // [0 1 0 dt]
    // [0 0 1  0]
    // [0 0 0  1]
    float F[16] = {
        1, 0, dt, 0,
        0, 1, 0,  dt,
        0, 0, 1,  0,
        0, 0, 0,  1
    };

    // Predict state: x = F * x
    std::array<float, 4> newState;
    newState[0] = state_[0] + dt * state_[2]; // x + vx*dt
    newState[1] = state_[1] + dt * state_[3]; // y + vy*dt
    newState[2] = state_[2];                   // vx
    newState[3] = state_[3];                   // vy
    state_ = newState;

    // Predict covariance: P = F*P*F' + Q
    float FT[16];
    mat4Transpose(F, FT);

    float FP[16];
    mat4Mul(F, P_.data(), FP);

    float FPFT[16];
    mat4Mul(FP, FT, FPFT);

    // Process noise Q (diagonal)
    float Q[16] = {};
    Q[0]  = kProcessNoisePosVar;  // x
    Q[5]  = kProcessNoisePosVar;  // y
    Q[10] = kProcessNoiseVelVar;  // vx
    Q[15] = kProcessNoiseVelVar;  // vy

    for (int i = 0; i < 16; ++i) {
        P_[i] = FPFT[i] + Q[i];
    }
}

void KalmanFilter2D::update(float measX, float measY) {
    if (!initialized_) {
        initialize(measX, measY);
        return;
    }

    // Measurement matrix H (2x4):
    // [1 0 0 0]
    // [0 1 0 0]
    // H*x = [x, y]

    // Innovation: y = z - H*x
    float y0 = measX - state_[0];
    float y1 = measY - state_[1];

    // Innovation covariance: S = H*P*H' + R  (2x2)
    // S[0][0] = P[0][0] + R
    // S[0][1] = P[0][1]
    // S[1][0] = P[1][0]
    // S[1][1] = P[1][1] + R
    float S[4] = {
        P_[0]  + kMeasurementNoiseVar, P_[1],
        P_[4]  ,                        P_[5] + kMeasurementNoiseVar
    };

    // Kalman gain: K = P*H' * S^{-1}  (4x2)
    // P*H' is the first two columns of P (since H' is [[1,0],[0,1],[0,0],[0,0]])
    float PHt[8] = {
        P_[0],  P_[1],   // row 0
        P_[4],  P_[5],   // row 1
        P_[8],  P_[9],   // row 2
        P_[12], P_[13]   // row 3
    };

    float Sinv[4];
    if (!mat2Inv(S, Sinv)) return; // Singular — skip update

    // K = PHt * Sinv  (4x2 * 2x2 = 4x2)
    float K[8];
    for (int i = 0; i < 4; ++i) {
        K[i * 2 + 0] = PHt[i * 2 + 0] * Sinv[0] + PHt[i * 2 + 1] * Sinv[2];
        K[i * 2 + 1] = PHt[i * 2 + 0] * Sinv[1] + PHt[i * 2 + 1] * Sinv[3];
    }

    // Update state: x += K * y
    for (int i = 0; i < 4; ++i) {
        state_[i] += K[i * 2 + 0] * y0 + K[i * 2 + 1] * y1;
    }

    // Update covariance: P = (I - K*H) * P
    // K*H is 4x4, where KH[i][j] = K[i][0]*H[0][j] + K[i][1]*H[1][j]
    // Since H = [[1,0,0,0],[0,1,0,0]]:
    // KH[i][0] = K[i][0], KH[i][1] = K[i][1], KH[i][2..3] = 0
    float IKH[16];
    for (int i = 0; i < 4; ++i) {
        for (int j = 0; j < 4; ++j) {
            float kh = 0.0f;
            if (j == 0) kh = K[i * 2 + 0];
            if (j == 1) kh = K[i * 2 + 1];
            IKH[i * 4 + j] = ((i == j) ? 1.0f : 0.0f) - kh;
        }
    }

    float newP[16];
    mat4Mul(IKH, P_.data(), newP);
    std::memcpy(P_.data(), newP, sizeof(newP));
}

void KalmanFilter2D::getPosition(float &x, float &y) const {
    x = state_[0];
    y = state_[1];
}

void KalmanFilter2D::getVelocity(float &vx, float &vy) const {
    vx = state_[2];
    vy = state_[3];
}

// --- Matrix helpers (4x4, row-major) ---

void KalmanFilter2D::mat4Mul(const float *A, const float *B, float *C) {
    for (int i = 0; i < 4; ++i) {
        for (int j = 0; j < 4; ++j) {
            float sum = 0.0f;
            for (int k = 0; k < 4; ++k) {
                sum += A[i * 4 + k] * B[k * 4 + j];
            }
            C[i * 4 + j] = sum;
        }
    }
}

void KalmanFilter2D::mat4Transpose(const float *A, float *AT) {
    for (int i = 0; i < 4; ++i)
        for (int j = 0; j < 4; ++j)
            AT[j * 4 + i] = A[i * 4 + j];
}

bool KalmanFilter2D::mat2Inv(const float *A, float *Ainv) {
    float det = A[0] * A[3] - A[1] * A[2];
    if (std::abs(det) < 1e-10f) return false;
    float invDet = 1.0f / det;
    Ainv[0] =  A[3] * invDet;
    Ainv[1] = -A[1] * invDet;
    Ainv[2] = -A[2] * invDet;
    Ainv[3] =  A[0] * invDet;
    return true;
}

} // namespace alice
