# Sync Setup

Alice Studio (desktop) and the Android app can pair over LAN to form a unified control surface. The desktop acts as the hardware host (RealSense, motor dongle, capture card connected via USB), and streams video + telemetry to the phone over WebSocket.

## Pairing

1. **Desktop**: open CFG > Connection, or click the **Sync** badge in the toolbar. The sync server starts automatically and displays a QR code.
2. **Android**: open Settings > Sync and scan the QR code. The connection establishes within a second on a local network.

Both apps show a green "Linked" indicator when paired.

## What Syncs

| Data | Direction | Notes |
|:-----|:----------|:------|
| Camera feed (HDMI) | Desktop -> Android | JPEG, quality set by "Camera (main view)" slider. Native 1080p by default. |
| Depth colormap | Desktop -> Android | JPEG, quality set by "Depth overlay" slider. Clamped to 640x480. |
| RealSense color | Desktop -> Android | JPEG, same quality as depth overlay. |
| Motor position | Bidirectional | Either side can move the motor. |
| Autofocus mode | Bidirectional | Mode and enable/disable state. |
| Depth readings | Desktop -> Android | Current depth + confidence. |
| Face tracking | Desktop -> Android | Bounding boxes, tracking IDs, eye positions, selected face. |
| Measurement point | Bidirectional | Tap-to-focus crosshair position. |
| Device status | Desktop -> Android | Motor / RealSense / capture card connected state. |

## Transmission Quality

The CFG > Connection tab has three sliders:

- **Camera (main view)** — JPEG quality for the capture card stream (default 92). This is the primary monitoring image on the phone. Set to 95-100 for near-lossless color fidelity.
- **Depth overlay** — JPEG quality for the RealSense depth colormap and RGB stream (default 70). These are small overlays on the phone, so lower quality is fine.
- **Max FPS** — caps all streams (default 30). Drop to 15 on slow WiFi.

## Color Accuracy

Alice Studio converts all capture-card frames to sRGB before JPEG encoding, and the Android app decodes with explicit sRGB preference. This ensures consistent color between the desktop preview and the phone display, regardless of whether the capture card delivers BT.709 or sRGB-tagged frames.

## Network Requirements

At default settings (1080p camera @ quality 92, 30 fps + two overlays @ quality 70):

- Camera stream: ~5-9 MB/s (40-72 Mbps)
- Overlay streams: ~0.5-0.75 MB/s combined
- Total: ~6-10 MB/s

A 5 GHz WiFi connection or wired LAN is recommended. 2.4 GHz WiFi may struggle at full quality. If you experience frame drops, lower the camera quality or max FPS.
