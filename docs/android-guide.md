# Android Guide

Daily usage of the Alice Android app.

## Camera Screen

The main screen shows your camera preview (via UVC capture card or RealSense color feed) with a draggable depth measurement point. The device status indicator at the top-left shows connected devices (e.g., "2/3" means 2 of 3 slots active).

### Autofocus Modes

Switch modes from the bottom toolbar or Settings > Autofocus.

| Mode | How to use |
|:-----|:-----------|
| **Manual (MF)** | Drag the motor slider to set focus position directly. Use the 5 preset buttons (1-5) for quick jumps across the motor range. |
| **Single (AF-S)** | Tap the depth preview to measure distance at that point. Alice focuses once and locks. Tap again to re-focus. |
| **Continuous (AF-C)** | Tap to select a tracking point. Alice continuously reads depth there and adjusts focus as the subject moves. |
| **Face (AF-F)** | Alice detects faces automatically and focuses on the nearest one. Tap a specific face to pin it as the primary target. |

### Depth Overlay

The draggable depth preview shows the RealSense color feed with a crosshair at the current measurement point. Drag the crosshair to sample depth at a different location. The depth value and confidence percentage are displayed alongside.

### Face Tracking Details

In AF-F mode, detected faces are shown as colored bounding boxes:
- **Green box** — eye-locked (best quality)
- **Blue box** — face detected, no eye lock
- **Orange dashed box** — predicted position (face temporarily occluded)

The primary face (the one Alice focuses on) is selected automatically by a scoring system that considers face size, screen position, eye visibility, and tracking history. You can override the selection by tapping a face.

## Settings

### Motor

- **Discover Motor Address** — scans for the correct Tilta address if the default (0xFFFF) doesn't respond
- **Reverse Direction** — inverts the motor travel for rigs where the lens rotates opposite
- **Calibration Offset** — adds a fixed step offset to every motor command

### Autofocus

- **Confidence Threshold** — minimum depth confidence to trigger a focus move (higher = fewer false moves)
- **Smoothing** — enables exponential moving average on motor commands for smoother transitions
- **Response Speed** — how aggressively Alice pursues new depth readings (higher = faster but more hunting)

### Video

Resolution and frame rate for the depth camera and capture card. Higher resolutions improve depth accuracy but increase USB bandwidth.

### Transmission Quality

When synced with Alice Studio, these sliders control JPEG compression for each stream:
- **Camera (main view)** — quality 10-100 (default 92). This is the primary monitoring feed.
- **Depth overlay** — quality 10-100 (default 70). Small overlay, can be compressed aggressively.
- **Max FPS** — caps all streams to conserve bandwidth on slower networks.
