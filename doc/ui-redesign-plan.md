# Alice Studio UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the Alice Studio Qt Quick desktop UI from a basic tab-based layout to a Palantir Gotham-inspired dual-mode (OPS/CFG) operational interface with responsive breakpoints, interactive status badges, camera zoom/pan, and professional calibration/settings views.

**Architecture:** Two-mode architecture: OPS mode (dense monitoring screen, 95% of usage) and CFG mode (calibration, settings, connection). The toolbar is shared. OPS adapts between 2-column (standard) and 3-column (wide, camera centered) layouts based on window width. Status badges are interactive with device-control popovers. A new `SystemMonitor` C++ class provides CPU/GPU/MEM metrics.

**Tech Stack:** Qt 6.5+ / Qt Quick / QML, C++17, CMake, Inter font family, Google Test

**Spec:** `docs/superpowers/specs/2026-03-30-alice-studio-ui-redesign-design.md`

---

## File Structure

### New C++ Files
| File | Responsibility |
|------|---------------|
| `src/core/system/SystemMonitor.h` | Q_OBJECT exposing CPU/GPU/MEM usage as Q_PROPERTYs |
| `src/core/system/SystemMonitor.cpp` | Linux `/proc` polling on 1s timer |
| `tests/test_system_monitor.cpp` | Unit tests for SystemMonitor |

### New QML Files
| File | Responsibility |
|------|---------------|
| `src/ui/qml/OpsView.qml` | Main operational view with responsive 2/3-column layout |
| `src/ui/qml/CfgView.qml` | Configuration mode container with sub-tab bar |
| `src/ui/qml/components/ModeToggle.qml` | OPS/CFG segmented button |
| `src/ui/qml/components/BadgePopover.qml` | Generic device popover (Motor, Depth, Camera) |
| `src/ui/qml/components/SyncPopover.qml` | Sync-specific popover with QR code |
| `src/ui/qml/components/ZoomToolbar.qml` | Camera zoom/pan floating toolbar |
| `src/ui/qml/components/MiniMap.qml` | Viewport position indicator (visible when zoomed >100%) |
| `src/ui/qml/components/CalibrationTable.qml` | Recorded points data table with confidence bars |
| `src/ui/qml/components/CalibrationGraph.qml` | Rich calibration curve with labeled axes, tooltips, R² |
| `src/ui/qml/components/SettingsCard.qml` | Reusable card for settings grid layout |
| `src/ui/qml/components/SystemMonitor.qml` | CPU/GPU/MEM progress bar display |
| `src/ui/qml/components/TelemetryGrid.qml` | Key-value telemetry display |
| `src/ui/qml/components/BottomStrip.qml` | Compact bottom status bar |
| `assets/icons/alice_logo.svg` | Alice icon converted from Android vector drawable |
| `assets/icons/zoom_in.svg` | Magnifier + plus icon (16x16) |
| `assets/icons/zoom_out.svg` | Magnifier + minus icon (16x16) |
| `assets/icons/zoom_fit.svg` | Fit-to-frame icon (16x16) |

### Modified Files
| File | Changes |
|------|---------|
| `src/ui/qml/Main.qml` | Replace tab navigation with OPS/CFG mode switching, new toolbar |
| `src/ui/qml/Theme.qml` | Add breakpoint, popover, sidebar, zoom tokens |
| `src/ui/qml/CalibrationView.qml` | Complete rewrite: compact previews, data table, rich graph |
| `src/ui/qml/SettingsView.qml` | Complete rewrite: single-screen card grid |
| `src/ui/qml/ConnectionDialog.qml` | Rename to ConnectionView.qml, rewrite with auto-start server, two states |
| `src/ui/qml/components/StatusBadge.qml` | Add click handler, popover trigger, hover/press states |
| `CMakeLists.txt` | Add SystemMonitor to alice_core, new QML files to alice_ui |
| `src/main.cpp` | Create and expose SystemMonitor as context property |

### Removed Files
| File | Replaced By |
|------|-------------|
| `src/ui/qml/CameraView.qml` | `OpsView.qml` |
| `src/ui/qml/ControlPanel.qml` | OpsView sidebar |
| `src/ui/qml/TelemetryPanel.qml` | `TelemetryGrid.qml` + `BottomStrip.qml` |

---

## Task 1: Theme Tokens Update

**Files:**
- Modify: `src/desktop/src/ui/qml/Theme.qml`

- [ ] **Step 1: Add new design tokens to Theme.qml**

Open `src/desktop/src/ui/qml/Theme.qml` and add these properties before the closing `}`:

```qml
    // Popover
    readonly property int popoverWidth: 200
    readonly property int syncPopoverWidth: 180

    // Sidebar widths
    readonly property int sidebarNarrow: 200
    readonly property int sidebarStandard: 260
    readonly property int sidebarWide: 240
    readonly property int telemetryColumnWidth: 260

    // Breakpoints (compact: < 1280, standard: 1280-1599, wide: 1600+)
    readonly property int breakpointCompact: 1024
    readonly property int breakpointStandard: 1280
    readonly property int breakpointWide: 1600

    // Camera zoom
    readonly property real zoomMin: 1.0
    readonly property real zoomMax: 4.0
    readonly property real zoomStep: 0.25

    // Section header (font size)
    readonly property int sectionFontSize: 9
```

- [ ] **Step 2: Verify the app still starts**

Run: `cd src/desktop/build && cmake --build . --target AliceDesktop 2>&1 | tail -5`
Expected: Build succeeds (or check for QML syntax errors if runtime only)

- [ ] **Step 3: Commit**

```bash
git add src/desktop/src/ui/qml/Theme.qml
git commit -m "feat(ui): add breakpoint, popover, sidebar, and zoom tokens to Theme"
```

---

## Task 2: Alice Logo SVG + Zoom Icons

**Files:**
- Create: `src/desktop/assets/icons/alice_logo.svg`
- Create: `src/desktop/assets/icons/zoom_in.svg`
- Create: `src/desktop/assets/icons/zoom_out.svg`
- Create: `src/desktop/assets/icons/zoom_fit.svg`
- Modify: `src/desktop/CMakeLists.txt`

- [ ] **Step 1: Create the icons directory**

```bash
mkdir -p src/desktop/assets/icons
```

- [ ] **Step 2: Create alice_logo.svg**

Convert the Android vector drawable paths to SVG. Create `src/desktop/assets/icons/alice_logo.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 538 595">
  <defs>
    <linearGradient id="g" x1="266" y1="326.66" x2="510.49" y2="461.46" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#DD5251"/>
      <stop offset="0.5" stop-color="#EB684D"/>
      <stop offset="1" stop-color="#F17144"/>
    </linearGradient>
  </defs>
  <path d="M383.21,114.07C547.2,183.66 564.14,359 508.24,465.46 452.33,571.91 306.86,645.95 168.08,550.01 285.81,595.36 415.17,515.14 455.04,435.04 494.9,354.93 506.38,204.45 383.21,114.07Z" fill="url(#g)" fill-rule="evenodd"/>
  <path d="M238.16,117.46C237.04,122.68 235.48,128.91 233.45,136.13 231.43,143.35 229.14,150.66 226.58,158.06L166.49,329.17 309.83,329.17 249.02,158.06C246.76,151.02 244.58,143.89 242.5,136.67 240.42,129.45 238.97,123.04 238.16,117.46ZM191.11,0 L285.94,0 434,410.63 431.68,416.35C421.74,436.36 406.21,456.39 386.67,474.46L365.73,491.02 336.61,407.47 138.99,407.47 96.28,530 0,530Z" fill="#FFFFFF" fill-rule="evenodd"/>
</svg>
```

- [ ] **Step 3: Create zoom_in.svg**

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
  <circle cx="11" cy="11" r="7"/>
  <line x1="16" y1="16" x2="21" y2="21"/>
  <line x1="8" y1="11" x2="14" y2="11"/>
  <line x1="11" y1="8" x2="11" y2="14"/>
</svg>
```

- [ ] **Step 4: Create zoom_out.svg**

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
  <circle cx="11" cy="11" r="7"/>
  <line x1="16" y1="16" x2="21" y2="21"/>
  <line x1="8" y1="11" x2="14" y2="11"/>
</svg>
```

- [ ] **Step 5: Create zoom_fit.svg**

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
  <path d="M8 3H5a2 2 0 0 0-2 2v3"/>
  <path d="M21 8V5a2 2 0 0 0-2-2h-3"/>
  <path d="M3 16v3a2 2 0 0 0 2 2h3"/>
  <path d="M16 21h3a2 2 0 0 0 2-2v-3"/>
</svg>
```

- [ ] **Step 6: Add icons as RESOURCES in CMakeLists.txt**

In the `qt_add_qml_module(alice_ui ...)` block, add to the `RESOURCES` section:

```cmake
        assets/icons/alice_logo.svg
        assets/icons/zoom_in.svg
        assets/icons/zoom_out.svg
        assets/icons/zoom_fit.svg
```

- [ ] **Step 7: Commit**

```bash
git add src/desktop/assets/icons/ src/desktop/CMakeLists.txt
git commit -m "feat(ui): add Alice logo SVG and zoom toolbar icons"
```

---

## Task 3: SystemMonitor C++ Class

**Files:**
- Create: `src/desktop/src/core/system/SystemMonitor.h`
- Create: `src/desktop/src/core/system/SystemMonitor.cpp`
- Create: `src/desktop/tests/test_system_monitor.cpp`
- Modify: `src/desktop/CMakeLists.txt`
- Modify: `src/desktop/src/main.cpp`

- [ ] **Step 1: Write the test file**

Create `src/desktop/tests/test_system_monitor.cpp`:

```cpp
#include <gtest/gtest.h>
#include "core/system/SystemMonitor.h"

TEST(SystemMonitorTest, InitialValuesAreZero) {
    alice::SystemMonitor monitor;
    EXPECT_DOUBLE_EQ(monitor.cpuUsage(), 0.0);
    EXPECT_GE(monitor.memoryUsage(), 0.0);
    // GPU may be 0 if not available
    EXPECT_GE(monitor.gpuUsage(), 0.0);
}

TEST(SystemMonitorTest, MemoryUsageIsReasonable) {
    alice::SystemMonitor monitor;
    monitor.poll();
    // Process should use at least some memory
    EXPECT_GT(monitor.memoryUsage(), 0.0);
    // And less than 16 GB
    EXPECT_LT(monitor.memoryUsage(), 16.0 * 1024 * 1024 * 1024);
}

TEST(SystemMonitorTest, CpuUsageInRange) {
    alice::SystemMonitor monitor;
    monitor.poll();
    EXPECT_GE(monitor.cpuUsage(), 0.0);
    EXPECT_LE(monitor.cpuUsage(), 100.0);
}
```

- [ ] **Step 2: Create SystemMonitor.h**

Create `src/desktop/src/core/system/SystemMonitor.h`:

```cpp
#pragma once

#include <QObject>
#include <QTimer>
#include <array>

namespace alice {

/**
 * Monitors system resource usage (CPU, GPU, memory) for the application.
 * Polls /proc on Linux. Exposed to QML for the SYSTEM panel.
 */
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

    /** Force a poll (used by tests). Normally called by internal timer. */
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
    double memoryUsage_ = 0.0;  // bytes

    // CPU tracking (previous tick values)
    long long prevCpuTotal_ = 0;
    long long prevCpuIdle_ = 0;
};

} // namespace alice
```

- [ ] **Step 3: Create SystemMonitor.cpp**

Create `src/desktop/src/core/system/SystemMonitor.cpp`:

```cpp
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

    // "cpu  user nice system idle iowait irq softirq steal"
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
                memoryUsage_ = parts[1].toDouble() * 1024.0;  // kB -> bytes
            break;
        }
    }
}

void SystemMonitor::readGpuUsage() {
    // Try NVIDIA first via nvidia-smi
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

    // Fallback: try DRM (Intel/AMD)
    QFile drmFile("/sys/class/drm/card0/device/gpu_busy_percent");
    if (drmFile.open(QIODevice::ReadOnly | QIODevice::Text)) {
        gpuUsage_ = QTextStream(&drmFile).readLine().trimmed().toDouble();
        return;
    }

    // No GPU info available
    gpuUsage_ = 0.0;
}

} // namespace alice
```

- [ ] **Step 4: Add to CMakeLists.txt**

In `add_library(alice_core STATIC ...)`, add:

```cmake
    src/core/system/SystemMonitor.cpp
```

In `add_executable(alice_tests ...)`, add:

```cmake
    tests/test_system_monitor.cpp
```

- [ ] **Step 5: Expose SystemMonitor in main.cpp**

In `src/desktop/src/main.cpp`, add include:

```cpp
#include "core/system/SystemMonitor.h"
```

After `alice::AppController controller;` and before the QML component load, add:

```cpp
    alice::SystemMonitor sysMonitor;
    engine.rootContext()->setContextProperty("sysMonitor", &sysMonitor);
```

- [ ] **Step 6: Build and run tests**

```bash
cd src/desktop/build && cmake .. && cmake --build . --target alice_tests && ./alice_tests --gtest_filter="SystemMonitor*"
```

Expected: All 3 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/desktop/src/core/system/ src/desktop/tests/test_system_monitor.cpp src/desktop/CMakeLists.txt src/desktop/src/main.cpp
git commit -m "feat(core): add SystemMonitor for CPU/GPU/MEM usage reporting"
```

---

## Task 4: Small Reusable Components

Build the small building-block components that larger views depend on.

**Files:**
- Create: `src/desktop/src/ui/qml/components/ModeToggle.qml`
- Create: `src/desktop/src/ui/qml/components/TelemetryGrid.qml`
- Create: `src/desktop/src/ui/qml/components/SystemMonitorPanel.qml`
- Create: `src/desktop/src/ui/qml/components/BottomStrip.qml`
- Create: `src/desktop/src/ui/qml/components/SettingsCard.qml`
- Modify: `src/desktop/CMakeLists.txt` (add QML_FILES entries)

- [ ] **Step 1: Create ModeToggle.qml**

Create `src/desktop/src/ui/qml/components/ModeToggle.qml`:

```qml
import QtQuick
import Alice.UI

Row {
    id: toggle
    property int currentMode: 0  // 0=OPS, 1=CFG
    signal modeChanged(int mode)

    spacing: 0

    Repeater {
        model: [
            { label: "OPS", mode: 0 },
            { label: "CFG", mode: 1 }
        ]

        Rectangle {
            required property var modelData
            required property int index

            width: 40
            height: 24
            color: toggle.currentMode === modelData.mode ? Theme.primary : "transparent"
            border.width: 1
            border.color: toggle.currentMode === modelData.mode ? Theme.primary : Theme.border

            // Square left edge on right segment, square right edge on left segment
            radius: 0

            Text {
                anchors.centerIn: parent
                text: modelData.label
                font.family: Theme.fontFamily
                font.pixelSize: Theme.fontSizeCaption
                font.weight: toggle.currentMode === modelData.mode ? Font.DemiBold : Font.Normal
                color: toggle.currentMode === modelData.mode ? "#ffffff" : Theme.textSecondary
            }

            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onClicked: toggle.modeChanged(modelData.mode)
            }
        }
    }
}
```

- [ ] **Step 2: Create TelemetryGrid.qml**

Create `src/desktop/src/ui/qml/components/TelemetryGrid.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

ColumnLayout {
    spacing: 4

    Label {
        text: "TELEMETRY"
        font.pixelSize: Theme.sectionFontSize
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }

    GridLayout {
        columns: 2
        columnSpacing: 10
        rowSpacing: 2

        Text { text: "Depth:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Confidence:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"; color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Motor:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: (alice ? alice.motorPosition : 0) + " / 4095"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Target:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Mode:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: ["Manual", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0] || "?"; color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "FPS:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: "30"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
    }
}
```

- [ ] **Step 3: Create SystemMonitorPanel.qml**

Create `src/desktop/src/ui/qml/components/SystemMonitorPanel.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

ColumnLayout {
    spacing: 4

    Label {
        text: "SYSTEM"
        font.pixelSize: Theme.sectionFontSize
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }

    // CPU
    RowLayout {
        Layout.fillWidth: true
        spacing: 8
        Text { text: "CPU"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10; Layout.preferredWidth: 32 }
        Rectangle {
            Layout.fillWidth: true; height: 4; radius: 2; color: Theme.surface
            Rectangle {
                width: parent.width * Math.min(1, (sysMonitor ? sysMonitor.cpuUsage : 0) / 100)
                height: parent.height; radius: 2
                color: (sysMonitor && sysMonitor.cpuUsage > 80) ? Theme.danger : Theme.success
            }
        }
        Text { text: Math.round(sysMonitor ? sysMonitor.cpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10; Layout.preferredWidth: 32; horizontalAlignment: Text.AlignRight }
    }

    // GPU
    RowLayout {
        Layout.fillWidth: true
        spacing: 8
        Text { text: "GPU"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10; Layout.preferredWidth: 32 }
        Rectangle {
            Layout.fillWidth: true; height: 4; radius: 2; color: Theme.surface
            Rectangle {
                width: parent.width * Math.min(1, (sysMonitor ? sysMonitor.gpuUsage : 0) / 100)
                height: parent.height; radius: 2
                color: (sysMonitor && sysMonitor.gpuUsage > 80) ? Theme.danger : Theme.warning
            }
        }
        Text { text: Math.round(sysMonitor ? sysMonitor.gpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10; Layout.preferredWidth: 32; horizontalAlignment: Text.AlignRight }
    }

    // MEM
    RowLayout {
        Layout.fillWidth: true
        spacing: 8
        Text { text: "MEM"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10; Layout.preferredWidth: 32 }
        Rectangle {
            Layout.fillWidth: true; height: 4; radius: 2; color: Theme.surface
            Rectangle {
                width: parent.width * Math.min(1, (sysMonitor ? sysMonitor.memoryUsage : 0) / (8.0 * 1024 * 1024 * 1024))
                height: parent.height; radius: 2; color: Theme.primary
            }
        }
        Text { text: sysMonitor ? sysMonitor.memoryFormatted : "0M"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10; Layout.preferredWidth: 32; horizontalAlignment: Text.AlignRight }
    }
}
```

- [ ] **Step 4: Create BottomStrip.qml**

Create `src/desktop/src/ui/qml/components/BottomStrip.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    height: 22
    color: Theme.surface

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 12
        anchors.rightMargin: 12
        spacing: 14

        Text { text: "Depth: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + "m" : "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text { text: "Conf: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"; color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text { text: "Motor: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: (alice ? alice.motorPosition : 0) + "/4095"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text { text: "Target: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text {
            text: alice && alice.activelyFocusing ? ["MF", "AF-S LOCKED", "AF-C LOCKED", "AF-F LOCKED"][alice.focusMode] : ["MF", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
            color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary
            font.family: Theme.fontFamilyMono; font.pixelSize: 9; font.weight: Font.DemiBold
        }

        Rectangle { width: 1; Layout.fillHeight: true; Layout.topMargin: 4; Layout.bottomMargin: 4; color: Theme.border }

        Text { text: "CPU " + Math.round(sysMonitor ? sysMonitor.cpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: "GPU " + Math.round(sysMonitor ? sysMonitor.gpuUsage : 0) + "%"; color: (sysMonitor && sysMonitor.gpuUsage > 80) ? Theme.warning : Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: "MEM " + (sysMonitor ? sysMonitor.memoryFormatted : "0M"); color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Item { Layout.fillWidth: true }

        Text { text: alice ? alice.logMessages[alice.logMessages.length - 1] || "" : ""; color: Theme.textDisabled; font.family: Theme.fontFamilyMono; font.pixelSize: 9; elide: Text.ElideRight; Layout.maximumWidth: 300 }
    }
}
```

- [ ] **Step 5: Create SettingsCard.qml**

Create `src/desktop/src/ui/qml/components/SettingsCard.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    property string title: ""
    property int columnSpan: 1
    default property alias content: contentColumn.children

    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    implicitHeight: mainColumn.implicitHeight + 2 * 12

    Layout.columnSpan: columnSpan
    Layout.fillWidth: true

    ColumnLayout {
        id: mainColumn
        anchors.fill: parent
        anchors.margins: 12
        spacing: 10

        Text {
            visible: title !== ""
            text: title.toUpperCase()
            font.family: Theme.fontFamily
            font.pixelSize: 10
            font.weight: Font.DemiBold
            font.letterSpacing: 1.0
            color: Theme.textPrimary
        }
        Rectangle {
            visible: title !== ""
            Layout.fillWidth: true
            height: 1
            color: Theme.border
        }
        ColumnLayout {
            id: contentColumn
            Layout.fillWidth: true
            spacing: 10
        }
    }
}
```

- [ ] **Step 6: Add all new QML files to CMakeLists.txt**

In the `QML_FILES` section of `qt_add_qml_module(alice_ui ...)`, add:

```cmake
        src/ui/qml/components/ModeToggle.qml
        src/ui/qml/components/TelemetryGrid.qml
        src/ui/qml/components/SystemMonitorPanel.qml
        src/ui/qml/components/BottomStrip.qml
        src/ui/qml/components/SettingsCard.qml
```

- [ ] **Step 7: Build to check for QML errors**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

Expected: Build succeeds.

- [ ] **Step 8: Commit**

```bash
git add src/desktop/src/ui/qml/components/ModeToggle.qml src/desktop/src/ui/qml/components/TelemetryGrid.qml src/desktop/src/ui/qml/components/SystemMonitorPanel.qml src/desktop/src/ui/qml/components/BottomStrip.qml src/desktop/src/ui/qml/components/SettingsCard.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): add ModeToggle, TelemetryGrid, SystemMonitorPanel, BottomStrip, SettingsCard components"
```

---

## Task 5: Interactive StatusBadge + Popover Components

**Files:**
- Modify: `src/desktop/src/ui/qml/components/StatusBadge.qml`
- Create: `src/desktop/src/ui/qml/components/BadgePopover.qml`
- Create: `src/desktop/src/ui/qml/components/SyncPopover.qml`
- Modify: `src/desktop/CMakeLists.txt`

- [ ] **Step 1: Rewrite StatusBadge.qml with click handler**

Replace the contents of `src/desktop/src/ui/qml/components/StatusBadge.qml`:

```qml
import QtQuick
import Alice.UI
import QtQuick.Layouts

Item {
    id: badge
    property string label: ""
    property bool connected: false
    property string deviceName: ""
    property string deviceInfo: ""
    property string uptime: ""
    property bool isSync: false
    signal clicked()

    width: row.implicitWidth + 16
    height: 28

    Rectangle {
        id: bg
        anchors.fill: parent
        radius: Theme.radiusSm
        color: connected ? Theme.successMuted : Theme.elevated
        border.color: connected ? Theme.success : Theme.border
        border.width: 1

        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Theme.easingEnter } }
        Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Theme.easingEnter } }
    }

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: 6

        Rectangle {
            width: 6; height: 6; radius: 3
            color: connected ? Theme.success : Theme.danger

            SequentialAnimation on opacity {
                running: !connected && badge.isSync
                loops: Animation.Infinite
                NumberAnimation { to: 0.4; duration: 800 }
                NumberAnimation { to: 1.0; duration: 800 }
            }
        }

        Text {
            text: badge.label
            font.family: Theme.fontFamily
            font.pixelSize: Theme.fontSizeCaption
            color: connected ? Theme.textPrimary : Theme.textSecondary
        }
    }

    MouseArea {
        anchors.fill: parent
        cursorShape: Qt.PointingHandCursor
        hoverEnabled: true
        onClicked: badge.clicked()
        onEntered: bg.color = connected ? Qt.lighter(Theme.successMuted, 1.15) : Theme.surfaceHover
        onExited: bg.color = connected ? Theme.successMuted : Theme.elevated
    }
}
```

- [ ] **Step 2: Create BadgePopover.qml**

Create `src/desktop/src/ui/qml/components/BadgePopover.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    id: popover
    property string title: ""
    property bool connected: false
    property string statusText: connected ? "Connected" : "Offline"
    property string deviceName: ""
    property string deviceAddress: ""
    property string uptime: ""

    signal reconnectClicked()
    signal disconnectClicked()
    signal restartClicked()

    visible: false
    width: Theme.popoverWidth
    implicitHeight: col.implicitHeight + 20
    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    z: 100

    ColumnLayout {
        id: col
        anchors.fill: parent
        anchors.margins: 10
        spacing: 10

        // Header
        RowLayout {
            Layout.fillWidth: true
            Text { text: popover.title; font.family: Theme.fontFamily; font.pixelSize: 12; font.weight: Font.DemiBold; color: Theme.textPrimary; Layout.fillWidth: true }
            Rectangle {
                width: statusRow.implicitWidth + 10; height: 18; radius: Theme.radiusSm
                color: connected ? Theme.successMuted : Theme.dangerMuted
                border.width: 1; border.color: connected ? Theme.success : Qt.rgba(0.86, 0.22, 0.22, 0.4)
                RowLayout {
                    id: statusRow; anchors.centerIn: parent; spacing: 4
                    Rectangle { width: 5; height: 5; radius: 3; color: connected ? Theme.success : Theme.danger }
                    Text { text: popover.statusText; font.family: Theme.fontFamily; font.pixelSize: 9; font.weight: Font.DemiBold; color: connected ? Theme.success : Theme.dangerText }
                }
            }
        }

        // Data grid
        GridLayout {
            Layout.fillWidth: true
            columns: 2; columnSpacing: 10; rowSpacing: 4

            Text { text: "Device"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: popover.deviceName || "—"; color: connected ? Theme.textPrimary : Theme.textDisabled; font.pixelSize: 10 }

            Text { text: connected ? "Address" : "Last seen"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: connected ? popover.deviceAddress : popover.uptime; color: connected ? Theme.primary : Theme.textDisabled; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

            Text { visible: connected; text: "Uptime"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { visible: connected; text: popover.uptime; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
        }

        // Actions
        RowLayout {
            Layout.fillWidth: true; spacing: 4
            visible: connected
            Rectangle {
                Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
                color: Theme.elevated; border.width: 1; border.color: Theme.border
                Text { anchors.centerIn: parent; text: "Restart"; font.pixelSize: 10; color: Theme.textPrimary }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.restartClicked() }
            }
            Rectangle {
                Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
                color: Theme.dangerMuted; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
                Text { anchors.centerIn: parent; text: "Disconnect"; font.pixelSize: 10; color: Theme.dangerText }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.disconnectClicked() }
            }
        }

        Rectangle {
            Layout.fillWidth: true; height: 22; radius: Theme.radiusSm; visible: !connected
            color: Theme.primaryMuted; border.width: 1; border.color: Qt.rgba(0.17, 0.58, 0.84, 0.4)
            Text { anchors.centerIn: parent; text: "Reconnect"; font.pixelSize: 10; color: Theme.primaryHover }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.reconnectClicked() }
        }
    }

    // Click outside to close
    function toggle() { visible = !visible }
}
```

- [ ] **Step 3: Create SyncPopover.qml**

Create `src/desktop/src/ui/qml/components/SyncPopover.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI
import Alice.Renderers 1.0

Rectangle {
    id: popover
    visible: false
    width: Theme.syncPopoverWidth
    implicitHeight: col.implicitHeight + 20
    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    z: 100

    property bool connected: alice ? alice.syncClientConnected : false

    // Auto-start server when popover becomes visible
    onVisibleChanged: {
        if (visible && alice && !alice.syncServerRunning) {
            alice.startSyncServer()
        }
    }

    ColumnLayout {
        id: col
        anchors.fill: parent
        anchors.margins: 10
        spacing: 8

        // Connected state: header + details
        RowLayout {
            visible: connected
            Layout.fillWidth: true
            Text { text: "Sync"; font.family: Theme.fontFamily; font.pixelSize: 12; font.weight: Font.DemiBold; color: Theme.textPrimary; Layout.fillWidth: true }
            Rectangle {
                width: linkedRow.implicitWidth + 10; height: 18; radius: Theme.radiusSm
                color: Theme.successMuted; border.width: 1; border.color: Theme.success
                RowLayout { id: linkedRow; anchors.centerIn: parent; spacing: 4
                    Rectangle { width: 5; height: 5; radius: 3; color: Theme.success }
                    Text { text: "Linked"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.success }
                }
            }
        }

        GridLayout {
            visible: connected
            columns: 2; columnSpacing: 10; rowSpacing: 4; Layout.fillWidth: true
            Text { text: "Client"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: "Android"; color: Theme.textPrimary; font.pixelSize: 10 }
            Text { text: "IP"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: alice ? alice.syncQrPayload : ""; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
        }

        Rectangle {
            visible: connected; Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
            color: Theme.dangerMuted; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
            Text { anchors.centerIn: parent; text: "Disconnect"; font.pixelSize: 10; color: Theme.dangerText }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: { if (alice) alice.stopSyncServer() } }
        }

        // Disconnected state: QR code
        VideoRenderer {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            Layout.alignment: Qt.AlignHCenter
            width: 120; height: 120
            source: alice ? alice.qrCodeImage : null

            Rectangle {
                anchors.fill: parent; color: "#ffffff"; z: -1; radius: Theme.radiusSm
            }
        }

        Text {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            text: alice ? alice.syncQrPayload : ""
            font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary
            Layout.alignment: Qt.AlignHCenter
        }

        Text {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            text: "Scan with Alice Android"
            font.pixelSize: 9; color: Theme.textDisabled
            Layout.alignment: Qt.AlignHCenter
        }

        // Waiting indicator
        Rectangle {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
            color: Theme.warningMuted; border.width: 1; border.color: Qt.rgba(0.85, 0.51, 0.17, 0.3)
            Row {
                anchors.centerIn: parent; spacing: 5
                Rectangle {
                    width: 5; height: 5; radius: 3; color: Theme.warning
                    SequentialAnimation on opacity { loops: Animation.Infinite
                        NumberAnimation { to: 0.4; duration: 800 }
                        NumberAnimation { to: 1.0; duration: 800 }
                    }
                }
                Text { text: "Waiting..."; font.pixelSize: 9; color: Theme.warning }
            }
        }
    }

    function toggle() { visible = !visible }
}
```

- [ ] **Step 4: Add new QML files to CMakeLists.txt**

Add to `QML_FILES`:

```cmake
        src/ui/qml/components/BadgePopover.qml
        src/ui/qml/components/SyncPopover.qml
```

- [ ] **Step 5: Build**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

- [ ] **Step 6: Commit**

```bash
git add src/desktop/src/ui/qml/components/StatusBadge.qml src/desktop/src/ui/qml/components/BadgePopover.qml src/desktop/src/ui/qml/components/SyncPopover.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): add interactive StatusBadge with BadgePopover and SyncPopover"
```

---

## Task 6: ZoomToolbar + MiniMap Components

**Files:**
- Create: `src/desktop/src/ui/qml/components/ZoomToolbar.qml`
- Create: `src/desktop/src/ui/qml/components/MiniMap.qml`
- Modify: `src/desktop/CMakeLists.txt`

- [ ] **Step 1: Create ZoomToolbar.qml**

Create `src/desktop/src/ui/qml/components/ZoomToolbar.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    id: toolbar
    property real zoomLevel: 1.0
    signal zoomIn()
    signal zoomOut()
    signal zoomTo(real level)
    signal fitRequested()

    color: Qt.rgba(0.106, 0.125, 0.145, 0.92)
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    width: row.implicitWidth + 12
    height: 28

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: 2

        // Zoom out
        Image {
            source: "qrc:/qt/qml/Alice/UI/assets/icons/zoom_out.svg"
            width: 14; height: 14; sourceSize: Qt.size(14, 14)
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.zoomOut() }
        }

        // Zoom slider
        Item {
            width: 70; height: 14

            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width; height: 3; radius: 2; color: Theme.border

                Rectangle {
                    width: parent.width * Math.max(0, (toolbar.zoomLevel - Theme.zoomMin) / (Theme.zoomMax - Theme.zoomMin))
                    height: parent.height; radius: 2; color: Theme.primary
                }
            }

            Rectangle {
                id: handle
                x: parent.width * Math.max(0, (toolbar.zoomLevel - Theme.zoomMin) / (Theme.zoomMax - Theme.zoomMin)) - 5
                anchors.verticalCenter: parent.verticalCenter
                width: 10; height: 10; radius: 5
                color: Theme.textPrimary; border.width: 1; border.color: Theme.border
            }

            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onPositionChanged: (mouse) => {
                    let ratio = Math.max(0, Math.min(1, mouse.x / parent.width))
                    toolbar.zoomTo(Theme.zoomMin + ratio * (Theme.zoomMax - Theme.zoomMin))
                }
                onClicked: (mouse) => {
                    let ratio = Math.max(0, Math.min(1, mouse.x / parent.width))
                    toolbar.zoomTo(Theme.zoomMin + ratio * (Theme.zoomMax - Theme.zoomMin))
                }
            }
        }

        // Zoom in
        Image {
            source: "qrc:/qt/qml/Alice/UI/assets/icons/zoom_in.svg"
            width: 14; height: 14; sourceSize: Qt.size(14, 14)
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.zoomIn() }
        }

        Rectangle { width: 1; height: 12; color: Theme.border }

        // Percentage
        Text {
            text: Math.round(toolbar.zoomLevel * 100) + "%"
            font.family: Theme.fontFamilyMono; font.pixelSize: 9
            color: Theme.textSecondary
            Layout.preferredWidth: 32; horizontalAlignment: Text.AlignHCenter
        }

        Rectangle { width: 1; height: 12; color: Theme.border }

        // FIT button
        Text {
            text: "FIT"
            font.family: Theme.fontFamily; font.pixelSize: 9; color: Theme.textPrimary
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.fitRequested() }
        }
    }
}
```

- [ ] **Step 2: Create MiniMap.qml**

Create `src/desktop/src/ui/qml/components/MiniMap.qml`:

```qml
import QtQuick
import Alice.UI

Rectangle {
    id: minimap
    property real zoomLevel: 1.0
    property real panX: 0.0  // normalized 0-1
    property real panY: 0.0

    visible: zoomLevel > 1.0
    width: 60; height: 40
    color: Qt.rgba(0.106, 0.125, 0.145, 0.85)
    border.width: 1; border.color: Theme.border; radius: Theme.radiusSm

    // Full frame outline
    Rectangle {
        anchors.fill: parent; anchors.margins: 2
        color: "transparent"; border.width: 1; border.color: Theme.border; radius: 1

        // Viewport indicator
        Rectangle {
            property real viewW: Math.min(1.0, 1.0 / minimap.zoomLevel)
            property real viewH: Math.min(1.0, 1.0 / minimap.zoomLevel)

            x: parent.width * panX * (1 - viewW)
            y: parent.height * panY * (1 - viewH)
            width: parent.width * viewW
            height: parent.height * viewH
            color: Qt.rgba(0.17, 0.58, 0.84, 0.1)
            border.width: 1; border.color: Theme.primary; radius: 1
        }
    }
}
```

- [ ] **Step 3: Add to CMakeLists.txt QML_FILES**

```cmake
        src/ui/qml/components/ZoomToolbar.qml
        src/ui/qml/components/MiniMap.qml
```

- [ ] **Step 4: Build**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add src/desktop/src/ui/qml/components/ZoomToolbar.qml src/desktop/src/ui/qml/components/MiniMap.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): add ZoomToolbar and MiniMap components for camera zoom/pan"
```

---

## Task 7: OpsView — Main Operational Layout

**Files:**
- Create: `src/desktop/src/ui/qml/OpsView.qml`
- Modify: `src/desktop/CMakeLists.txt`

- [ ] **Step 1: Create OpsView.qml**

Create `src/desktop/src/ui/qml/OpsView.qml` — the full operational view with responsive 2/3-column layout. This is the largest single file. It uses all the components built in previous tasks.

```qml
import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: opsView

    // Responsive modes: compact (<1280), standard (1280-1599), wide (1600+)
    readonly property bool compactMode: width < Theme.breakpointStandard
    readonly property bool wideMode: width >= Theme.breakpointWide

    // Camera zoom/pan state
    property real zoomLevel: 1.0
    property real panX: 0.5
    property real panY: 0.5

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            // LEFT sidebar (wide mode only — controls move here)
            ColumnLayout {
                visible: wideMode
                Layout.preferredWidth: Theme.sidebarWide
                Layout.fillHeight: true
                spacing: 0

                // Motor
                Rectangle {
                    Layout.fillWidth: true; Layout.preferredHeight: motorCol.implicitHeight + 20
                    color: Theme.bg; border.width: 0

                    ColumnLayout {
                        id: motorCol
                        anchors.fill: parent; anchors.margins: 10; spacing: 8

                        SectionHeader { text: "MOTOR POSITION" }
                        MotorSlider {
                            Layout.fillWidth: true
                            motorPos: alice ? alice.motorPosition : 0
                            enabled: alice ? alice.motorConnected : false
                            onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                        }
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                // Depth feed
                Item {
                    Layout.fillWidth: true; Layout.fillHeight: true

                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 10; spacing: 6

                        RowLayout {
                            SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                            Text {
                                text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m " + Math.round(alice.depthConfidence * 100) + "%" : "—"
                                font.family: Theme.fontFamilyMono; font.pixelSize: 11; font.weight: Font.DemiBold
                                color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                            }
                        }

                        Item {
                            Layout.fillWidth: true; Layout.fillHeight: true

                            Rectangle {
                                anchors.fill: parent; color: Theme.well; radius: Theme.radiusSm

                                VideoRenderer {
                                    anchors.centerIn: parent
                                    width: Math.min(parent.width, parent.height * 4 / 3)
                                    height: width * 3 / 4
                                    source: alice ? alice.colorFrame : null
                                    visible: alice ? alice.realSenseConnected : false
                                }

                                // Crosshair
                                Item {
                                    property real normX: alice ? alice.measureX : 0.5
                                    property real normY: alice ? alice.measureY : 0.5
                                    x: normX * parent.width - 8; y: normY * parent.height - 8
                                    width: 16; height: 16; visible: alice ? alice.realSenseConnected : false
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 16; height: 1; color: "#fff" }
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 16; color: "#fff" }
                                }

                                MouseArea {
                                    anchors.fill: parent; cursorShape: Qt.CrossCursor
                                    property bool dragging: false
                                    onPressed: (mouse) => { dragging = true; updatePos(mouse.x, mouse.y) }
                                    onPositionChanged: (mouse) => { if (dragging) updatePos(mouse.x, mouse.y) }
                                    onReleased: dragging = false
                                    function updatePos(mx, my) {
                                        if (!alice) return
                                        alice.setMeasurementPosition(Math.max(0, Math.min(1, mx / width)), Math.max(0, Math.min(1, my / height)))
                                    }
                                }
                            }
                        }
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                // Calibration quick access
                Rectangle {
                    Layout.fillWidth: true; Layout.preferredHeight: calibCol.implicitHeight + 20
                    color: Theme.bg

                    ColumnLayout {
                        id: calibCol
                        anchors.fill: parent; anchors.margins: 10; spacing: 6

                        SectionHeader { text: "CALIBRATION" }
                        Label { visible: alice ? alice.hasMapping : false; text: alice ? ("Active: " + alice.mappingName) : ""; font.pixelSize: 10; color: Theme.success }
                        ComboBox {
                            Layout.fillWidth: true; model: ["Select Preset...", "Linear", "Logarithmic", "Portrait", "Landscape", "Macro"]
                            Material.accent: Theme.primary
                            onActivated: (index) => { if (alice && index > 0) alice.loadPreset(index - 1) }
                        }
                    }
                }
            }

            Rectangle { visible: wideMode; Layout.fillHeight: true; width: 1; color: Theme.border }

            // CENTER: Camera feed (always present, flex)
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true

                Rectangle {
                    anchors.fill: parent; color: Theme.well

                    VideoRenderer {
                        id: cameraFeed
                        anchors.fill: parent
                        source: alice ? alice.captureFrame : null
                        visible: alice ? alice.captureCardConnected : false
                    }

                    // Placeholder
                    Label {
                        anchors.centerIn: parent; text: "No camera"; font.pixelSize: 18; color: Theme.textPlaceholder
                        visible: alice ? !alice.captureCardConnected : true
                    }

                    // Face overlay
                    FaceOverlay {
                        anchors.fill: parent
                        faces: alice ? alice.trackedFaces() : []
                        showCrosshair: false
                        visible: alice ? alice.focusMode === 3 : false
                    }

                    // AF status overlay (top-left)
                    Rectangle {
                        anchors.top: parent.top; anchors.left: parent.left; anchors.margins: 8
                        width: afLabel.implicitWidth + 16; height: 20; radius: Theme.radiusSm
                        color: alice && alice.activelyFocusing ? Qt.rgba(0.055, 0.231, 0.173, 0.9) : Qt.rgba(0.106, 0.125, 0.145, 0.9)
                        border.width: 1; border.color: alice && alice.activelyFocusing ? Theme.success : Theme.border
                        visible: alice ? alice.focusMode > 0 : false
                        Text {
                            id: afLabel; anchors.centerIn: parent
                            text: alice && alice.activelyFocusing ? ["", "AF-S LOCKED", "AF-C LOCKED", "AF-F LOCKED"][alice.focusMode] : ["", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
                            font.pixelSize: 10; font.weight: Font.DemiBold; color: alice && alice.activelyFocusing ? Theme.success : Theme.textSecondary
                        }
                    }

                    // Histogram (top-right)
                    HistogramRenderer {
                        anchors.top: parent.top; anchors.right: parent.right; anchors.margins: 8
                        width: 130; height: 75
                        source: alice ? (alice.captureCardConnected ? alice.captureFrame : alice.colorFrame) : null
                        visible: source !== null; opacity: 0.92
                    }

                    // Zoom toolbar (bottom-left)
                    ZoomToolbar {
                        anchors.bottom: parent.bottom; anchors.left: parent.left; anchors.margins: 8
                        zoomLevel: opsView.zoomLevel
                        onZoomIn: opsView.zoomLevel = Math.min(Theme.zoomMax, opsView.zoomLevel + Theme.zoomStep)
                        onZoomOut: opsView.zoomLevel = Math.max(Theme.zoomMin, opsView.zoomLevel - Theme.zoomStep)
                        onZoomTo: (level) => { opsView.zoomLevel = Math.max(Theme.zoomMin, Math.min(Theme.zoomMax, level)) }
                        onFitRequested: opsView.zoomLevel = 1.0
                    }

                    // MiniMap (bottom-right)
                    MiniMap {
                        anchors.bottom: parent.bottom; anchors.right: parent.right; anchors.margins: 8
                        zoomLevel: opsView.zoomLevel; panX: opsView.panX; panY: opsView.panY
                    }

                    // Tap-to-focus / zoom scroll
                    MouseArea {
                        anchors.fill: parent; z: -1
                        onClicked: (mouse) => { if (!alice || opsView.zoomLevel > 1.0) return; alice.processTap(mouse.x / width, mouse.y / height) }
                        onWheel: (wheel) => {
                            if (wheel.angleDelta.y > 0) opsView.zoomLevel = Math.min(Theme.zoomMax, opsView.zoomLevel + 0.1)
                            else opsView.zoomLevel = Math.max(Theme.zoomMin, opsView.zoomLevel - 0.1)
                        }
                    }
                }
            }

            Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

            // RIGHT sidebar
            ColumnLayout {
                Layout.preferredWidth: compactMode ? Theme.sidebarNarrow : (wideMode ? Theme.telemetryColumnWidth : Theme.sidebarStandard)
                Layout.fillHeight: true
                spacing: 0

                // Compact: motor + numeric depth only
                // Standard: motor + depth feed + calibration
                // Wide: telemetry + system + log
                Loader {
                    Layout.fillWidth: true; Layout.fillHeight: true
                    sourceComponent: compactMode ? compactRightComponent : (wideMode ? wideRightComponent : standardRightComponent)
                }
            }
        }

        // Bottom strip
        BottomStrip { Layout.fillWidth: true }
    }

    // Compact mode right sidebar (1024-1279px): motor + numeric depth only
    Component {
        id: compactRightComponent

        ColumnLayout {
            spacing: 0

            // Motor
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: cmpMotorCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: cmpMotorCol; anchors.fill: parent; anchors.margins: 10; spacing: 8
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth numeric readout (no video feed)
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: cmpDepthCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: cmpDepthCol; anchors.fill: parent; anchors.margins: 10; spacing: 8
                    SectionHeader { text: "DEPTH" }
                    Text {
                        text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + " m" : "—"
                        font.family: Theme.fontFamilyMono; font.pixelSize: 24; font.weight: Font.Bold
                        color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                        Layout.alignment: Qt.AlignHCenter
                    }
                    Text {
                        text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "% confidence"
                        font.family: Theme.fontFamilyMono; font.pixelSize: 11; color: Theme.textSecondary
                        Layout.alignment: Qt.AlignHCenter
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Focus mode indicator
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: 40; color: Theme.bg
                Text {
                    anchors.centerIn: parent
                    text: alice && alice.activelyFocusing ? ["MF", "AF-S LOCKED", "AF-C LOCKED", "AF-F LOCKED"][alice.focusMode] : ["MF", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
                    font.family: Theme.fontFamilyMono; font.pixelSize: 13; font.weight: Font.DemiBold
                    color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary
                }
            }

            Item { Layout.fillHeight: true }
        }
    }

    // Standard mode right sidebar content
    Component {
        id: standardRightComponent

        ColumnLayout {
            spacing: 0

            // Motor
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: stdMotorCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: stdMotorCol; anchors.fill: parent; anchors.margins: 10; spacing: 8
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth feed
            Item {
                Layout.fillWidth: true; Layout.fillHeight: true
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: 10; spacing: 6
                    RowLayout {
                        SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                        Text {
                            text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m " + Math.round(alice.depthConfidence * 100) + "%" : "—"
                            font.family: Theme.fontFamilyMono; font.pixelSize: 11; font.weight: Font.DemiBold
                            color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                        }
                    }
                    Item {
                        Layout.fillWidth: true; Layout.fillHeight: true
                        Rectangle {
                            anchors.fill: parent; color: Theme.well; radius: Theme.radiusSm
                            VideoRenderer {
                                anchors.centerIn: parent; width: Math.min(parent.width, parent.height * 4 / 3); height: width * 3 / 4
                                source: alice ? alice.colorFrame : null; visible: alice ? alice.realSenseConnected : false
                            }
                            Item {
                                property real normX: alice ? alice.measureX : 0.5; property real normY: alice ? alice.measureY : 0.5
                                x: normX * parent.width - 8; y: normY * parent.height - 8; width: 16; height: 16; visible: alice ? alice.realSenseConnected : false
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 16; height: 1; color: "#fff" }
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 16; color: "#fff" }
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.CrossCursor; property bool dragging: false
                                onPressed: (mouse) => { dragging = true; updatePos(mouse.x, mouse.y) }
                                onPositionChanged: (mouse) => { if (dragging) updatePos(mouse.x, mouse.y) }
                                onReleased: dragging = false
                                function updatePos(mx, my) { if (alice) alice.setMeasurementPosition(Math.max(0, Math.min(1, mx / width)), Math.max(0, Math.min(1, my / height))) }
                            }
                        }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Calibration
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: stdCalibCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: stdCalibCol; anchors.fill: parent; anchors.margins: 10; spacing: 6
                    SectionHeader { text: "CALIBRATION" }
                    Label { visible: alice ? alice.hasMapping : false; text: alice ? ("Active: " + alice.mappingName) : ""; font.pixelSize: 10; color: Theme.success }
                    ComboBox { Layout.fillWidth: true; model: ["Select Preset...", "Linear", "Logarithmic", "Portrait", "Landscape", "Macro"]; Material.accent: Theme.primary; onActivated: (index) => { if (alice && index > 0) alice.loadPreset(index - 1) } }
                }
            }
        }
    }

    // Wide mode right sidebar content
    Component {
        id: wideRightComponent

        ColumnLayout {
            spacing: 0

            // Telemetry
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: telGrid.implicitHeight + 20; color: Theme.bg
                TelemetryGrid { id: telGrid; anchors.fill: parent; anchors.margins: 10 }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // System
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: sysPanel.implicitHeight + 20; color: Theme.bg
                SystemMonitorPanel { id: sysPanel; anchors.fill: parent; anchors.margins: 10 }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Log
            LogDisplay { Layout.fillWidth: true; Layout.fillHeight: true; messages: alice ? alice.logMessages : [] }
        }
    }
}
```

- [ ] **Step 2: Add to CMakeLists.txt QML_FILES**

```cmake
        src/ui/qml/OpsView.qml
```

- [ ] **Step 3: Build**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add src/desktop/src/ui/qml/OpsView.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): add OpsView with responsive 2/3-column layout"
```

---

## Task 8: CfgView + Revised CalibrationView

**Files:**
- Create: `src/desktop/src/ui/qml/CfgView.qml`
- Create: `src/desktop/src/ui/qml/components/CalibrationTable.qml`
- Create: `src/desktop/src/ui/qml/components/CalibrationGraph.qml`
- Rewrite: `src/desktop/src/ui/qml/CalibrationView.qml`
- Modify: `src/desktop/CMakeLists.txt`

This task creates the CFG mode container and revamps calibration. Due to the size of CalibrationGraph.qml (Canvas-based plotting with labeled axes, curves, tooltips), this is a substantial task. The implementing agent should write each file one at a time, building and verifying after each.

- [ ] **Step 1: Create CfgView.qml**

Create `src/desktop/src/ui/qml/CfgView.qml`:

```qml
import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.UI

Item {
    id: cfgView

    property int currentTab: 0  // 0=Calibration, 1=Settings, 2=Connection

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Sub-tab bar
        Rectangle {
            Layout.fillWidth: true
            height: 32
            color: Theme.surface

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 12
                spacing: 0

                Repeater {
                    model: ["Calibration", "Settings", "Connection"]

                    Item {
                        required property string modelData
                        required property int index

                        Layout.preferredWidth: implicitWidth
                        implicitWidth: tabLabel.implicitWidth + 32
                        Layout.fillHeight: true

                        Text {
                            id: tabLabel
                            anchors.centerIn: parent
                            text: modelData
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.fontSizeCaption
                            font.weight: currentTab === index ? Font.DemiBold : Font.Normal
                            color: currentTab === index ? Theme.textPrimary : Theme.textSecondary
                        }

                        Rectangle {
                            anchors.bottom: parent.bottom
                            width: parent.width; height: 2
                            color: currentTab === index ? Theme.primary : "transparent"
                        }

                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.PointingHandCursor
                            onClicked: currentTab = index
                        }
                    }
                }

                Item { Layout.fillWidth: true }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Content
        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: currentTab

            CalibrationView {}
            SettingsView {}
            ConnectionView {}
        }
    }
}
```

- [ ] **Step 2: Create CalibrationTable.qml**

Create `src/desktop/src/ui/qml/components/CalibrationTable.qml`:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    id: table
    property var points: []
    signal pointRemoved(int index)
    signal exportRequested()
    signal clearRequested()

    color: Theme.bg

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Header
        Rectangle {
            Layout.fillWidth: true; height: 28; color: Theme.bg
            RowLayout {
                anchors.fill: parent; anchors.leftMargin: 8; anchors.rightMargin: 8; spacing: 4
                Text { text: "RECORDED POINTS"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; font.letterSpacing: Theme.sectionLetterSpacing; color: Theme.textSecondary; Layout.fillWidth: true }
                Text { text: points.length + " points"; font.pixelSize: 10; color: points.length >= 3 ? Theme.success : Theme.warning }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Column headers
        Rectangle {
            Layout.fillWidth: true; height: 22; color: Theme.surface
            RowLayout {
                anchors.fill: parent; anchors.leftMargin: 8; anchors.rightMargin: 8; spacing: 4
                Text { text: "#"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.preferredWidth: 20 }
                Text { text: "Depth"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.fillWidth: true }
                Text { text: "Motor"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.fillWidth: true }
                Text { text: "Conf"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.preferredWidth: 44 }
                Item { Layout.preferredWidth: 20 }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Rows
        ListView {
            Layout.fillWidth: true; Layout.fillHeight: true; clip: true; model: points
            delegate: Rectangle {
                required property var modelData
                required property int index
                width: table.width; height: 26
                color: index % 2 === 0 ? "transparent" : Qt.rgba(1, 1, 1, 0.015)

                RowLayout {
                    anchors.fill: parent; anchors.leftMargin: 8; anchors.rightMargin: 8; spacing: 4
                    Text { text: (index + 1).toString(); font.pixelSize: 10; color: Theme.textDisabled; Layout.preferredWidth: 20 }
                    Text { text: modelData.depth.toFixed(2) + " m"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.textPrimary; Layout.fillWidth: true }
                    Text { text: modelData.motorPosition.toString(); font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary; Layout.fillWidth: true }
                    // Confidence bar
                    Row {
                        Layout.preferredWidth: 44; spacing: 3
                        Rectangle {
                            width: 24; height: 3; radius: 2; color: Theme.surface; anchors.verticalCenter: parent.verticalCenter
                            Rectangle { width: parent.width * modelData.confidence; height: parent.height; radius: 2; color: modelData.confidence > 0.7 ? Theme.success : Theme.warning }
                        }
                        Text { text: Math.round(modelData.confidence * 100).toString(); font.family: Theme.fontFamilyMono; font.pixelSize: 9; color: Theme.textSecondary }
                    }
                    Text { text: "\u00D7"; font.pixelSize: 12; color: Theme.textDisabled; Layout.preferredWidth: 20; horizontalAlignment: Text.AlignHCenter
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: table.pointRemoved(index) }
                    }
                }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Footer
        RowLayout {
            Layout.fillWidth: true; Layout.margins: 8; spacing: 4
            Rectangle {
                Layout.fillWidth: true; height: 24; radius: Theme.radiusSm; color: Theme.primary; opacity: points.length >= 3 ? 1.0 : 0.4
                Text { anchors.centerIn: parent; text: "Export Mapping"; font.pixelSize: 10; font.weight: Font.DemiBold; color: "#fff" }
                MouseArea { anchors.fill: parent; enabled: points.length >= 3; cursorShape: Qt.PointingHandCursor; onClicked: table.exportRequested() }
            }
            Rectangle {
                width: 60; height: 24; radius: Theme.radiusSm; color: "transparent"; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
                Text { anchors.centerIn: parent; text: "Clear"; font.pixelSize: 10; color: Theme.dangerText }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: table.clearRequested() }
            }
        }
    }
}
```

- [ ] **Step 3: Create CalibrationGraph.qml**

Create `src/desktop/src/ui/qml/components/CalibrationGraph.qml`. This uses Canvas for the graph plotting:

```qml
import QtQuick
import QtQuick.Layouts
import Alice.UI

Item {
    id: graph
    property var points: []
    property int currentMotorPos: 0
    property string fitType: "Cubic Spline"

    ColumnLayout {
        anchors.fill: parent
        spacing: 8

        // Header with legend
        RowLayout {
            Layout.fillWidth: true
            Text { text: "CALIBRATION CURVE"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; font.letterSpacing: Theme.sectionLetterSpacing; color: Theme.textSecondary; Layout.fillWidth: true }
            Row {
                spacing: 12
                Row { spacing: 3; Rectangle { width: 8; height: 2; color: Theme.primary; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Interpolated"; font.pixelSize: 9; color: Theme.textSecondary } }
                Row { spacing: 3; Rectangle { width: 6; height: 6; color: Theme.primaryHover; radius: 3; anchors.verticalCenter: parent.verticalCenter } Text { text: "Sampled"; font.pixelSize: 9; color: Theme.textSecondary } }
                Row { spacing: 3; Rectangle { width: 8; height: 2; color: Theme.warning; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Current"; font.pixelSize: 9; color: Theme.textSecondary } }
            }
        }

        // Graph canvas
        Canvas {
            id: canvas
            Layout.fillWidth: true; Layout.fillHeight: true

            property real marginLeft: 40
            property real marginBottom: 24
            property real marginTop: 8
            property real marginRight: 8
            property real maxDepth: 5.0
            property int maxMotor: 4095

            onPaint: {
                var ctx = getContext("2d")
                var w = width; var h = height
                var gx = marginLeft; var gy = marginTop
                var gw = w - marginLeft - marginRight; var gh = h - marginTop - marginBottom
                ctx.clearRect(0, 0, w, h)
                ctx.font = "8px monospace"

                // Background
                ctx.fillStyle = Theme.well
                ctx.fillRect(gx, gy, gw, gh)

                // Grid
                ctx.strokeStyle = Theme.border; ctx.lineWidth = 0.5
                for (var i = 0; i <= 4; i++) {
                    var yy = gy + (i / 4) * gh
                    ctx.beginPath(); ctx.moveTo(gx, yy); ctx.lineTo(gx + gw, yy); ctx.stroke()
                    ctx.fillStyle = Theme.textDisabled
                    ctx.fillText((maxDepth - (i / 4) * maxDepth).toFixed(1), 2, yy + 3)

                    var xx = gx + (i / 4) * gw
                    ctx.beginPath(); ctx.moveTo(xx, gy); ctx.lineTo(xx, gy + gh); ctx.stroke()
                    ctx.fillStyle = Theme.textDisabled
                    ctx.fillText(Math.round((i / 4) * maxMotor).toString(), xx - 10, h - 4)
                }

                // Axis labels
                ctx.fillStyle = Theme.textDisabled
                ctx.fillText("Motor Position", gx + gw / 2 - 30, h - 1)

                if (points.length === 0) return

                // Current motor position line
                var curX = gx + (currentMotorPos / maxMotor) * gw
                ctx.strokeStyle = Theme.warning; ctx.lineWidth = 1; ctx.setLineDash([4, 3])
                ctx.beginPath(); ctx.moveTo(curX, gy); ctx.lineTo(curX, gy + gh); ctx.stroke()
                ctx.setLineDash([])

                // Sort points by depth
                var sorted = points.slice().sort(function(a, b) { return a.depth - b.depth })

                // Draw interpolated line
                if (sorted.length >= 2) {
                    ctx.strokeStyle = Theme.primary; ctx.lineWidth = 1.5; ctx.globalAlpha = 0.8
                    ctx.beginPath()
                    for (var j = 0; j < sorted.length; j++) {
                        var px = gx + (sorted[j].motorPosition / maxMotor) * gw
                        var py = gy + (1 - sorted[j].depth / maxDepth) * gh
                        if (j === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py)
                    }
                    ctx.stroke(); ctx.globalAlpha = 1.0
                }

                // Draw points
                for (var k = 0; k < sorted.length; k++) {
                    var pt = sorted[k]
                    var ptx = gx + (pt.motorPosition / maxMotor) * gw
                    var pty = gy + (1 - pt.depth / maxDepth) * gh
                    var r = pt.confidence > 0.7 ? 4 : 3
                    ctx.fillStyle = pt.confidence > 0.7 ? Theme.primaryHover : Theme.warning
                    ctx.beginPath(); ctx.arc(ptx, pty, r, 0, 2 * Math.PI); ctx.fill()
                    ctx.strokeStyle = Theme.bg; ctx.lineWidth = 1; ctx.stroke()
                }
            }
        }

        // Controls bar
        RowLayout {
            Layout.fillWidth: true; spacing: 8

            Rectangle {
                height: 22; width: fitRow.implicitWidth + 16; radius: Theme.radiusSm; color: Theme.surface; border.width: 1; border.color: Theme.border
                RowLayout { id: fitRow; anchors.centerIn: parent; spacing: 4
                    Text { text: "Fit:"; font.pixelSize: 10; color: Theme.textSecondary }
                    Text { text: graph.fitType; font.pixelSize: 10; font.weight: Font.DemiBold; color: Theme.primary }
                }
            }

            Rectangle {
                visible: points.length >= 2
                height: 22; width: rangeRow.implicitWidth + 16; radius: Theme.radiusSm; color: Theme.surface; border.width: 1; border.color: Theme.border
                RowLayout { id: rangeRow; anchors.centerIn: parent; spacing: 4
                    Text { text: "Range:"; font.pixelSize: 10; color: Theme.textSecondary }
                    Text {
                        property var sorted: { var s = points.slice().sort(function(a,b){return a.depth-b.depth}); return s }
                        text: sorted.length >= 2 ? sorted[0].depth.toFixed(2) + " – " + sorted[sorted.length-1].depth.toFixed(2) + " m" : ""
                        font.pixelSize: 10; color: Theme.textPrimary
                    }
                }
            }

            Item { Layout.fillWidth: true }
        }
    }

    onPointsChanged: canvas.requestPaint()
    onCurrentMotorPosChanged: canvas.requestPaint()
}
```

- [ ] **Step 4: Rewrite CalibrationView.qml**

Replace the full contents of `src/desktop/src/ui/qml/CalibrationView.qml`:

```qml
import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: calibView
    property var calibrationPoints: []

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // LEFT: Motor + Previews (200px)
        ColumnLayout {
            Layout.preferredWidth: Theme.sidebarNarrow
            Layout.fillHeight: true
            spacing: 0

            // Motor control
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: motorSection.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: motorSection; anchors.fill: parent; anchors.margins: 10; spacing: 6
                    SectionHeader { text: "MOTOR CONTROL" }
                    MotorSlider { Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0; enabled: alice ? alice.motorConnected : false; onMotorMoved: (pos) => { if (alice) alice.setMotorPosition(pos) } }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Camera preview
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: cameraPreviewCol.implicitHeight + 16; color: Theme.bg
                ColumnLayout {
                    id: cameraPreviewCol; anchors.fill: parent; anchors.margins: 8; spacing: 4
                    Text { text: "CAMERA"; font.pixelSize: 8; color: Theme.textDisabled }
                    Rectangle {
                        Layout.fillWidth: true; Layout.preferredHeight: width * 9 / 16; color: Theme.well; radius: Theme.radiusSm
                        VideoRenderer { anchors.fill: parent; source: alice ? alice.captureFrame : null; visible: alice ? alice.captureCardConnected : false }
                        Label { anchors.centerIn: parent; text: "No camera"; font.pixelSize: 10; color: Theme.textPlaceholder; visible: alice ? !alice.captureCardConnected : true }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth preview
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: depthPreviewCol.implicitHeight + 16; color: Theme.bg
                ColumnLayout {
                    id: depthPreviewCol; anchors.fill: parent; anchors.margins: 8; spacing: 4
                    RowLayout {
                        Text { text: "DEPTH"; font.pixelSize: 8; color: Theme.textDisabled; Layout.fillWidth: true }
                        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m" : "—"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.success }
                    }
                    Rectangle {
                        Layout.fillWidth: true; Layout.preferredHeight: width * 3 / 4; color: Theme.well; radius: Theme.radiusSm
                        VideoRenderer { anchors.centerIn: parent; width: Math.min(parent.width, parent.height * 4 / 3); height: width * 3 / 4; source: alice ? alice.colorFrame : null; visible: alice ? alice.realSenseConnected : false }
                        Item {
                            property real normX: alice ? alice.measureX : 0.5; property real normY: alice ? alice.measureY : 0.5
                            x: normX * parent.width - 6; y: normY * parent.height - 6; width: 12; height: 12; visible: alice ? alice.realSenseConnected : false
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 12; height: 1; color: "#fff" }
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 12; color: "#fff" }
                        }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.CrossCursor; property bool dragging: false
                            onPressed: (mouse) => { dragging = true; updatePos(mouse.x, mouse.y) }
                            onPositionChanged: (mouse) => { if (dragging) updatePos(mouse.x, mouse.y) }
                            onReleased: dragging = false
                            function updatePos(mx, my) { if (alice) alice.setMeasurementPosition(Math.max(0, Math.min(1, mx / width)), Math.max(0, Math.min(1, my / height))) }
                        }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Actions
            ColumnLayout {
                Layout.fillWidth: true; Layout.margins: 8; spacing: 4
                CheckBox { text: "Test mode"; Material.accent: Theme.primary }
                Button {
                    text: "Record Point"; Layout.fillWidth: true; Material.background: Theme.primary
                    enabled: alice ? (alice.motorConnected && alice.realSenseConnected && alice.depth > 0 && alice.depthConfidence >= 0.5) : false
                    onClicked: {
                        if (!alice) return
                        calibrationPoints.push({ depth: alice.depth, motorPosition: alice.motorPosition, confidence: alice.depthConfidence })
                        calibrationPointsChanged()
                    }
                }
            }

            Item { Layout.fillHeight: true }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // CENTER: Data table (280px)
        CalibrationTable {
            Layout.preferredWidth: 280; Layout.fillHeight: true
            points: calibrationPoints
            onPointRemoved: (index) => { calibrationPoints.splice(index, 1); calibrationPointsChanged() }
            onExportRequested: exportDialog.open()
            onClearRequested: { calibrationPoints = []; calibrationPointsChanged() }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // RIGHT: Graph (flex)
        CalibrationGraph {
            Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 12
            points: calibrationPoints
            currentMotorPos: alice ? alice.motorPosition : 0
        }
    }

    onVisibleChanged: { if (visible && alice) alice.focusMode = 0 }
}
```

- [ ] **Step 5: Add new QML files to CMakeLists.txt**

Add to `QML_FILES`:

```cmake
        src/ui/qml/CfgView.qml
        src/ui/qml/components/CalibrationTable.qml
        src/ui/qml/components/CalibrationGraph.qml
```

- [ ] **Step 6: Build**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

- [ ] **Step 7: Commit**

```bash
git add src/desktop/src/ui/qml/CfgView.qml src/desktop/src/ui/qml/CalibrationView.qml src/desktop/src/ui/qml/components/CalibrationTable.qml src/desktop/src/ui/qml/components/CalibrationGraph.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): add CfgView container, CalibrationTable, CalibrationGraph, rewrite CalibrationView"
```

---

## Task 9: Revised SettingsView + ConnectionView

**Files:**
- Rewrite: `src/desktop/src/ui/qml/SettingsView.qml`
- Rename/Rewrite: `src/desktop/src/ui/qml/ConnectionDialog.qml` → `src/desktop/src/ui/qml/ConnectionView.qml`
- Modify: `src/desktop/CMakeLists.txt`

- [ ] **Step 1: Rewrite SettingsView.qml as card grid**

Replace the full contents of `src/desktop/src/ui/qml/SettingsView.qml`:

```qml
import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

ScrollView {
    id: settingsView

    GridLayout {
        width: settingsView.width - 32
        anchors.margins: 16
        columns: 3
        columnSpacing: 12
        rowSpacing: 12

        // Autofocus
        SettingsCard {
            title: "Autofocus"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                RowLayout { Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: "0.70"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                Slider { Layout.fillWidth: true; from: 0; to: 1; stepSize: 0.05; value: 0.7; Material.accent: Theme.primary }
                RowLayout { Text { text: "Smoothing"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Switch { Material.accent: Theme.primary; checked: true } }
                RowLayout { Text { text: "Response Speed"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: "50"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                Slider { Layout.fillWidth: true; from: 0; to: 100; stepSize: 5; value: 50; Material.accent: Theme.primary }
            }
        }

        // Motor
        SettingsCard {
            title: "Motor"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                RowLayout { Text { text: "Reverse Direction"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Switch { Material.accent: Theme.primary } }
                Text { text: "Calibration Offset"; color: Theme.textSecondary; font.pixelSize: 10 }
                SpinBox { from: -500; to: 500; value: 0; Layout.fillWidth: true }
                Text { text: "Dest Address (hex)"; color: Theme.textSecondary; font.pixelSize: 10 }
                RowLayout {
                    TextField { id: destField; text: "FFFF"; Layout.fillWidth: true; inputMask: "HHHH"; font.family: Theme.fontFamilyMono }
                    Button { text: "Set"; onClicked: { if (alice) alice.setMotorDestination(parseInt(destField.text, 16)) } }
                    Button { text: "Scan"; onClicked: { if (alice) alice.scanMotorAddress(parseInt(destField.text, 16)) } }
                }
            }
        }

        // Depth
        SettingsCard {
            title: "Depth Sensor"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                RowLayout { Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: "0.70"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                Slider { Layout.fillWidth: true; from: 0; to: 1; stepSize: 0.05; value: 0.7; Material.accent: Theme.primary }
                Text { text: "Min Distance (mm)"; color: Theme.textSecondary; font.pixelSize: 10 }
                SpinBox { from: 100; to: 1000; value: 200; Layout.fillWidth: true }
                Text { text: "Max Distance (mm)"; color: Theme.textSecondary; font.pixelSize: 10 }
                SpinBox { from: 1000; to: 10000; value: 5000; Layout.fillWidth: true }
            }
        }

        // Video (2-col span)
        SettingsCard {
            title: "Video"
            columnSpan: 2
            RowLayout {
                Layout.fillWidth: true; spacing: 16
                ColumnLayout {
                    Layout.fillWidth: true; spacing: 6
                    Text { text: "Depth Camera (RealSense)"; color: Theme.textPrimary; font.pixelSize: 10; font.weight: Font.DemiBold }
                    ComboBox { Layout.fillWidth: true; model: alice ? alice.realSenseDepthModes : []; textRole: "label"; Material.accent: Theme.primary
                        onActivated: (index) => { if (!alice) return; let m = alice.realSenseDepthModes[index]; alice.setRealSenseResolution(m.width, m.height, m.fps, m.width, m.height, m.fps) } }
                    Rectangle { Layout.fillWidth: true; Layout.preferredHeight: width * 3 / 4; Layout.maximumHeight: 80; color: Theme.well; radius: Theme.radiusSm
                        DepthRenderer { anchors.centerIn: parent; width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3); height: width * 3 / 4; source: alice ? alice.depthFrame : null; depth: alice ? alice.depth : 0; confidence: alice ? alice.depthConfidence : 0 } }
                }
                ColumnLayout {
                    Layout.fillWidth: true; spacing: 6
                    Text { text: "Camera (Capture Card)"; color: Theme.textPrimary; font.pixelSize: 10; font.weight: Font.DemiBold }
                    ComboBox { Layout.fillWidth: true; model: alice ? alice.captureCardFormats : []; textRole: "label"; Material.accent: Theme.primary
                        onActivated: (index) => { if (!alice) return; let f = alice.captureCardFormats[index]; alice.setCaptureCardResolution(f.width, f.height, f.maxFps) } }
                    Rectangle { Layout.fillWidth: true; Layout.preferredHeight: width * 9 / 16; Layout.maximumHeight: 80; color: Theme.well; radius: Theme.radiusSm
                        VideoRenderer { anchors.centerIn: parent; width: Math.min(parent.width - 4, (parent.height - 4) * 16 / 9); height: width * 9 / 16; source: alice ? alice.captureFrame : null } }
                }
            }
        }

        // System
        SettingsCard {
            title: "System"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                Text { text: "Log Verbosity"; color: Theme.textSecondary; font.pixelSize: 10 }
                ComboBox { model: ["ERROR", "WARNING", "INFO", "DEBUG"]; currentIndex: 2; Layout.fillWidth: true }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
                Button { text: "Reset All Settings"; flat: true; Material.foreground: Theme.dangerText }
            }
        }
    }
}
```

- [ ] **Step 2: Create ConnectionView.qml**

Create `src/desktop/src/ui/qml/ConnectionView.qml`:

```qml
import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: connectionView

    property bool connected: alice ? alice.syncClientConnected : false

    // Auto-start server on view entry
    onVisibleChanged: {
        if (visible && alice && !alice.syncServerRunning)
            alice.startSyncServer()
    }

    // Waiting state — centered
    ColumnLayout {
        visible: !connected
        anchors.centerIn: parent
        spacing: 10
        width: Math.min(parent.width * 0.6, 400)

        // QR Code
        Rectangle {
            Layout.alignment: Qt.AlignHCenter; width: 148; height: 148; color: "#ffffff"; radius: Theme.radiusSm
            visible: alice ? alice.syncServerRunning : false
            VideoRenderer { anchors.centerIn: parent; width: 120; height: 120; source: alice ? alice.qrCodeImage : null }
        }

        Label { Layout.alignment: Qt.AlignHCenter; text: alice ? alice.syncQrPayload : ""; font.family: Theme.fontFamilyMono; font.pixelSize: 11; color: Theme.primary; visible: alice ? alice.syncServerRunning : false }
        Label { Layout.alignment: Qt.AlignHCenter; text: "Scan with Alice Android"; font.pixelSize: 10; color: Theme.textDisabled; visible: alice ? alice.syncServerRunning : false }

        // Waiting indicator
        Rectangle {
            Layout.alignment: Qt.AlignHCenter; width: waitRow.implicitWidth + 24; height: 24; radius: Theme.radiusSm
            color: Theme.warningMuted; border.width: 1; border.color: Qt.rgba(0.85, 0.51, 0.17, 0.3)
            visible: alice ? (alice.syncServerRunning && !connected) : false
            Row { id: waitRow; anchors.centerIn: parent; spacing: 5
                Rectangle { width: 5; height: 5; radius: 3; color: Theme.warning; SequentialAnimation on opacity { loops: Animation.Infinite; NumberAnimation { to: 0.4; duration: 800 }; NumberAnimation { to: 1.0; duration: 800 } } }
                Text { text: "Waiting..."; font.pixelSize: 10; color: Theme.warning }
            }
        }

        Button { Layout.alignment: Qt.AlignHCenter; text: "Stop Server"; flat: true; Material.foreground: Theme.dangerText; visible: alice ? alice.syncServerRunning : false; onClicked: { if (alice) alice.stopSyncServer() } }
    }

    // Connected state — split
    RowLayout {
        visible: connected
        anchors.fill: parent
        spacing: 0

        // Left: sync telemetry
        ColumnLayout {
            Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 16; spacing: 12

            Rectangle {
                Layout.fillWidth: true; height: 32; radius: Theme.radiusSm; color: Theme.successMuted; border.width: 1; border.color: Qt.rgba(0.082, 0.702, 0.443, 0.3)
                Row { anchors.centerIn: parent; spacing: 6; Rectangle { width: 6; height: 6; radius: 3; color: Theme.success } Text { text: "Android connected"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Theme.textPrimary } }
            }

            GridLayout {
                columns: 2; columnSpacing: 12; rowSpacing: 4
                Text { text: "Client IP"; color: Theme.textSecondary; font.pixelSize: 10 }
                Text { text: alice ? alice.syncQrPayload : ""; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
                Text { text: "Uptime"; color: Theme.textSecondary; font.pixelSize: 10 }
                Text { text: "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
            }

            Button { text: "Disconnect"; flat: true; Material.foreground: Theme.dangerText; onClicked: { if (alice) alice.stopSyncServer() } }
            Item { Layout.fillHeight: true }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // Right: TX quality
        ColumnLayout {
            Layout.preferredWidth: 240; Layout.fillHeight: true; Layout.margins: 16; spacing: 10

            SectionHeader { text: "TX QUALITY" }

            ColumnLayout {
                spacing: 10; Layout.fillWidth: true

                ColumnLayout {
                    spacing: 3; Layout.fillWidth: true
                    RowLayout { Text { text: "Depth/Color"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: alice ? alice.txQualityDepth.toString() : "85"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                    Slider { Layout.fillWidth: true; from: 10; to: 100; stepSize: 5; value: alice ? alice.txQualityDepth : 85; Material.accent: Theme.primary; onMoved: { if (alice) alice.txQualityDepth = value } }
                }

                ColumnLayout {
                    spacing: 3; Layout.fillWidth: true
                    RowLayout { Text { text: "Camera"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: alice ? alice.txQualityCapture.toString() : "80"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                    Slider { Layout.fillWidth: true; from: 10; to: 100; stepSize: 5; value: alice ? alice.txQualityCapture : 80; Material.accent: Theme.primary; onMoved: { if (alice) alice.txQualityCapture = value } }
                }

                ColumnLayout {
                    spacing: 3; Layout.fillWidth: true
                    RowLayout { Text { text: "Max FPS"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: alice ? alice.txMaxFps.toString() : "30"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                    Slider { Layout.fillWidth: true; from: 5; to: 60; stepSize: 5; value: alice ? alice.txMaxFps : 30; Material.accent: Theme.primary; onMoved: { if (alice) alice.txMaxFps = value } }
                }
            }

            Item { Layout.fillHeight: true }
        }
    }
}
```

- [ ] **Step 3: Update CMakeLists.txt**

Remove `src/ui/qml/ConnectionDialog.qml` from `QML_FILES` and add `src/ui/qml/ConnectionView.qml`. Keep `ConnectionDialog.qml` on disk for now (it will be deleted in the final cleanup task).

- [ ] **Step 4: Build**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add src/desktop/src/ui/qml/SettingsView.qml src/desktop/src/ui/qml/ConnectionView.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): rewrite SettingsView as card grid, add ConnectionView with auto-start server"
```

---

## Task 10: Main.qml — Wire Everything Together

**Files:**
- Rewrite: `src/desktop/src/ui/qml/Main.qml`
- Modify: `src/desktop/CMakeLists.txt` (remove old files from QML_FILES)

- [ ] **Step 1: Rewrite Main.qml**

Replace the full contents of `src/desktop/src/ui/qml/Main.qml`:

```qml
import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

ApplicationWindow {
    id: root
    visible: true
    width: 1440; height: 900
    minimumWidth: 1024; minimumHeight: 600
    title: "Alice Studio"

    Material.theme: Material.Dark
    Material.primary: Theme.primary
    Material.accent: Theme.primary
    Material.background: Theme.bg

    color: Theme.bg

    // Mode: 0=OPS, 1=CFG
    property int currentMode: 0

    // Keyboard shortcuts
    Shortcut { sequence: "Ctrl+1"; onActivated: currentMode = 0 }
    Shortcut { sequence: "Ctrl+2"; onActivated: currentMode = 1 }
    Shortcut { sequence: "M"; onActivated: { if (alice) alice.focusMode = 0 } }
    Shortcut { sequence: "S"; onActivated: { if (alice) alice.focusMode = 1 } }
    Shortcut { sequence: "C"; onActivated: { if (alice) alice.focusMode = 2 } }
    Shortcut { sequence: "F"; onActivated: { if (alice) alice.focusMode = 3 } }
    Shortcut { sequence: "Space"; onActivated: { if (alice) alice.autofocusEnabled = !alice.autofocusEnabled } }
    Shortcut { sequence: "Escape"; onActivated: currentMode = 0 }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Toolbar (40px)
        Rectangle {
            Layout.fillWidth: true; height: 40; color: Theme.elevated

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 12; anchors.rightMargin: 12
                spacing: 12

                // Alice icon
                Image {
                    source: "qrc:/qt/qml/Alice/UI/assets/icons/alice_logo.svg"
                    width: 22; height: 22; sourceSize: Qt.size(22, 22)
                }

                // Mode toggle
                ModeToggle {
                    currentMode: root.currentMode
                    onModeChanged: (mode) => { root.currentMode = mode }
                }

                Rectangle { width: 1; height: 20; color: Theme.border }

                // Focus modes (dimmed in CFG)
                FocusModeSelector {
                    opacity: currentMode === 0 ? 1.0 : 0.5
                    currentMode: alice ? alice.focusMode : 0
                    enabled: alice ? alice.hasMapping : false
                    onModeChanged: (mode) => { if (alice) alice.focusMode = mode }
                }

                Item { Layout.fillWidth: true }

                // Status badges
                Row {
                    spacing: 6

                    StatusBadge {
                        id: motorBadge
                        label: "Motor"; connected: alice ? alice.motorConnected : false
                        deviceName: "nRF52840"; deviceAddress: "0xFFFF"
                        onClicked: motorPopover.toggle()
                    }
                    StatusBadge {
                        id: depthBadge
                        label: "Depth"; connected: alice ? alice.realSenseConnected : false
                        deviceName: "RealSense D455"
                        onClicked: depthPopover.toggle()
                    }
                    StatusBadge {
                        id: camBadge
                        label: "Cam"; connected: alice ? alice.captureCardConnected : false
                        deviceName: "Capture Card"
                        onClicked: camPopover.toggle()
                    }
                    StatusBadge {
                        id: syncBadge
                        label: "Sync"; connected: alice ? alice.syncClientConnected : false
                        isSync: true
                        onClicked: syncPopover.toggle()
                    }
                }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Main content
        StackLayout {
            Layout.fillWidth: true; Layout.fillHeight: true
            currentIndex: currentMode

            OpsView {}
            CfgView {}
        }
    }

    // Popovers (positioned relative to badges)
    BadgePopover {
        id: motorPopover; title: "Motor"
        connected: alice ? alice.motorConnected : false
        deviceName: "nRF52840"; deviceAddress: "0xFFFF"
        x: motorBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }
    BadgePopover {
        id: depthPopover; title: "Depth"
        connected: alice ? alice.realSenseConnected : false
        deviceName: "RealSense D455"
        x: depthBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }
    BadgePopover {
        id: camPopover; title: "Camera"
        connected: alice ? alice.captureCardConnected : false
        deviceName: "Capture Card"
        x: camBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }
    SyncPopover {
        id: syncPopover
        x: syncBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }

    // Click-outside handler to close popovers
    MouseArea {
        anchors.fill: parent; z: 50
        visible: motorPopover.visible || depthPopover.visible || camPopover.visible || syncPopover.visible
        onClicked: { motorPopover.visible = false; depthPopover.visible = false; camPopover.visible = false; syncPopover.visible = false }
    }

    Component.onCompleted: { if (alice) alice.initialize() }
}
```

- [ ] **Step 2: Update CMakeLists.txt QML_FILES**

Remove these from `QML_FILES`:
- `src/ui/qml/CameraView.qml`
- `src/ui/qml/ControlPanel.qml`
- `src/ui/qml/TelemetryPanel.qml`
- `src/ui/qml/ConnectionDialog.qml`

Add these to `QML_FILES` (if not already added in previous tasks):
- `src/ui/qml/OpsView.qml`
- `src/ui/qml/CfgView.qml`
- `src/ui/qml/ConnectionView.qml`

- [ ] **Step 3: Build**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

Expected: Builds successfully. The old QML files (CameraView, ControlPanel, TelemetryPanel, ConnectionDialog) remain on disk but are no longer referenced by CMake.

- [ ] **Step 4: Run the application to verify**

```bash
cd src/desktop/build && ./AliceDesktop
```

Verify: App starts, shows toolbar with Alice icon + OPS/CFG toggle + focus modes + status badges. OPS mode shows camera feed area + sidebar. Clicking CFG shows sub-tabs for Calibration/Settings/Connection.

- [ ] **Step 5: Commit**

```bash
git add src/desktop/src/ui/qml/Main.qml src/desktop/CMakeLists.txt
git commit -m "feat(ui): wire Main.qml with OPS/CFG dual-mode, toolbar, popovers"
```

---

## Task 11: Cleanup — Remove Old Files

**Files:**
- Delete: `src/desktop/src/ui/qml/CameraView.qml`
- Delete: `src/desktop/src/ui/qml/ControlPanel.qml`
- Delete: `src/desktop/src/ui/qml/TelemetryPanel.qml`
- Delete: `src/desktop/src/ui/qml/ConnectionDialog.qml`

- [ ] **Step 1: Delete old files**

```bash
cd src/desktop
rm src/ui/qml/CameraView.qml src/ui/qml/ControlPanel.qml src/ui/qml/TelemetryPanel.qml src/ui/qml/ConnectionDialog.qml
```

- [ ] **Step 2: Build to verify nothing breaks**

```bash
cd src/desktop/build && cmake .. && cmake --build . 2>&1 | tail -10
```

- [ ] **Step 3: Commit**

```bash
git add -A src/desktop/src/ui/qml/
git commit -m "chore(ui): remove old CameraView, ControlPanel, TelemetryPanel, ConnectionDialog"
```
