#pragma once

#include <QObject>
#include <QSettings>
#include <QString>

namespace alice {

/**
 * Persistent settings manager using QSettings.
 * Same keys and defaults as Android SettingsManager.kt.
 */
class SettingsManager : public QObject {
    Q_OBJECT

    // Autofocus
    Q_PROPERTY(bool autofocusEnabled READ autofocusEnabled WRITE setAutofocusEnabled NOTIFY autofocusEnabledChanged)
    Q_PROPERTY(QString autofocusMode READ autofocusMode WRITE setAutofocusMode NOTIFY autofocusModeChanged)
    Q_PROPERTY(float confidenceThreshold READ confidenceThreshold WRITE setConfidenceThreshold NOTIFY confidenceThresholdChanged)
    Q_PROPERTY(bool smoothingEnabled READ smoothingEnabled WRITE setSmoothingEnabled NOTIFY smoothingEnabledChanged)
    Q_PROPERTY(int responseSpeed READ responseSpeed WRITE setResponseSpeed NOTIFY responseSpeedChanged)

    // Motor
    Q_PROPERTY(int motorSpeed READ motorSpeed WRITE setMotorSpeed NOTIFY motorSpeedChanged)
    Q_PROPERTY(bool motorReverse READ motorReverse WRITE setMotorReverse NOTIFY motorReverseChanged)
    Q_PROPERTY(int motorOffset READ motorOffset WRITE setMotorOffset NOTIFY motorOffsetChanged)
    Q_PROPERTY(int motorDestAddress READ motorDestAddress WRITE setMotorDestAddress NOTIFY motorDestAddressChanged)

    // Depth
    Q_PROPERTY(float depthConfidenceThreshold READ depthConfidenceThreshold WRITE setDepthConfidenceThreshold NOTIFY depthConfidenceThresholdChanged)
    Q_PROPERTY(int depthMinDistance READ depthMinDistance WRITE setDepthMinDistance NOTIFY depthMinDistanceChanged)
    Q_PROPERTY(int depthMaxDistance READ depthMaxDistance WRITE setDepthMaxDistance NOTIFY depthMaxDistanceChanged)

    // Network
    Q_PROPERTY(int syncPort READ syncPort WRITE setSyncPort NOTIFY syncPortChanged)

public:
    explicit SettingsManager(QObject *parent = nullptr);

    // Autofocus
    bool autofocusEnabled() const;
    void setAutofocusEnabled(bool v);
    QString autofocusMode() const;
    void setAutofocusMode(const QString &v);
    float confidenceThreshold() const;
    void setConfidenceThreshold(float v);
    bool smoothingEnabled() const;
    void setSmoothingEnabled(bool v);
    int responseSpeed() const;
    void setResponseSpeed(int v);

    // Motor
    int motorSpeed() const;
    void setMotorSpeed(int v);
    bool motorReverse() const;
    void setMotorReverse(bool v);
    int motorOffset() const;
    void setMotorOffset(int v);
    int motorDestAddress() const;
    void setMotorDestAddress(int v);

    // Depth
    float depthConfidenceThreshold() const;
    void setDepthConfidenceThreshold(float v);
    int depthMinDistance() const;
    void setDepthMinDistance(int v);
    int depthMaxDistance() const;
    void setDepthMaxDistance(int v);

    // Network
    int syncPort() const;
    void setSyncPort(int v);

    // System
    QString logVerbosity() const;
    void setLogVerbosity(const QString &v);
    bool autoReconnect() const;
    void setAutoReconnect(bool v);

    // Video resolution
    int depthResW() const;
    int depthResH() const;
    int depthResFps() const;
    void setDepthResolution(int w, int h, int fps);
    int colorResW() const;
    int colorResH() const;
    int colorResFps() const;
    void setColorResolution(int w, int h, int fps);
    int captureResW() const;
    int captureResH() const;
    int captureResFps() const;
    void setCaptureResolution(int w, int h, int fps);

    // Transmission quality
    int txQualityDepth() const;
    void setTxQualityDepth(int v);
    int txQualityCapture() const;
    void setTxQualityCapture(int v);
    int txMaxFps() const;
    void setTxMaxFps(int v);

    // Motor last position
    int motorLastPosition() const;
    void setMotorLastPosition(int v);

public slots:
    void resetAutofocusSettings();
    void resetMotorSettings();
    void resetDepthSettings();
    void resetAllSettings();

signals:
    void autofocusEnabledChanged();
    void autofocusModeChanged();
    void confidenceThresholdChanged();
    void smoothingEnabledChanged();
    void responseSpeedChanged();
    void motorSpeedChanged();
    void motorReverseChanged();
    void motorOffsetChanged();
    void motorDestAddressChanged();
    void depthConfidenceThresholdChanged();
    void depthMinDistanceChanged();
    void depthMaxDistanceChanged();
    void syncPortChanged();

private:
    QSettings settings_;
};

} // namespace alice
