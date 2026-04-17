; =====================================================================
;   Alice Studio — Windows Installer (NSIS)
;
;   Built by release/package.sh on a Windows host with makensis in PATH.
;   The script stages every file the installer ships into a bundle dir
;   (windeployqt output + binary + assets + realsense2.dll), then invokes:
;
;       makensis /DVERSION=X.Y /DBUNDLE_DIR=/abs/path /DOUTPUT_FILE=/abs/path ...
;
;   The /D flags are mandatory — this template has no hardcoded paths.
;
;   Branding bitmaps (welcome.bmp 164x314, header.bmp 150x57) live next
;   to this script and are committed to the repo so every build host can
;   produce an identically-branded installer without ImageMagick /
;   Pillow. Regenerate them with release/windows/make_installer_art.py.
;
;   Requires NSIS 3.x for the MUI2 macros and Unicode default.
; =====================================================================

Unicode true

!ifndef VERSION
    !error "VERSION must be passed via /DVERSION=<semver>"
!endif
!ifndef BUNDLE_DIR
    !error "BUNDLE_DIR must be passed via /DBUNDLE_DIR=<absolute path>"
!endif
!ifndef OUTPUT_FILE
    !define OUTPUT_FILE "AliceStudio-${VERSION}-windows-x64-installer.exe"
!endif

; --- App identity ------------------------------------------------------
!define APP_NAME      "Alice Studio"
!define APP_EXE       "AliceStudio.exe"
!define APP_PUBLISHER "Selka"
!define APP_URL       "https://github.com/Stry233/alice"
!define APP_REGKEY    "Software\Microsoft\Windows\CurrentVersion\Uninstall\AliceStudio"

Name              "${APP_NAME}"
BrandingText      "${APP_NAME} v${VERSION}"
OutFile           "${OUTPUT_FILE}"
InstallDir        "$PROGRAMFILES64\Alice Studio"
InstallDirRegKey  HKLM "Software\AliceStudio" "InstallDir"
RequestExecutionLevel admin
SetCompressor     /SOLID lzma

; Tell Windows we handle DPI scaling ourselves (NSIS 3.x supports this).
; Without this, Windows bilinear-scales the installer dialog at >=125%
; DPI and everything — text, icons, bitmaps — ends up blurry. The
; branding BMPs (welcome.bmp / header.bmp) are rendered at 2x source
; pixel density by release/windows/make_installer_art.py so MUI2's
; stretch-to-fit still looks crisp on high-DPI panels.
ManifestDPIAware true

VIProductVersion    "${VERSION}.0.0"
VIAddVersionKey     "ProductName"     "${APP_NAME}"
VIAddVersionKey     "CompanyName"     "${APP_PUBLISHER}"
VIAddVersionKey     "FileDescription" "Alice Studio installer"
VIAddVersionKey     "FileVersion"     "${VERSION}"
VIAddVersionKey     "ProductVersion"  "${VERSION}"
VIAddVersionKey     "LegalCopyright"  "(C) 2026 ${APP_PUBLISHER}"

; --- Modern UI + FileFunc (for ${GetSize} in the uninstall-size reg value)
!include "MUI2.nsh"
!include "FileFunc.nsh"
!insertmacro GetSize

; --- Branding ----------------------------------------------------------
!define MUI_ABORTWARNING

; Welcome/Finish background bitmap. Authored at 2x (328x628) for
; high-DPI sharpness; MUI2's default stretch-to-fit will scale it down
; at 100% DPI and up at 200% DPI without introducing blur. Do NOT set
; MUI_WELCOMEFINISHPAGE_BITMAP_NOSTRETCH here — NOSTRETCH pins the BMP
; to its source pixel size, which either overflows the panel (if the
; source is 2x) or shows a tiny crisp image floating in grey.
!if /FILEEXISTS "${__FILEDIR__}\welcome.bmp"
    !define MUI_WELCOMEFINISHPAGE_BITMAP   "${__FILEDIR__}\welcome.bmp"
    !define MUI_UNWELCOMEFINISHPAGE_BITMAP "${__FILEDIR__}\welcome.bmp"
!endif

; Header bitmap (150x57) shown on inner pages (components / directory /
; install). Default MUI2 header is an ugly grey gradient.
!if /FILEEXISTS "${__FILEDIR__}\header.bmp"
    !define MUI_HEADERIMAGE
    !define MUI_HEADERIMAGE_BITMAP         "${__FILEDIR__}\header.bmp"
    !define MUI_HEADERIMAGE_UNBITMAP       "${__FILEDIR__}\header.bmp"
    !define MUI_HEADERIMAGE_RIGHT
!endif

; App icon in the upper-left of every page + uninstaller chrome.
!if /FILEEXISTS "${BUNDLE_DIR}\alice_app_icon.ico"
    !define MUI_ICON   "${BUNDLE_DIR}\alice_app_icon.ico"
    !define MUI_UNICON "${BUNDLE_DIR}\alice_app_icon.ico"
!endif

; --- Page copy ---------------------------------------------------------
; Plain ASCII punctuation throughout — em-dashes / curly quotes render
; unreliably in the MUI2 welcome-page font on some Windows code pages,
; and NSIS consumes a literal `&` as the accelerator prefix (so the
; next character gets underlined). If you need a literal ampersand,
; write `&&`.
!define MUI_WELCOMEPAGE_TITLE   "Install ${APP_NAME} v${VERSION}"
!define MUI_WELCOMEPAGE_TEXT    "Alice turns any camera into an autofocus cinema rig. It pairs a depth sensor with a wireless focus motor to deliver tap-to-focus, continuous autofocus, and real-time face and eye tracking. Works with any manual or adapted lens.$\r$\n$\r$\n${APP_NAME} is the desktop monitoring station for the system: live camera preview, depth overlay, autofocus pipeline, calibration tools, and system telemetry.$\r$\n$\r$\nThis installer ships every dependency ${APP_NAME} needs. No additional drivers or runtimes are required.$\r$\n$\r$\nClick Next to continue."

!define MUI_FINISHPAGE_TITLE    "${APP_NAME} is installed"
!define MUI_FINISHPAGE_TEXT     "${APP_NAME} v${VERSION} is now installed on your computer.$\r$\n$\r$\nConnect an Intel RealSense depth camera, power on the motor dongle, and launch the app to get started."
!define MUI_FINISHPAGE_RUN      "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Launch ${APP_NAME}"
!define MUI_FINISHPAGE_LINK     "Visit the project on GitHub"
!define MUI_FINISHPAGE_LINK_LOCATION "${APP_URL}"

; --- Page order --------------------------------------------------------
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

!insertmacro MUI_LANGUAGE "English"

; --- Install sections --------------------------------------------------
;
; SEC_APP is read-only (required). SEC_STARTMENU and SEC_DESKTOP are
; opt-outs surfaced on the Components page; both default to selected
; because most users expect to see shortcuts after install.
;
Section "${APP_NAME} (required)" SEC_APP
    SectionIn RO

    SetOutPath "$INSTDIR"
    File /r "${BUNDLE_DIR}\*.*"

    ; Uninstaller entry in Add/Remove Programs
    WriteUninstaller "$INSTDIR\Uninstall.exe"
    WriteRegStr HKLM "${APP_REGKEY}" "DisplayName"     "${APP_NAME}"
    WriteRegStr HKLM "${APP_REGKEY}" "DisplayVersion"  "${VERSION}"
    WriteRegStr HKLM "${APP_REGKEY}" "Publisher"       "${APP_PUBLISHER}"
    WriteRegStr HKLM "${APP_REGKEY}" "URLInfoAbout"    "${APP_URL}"
    WriteRegStr HKLM "${APP_REGKEY}" "InstallLocation" "$INSTDIR"
    WriteRegStr HKLM "${APP_REGKEY}" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
    WriteRegStr HKLM "${APP_REGKEY}" "QuietUninstallString" "$\"$INSTDIR\Uninstall.exe$\" /S"
    !if /FILEEXISTS "${BUNDLE_DIR}\alice_app_icon.ico"
        WriteRegStr HKLM "${APP_REGKEY}" "DisplayIcon" "$\"$INSTDIR\alice_app_icon.ico$\""
    !endif
    WriteRegDWORD HKLM "${APP_REGKEY}" "NoModify" 1
    WriteRegDWORD HKLM "${APP_REGKEY}" "NoRepair" 1

    ; Approximate install footprint for Add/Remove Programs (KB)
    ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
    IntFmt $0 "0x%08X" $0
    WriteRegDWORD HKLM "${APP_REGKEY}" "EstimatedSize" "$0"

    WriteRegStr HKLM "Software\AliceStudio" "InstallDir" "$INSTDIR"
SectionEnd

Section "Start Menu shortcut" SEC_STARTMENU
    CreateDirectory "$SMPROGRAMS\${APP_NAME}"
    CreateShortcut  "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
    CreateShortcut  "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Desktop shortcut" SEC_DESKTOP
    CreateShortcut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
SectionEnd

; Component descriptions shown when hovering each checkbox.
LangString DESC_SEC_APP       ${LANG_ENGLISH} "Core application and all runtime dependencies. Required."
LangString DESC_SEC_STARTMENU ${LANG_ENGLISH} "Add ${APP_NAME} to the Start Menu for easy access."
LangString DESC_SEC_DESKTOP   ${LANG_ENGLISH} "Place a ${APP_NAME} shortcut on the Desktop."

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${SEC_APP}       $(DESC_SEC_APP)
    !insertmacro MUI_DESCRIPTION_TEXT ${SEC_STARTMENU} $(DESC_SEC_STARTMENU)
    !insertmacro MUI_DESCRIPTION_TEXT ${SEC_DESKTOP}   $(DESC_SEC_DESKTOP)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; --- Uninstall section -------------------------------------------------
;
; We don't know which shortcuts were created at install time (the user
; may have opted out of either), so blindly try to remove them — Delete
; and RMDir silently no-op if the target doesn't exist.
;
Section "Uninstall"
    Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
    Delete "$SMPROGRAMS\${APP_NAME}\Uninstall ${APP_NAME}.lnk"
    RMDir  "$SMPROGRAMS\${APP_NAME}"
    Delete "$DESKTOP\${APP_NAME}.lnk"

    RMDir /r "$INSTDIR"

    DeleteRegKey HKLM "${APP_REGKEY}"
    DeleteRegKey HKLM "Software\AliceStudio"
SectionEnd
