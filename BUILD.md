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
- Qt 6.x (Core, Quick, SerialPort, WebSockets)
- Intel RealSense SDK 2.x (`librealsense2`)
- ONNX Runtime (C++ API)
- A C++17 compatible compiler (GCC 9+, Clang 10+, MSVC 2019+)

### Build Steps

```bash
cd src/desktop
mkdir build && cd build
cmake .. -DCMAKE_PREFIX_PATH=/path/to/qt6
cmake --build .
```

The binary will be at `src/desktop/build/AliceDesktop`.

### Notes

On Linux, install librealsense2 via your package manager or build from source. On Windows, the Intel RealSense SDK installer provides the necessary libraries.

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

**Desktop build fails to find Qt**
- Set `-DCMAKE_PREFIX_PATH` to your Qt 6 installation directory
- Ensure Qt SerialPort and WebSockets modules are installed

**Desktop build fails to find librealsense2**
- On Ubuntu/Fedora: install `librealsense2-dev` from the Intel repository
- On Windows: install the Intel RealSense SDK and set `realsense2_DIR` in CMake
