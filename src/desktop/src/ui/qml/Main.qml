import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

ApplicationWindow {
    id: root
    visible: true
    width: 1440
    height: 900
    minimumWidth: 1024
    minimumHeight: 600
    title: "Alice Studio"

    Material.theme: Material.Dark
    Material.primary: "#6650a4"
    Material.accent: "#625b71"
    Material.background: "#1c1b1f"

    color: "#1c1b1f"

    // Navigation
    property int currentView: 0  // 0=Camera, 1=Calibration, 2=Settings, 3=Connection

    // Keyboard shortcuts
    Shortcut { sequence: "M"; onActivated: { if (!alice) return; alice.focusMode = 0 } }  // Manual
    Shortcut { sequence: "S"; onActivated: { if (!alice) return; alice.focusMode = 1 } }  // AF-S
    Shortcut { sequence: "C"; onActivated: { if (!alice) return; alice.focusMode = 2 } }  // AF-C
    Shortcut { sequence: "F"; onActivated: { if (!alice) return; alice.focusMode = 3 } }  // AF-F
    Shortcut { sequence: "Space"; onActivated: { if (!alice) return; alice.autofocusEnabled = !alice.autofocusEnabled } }
    Shortcut { sequence: "Escape"; onActivated: currentView = 0 }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Top toolbar
        ToolBar {
            Layout.fillWidth: true
            height: 48
            Material.background: "#2b2930"

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 16
                anchors.rightMargin: 16

                Label {
                    text: "ALICE STUDIO"
                    font.pixelSize: 16
                    font.weight: Font.Bold
                    font.letterSpacing: 2
                    color: "#d0bcff"
                }

                Item { Layout.fillWidth: true }

                // View tabs
                TabBar {
                    id: viewTabs
                    currentIndex: currentView
                    onCurrentIndexChanged: currentView = currentIndex
                    Material.accent: "#d0bcff"

                    TabButton { text: "Camera"; width: implicitWidth }
                    TabButton { text: "Calibration"; width: implicitWidth }
                    TabButton { text: "Settings"; width: implicitWidth }
                    TabButton { text: "Connection"; width: implicitWidth }
                }

                Item { Layout.fillWidth: true }

                // Status badges
                Row {
                    spacing: 8
                    StatusBadge { label: "Motor"; connected: alice ? alice.motorConnected : false }
                    StatusBadge { label: "Depth"; connected: alice ? alice.realSenseConnected : false }
                    StatusBadge { label: "Camera"; connected: alice ? alice.captureCardConnected : false }
                    StatusBadge { label: "Sync"; connected: alice ? alice.syncClientConnected : false }
                }
            }
        }

        // Main content area
        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: currentView

            CameraView {}
            CalibrationView {}
            SettingsView {}
            ConnectionDialog {}
        }

        // Bottom telemetry bar
        TelemetryPanel {
            Layout.fillWidth: true
            Layout.preferredHeight: 200
        }
    }

    // Startup
    Component.onCompleted: {
        if (!alice) return;
        alice.initialize()
    }
}
