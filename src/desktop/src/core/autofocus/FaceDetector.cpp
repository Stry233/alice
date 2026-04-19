#include "core/autofocus/FaceDetector.h"

#include <algorithm>
#include <cmath>
#include <QMutexLocker>
#include <QMutex>

#ifdef ALICE_HAS_ONNX
#include <onnxruntime_cxx_api.h>
#endif

namespace alice {

#ifdef ALICE_HAS_ONNX

struct FaceDetector::OnnxImpl {
    Ort::Env env{ORT_LOGGING_LEVEL_WARNING, "AliceFace"};
    Ort::SessionOptions sessionOptions;
    std::unique_ptr<Ort::Session> session;
    QMutex mutex; // Protect inference
};

FaceDetector::FaceDetector(QObject *parent)
    : QObject(parent)
    , onnx_(std::make_unique<OnnxImpl>())
{
    onnx_->sessionOptions.SetIntraOpNumThreads(2);
    onnx_->sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
}

FaceDetector::~FaceDetector() = default;

bool FaceDetector::loadModel(const QString &modelPath) {
    try {
        auto pathStr = modelPath.toStdString();
        onnx_->session = std::make_unique<Ort::Session>(
            onnx_->env, pathStr.c_str(), onnx_->sessionOptions);

        // Analyze output shape to determine format
        auto outputInfo = onnx_->session->GetOutputTypeInfo(0);
        auto tensorInfo = outputInfo.GetTensorTypeAndShapeInfo();
        auto shape = tensorInfo.GetShape();

        // Common YOLO output shapes:
        // [1, numFeatures, numBoxes] (transposed) or [1, numBoxes, numFeatures]
        if (shape.size() == 3) {
            int dim1 = static_cast<int>(shape[1]);
            int dim2 = static_cast<int>(shape[2]);

            if (dim1 < dim2) {
                // [1, features, boxes] — transposed format
                outputFeatures_ = dim1;
                outputBoxes_ = dim2;
                outputTransposed_ = true;
            } else {
                // [1, boxes, features]
                outputBoxes_ = dim1;
                outputFeatures_ = dim2;
                outputTransposed_ = false;
            }
        }

        // Detect corner format (x1,y1,x2,y2) vs center format (cx,cy,w,h)
        // Models with 5 features use corner + confidence
        // Models with 6+ features use center + width/height + ...
        usesCornerFormat_ = (outputFeatures_ == 5);

        modelLoaded_ = true;
        emit modelLoaded(true);
        return true;

    } catch (const std::exception &e) {
        emit error(QString("Failed to load ONNX model: %1").arg(e.what()));
        modelLoaded_ = false;
        emit modelLoaded(false);
        return false;
    }
}

std::vector<RawFaceDetection> FaceDetector::detect(const QImage &image) {
    if (!modelLoaded_ || !onnx_->session) return {};

    QMutexLocker lock(&onnx_->mutex);

    // Preprocess: resize to 640x640, normalize to [0,1], convert to CHW
    QImage resized = image.scaled(kInputSize, kInputSize, Qt::IgnoreAspectRatio, Qt::SmoothTransformation)
                          .convertToFormat(QImage::Format_RGB888);

    float scaleX = static_cast<float>(image.width()) / kInputSize;
    float scaleY = static_cast<float>(image.height()) / kInputSize;

    // Create input tensor [1, 3, 640, 640] in CHW format
    std::vector<float> inputData(3 * kInputSize * kInputSize);
    const uint8_t *pixels = resized.constBits();
    int stride = resized.bytesPerLine();

    for (int y = 0; y < kInputSize; ++y) {
        const uint8_t *row = pixels + y * stride;
        for (int x = 0; x < kInputSize; ++x) {
            int srcIdx = x * 3;
            int dstIdx = y * kInputSize + x;
            inputData[0 * kInputSize * kInputSize + dstIdx] = row[srcIdx + 0] / 255.0f; // R
            inputData[1 * kInputSize * kInputSize + dstIdx] = row[srcIdx + 1] / 255.0f; // G
            inputData[2 * kInputSize * kInputSize + dstIdx] = row[srcIdx + 2] / 255.0f; // B
        }
    }

    // Run inference
    try {
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::array<int64_t, 4> inputShape = {1, 3, kInputSize, kInputSize};
        Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
            memInfo, inputData.data(), inputData.size(), inputShape.data(), inputShape.size());

        auto inputName = onnx_->session->GetInputNameAllocated(0, Ort::AllocatorWithDefaultOptions());
        auto outputName = onnx_->session->GetOutputNameAllocated(0, Ort::AllocatorWithDefaultOptions());
        const char *inputNames[] = {inputName.get()};
        const char *outputNames[] = {outputName.get()};

        auto outputs = onnx_->session->Run(
            Ort::RunOptions{nullptr}, inputNames, &inputTensor, 1, outputNames, 1);

        const float *outputData = outputs[0].GetTensorData<float>();
        return postprocess(outputData, outputBoxes_, outputFeatures_,
                           outputTransposed_, scaleX, scaleY);

    } catch (const std::exception &e) {
        emit error(QString("ONNX inference failed: %1").arg(e.what()));
        return {};
    }
}

std::vector<RawFaceDetection> FaceDetector::postprocess(
    const float *data, int numBoxes, int numFeatures,
    bool transposed, float scaleX, float scaleY) {

    std::vector<RawFaceDetection> detections;

    for (int i = 0; i < numBoxes; ++i) {
        auto getValue = [&](int feature) -> float {
            return transposed ? data[feature * numBoxes + i]
                              : data[i * numFeatures + feature];
        };

        float confidence = getValue(4);
        if (confidence < kConfidenceThreshold) continue;

        float x, y, w, h;
        if (usesCornerFormat_) {
            float x1 = getValue(0), y1 = getValue(1);
            float x2 = getValue(2), y2 = getValue(3);
            x = (x1 + x2) / 2.0f;
            y = (y1 + y2) / 2.0f;
            w = x2 - x1;
            h = y2 - y1;
        } else {
            x = getValue(0);
            y = getValue(1);
            w = getValue(2);
            h = getValue(3);
        }

        // Scale to original image coordinates
        float left   = (x - w / 2.0f) * scaleX;
        float top    = (y - h / 2.0f) * scaleY;
        float right  = (x + w / 2.0f) * scaleX;
        float bottom = (y + h / 2.0f) * scaleY;

        RawFaceDetection det;
        det.boundingBox = QRectF(left, top, right - left, bottom - top);
        det.confidence = confidence;

        // Extract landmarks if available (features 6–15 = 5 points × 2)
        if (numFeatures >= 16) {
            for (int lm = 0; lm < 5; ++lm) {
                float lx = getValue(6 + lm * 2) * scaleX;
                float ly = getValue(6 + lm * 2 + 1) * scaleY;
                det.landmarks.emplace_back(lx, ly);
            }
        }

        detections.push_back(std::move(det));
    }

    return nonMaxSuppression(detections);
}

float FaceDetector::computeIoU(const QRectF &a, const QRectF &b) {
    QRectF inter = a.intersected(b);
    if (inter.isEmpty()) return 0.0f;
    float interArea = static_cast<float>(inter.width() * inter.height());
    float unionArea = static_cast<float>(a.width() * a.height() + b.width() * b.height()) - interArea;
    return (unionArea > 0) ? interArea / unionArea : 0.0f;
}

std::vector<RawFaceDetection> FaceDetector::nonMaxSuppression(
    std::vector<RawFaceDetection> &detections) {

    std::sort(detections.begin(), detections.end(),
              [](const auto &a, const auto &b) { return a.confidence > b.confidence; });

    std::vector<RawFaceDetection> results;
    std::vector<bool> suppressed(detections.size(), false);

    for (size_t i = 0; i < detections.size() && results.size() < kMaxFaces; ++i) {
        if (suppressed[i]) continue;
        results.push_back(detections[i]);
        for (size_t j = i + 1; j < detections.size(); ++j) {
            if (!suppressed[j] && computeIoU(detections[i].boundingBox, detections[j].boundingBox) > kIouThreshold) {
                suppressed[j] = true;
            }
        }
    }
    return results;
}

#else // !ALICE_HAS_ONNX

struct FaceDetector::OnnxImpl {};

FaceDetector::FaceDetector(QObject *parent) : QObject(parent), onnx_(std::make_unique<OnnxImpl>()) {}
FaceDetector::~FaceDetector() = default;
bool FaceDetector::loadModel(const QString &) { emit error("ONNX Runtime not available"); return false; }
std::vector<RawFaceDetection> FaceDetector::detect(const QImage &) { return {}; }

std::vector<RawFaceDetection> FaceDetector::postprocess(const float*, int, int, bool, float, float) { return {}; }
float FaceDetector::computeIoU(const QRectF&, const QRectF&) { return 0; }
std::vector<RawFaceDetection> FaceDetector::nonMaxSuppression(std::vector<RawFaceDetection>&) { return {}; }

#endif

} // namespace alice
