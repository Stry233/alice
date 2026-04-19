#include "core/system/SystemMonitor.h"
#include <cstdio>
#include <cstring>

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
    // Use C I/O — faster than QFile/QTextStream, no heap allocation
    FILE *f = fopen("/proc/stat", "r");
    if (!f) return;

    char buf[256];
    if (!fgets(buf, sizeof(buf), f)) { fclose(f); return; }
    fclose(f);

    // Parse "cpu  user nice system idle iowait irq softirq steal"
    long long user, nice, system, idle, iowait, irq, softirq, steal;
    if (sscanf(buf, "cpu %lld %lld %lld %lld %lld %lld %lld %lld",
               &user, &nice, &system, &idle, &iowait, &irq, &softirq, &steal) < 4)
        return;

    long long total = user + nice + system + idle + iowait + irq + softirq + steal;
    long long totalDelta = total - prevCpuTotal_;
    long long idleDelta = idle - prevCpuIdle_;

    if (totalDelta > 0)
        cpuUsage_ = 100.0 * (1.0 - static_cast<double>(idleDelta) / totalDelta);

    prevCpuTotal_ = total;
    prevCpuIdle_ = idle;
}

void SystemMonitor::readMemoryUsage() {
    FILE *f = fopen("/proc/self/status", "r");
    if (!f) return;

    char buf[256];
    while (fgets(buf, sizeof(buf), f)) {
        if (strncmp(buf, "VmRSS:", 6) == 0) {
            long kb = 0;
            sscanf(buf + 6, " %ld", &kb);
            memoryUsage_ = static_cast<double>(kb) * 1024.0;
            break;
        }
    }
    fclose(f);
}

void SystemMonitor::readGpuUsage() {
    // NVIDIA
    FILE *f = fopen("/proc/driver/nvidia/gpus/0/utilization", "r");
    if (f) {
        char buf[256];
        while (fgets(buf, sizeof(buf), f)) {
            if (strstr(buf, "Gpu")) {
                int val = 0;
                sscanf(strstr(buf, "Gpu") + 3, " %d", &val);
                gpuUsage_ = val;
                break;
            }
        }
        fclose(f);
        return;
    }

    // AMD/Intel DRM
    f = fopen("/sys/class/drm/card0/device/gpu_busy_percent", "r");
    if (f) {
        int val = 0;
        fscanf(f, "%d", &val);
        gpuUsage_ = val;
        fclose(f);
        return;
    }

    gpuUsage_ = 0.0;
}

} // namespace alice
