#include "core/realsense/BilateralFilter.h"
#include <algorithm>
#include <cmath>

namespace alice {

BilateralFilter::BilateralFilter(float spatialSigma, float rangeSigma)
    : spatialSigma_(spatialSigma)
    , rangeSigma_(rangeSigma) {
    // Precompute the spatial gaussian term once — it depends only on the
    // fixed kernel offsets and spatialSigma_. Saves 25 sqrt + 25 exp calls
    // per invocation at runtime for the default radius-2 kernel.
    for (int dy = -kMaxRadius; dy <= kMaxRadius; ++dy) {
        for (int dx = -kMaxRadius; dx <= kMaxRadius; ++dx) {
            const float dist = std::sqrt(static_cast<float>(dx * dx + dy * dy));
            spatialWeights_[dy + kMaxRadius][dx + kMaxRadius] =
                gaussian(dist, spatialSigma_);
        }
    }
}

float BilateralFilter::filter(const uint16_t *depthData, int width, int height,
                               int centerX, int centerY, int radius) const {
    int centerIdx = centerY * width + centerX;
    if (centerIdx >= width * height) return 0.0f;

    float centerDepth = static_cast<float>(depthData[centerIdx]);
    if (centerDepth <= 0.0f) return 0.0f;

    // Clamp the runtime radius to the precomputed table size.
    const int r = std::min(radius, kMaxRadius);

    // The inv-2σ² range term is pulled out of the inner loop so the per-sample
    // gaussian() call collapses to a single multiply + exp.
    const float rangeInv2SigmaSq = 1.0f / (2.0f * rangeSigma_ * rangeSigma_);

    float weightedSum = 0.0f;
    float weightSum = 0.0f;

    for (int dy = -r; dy <= r; ++dy) {
        const int y = centerY + dy;
        if (y < 0 || y >= height) continue;
        const uint16_t *row = depthData + y * width;
        const auto &wRow = spatialWeights_[dy + kMaxRadius];

        for (int dx = -r; dx <= r; ++dx) {
            const int x = centerX + dx;
            if (x < 0 || x >= width) continue;

            const float depth = static_cast<float>(row[x]);
            if (depth <= 0.0f) continue;

            const float spatialWeight = wRow[dx + kMaxRadius];

            const float rangeDist = depth - centerDepth;
            const float rangeWeight = std::exp(-(rangeDist * rangeDist) * rangeInv2SigmaSq);

            const float weight = spatialWeight * rangeWeight;
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
