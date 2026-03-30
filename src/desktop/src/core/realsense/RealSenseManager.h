#pragma once

#include <QObject>
#include <QMutex>
#include <QTimer>
#include <QElapsedTimer>
#include <QImage>
#include <QVariantList>
#include <QVariantMap>
#include <memory>
#include <atomic>
#include <thread>

#include "core/realsense/KalmanFilter.h"
#include "core/realsense/BilateralFilter.h"

namespace alice {

/**
 * RealSense depth camera manager.
 * Wraps librealsense2 for depth/color streaming with ROI-based depth calculation.
 * Ported from RealSenseManager.kt + RealSenseDepthCalculator.kt.
 */
class RealSenseManager : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool connected READ isConnected NOTIFY connectionChanged)
    Q_PROPERTY(float depth READ currentDepth NOTIFY depthChanged)
    Q_PROPERTY(float confidence READ currentConfidence NOTIFY depthChanged)

public:
    explicit RealSenseManager(QObject *parent = nullptr);
    ~RealSenseManager() override;

    bool isConnected() const { return connected_; }
    float currentDepth() const { return depth_; }
    float currentConfidence() const { return confidence_; }

    void setMeasurementPosition(float x, float y);
    void getMeasurementPosition(float &x, float &y) const;

    // Configuration
    void setStreamConfig(int depthW, int depthH, int depthFps, int colorW, int colorH, int colorFps);
    QVariantList availableDepthModes() const;
    QVariantList availableColorModes() const;

    static constexpr int kMinRoiSize = 8;
    static constexpr int kMaxRoiSize = 24;
    static constexpr int kDefaultRoiSize = 16;

    static constexpr int kMinValidDepth = 200;
    static constexpr int kMaxValidDepth = 10000;

public slots:
    void start();
    void stop();

signals:
    void connectionChanged(bool connected);
    void depthChanged(float depthMeters, float confidence);
    void colorFrameReady(const QImage &frame);
    void depthFrameReady(const QImage &colorizedDepth);
    void error(const QString &message);

private slots:
    void checkFrameTimeout();

private:
    void captureLoop();
    float calculateDepth(const uint16_t *depthData, int width, int height);
    QImage colorizeDepth(const uint16_t *depthData, int width, int height);

    // Threading — uses std::thread (not QThread) so it can be detached
    // when the device is yanked and wait_for_frames blocks indefinitely
    std::thread captureThread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> connected_{false};

    // Depth processing
    KalmanFilter kalmanFilter_;
    BilateralFilter bilateralFilter_;
    mutable QMutex positionMutex_;
    float measureX_ = 0.5f;
    float measureY_ = 0.5f;
    int roiSize_ = kDefaultRoiSize;

    // Current state
    std::atomic<float> depth_{0.0f};
    std::atomic<float> confidence_{0.0f};

    // Frame timeout watchdog (main thread timer)
    QTimer frameTimeoutTimer_;
    std::atomic<int64_t> lastFrameTimeMs_{0};
    static constexpr int kFrameTimeoutMs = 3000;

    // Suppress repeated "no device" errors (same pattern as motor)
    std::atomic<bool> lastInitFailed_{false};

    // Configurable stream resolution (default 640x480@30)
    int depthWidth_ = 640, depthHeight_ = 480, depthFps_ = 30;
    int colorWidth_ = 640, colorHeight_ = 480, colorFps_ = 30;

    // Forward-declared implementation (hides librealsense2 headers).
    // Shared pointer so a detached thread keeps the pipeline alive.
    struct Impl;
    std::shared_ptr<Impl> impl_;
};

} // namespace alice
