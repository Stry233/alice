cmake_minimum_required(VERSION 3.21)
project(AliceDesktop VERSION 0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

# Qt 6
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Svg Quick QuickControls2 QuickDialogs2 Multimedia Concurrent SerialPort WebSockets)
qt_standard_project_setup(REQUIRES 6.5)

# Intel RealSense SDK
find_package(realsense2 REQUIRED)

# ONNX Runtime
find_package(OnnxRuntime QUIET)
if(NOT OnnxRuntime_FOUND)
    find_library(ONNXRUNTIME_LIB onnxruntime)
    find_path(ONNXRUNTIME_INCLUDE onnxruntime_cxx_api.h PATH_SUFFIXES onnxruntime)
    if(ONNXRUNTIME_LIB AND ONNXRUNTIME_INCLUDE)
        add_library(OnnxRuntime::OnnxRuntime INTERFACE IMPORTED)
        set_target_properties(OnnxRuntime::OnnxRuntime PROPERTIES
            INTERFACE_LINK_LIBRARIES "${ONNXRUNTIME_LIB}"
            INTERFACE_INCLUDE_DIRECTORIES "${ONNXRUNTIME_INCLUDE}"
        )
        set(OnnxRuntime_FOUND TRUE)
    endif()
endif()

# nlohmann/json (header-only)
include(FetchContent)
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
    src/core/realsense/KalmanFilter.cpp
    src/core/autofocus/KalmanFilter2D.cpp
    src/core/realsense/BilateralFilter.cpp
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
        src/ui/FaceOverlay.h src/ui/FaceOverlay.cpp
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
        src/ui/qml/SplashScreen.qml
        src/ui/qml/CfgView.qml
        src/ui/qml/components/CalibrationTable.qml
        src/ui/qml/components/CalibrationGraph.qml
        src/ui/qml/components/MappingPlot.qml
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
qt_add_executable(AliceDesktop src/main.cpp)
target_link_libraries(AliceDesktop PRIVATE
    alice_ui
    alice_uiplugin
    Qt6::QuickControls2
    Qt6::Widgets
    Qt6::Svg
)
qt_import_qml_plugins(AliceDesktop)

# Install
install(TARGETS AliceDesktop RUNTIME DESTINATION bin)

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
