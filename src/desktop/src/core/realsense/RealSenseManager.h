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
 * depth estimation. Runs the capture loop on a detachable std::thread so
 * a device yank never blocks the Qt event loop. Depth is processed by
 * DepthEstimator (ROI median + confidence-weighted EMA with discontinuity
 * detection); colour frames are aligned to depth and cached for
 * face-detection consumers.
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
    /** Lock-free read of the current measurement X (normalised 0..1). */
    float measureX() const { return measureXCached_.load(std::memory_order_relaxed); }
    /** Lock-free read of the current measurement Y (normalised 0..1). */
    float measureY() const { return measureYCached_.load(std::memory_order_relaxed); }
    /** Human-readable model name from RS2_CAMERA_INFO_NAME (e.g. "Intel RealSense D455"). */
    QString deviceName() const { QMutexLocker l(&deviceInfoMutex_); return deviceName_; }
    /** Bus identifier (typically "USB 3.x") for the attached camera. */
    QString deviceBus() const { QMutexLocker l(&deviceInfoMutex_); return deviceBus_; }

    void setMeasurementPosition(float x, float y);
    void getMeasurementPosition(float &x, float &y) const;

    /**
     * Treat the upcoming measurement as a teleport to a new target:
     * clear the depth estimator's temporal state so the next reading is
     * taken without any blending from the old position. Called by the UI
     * on mouse-press / tap events. Drag events keep using
     * setMeasurementPosition alone — the auto per-frame delta check still
     * covers big single-frame jumps, and the filter intentionally carries
     * some context across small drifts.
     */
    void jumpToPosition(float x, float y);

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

    /**
     * Sample a horizontal slice of depth values across the FULL frame
     * width at normalised Y = ny. Produces a left-to-right "top-view"
     * — each sample is the depth at a different lateral position in
     * the scene, so objects at different horizontal positions show
     * up at their respective depths in the plot, not collapsed into
     * a single value the way a vertical column does on a flat wall.
     *
     * Returns `samples` depth readings in meters, evenly spaced across
     * the frame width. Zero for pixels with no valid depth. Each
     * sample is the 3×3 local median around the chosen pixel so
     * one-pixel sensor holes don't spike the trace.
     *
     * Safe to call from any thread — takes the depth-cache mutex for
     * the duration of the copy + per-sample median.
     */
    QVariantList depthHorizontalSlice(float ny, int samples) const;

    // Enable/disable generation of the colorized depth visualisation.
    // When disabled, captureLoop skips the expensive colorizeDepth() call
    // entirely. Safe to call from any thread.
    void setColormapEnabled(bool enabled) { colormapEnabled_ = enabled; }
    bool isColormapEnabled() const { return colormapEnabled_.load(); }

    // Configuration
    void setStreamConfig(int depthW, int depthH, int depthFps, int colorW, int colorH, int colorFps);
    QVariantList availableDepthModes() const;
    QVariantList availableColorModes() const;

    /**
     * Enumerate every RealSense camera currently seen by librealsense.
     * Each entry is a QVariantMap with:
     *   "id"     — QString, RS2_CAMERA_INFO_SERIAL_NUMBER (stable,
     *              globally unique per camera)
     *   "name"   — QString, RS2_CAMERA_INFO_NAME
     *   "active" — bool, true for the camera currently streaming
     *
     * Safe to call from any thread — takes no internal locks.
     */
    QVariantList availableDevices() const;

    /**
     * Persist a preferred serial number. On the next start() (or if
     * currently running, immediately via restart) the pipeline will
     * bind to this specific camera. Empty string clears the
     * preference, reverting to librealsense's default pick.
     */
    void selectDevice(const QString &serial);

    /** Read the currently-stored preference; empty if auto-pick. */
    QString preferredDeviceId() const { return preferredSerial_; }

    // D415 / D435 / D435i measure reliably down to ~110 mm (the stereo
    // minimum depth below which the cameras' fields of view stop
    // overlapping). Older default of 200 mm was clipping legitimate
    // close-focus readings for macro and product shots. 150 mm gives
    // a ~40 mm cushion above the hardware minimum — conservative
    // enough that we don't publish readings from the noise floor
    // right at the edge, aggressive enough to unlock the useful
    // close-focus range the sensor actually supports.
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
    /** Enumeration or active camera changed — UI re-reads the list. */
    void availableDevicesChanged();

private slots:
    void checkFrameTimeout();

private:
    void captureLoop();
    QImage colorizeDepth(const uint16_t *depthData, int width, int height);

    // Threading — uses std::thread (not QThread) so it can be detached
    // when the device is yanked and wait_for_frames blocks indefinitely
    std::thread captureThread_;
    std::atomic<bool> running_{false};
    std::atomic<bool> connected_{false};

    // Depth processing: single-point estimator (ROI median + EMA with
    // discontinuity detection). Owned by the capture thread; parameter
    // setters from the UI thread use estimatorParamsMutex_.
    DepthEstimator estimator_;
    mutable QMutex estimatorParamsMutex_;
    mutable QMutex positionMutex_;
    float measureX_ = 0.5f;
    float measureY_ = 0.5f;

    int minValidDepth_ = kDefaultMinValidDepth;
    int maxValidDepth_ = kDefaultMaxValidDepth;
    // Lock-free mirrors of measureX_/measureY_ for QML binding reads at
    // ~60 Hz. Written under positionMutex_ in setMeasurementPosition and
    // read atomically from the UI thread to avoid taking the mutex from
    // two separate property getters per paint (measureX() and measureY()).
    std::atomic<float> measureXCached_{0.5f};
    std::atomic<float> measureYCached_{0.5f};

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

    // Sensor depth scale in metres per raw Z16 unit. Default 0.001
    // (1 mm per unit) matches librealsense's factory configuration; the
    // capture loop overwrites this with the actual reported scale after
    // pipeline.start(), typically 0.0001 when we succeed in setting
    // RS2_OPTION_DEPTH_UNITS to sub-mm resolution. Atomic so the UI
    // thread's depthAt() / depthHorizontalSlice() can read without a
    // lock while the capture thread refreshes it on reconnect.
    std::atomic<float> depthScaleM_{0.001f};

    // Device identity read from RS2_CAMERA_INFO_NAME when the pipeline starts.
    // Protected by its own mutex so UI threads can read it safely.
    mutable QMutex deviceInfoMutex_;
    QString deviceName_;
    QString deviceBus_;
    // Serial of the currently-streaming camera — used as the "active"
    // match key in availableDevices().
    QString activeSerial_;

    // User-selected preferred serial. Populated at startup from
    // SettingsManager and by selectDevice() from the UI. On a new
    // start() we pass this to rs2::config::enable_device() so librealsense
    // binds to the exact camera the user picked.
    QString preferredSerial_;

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
