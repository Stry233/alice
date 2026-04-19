# Alice Studio Desktop UI Redesign — Design Spec

**Date:** 2026-03-30
**Status:** Approved
**Scope:** Complete UI overhaul of the Qt Quick desktop application at `src/desktop/`

## 1. Design Philosophy

Gotham-style operational interface with Blueprint component precision. The UI should feel like a professional monitoring/control station — high-density, utilitarian, and precise. Palantir Gotham influence (tiled data panels, status bars, live metrics) with Blueprint-level component craft (crisp borders, compact controls, semantic color tokens).

## 2. Two-Mode Architecture

### 2.1 OPS Mode (Default)

The operational monitoring screen where the user spends ~95% of their time. Everything relevant to active shooting is visible in one view — no navigation required.

**Panels:**
- Camera feed (primary, largest panel, with zoom/pan toolkit)
- Motor position control (always visible, always interactive)
- Depth feed (RealSense RGB + crosshair + depth readout)
- Calibration quick-access (active mapping name, preset selector)
- Telemetry metrics (depth, confidence, motor, target, mode, FPS)
- System resources (CPU, GPU, memory — progress bars + values)
- Log panel (scrolling, color-coded by severity)
- RGB histogram overlay (top-right of camera feed)
- Bottom status strip (compact at-a-glance metrics)

### 2.2 CFG Mode

Full-screen workspace for infrequent configuration tasks. Accessed via the mode toggle (OPS/CFG) in the toolbar. Three sub-tabs: Calibration, Settings, Connection.

**Keyboard:** `Ctrl+1` = OPS, `Ctrl+2` = CFG, `Escape` = return to OPS.

## 3. Toolbar (40px, Shared Across Modes)

Left-to-right layout:

| Element | Details |
|---------|---------|
| Alice icon | 22x22 SVG converted from Android drawable (`logo_internal.xml`). No text title. |
| Mode toggle | OPS / CFG segmented button. `Ctrl+1` / `Ctrl+2` |
| Separator | 1px vertical, 20px tall |
| Focus modes | MF, AF-S, AF-C, AF-F toggle group. Keys: `M`, `S`, `C`, `F`. Dimmed (opacity 0.5) when in CFG mode. |
| Spacer | Fills remaining width |
| Status badges | Motor, Depth, Camera, Sync. Clickable — each opens a popover. |

### 3.1 Interactive Status Badge Popovers

Clicking any status badge opens a popover anchored below it.

**Device badges (Motor, Depth, Camera):**
- Header: device name + status chip (Connected/Offline with colored indicator)
- Body: structured data grid (Device, Address/Model, Uptime or Last Seen)
- Footer: Restart + Disconnect buttons (when connected), or Reconnect button (when disconnected)
- Width: 200px

**Sync badge:**
- **When disconnected:** Server auto-starts on popover open. Shows QR code (120x120 on white background), IP:port below, "Scan with Alice Android" hint, pulsing "Waiting..." indicator. Width: 180px.
- **When connected:** Header with "Sync" + "Linked" status chip. Data grid: Client (Android), IP, Latency. Disconnect button. Width: 180px. QR code hidden.

## 4. OPS Mode Layouts

### 4.1 Responsive Breakpoints

| Breakpoint | Layout | Columns |
|------------|--------|---------|
| 1024–1279px | Compact | 2: camera + narrow sidebar (motor slider + value, depth numeric readout with confidence, focus mode indicator, AF status) |
| 1280–1599px | Standard | 2: camera (left, dominant) + full sidebar (motor, depth feed, calibration) |
| 1600px+ | Wide | 3: controls (left) + camera CENTER (dominant) + telemetry/system/log (right) |

### 4.2 Standard Layout (1280–1599px)

Two columns:
- **Left (flex:1):** Camera feed with histogram overlay (top-right), AF status overlay (top-left), zoom/pan toolkit (bottom-left).
- **Right (260px):** Stacked panels — Motor Position, Depth feed (4:3 RealSense RGB + crosshair), Calibration quick-access.
- **Bottom strip (22px):** Full metrics — Depth, Confidence, Motor, Target, AF status, CPU, GPU, MEM, last log line.

### 4.3 Wide Layout (1600px+)

Three columns:
- **Left (240px):** Motor Position, Depth feed, Calibration quick-access.
- **Center (flex:1):** Camera feed with histogram, AF overlay, zoom/pan toolkit. This is the visual anchor.
- **Right (260px):** Telemetry grid, System resources (CPU/GPU/MEM progress bars), Log panel.
- **Bottom strip (22px):** Same full metrics as standard layout (no deduplication — serves as quick-glance bar even when right column is visible).

### 4.4 Camera Zoom/Pan Toolkit

Floating toolbar anchored bottom-left of the camera feed. Semi-transparent background (`rgba(27,32,37,0.92)`) with 1px border.

**Controls (left to right):**
- Zoom out icon (magnifier with minus)
- Zoom slider (60–80px track)
- Zoom in icon (magnifier with plus)
- Separator
- Zoom percentage (monospace, e.g., "135%")
- Separator
- FIT button (resets to 100%)

**Interactions:**

| Input | Behavior |
|-------|----------|
| Scroll wheel | Zoom in/out centered on cursor position |
| Drag (when zoomed >100%) | Pan viewport, cursor changes to grab hand |
| Slider drag | Smooth zoom 100%–400%, centered on viewport |
| +/- icons | Step zoom ±25% |
| FIT button | Reset to 100% fit-to-panel |
| Click (at 100%) | Tap-to-focus at normalized coordinates |

**Mini-map:** Small rectangle (60x40px) anchored bottom-right of camera feed. Shows full-frame outline with a blue viewport indicator rectangle. Only visible when zoom > 100%. Click to jump viewport position.

## 5. CFG Mode

Sub-tab bar below the toolbar: Calibration | Settings | Connection. Underline indicator (2px solid primary) on active tab. Background: `surface` color.

### 5.1 Calibration View

Three-panel layout. Auto-switches to Manual Focus on entry.

**Left panel (200px) — Motor + Previews:**
- Motor control: slider (0–4095), current value, quick preset buttons (1–5)
- Camera preview: natural 16:9 aspect ratio, compact
- Depth preview: natural 4:3 aspect ratio (or 16:9 depending on device), crosshair, depth readout
- Test mode checkbox
- Record Point button (enabled when motor + RealSense connected, depth > 0, confidence >= 50%)

**Center panel (280px) — Recorded Points Data Table:**
- Header row: #, Depth, Motor, Conf, (delete)
- Each row: index, depth in meters (monospace), motor position (monospace, primary color), confidence mini progress bar + percentage, delete button (×)
- Alternating row highlight on hover
- Footer: Export Mapping button (primary) + Clear button (danger)
- Point count displayed in panel header

**Right panel (flex:1) — Calibration Graph:**
- Labeled axes: X = Motor Position (0–4095 with tick marks at 1024 intervals), Y = Depth in meters (0–5.0m with tick marks)
- Grid lines at each tick
- Plotted sample points: sized/colored by confidence (blue = high, orange = low)
- Smooth interpolated curve with subtle confidence band shading
- Current motor position: vertical dashed orange line
- Hover tooltip: shows depth, motor position, confidence for nearest point
- Graph controls bar below:
  - Curve fit type dropdown (Cubic Spline, Linear, Logarithmic)
  - Effective range display (e.g., "0.82 – 4.87 m")
  - R² fit quality indicator

### 5.2 Settings View (Single-Screen Card Grid)

Inspired by the classic Mac OS Control Panel. All settings visible on one scrollable screen. No vertical tabs or sub-navigation.

**Layout:** 3-column card grid with 12px gap, 16px padding. Cards use `surface` background with 1px `border` and `radiusSm` corners.

**Cards:**

| Card | Width | Contents |
|------|-------|----------|
| Autofocus | 1 col | Confidence Threshold slider (0–1.0), Smoothing toggle, Response Speed slider (0–100) |
| Motor | 1 col | Reverse Direction toggle, Calibration Offset slider (-500 to 500), Destination Address hex input + Set/Scan buttons |
| Depth Sensor | 1 col | Confidence Threshold slider, Min Distance (100–1000mm), Max Distance (1000–10000mm) |
| Video | 2 cols | Split: Depth Camera (resolution dropdown + preview) and Camera/Capture Card (resolution dropdown + preview). Previews at natural aspect ratios. |
| System | 1 col | Log Verbosity dropdown (ERROR/WARNING/INFO/DEBUG), Reset All Settings button (danger) |

Each card header: uppercase title with letter-spacing, bottom border separator.

### 5.3 Connection View

Auto-starts sync server on tab entry (same behavior as sync badge popover).

**Waiting state (centered layout):**
- QR code (148x148 white background)
- IP:port (monospace, primary color)
- "Scan with Alice Android" hint text
- Pulsing "Waiting..." indicator
- Stop Server button (danger, small)

**Connected state (split layout):**
- Left: Connected banner (success background), sync telemetry grid (Client IP, Latency, Uptime, Frames sent, Bandwidth), Disconnect button
- Right (240px): TX Quality panel — Depth/Color quality slider, Camera quality slider, Max FPS slider. Each with label + monospace value.

## 6. Design Tokens (Theme.qml)

The existing `Theme.qml` singleton is already well-structured. Updates needed:

### 6.1 Colors (existing, no changes needed)

```
bg:            #1B2025    surface:       #252C33
surfaceHover:  #2E363E    surfaceActive: #363F49
elevated:      #2A3139    well:          #181D22
border:        #394049    borderStrong:  #4A5361
textPrimary:   #E1E8ED    textSecondary: #8A9BA8
textDisabled:  #5C6B7A    textPlaceholder:#6B7785
primary:       #2B95D6    primaryHover:  #48AFF0
success:       #15B371    warning:       #D9822B
danger:        #DB3737    dangerText:    #FF7373
```

### 6.2 Spacing (existing, 4px grid)

```
spaceXs: 4   spaceSm: 8   spaceMd: 12   spaceLg: 16   spaceXl: 24
```

### 6.3 Typography (existing)

```
fontFamily: "Inter"   fontFamilyMono: "RobotoMono"
H1: 20  H2: 16  H3: 14  Body: 13  Small: 12  Caption: 11  Micro: 10
```

### 6.4 New Tokens Needed

```qml
// Popover
readonly property int popoverWidth: 200
readonly property int syncPopoverWidth: 180

// Sidebar widths
readonly property int sidebarNarrow: 200
readonly property int sidebarStandard: 260
readonly property int sidebarWide: 240
readonly property int telemetryColumnWidth: 260

// Breakpoints
readonly property int breakpointStandard: 1280
readonly property int breakpointWide: 1600

// Camera zoom
readonly property real zoomMin: 1.0
readonly property real zoomMax: 4.0
readonly property real zoomStep: 0.25

// Section header style
readonly property int sectionFontSize: 9
readonly property real sectionLetterSpacing: 1.2  // already exists
```

## 7. Animation Specifications

All existing timing values apply:

| Interaction | Duration | Easing |
|-------------|----------|--------|
| Hover color shift | 100ms | OutCubic |
| Panel show/hide | 150ms | OutCubic (enter), InCubic (exit) |
| Popover open | 150ms | OutCubic |
| Popover close | 100ms | InCubic |
| Mode switch (OPS↔CFG) | 150ms | OutCubic |
| Camera zoom | 150ms | OutCubic |
| Status badge pulse (disconnected) | 1600ms | Infinite, linear opacity 1.0→0.4→1.0 |

## 8. Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+1` | Switch to OPS mode |
| `Ctrl+2` | Switch to CFG mode |
| `M` | Manual Focus |
| `S` | AF-S (Single) |
| `C` | AF-C (Continuous) |
| `F` | AF-F (Face Tracking) |
| `Space` | Toggle autofocus on/off |
| `Escape` | Return to OPS from CFG |
| `Scroll on motor slider` | Fine adjustment ±10 steps |
| `Click on camera feed` | Tap-to-focus (at 100% zoom) |
| `Scroll on camera feed` | Zoom in/out |
| `Drag on camera feed` | Pan (when zoomed >100%) |
| `Click on depth feed` | Set measurement position |

## 9. New QML Files Needed

### Views
- `OpsView.qml` — Main operational view with responsive breakpoints
- `CfgView.qml` — Configuration mode container with sub-tab navigation
- `CalibrationView.qml` — Revised calibration (replaces existing)
- `SettingsView.qml` — Card grid settings (replaces existing)
- `ConnectionView.qml` — Revised connection (replaces existing `ConnectionDialog.qml`)

### Components (new)
- `components/ModeToggle.qml` — OPS/CFG segmented button
- `components/BadgePopover.qml` — Device status popover (generic)
- `components/SyncPopover.qml` — Sync-specific popover with QR code
- `components/ZoomToolbar.qml` — Camera zoom/pan floating toolbar
- `components/MiniMap.qml` — Viewport position indicator for zoomed camera
- `components/CalibrationTable.qml` — Recorded points data table
- `components/CalibrationGraph.qml` — Rich calibration curve graph
- `components/SettingsCard.qml` — Reusable card container for settings grid
- `components/SystemMonitor.qml` — CPU/GPU/MEM progress bars
- `components/TelemetryGrid.qml` — Key-value telemetry display

### Components (modified)
- `Main.qml` — Replace StackLayout tabs with OPS/CFG mode switching
- `Theme.qml` — Add new tokens (breakpoints, popover sizes, zoom constants)
- `components/StatusBadge.qml` — Add click handler + popover trigger

### Components (unchanged, reused as-is)
- `components/MotorSlider.qml`
- `components/FocusModeSelector.qml`
- `components/LogDisplay.qml`
- `components/SectionHeader.qml`
- `components/DataLabel.qml`
- `components/AppCard.qml`

### Components (removed)
- `ControlPanel.qml` — Functionality absorbed into OpsView sidebar
- `TelemetryPanel.qml` — Replaced by TelemetryGrid + bottom status strip
- `CameraView.qml` — Replaced by OpsView

## 10. C++ Backend Additions

### System Resource Monitor

New class `SystemMonitor` (in `src/core/` or `src/ui/`) exposing:
- `Q_PROPERTY(double cpuUsage READ cpuUsage NOTIFY cpuUsageChanged)` — app CPU %
- `Q_PROPERTY(double gpuUsage READ gpuUsage NOTIFY gpuUsageChanged)` — GPU utilization (via `/sys/class/drm/` on Linux, NVML if NVIDIA)
- `Q_PROPERTY(double memoryUsage READ memoryUsage NOTIFY memoryUsageChanged)` — app RSS in bytes

Polled on a timer (1s interval). Exposed to QML as a context property.

### Camera Zoom/Pan State

The `VideoRenderer` or a new helper needs:
- `Q_PROPERTY(qreal zoomLevel ...)` — 1.0 to 4.0
- `Q_PROPERTY(QPointF panOffset ...)` — normalized pan offset
- `Q_INVOKABLE void zoomAt(qreal factor, QPointF center)` — zoom centered on a point
- `Q_INVOKABLE void resetZoom()` — reset to fit

## 11. Icon Requirements

All icons as SVG, designed on 16x16 grid with 1.5px stroke:
- Zoom in / zoom out (magnifier + / -)
- Fit-to-frame
- Alice app icon (already exists as Android vector, convert to SVG)

## 12. Inter Font

The Inter font files exist at `/home/yuetian/Documents/repo/Alice/Vanta/Inter/`. Ensure they are registered in the Qt application at startup and available as the primary font family.
