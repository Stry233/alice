# Building Alice

Instructions for building the Android app and dongle firmware from source.

## Android App

### Prerequisites

- Android Studio (latest stable)
- JDK 11 or higher
- Android SDK with API level 35

### Build Steps

1. Clone the repository
2. Open the project in Android Studio
3. Wait for Gradle to sync dependencies
4. Build > Make Project

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build, use Build > Generate Signed Bundle / APK.

### Notes

Dependencies are managed by Gradle and will be downloaded automatically. The RealSense SDK AAR is bundled in `app/libs/`.

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

**Board not found during firmware build**
- Confirm nRF Connect SDK is fully installed via the VS Code extension
- Board name must be exactly `nrf52840dongle_nrf52840`

**Dongle not detected in Programmer**
- Press Reset to enter bootloader mode (LED should pulse)
- Try a different USB port
- On Windows, install J-Link software with the "Legacy USB Driver" option selected
