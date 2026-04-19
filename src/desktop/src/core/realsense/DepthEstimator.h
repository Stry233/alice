#pragma once

#include <cstdint>

namespace alice {

/**
 * Single-point depth estimator for autofocus-grade measurement.
 *
 * Pipeline: spatial ROI median → confidence-weighted temporal EMA
 * with discontinuity detection and position-aware reset.
 *
 * Thread safety: not internally synchronized. Callers must serialize.
 */
class DepthEstimator {
public:
    struct Reading {
        float valueM = 0.0f;
        float confidence = 0.0f;
        bool isValid = false;
        int staleFrames = 0;
    };

    /**
     * Process one depth frame at the given target position.
     *
     * @param depthData     Z16 depth buffer, row-major, w*h elements.
     * @param width,height  Frame dimensions.
     * @param targetNormX   Target x in [0,1].
     * @param targetNormY   Target y in [0,1].
     * @return Filtered reading. Check isValid before publishing.
     */
    Reading process(const uint16_t *depthData, int width, int height,
                    float targetNormX, float targetNormY);

    void reset();

    /** Valid-depth range in mm. Readings outside are treated as holes. */
    void setValidRange(int minMm, int maxMm);

    /**
     * Metres per raw Z16 unit. Default 0.001 (1 unit = 1 mm) matches
     * librealsense's D4xx default; 0.0001 unlocks sub-mm precision when
     * the sensor's DEPTH_UNITS option is reconfigured. Call once after
     * pipeline.start() with rs2::depth_sensor::get_depth_scale().
     */
    void setDepthScale(float metersPerUnit);
    float depthScale() const { return depthScale_; }

    /**
     * Temporal smoothing base.
     *   0.05 — very smooth, slow response (~1 s to 95 % settled)
     *   0.30 — default, balanced
     *   0.80 — very responsive, little smoothing
     * Actual per-frame alpha = base * measurement_confidence.
     */
    void setSmoothing(float baseAlpha);
    float smoothing() const { return baseAlpha_; }

private:
    static constexpr int kRoiRadius = 3;                 // 7×7 window
    // 4 pixels is the minimum for a defined IQR. At 4-5 m the D4xx
    // stereo matcher often returns only 4-6 valid pixels in a small
    // ROI on low-texture subjects, so a stricter threshold would mark
    // legitimate readings invalid and the UI would freeze on stale.
    static constexpr int kMinValidPixels = 4;
    static constexpr float kDiscontinuityDelta = 0.25f;
    static constexpr float kDiscontinuityEvidence = 0.15f;
    static constexpr float kPositionResetThresholdSq = 4e-4f; // 2 % of frame
    static constexpr int kMaxStaleFrames = 30;
    static constexpr float kTemporalConfGrowth = 0.15f;

    float stateM_ = 0.0f;
    bool initialized_ = false;
    int staleFrames_ = 0;
    float lastTargetX_ = -1.0f;
    float lastTargetY_ = -1.0f;

    // Grows with stable updates; resets on discontinuity / init. Output
    // confidence is max(spatial, temporal) so settled tracking stays
    // confident even when instantaneous ROI quality dips.
    float temporalConf_ = 0.0f;

    int minValidMm_ = 150;
    int maxValidMm_ = 10000;
    float baseAlpha_ = 0.30f;
    float depthScale_ = 0.001f;

    struct SampleResult {
        float medianM;
        float spatialConfidence;
        bool valid;
    };
    SampleResult sampleROI(const uint16_t *depthData, int width, int height,
                           int cx, int cy) const;
};

} // namespace alice
