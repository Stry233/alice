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
    Q_PROPERTY(qint64 connectedSinceMs READ connectedSinceMs NOTIFY connectionChanged)
    Q_PROPERTY(qint64 lastDisconnectMs READ lastDisconnectMs NOTIFY connectionChanged)

public:
    explicit RealSenseManager(QObject *parent = nullptr);
    ~RealSenseManager() override;

    bool isConnected() const { return connected_; }
    float currentDepth() const { return depth_; }
    float currentConfidence() const { return confidence_; }
    qint64 connectedSinceMs() const { return connectedSinceMs_.load(); }
    qint64 lastDisconnectMs() const { return lastDisconnectMs_.load(); }
    /** Human-readable model name from RS2_CAMERA_INFO_NAME (e.g. "Intel RealSense D455"). */
    QString deviceName() const { QMutexLocker l(&deviceInfoMutex_); return deviceName_; }
    /** Bus identifier (typically "USB 3.x") for the attached camera. */
    QString deviceBus() const { QMutexLocker l(&deviceInfoMutex_); return deviceBus_; }

    void setMeasurementPosition(float x, float y);
    void getMeasurementPosition(float &x, float &y) const;

    /**
     * Sample the depth (in meters) at a point in the color frame.
     * @param nx Normalised X in [0, 1] (color-frame coordinates)
     * @param ny Normalised Y in [0, 1]
     * @return Depth in meters, or 0 if no valid data at the point.
     *
     * Reads from the last captured depth frame (already aligned to color),
     * sampling a small neighbourhood and returning the median of valid
     * depths to suppress holes. Thread-safe — callable from any thread.
     */
    float depthAt(float nx, float ny) const;

    // Enable/disable generation of the colorized depth visualisation.
    // When disabled, captureLoop skips the expensive colorizeDepth() call
    // entirely. Safe to call from any thread.
    void setColormapEnabled(bool enabled) { colormapEnabled_ = enabled; }
    bool isColormapEnabled() const { return colormapEnabled_.load(); }

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

    // Connection lifecycle timestamps (epoch ms; 0 = never)
    std::atomic<qint64> connectedSinceMs_{0};
    std::atomic<qint64> lastDisconnectMs_{0};

    // Visual colormap is expensive — only generate when something will
    // actually consume the result (depth overlay visible or sync stream on).
    std::atomic<bool> colormapEnabled_{false};

    // Cache of the most recent color-aligned depth frame so that consumers
    // (face tracker, per-eye depth sampling) can query depths away from
    // the crosshair without waiting for the capture thread.
    mutable QMutex depthCacheMutex_;
    std::vector<uint16_t> depthCache_;
    int depthCacheW_ = 0;
    int depthCacheH_ = 0;

    // Device identity read from RS2_CAMERA_INFO_NAME when the pipeline starts.
    // Protected by its own mutex so UI threads can read it safely.
    mutable QMutex deviceInfoMutex_;
    QString deviceName_;
    QString deviceBus_;

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
