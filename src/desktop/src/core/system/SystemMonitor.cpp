#include "core/system/SystemMonitor.h"
#include <QFile>
#include <QTextStream>
#include <QProcess>

namespace alice {

SystemMonitor::SystemMonitor(QObject *parent)
    : QObject(parent)
{
    timer_.setInterval(1000);
    connect(&timer_, &QTimer::timeout, this, &SystemMonitor::poll);
    timer_.start();
}

void SystemMonitor::poll() {
    readCpuUsage();
    readMemoryUsage();
    readGpuUsage();
    emit statsChanged();
}

QString SystemMonitor::memoryFormatted() const {
    double gb = memoryUsage_ / (1024.0 * 1024.0 * 1024.0);
    if (gb >= 1.0)
        return QString::number(gb, 'f', 1) + "G";
    double mb = memoryUsage_ / (1024.0 * 1024.0);
    return QString::number(static_cast<int>(mb)) + "M";
}

void SystemMonitor::readCpuUsage() {
    QFile file("/proc/stat");
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
        return;

    QString line = QTextStream(&file).readLine();
    file.close();

    QStringList parts = line.split(QRegularExpression("\\s+"), Qt::SkipEmptyParts);
    if (parts.size() < 5 || parts[0] != "cpu")
        return;

    long long total = 0;
    for (int i = 1; i < parts.size(); ++i)
        total += parts[i].toLongLong();

    long long idle = parts[4].toLongLong();

    long long totalDelta = total - prevCpuTotal_;
    long long idleDelta = idle - prevCpuIdle_;

    if (totalDelta > 0)
        cpuUsage_ = 100.0 * (1.0 - static_cast<double>(idleDelta) / totalDelta);

    prevCpuTotal_ = total;
    prevCpuIdle_ = idle;
}

void SystemMonitor::readMemoryUsage() {
    QFile file("/proc/self/status");
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text))
        return;

    QTextStream stream(&file);
    while (!stream.atEnd()) {
        QString line = stream.readLine();
        if (line.startsWith("VmRSS:")) {
            QStringList parts = line.split(QRegularExpression("\\s+"), Qt::SkipEmptyParts);
            if (parts.size() >= 2)
                memoryUsage_ = parts[1].toDouble() * 1024.0;
            break;
        }
    }
}

void SystemMonitor::readGpuUsage() {
    QFile nvFile("/proc/driver/nvidia/gpus/0/utilization");
    if (nvFile.exists() && nvFile.open(QIODevice::ReadOnly | QIODevice::Text)) {
        QTextStream stream(&nvFile);
        while (!stream.atEnd()) {
            QString line = stream.readLine();
            if (line.contains("Gpu")) {
                QStringList parts = line.split(QRegularExpression("\\s+"), Qt::SkipEmptyParts);
                if (parts.size() >= 2)
                    gpuUsage_ = parts[1].toDouble();
                break;
            }
        }
        return;
    }

    QFile drmFile("/sys/class/drm/card0/device/gpu_busy_percent");
    if (drmFile.open(QIODevice::ReadOnly | QIODevice::Text)) {
        gpuUsage_ = QTextStream(&drmFile).readLine().trimmed().toDouble();
        return;
    }

    gpuUsage_ = 0.0;
}

} // namespace alice
