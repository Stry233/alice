#include "core/realsense/DepthEstimator.h"

#include <algorithm>
#include <cmath>

namespace alice {

void DepthEstimator::reset() {
    stateM_ = 0.0f;
    initialized_ = false;
    staleFrames_ = 0;
    lastTargetX_ = -1.0f;
    lastTargetY_ = -1.0f;
    temporalConf_ = 0.0f;
}

void DepthEstimator::setValidRange(int minMm, int maxMm) {
    minValidMm_ = std::max(50, minMm);
    maxValidMm_ = std::max(minValidMm_ + 100, maxMm);
}

void DepthEstimator::setSmoothing(float baseAlpha) {
    baseAlpha_ = std::clamp(baseAlpha, 0.02f, 1.0f);
}

void DepthEstimator::setDepthScale(float metersPerUnit) {
    depthScale_ = std::clamp(metersPerUnit, 1e-5f, 1e-2f);
}

DepthEstimator::SampleResult DepthEstimator::sampleROI(
        const uint16_t *depthData, int width, int height,
        int cx, int cy) const {
    constexpr int r = kRoiRadius;
    constexpr int kCapacity = (2 * r + 1) * (2 * r + 1);
    uint16_t samples[kCapacity];
    int n = 0;

    const int x0 = std::max(0, cx - r);
    const int x1 = std::min(width - 1, cx + r);
    const int y0 = std::max(0, cy - r);
    const int y1 = std::min(height - 1, cy + r);

    // Convert mm thresholds to raw-unit thresholds. Ceil/floor keep the
    // comparison strictly conservative against sub-unit rounding.
    const float mmPerUnit = depthScale_ * 1000.0f;
    const uint16_t minUnits = static_cast<uint16_t>(
        std::clamp(std::ceil(minValidMm_ / mmPerUnit), 1.0f, 65535.0f));
    const uint16_t maxUnits = static_cast<uint16_t>(
        std::clamp(std::floor(maxValidMm_ / mmPerUnit), 1.0f, 65535.0f));

    for (int y = y0; y <= y1; ++y) {
        const uint16_t *row = depthData + y * width;
        for (int x = x0; x <= x1; ++x) {
            const uint16_t d = row[x];
            if (d >= minUnits && d <= maxUnits) samples[n++] = d;
        }
    }

    SampleResult result;
    if (n < kMinValidPixels) {
        result.medianM = 0.0f;
        result.spatialConfidence = 0.0f;
        result.valid = false;
        return result;
    }

    std::sort(samples, samples + n);
    const uint16_t medianUnits = samples[n / 2];
    const uint16_t q25Units    = samples[n / 4];
    const uint16_t q75Units    = samples[(3 * n) / 4];

    // Spatial confidence = validity (saturates at 50 % valid pixels)
    //                    × tightness (1 - IQR/median × 4, clamped).
    const float validRatio = static_cast<float>(n) / kCapacity;
    const float validity = std::min(validRatio * 2.0f, 1.0f);
    const float normalizedIqr = (medianUnits > 1)
        ? static_cast<float>(q75Units - q25Units) / medianUnits
        : 1.0f;
    const float tightness = std::clamp(1.0f - normalizedIqr * 4.0f, 0.0f, 1.0f);

    result.medianM = medianUnits * depthScale_;
    result.spatialConfidence = validity * tightness;
    result.valid = true;
    return result;
}

DepthEstimator::Reading DepthEstimator::process(
        const uint16_t *depthData, int width, int height,
        float targetNormX, float targetNormY) {
    Reading out;

    if (lastTargetX_ >= 0.0f) {
        const float dx = targetNormX - lastTargetX_;
        const float dy = targetNormY - lastTargetY_;
        if (dx * dx + dy * dy > kPositionResetThresholdSq) {
            initialized_ = false;
            staleFrames_ = 0;
        }
    }
    lastTargetX_ = targetNormX;
    lastTargetY_ = targetNormY;

    const int centerX = std::clamp(
        static_cast<int>(targetNormX * width), 0, width - 1);
    const int centerY = std::clamp(
        static_cast<int>(targetNormY * height), 0, height - 1);

    const SampleResult sample = sampleROI(depthData, width, height,
                                          centerX, centerY);

    if (!sample.valid) {
        ++staleFrames_;
        if (initialized_ && staleFrames_ < kMaxStaleFrames) {
            out.valueM = stateM_;
            out.confidence = temporalConf_ *
                std::max(0.0f, 1.0f - static_cast<float>(staleFrames_) / kMaxStaleFrames);
            out.staleFrames = staleFrames_;
            out.isValid = false;
        }
        return out;
    }
    staleFrames_ = 0;

    const float measurementM = sample.medianM;
    const float spatialConf = sample.spatialConfidence;

    if (!initialized_) {
        stateM_ = measurementM;
        temporalConf_ = 0.0f;
        initialized_ = true;
    } else {
        const float relDelta = std::abs(measurementM - stateM_)
                             / std::max(stateM_, 1e-6f);
        const bool discontinuity =
            relDelta > kDiscontinuityDelta &&
            relDelta * spatialConf > kDiscontinuityEvidence;

        if (discontinuity) {
            stateM_ = measurementM;
            temporalConf_ = 0.0f;
        } else {
            // Adaptive alpha: quiet scenes (relDelta ≤ 5 %) get heavy
            // smoothing; mid-range transitions (5 %–25 %) ramp up to 1.0
            // so the EMA doesn't lag sub-discontinuity steps.
            constexpr float kQuietRelDelta = 0.05f;
            const float effectiveAlpha = [&]() {
                const float alphaQuiet = baseAlpha_ * spatialConf;
                if (relDelta <= kQuietRelDelta) return alphaQuiet;
                const float t = std::clamp(
                    (relDelta - kQuietRelDelta) /
                        (kDiscontinuityDelta - kQuietRelDelta),
                    0.0f, 1.0f);
                return std::clamp(alphaQuiet + t * (1.0f - alphaQuiet),
                                  0.02f, 1.0f);
            }();
            stateM_ = effectiveAlpha * measurementM
                    + (1.0f - effectiveAlpha) * stateM_;
            temporalConf_ = std::min(1.0f, temporalConf_ + kTemporalConfGrowth);
        }
    }

    out.valueM = stateM_;
    out.confidence = std::clamp(std::max(spatialConf, temporalConf_), 0.0f, 1.0f);
    out.isValid = true;
    out.staleFrames = 0;
    return out;
}

} // namespace alice
