#include <gtest/gtest.h>
#include <QCoreApplication>
#include "core/system/SystemMonitor.h"

// QFile and QTextStream need a QCoreApplication instance
class SystemMonitorTest : public ::testing::Test {
protected:
    static void SetUpTestSuite() {
        if (!QCoreApplication::instance()) {
            static int argc = 1;
            static char arg0[] = "test";
            static char *argv[] = {arg0};
            static QCoreApplication app(argc, argv);
        }
    }
};

TEST_F(SystemMonitorTest, InitialValuesAreZero) {
    alice::SystemMonitor monitor;
    EXPECT_DOUBLE_EQ(monitor.cpuUsage(), 0.0);
    EXPECT_GE(monitor.memoryUsage(), 0.0);
    EXPECT_GE(monitor.gpuUsage(), 0.0);
}

TEST_F(SystemMonitorTest, MemoryUsageIsReasonable) {
    alice::SystemMonitor monitor;
    monitor.poll();
    EXPECT_GT(monitor.memoryUsage(), 0.0);
    EXPECT_LT(monitor.memoryUsage(), 16.0 * 1024 * 1024 * 1024);
}

TEST_F(SystemMonitorTest, CpuUsageInRange) {
    alice::SystemMonitor monitor;
    monitor.poll();
    EXPECT_GE(monitor.cpuUsage(), 0.0);
    EXPECT_LE(monitor.cpuUsage(), 100.0);
}
