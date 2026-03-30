import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

ApplicationWindow {
    id: root
    visible: true
    width: 1440; height: 900
    minimumWidth: 1024; minimumHeight: 600
    title: "Alice Studio"

    Material.theme: Material.Dark
    Material.primary: Theme.primary
    Material.accent: Theme.primary
    Material.background: Theme.bg

    color: Theme.bg

    // Mode: 0=OPS, 1=CFG
    property int currentMode: 0

    // Keyboard shortcuts
    Shortcut { sequence: "Ctrl+1"; onActivated: currentMode = 0 }
    Shortcut { sequence: "Ctrl+2"; onActivated: currentMode = 1 }
    Shortcut { sequence: "M"; onActivated: { if (alice) alice.focusMode = 0 } }
    Shortcut { sequence: "S"; onActivated: { if (alice) alice.focusMode = 1 } }
    Shortcut { sequence: "C"; onActivated: { if (alice) alice.focusMode = 2 } }
    Shortcut { sequence: "F"; onActivated: { if (alice) alice.focusMode = 3 } }
    Shortcut { sequence: "Space"; onActivated: { if (alice) alice.autofocusEnabled = !alice.autofocusEnabled } }
    Shortcut { sequence: "Escape"; onActivated: currentMode = 0 }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Toolbar (40px)
        Rectangle {
            Layout.fillWidth: true; height: 40; color: Theme.elevated

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 12; anchors.rightMargin: 12
                spacing: 12

                // Alice icon
                Image {
                    source: "qrc:/qt/qml/Alice/UI/assets/icons/alice_logo.svg"
                    width: 22; height: 22; sourceSize: Qt.size(22, 22)
                }

                // Mode toggle
                ModeToggle {
                    currentMode: root.currentMode
                    onModeChanged: (mode) => { root.currentMode = mode }
                }

                Rectangle { width: 1; height: 20; color: Theme.border }

                // Focus modes (dimmed in CFG)
                FocusModeSelector {
                    opacity: currentMode === 0 ? 1.0 : 0.5
                    currentMode: alice ? alice.focusMode : 0
                    enabled: alice ? alice.hasMapping : false
                    onModeChanged: (mode) => { if (alice) alice.focusMode = mode }
                }

                Item { Layout.fillWidth: true }

                // Status badges
                Row {
                    spacing: 6

                    StatusBadge {
                        id: motorBadge
                        label: "Motor"; connected: alice ? alice.motorConnected : false
                        deviceName: "nRF52840"; deviceAddress: "0xFFFF"
                        onClicked: motorPopover.toggle()
                    }
                    StatusBadge {
                        id: depthBadge
                        label: "Depth"; connected: alice ? alice.realSenseConnected : false
                        deviceName: "RealSense D455"
                        onClicked: depthPopover.toggle()
                    }
                    StatusBadge {
                        id: camBadge
                        label: "Cam"; connected: alice ? alice.captureCardConnected : false
                        deviceName: "Capture Card"
                        onClicked: camPopover.toggle()
                    }
                    StatusBadge {
                        id: syncBadge
                        label: "Sync"; connected: alice ? alice.syncClientConnected : false
                        isSync: true
                        onClicked: syncPopover.toggle()
                    }
                }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Main content
        StackLayout {
            Layout.fillWidth: true; Layout.fillHeight: true
            currentIndex: currentMode

            OpsView {}
            CfgView {}
        }
    }

    // Popovers (positioned relative to badges)
    BadgePopover {
        id: motorPopover; title: "Motor"
        connected: alice ? alice.motorConnected : false
        deviceName: "nRF52840"; deviceAddress: "0xFFFF"
        x: motorBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }
    BadgePopover {
        id: depthPopover; title: "Depth"
        connected: alice ? alice.realSenseConnected : false
        deviceName: "RealSense D455"
        x: depthBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }
    BadgePopover {
        id: camPopover; title: "Camera"
        connected: alice ? alice.captureCardConnected : false
        deviceName: "Capture Card"
        x: camBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }
    SyncPopover {
        id: syncPopover
        x: syncBadge.mapToItem(root.contentItem, 0, 0).x; y: 44
    }

    // Click-outside handler to close popovers
    MouseArea {
        anchors.fill: parent; z: 50
        visible: motorPopover.visible || depthPopover.visible || camPopover.visible || syncPopover.visible
        onClicked: { motorPopover.visible = false; depthPopover.visible = false; camPopover.visible = false; syncPopover.visible = false }
    }

    Component.onCompleted: { if (alice) alice.initialize() }
}
