#include <gtest/gtest.h>
#include "core/system/SystemMonitor.h"

TEST(SystemMonitorTest, InitialValuesAreZero) {
    alice::SystemMonitor monitor;
    EXPECT_DOUBLE_EQ(monitor.cpuUsage(), 0.0);
    EXPECT_GE(monitor.memoryUsage(), 0.0);
    EXPECT_GE(monitor.gpuUsage(), 0.0);
}

TEST(SystemMonitorTest, MemoryUsageIsReasonable) {
    alice::SystemMonitor monitor;
    monitor.poll();
    EXPECT_GT(monitor.memoryUsage(), 0.0);
    EXPECT_LT(monitor.memoryUsage(), 16.0 * 1024 * 1024 * 1024);
}

TEST(SystemMonitorTest, CpuUsageInRange) {
    alice::SystemMonitor monitor;
    monitor.poll();
    EXPECT_GE(monitor.cpuUsage(), 0.0);
    EXPECT_LE(monitor.cpuUsage(), 100.0);
}
