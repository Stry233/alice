# Building Alice

Instructions for building the Android app, desktop client, and dongle firmware from source.

## Android App

### Prerequisites

- Android Studio (latest stable)
- JDK 11 or higher
- Android SDK with API level 35

### Build Steps

1. Clone the repository
2. Open `src/android/` in Android Studio as the project root
3. Wait for Gradle to sync dependencies
4. Build > Make Project

The debug APK will be at `src/android/app/build/outputs/apk/debug/app-debug.apk`.

For a release build, use Build > Generate Signed Bundle / APK.

### Notes

Dependencies are managed by Gradle and will be downloaded automatically. The RealSense SDK AAR is bundled in `src/android/app/libs/`.

---

## Desktop Client (C++ / Qt)

### Prerequisites

- CMake 3.21 or higher
- Qt 6.5+ (Core, Gui, Quick, QuickControls2, Multimedia, SerialPort, WebSockets)
- Intel RealSense SDK 2.x (`librealsense2`)
- ONNX Runtime (C++ API) — optional, enables face detection
- A C++17 compatible compiler (GCC 9+, Clang 10+, MSVC 2019+)

### Installing Dependencies

**Fedora (41+)**

```bash
sudo dnf install \
  cmake gcc-c++ \
  qt6-qtbase-devel \
  qt6-qtdeclarative-devel \
  qt6-qtquickcontrols2-devel \
  qt6-qtsvg-devel \
  qt6-qtmultimedia-devel \
  qt6-qtserialport-devel \
  qt6-qtwebsockets-devel \
  librealsense-devel \
  onnxruntime-devel \
  qrencode-devel
```

**Ubuntu / Debian (24.04+)**

```bash
sudo apt install \
  cmake g++ \
  qt6-base-dev \
  qt6-declarative-dev \
  qt6-svg-dev \
  qt6-multimedia-dev \
  qt6-serialport-dev \
  qt6-websockets-dev \
  libqt6quickcontrols2-6 \
  librealsense2-dkms librealsense2-dev \
  libonnxruntime-dev \
  libqrencode-dev
```

**Windows**

1. Install [Qt 6](https://www.qt.io/download-qt-installer) — select Desktop, Qt Quick, Multimedia, Serial Port, and WebSockets components
2. Install the [Intel RealSense SDK](https://www.intelrealsense.com/sdk-2/) (adds CMake config automatically)
3. Install [ONNX Runtime](https://github.com/microsoft/onnxruntime/releases) (optional) — extract and set `OnnxRuntime_DIR` in CMake
4. `qrencode` and `nlohmann/json` are fetched automatically by CMake

### Device Permissions (Linux)

The nRF52840 motor dongle exposes a CDC-ACM serial port (`/dev/ttyACM0`). By default, only `root` can access it. Run the included setup script to install a udev rule that grants access automatically:

```bash
cd src/desktop
./alice-setup install
```

Then re-plug the motor dongle. To verify: `ls -la /dev/alice-motor` should show the symlink.

To cleanly remove the rule later (e.g., when uninstalling Alice):

```bash
./alice-setup uninstall
```

**Alternative:** If you prefer not to install a udev rule, add your user to the `dialout` group instead (`sudo usermod -aG dialout $USER`), then reboot.

### Build Steps

```bash
cd src/desktop
mkdir build && cd build
cmake ..
cmake --build .
```

On Linux with system-installed Qt6, no `-DCMAKE_PREFIX_PATH` is needed. On Windows or custom Qt installs, pass it explicitly:

```bash
cmake .. -DCMAKE_PREFIX_PATH=/path/to/qt6
```

The binary will be at `src/desktop/build/AliceDesktop`.

### Running

```bash
./AliceDesktop
```

The app will auto-discover connected hardware (RealSense, motor dongle, capture card). Check the log panel at the bottom for connection status and errors.

---

## Dongle Firmware

The firmware is a Zephyr RTOS project targeting the nRF52840.

### Prerequisites

1. **nRF Connect SDK for VS Code**

   Follow Nordic's [nRF Connect SDK Fundamentals - Exercise 1](https://academy.nordicsemi.com/courses/nrf-connect-sdk-fundamentals/lessons/lesson-1-nrf-connect-sdk-introduction/topic/exercise-1-1/) to install the SDK and VS Code extension.

2. **nRF Connect for Desktop** with the Programmer app

   Download from [Nordic Semiconductor](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-Desktop), then install the Programmer tool from within the application.

### Build Steps

1. Open the `firmware/` folder in VS Code
2. In the nRF Connect sidebar, click **Create new build configuration**
   - Board: `nrf52840dongle_nrf52840`
   - Configuration: `prj.conf`
3. Click **Build**

The compiled firmware will be at `firmware/build/merged.hex`.

### Flashing

1. Open nRF Connect for Desktop > Programmer
2. Insert the nRF52840 dongle and press the **Reset** button to enter bootloader mode
3. Select the dongle in the device dropdown
4. Click **Add File** and select `merged.hex`
5. Click **Write**

The LED turns blue when flashing completes, and green when connected to Alice.

---

## Troubleshooting

**Gradle sync fails**
- Verify JDK 11+ is configured in Android Studio
- Ensure Android SDK API 35 is installed
- Make sure you opened `src/android/` as the project root, not the repository root

**Board not found during firmware build**
- Confirm nRF Connect SDK is fully installed via the VS Code extension
- Board name must be exactly `nrf52840dongle_nrf52840`

**Dongle not detected in Programmer**
- Press Reset to enter bootloader mode (LED should pulse)
- Try a different USB port
- On Windows, install J-Link software with the "Legacy USB Driver" option selected

**Motor shows "Disconnected" on desktop**
- Verify `/dev/ttyACM0` exists: `ls /dev/ttyACM*`
- Run `./alice-setup status` to check if the udev rule is installed
- If not installed: `./alice-setup install`, then re-plug the dongle
- Check the log panel for "permission denied" errors

**Capture card not detected**
- Verify the device appears in `ls /dev/video*`
- Some capture cards need a specific V4L2 driver — check `dmesg | tail` after plugging in

**Desktop build fails to find Qt**
- On Linux: install the `-devel` packages listed above
- On Windows: set `-DCMAKE_PREFIX_PATH` to your Qt 6 installation directory

**Desktop build fails to find librealsense2**
- On Fedora: `sudo dnf install librealsense-devel`
- On Ubuntu: follow the [Intel librealsense installation guide](https://github.com/IntelRealSense/librealsense/blob/master/doc/distribution_linux.md)
- On Windows: install the Intel RealSense SDK and set `realsense2_DIR` in CMake

**Depth overlay not showing**
- Click the "Show depth overlay" checkbox in the right-side control panel
- Verify the RealSense shows "Connected" in the status bar
