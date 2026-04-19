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

#include "core/realsense/DepthEstimator.h"

namespace alice {

/**
 * RealSense depth camera manager.
 *
 * Wraps librealsense2 for depth/color streaming with robust single-point
 * depth estimation. The capture loop runs on a detachable std::thread so
 * a device yank never blocks the Qt event loop.
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
    float measureX() const { return measureXCached_.load(std::memory_order_relaxed); }
    float measureY() const { return measureYCached_.load(std::memory_order_relaxed); }
    QString deviceName() const { QMutexLocker l(&deviceInfoMutex_); return deviceName_; }
    QString deviceBus() const { QMutexLocker l(&deviceInfoMutex_); return deviceBus_; }

    /** User move (tap, drag). Resets the depth estimator. */
    void setMeasurementPosition(float x, float y);

    /**
     * AF-F tracker update. Does NOT reset — the tracker moves by sub-pixel
     * amounts per frame and EMA continuity across those is desirable. A
     * real tracker jump still trips the estimator's own delta check.
     */
    void setTrackedPosition(float x, float y);

    void getMeasurementPosition(float &x, float &y) const;

    /**
     * Alias for setMeasurementPosition; kept distinct so the tap-to-focus
     * path can broadcast to the sync peer and update the AF target.
     */
    void jumpToPosition(float x, float y);

    /**
     * Sample depth (m) at a point in the color-aligned frame. Returns 0
     * if no valid data. Thread-safe.
     */
    float depthAt(float nx, float ny) const;

    /**
     * Horizontal top-view slice at normalised y = ny — a left-to-right
     * depth trace across the full frame width, one 3×3 median per sample.
     * Zero for pixels with no valid depth. Thread-safe.
     */
    QVariantList depthHorizontalSlice(float ny, int samples) const;

    void setColormapEnabled(bool enabled) { colormapEnabled_ = enabled; }
    bool isColormapEnabled() const { return colormapEnabled_.load(); }

    void setStreamConfig(int depthW, int depthH, int depthFps, int colorW, int colorH, int colorFps);
    QVariantList availableDepthModes() const;
    QVariantList availableColorModes() const;

    /**
     * Every RealSense camera currently visible. Each entry:
     *   "id"     — serial number (stable, globally unique)
     *   "name"   — human-readable model
     *   "active" — true for the camera currently streaming
     */
    QVariantList availableDevices() const;

    /** Persist a preferred serial. Empty string reverts to auto-pick. */
    void selectDevice(const QString &serial);
    QString preferredDeviceId() const { return preferredSerial_; }

    // 150 mm sits ~40 mm above the D4xx ~110 mm stereo minimum — enough
    // cushion to stay off the noise floor while keeping macro shots usable.
    static constexpr int kDefaultMinValidDepth = 150;
    static constexpr int kDefaultMaxValidDepth = 10000;

    void setMinValidDepth(int mm);
    void setMaxValidDepth(int mm);
    void setDepthSmoothing(float alpha);
    float depthSmoothing() const { return estimator_.smoothing(); }

public slots:
    void start();
    void stop();

signals:
    void connectionChanged(bool connected);
    void depthChanged(float depthMeters, float confidence);
    void colorFrameReady(const QImage &frame);
    void depthFrameReady(const QImage &colorizedDepth);
    void error(const QString &message);
    void availableDevicesChanged();

private slots:
    void checkFrameTimeout();

private:
    void captureLoop();
    QImage colorizeDepth(const uint16_t *depthData, int width, int height);

    // std::thread (not QThread) so a blocked wait_for_frames can be
    // detached when the device is yanked.
    std::thread captureThread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> connected_{false};

    DepthEstimator estimator_;
    mutable QMutex estimatorParamsMutex_;
    mutable QMutex positionMutex_;
    float measureX_ = 0.5f;
    float measureY_ = 0.5f;

    int minValidDepth_ = kDefaultMinValidDepth;
    int maxValidDepth_ = kDefaultMaxValidDepth;

    // Lock-free mirrors of measureX_/Y_ for QML binding reads at ~60 Hz.
    std::atomic<float> measureXCached_{0.5f};
    std::atomic<float> measureYCached_{0.5f};

    std::atomic<float> depth_{0.0f};
    std::atomic<float> confidence_{0.0f};

    std::atomic<qint64> connectedSinceMs_{0};
    std::atomic<qint64> lastDisconnectMs_{0};

    // Colormap is expensive — skip unless a consumer is listening.
    std::atomic<bool> colormapEnabled_{false};

    // Color-aligned depth cache for off-crosshair sampling (face tracker,
    // per-eye depth, horizontal slice).
    mutable QMutex depthCacheMutex_;
    std::vector<uint16_t> depthCache_;
    int depthCacheW_ = 0;
    int depthCacheH_ = 0;

    // Metres per raw Z16 unit. Refreshed from the sensor after start().
    // Atomic so off-thread sampling (depthAt, depthHorizontalSlice) can
    // read without a lock.
    std::atomic<float> depthScaleM_{0.001f};

    mutable QMutex deviceInfoMutex_;
    QString deviceName_;
    QString deviceBus_;
    QString activeSerial_;

    // Preferred camera serial. Populated from SettingsManager and by
    // selectDevice(); passed to rs2::config::enable_device() at start().
    QString preferredSerial_;

    QTimer frameTimeoutTimer_;
    std::atomic<int64_t> lastFrameTimeMs_{0};
    static constexpr int kFrameTimeoutMs = 3000;

    std::atomic<bool> lastInitFailed_{false};

    // Consecutive invalid frames. After kInvalidClearFrames (~0.25 s) the
    // capture loop emits a zeroed depthChanged so the UI blanks instead
    // of freezing on pre-drag depth.
    int invalidStreak_ = 0;
    static constexpr int kInvalidClearFrames = 8;

    int depthWidth_ = 640, depthHeight_ = 480, depthFps_ = 30;
    int colorWidth_ = 640, colorHeight_ = 480, colorFps_ = 30;

    // PIMPL hides librealsense2 headers. Shared so a detached thread
    // keeps the pipeline alive.
    struct Impl;
    std::shared_ptr<Impl> impl_;
};

} // namespace alice
