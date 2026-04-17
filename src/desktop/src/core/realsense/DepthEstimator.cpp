#include "core/realsense/DepthEstimator.h"

#include <algorithm>
#include <cmath>

namespace alice {

void DepthEstimator::reset() {
    stateMm_ = 0.0f;
    initialized_ = false;
    staleFrames_ = 0;
    lastTargetX_ = -1.0f;
    lastTargetY_ = -1.0f;
    lastConfidence_ = 0.0f;
}

void DepthEstimator::setValidRange(int minMm, int maxMm) {
    minValidMm_ = std::max(50, minMm);
    maxValidMm_ = std::max(minValidMm_ + 100, maxMm);
}

void DepthEstimator::setSmoothing(float baseAlpha) {
    baseAlpha_ = std::clamp(baseAlpha, 0.02f, 1.0f);
}

DepthEstimator::SampleResult DepthEstimator::sampleROI(
        const uint16_t *depthData, int width, int height,
        int cx, int cy) const {
    // Collect valid pixels from a (2r+1)² window. Validity = in-range,
    // non-zero. We do NOT require the center pixel to be valid — that
    // was the central failure mode of the bilateral approach at far
    // ranges where the center is commonly a sensor hole.
    constexpr int r = kRoiRadius;
    constexpr int kCapacity = (2 * r + 1) * (2 * r + 1); // 49
    uint16_t samples[kCapacity];
    int n = 0;

    const int x0 = std::max(0, cx - r);
    const int x1 = std::min(width - 1, cx + r);
    const int y0 = std::max(0, cy - r);
    const int y1 = std::min(height - 1, cy + r);

    const uint16_t minMm = static_cast<uint16_t>(minValidMm_);
    const uint16_t maxMm = static_cast<uint16_t>(maxValidMm_);

    for (int y = y0; y <= y1; ++y) {
        const uint16_t *row = depthData + y * width;
        for (int x = x0; x <= x1; ++x) {
            const uint16_t d = row[x];
            if (d >= minMm && d <= maxMm) {
                samples[n++] = d;
            }
        }
    }

    SampleResult result;
    if (n < kMinValidPixels) {
        result.medianMm = 0.0f;
        result.spatialConfidence = 0.0f;
        result.valid = false;
        return result;
    }

    // Median and inter-quartile range via partial sort.
    std::sort(samples, samples + n);
    const float median = static_cast<float>(samples[n / 2]);
    const float q25 = static_cast<float>(samples[n / 4]);
    const float q75 = static_cast<float>(samples[(3 * n) / 4]);

    // Spatial confidence = validRatio × tightness.
    //   validRatio : fraction of ROI pixels that produced a valid reading.
    //                Low ratio → depth is patchy here (edge, hole, specular).
    //   tightness  : 1 - IQR/median, clamped. Measures how tightly the
    //                valid pixels cluster around the median. High IQR (scene
    //                has a depth discontinuity within the ROI) → low trust.
    const float validRatio = static_cast<float>(n) / static_cast<float>(kCapacity);
    const float iqr = q75 - q25;
    const float normalizedIqr = (median > 1.0f) ? (iqr / median) : 1.0f;
    const float tightness = std::clamp(1.0f - normalizedIqr * 4.0f, 0.0f, 1.0f);

    result.medianMm = median;
    result.spatialConfidence = validRatio * tightness;
    result.valid = true;
    return result;
}

DepthEstimator::Reading DepthEstimator::process(
        const uint16_t *depthData, int width, int height,
        float targetNormX, float targetNormY) {
    Reading out;

    // ── 1. Detect measurement-position changes ────────────────────────
    // The temporal state is spatially anchored: it represents the depth
    // AT the last target point. When the target moves to a new location,
    // the state is meaningless and must be discarded — otherwise the
    // filter fights the new reading with stale context.
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

    // ── 2. Spatial sampling ────────────────────────────────────────────
    const int centerX = std::clamp(
        static_cast<int>(targetNormX * width), 0, width - 1);
    const int centerY = std::clamp(
        static_cast<int>(targetNormY * height), 0, height - 1);

    const SampleResult sample = sampleROI(depthData, width, height,
                                          centerX, centerY);

    if (!sample.valid) {
        // Not enough valid pixels. Hold the last state if we have one,
        // but mark the reading as stale so the UI can reflect that.
        ++staleFrames_;
        if (initialized_ && staleFrames_ < kMaxStaleFrames) {
            out.valueM = stateMm_ / 1000.0f;
            out.confidence = lastConfidence_ *
                std::max(0.0f, 1.0f - static_cast<float>(staleFrames_)
                                       / kMaxStaleFrames);
            out.staleFrames = staleFrames_;
            out.isValid = false; // stale — caller shouldn't re-emit
        }
        return out;
    }

    staleFrames_ = 0;

    // ── 3. Temporal filtering ─────────────────────────────────────────
    const float measurementMm = sample.medianMm;
    const float spatialConf = sample.spatialConfidence;

    if (!initialized_) {
        // Fresh start: the first valid reading IS the state, and the
        // historical confidence is seeded with this frame's spatial
        // confidence (otherwise the 70/30 blend below would artificially
        // depress the very first reported confidence).
        stateMm_ = measurementMm;
        lastConfidence_ = spatialConf;
        initialized_ = true;
    } else {
        // Discontinuity check. Depth can change abruptly for legitimate
        // reasons: object leaves frame, user moves hand in front of lens,
        // scene cut. We want to snap to the new value — but only if the
        // measurement is confident enough that it's not just noise.
        const float relDelta = std::abs(measurementMm - stateMm_)
                             / std::max(stateMm_, 1.0f);
        const bool discontinuity =
            relDelta > kDiscontinuityDelta &&
            spatialConf > kDiscontinuityConfidence;

        if (discontinuity) {
            // Hard reset — new scene, new state.
            stateMm_ = measurementMm;
        } else {
            // Normal tracking: confidence-weighted EMA.
            // High-confidence measurements pull the state quickly;
            // low-confidence ones contribute little, preserving stability
            // during patchy sensor data.
            const float alpha = std::clamp(baseAlpha_ * spatialConf, 0.0f, 1.0f);
            stateMm_ = alpha * measurementMm + (1.0f - alpha) * stateMm_;
        }
    }

    // ── 4. Output ──────────────────────────────────────────────────────
    // Combined confidence: spatial (this frame) × 0.7 + historical × 0.3.
    // Gives a smoothed confidence signal that doesn't flicker with
    // frame-to-frame ROI quality changes.
    const float combined = spatialConf * 0.7f + lastConfidence_ * 0.3f;
    lastConfidence_ = combined;

    out.valueM = stateMm_ / 1000.0f;
    out.confidence = std::clamp(combined, 0.0f, 1.0f);
    out.isValid = true;
    out.staleFrames = 0;
    return out;
}

} // namespace alice
