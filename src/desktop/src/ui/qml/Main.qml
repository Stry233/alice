import QtQuick
import QtQuick.Window
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

ApplicationWindow {
    id: root
    visible: true
    width: 1600; height: 900
    minimumWidth: 1024; minimumHeight: 600
    title: "Alice Studio"

    Material.theme: Material.Dark
    Material.primary: Theme.primary
    Material.accent: Theme.primary
    Material.background: Theme.bg

    color: Theme.bg

    // Compute DPI-based scale factor at startup.
    // Base design is for 96 DPI. Screen.pixelDensity is in px/mm.
    // 96 DPI = 96/25.4 = 3.78 px/mm.
    Component.onCompleted: {
        // Qt handles DPI scaling automatically. Our base values are 2x HTML CSS
        // (matching the 200% demo). No additional scaling needed.
        Theme.scaleFactor = 1.0
        console.log("[Alice] Screen:", Screen.width + "x" + Screen.height,
                    "DPR:", Screen.devicePixelRatio)

        if (alice) alice.initialize()
    }

    // Mode: 0=OPS, 1=CFG
    property int currentMode: 0

    // Keyboard shortcuts
    Shortcut { sequence: "Ctrl+1"; onActivated: currentMode = 0 }
    Shortcut { sequence: "Ctrl+2"; onActivated: currentMode = 1 }
    Shortcut { sequence: "M"; onActivated: { if (alice) { alice.focusMode = 0; alice.autofocusEnabled = false } } }
    Shortcut { sequence: "S"; onActivated: { if (alice) { alice.focusMode = 1; alice.autofocusEnabled = true } } }
    Shortcut { sequence: "C"; onActivated: { if (alice) { alice.focusMode = 2; alice.autofocusEnabled = true } } }
    Shortcut { sequence: "F"; onActivated: { if (alice) { alice.focusMode = 3; alice.autofocusEnabled = true } } }
    Shortcut { sequence: "Space"; onActivated: { if (alice) { if (alice.focusMode === 0) { alice.focusMode = 1; alice.autofocusEnabled = true } else { alice.focusMode = 0; alice.autofocusEnabled = false } } } }
    Shortcut { sequence: "Escape"; onActivated: currentMode = 0 }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Toolbar
        Rectangle {
            Layout.fillWidth: true; height: Theme.toolbarHeight; color: Theme.elevated

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: Theme.dp(24); anchors.rightMargin: Theme.dp(24)
                spacing: Theme.dp(24)

                // Alice icon
                Item {
                    Layout.preferredWidth: Theme.dp(32)
                    Layout.preferredHeight: Theme.dp(36)
                    Layout.maximumWidth: Theme.dp(32)
                    Layout.maximumHeight: Theme.dp(36)
                    Layout.fillHeight: false
                    Layout.alignment: Qt.AlignVCenter
                    Image {
                        width: Theme.dp(32); height: Theme.dp(36)
                        anchors.centerIn: parent
                        source: "qrc:/qt/qml/Alice/UI/assets/icons/alice_logo.svg"
                        sourceSize: Qt.size(Theme.dp(48), Theme.dp(56))
                        fillMode: Image.PreserveAspectFit
                    }
                }

                // Mode toggle
                ModeToggle {
                    currentMode: root.currentMode
                    onModeChanged: (mode) => { root.currentMode = mode }
                }

                Rectangle { width: 1; height: Theme.dp(32); Layout.alignment: Qt.AlignVCenter; color: Theme.border }

                // Focus modes (dimmed in CFG)
                FocusModeSelector {
                    opacity: currentMode === 0 ? 1.0 : 0.5
                    currentMode: alice ? alice.focusMode : 0
                    enabled: alice ? alice.hasMapping : false
                    onModeChanged: (mode) => {
                        if (!alice) return
                        alice.focusMode = mode
                        // Auto-enable/disable autofocus based on mode
                        alice.autofocusEnabled = (mode > 0)
                    }
                }

                Item { Layout.fillWidth: true }

                // Status badges
                Row {
                    spacing: Theme.dp(8)  // badge gap: 4px at 200% = 8

                    StatusBadge {
                        id: motorBadge
                        label: "Motor"; connected: alice ? alice.motorConnected : false
                        deviceName: "nRF52840"
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

    // Popovers (use Timer to position after layout)
    BadgePopover {
        id: motorPopover; title: "Motor"
        connected: alice ? alice.motorConnected : false
        deviceName: "nRF52840"; deviceAddress: "0xFFFF"; uptime: "—"
        y: 48
        onVisibleChanged: if (visible) { var pos = motorBadge.mapToItem(root.contentItem, 0, motorBadge.height + 4); x = Math.max(0, Math.min(pos.x, root.width - width)); y = pos.y }
    }
    BadgePopover {
        id: depthPopover; title: "Depth"
        connected: alice ? alice.realSenseConnected : false
        deviceName: "RealSense D455"; deviceAddress: "USB 3.2"; uptime: "—"
        y: 48
        onVisibleChanged: if (visible) { var pos = depthBadge.mapToItem(root.contentItem, 0, depthBadge.height + 4); x = Math.max(0, Math.min(pos.x, root.width - width)); y = pos.y }
    }
    BadgePopover {
        id: camPopover; title: "Camera"
        connected: alice ? alice.captureCardConnected : false
        deviceName: "Capture Card"; deviceAddress: "UVC"; uptime: "—"
        y: 48
        onVisibleChanged: if (visible) { var pos = camBadge.mapToItem(root.contentItem, 0, camBadge.height + 4); x = Math.max(0, Math.min(pos.x, root.width - width)); y = pos.y }
    }
    SyncPopover {
        id: syncPopover
        y: 48
        onVisibleChanged: if (visible) { var pos = syncBadge.mapToItem(root.contentItem, 0, syncBadge.height + 4); x = Math.max(0, Math.min(pos.x - width + syncBadge.width, root.width - width)); y = pos.y }
    }

    // Close popovers on window resize
    onWidthChanged: { motorPopover.visible = false; depthPopover.visible = false; camPopover.visible = false; syncPopover.visible = false }
    onHeightChanged: { motorPopover.visible = false; depthPopover.visible = false; camPopover.visible = false; syncPopover.visible = false }

    // Click-outside handler to close popovers
    MouseArea {
        anchors.fill: parent; z: 50
        visible: motorPopover.visible || depthPopover.visible || camPopover.visible || syncPopover.visible
        onClicked: { motorPopover.visible = false; depthPopover.visible = false; camPopover.visible = false; syncPopover.visible = false }
    }

    // alice.initialize() is called in the onCompleted above
}
