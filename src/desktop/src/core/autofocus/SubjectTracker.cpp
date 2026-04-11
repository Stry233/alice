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

// ── Helpers ──────────────────────────────────────────────────────────

float SubjectTracker::computeIoU(const QRectF &a, const QRectF &b) {
    QRectF inter = a.intersected(b);
    if (inter.isEmpty()) return 0.0f;
    const float interArea = static_cast<float>(inter.width() * inter.height());
    const float unionArea =
        static_cast<float>(a.width() * a.height() + b.width() * b.height()) - interArea;
    return (unionArea > 0.0f) ? interArea / unionArea : 0.0f;
}

QRectF SubjectTracker::expandBox(const QRectF &box, float factor) {
    const float dw = box.width()  * factor;
    const float dh = box.height() * factor;
    return box.adjusted(-dw, -dh, dw, dh);
}

SubjectTracker::Histogram SubjectTracker::computeHistogram(
    const QImage &image, const QRectF &bbox)
{
    Histogram hist{};
    if (image.isNull() || bbox.isEmpty()) return hist;

    // Clamp bbox to image bounds
    const int x0 = std::max(0, static_cast<int>(bbox.x()));
    const int y0 = std::max(0, static_cast<int>(bbox.y()));
    const int x1 = std::min(image.width(),  static_cast<int>(bbox.x() + bbox.width()));
    const int y1 = std::min(image.height(), static_cast<int>(bbox.y() + bbox.height()));
    if (x1 <= x0 || y1 <= y0) return hist;

    // Work on RGB888 — convert cheaply if needed (view into the image data).
    const QImage rgb = (image.format() == QImage::Format_RGB888)
        ? image
        : image.convertToFormat(QImage::Format_RGB888);

    // Stride-based sampling to cap cost regardless of bbox size.
    const int boxW = x1 - x0;
    const int boxH = y1 - y0;
    const int targetSamples = 1024;
    const int step = std::max(1, static_cast<int>(
        std::sqrt(static_cast<float>(boxW * boxH) / targetSamples)));

    int totalPixels = 0;
    for (int y = y0; y < y1; y += step) {
        const uchar *line = rgb.constScanLine(y);
        for (int x = x0; x < x1; x += step) {
            const int i = x * 3;
            const int r = line[i + 0];
            const int g = line[i + 1];
            const int b = line[i + 2];
            // Rec.601 luma, fixed-point to avoid a float multiply per pixel
            const int gray = (77 * r + 150 * g + 29 * b) >> 8; // ~0.299 R + 0.587 G + 0.114 B
            const int bin = std::clamp(gray * kHistogramBins / 256, 0, kHistogramBins - 1);
            hist[bin] += 1.0f;
            ++totalPixels;
        }
    }

    if (totalPixels > 0) {
        const float inv = 1.0f / static_cast<float>(totalPixels);
        for (auto &h : hist) h *= inv;
    }
    return hist;
}

float SubjectTracker::histogramCorrelation(const Histogram &a, const Histogram &b) {
    // Bhattacharyya coefficient — well-behaved on normalized histograms,
    // 1.0 = identical distribution, 0.0 = no overlap.
    float sum = 0.0f;
    for (int i = 0; i < kHistogramBins; ++i) {
        sum += std::sqrt(a[i] * b[i]);
    }
    return sum;
}

void SubjectTracker::assignEyes(Track &track, const RawFaceDetection &det,
                                int imageWidth, int imageHeight)
{
    const float imgW = static_cast<float>(imageWidth);
    const float imgH = static_cast<float>(imageHeight);

    if (det.landmarks.size() >= 2) {
        // Real landmarks from the detector: indices 0 = left eye, 1 = right eye
        // (YOLO-face / SCRFD convention)
        track.leftEye  = QPointF(det.landmarks[0].x() / imgW, det.landmarks[0].y() / imgH);
        track.rightEye = QPointF(det.landmarks[1].x() / imgW, det.landmarks[1].y() / imgH);
        track.hasRealLandmarks = true;
        return;
    }

    // Heuristic fallback — placed at roughly-anthropometric positions inside
    // the face bbox. Gives the autofocus loop a much better depth-sample
    // point than the bbox center (which often lands on the nose/mouth).
    //
    //  Eye line : ~38 % from the top of the bbox
    //  Eyes X   : 30 % and 70 % from the left
    const QRectF &box = det.boundingBox;
    const float eyeY  = static_cast<float>(box.y() + box.height() * 0.38);
    const float leftX = static_cast<float>(box.x() + box.width()  * 0.30);
    const float rightX= static_cast<float>(box.x() + box.width()  * 0.70);
    track.leftEye  = QPointF(leftX  / imgW, eyeY / imgH);
    track.rightEye = QPointF(rightX / imgW, eyeY / imgH);
    track.hasRealLandmarks = false;
}

// ── Main update ──────────────────────────────────────────────────────

std::vector<TrackedFace> SubjectTracker::update(
    const std::vector<RawFaceDetection> &detections,
    int imageWidth, int imageHeight,
    const QImage &sourceImage)
{
    const float imgW = static_cast<float>(imageWidth);
    const float imgH = static_cast<float>(imageHeight);

    // 1. Predict every existing track forward one frame (both visible and ghost).
    for (auto &track : tracks_) {
        track.kalman.predict(1.0f / 30.0f);
    }

    // Precompute per-detection histograms once — we may need them either for
    // ghost re-id or to initialise new tracks' appearance fingerprints.
    std::vector<Histogram> detHists;
    detHists.reserve(detections.size());
    const bool haveImage = !sourceImage.isNull();
    if (haveImage) {
        for (const auto &det : detections) {
            detHists.push_back(computeHistogram(sourceImage, det.boundingBox));
        }
    }

    std::vector<bool> detMatched(detections.size(), false);
    std::vector<bool> trackMatched(tracks_.size(), false);

    auto applyDetection = [&](Track &track, size_t detIdx, int fallbackW, int fallbackH) {
        (void)fallbackW; (void)fallbackH;
        const auto &det = detections[detIdx];
        const float cx = static_cast<float>(det.boundingBox.center().x()) / imgW;
        const float cy = static_cast<float>(det.boundingBox.center().y()) / imgH;
        track.kalman.update(cx, cy);
        track.lastBox = det.boundingBox;
        track.framesSinceDetection = 0;
        track.stabilityFrames++;
        track.totalFrames++;
        track.lastConfidence = det.confidence;
        track.inGhostPhase = false;
        track.ghostFramesRemaining = 0;
        if (haveImage) {
            track.histogram = detHists[detIdx];
            track.hasHistogram = true;
        }
        assignEyes(track, det, imageWidth, imageHeight);
    };

    // 2. IoU matching — only against VISIBLE tracks (ghosts handled later).
    for (size_t ti = 0; ti < tracks_.size(); ++ti) {
        if (tracks_[ti].inGhostPhase) continue;
        float bestIoU = kIouMatchThreshold;
        int bestDet = -1;
        for (size_t di = 0; di < detections.size(); ++di) {
            if (detMatched[di]) continue;
            const float iou = computeIoU(tracks_[ti].lastBox, detections[di].boundingBox);
            if (iou > bestIoU) {
                bestIoU = iou;
                bestDet = static_cast<int>(di);
            }
        }
        if (bestDet >= 0) {
            trackMatched[ti] = true;
            detMatched[bestDet] = true;
            applyDetection(tracks_[ti], static_cast<size_t>(bestDet), imageWidth, imageHeight);
        }
    }

    // 3. Distance-based fallback for still-unmatched visible tracks.
    for (size_t ti = 0; ti < tracks_.size(); ++ti) {
        if (trackMatched[ti] || tracks_[ti].inGhostPhase) continue;
        float bestDist = kMaxDistanceThreshold;
        int bestDet = -1;
        float tkX, tkY;
        tracks_[ti].kalman.getPosition(tkX, tkY);
        for (size_t di = 0; di < detections.size(); ++di) {
            if (detMatched[di]) continue;
            const float dx = static_cast<float>(detections[di].boundingBox.center().x()) / imgW - tkX;
            const float dy = static_cast<float>(detections[di].boundingBox.center().y()) / imgH - tkY;
            const float dist = std::sqrt(dx * dx + dy * dy);
            if (dist < bestDist) {
                bestDist = dist;
                bestDet = static_cast<int>(di);
            }
        }
        if (bestDet >= 0) {
            trackMatched[ti] = true;
            detMatched[bestDet] = true;
            applyDetection(tracks_[ti], static_cast<size_t>(bestDet), imageWidth, imageHeight);
        }
    }

    // 4. Ghost re-acquire — lost tracks that are still within their ghost
    // window get a shot at matching unmatched detections via expanded IoU or
    // appearance correlation. This is the "quick restore" path: the operator
    // can turn away for up to ~1.5 s and the subject id is preserved when
    // they turn back.
    for (size_t di = 0; di < detections.size(); ++di) {
        if (detMatched[di]) continue;

        float bestScore = 0.0f;
        int bestTrack = -1;
        bool bestViaHist = false;

        for (size_t ti = 0; ti < tracks_.size(); ++ti) {
            auto &track = tracks_[ti];
            if (trackMatched[ti]) continue;
            if (!track.inGhostPhase) continue;

            // Expanded IoU — the track's last-known box widened to 2×.
            const QRectF expanded = expandBox(track.lastBox, 0.5f);
            const float iou = computeIoU(expanded, detections[di].boundingBox);

            float histSim = 0.0f;
            if (track.hasHistogram && haveImage) {
                histSim = histogramCorrelation(track.histogram, detHists[di]);
            }

            // Accept either: strong spatial overlap OR strong appearance match.
            // Histogram-only re-id requires a tighter threshold to avoid
            // snapping onto an unrelated face with similar brightness.
            float score = 0.0f;
            bool viaHist = false;
            if (iou >= kGhostIouThreshold) {
                score = std::max(iou, histSim * 0.9f);
            } else if (histSim >= kReIdMatchThreshold) {
                score = histSim;
                viaHist = true;
            }

            if (score > bestScore) {
                bestScore = score;
                bestTrack = static_cast<int>(ti);
                bestViaHist = viaHist;
            }
        }

        if (bestTrack >= 0) {
            (void)bestViaHist;
            trackMatched[bestTrack] = true;
            detMatched[di] = true;
            applyDetection(tracks_[bestTrack], di, imageWidth, imageHeight);
        }
    }

    // 5. Advance unmatched tracks — bump framesSinceDetection and transition
    // into ghost phase when the visible prediction window expires.
    const size_t originalTrackCount = trackMatched.size();
    for (size_t ti = 0; ti < originalTrackCount; ++ti) {
        if (trackMatched[ti]) continue;
        auto &track = tracks_[ti];
        track.framesSinceDetection++;
        if (!track.inGhostPhase && track.framesSinceDetection > kPredictionHoldFrames) {
            track.inGhostPhase = true;
            track.ghostFramesRemaining = kGhostHoldFrames;
        } else if (track.inGhostPhase) {
            track.ghostFramesRemaining--;
        }
    }

    // 6. Create new tracks for detections that still have no home.
    for (size_t di = 0; di < detections.size(); ++di) {
        if (detMatched[di]) continue;
        Track track;
        track.id = nextId_++;
        const auto &det = detections[di];
        const float cx = static_cast<float>(det.boundingBox.center().x()) / imgW;
        const float cy = static_cast<float>(det.boundingBox.center().y()) / imgH;
        track.kalman.initialize(cx, cy);
        track.lastBox = det.boundingBox;
        track.color = kColorPalette[nextColorIdx_ % 8];
        nextColorIdx_++;
        track.lastConfidence = det.confidence;
        track.stabilityFrames = 1;
        track.totalFrames = 1;
        if (haveImage) {
            track.histogram = detHists[di];
            track.hasHistogram = true;
        }
        assignEyes(track, det, imageWidth, imageHeight);
        tracks_.push_back(std::move(track));
    }

    // 7. Remove expired ghosts.
    tracks_.erase(
        std::remove_if(tracks_.begin(), tracks_.end(),
            [](const Track &t) {
                return t.inGhostPhase && t.ghostFramesRemaining <= 0;
            }),
        tracks_.end());

    // 8. Build output (ghost tracks excluded — they're invisible by design).
    std::vector<TrackedFace> result;
    result.reserve(tracks_.size());
    for (const auto &track : tracks_) {
        if (track.inGhostPhase) continue;
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
        face.hasRealLandmarks = track.hasRealLandmarks;

        if (track.framesSinceDetection == 0) {
            face.state = track.hasRealLandmarks
                ? TrackingState::EyeLocked
                : TrackingState::FaceOnly;
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
    const float imgW = static_cast<float>(imageWidth);
    const float imgH = static_cast<float>(imageHeight);
    const float imgArea = imgW * imgH;

    // Size score — the cinema subject is typically the largest face.
    const float faceArea = static_cast<float>(track.lastBox.width() * track.lastBox.height());
    const float sizeScore = std::clamp(faceArea / imgArea * 4.0f, 0.0f, 1.0f);

    // Center score (mild bias toward on-axis faces).
    float cx, cy;
    track.kalman.getPosition(cx, cy);
    const float distFromCenter = std::sqrt((cx - 0.5f) * (cx - 0.5f) +
                                            (cy - 0.5f) * (cy - 0.5f));
    const float centerScore = 1.0f - std::clamp(distFromCenter * 2.0f, 0.0f, 1.0f);

    // Eye score — real landmarks > heuristic > none.
    float eyeScore = 0.3f;
    if (track.hasRealLandmarks) {
        eyeScore = (track.leftEye && track.rightEye) ? 1.0f
                 : (track.leftEye || track.rightEye) ? 0.7f : 0.3f;
    } else if (track.leftEye && track.rightEye) {
        // Heuristic eyes — still counts for something, but less than real.
        eyeScore = 0.55f;
    }

    // Stability score — established tracks outrank fresh detections.
    const float stabilityScore =
        std::clamp(static_cast<float>(track.totalFrames) / 100.0f, 0.0f, 1.0f);

    return sizeScore      * kScoreWeightSize
         + centerScore    * kScoreWeightCenter
         + eyeScore       * kScoreWeightEyes
         + stabilityScore * kScoreWeightStability;
}

} // namespace alice
