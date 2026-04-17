#!/usr/bin/env bash
# Package Alice Studio for release.
#
# Usage: ./package.sh [deb|rpm|linux|windows|all]
#
# Artifacts produced (per target):
#   deb      → build/alice-studio_<ver>_amd64.deb                   (Debian / Ubuntu)
#   rpm      → build/alice-studio-<ver>-1.fc<n>.x86_64.rpm          (Fedora / RHEL)
#   windows  → build/AliceStudio-<ver>-windows-x64-installer.exe    (NSIS)
#              build/AliceStudio-<ver>-windows-x64.zip              (portable)
#   linux    → deb + rpm on whichever is available on the build box
#   all      → linux + windows (windows requires running on Windows)
#
# Prerequisites:
#   .deb:      dpkg-deb                       (Debian / Ubuntu)
#   .rpm:      rpmbuild                       (Fedora / RHEL)
#   Windows:   windeployqt (from Qt)          mandatory for DLL bundling
#              makensis (NSIS 3.x)            optional, enables the installer
#              ImageMagick (magick/convert)   optional, generates the app .ico

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

VERSION=$(grep "project(AliceStudio VERSION" CMakeLists.txt | sed 's/.*VERSION \([0-9.]*\).*/\1/')
APP_NAME="AliceStudio"
ARCH="$(uname -m)"
echo "Packaging $APP_NAME v$VERSION ($ARCH)"
BUILD_DIR="build"

if [ ! -f "$BUILD_DIR/$APP_NAME" ] && [ ! -f "$BUILD_DIR/${APP_NAME}.exe" ]; then
    echo "Error: Build not found. Run 'cmake --build build' first."
    exit 1
fi

# --- Shared helpers ---

install_tree() {
    # install_tree <root>
    # Populates a standard Linux filesystem tree under <root>
    local ROOT="$1"
    mkdir -p "$ROOT/usr/bin"
    mkdir -p "$ROOT/usr/share/applications"
    mkdir -p "$ROOT/usr/share/icons/hicolor/scalable/apps"
    mkdir -p "$ROOT/usr/share/icons/hicolor/48x48/apps"
    mkdir -p "$ROOT/usr/share/icons/hicolor/128x128/apps"
    mkdir -p "$ROOT/usr/share/icons/hicolor/256x256/apps"
    mkdir -p "$ROOT/etc/udev/rules.d"

    cp "$BUILD_DIR/$APP_NAME" "$ROOT/usr/bin/"
    chmod 755 "$ROOT/usr/bin/$APP_NAME"

    cat > "$ROOT/usr/share/applications/alice-studio.desktop" << 'DESKTOP'
[Desktop Entry]
Name=Alice Studio
Comment=Autofocus Lens Interface for Cinema Equipment
Exec=AliceStudio
Icon=alice-studio
Type=Application
Categories=Video;AudioVideo;
DESKTOP

    # Icons: SVG + pre-rendered PNGs at standard sizes
    cp assets/icons/alice_app_icon.svg "$ROOT/usr/share/icons/hicolor/scalable/apps/alice-studio.svg"
    cp assets/icons/alice-studio-48.png "$ROOT/usr/share/icons/hicolor/48x48/apps/alice-studio.png"
    cp assets/icons/alice-studio-128.png "$ROOT/usr/share/icons/hicolor/128x128/apps/alice-studio.png"
    cp assets/icons/alice-studio-256.png "$ROOT/usr/share/icons/hicolor/256x256/apps/alice-studio.png"

    # udev rule for nRF52840 motor dongle
    cat > "$ROOT/etc/udev/rules.d/99-alice.rules" << 'UDEV'
# Alice — nRF52840 Motor Control Dongle (Tilta Nucleus Nano II)
SUBSYSTEM=="tty", ATTRS{idVendor}=="2fe3", ATTRS{idProduct}=="0100", MODE="0666", SYMLINK+="alice-motor"
UDEV
}

# --- .deb ---

package_deb() {
    echo "=== Packaging .deb ==="
    local PKGNAME="alice-studio"
    local DEBDIR="$BUILD_DIR/${PKGNAME}_${VERSION}_amd64"
    rm -rf "$DEBDIR"

    install_tree "$DEBDIR"

    mkdir -p "$DEBDIR/DEBIAN"
    cat > "$DEBDIR/DEBIAN/control" << CONTROL
Package: $PKGNAME
Version: $VERSION
Section: video
Priority: optional
Architecture: amd64
Depends: qt6-base-dev | libqt6core6 (>= 6.5),
 qt6-declarative-dev | libqt6quick6 (>= 6.5),
 qt6-multimedia-dev | libqt6multimedia6 (>= 6.5),
 qt6-serialport-dev | libqt6serialport6 (>= 6.5),
 qt6-websockets-dev | libqt6websockets6 (>= 6.5),
 qt6-svg-dev | libqt6svg6 (>= 6.5),
 librealsense2 (>= 2.50)
Recommends: libonnxruntime (>= 1.16)
Maintainer: Selka <noreply@github.com>
Description: Autofocus Lens Interface for Cinema Equipment
 Alice Studio is a desktop application for controlling cinema autofocus
 systems via Intel RealSense depth cameras and motorized lens controllers.
 Features depth-based face tracking, manual/automatic focus modes, and
 wireless sync with Android companion app.
CONTROL

    cat > "$DEBDIR/DEBIAN/postinst" << 'POSTINST'
#!/bin/sh
set -e
if command -v udevadm >/dev/null 2>&1; then
    udevadm control --reload-rules || true
    udevadm trigger || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t /usr/share/icons/hicolor || true
fi
POSTINST
    chmod 755 "$DEBDIR/DEBIAN/postinst"

    cat > "$DEBDIR/DEBIAN/postrm" << 'POSTRM'
#!/bin/sh
set -e
if command -v udevadm >/dev/null 2>&1; then
    udevadm control --reload-rules || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t /usr/share/icons/hicolor || true
fi
POSTRM
    chmod 755 "$DEBDIR/DEBIAN/postrm"

    dpkg-deb --build --root-owner-group "$DEBDIR"
    mv "${DEBDIR}.deb" "$BUILD_DIR/${PKGNAME}_${VERSION}_amd64.deb"
    echo "Created: $BUILD_DIR/${PKGNAME}_${VERSION}_amd64.deb"
}

# --- .rpm ---

package_rpm() {
    echo "=== Packaging .rpm ==="
    local PKGNAME="alice-studio"
    local RPMROOT="$(pwd)/$BUILD_DIR/rpmbuild"
    local SRCROOT="$(pwd)"
    local BUILD_ABS="$(pwd)/$BUILD_DIR"
    rm -rf "$RPMROOT"
    mkdir -p "$RPMROOT"/{SPECS,BUILD,RPMS,SOURCES,SRPMS}

    cat > "$RPMROOT/SPECS/${PKGNAME}.spec" << SPEC
Name:           $PKGNAME
Version:        $VERSION
Release:        1%{?dist}
Summary:        Autofocus Lens Interface for Cinema Equipment
License:        MIT
URL:            https://github.com/Stry233/alice
Vendor:         Selka

AutoReqProv:    no

Requires:       qt6-qtbase >= 6.5
Requires:       qt6-qtdeclarative >= 6.5
Requires:       qt6-qtmultimedia >= 6.5
Requires:       qt6-qtserialport >= 6.5
Requires:       qt6-qtwebsockets >= 6.5
Requires:       qt6-qtsvg >= 6.5
Requires:       qt6-qtwayland >= 6.5
Requires:       librealsense >= 2.50
Recommends:     onnxruntime >= 1.16

%description
Alice Studio is a desktop application for controlling cinema autofocus
systems via Intel RealSense depth cameras and motorized lens controllers.
Features depth-based face tracking, manual/automatic focus modes, and
wireless sync with Android companion app.

%install
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/applications
mkdir -p %{buildroot}/usr/share/icons/hicolor/scalable/apps
mkdir -p %{buildroot}/usr/share/icons/hicolor/48x48/apps
mkdir -p %{buildroot}/usr/share/icons/hicolor/128x128/apps
mkdir -p %{buildroot}/usr/share/icons/hicolor/256x256/apps
mkdir -p %{buildroot}/etc/udev/rules.d
install -m 755 ${BUILD_ABS}/AliceStudio %{buildroot}/usr/bin/AliceStudio
install -m 644 ${SRCROOT}/assets/icons/alice_app_icon.svg %{buildroot}/usr/share/icons/hicolor/scalable/apps/alice-studio.svg
install -m 644 ${SRCROOT}/assets/icons/alice-studio-48.png %{buildroot}/usr/share/icons/hicolor/48x48/apps/alice-studio.png
install -m 644 ${SRCROOT}/assets/icons/alice-studio-128.png %{buildroot}/usr/share/icons/hicolor/128x128/apps/alice-studio.png
install -m 644 ${SRCROOT}/assets/icons/alice-studio-256.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/alice-studio.png
cat > %{buildroot}/usr/share/applications/alice-studio.desktop << 'DESK'
[Desktop Entry]
Name=Alice Studio
Comment=Autofocus Lens Interface for Cinema Equipment
Exec=AliceStudio
Icon=alice-studio
Type=Application
Categories=Video;AudioVideo;
DESK
cat > %{buildroot}/etc/udev/rules.d/99-alice.rules << 'UDEV'
# Alice — nRF52840 Motor Control Dongle (Tilta Nucleus Nano II)
SUBSYSTEM=="tty", ATTRS{idVendor}=="2fe3", ATTRS{idProduct}=="0100", MODE="0666", SYMLINK+="alice-motor"
UDEV

%files
%attr(755, root, root) /usr/bin/AliceStudio
/usr/share/applications/alice-studio.desktop
/usr/share/icons/hicolor/scalable/apps/alice-studio.svg
/usr/share/icons/hicolor/48x48/apps/alice-studio.png
/usr/share/icons/hicolor/128x128/apps/alice-studio.png
/usr/share/icons/hicolor/256x256/apps/alice-studio.png
/etc/udev/rules.d/99-alice.rules

%post
if command -v udevadm >/dev/null 2>&1; then
    udevadm control --reload-rules || true
    udevadm trigger || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t /usr/share/icons/hicolor || true
fi

%postun
if command -v udevadm >/dev/null 2>&1; then
    udevadm control --reload-rules || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t /usr/share/icons/hicolor || true
fi
SPEC

    # QA_RPATHS 0x0010: allow empty RUNPATH (binary was built with CMake defaults)
    QA_RPATHS=0x0010 rpmbuild --define "_topdir $RPMROOT" -bb "$RPMROOT/SPECS/${PKGNAME}.spec"

    mv "$RPMROOT"/RPMS/*/*.rpm "$BUILD_DIR/"
    local RPMFILE=$(ls "$BUILD_DIR"/${PKGNAME}-${VERSION}*.rpm 2>/dev/null | head -1)
    echo "Created: $RPMFILE"
}

# --- Windows ---
#
# Produces two artifacts from the same staged bundle:
#
#   AliceStudio-<version>-windows-x64-installer.exe
#       NSIS installer — the "easy-to-use" path. Lands in
#       Program Files\Alice Studio, writes Start-Menu + Desktop
#       shortcuts, registers with Add/Remove Programs. Requires
#       `makensis` (NSIS 3.x) in PATH.
#
#   AliceStudio-<version>-windows-x64.zip
#       Portable fallback. No install step, users extract and run.
#       Produced unconditionally so there's always a distributable
#       even if NSIS isn't installed on the build box.
#
# Must be run in a bash shell ON WINDOWS (Git Bash / MSYS2) where the
# binary has already been built and `windeployqt` is on PATH. Linux
# hosts can't cross-build the Windows exe; this function exits early
# if no AliceStudio.exe is present in $BUILD_DIR.

package_windows() {
    echo "=== Packaging Windows ==="

    if [ ! -f "$BUILD_DIR/${APP_NAME}.exe" ]; then
        echo "Error: $BUILD_DIR/${APP_NAME}.exe not found."
        echo "Build the Windows binary first (from a Windows host with Qt + MSVC/MinGW)."
        return 1
    fi

    # Stage bundle: binary + Qt runtime + QML imports + icons/desktop
    local BUNDLE="$BUILD_DIR/${APP_NAME}-${VERSION}-windows-x64"
    rm -rf "$BUNDLE"
    mkdir -p "$BUNDLE"

    cp "$BUILD_DIR/${APP_NAME}.exe" "$BUNDLE/"

    # Face-detection ONNX models. CMake stages them next to the exe as
    # a POST_BUILD step; mirror that layout in the bundle so runtime
    # model lookup (applicationDirPath()/models/*.onnx) keeps working
    # after install.
    if [ -d "$BUILD_DIR/models" ]; then
        cp -r "$BUILD_DIR/models" "$BUNDLE/"
    fi

    # Intel RealSense runtime (realsense2.dll). The SDK installer normally
    # adds its bin/ to the system PATH, but bundling the DLL next to the
    # exe makes the installer self-contained — end users don't need the
    # full RealSense SDK installed. The DLL is redistributable under the
    # RealSense SDK's Apache 2.0 license.
    local RS_DLL=""
    for candidate in \
        "${REALSENSE2_ROOT:-}/bin/x64/realsense2.dll" \
        "/c/Program Files/RealSense SDK 2.0/bin/x64/realsense2.dll" \
        "/c/Program Files (x86)/RealSense SDK 2.0/bin/x64/realsense2.dll" \
        "/c/Program Files/Intel RealSense SDK 2.0/bin/x64/realsense2.dll"; do
        if [ -f "$candidate" ]; then
            RS_DLL="$candidate"
            break
        fi
    done
    if [ -n "$RS_DLL" ]; then
        cp "$RS_DLL" "$BUNDLE/"
        echo "Bundled RealSense runtime: $RS_DLL"
    else
        echo "Warning: realsense2.dll not found — installer will depend on a system-wide SDK install"
    fi

    # windeployqt gathers every Qt DLL, QML import, and plugin the
    # binary references — without it the exe won't find platform
    # plugins and crashes on launch.
    if command -v windeployqt6 &>/dev/null; then
        windeployqt6 --qmldir src/ui/qml --release "$BUNDLE/${APP_NAME}.exe"
    elif command -v windeployqt &>/dev/null; then
        windeployqt   --qmldir src/ui/qml --release "$BUNDLE/${APP_NAME}.exe"
    else
        echo "Warning: windeployqt not found — you'll need to ship Qt DLLs manually."
    fi

    # Copy the committed multi-resolution .ico (generated once by
    # release/windows/make_installer_art.py siblings via Pillow) into
    # the bundle. installer.nsi picks it up for MUI_ICON + MUI_UNICON
    # and the Add/Remove Programs DisplayIcon entry. This used to be
    # rebuilt on the fly from PNGs via ImageMagick; committing the .ico
    # removes that optional build dep and keeps every packaging run
    # byte-identical regardless of host tooling.
    if [ -f "assets/icons/alice_app_icon.ico" ]; then
        cp "assets/icons/alice_app_icon.ico" "$BUNDLE/"
    fi

    # 1. NSIS installer (preferred distribution format)
    if command -v makensis &>/dev/null; then
        local NSI="$SCRIPT_DIR/../release/windows/installer.nsi"
        local BUNDLE_ABS BUILD_ABS
        # Prefer pwd -W (MSYS/MinGW → Windows-style path, which makensis
        # wants) and fall back to plain pwd otherwise. Wrap both branches
        # in braces so `&&`/`||` precedence doesn't chain across them and
        # cause both paths to be emitted on separate lines.
        BUNDLE_ABS="$(cd "$BUNDLE" && { pwd -W 2>/dev/null || pwd; })"
        BUILD_ABS="$(cd "$BUILD_DIR" && { pwd -W 2>/dev/null || pwd; })"
        local OUTFILE="${APP_NAME}-${VERSION}-windows-x64-installer.exe"
        # Pass OUTPUT_FILE as an absolute path — NSIS resolves `OutFile`
        # relative to the .nsi script, so a bare filename lands next to
        # installer.nsi, not in build/.
        makensis \
            -DVERSION="$VERSION" \
            -DBUNDLE_DIR="$BUNDLE_ABS" \
            -DOUTPUT_FILE="$BUILD_ABS/$OUTFILE" \
            "$NSI"
        if [ -f "$BUILD_DIR/$OUTFILE" ]; then
            echo "Created: $BUILD_DIR/$OUTFILE"
        fi
    else
        echo "Skipped installer — makensis not found (install NSIS 3.x to enable)"
    fi

    # 2. Portable zip (always produced — "unzip and run" fallback)
    if command -v zip &>/dev/null; then
        (cd "$BUILD_DIR" && zip -rq "$(basename "$BUNDLE").zip" "$(basename "$BUNDLE")")
        echo "Created: ${BUNDLE}.zip"
    else
        echo "Created staging dir: $BUNDLE (zip it manually)"
    fi
}

# --- Main ---

case "${1:-all}" in
    deb)     package_deb ;;
    rpm)     package_rpm ;;
    windows) package_windows ;;
    linux)
        package_deb 2>/dev/null || echo "(skipped .deb — dpkg-deb not found)"
        package_rpm 2>/dev/null || echo "(skipped .rpm — rpmbuild not found)"
        ;;
    all)
        package_deb 2>/dev/null || echo "(skipped .deb — dpkg-deb not found)"
        package_rpm 2>/dev/null || echo "(skipped .rpm — rpmbuild not found)"
        package_windows 2>/dev/null || echo "(skipped Windows — not on Windows)"
        ;;
    *)
        echo "Usage: $0 [deb|rpm|windows|linux|all]"
        exit 1
        ;;
esac
