package com.selkacraft.alice.comm.realsense

import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Single-point depth estimator for autofocus-grade measurement.
 *
 * Pipeline: spatial ROI median  →  confidence-weighted temporal EMA
 *                                  with discontinuity detection and
 *                                  position-aware reset.
 *
 * This replaces the bilateral + Kalman chain used previously. Foundational
 * reasons, validated by reproduction tests on the desktop port:
 *
 *   1. Bilateral filters are edge-preserving smoothers for full-frame
 *      depth maps. At a single point they fail whenever the center pixel
 *      is a sensor hole — common at 4 m+, where most readings come from
 *      sparse valid neighbours. Median-of-valid-pixels survives any hole
 *      pattern as long as ≥ 8 ROI pixels are valid.
 *
 *   2. Kalman adaptive-noise estimation raises R during exactly the
 *      transitions it should accept. The history buffer briefly contains
 *      both old and new values, stdDev spikes, the gain collapses, and
 *      new readings barely move the state. A confidence-weighted EMA
 *      with an explicit discontinuity gate is simpler and more robust.
 */
class RealSenseDepthCalculator {
    companion object {
        private const val TAG = "RealSenseDepthCalculator"

        // ── Tunables ────────────────────────────────────────────────
        private const val ROI_RADIUS = 3                       // 7×7 window
        private const val ROI_CAPACITY = (2 * ROI_RADIUS + 1) * (2 * ROI_RADIUS + 1)
        private const val MIN_VALID_PIXELS = 8                 // need ≥ 8 of 49
        private const val DISCONTINUITY_DELTA = 0.25f          // min 25% rel. change
        private const val DISCONTINUITY_EVIDENCE = 0.15f       // relΔ × conf bar
        private const val POSITION_RESET_THRESHOLD_SQ = 4e-4f  // 2% of frame
        private const val MAX_STALE_FRAMES = 30                // ~1 s at 30 FPS
        private const val DEFAULT_BASE_ALPHA = 0.30f           // balanced smoothing
        private const val TEMPORAL_CONF_GROWTH = 0.15f         // ~7 stable frames → 1.0
    }

    // ── State (temporal) ────────────────────────────────────────────
    private var stateMm = 0f
    private var initialized = false
    private var staleFrames = 0
    private var lastTargetX = -1f
    private var lastTargetY = -1f
    // Temporal tracking confidence: grows toward 1 with each successive
    // stable update, resets on discontinuity / init / external reset().
    // The reported output confidence is max(spatial, temporal) so that
    // once the estimator has settled, transient ROI-quality dips (sparse
    // valid pixels at 4 m+) don't depress confidence below autofocus
    // gating thresholds. Mirrors the old 1-D Kalman's P-decay curve.
    private var temporalConf = 0f

    // ── Params ──────────────────────────────────────────────────────
    private var minValidMm = RealSenseParameters.MIN_VALID_DEPTH
    private var maxValidMm = RealSenseParameters.MAX_VALID_DEPTH
    private var baseAlpha = DEFAULT_BASE_ALPHA

    /**
     * Calculate depth at the given normalized position.
     *
     * Returns a `DepthMeasurement` where `depth == 0f && confidence == 0f`
     * signals "no valid reading this frame." When the sensor has gone dark
     * but the estimator has a recent history, it will keep reporting the
     * last smoothed value with a decayed confidence until `MAX_STALE_FRAMES`
     * elapse; during this window the field `confidence` shrinks toward 0
     * so the UI can reflect staleness.
     */
    fun calculateDepth(
        depthFrame: ShortArray,
        width: Int,
        height: Int,
        normalizedX: Float,
        normalizedY: Float,
    ): DepthMeasurement {
        // ── Input validation ──────────────────────────────────────────
        if (depthFrame.isEmpty() || width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid input: empty frame or dimensions (w=$width, h=$height)")
            return DepthMeasurement(0f, 0f, normalizedX, normalizedY)
        }
        if (!normalizedX.isFinite() || !normalizedY.isFinite() ||
            normalizedX < 0f || normalizedX > 1f ||
            normalizedY < 0f || normalizedY > 1f) {
            Log.w(TAG, "Invalid normalized coordinates: ($normalizedX, $normalizedY)")
            return DepthMeasurement(0f, 0f, 0.5f, 0.5f)
        }
        if (depthFrame.size < width * height) {
            Log.w(TAG, "Frame size mismatch: got ${depthFrame.size}, expected ${width * height}")
            return DepthMeasurement(0f, 0f, normalizedX, normalizedY)
        }

        // ── 1. Position-change reset ───────────────────────────────────
        // The state is the depth AT the last target. If the target moves,
        // that value is meaningless and must not bias the new reading.
        if (lastTargetX >= 0f) {
            val dx = normalizedX - lastTargetX
            val dy = normalizedY - lastTargetY
            if (dx * dx + dy * dy > POSITION_RESET_THRESHOLD_SQ) {
                initialized = false
                staleFrames = 0
            }
        }
        lastTargetX = normalizedX
        lastTargetY = normalizedY

        // ── 2. Spatial sampling (median of valid pixels in 7×7 ROI) ───
        val centerX = (normalizedX * width).toInt().coerceIn(0, width - 1)
        val centerY = (normalizedY * height).toInt().coerceIn(0, height - 1)
        val sample = sampleROI(depthFrame, width, height, centerX, centerY)

        if (!sample.valid) {
            // Not enough valid data. Hold the last state if we have one,
            // but surface it as stale so the UI can distinguish "old
            // reading" from "fresh reading."
            staleFrames++
            return if (initialized && staleFrames < MAX_STALE_FRAMES) {
                val decay = max(0f, 1f - staleFrames.toFloat() / MAX_STALE_FRAMES)
                DepthMeasurement(stateMm / 1000f, temporalConf * decay,
                                 normalizedX, normalizedY)
            } else {
                DepthMeasurement(0f, 0f, normalizedX, normalizedY)
            }
        }
        staleFrames = 0

        // ── 3. Temporal filter ─────────────────────────────────────────
        val measurementMm = sample.medianMm
        val spatialConf = sample.spatialConfidence

        if (!initialized) {
            // Fresh start: first valid reading IS the state. Temporal
            // confidence starts at 0 — one sample is not enough to trust;
            // the output falls back to spatial for this frame.
            stateMm = measurementMm
            temporalConf = 0f
            initialized = true
        } else {
            // Discontinuity gate. The product relDelta × spatialConf acts
            // as a statistical-evidence threshold: huge deltas with
            // moderate confidence still snap; small deltas require high
            // confidence (otherwise they could be noise).
            val relDelta = abs(measurementMm - stateMm) / max(stateMm, 1f)
            val discontinuity = relDelta > DISCONTINUITY_DELTA &&
                                relDelta * spatialConf > DISCONTINUITY_EVIDENCE
            if (discontinuity) {
                stateMm = measurementMm
                temporalConf = 0f
            } else {
                // Normal tracking: confidence-weighted EMA on state, and
                // separate growth on temporal confidence — saturates near
                // 1.0 in ~7 stable frames, matching the old Kalman curve.
                val alpha = (baseAlpha * spatialConf).coerceIn(0f, 1f)
                stateMm = alpha * measurementMm + (1f - alpha) * stateMm
                temporalConf = min(1f, temporalConf + TEMPORAL_CONF_GROWTH)
            }
        }

        // Output max(spatial, temporal) — once tracking has settled the
        // temporal term dominates (≈ 1.0), so autofocus consumers don't
        // see spurious low-confidence readings during transient ROI dips.
        val outputConf = max(spatialConf, temporalConf).coerceIn(0f, 1f)
        return DepthMeasurement(stateMm / 1000f, outputConf,
                                normalizedX, normalizedY)
    }

    /**
     * Reset all temporal state. Called on reconnect, user-initiated reset,
     * or when the consumer wants to abandon the current track.
     */
    fun reset() {
        stateMm = 0f
        initialized = false
        staleFrames = 0
        lastTargetX = -1f
        lastTargetY = -1f
        temporalConf = 0f
        Log.d(TAG, "Depth estimator reset")
    }

    /** Valid-depth range in mm. Readings outside are treated as holes. */
    fun setValidRange(minMm: Int, maxMm: Int) {
        minValidMm = max(50, minMm)
        maxValidMm = max(minValidMm + 100, maxMm)
    }

    /**
     * Temporal smoothing base:
     *   0.05  — very smooth, ~1 s to 95 % settled
     *   0.30  — default, balanced
     *   0.80  — very responsive, little smoothing
     */
    fun setSmoothing(alpha: Float) {
        baseAlpha = alpha.coerceIn(0.02f, 1f)
    }

    // ── Backwards-compat shims (no-ops in the new pipeline) ─────────
    @Deprecated("Bilateral filter replaced by ROI median; flag has no effect")
    fun setSpatialFilteringEnabled(enabled: Boolean) { /* no-op */ }

    @Deprecated("ROI is now fixed-size for predictable latency; flag has no effect")
    fun setAdaptiveROIEnabled(enabled: Boolean) { /* no-op */ }

    /** Current smoothed depth estimate in meters (0 if uninitialized). */
    fun getCurrentDepth(): Float = if (initialized) stateMm / 1000f else 0f

    // ── Internal helpers ────────────────────────────────────────────
    private data class SampleResult(
        val medianMm: Float,
        val spatialConfidence: Float,
        val valid: Boolean,
    )

    private fun sampleROI(
        depthFrame: ShortArray, width: Int, height: Int,
        cx: Int, cy: Int,
    ): SampleResult {
        val samples = IntArray(ROI_CAPACITY)
        var n = 0

        val x0 = max(0, cx - ROI_RADIUS)
        val x1 = min(width - 1, cx + ROI_RADIUS)
        val y0 = max(0, cy - ROI_RADIUS)
        val y1 = min(height - 1, cy + ROI_RADIUS)

        for (y in y0..y1) {
            val rowBase = y * width
            for (x in x0..x1) {
                val d = depthFrame[rowBase + x].toInt() and 0xFFFF
                if (d in minValidMm..maxValidMm) {
                    samples[n++] = d
                }
            }
        }

        if (n < MIN_VALID_PIXELS) {
            return SampleResult(0f, 0f, false)
        }

        // Sort the populated prefix and pull percentile values.
        java.util.Arrays.sort(samples, 0, n)
        val median = samples[n / 2].toFloat()
        val q25 = samples[n / 4].toFloat()
        val q75 = samples[(3 * n) / 4].toFloat()

        // Spatial confidence combines two signals:
        //   validity  = min(validRatio × 2, 1). Saturates at 50 % valid
        //               pixels — above that the median's reliability is
        //               already maxed; extra pixels only tighten variance
        //               (captured by tightness).
        //   tightness = 1 - (IQR / median) × 4, clamped. ROI straddling a
        //               depth edge → wide IQR → low trust, regardless of
        //               how many pixels were valid.
        val validRatio = n.toFloat() / ROI_CAPACITY
        val validity = min(validRatio * 2f, 1f)
        val normalizedIqr = if (median > 1f) (q75 - q25) / median else 1f
        val tightness = (1f - normalizedIqr * 4f).coerceIn(0f, 1f)

        return SampleResult(median, validity * tightness, true)
    }
}
