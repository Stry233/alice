#pragma once

#include <array>
#include <cstdint>

namespace alice {

/**
 * Bilateral spatial filter for depth data.
 *
 * Provides edge-preserving smoothing that reduces noise at flat surfaces
 * while preserving sharp depth discontinuities (e.g. foreground/background
 * edges). Uses a precomputed 9x9 spatial-weight LUT to avoid per-sample
 * exp() calls in the inner loop.
 */
class BilateralFilter {
public:
    // Upper bound on the supported filter radius. The spatial-weight table
    // is sized for this; runtime `radius` values larger than this will be
    // clamped.
    static constexpr int kMaxRadius = 4;
    static constexpr int kTableSize = 2 * kMaxRadius + 1;

    /**
     * @param spatialSigma Gaussian sigma for spatial distance (pixels)
     * @param rangeSigma   Gaussian sigma for depth difference (mm)
     */
    explicit BilateralFilter(float spatialSigma = 2.0f, float rangeSigma = 50.0f);

    /**
     * Apply bilateral filter to a region of depth data.
     * @param depthData Depth values in mm (unsigned 16-bit stored as uint16_t)
     * @param width     Width of the depth image
     * @param height    Height of the depth image
     * @param centerX   X coordinate of the center pixel
     * @param centerY   Y coordinate of the center pixel
     * @param radius    Filter radius in pixels (default 2, capped at kMaxRadius)
     * @return Filtered depth value at center in mm
     */
    float filter(const uint16_t *depthData, int width, int height,
                 int centerX, int centerY, int radius = 2) const;

private:
    static float gaussian(float distance, float sigma);

    float spatialSigma_;
    float rangeSigma_;

    // Precomputed spatial gaussian weights, indexed by [dy+kMaxRadius][dx+kMaxRadius].
    // The spatial term depends only on (dx, dy) and `spatialSigma_`, so computing
    // it 25 times per frame (for a radius-2 kernel) would waste 25 sqrts + 25 exps.
    std::array<std::array<float, kTableSize>, kTableSize> spatialWeights_{};
};

} // namespace alice
