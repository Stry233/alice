#pragma once

#include <cstdint>

namespace alice {

/**
 * Single-point depth estimator for autofocus-grade measurement.
 *
 * Pipeline: spatial ROI median  →  confidence-weighted temporal EMA
 *                                  with discontinuity detection and
 *                                  position-aware reset.
 *
 * This replaces the bilateral + Kalman chain used previously. Two
 * foundational reasons:
 *   1. Bilateral filters are edge-preserving smoothers for full-frame
 *      depth maps. At a single point with center-pixel dependency they
 *      fail whenever the center pixel is a sensor hole (common at 4m+).
 *      Median survives any hole pattern as long as enough neighbors are
 *      valid.
 *   2. Kalman adaptive-noise estimation raises R during exactly the
 *      transitions it should accept. A user-tunable EMA with explicit
 *      discontinuity detection is both simpler and more predictable.
 *
 * Thread safety: not internally synchronized. Callers must serialize.
 */
class DepthEstimator {
public:
    struct Reading {
        float valueM = 0.0f;       // Smoothed depth in meters (0 if invalid).
        float confidence = 0.0f;   // 0-1 combined spatial + temporal confidence.
        bool isValid = false;      // True iff caller should publish this reading.
        int staleFrames = 0;       // Frames since last valid measurement.
    };

    /**
     * Process one depth frame at the given target position.
     *
     * @param depthData      Z16 depth buffer (mm), row-major, w*h elements.
     * @param width,height   Frame dimensions.
     * @param targetNormX    Target x in [0,1].
     * @param targetNormY    Target y in [0,1].
     * @return Filtered reading. Check isValid before publishing.
     */
    Reading process(const uint16_t *depthData, int width, int height,
                    float targetNormX, float targetNormY);

    /** Reset all temporal state (e.g., on device reconnect). */
    void reset();

    /** Valid-depth range in mm. Readings outside are treated as holes. */
    void setValidRange(int minMm, int maxMm);

    /**
     * Depth scale: metres per raw Z16 unit. librealsense's default for
     * D4xx is 0.001 (one unit = 1 mm). Setting the sensor's
     * DEPTH_UNITS option to 0.0001 gives 0.1 mm per unit — a 10× finer
     * quantization the RealSense firmware is capable of producing
     * internally from sub-pixel disparity. Combined with the temporal
     * filter below, that unlocks sub-millimetre precision on a
     * stationary subject.
     *
     * Call this once after pipeline.start() with the value returned by
     * rs2::depth_sensor::get_depth_scale(). Existing callers that
     * never call it get the legacy 0.001 default — all pre-scale test
     * data was expressed in mm-quantised uint16 and keeps working.
     */
    void setDepthScale(float metersPerUnit);
    float depthScale() const { return depthScale_; }

    /**
     * Temporal smoothing base.
     *   0.05  — very smooth, slow response (~1 s to 95 % settled)
     *   0.30  — default, balanced (cinema-appropriate)
     *   0.80  — very responsive, little smoothing
     * Actual per-frame alpha = base * measurement_confidence.
     */
    void setSmoothing(float baseAlpha);
    float smoothing() const { return baseAlpha_; }

private:
    // ── Tunables ────────────────────────────────────────────────────
    static constexpr int kRoiRadius = 3;              // 7×7 sample window
    static constexpr int kMinValidPixels = 8;         // require ≥8 of 49
    static constexpr float kDiscontinuityDelta = 0.25f;      // min 25 % rel. change
    static constexpr float kDiscontinuityEvidence = 0.15f;   // relΔ × conf bar
    static constexpr float kPositionResetThresholdSq = 4e-4f; // 2 % of frame
    static constexpr int kMaxStaleFrames = 30;        // ~1 s at 30 FPS
    static constexpr float kTemporalConfGrowth = 0.15f; // ~7 stable frames → 1.0

    // ── State ───────────────────────────────────────────────────────
    // Internal state is always in metres. Previous version used mm
    // (stateMm_) which quantized to 1 mm on every update even when the
    // input had finer precision — that hard floor defeated any sub-mm
    // benefit from the spatial / temporal filters applied upstream.
    float stateM_ = 0.0f;
    bool initialized_ = false;
    int staleFrames_ = 0;
    float lastTargetX_ = -1.0f;
    float lastTargetY_ = -1.0f;

    // Temporal tracking confidence. Grows toward 1 with each stable
    // update, resets on discontinuity / init / external reset(). The
    // reported output confidence is max(spatial, temporal) so once the
    // estimator has settled it reports high confidence even when the
    // instantaneous ROI quality dips (sparse valid pixels at 4 m+ etc.).
    // Mirrors the behaviour of the old 1-D Kalman's P-derived confidence
    // and is what autofocus consumers expect.
    float temporalConf_ = 0.0f;

    // ── Params ──────────────────────────────────────────────────────
    // Validity range is specified in mm (user-visible API) but compared
    // against raw Z16 units internally after dividing by depthScale_.
    // Keeping the public API in mm decouples it from the sensor's
    // depth-units option.
    int minValidMm_ = 150;
    int maxValidMm_ = 10000;
    float baseAlpha_ = 0.30f;

    // Metres per raw Z16 unit. 0.001 matches the librealsense default
    // (1 unit = 1 mm); set to 0.0001 by RealSenseManager when the
    // sensor's DEPTH_UNITS option can be reconfigured to 0.1 mm.
    float depthScale_ = 0.001f;

    // Sampling helpers
    struct SampleResult {
        float medianM;           // metres (with whatever sub-mm precision the scale affords)
        float spatialConfidence; // 0-1
        bool valid;
    };
    SampleResult sampleROI(const uint16_t *depthData, int width, int height,
                           int cx, int cy) const;
};

} // namespace alice
