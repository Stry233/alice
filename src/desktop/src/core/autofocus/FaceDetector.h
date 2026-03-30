#pragma once

#include <QObject>
#include <QImage>
#include <QRectF>
#include <QPointF>
#include <vector>
#include <memory>

namespace alice {

struct RawFaceDetection {
    QRectF boundingBox;       // Pixel coordinates
    float confidence = 0.0f;
    std::vector<QPointF> landmarks; // 5 points if available (eyes, nose, mouth corners)
};

/**
 * ONNX-based YOLO face detector.
 * Ported from OnnxFaceDetector.kt.
 *
 * Supports YOLOv8n/v10n/v11n face models from HuggingFace.
 * Input: 640x640, output auto-detected (transposed/non-transposed, center/corner format).
 */
class FaceDetector : public QObject {
    Q_OBJECT

public:
    static constexpr int kInputSize = 640;
    static constexpr float kConfidenceThreshold = 0.45f;
    static constexpr float kIouThreshold = 0.45f;
    static constexpr int kMaxFaces = 4;

    explicit FaceDetector(QObject *parent = nullptr);
    ~FaceDetector() override;

    /** Load ONNX model from file path. Returns true on success. */
    bool loadModel(const QString &modelPath);

    /** Check if model is loaded and ready. */
    bool isReady() const { return modelLoaded_; }

    /** Run face detection on an image. Thread-safe. */
    std::vector<RawFaceDetection> detect(const QImage &image);

signals:
    void modelLoaded(bool success);
    void error(const QString &message);

private:
    std::vector<RawFaceDetection> postprocess(
        const float *outputData, int numBoxes, int numFeatures,
        bool transposed, float scaleX, float scaleY);

    static float computeIoU(const QRectF &a, const QRectF &b);
    std::vector<RawFaceDetection> nonMaxSuppression(
        std::vector<RawFaceDetection> &detections);

    bool modelLoaded_ = false;
    int outputBoxes_ = 0;
    int outputFeatures_ = 0;
    bool outputTransposed_ = false;
    bool usesCornerFormat_ = false;

    struct OnnxImpl;
    std::unique_ptr<OnnxImpl> onnx_;
};

} // namespace alice
