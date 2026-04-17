# Alice Studio — Packaging for Release

Internal guide for producing distributable binaries for GitHub Releases.

The Linux artifacts (`.deb` / `.rpm`) are native packages that declare
their Qt / RealSense dependencies — the user's package manager resolves
them automatically on install. The Windows artifact is an NSIS installer
that bundles every Qt DLL alongside the exe, so the target machine only
needs the Intel RealSense runtime and the nRF52840 USB CDC driver
(both standard Windows installers — see **End-user prerequisites** at
the bottom of this page).

## Version

Defined once in [`src/desktop/CMakeLists.txt`](../CMakeLists.txt):

```cmake
project(AliceStudio VERSION 0.1 LANGUAGES CXX)
```

Edit only that line to bump the version — splash screen, log banner,
compiled binary, and package filenames pick it up automatically.

---

## Linux (.deb for Debian / Ubuntu)

### Build host

Any Debian-family distro with `dpkg-deb`. The produced `.deb` declares
its runtime dependencies and will install on Ubuntu 24.04+ and Debian 12+.

### Prereqs on build host

```bash
sudo apt install dpkg librsvg2-bin  # dpkg-deb + optional PNG icon rendering
```

Plus a full Qt 6 / RealSense / ONNX Runtime toolchain — see [BUILD.md](../../../BUILD.md).

### Build & package

```bash
cd src/desktop
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . -j$(nproc)
cd ..
./release/package.sh deb
```

**Output:** `build/alice-studio_0.1_amd64.deb`

**End-user install:** `sudo apt install ./alice-studio_0.1_amd64.deb`

---

## Linux (.rpm for Fedora / RHEL)

### Build host

Any RPM-family distro with `rpmbuild`. Tested on Fedora 43.

### Prereqs on build host

```bash
sudo dnf install rpm-build
```

Plus the Qt 6 / RealSense / ONNX Runtime toolchain.

### Build & package

```bash
cd src/desktop
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . -j$(nproc)
cd ..
./release/package.sh rpm
```

**Output:** `build/alice-studio-0.1-1.fc43.x86_64.rpm`

**End-user install:** `sudo dnf install ./alice-studio-0.1-1.fc43.x86_64.rpm`

---

## Windows (NSIS installer)

### Build host

A Windows 10 / 11 machine with Git Bash or MSYS2 (the script is bash).
Cross-compilation from Linux is **not** supported — the Windows binary
must be built natively for the Qt runtime to link correctly.

### Prereqs on build host

| Tool | Purpose | Where to get |
|:-----|:--------|:-------------|
| [Qt 6.5+ with MinGW desktop kit](https://www.qt.io/download-qt-installer) | Build + `windeployqt` | Defaults below assume `C:/Qt/6.11.0/mingw_64` |
| [Intel RealSense SDK for Windows](https://github.com/IntelRealSense/librealsense/releases) | RealSense link-time libs + runtime DLLs | `Intel.RealSense.SDK-WIN10-*.exe`, default install prefix |
| [NSIS 3.x](https://nsis.sourceforge.io/Download) | `makensis` — produces the `.exe` installer | Pick "Unicode" build |
| [ImageMagick](https://imagemagick.org/script/download.php) *(optional)* | Generates the multi-resolution app `.ico` | Any recent version |

#### Required Qt components

The Qt online installer defaults to a minimal desktop kit. Make sure the
following are all checked under **Qt → Qt 6.11.0**:

- **MinGW 13.1.0 64-bit** (the compiler kit itself)
- Additional Libraries → **Qt Serial Port**
- Additional Libraries → **Qt WebSockets**
- Additional Libraries → **Qt Multimedia** (included in most kits, but verify)
- Qt Tools → **CMake**, **Ninja**, **MinGW 13.1.0 64-bit** (under *Developer and Designer Tools*)

Headless one-liner for a fresh machine (adjust the version suffix if you
pick a different Qt release — `6110` means 6.11.0):

```bash
/c/Qt/MaintenanceTool.exe install \
  qt.qt6.6110.addons.qtserialport \
  qt.qt6.6110.addons.qtwebsockets \
  --accept-licenses --accept-obligations --confirm-command
```

### Build & package

From a bash shell (Git Bash), with Qt's MinGW + Ninja on `PATH` so CMake
can find them (otherwise CMake defaults to the MSVC `nmake` generator and
aborts with *"CMAKE_CXX_COMPILER not set"*):

```bash
# One-time — add to ~/.bashrc for persistence
export PATH="/c/Qt/Tools/mingw1310_64/bin:/c/Qt/Tools/Ninja:$PATH"

cd src/desktop
mkdir -p build && cd build
cmake .. -G Ninja \
  -DCMAKE_PREFIX_PATH="C:/Qt/6.11.0/mingw_64" \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_TESTS=OFF \
  -DALICE_WIN32_GUI=ON
cmake --build . -j
cd ..
./release/package.sh windows
```

The `-G Ninja` flag is load-bearing on Windows — without it CMake picks
the Visual Studio / `nmake` generator, which does not match the MinGW Qt
kit and fails immediately.

The RealSense SDK is discovered automatically at
`C:/Program Files/RealSense SDK 2.0` (the Intel installer's default).
For a non-default install prefix, pass `-DREALSENSE2_ROOT=<path>`.

`-DBUILD_TESTS=OFF` is recommended for packaging builds: the default
`ON` path runs the gtest binary at configure time to enumerate test
cases, which aborts on Windows unless Qt's `bin/` is on `PATH` at
discovery time. Release artifacts don't ship tests anyway.

`-DALICE_WIN32_GUI=ON` links `AliceStudio.exe` as a Windows GUI-subsystem
binary, so end users don't see a stray console window behind the app.
Leave it **off** (the default) for day-to-day development — with the
console subsystem, `qDebug()` / `std::cerr` output shows up directly in
the terminal that launched the app, which is the primary way to debug
issues during development.

### Output

Two artifacts side by side in `build/`:

| File | Purpose |
|:-----|:--------|
| `AliceStudio-0.1-windows-x64-installer.exe` | NSIS installer — primary distribution |
| `AliceStudio-0.1-windows-x64.zip` | Portable bundle — "unzip and run" alternative |

### What the installer does

- Installs into `C:\Program Files\Alice Studio` (user can relocate during install)
- Creates Start Menu entries and a Desktop shortcut
- Registers with **Add/Remove Programs** — shows publisher "Selka",
  version number, and a clean uninstaller
- **Self-contained bundle** — no external dependencies on the end user's
  machine. Ships every Qt DLL, QML import, plugin (via `windeployqt`),
  MinGW runtime (`libgcc_s_seh-1.dll`, `libstdc++-6.dll`,
  `libwinpthread-1.dll`), the YOLO-face ONNX models, and
  `realsense2.dll` copied from the build host's RealSense SDK

If `makensis` isn't on PATH the script silently skips the installer
step and produces only the `.zip`.

### SmartScreen / Smart App Control

The installer and exe are currently **unsigned**, so Windows' reputation
filters will block both:

- **Microsoft Defender SmartScreen** — shows "Windows protected your PC…
  Unknown publisher". End users click *More info → Run anyway*.
- **Smart App Control** (Windows 11, stricter) — silently blocks
  unsigned apps with no override path. Affected users must disable
  Smart App Control system-wide, install, then re-enable.

Fixing this properly requires an Authenticode code-signing certificate:

| Cert type | Cost | Effect |
|:----------|:-----|:-------|
| Standard (OV) code-signing | ~$100–$200/yr | Unblocks SmartScreen after enough installs build reputation (days–weeks) |
| **EV code-signing** | ~$300–$500/yr | Bypasses SmartScreen immediately; required for Smart App Control compatibility |
| Microsoft Store submission | $20 one-time (dev account) | Store-signed builds, but means shipping through the Store |

Signing integration is not yet wired into `package.sh` — when a cert is
procured, insert a `signtool sign /f <pfx> /fd sha256 /tr <timestamp-url>
/td sha256` call between `windeployqt` and `makensis`, and a second call
over the finished `installer.exe`.

---

## GitHub Release

1. Build on each platform (`.deb` / `.rpm` from their respective Linux hosts,
   `.exe` + `.zip` from a Windows host).
2. Go to <https://github.com/Stry233/Vanta/releases/new> (internal
   mirror — the public `Stry233/alice` repo is driven from release
   artifacts only).
3. Tag: `v0.1` (matching `project(AliceStudio VERSION 0.1 …)` in CMakeLists).
4. Title: `Alice Studio v0.1`.
5. Attach all four artifacts:
   - `alice-studio_0.1_amd64.deb`
   - `alice-studio-0.1-1.fc43.x86_64.rpm`
   - `AliceStudio-0.1-windows-x64-installer.exe`
   - `AliceStudio-0.1-windows-x64.zip`
6. Release notes should summarise user-visible changes and call out
   the end-user prerequisites below.

---

## End-user prerequisites

Regardless of which package format the user installs, the camera and
motor stacks are kernel-level drivers that have to be installed by the
OS vendor's own installer:

**Intel RealSense runtime**

- Linux: `sudo apt install librealsense2 librealsense2-dkms` (Ubuntu)
  or `sudo dnf install librealsense` (Fedora). Our `.deb` / `.rpm`
  declare this as a required dependency so the package manager
  pulls it in automatically.
- Windows: **not required** — `realsense2.dll` is bundled inside the
  installer (copied from the build host's Intel SDK under Apache 2.0).
  End users who want the Intel RealSense Viewer / camera calibration
  tools can still install [the full SDK](https://github.com/IntelRealSense/librealsense/releases), but it's not needed to run Alice Studio.

**nRF52840 USB CDC driver (motor dongle)**

- Linux: no driver install needed — the dongle shows up as
  `/dev/ttyACM0`. Our packages install a udev rule that grants
  the current user read/write access. Running
  `src/desktop/alice-setup install` does the same thing manually.
- Windows: the modern CDC-ACM class driver ships with Windows 10 / 11;
  the dongle enumerates as a COM port automatically. No extra install.

**Optional: GPU face detection**

See [BUILD.md](../../../BUILD.md#gpu-acceleration-face-tracking) for
swapping in a CUDA / TensorRT / DirectML build of ONNX Runtime. The
packaged binary auto-detects whichever EP the bundled runtime supports
and falls back to CPU otherwise.
