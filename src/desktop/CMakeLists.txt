cmake_minimum_required(VERSION 3.21)
project(AliceStudio VERSION 0.1 LANGUAGES CXX)

# Default to a Release build when the caller hasn't specified one. Without
# this, GCC falls back to -O0 and all of the depth-processing hot paths
# (bilateral filter, Kalman update, colormap loop) run ~5–10× slower than
# they should. Honour the value if the user already set it (e.g. Debug).
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING
        "Build type (Debug, Release, RelWithDebInfo, MinSizeRel)" FORCE)
    message(STATUS "CMAKE_BUILD_TYPE not set — defaulting to Release")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

# Qt 6
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Svg Quick QuickControls2 QuickDialogs2 Multimedia Concurrent SerialPort WebSockets)
qt_standard_project_setup(REQUIRES 6.5)

# Intel RealSense SDK
#
# On Linux, librealsense2-dev ships realsense2Config.cmake so find_package
# works directly. The Windows SDK installer (Intel.RealSense.SDK-WIN10-*.exe)
# does NOT ship a CMake config — it only drops headers + a .lib under
# "C:/Program Files/RealSense SDK 2.0". Fall back to manual discovery in
# that case, mirroring the ONNX Runtime pattern below. Override via
# REALSENSE2_ROOT (cache or env var) for non-default install prefixes.
find_package(realsense2 QUIET)
if(NOT realsense2_FOUND)
    set(_rs_hint_root "${REALSENSE2_ROOT}")
    if(NOT _rs_hint_root AND DEFINED ENV{REALSENSE2_ROOT})
        set(_rs_hint_root "$ENV{REALSENSE2_ROOT}")
    endif()
    if(NOT _rs_hint_root AND WIN32)
        set(_rs_hint_root "C:/Program Files/RealSense SDK 2.0")
    endif()

    find_library(REALSENSE2_LIB realsense2
        HINTS "${_rs_hint_root}/lib/x64" "${_rs_hint_root}/lib")
    find_path(REALSENSE2_INCLUDE librealsense2/rs.hpp
        HINTS "${_rs_hint_root}/include")
    if(REALSENSE2_LIB AND REALSENSE2_INCLUDE)
        add_library(realsense2::realsense2 INTERFACE IMPORTED)
        set_target_properties(realsense2::realsense2 PROPERTIES
            INTERFACE_LINK_LIBRARIES "${REALSENSE2_LIB}"
            INTERFACE_INCLUDE_DIRECTORIES "${REALSENSE2_INCLUDE}")
        set(realsense2_FOUND TRUE)
        message(STATUS "RealSense SDK: ${REALSENSE2_LIB}")
    else()
        message(FATAL_ERROR
            "Intel RealSense SDK not found. Install it from "
            "https://github.com/IntelRealSense/librealsense/releases "
            "or set -DREALSENSE2_ROOT=<path>.")
    endif()
endif()

# ONNX Runtime
#
# Search order:
#   1. find_package(OnnxRuntime) — if a CMake package file is installed.
#   2. ONNXRUNTIME_ROOT cache var or env var — explicit path, e.g. to a
#      pre-built GPU release extracted under /opt/onnxruntime-linux-x64-gpu-*.
#      This is the most common path for enabling CUDA / TensorRT / DirectML
#      without rebuilding ONNX Runtime from source.
#   3. System paths — whatever libonnxruntime the OS package ships (usually
#      CPU-only on Linux distros).
#
# At runtime FaceDetector::loadModel will pick the fastest available EP
# from whatever this library was built with — you don't need to recompile
# Alice to switch between CPU and GPU, you just swap the onnxruntime that
# it links against (and ensure it's on LD_LIBRARY_PATH / PATH at launch).
find_package(OnnxRuntime QUIET)
if(NOT OnnxRuntime_FOUND)
    set(_ort_hint_root "${ONNXRUNTIME_ROOT}")
    if(NOT _ort_hint_root AND DEFINED ENV{ONNXRUNTIME_ROOT})
        set(_ort_hint_root "$ENV{ONNXRUNTIME_ROOT}")
    endif()
    if(_ort_hint_root)
        message(STATUS "Using ONNXRUNTIME_ROOT=${_ort_hint_root}")
    endif()

    find_library(ONNXRUNTIME_LIB onnxruntime
        HINTS ${_ort_hint_root}/lib ${_ort_hint_root}/lib64
        PATHS /usr/local/lib /opt/onnxruntime/lib)
    find_path(ONNXRUNTIME_INCLUDE onnxruntime_cxx_api.h
        HINTS ${_ort_hint_root}/include ${_ort_hint_root}/include/onnxruntime
        PATHS /usr/local/include /opt/onnxruntime/include
        PATH_SUFFIXES onnxruntime)
    if(ONNXRUNTIME_LIB AND ONNXRUNTIME_INCLUDE)
        add_library(OnnxRuntime::OnnxRuntime INTERFACE IMPORTED)
        set_target_properties(OnnxRuntime::OnnxRuntime PROPERTIES
            INTERFACE_LINK_LIBRARIES "${ONNXRUNTIME_LIB}"
            INTERFACE_INCLUDE_DIRECTORIES "${ONNXRUNTIME_INCLUDE}"
        )
        set(OnnxRuntime_FOUND TRUE)
        message(STATUS "ONNX Runtime: ${ONNXRUNTIME_LIB}")
    endif()
endif()

# Report which GPU execution providers are likely to be available at
# runtime, based on the platform and what's on disk. This is advisory —
# the final EP selection happens at runtime via Ort::GetAvailableProviders.
if(OnnxRuntime_FOUND)
    set(_ort_eps "CPU")
    if(WIN32)
        list(APPEND _ort_eps "DirectML (if onnxruntime-directml)")
        list(APPEND _ort_eps "CUDA (if onnxruntime-gpu)")
    elseif(APPLE)
        list(APPEND _ort_eps "CoreML (if onnxruntime with CoreML)")
    elseif(UNIX)
        list(APPEND _ort_eps "CUDA (if onnxruntime-gpu)")
        list(APPEND _ort_eps "TensorRT (if onnxruntime-gpu with TRT)")
    endif()
    message(STATUS "ONNX Runtime candidate EPs for this platform: ${_ort_eps}")
endif()

# nlohmann/json (header-only)
include(FetchContent)

# CMake 4.x removed compatibility with pre-3.5 policies, but libqrencode
# v4.1.1 still declares cmake_minimum_required(VERSION 2.8.12). Inject
# the minimum policy version globally so fetched dependencies configure
# cleanly regardless of host CMake version.
set(CMAKE_POLICY_VERSION_MINIMUM 3.5 CACHE STRING
    "Minimum policy version applied to fetched third-party CMake projects" FORCE)

FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG v3.11.3
)
FetchContent_MakeAvailable(nlohmann_json)

# QR code generation
FetchContent_Declare(
    qrencode
    GIT_REPOSITORY https://github.com/fukuchi/libqrencode.git
    GIT_TAG v4.1.1
)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WITH_TOOLS OFF CACHE BOOL "" FORCE)
set(WITH_TESTS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(qrencode)

# libqrencode's CMakeLists.txt does not export its include directory,
# so consumers can't find <qrencode.h>. Add it explicitly.
target_include_directories(qrencode PUBLIC "${qrencode_SOURCE_DIR}")

# Core library (algorithms + hardware, no UI dependency)
add_library(alice_core STATIC
    src/core/realsense/DepthEstimator.cpp
    src/core/autofocus/KalmanFilter2D.cpp
    src/core/autofocus/AutofocusMapping.cpp
    src/core/motor/MotorProtocol.cpp
    src/core/motor/MotorController.cpp
    src/core/realsense/RealSenseManager.cpp
    src/core/autofocus/FaceDetector.cpp
    src/core/autofocus/SubjectTracker.cpp
    src/core/autofocus/AutofocusController.cpp
    src/core/coordination/DeviceCoordinator.cpp
    src/core/coordination/SettingsManager.cpp
    src/core/camera/CaptureCardManager.cpp
    src/core/system/SystemMonitor.cpp
)
target_include_directories(alice_core PUBLIC src)
# Restrict these to CXX compiles only. Without the generator-expression
# guard, CMake also feeds them to MinGW windres (when alice_studio.rc is
# part of AliceStudio transitively linking alice_core), and windres's
# preprocessor mangles the \"-escaped string values into bare `-\` flags
# that gcc rejects. COMPILE_LANGUAGE:CXX excludes the RC language while
# preserving the definitions for every .cpp compile.
target_compile_definitions(alice_core PUBLIC
    $<$<COMPILE_LANGUAGE:CXX>:ALICE_APP_VERSION="${PROJECT_VERSION}">
    $<$<COMPILE_LANGUAGE:CXX>:ALICE_APP_NAME="Alice Studio">
)
target_link_libraries(alice_core PUBLIC
    Qt6::Core
    Qt6::Gui
    Qt6::Multimedia
    Qt6::SerialPort
    realsense2::realsense2
    nlohmann_json::nlohmann_json
)
if(OnnxRuntime_FOUND)
    target_link_libraries(alice_core PUBLIC OnnxRuntime::OnnxRuntime)
    target_compile_definitions(alice_core PUBLIC ALICE_HAS_ONNX=1)
endif()

# Network library
add_library(alice_network STATIC
    src/network/SyncProtocol.cpp
    src/network/SyncServer.cpp
    src/network/SyncClient.cpp
    src/network/QrGenerator.cpp
)
target_include_directories(alice_network PUBLIC src)
target_link_libraries(alice_network PUBLIC
    Qt6::Core
    Qt6::WebSockets
    alice_core
    nlohmann_json::nlohmann_json
    qrencode
)

# Mark Theme.qml as a singleton for the QML module system
set_source_files_properties(src/ui/qml/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

# UI library
qt_add_qml_module(alice_ui
    URI Alice.UI
    VERSION 1.0
    STATIC
    OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/Alice/UI"
    SOURCES
        src/ui/AppController.h src/ui/AppController.cpp
        src/ui/VideoRenderer.h src/ui/VideoRenderer.cpp
        src/ui/DepthRenderer.h src/ui/DepthRenderer.cpp
        src/ui/HistogramRenderer.h src/ui/HistogramRenderer.cpp
    QML_FILES
        src/ui/qml/Main.qml
        src/ui/qml/Theme.qml
        src/ui/qml/CalibrationView.qml
        src/ui/qml/SettingsView.qml
        src/ui/qml/ConnectionView.qml
        src/ui/qml/OpsView.qml
        src/ui/qml/components/AppCard.qml
        src/ui/qml/components/SectionHeader.qml
        src/ui/qml/components/DataLabel.qml
        src/ui/qml/components/StatusBadge.qml
        src/ui/qml/components/FocusModeSelector.qml
        src/ui/qml/components/MotorSlider.qml
        src/ui/qml/components/LogDisplay.qml
        src/ui/qml/components/ModeToggle.qml
        src/ui/qml/components/TelemetryGrid.qml
        src/ui/qml/components/SystemMonitorPanel.qml
        src/ui/qml/components/BottomStrip.qml
        src/ui/qml/components/SettingsCard.qml
        src/ui/qml/components/ZoomToolbar.qml
        src/ui/qml/components/MiniMap.qml
        src/ui/qml/components/BadgePopover.qml
        src/ui/qml/components/SyncPopover.qml
        src/ui/qml/components/FaceOverlay.qml
        src/ui/qml/components/LidarWaveform.qml
        src/ui/qml/SplashScreen.qml
        src/ui/qml/CfgView.qml
        src/ui/qml/components/CalibrationTable.qml
        src/ui/qml/components/CalibrationGraph.qml
        src/ui/qml/components/MappingPlot.qml
        src/ui/qml/components/AliceSlider.qml
        src/ui/qml/components/AliceTextField.qml
        src/ui/qml/components/AliceSpinBox.qml
    RESOURCES
        assets/fonts/Inter-Regular.ttf
        assets/fonts/Inter-Medium.ttf
        assets/fonts/Inter-SemiBold.ttf
        assets/fonts/Inter-Bold.ttf
        assets/fonts/RobotoMono-Regular.ttf
        assets/icons/alice_logo.svg
        assets/icons/zoom_in.svg
        assets/icons/zoom_out.svg
        assets/icons/zoom_fit.svg
        assets/icons/alice_app_icon.svg
)
target_link_libraries(alice_ui PUBLIC
    alice_core
    alice_network
    Qt6::Quick
    Qt6::QuickDialogs2
    Qt6::Concurrent
)

# Main executable
#
# ALICE_WIN32_GUI controls whether Windows spawns a console alongside the
# GUI window. Default OFF — developers want the console to see qDebug()
# output. Release packaging sets it ON (see release/package.sh + the
# Windows section of release/PACKAGING.md) so end users don't see a stray
# black terminal behind the app. No-op on non-Windows platforms.
option(ALICE_WIN32_GUI "Link AliceStudio as a Windows GUI app (no console window)" OFF)

# On Windows, embed an icon + VERSIONINFO (CompanyName, ProductName,
# FileVersion, ...) into AliceStudio.exe via a resource file. Without
# this, File Explorer / Start Menu / Task Manager show a default exe
# icon and empty publisher metadata. src/alice_studio.rc.in is
# configure-time-templated with PROJECT_VERSION + the absolute path to
# the committed .ico so CMake only needs to run once for the metadata
# to be current.
#
# The .rc compiles in its own OBJECT library so windres does NOT inherit
# the main target's include dirs or compile-definitions. MinGW's windres
# invokes gcc as a preprocessor, and gcc chokes on include paths
# containing " - " (OneDrive folder names, etc.) — it fragments the
# argument on the dash and emits "unrecognized command-line option '-\'".
# Isolating the .rc keeps that command line clean of non-essential -I
# and -D flags. The object is plumbed into AliceStudio via
# $<TARGET_OBJECTS:...>, adding exactly the compiled resource section.
set(ALICE_WIN32_RC_OBJECTS "")
if(WIN32)
    set(ALICE_ICON_PATH "${CMAKE_CURRENT_SOURCE_DIR}/assets/icons/alice_app_icon.ico")
    configure_file(
        "${CMAKE_CURRENT_SOURCE_DIR}/src/alice_studio.rc.in"
        "${CMAKE_CURRENT_BINARY_DIR}/alice_studio.rc"
        @ONLY)
    add_library(alice_winres OBJECT "${CMAKE_CURRENT_BINARY_DIR}/alice_studio.rc")
    # Global CMAKE_AUTOMOC ON (set above) adds an autogen include path
    # to every target, including this RC-only OBJECT lib — and that
    # path flows into windres's preprocessor, which then breaks on the
    # ` - ` in the OneDrive folder name. Turn off all three AUTO* here
    # so windres gets an empty -I list.
    set_target_properties(alice_winres PROPERTIES
        AUTOMOC OFF
        AUTORCC OFF
        AUTOUIC OFF)
    set(ALICE_WIN32_RC_OBJECTS "$<TARGET_OBJECTS:alice_winres>")
endif()

qt_add_executable(AliceStudio src/main.cpp ${ALICE_WIN32_RC_OBJECTS})
if(WIN32 AND ALICE_WIN32_GUI)
    set_target_properties(AliceStudio PROPERTIES WIN32_EXECUTABLE TRUE)
endif()
target_link_libraries(AliceStudio PRIVATE
    alice_ui
    alice_uiplugin
    Qt6::QuickControls2
    Qt6::Widgets
    Qt6::Svg
)
qt_import_qml_plugins(AliceStudio)

# Stage the YOLO-face ONNX models next to the binary at build time so
# AppController can load them via QCoreApplication::applicationDirPath() +
# "/models/<name>.onnx" without extra runtime configuration. The larger
# yolov11s-face model is preferred; the nano yolo-face is kept as a
# smaller fallback for low-end machines or CI.
set(ALICE_FACE_MODEL_CANDIDATES
    "yolov11s-face.onnx"
    "yolo-face.onnx")

add_custom_command(TARGET AliceStudio POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory
        "$<TARGET_FILE_DIR:AliceStudio>/models"
    COMMENT "Creating models directory next to AliceStudio")

foreach(model ${ALICE_FACE_MODEL_CANDIDATES})
    set(src "${CMAKE_CURRENT_SOURCE_DIR}/assets/models/${model}")
    if(EXISTS "${src}")
        add_custom_command(TARGET AliceStudio POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                "${src}" "$<TARGET_FILE_DIR:AliceStudio>/models/${model}"
            COMMENT "Staging ${model} next to AliceStudio")
        install(FILES "${src}" DESTINATION share/alice-studio/models)
    endif()
endforeach()

# Install
install(TARGETS AliceStudio RUNTIME DESTINATION bin)

# Testing
option(BUILD_TESTS "Build unit tests" ON)
if(BUILD_TESTS)
    enable_testing()
    FetchContent_Declare(
        googletest
        GIT_REPOSITORY https://github.com/google/googletest.git
        GIT_TAG v1.14.0
    )
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(googletest)

    add_executable(alice_tests
        tests/test_kalman.cpp
        tests/test_autofocus.cpp
        tests/test_motor_protocol.cpp
        tests/test_system_monitor.cpp
    )
    target_link_libraries(alice_tests PRIVATE
        alice_core
        GTest::gtest_main
    )
    include(GoogleTest)
    gtest_discover_tests(alice_tests)
endif()
