#pragma once

#include <QObject>
#include <QTimer>
#include <array>

namespace alice {

class SystemMonitor : public QObject {
    Q_OBJECT
    Q_PROPERTY(double cpuUsage READ cpuUsage NOTIFY statsChanged)
    Q_PROPERTY(double gpuUsage READ gpuUsage NOTIFY statsChanged)
    Q_PROPERTY(double memoryUsage READ memoryUsage NOTIFY statsChanged)
    Q_PROPERTY(QString memoryFormatted READ memoryFormatted NOTIFY statsChanged)

public:
    explicit SystemMonitor(QObject *parent = nullptr);

    double cpuUsage() const { return cpuUsage_; }
    double gpuUsage() const { return gpuUsage_; }
    double memoryUsage() const { return memoryUsage_; }
    QString memoryFormatted() const;

    void poll();

signals:
    void statsChanged();

private:
    void readCpuUsage();
    void readMemoryUsage();
    void readGpuUsage();

    QTimer timer_;
    double cpuUsage_ = 0.0;
    double gpuUsage_ = 0.0;
    double memoryUsage_ = 0.0;

    long long prevCpuTotal_ = 0;
    long long prevCpuIdle_ = 0;
};

} // namespace alice
