#include "core/realsense/BilateralFilter.h"
#include <cmath>

namespace alice {

BilateralFilter::BilateralFilter(float spatialSigma, float rangeSigma)
    : spatialSigma_(spatialSigma)
    , rangeSigma_(rangeSigma) {}

float BilateralFilter::filter(const uint16_t *depthData, int width, int height,
                               int centerX, int centerY, int radius) const {
    int centerIdx = centerY * width + centerX;
    if (centerIdx >= width * height) return 0.0f;

    float centerDepth = static_cast<float>(depthData[centerIdx]);
    if (centerDepth <= 0.0f) return 0.0f;

    float weightedSum = 0.0f;
    float weightSum = 0.0f;

    for (int dy = -radius; dy <= radius; ++dy) {
        for (int dx = -radius; dx <= radius; ++dx) {
            int x = centerX + dx;
            int y = centerY + dy;

            if (x < 0 || x >= width || y < 0 || y >= height) continue;

            int idx = y * width + x;
            float depth = static_cast<float>(depthData[idx]);
            if (depth <= 0.0f) continue;

            // Spatial weight (based on pixel distance)
            float spatialDist = std::sqrt(static_cast<float>(dx * dx + dy * dy));
            float spatialWeight = gaussian(spatialDist, spatialSigma_);

            // Range weight (based on depth difference)
            float rangeDist = std::abs(depth - centerDepth);
            float rangeWeight = gaussian(rangeDist, rangeSigma_);

            float weight = spatialWeight * rangeWeight;
            weightedSum += depth * weight;
            weightSum += weight;
        }
    }

    return (weightSum > 0.0f) ? (weightedSum / weightSum) : centerDepth;
}

float BilateralFilter::gaussian(float distance, float sigma) {
    return std::exp(-(distance * distance) / (2.0f * sigma * sigma));
}

} // namespace alice
