#pragma once

#include <QObject>
#include <QImage>
#include <QRectF>
#include <QPointF>
#include <QColor>
#include <array>
#include <vector>
#include <optional>

#include "core/autofocus/KalmanFilter2D.h"
#include "core/autofocus/FaceDetector.h"

namespace alice {

enum class TrackingState {
    EyeLocked,   // Eyes visible (real landmarks from detector)
    FaceOnly,    // Face detected, eyes heuristic or missing
    Predicted,   // Using Kalman prediction (brief occlusion)
    Lost         // Prediction expired, track to be removed
};

struct TrackedFace {
    int trackingId = 0;
    QRectF boundingBox;             // Pixel coordinates
    QPointF center;                 // Normalized (0–1)
    float confidence = 0.0f;
    QColor color;
    TrackingState state = TrackingState::FaceOnly;
    float score = 0.0f;            // Priority score

    // Eye positions (normalized). Set either by the detector (real landmarks)
    // or synthesized heuristically from the bbox (see hasRealLandmarks).
    std::optional<QPointF> leftEye;
    std::optional<QPointF> rightEye;
    bool hasRealLandmarks = false;

    // Focus target: eye midpoint if available, otherwise face center
    QPointF focusPoint() const;
};

/**
 * Multi-face tracker with per-subject Kalman filtering.
 * Matches incoming detections to existing tracks via IoU and distance thresholds,
 * predicts positions through brief occlusions with Kalman prediction, re-acquires
 * ghost tracks using histogram correlation, and ranks subjects by a weighted score.
 */
class SubjectTracker : public QObject {
    Q_OBJECT

public:
    // Matching thresholds
    static constexpr float kIouMatchThreshold = 0.3f;
    static constexpr float kMaxDistanceThreshold = 0.25f;

    // Lifecycle — visible prediction first, then an invisible "ghost" phase
    // where the track is still alive for re-acquisition but not returned to
    // the UI. Keeping tracks alive longer lets the primary subject survive
    // a brief head-turn or occlusion.
    static constexpr int kPredictionHoldFrames = 15;   // ~500 ms visible Kalman prediction
    static constexpr int kGhostHoldFrames      = 30;   // ~1 s invisible re-acquire window
    static constexpr int kStabilityFramesRequired = 2;

    // Scoring weights — cinema subject is typically the largest face in frame.
    // Boost size dominance, shrink center bias.
    static constexpr float kScoreWeightSize      = 0.45f;
    static constexpr float kScoreWeightCenter    = 0.15f;
    static constexpr float kScoreWeightEyes      = 0.20f;
    static constexpr float kScoreWeightStability = 0.20f;

    // Appearance histogram used for re-id when IoU matching fails.
    static constexpr int kHistogramBins = 8;
    using Histogram = std::array<float, kHistogramBins>;

    // Re-id match threshold (Bhattacharyya coefficient, 0..1)
    static constexpr float kReIdMatchThreshold = 0.88f;
    // Expanded-IoU match threshold for ghost re-acquire
    static constexpr float kGhostIouThreshold  = 0.15f;

    static constexpr int kMaxTrackedFaces = 4;

    explicit SubjectTracker(QObject *parent = nullptr);

    /**
     * Update tracker with new detections from FaceDetector.
     * @param detections  Raw detections from current frame
     * @param imageWidth  Frame width in pixels
     * @param imageHeight Frame height in pixels
     * @param sourceImage Optional source color frame (used to compute per-face
     *                    appearance histograms for re-id). If null, re-id
     *                    degrades to pure IoU matching.
     * @return Tracked faces sorted by priority score (ghost tracks excluded)
     */
    std::vector<TrackedFace> update(
        const std::vector<RawFaceDetection> &detections,
        int imageWidth, int imageHeight,
        const QImage &sourceImage = QImage());

    /** Select a face by tracking ID for priority focus. */
    void selectFace(int trackingId);

    /** Get the currently selected face ID (or -1). */
    int selectedFaceId() const { return selectedId_; }

    /** Reset all tracks. */
    void reset();

private:
    struct Track {
        int id = 0;
        KalmanFilter2D kalman;
        QRectF lastBox;
        QColor color;
        int framesSinceDetection = 0;
        int stabilityFrames = 0;
        int totalFrames = 0;
        float lastConfidence = 0.0f;
        std::optional<QPointF> leftEye;
        std::optional<QPointF> rightEye;
        bool hasRealLandmarks = false;

        // Re-id
        Histogram histogram{};
        bool hasHistogram = false;

        // Lifecycle
        bool inGhostPhase = false;            // invisible to the output, still matchable
        int  ghostFramesRemaining = 0;
    };

    float computeScore(const Track &track, int imageWidth, int imageHeight) const;
    static float computeIoU(const QRectF &a, const QRectF &b);
    static QRectF expandBox(const QRectF &box, float factor);

    // Appearance helpers
    static Histogram computeHistogram(const QImage &image, const QRectF &bbox);
    static float histogramCorrelation(const Histogram &a, const Histogram &b);

    // Eye helpers
    void assignEyes(Track &track, const RawFaceDetection &det,
                    int imageWidth, int imageHeight);

    std::vector<Track> tracks_;
    int nextId_ = 1;
    int selectedId_ = -1;

    static const QColor kColorPalette[8];
    int nextColorIdx_ = 0;
};

} // namespace alice
