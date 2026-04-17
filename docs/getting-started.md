# Getting Started

Hardware setup, firmware flashing, and first connection.

## 1. Motor Pairing

The Tilta motor must be configured to operate on IEEE 802.15.4 channel 12. This is a one-time setup using the Tilta hand controller (knob).

1. Turn on the Knob
2. Navigate to **Menu > Connect > 2.4G > Channels** and activate **CH1**
3. Power on the motor
4. Short-press the motor's button until the LED turns **pink** (focus motor mode)
5. Double-click the motor's button to enter pairing mode
6. On the Knob, tap **Search** and select the motor from the list
7. Verify the motor responds to the Knob
8. Turn off the Knob (Alice will control the motor from here)

## 2. Firmware Flashing

The nRF52840 dongle bridges USB to the Tilta wireless protocol.

### Prerequisites

1. **J-Link Software** from [Segger](https://www.segger.com/downloads/jlink/#J-LinkSoftwareAndDocumentationPack). During installation, select **"Install Legacy USB Driver for J-Link"**.
2. **nRF Connect for Desktop** from [Nordic Semiconductor](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-Desktop). Install the **Programmer** app from within nRF Connect.

### Flashing Steps

1. Download `firmware.hex` from the [Releases](https://github.com/Stry233/Vanta/releases) page
2. Open nRF Connect for Desktop > **Programmer**
3. Insert the nRF52840 dongle
4. Press the **Reset** button on the dongle to enter bootloader mode (LED should pulse)
5. Select the dongle in the device dropdown
6. Click **Add File** and browse to `firmware.hex`
7. Click **Write**

The LED turns solid blue when flashing completes, and green when Alice connects.

## 3. App Installation

### Android

1. Download `alice.apk` from [Releases](https://github.com/Stry233/Vanta/releases)
2. Install on your phone (enable "Install from unknown sources" if prompted)
3. Launch Alice and complete the onboarding screens
4. Grant permissions when requested (Camera, USB access)

### Alice Studio (Desktop)

Pre-built binaries are available on the Releases page. To build from source, see [BUILD.md](../BUILD.md).

On Linux, install the udev rule for the motor dongle before first use:

```bash
cd src/desktop
./alice-setup install
```

## 4. First Connection

1. Connect all devices to your phone or PC via a USB hub:
   - nRF52840 dongle (motor bridge)
   - Intel RealSense depth camera
   - *(Optional)* HDMI capture card for camera monitoring
2. Launch Alice (Android) or Alice Studio (Desktop)
3. Grant USB permissions when prompted for each device
4. Check connection status:
   - **Android**: Settings > Status (green indicators)
   - **Desktop**: toolbar badges (green dots + device names)

All three devices should show connected. If the motor shows "Disconnected", see the [motor address discovery](troubleshooting.md#motor-not-responding) section.

## Next Steps

- [Android Guide](android-guide.md) for phone-based operation
- [Alice Studio Guide](desktop-guide.md) for desktop operation
- [Calibration](calibration.md) to set up your first lens profile
- [Sync Setup](sync-guide.md) to pair Android and Desktop
