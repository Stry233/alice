# Alice Studio Guide

Alice Studio is the Qt 6 desktop application for Linux and Windows.

<!-- PLACEHOLDER: res/studio-ops-mode.png — OPS view with camera feed,
     depth overlay, face tracking, motor slider, and telemetry -->

## Layout Modes

The interface adapts to your window width:

| Width | Layout |
|:------|:-------|
| < 1280 px | **Compact** — camera center + right sidebar (motor, depth readout, system monitor, log) |
| 1280-1599 px | **Standard** — camera center + right sidebar (motor, depth preview with reticle, calibration selector) |
| >= 1600 px | **Wide** — left sidebar (motor, depth preview, calibration) + camera center + right sidebar (telemetry, system, log) |

## OPS Mode

The primary operating view. Keyboard shortcut: `Ctrl+1`

### Camera Preview

The center panel shows the live capture-card feed (HDMI input). Zoom with the scroll wheel or the zoom toolbar at the bottom-left. Drag to pan when zoomed in.

### Depth Panel

Shows the RealSense color feed with a draggable measurement reticle. Click or drag to move the depth sampling point. The reticle animates smoothly to new positions with a "lock pulse" on the center ring when the target shifts.

In AF-F mode, the reticle is replaced by face tracking bounding boxes. Each box shows a tracking ID and confidence percentage. Click a face to pin it as the primary focus target.

### AF Status Chip

A floating badge in the top-left of the camera preview shows the active autofocus mode (AF-S / AF-C / AF-F) and whether the system is actively focusing (green) or idle.

### Histogram

A real-time luminance histogram of the camera feed, shown in the top-right corner of the camera preview. Only visible when a capture card is connected.

### Status Badges

The toolbar shows one badge per device: **Motor**, **Depth**, **Cam**, **Sync**. Each badge shows a green/red dot and the device's real name (read from USB descriptors on connection). Click a badge to open its popover with:

- Device model and bus address
- Uptime or last-seen time
- Restart / Disconnect / Reconnect buttons

### Motor Slider

A custom slider (0-4095 range) with 5 preset buttons. Scroll the mouse wheel over the slider for fine adjustments. The numeric value is displayed between the endpoints.

## CFG Mode

Configuration and calibration. Keyboard shortcut: `Ctrl+2`

### Tabs

| Tab | Contents |
|:----|:---------|
| **Calibration** | Motor position slider, camera preview, depth preview with reticle, recorded-points table with interactive graph, export mapping button |
| **Settings** | Autofocus tuning (confidence, smoothing, response speed), motor configuration (reverse, offset, address), depth sensor parameters, video resolution selection, system controls |
| **Connection** | LAN sync server with QR code for Android pairing, per-stream transmission quality sliders, connected-client status |

### Calibration Workflow

See [Calibration](calibration.md) for the full process. The CFG > Calibration tab provides the recording interface with live depth + motor readouts and an interactive interpolation graph.

## Keyboard Shortcuts

| Key | Action |
|:----|:-------|
| `Ctrl+1` | Switch to OPS mode |
| `Ctrl+2` | Switch to CFG mode |
| `M` | Manual focus mode |
| `S` | AF-S (single) mode |
| `C` | AF-C (continuous) mode |
| `F` | AF-F (face tracking) mode |
| `Space` | Toggle between MF and last AF mode |
| `Esc` | Return to OPS mode |

## Window Sizing

Alice Studio opens at 73% of your screen width with a 16:9 aspect ratio, capped to a minimum of 1200 x 780 px. The window freely resizes without aspect-ratio locking.

## GPU Acceleration

Face detection runs through ONNX Runtime. Alice auto-selects the fastest available execution provider:

```
TensorRT > CUDA > DirectML (Windows) > CPU
```

The startup log shows which EP is active: `Face detector loaded: yolov11s-face.onnx (EP=CUDA)`. See [BUILD.md](../BUILD.md#gpu-acceleration-face-tracking) for instructions on swapping to a GPU-enabled ONNX Runtime build.
