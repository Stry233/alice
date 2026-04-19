#pragma once

#include <QObject>
#include <QRectF>
#include <QPointF>
#include <QColor>
#include <vector>
#include <optional>

#include "core/autofocus/KalmanFilter2D.h"
#include "core/autofocus/FaceDetector.h"

namespace alice {

enum class TrackingState {
    EyeLocked,   // Eyes visible and detected (best quality)
    FaceOnly,    // Face detected, eyes not visible
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

    // Eye positions (normalized), if available
    std::optional<QPointF> leftEye;
    std::optional<QPointF> rightEye;

    // Focus target: eye midpoint if available, otherwise face center
    QPointF focusPoint() const;
};

/**
 * Multi-face tracker with per-subject Kalman filtering.
 * Ported from SubjectTracker.kt.
 */
class SubjectTracker : public QObject {
    Q_OBJECT

public:
    // Matching thresholds
    static constexpr float kIouMatchThreshold = 0.3f;
    static constexpr float kMaxDistanceThreshold = 0.25f;
    static constexpr int kPredictionHoldFrames = 5;
    static constexpr int kStabilityFramesRequired = 2;

    // Scoring weights
    static constexpr float kScoreWeightSize = 0.3f;
    static constexpr float kScoreWeightCenter = 0.25f;
    static constexpr float kScoreWeightEyes = 0.25f;
    static constexpr float kScoreWeightStability = 0.2f;

    static constexpr int kMaxTrackedFaces = 4;

    explicit SubjectTracker(QObject *parent = nullptr);

    /**
     * Update tracker with new detections from FaceDetector.
     * @param detections Raw detections from current frame
     * @param imageWidth  Frame width in pixels
     * @param imageHeight Frame height in pixels
     * @return Tracked faces sorted by priority score
     */
    std::vector<TrackedFace> update(
        const std::vector<RawFaceDetection> &detections,
        int imageWidth, int imageHeight);

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
    };

    float computeScore(const Track &track, int imageWidth, int imageHeight) const;
    static float computeIoU(const QRectF &a, const QRectF &b);

    std::vector<Track> tracks_;
    int nextId_ = 1;
    int selectedId_ = -1;

    static const QColor kColorPalette[8];
    int nextColorIdx_ = 0;
};

} // namespace alice
