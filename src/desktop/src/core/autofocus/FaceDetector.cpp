#include "core/autofocus/FaceDetector.h"

#include <algorithm>
#include <cmath>
#include <QMutexLocker>
#include <QMutex>

#ifdef ALICE_HAS_ONNX
#include <onnxruntime_cxx_api.h>

// Platform-specific execution provider factories. These headers are only
// shipped on their target platform by the onnxruntime distribution, so we
// gate the corresponding code paths on __has_include so the build still
// succeeds on systems where only CPU (+ optionally CUDA) is available.
#if defined(_WIN32) && __has_include(<dml_provider_factory.h>)
#  include <dml_provider_factory.h>
#  define ALICE_ORT_HAS_DML 1
#endif
#if defined(__APPLE__) && __has_include(<coreml_provider_factory.h>)
#  include <coreml_provider_factory.h>
#  define ALICE_ORT_HAS_COREML 1
#endif
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
    onnx_->sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
}

FaceDetector::~FaceDetector() = default;

bool FaceDetector::loadModel(const QString &modelPath) {
    try {
        // Cross-platform execution-provider selection. We ask the runtime
        // which providers it was compiled with via Ort::GetAvailableProviders
        // and try them in priority order. Each append is wrapped in its own
        // try/catch so a provider that *looks* registered but fails at
        // initialisation (missing cuDNN, stale driver, unsupported GPU)
        // gracefully falls through to the next option. CPU is always the
        // final fallback and doesn't need an explicit Append call.
        //
        //   Priority:  TensorRT → CUDA → DirectML → CoreML → CPU
        //
        // This matches the real-world "fastest available path" on each
        // platform — NVIDIA boxes prefer TRT/CUDA, Windows without NVIDIA
        // still gets hardware acceleration via DirectML, and Apple Silicon
        // picks up ANE/GPU via CoreML.
        executionProvider_ = "CPU";
        const auto providers = Ort::GetAvailableProviders();
        auto hasProvider = [&](const char *name) {
            return std::find(providers.begin(), providers.end(),
                             std::string(name)) != providers.end();
        };

        auto tryProvider = [&](const char *displayName, auto &&fn) {
            if (executionProvider_ != "CPU") return; // already got one
            try {
                fn();
                executionProvider_ = displayName;
            } catch (const std::exception &e) {
                emit error(QString("%1 EP present but failed to init, skipping: %2")
                               .arg(displayName, e.what()));
            }
        };

        // 1. TensorRT — NVIDIA, fastest when available (requires TRT + cuDNN).
        if (hasProvider("TensorrtExecutionProvider")) {
            tryProvider("TensorRT", [&]{
                OrtTensorRTProviderOptions trtOpts{};
                trtOpts.device_id = 0;
                trtOpts.trt_fp16_enable = 1;  // safe on all modern NVIDIA
                onnx_->sessionOptions.AppendExecutionProvider_TensorRT(trtOpts);
            });
        }

        // 2. CUDA — NVIDIA fallback when TensorRT isn't installed.
        if (hasProvider("CUDAExecutionProvider")) {
            tryProvider("CUDA", [&]{
                OrtCUDAProviderOptions cudaOpts{};
                cudaOpts.device_id = 0;
                onnx_->sessionOptions.AppendExecutionProvider_CUDA(cudaOpts);
            });
        }

#ifdef ALICE_ORT_HAS_DML
        // 3. DirectML — Windows only, any DX12-capable GPU (NVIDIA / AMD / Intel).
        if (hasProvider("DmlExecutionProvider")) {
            tryProvider("DirectML", [&]{
                Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_DML(
                    static_cast<OrtSessionOptions *>(onnx_->sessionOptions), 0));
            });
        }
#endif

#ifdef ALICE_ORT_HAS_COREML
        // 4. CoreML — macOS only, uses Apple Neural Engine + GPU when present.
        if (hasProvider("CoreMLExecutionProvider")) {
            tryProvider("CoreML", [&]{
                uint32_t flags = 0;
                Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_CoreML(
                    static_cast<OrtSessionOptions *>(onnx_->sessionOptions), flags));
            });
        }
#endif

        // CPU intra-op threads — tuned for the GPU-absent case. When a GPU EP
        // is active this is ignored for the heavy ops but still used for the
        // CPU kernels that fall through.
        onnx_->sessionOptions.SetIntraOpNumThreads(4);

        auto pathStr = modelPath.toStdString();
        onnx_->session = std::make_unique<Ort::Session>(
            onnx_->env, pathStr.c_str(), onnx_->sessionOptions);

        // NOTE: We deliberately do NOT trust the static output shape here.
        // YOLO-face exports from deepghs/yolo-face have a dynamic second/third
        // axis (shape = [batch, 5, floor(h/2-..)*floor(w/2-..) + ...]) which
        // ONNX reports as -1. A naive `dim1 < dim2` comparison with -1 flips
        // the layout detection and catastrophically mis-indexes the output
        // buffer. Instead, we wait until the first real inference and read
        // the concrete shape of the returned tensor.
        outputBoxes_ = 0;
        outputFeatures_ = 0;
        outputTransposed_ = false;

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

        // Determine the layout from the CONCRETE output shape of this
        // inference, not from the (potentially dynamic) metadata. Cached
        // after the first call since the shape is deterministic for a
        // fixed-size 640×640 input.
        if (outputBoxes_ == 0 || outputFeatures_ == 0) {
            auto outShapeInfo = outputs[0].GetTensorTypeAndShapeInfo();
            auto outShape = outShapeInfo.GetShape();
            if (outShape.size() == 3) {
                const int d1 = static_cast<int>(outShape[1]);
                const int d2 = static_cast<int>(outShape[2]);
                if (d1 > 0 && d2 > 0) {
                    if (d1 < d2) {
                        outputFeatures_ = d1;
                        outputBoxes_    = d2;
                        outputTransposed_ = true;
                    } else {
                        outputBoxes_      = d1;
                        outputFeatures_   = d2;
                        outputTransposed_ = false;
                    }
                }
            } else if (outShape.size() == 2) {
                // [boxes, features] — already-NMSed output
                outputBoxes_ = static_cast<int>(outShape[0]);
                outputFeatures_ = static_cast<int>(outShape[1]);
                outputTransposed_ = false;
            }
            if (outputBoxes_ <= 0 || outputFeatures_ <= 0) {
                emit error(QString("Unexpected output shape: dims=%1").arg(outShape.size()));
                return {};
            }
        }

        const float *outputData = outputs[0].GetTensorData<float>();
        return postprocess(outputData, outputBoxes_, outputFeatures_,
                           outputTransposed_, scaleX, scaleY,
                           image.width(), image.height());

    } catch (const std::exception &e) {
        emit error(QString("ONNX inference failed: %1").arg(e.what()));
        return {};
    }
}

std::vector<RawFaceDetection> FaceDetector::postprocess(
    const float *data, int numBoxes, int numFeatures,
    bool transposed, float scaleX, float scaleY, int origW, int origH) {

    std::vector<RawFaceDetection> detections;
    detections.reserve(32);

    const float origWf = static_cast<float>(origW);
    const float origHf = static_cast<float>(origH);

    for (int i = 0; i < numBoxes; ++i) {
        auto getValue = [&](int feature) -> float {
            return transposed ? data[feature * numBoxes + i]
                              : data[i * numFeatures + feature];
        };

        // YOLO pre-NMS outputs always use the center format (cx, cy, w, h).
        // The previous "usesCornerFormat for 5 features" heuristic was wrong
        // and produced negative widths / tiny boxes anchored at the origin.
        const float cx = getValue(0);
        const float cy = getValue(1);
        const float w  = getValue(2);
        const float h  = getValue(3);
        const float confidence = getValue(4);

        if (!(confidence >= kConfidenceThreshold)) continue;  // NaN-safe
        if (!(w > 0.0f) || !(h > 0.0f)) continue;
        // Reject degenerate / obviously-bogus boxes (>2x input size).
        if (w > 2.0f * kInputSize || h > 2.0f * kInputSize) continue;

        float left   = (cx - w * 0.5f) * scaleX;
        float top    = (cy - h * 0.5f) * scaleY;
        float right  = (cx + w * 0.5f) * scaleX;
        float bottom = (cy + h * 0.5f) * scaleY;

        // Clamp to image bounds and skip if the clamped box is empty.
        left   = std::clamp(left,   0.0f, origWf);
        top    = std::clamp(top,    0.0f, origHf);
        right  = std::clamp(right,  0.0f, origWf);
        bottom = std::clamp(bottom, 0.0f, origHf);
        const float boxW = right - left;
        const float boxH = bottom - top;
        // Minimum face size: drop background faces that are too small to
        // matter for cinema autofocus. 4% of the shorter dimension ≈ 20 px
        // on a 480-row frame, ≈ 28 px on a 720-row frame.
        const float minDim = 0.04f * std::min(origWf, origHf);
        if (boxW < minDim || boxH < minDim) continue;
        if (boxW * boxH > 0.95f * origWf * origHf) continue; // covers whole frame → garbage

        RawFaceDetection det;
        det.boundingBox = QRectF(left, top, boxW, boxH);
        det.confidence = confidence;

        // Extract 5-point landmarks if the model provides them. YOLO-face
        // variants with landmarks use 16 features: cxywh + conf + 5*(x,y) + class.
        // Landmarks start at index 6 (index 5 is the single-class score).
        if (numFeatures >= 15) {
            for (int lm = 0; lm < 5; ++lm) {
                const int baseIdx = 6 + lm * 2;
                if (baseIdx + 1 >= numFeatures) break;
                float lx = getValue(baseIdx) * scaleX;
                float ly = getValue(baseIdx + 1) * scaleY;
                if (std::isfinite(lx) && std::isfinite(ly)) {
                    det.landmarks.emplace_back(std::clamp(lx, 0.0f, origWf),
                                               std::clamp(ly, 0.0f, origHf));
                }
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

std::vector<RawFaceDetection> FaceDetector::postprocess(const float*, int, int, bool, float, float, int, int) { return {}; }
float FaceDetector::computeIoU(const QRectF&, const QRectF&) { return 0; }
std::vector<RawFaceDetection> FaceDetector::nonMaxSuppression(std::vector<RawFaceDetection>&) { return {}; }

#endif

} // namespace alice
