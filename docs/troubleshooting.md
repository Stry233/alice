# Troubleshooting

## Motor

### Motor not responding

The motor address may differ from the default (0xFFFF). On Android, go to Settings > Motor > **Discover Motor Address**. On Alice Studio, go to CFG > Settings > Motor and click **Scan** with the hex address field.

### Motor shows "Disconnected" on desktop

1. Verify the dongle is plugged in: `ls /dev/ttyACM*`
2. Run the udev rule installer: `cd src/desktop && ./alice-setup install`, then re-plug the dongle
3. Check `./alice-setup status` to confirm the rule is active
4. If not using the udev rule: `sudo usermod -aG dialout $USER`, then reboot

### Focus moves but result is incorrect

This usually indicates a calibration issue. Recalibrate with more points across your focus range. Verify you're measuring actual distances rather than estimating.

### Focus hunting or oscillation

- Increase the confidence threshold in autofocus settings
- Reduce response speed for smoother transitions
- Add more calibration points in the distance range where hunting occurs

## Depth Camera

### Unstable depth readings

- Ensure adequate lighting (RealSense performs best in well-lit scenes)
- Avoid reflective or transparent surfaces
- Clean the depth camera lens
- Move closer if at the edge of the sensor's range (> 6m for D435, > 4m for D415)

### Depth overlay not showing (desktop)

The depth colormap only renders in CFG mode or when sync streaming is active. Switch to CFG > Calibration to see the depth preview.

### Capture card not detected

- Verify the device appears in `ls /dev/video*` (Linux) or Device Manager (Windows)
- Some capture cards need a specific V4L2 driver. Check `dmesg | tail` after plugging in.
- Try a different USB port. Some hubs don't enumerate UVC devices correctly.

## Face Tracking

### No faces detected

- Verify the RealSense is connected (the face detector runs on the RealSense color feed, not the capture card)
- Check the log for `Face detector loaded: yolov11s-face.onnx (EP=...)`. If it says "face tracking disabled", no YOLO model was found in `models/`
- Ensure faces are at least ~4% of the frame height (roughly 20 px on a 480p feed)

### Face tracking flickers between subjects

Two similarly-sized faces at the same distance can compete for the primary slot. Alice applies a 15% score hysteresis, but extreme ties may still flicker. Tap one face to pin it as the primary target.

## Sync

### QR code not appearing

The sync server failed to start. Check the Alice Studio log panel for errors. Common causes:
- Port 8765 is already in use by another application
- Firewall blocking WebSocket connections

### Android can't connect to desktop

- Both devices must be on the same LAN (same WiFi network, or wired to the same router)
- Check that the desktop's firewall allows inbound TCP on port 8765
- Try disabling VPN if one is active

### Video stream is laggy or low quality

- On 2.4 GHz WiFi, lower the camera quality slider or reduce Max FPS to 15
- Switch to 5 GHz WiFi or a wired connection for best results
- The camera stream defaults to quality 92 at native 1080p. Drop to 80 to halve bandwidth.

### Color mismatch between desktop and Android

Alice Studio converts all capture-card frames to sRGB before encoding, and the Android decoder is locked to sRGB. If you still see a tint difference, it may be your phone's display color profile (e.g., "Vivid" mode on Samsung). Switch to the sRGB or Natural display preset in your phone's settings.

## Build Issues

### Desktop build fails to find Qt

- On Linux, install the `-devel` packages listed in [BUILD.md](../BUILD.md)
- On Windows, set `-DCMAKE_PREFIX_PATH` to your Qt 6 installation directory

### Desktop build fails to find librealsense2

- On Fedora: `sudo dnf install librealsense-devel`
- On Ubuntu: follow the [Intel librealsense installation guide](https://github.com/IntelRealSense/librealsense/blob/master/doc/distribution_linux.md)

### Gradle sync fails (Android)

- Verify JDK 11+ is configured in Android Studio
- Ensure Android SDK API 35 is installed
- Open `src/android/` as the project root (not the repository root)

### Dongle not detected in nRF Connect Programmer

- Press the Reset button to enter bootloader mode (LED should pulse)
- Try a different USB port
- On Windows, install J-Link software with "Legacy USB Driver" selected

## App Crashes

### App crashes when connecting devices (Android)

1. Disconnect all USB devices
2. Force-stop and restart Alice
3. Connect devices one at a time, granting permissions for each
4. Try a powered USB hub if using an unpowered one

### Desktop crashes on capture card connect

Check the terminal for the stack trace. The most common cause is a use-after-unmap in the frame processing pipeline — if you see this after upgrading, rebuild from a clean `build/` directory.
