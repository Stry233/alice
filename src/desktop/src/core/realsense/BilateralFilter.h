#pragma once

#include <cstdint>

namespace alice {

/**
 * Bilateral spatial filter for depth data.
 * Provides edge-preserving smoothing to reduce noise while maintaining sharp boundaries.
 * Ported from BilateralDepthFilter in KalmanDepthFilter.kt.
 */
class BilateralFilter {
public:
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
     * @param radius    Filter radius in pixels (default 2)
     * @return Filtered depth value at center in mm
     */
    float filter(const uint16_t *depthData, int width, int height,
                 int centerX, int centerY, int radius = 2) const;

private:
    static float gaussian(float distance, float sigma);

    float spatialSigma_;
    float rangeSigma_;
};

} // namespace alice
