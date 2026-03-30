#include "core/autofocus/SubjectTracker.h"
#include <algorithm>
#include <cmath>

namespace alice {

const QColor SubjectTracker::kColorPalette[8] = {
    QColor(0, 200, 255),   // Cyan
    QColor(255, 100, 100),  // Red
    QColor(100, 255, 100),  // Green
    QColor(255, 200, 50),   // Yellow
    QColor(200, 100, 255),  // Purple
    QColor(255, 150, 50),   // Orange
    QColor(100, 200, 200),  // Teal
    QColor(255, 100, 200),  // Pink
};

QPointF TrackedFace::focusPoint() const {
    if (leftEye && rightEye) {
        return QPointF((leftEye->x() + rightEye->x()) / 2.0,
                       (leftEye->y() + rightEye->y()) / 2.0);
    }
    return center;
}

SubjectTracker::SubjectTracker(QObject *parent) : QObject(parent) {}

std::vector<TrackedFace> SubjectTracker::update(
    const std::vector<RawFaceDetection> &detections,
    int imageWidth, int imageHeight)
{
    float imgW = static_cast<float>(imageWidth);
    float imgH = static_cast<float>(imageHeight);

    // 1. Predict all existing tracks
    for (auto &track : tracks_) {
        track.kalman.predict(1.0f / 30.0f);
    }

    // 2. Match detections to existing tracks (greedy, by IoU then distance)
    std::vector<bool> detMatched(detections.size(), false);
    std::vector<bool> trackMatched(tracks_.size(), false);

    // IoU-based matching
    for (size_t ti = 0; ti < tracks_.size(); ++ti) {
        float bestIoU = kIouMatchThreshold;
        int bestDet = -1;
        for (size_t di = 0; di < detections.size(); ++di) {
            if (detMatched[di]) continue;
            float iou = computeIoU(tracks_[ti].lastBox, detections[di].boundingBox);
            if (iou > bestIoU) {
                bestIoU = iou;
                bestDet = static_cast<int>(di);
            }
        }
        if (bestDet >= 0) {
            trackMatched[ti] = true;
            detMatched[bestDet] = true;
            auto &det = detections[bestDet];
            auto &track = tracks_[ti];

            float cx = static_cast<float>(det.boundingBox.center().x()) / imgW;
            float cy = static_cast<float>(det.boundingBox.center().y()) / imgH;
            track.kalman.update(cx, cy);
            track.lastBox = det.boundingBox;
            track.framesSinceDetection = 0;
            track.stabilityFrames++;
            track.totalFrames++;
            track.lastConfidence = det.confidence;

            // Update eye positions from landmarks
            if (det.landmarks.size() >= 2) {
                track.leftEye = QPointF(det.landmarks[0].x() / imgW, det.landmarks[0].y() / imgH);
                track.rightEye = QPointF(det.landmarks[1].x() / imgW, det.landmarks[1].y() / imgH);
            } else {
                track.leftEye.reset();
                track.rightEye.reset();
            }
        }
    }

    // Distance-based fallback for unmatched
    for (size_t ti = 0; ti < tracks_.size(); ++ti) {
        if (trackMatched[ti]) continue;
        float bestDist = kMaxDistanceThreshold;
        int bestDet = -1;
        float tkX, tkY;
        tracks_[ti].kalman.getPosition(tkX, tkY);

        for (size_t di = 0; di < detections.size(); ++di) {
            if (detMatched[di]) continue;
            float dx = static_cast<float>(detections[di].boundingBox.center().x()) / imgW - tkX;
            float dy = static_cast<float>(detections[di].boundingBox.center().y()) / imgH - tkY;
            float dist = std::sqrt(dx * dx + dy * dy);
            if (dist < bestDist) {
                bestDist = dist;
                bestDet = static_cast<int>(di);
            }
        }
        if (bestDet >= 0) {
            trackMatched[ti] = true;
            detMatched[bestDet] = true;
            auto &det = detections[bestDet];
            auto &track = tracks_[ti];
            float cx = static_cast<float>(det.boundingBox.center().x()) / imgW;
            float cy = static_cast<float>(det.boundingBox.center().y()) / imgH;
            track.kalman.update(cx, cy);
            track.lastBox = det.boundingBox;
            track.framesSinceDetection = 0;
            track.stabilityFrames++;
            track.totalFrames++;
            track.lastConfidence = det.confidence;
            if (det.landmarks.size() >= 2) {
                track.leftEye = QPointF(det.landmarks[0].x() / imgW, det.landmarks[0].y() / imgH);
                track.rightEye = QPointF(det.landmarks[1].x() / imgW, det.landmarks[1].y() / imgH);
            }
        }
    }

    // 3. Create new tracks for unmatched detections
    for (size_t di = 0; di < detections.size(); ++di) {
        if (detMatched[di]) continue;
        Track track;
        track.id = nextId_++;
        float cx = static_cast<float>(detections[di].boundingBox.center().x()) / imgW;
        float cy = static_cast<float>(detections[di].boundingBox.center().y()) / imgH;
        track.kalman.initialize(cx, cy);
        track.lastBox = detections[di].boundingBox;
        track.color = kColorPalette[nextColorIdx_ % 8];
        nextColorIdx_++;
        track.lastConfidence = detections[di].confidence;
        if (detections[di].landmarks.size() >= 2) {
            track.leftEye = QPointF(detections[di].landmarks[0].x() / imgW, detections[di].landmarks[0].y() / imgH);
            track.rightEye = QPointF(detections[di].landmarks[1].x() / imgW, detections[di].landmarks[1].y() / imgH);
        }
        tracks_.push_back(std::move(track));
    }

    // 4. Update unmatched tracks (prediction only) and remove stale ones
    for (size_t ti = 0; ti < tracks_.size(); ++ti) {
        if (!trackMatched[ti]) {
            tracks_[ti].framesSinceDetection++;
        }
    }
    tracks_.erase(
        std::remove_if(tracks_.begin(), tracks_.end(),
                        [](const Track &t) { return t.framesSinceDetection > kPredictionHoldFrames; }),
        tracks_.end());

    // 5. Build output
    std::vector<TrackedFace> result;
    for (const auto &track : tracks_) {
        if (track.stabilityFrames < kStabilityFramesRequired) continue;

        TrackedFace face;
        face.trackingId = track.id;
        face.boundingBox = track.lastBox;
        float px, py;
        track.kalman.getPosition(px, py);
        face.center = QPointF(px, py);
        face.confidence = track.lastConfidence;
        face.color = track.color;
        face.leftEye = track.leftEye;
        face.rightEye = track.rightEye;

        if (track.framesSinceDetection == 0) {
            face.state = track.leftEye.has_value() ? TrackingState::EyeLocked : TrackingState::FaceOnly;
        } else {
            face.state = TrackingState::Predicted;
        }

        face.score = computeScore(track, imageWidth, imageHeight);
        result.push_back(face);
    }

    std::sort(result.begin(), result.end(),
              [](const auto &a, const auto &b) { return a.score > b.score; });

    if (result.size() > kMaxTrackedFaces) {
        result.resize(kMaxTrackedFaces);
    }

    return result;
}

void SubjectTracker::selectFace(int trackingId) {
    selectedId_ = trackingId;
}

void SubjectTracker::reset() {
    tracks_.clear();
    nextId_ = 1;
    selectedId_ = -1;
}

float SubjectTracker::computeScore(const Track &track, int imageWidth, int imageHeight) const {
    float imgW = static_cast<float>(imageWidth);
    float imgH = static_cast<float>(imageHeight);
    float imgArea = imgW * imgH;

    // Size score
    float faceArea = static_cast<float>(track.lastBox.width() * track.lastBox.height());
    float sizeScore = std::clamp(faceArea / imgArea * 4.0f, 0.0f, 1.0f);

    // Center score
    float cx, cy;
    track.kalman.getPosition(cx, cy);
    float distFromCenter = std::sqrt((cx - 0.5f) * (cx - 0.5f) + (cy - 0.5f) * (cy - 0.5f));
    float centerScore = 1.0f - std::clamp(distFromCenter * 2.0f, 0.0f, 1.0f);

    // Eye score
    float eyeScore = (track.leftEye && track.rightEye) ? 1.0f
                   : (track.leftEye || track.rightEye) ? 0.7f
                   : 0.3f;

    // Stability score
    float stabilityScore = std::clamp(static_cast<float>(track.totalFrames) / 100.0f, 0.0f, 1.0f);

    return sizeScore * kScoreWeightSize
         + centerScore * kScoreWeightCenter
         + eyeScore * kScoreWeightEyes
         + stabilityScore * kScoreWeightStability;
}

float SubjectTracker::computeIoU(const QRectF &a, const QRectF &b) {
    QRectF inter = a.intersected(b);
    if (inter.isEmpty()) return 0.0f;
    float interArea = static_cast<float>(inter.width() * inter.height());
    float unionArea = static_cast<float>(a.width() * a.height() + b.width() * b.height()) - interArea;
    return (unionArea > 0.0f) ? interArea / unionArea : 0.0f;
}

} // namespace alice
