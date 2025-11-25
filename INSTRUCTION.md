# Setup Instructions

This guide covers motor pairing, app installation, firmware flashing, and lens calibration.

---

## 1. Motor Setup

The Tilta motor must be configured to operate on IEEE 802.15.4 channel 12. This is a one-time setup using the Tilta hand controller (knob).

### Pairing Procedure

1. Turn on the Knob
2. Navigate to Menu → Connect → 2.4G → Channels and activate **CH1**
3. Power on the motor
4. Short-press the motor's button until the LED turns **pink** (focus motor mode)
5. Double-click the motor's button to enter pairing mode
6. On the Knob, tap **Search** and select the motor from the list
7. Verify the motor responds to the Knob
8. Turn off the Knob—Alice will control the motor from here

---

## 2. App Installation

1. Download `alice.apk` from the [Releases](https://github.com/Stry233/Vanta/releases) page
2. Install the APK on your phone (enable "Install from unknown sources" if prompted)
3. Launch Alice and complete the onboarding screens
4. Grant permissions when requested (Camera, USB access)

---

## 3. Firmware Flashing

### Prerequisites

Install these tools on your computer:

1. **J-Link Software**
   - Download from [Segger](https://www.segger.com/downloads/jlink/#J-LinkSoftwareAndDocumentationPack)
   - During installation, select **"Install Legacy USB Driver for J-Link"** in the Optional Components window

2. **nRF Connect for Desktop**
   - Download from [Nordic Semiconductor](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-Desktop)
   - Install the **Programmer** app from within nRF Connect

### Flashing Steps

1. Download `firmware.hex` from the [Releases](https://github.com/Stry233/Vanta/releases) page
2. Open nRF Connect for Desktop > **Programmer**
3. Insert the nRF52840 dongle
4. Press the **Reset** button on the dongle to enter bootloader mode (LED should pulse)
5. Select the dongle in the device dropdown
6. Click **Add File** and browse to `firmware.hex`
7. Click **Write**

The LED turns solid blue when flashing completes.

---

## 4. First Connection

1. Connect all devices to your phone via a USB hub:
   - nRF52840 dongle
   - Intel RealSense depth camera
   - (Optional) UVC camera for monitoring

2. Launch Alice

3. Grant USB permissions when prompted for each device

4. Check connection status in Settings → Status:
   - Motor: should show connected (green)
   - Depth Camera: should show connected (green)

---

## 5. Lens Calibration

Each lens has a unique relationship between focus distance and motor position. Calibration teaches Alice this mapping for your specific lens.

### Calibration Process

1. Go to **Settings → Autofocus → Calibrator**

2. For each calibration point:
   - Position a subject at a known distance
   - Manually adjust the motor until the subject is in focus
   - Wait for the depth reading to stabilize
   - Tap **Record Point**

3. Record at least 3 points across your focus range. More points improve accuracy.

4. Recommended distances: minimum focus distance, 1m, 2m, 5m, and near infinity

5. Tap **Export** and name the calibration (e.g., "Canon_50mm_f1.4")

The calibration file will be available in autofocus settings.

---

## 6. Using Autofocus

1. Go to **Settings → Autofocus**
2. Select your calibration file
3. Choose an autofocus mode:
   - **Manual**: Motor control only, no automatic focusing
   - **Single (AF-S)**: Tap to focus, position locks until next tap
   - **Continuous (AF-C)**: Tracks depth at selected point
   - **Face (AF-F)**: Tracks detected faces
4. Enable autofocus with the toggle
5. Return to the camera screen

---

## Troubleshooting

**Motor not responding**

The motor address may differ from the default (0xFFFF). Go to Settings → Motor → **Discover Motor Address** to find and save the correct address.

**Unstable depth readings**

- Ensure adequate lighting
- Avoid reflective or transparent surfaces
- Clean the depth camera lens
- Move closer to the subject if at the edge of the sensor's range

**App crashes when connecting devices**

- Disconnect all USB devices
- Restart Alice
- Connect devices one at a time, granting permissions for each
- Try a powered USB hub if using an unpowered one

**Dongle not detected**

- Try a different USB port or hub
- Verify the firmware was flashed successfully (LED should be blue or green, not pulsing)
- Some hubs don't properly support CDC-ACM devices—a powered hub often helps

**Focus hunting or oscillation**

- Increase the confidence threshold in autofocus settings
- Reduce response speed for smoother transitions
- Consider recalibrating with more points if accuracy is off

**Focus moves but result is incorrect**

This usually indicates a calibration issue. Recalibrate with more points across your focus range, and verify you're measuring actual distances rather than estimating.
