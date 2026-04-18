#include <gtest/gtest.h>
#include "core/realsense/DepthEstimator.h"
#include "core/autofocus/KalmanFilter2D.h"
#include <cstdint>
#include <vector>

using namespace alice;

// ── DepthEstimator ───────────────────────────────────────────────────
//
// Industry-grade single-point depth estimator: ROI median + confidence-
// weighted EMA with discontinuity detection and position-aware reset.

namespace {

// Build a synthetic W×H depth frame filled with a uniform value. Useful
// for the stable-tracking and baseline tests.
std::vector<uint16_t> makeUniformFrame(int w, int h, uint16_t value) {
    return std::vector<uint16_t>(static_cast<size_t>(w) * h, value);
}

// Build a frame with a specific rectangular region at one depth and the
// background at another. Simulates an object over a wall.
std::vector<uint16_t> makeTwoDepthFrame(int w, int h,
                                        uint16_t background, uint16_t patch,
                                        int px, int py, int pw, int ph) {
    std::vector<uint16_t> frame(static_cast<size_t>(w) * h, background);
    for (int y = py; y < py + ph && y < h; ++y) {
        for (int x = px; x < px + pw && x < w; ++x) {
            frame[y * w + x] = patch;
        }
    }
    return frame;
}

} // namespace

TEST(DepthEstimator, StableReadingOnUniformFrame) {
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 1500);

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(r.isValid);
    EXPECT_NEAR(r.valueM, 1.5f, 0.01f);
    EXPECT_GT(r.confidence, 0.8f); // clean frame — high confidence
}

TEST(DepthEstimator, SurvivesCenterPixelHole) {
    // The bilateral approach failed here: if the center pixel was 0
    // (common at 4 m+), it bailed out early. The median-of-valid-pixels
    // approach must succeed as long as enough neighbours are valid.
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 4000);
    const int cx = 320, cy = 240;
    frame[cy * 640 + cx] = 0;         // hole at exact center
    frame[(cy + 1) * 640 + cx] = 0;   // another hole
    frame[cy * 640 + (cx - 1)] = 0;   // and another

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(r.isValid);
    EXPECT_NEAR(r.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, RejectsFrameWithTooFewValidPixels) {
    // If almost every pixel in the ROI is a hole, the reading is
    // untrustworthy and must be marked invalid (not silently made up).
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 0);
    // Plant only a handful of valid pixels — below the kMinValidPixels
    // threshold (8).
    frame[240 * 640 + 320] = 3000;
    frame[240 * 640 + 321] = 3000;
    frame[241 * 640 + 320] = 3000;

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_FALSE(r.isValid);
}

TEST(DepthEstimator, RejectsOutOfRangePixels) {
    DepthEstimator est;
    est.setValidRange(200, 5000);
    auto frame = makeUniformFrame(640, 480, 8000); // beyond max

    auto r = est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_FALSE(r.isValid);
}

TEST(DepthEstimator, ExplicitResetAfterSmallPositionMoveIsTreatedAsJump) {
    // Press/click semantics: when the user taps a new location, the new
    // sample must be taken "independently" — with no blending against the
    // old crosshair's depth. This must hold even if the click lands close
    // to the previous position (delta below the auto-reset threshold),
    // because the user's intent is a teleport, not a drift.
    //
    // Callers distinguish press from drag and invoke reset() before the
    // first sample at the new target. This test verifies that pathway.
    DepthEstimator est;
    auto close = makeUniformFrame(640, 480, 500);
    for (int i = 0; i < 30; ++i) {
        est.process(close.data(), 640, 480, 0.5f, 0.5f);
    }

    // Simulate the user tapping near the current crosshair on a frame
    // where the scene has changed to 4000 mm. Without an explicit reset
    // the 1 % position delta would not trip the auto reset, and the
    // estimator would EMA-blend toward the new value instead of snapping.
    est.reset();
    auto far = makeUniformFrame(640, 480, 4000);
    auto r = est.process(far.data(), 640, 480, 0.51f, 0.5f);
    EXPECT_TRUE(r.isValid);
    EXPECT_NEAR(r.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, SlowDragAcrossTwoDepthRegionsSnapsIndependently) {
    // Reproduces the user-reported bug: when the crosshair is dragged
    // smoothly from one scene region to another (each per-frame delta is
    // below the reset threshold), the old depth lingers. The estimator
    // must treat ANY displacement that has accumulated past the threshold
    // since the last reset as a target change — not just frame-to-frame
    // jumps. Otherwise a 60-frame slow drag across the whole field of
    // view never triggers the reset.
    DepthEstimator est;

    // Left half of frame (x < 320) is at 500 mm, right half at 4000 mm.
    const int W = 640, H = 480;
    std::vector<uint16_t> frame(W * H, 0);
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            frame[y * W + x] = (x < 320) ? 500 : 4000;
        }
    }

    // Converge on the left region.
    for (int i = 0; i < 30; ++i) {
        est.process(frame.data(), W, H, 0.2f, 0.5f);
    }

    // Slow drag: 0.2 → 0.8 over 60 frames, 0.01 per frame. No single
    // frame's delta exceeds 0.02, so the previous "frame-to-frame" reset
    // check never fires.
    DepthEstimator::Reading last;
    for (int i = 1; i <= 60; ++i) {
        const float x = 0.2f + i * 0.01f;
        last = est.process(frame.data(), W, H, x, 0.5f);
    }

    // End position is firmly in the 4000 mm region. By the end of the drag
    // the reported depth must reflect the NEW region, not some EMA-blended
    // compromise with the stale 500 mm state.
    EXPECT_TRUE(last.isValid);
    EXPECT_NEAR(last.valueM, 4.0f, 0.1f)
        << "Slow drag must reach the new region. Got " << last.valueM
        << " m after crosshair moved 60 % of the frame";
}

// ── Diagnostic tests for the "drag leaks old context" bug ────────────
//
// The preceding SlowDragAcrossTwoDepthRegionsSnapsIndependently test
// passes because a 500→4000 mm jump trips the discontinuity gate
// (relDelta = 7.0, well above kDiscontinuityDelta = 0.25). Real scenes
// rarely produce such clean 8× steps — depths vary continuously across
// a wall, a product shot, or a dolly move. The tests below probe the
// behaviour that real footage exercises.

TEST(DepthEstimator, DragAcrossMildDepthGradientBlendsStaleContext) {
    // Scene: left half 1000 mm, right half 1200 mm (20 % relative step —
    // below kDiscontinuityDelta = 25 %). Converge at left, then drag to
    // the right at 1 %/frame. The discontinuity gate does NOT fire (step
    // is sub-threshold), so the EMA must carry the estimator to the new
    // value. Adaptive alpha helps, but this is where drift is visible
    // to the user.
    DepthEstimator est;
    const int W = 640, H = 480;
    std::vector<uint16_t> frame(W * H, 0);
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            frame[y * W + x] = (x < 320) ? 1000 : 1200;
        }
    }

    for (int i = 0; i < 30; ++i) {
        est.process(frame.data(), W, H, 0.2f, 0.5f);
    }

    DepthEstimator::Reading last;
    for (int i = 1; i <= 60; ++i) {
        last = est.process(frame.data(), W, H, 0.2f + i * 0.01f, 0.5f);
    }

    // Documented expectation: within 10 mm of the new region's true
    // depth. If this fails at e.g. 1.14 m the sub-threshold EMA is
    // dragging old context — exactly what "use no context when the
    // crosshair moves" is supposed to eliminate.
    EXPECT_NEAR(last.valueM, 1.2f, 0.01f)
        << "After a slow drag across a sub-discontinuity depth step, the "
        << "reading must settle at the new region. Got " << last.valueM
        << " m — stale-context blending suspected.";
}

TEST(DepthEstimator, ResetPlusStalePositionProcessLeaksToNextFrame) {
    // Simulates the UI/capture race the fix was meant to close:
    //
    //   T+0: UI thread calls setMeasurementPosition(NEW). The function
    //        writes measureX_/Y_ under positionMutex_, THEN takes
    //        estimatorParamsMutex_ and calls estimator_.reset().
    //
    //   T+1: Capture thread had already read mx/my BEFORE the UI wrote
    //        (they're on separate mutexes). It now waits on
    //        estimatorParamsMutex_ and, once released, calls
    //        estimator_.process() with the STALE (old) mx/my.
    //
    // Modelled here as: converge at OLD, reset(), process() at OLD,
    // process() at NEW (sub-threshold delta). The last reading is
    // what the user sees. If it carries 500 mm context it proves the
    // race is the culprit.
    DepthEstimator est;
    const int W = 640, H = 480;
    std::vector<uint16_t> leftFrame(W * H, 0);
    std::vector<uint16_t> rightFrame(W * H, 0);
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            leftFrame[y * W + x] = (x < 320) ? 1000 : 1200;
            rightFrame[y * W + x] = (x < 320) ? 1000 : 1200;
        }
    }

    // Converge at OLD (left region).
    for (int i = 0; i < 30; ++i) {
        est.process(leftFrame.data(), W, H, 0.20f, 0.5f);
    }
    auto before = est.process(leftFrame.data(), W, H, 0.20f, 0.5f);
    EXPECT_NEAR(before.valueM, 1.0f, 0.01f);

    // UI drag event: reset().
    est.reset();
    // Race: capture processes at the STALE OLD position.
    est.process(leftFrame.data(), W, H, 0.20f, 0.5f);
    // Next frame: capture sees the NEW position, 1 % further right.
    // Sub-threshold delta, sub-discontinuity depth step. The adaptive
    // EMA must carry the reading — but it's fighting stale state that
    // was re-installed by the racy call above.
    DepthEstimator::Reading last;
    for (int i = 1; i <= 1; ++i) {
        last = est.process(rightFrame.data(), W, H, 0.21f + i * 0.00f, 0.5f);
    }
    // A single post-race frame at the new position: how close to 1.0
    // does the reading stay? Anywhere meaningfully above 1.0 confirms
    // that state WAS re-initialised at the stale location.
    EXPECT_NEAR(last.valueM, 1.0f, 0.01f)
        << "After reset+stale-process race, stateM must reflect the "
        << "stale OLD-position depth. Got " << last.valueM;
}

TEST(DepthEstimator, ResetAtNewPositionDirectlyYieldsNewDepth) {
    // Control: when the UI reset is followed by a process() at the NEW
    // position (no race), the very first reading IS the new depth with
    // zero blending. This is what the fix is SUPPOSED to produce on the
    // happy path.
    DepthEstimator est;
    const int W = 640, H = 480;
    std::vector<uint16_t> frame(W * H, 0);
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            frame[y * W + x] = (x < 320) ? 1000 : 1200;
        }
    }

    for (int i = 0; i < 30; ++i) {
        est.process(frame.data(), W, H, 0.20f, 0.5f);
    }
    est.reset();
    // No race — capture's next process sees the new position.
    auto r = est.process(frame.data(), W, H, 0.60f, 0.5f);
    EXPECT_NEAR(r.valueM, 1.2f, 0.01f)
        << "Reset followed by process at NEW position must snap — this "
        << "is the happy path. Got " << r.valueM;
}

TEST(DepthEstimator, DragAtQuarterPerFrameAcrossGradientStaysStuck) {
    // Continuous drag at 0.25 %/frame across the whole frame over 240
    // frames (~8 s at 30 fps). Depth steps through a smooth gradient
    // so NO single frame sees a discontinuity — the reading relies
    // entirely on alpha-weighted EMA. This matches what a cinematographer
    // experiences painting the crosshair across a subject to find depth
    // on different facial features.
    DepthEstimator est;
    const int W = 640, H = 480;
    std::vector<uint16_t> frame(W * H, 0);
    // Linear gradient: x=0 → 800 mm, x=W-1 → 1800 mm.
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            frame[y * W + x] = static_cast<uint16_t>(
                800 + (1000 * x) / (W - 1));
        }
    }

    for (int i = 0; i < 30; ++i) {
        est.process(frame.data(), W, H, 0.05f, 0.5f);
    }

    DepthEstimator::Reading last;
    // 0.05 → 0.95 over 360 frames, 0.0025 / frame. Deep below the 2 %
    // position-reset threshold.
    for (int i = 1; i <= 360; ++i) {
        last = est.process(frame.data(), W, H, 0.05f + i * 0.0025f, 0.5f);
    }
    // Expected depth at x=0.95 of the gradient: 800 + 1000*0.95 = 1750 mm.
    EXPECT_NEAR(last.valueM, 1.75f, 0.05f)
        << "After a 360-frame smooth drag across the whole gradient, the "
        << "reading must track the new position. Got " << last.valueM;
}

TEST(DepthEstimator, SparseFarROIBecomesValidAtLowerValidPixelThreshold) {
    // Documents the fix: with kMinValidPixels lowered to 4, a
    // 6-valid-pixel 4.75 m ROI becomes a VALID reading — low
    // confidence (reflecting the sparse ROI) but numerically
    // correct. That way the capture thread actually emits depth
    // and the UI updates to 4.75 m instead of freezing at 1.0 m.
    //
    // This test will fail until kMinValidPixels is lowered. It
    // exists to track the fix: if someone raises the threshold
    // back to 8, this test flags the regression.
    DepthEstimator est;
    std::vector<uint16_t> sparseFar(640 * 480, 0);
    const int cx = 320, cy = 240;
    int filled = 0;
    for (int dy = -3; dy <= 3 && filled < 6; ++dy) {
        for (int dx = -3; dx <= 3 && filled < 6; ++dx) {
            sparseFar[(cy + dy) * 640 + (cx + dx)] = 4750;
            ++filled;
        }
    }
    auto r = est.process(sparseFar.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(r.isValid)
        << "6 valid pixels at 4.75 m must produce a valid (low-confidence) "
        << "reading — lose this and the UI freezes at the previous depth "
        << "on any drag to a sparse far ROI.";
    EXPECT_NEAR(r.valueM, 4.75f, 0.02f);
    EXPECT_LT(r.confidence, 0.5f)
        << "Sparse ROI should report honestly low confidence.";
}

TEST(DepthEstimator, PositionChangeResetsState) {
    // This is the PRIMARY bug the new estimator fixes: when the target
    // moves to a new spatial location, the temporal state must be
    // discarded. Otherwise the filter holds the old position's depth and
    // fights every reading at the new position.
    DepthEstimator est;

    // Converge on a 500 mm surface at (0.2, 0.5).
    auto near = makeUniformFrame(640, 480, 500);
    for (int i = 0; i < 30; ++i) {
        est.process(near.data(), 640, 480, 0.2f, 0.5f);
    }
    auto beforeMove = est.process(near.data(), 640, 480, 0.2f, 0.5f);
    EXPECT_NEAR(beforeMove.valueM, 0.5f, 0.02f);

    // Move target to (0.8, 0.5) where depth is 4000 mm. The very first
    // reading after the move must snap to the new value, not carry any
    // weight from the old 500 mm state.
    auto far = makeUniformFrame(640, 480, 4000);
    auto afterMove = est.process(far.data(), 640, 480, 0.8f, 0.5f);
    EXPECT_TRUE(afterMove.isValid);
    EXPECT_NEAR(afterMove.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, JumpsToFarTargetWithPartialHoles) {
    // This reproduces the real-world bug: at 4 m+ the RealSense
    // commonly returns valid depth for only ~40 % of a small ROI
    // (plain walls have low texture → low matching confidence →
    // sparse valid pixels). A 400 % scene jump must still snap,
    // not slowly creep toward the new value.
    DepthEstimator est;

    auto close = makeUniformFrame(640, 480, 500);
    for (int i = 0; i < 20; ++i) {
        est.process(close.data(), 640, 480, 0.5f, 0.5f);
    }

    // Build a sparse far frame: only 20 / 49 ROI pixels valid (40 %).
    std::vector<uint16_t> farSparse(640 * 480, 0);
    const int cx = 320, cy = 240;
    int filled = 0;
    for (int dy = -3; dy <= 3 && filled < 20; ++dy) {
        for (int dx = -3; dx <= 3 && filled < 20; ++dx) {
            farSparse[(cy + dy) * 640 + (cx + dx)] = 4000;
            ++filled;
        }
    }

    auto r = est.process(farSparse.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(r.isValid);
    EXPECT_NEAR(r.valueM, 4.0f, 0.1f)
        << "Must snap to 4 m even with 40% validRatio. "
        << "Got " << r.valueM << " m, confidence " << r.confidence;
}

TEST(DepthEstimator, DiscontinuitySnapsAtFixedPosition) {
    // Crosshair stays at same pixel but the scene changes (object
    // removed, reveals far wall). Must snap without smoothing delay.
    DepthEstimator est;

    auto close = makeUniformFrame(640, 480, 800);
    for (int i = 0; i < 20; ++i) {
        est.process(close.data(), 640, 480, 0.5f, 0.5f);
    }

    auto far = makeUniformFrame(640, 480, 4500);
    auto snap = est.process(far.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_TRUE(snap.isValid);
    EXPECT_NEAR(snap.valueM, 4.5f, 0.05f);
}

TEST(DepthEstimator, SmoothsNoisyMeasurements) {
    // Normal tracking within the noise band must be smoothed.
    DepthEstimator est;
    est.setSmoothing(0.30f);

    const uint16_t noisy[] = {1500, 1520, 1480, 1510, 1490, 1505, 1495, 1510};
    for (uint16_t d : noisy) {
        auto frame = makeUniformFrame(640, 480, d);
        est.process(frame.data(), 640, 480, 0.5f, 0.5f);
    }
    auto r = est.process(makeUniformFrame(640, 480, 1500).data(),
                         640, 480, 0.5f, 0.5f);
    EXPECT_NEAR(r.valueM, 1.5f, 0.02f);
}

TEST(DepthEstimator, StaleFramesIncrementWhenSensorGoesDark) {
    // If the sensor stops producing valid data at this point, staleFrames
    // grows each call. The UI can use this to show "waiting..." state.
    DepthEstimator est;
    auto good = makeUniformFrame(640, 480, 2000);
    est.process(good.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_EQ(est.process(good.data(), 640, 480, 0.5f, 0.5f).staleFrames, 0);

    auto dark = makeUniformFrame(640, 480, 0);
    auto r1 = est.process(dark.data(), 640, 480, 0.5f, 0.5f);
    auto r2 = est.process(dark.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_FALSE(r1.isValid);
    EXPECT_FALSE(r2.isValid);
    EXPECT_GE(r2.staleFrames, 2);
}

TEST(DepthEstimator, Reset) {
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 1500);
    est.process(frame.data(), 640, 480, 0.5f, 0.5f);

    est.reset();
    auto frame2 = makeUniformFrame(640, 480, 4000);
    auto r = est.process(frame2.data(), 640, 480, 0.5f, 0.5f);
    // After reset, the very first reading IS the state — no blending
    // against the old 1500 mm.
    EXPECT_NEAR(r.valueM, 4.0f, 0.01f);
}

TEST(DepthEstimator, ConfidenceSaturatesDuringStableTracking) {
    // Regression guard: downstream consumers (AutofocusController) gate on
    // confidence >= 0.7 before driving the motor. After the estimator has
    // seen several stable readings its reported confidence must approach
    // 1.0, even if each individual ROI sample is only moderately clean.
    // This matches the old 1-D Kalman behaviour and is what the 0.7
    // autofocus threshold was calibrated against.
    DepthEstimator est;
    auto frame = makeUniformFrame(640, 480, 2000);
    float conf = 0.0f;
    for (int i = 0; i < 15; ++i) {
        conf = est.process(frame.data(), 640, 480, 0.5f, 0.5f).confidence;
    }
    EXPECT_GT(conf, 0.95f)
        << "Stable tracking must produce high confidence. Got " << conf;
}

TEST(DepthEstimator, ConfidenceDropsAfterDiscontinuitySnap) {
    // After a scene change, the estimator hasn't had time to verify its
    // new state with successive agreeing samples. Temporal confidence
    // resets, so the first post-snap reading reports only the spatial
    // confidence — rising again as the track proves itself.
    DepthEstimator est;
    auto close = makeUniformFrame(640, 480, 500);
    for (int i = 0; i < 20; ++i) est.process(close.data(), 640, 480, 0.5f, 0.5f);
    auto stable = est.process(close.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_GT(stable.confidence, 0.95f);

    auto far = makeUniformFrame(640, 480, 4500);
    auto snap = est.process(far.data(), 640, 480, 0.5f, 0.5f);
    EXPECT_LT(snap.confidence, 1.01f); // spatial only, no temporal boost yet
    EXPECT_NEAR(snap.valueM, 4.5f, 0.05f);
}

TEST(DepthEstimator, LowConfidenceForPatchyROI) {
    // A split-depth ROI (object edge) should produce a lower confidence
    // than a clean uniform ROI, even if both yield a valid reading.
    DepthEstimator estA, estB;

    auto clean = makeUniformFrame(640, 480, 2000);
    auto confA = estA.process(clean.data(), 640, 480, 0.5f, 0.5f).confidence;

    // Left half of the ROI is at 2000 mm, right half at 4500 mm. This
    // creates a real depth discontinuity inside the ROI, producing a
    // wide IQR and thus low tightness. A clean uniform ROI should score
    // meaningfully higher.
    auto split = makeTwoDepthFrame(640, 480, 2000, 4500,
                                   /*px=*/320, /*py=*/240 - 3,
                                   /*pw=*/4, /*ph=*/7);
    auto confB = estB.process(split.data(), 640, 480, 0.5f, 0.5f).confidence;
    EXPECT_GT(confA, confB);
}

// ── 2D Kalman Filter (face-tracking — unchanged) ──────────────────────

TEST(KalmanFilter2D, InitialState) {
    KalmanFilter2D kf;
    EXPECT_FALSE(kf.isInitialized());
}

TEST(KalmanFilter2D, Initialize) {
    KalmanFilter2D kf;
    kf.initialize(0.5f, 0.5f);
    EXPECT_TRUE(kf.isInitialized());

    float x, y;
    kf.getPosition(x, y);
    EXPECT_FLOAT_EQ(x, 0.5f);
    EXPECT_FLOAT_EQ(y, 0.5f);
}

TEST(KalmanFilter2D, PredictConstantVelocity) {
    KalmanFilter2D kf;
    kf.initialize(0.0f, 0.0f);

    for (int i = 0; i < 20; ++i) {
        float mx = 0.1f * i;
        kf.predict(1.0f / 30.0f);
        kf.update(mx, 0.0f);
    }

    float x, y;
    kf.getPosition(x, y);
    EXPECT_NEAR(x, 1.9f, 0.3f);
    EXPECT_NEAR(y, 0.0f, 0.1f);

    float vx, vy;
    kf.getVelocity(vx, vy);
    EXPECT_GT(vx, 0.0f);
}

TEST(KalmanFilter2D, AutoInitOnUpdate) {
    KalmanFilter2D kf;
    kf.update(0.3f, 0.7f);
    EXPECT_TRUE(kf.isInitialized());

    float x, y;
    kf.getPosition(x, y);
    EXPECT_FLOAT_EQ(x, 0.3f);
    EXPECT_FLOAT_EQ(y, 0.7f);
}
