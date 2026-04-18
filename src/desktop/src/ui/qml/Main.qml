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

    // Default window size: one knob (`windowScreenFraction`) picks how much
    // of the user's logical screen width the window occupies, and the
    // aspect ratio fixes the default shape. IMPORTANT: height is computed
    // from `Screen.width` (not the window's own `width`) so that when the
    // user drags the window's right edge to resize, the height stays
    // exactly where they put it. Binding height to `width` would break
    // that — every horizontal drag would also move the bottom edge.
    // Qt handles DPI scaling automatically via devicePixelRatio, so the
    // same formula works on 4K @100%, 1440p @125%, 1080p @200%, etc.
    readonly property real windowScreenFraction: 0.73
    readonly property real windowAspect: 16 / 9
    width: Math.max(minimumWidth,
                    Math.min(Screen.desktopAvailableWidth - 80,
                             Math.round(Screen.width * windowScreenFraction)))
    height: Math.max(minimumHeight,
                     Math.min(Screen.desktopAvailableHeight - 80,
                              Math.round(Screen.width * windowScreenFraction / windowAspect)))
    minimumWidth: 1200; minimumHeight: 780
    title: "Alice Studio"

    Material.theme: Material.Dark
    Material.primary: Theme.primary
    Material.accent: Theme.primary
    Material.background: Theme.bg

    color: Theme.bg

    Component.onCompleted: {
        Theme.scaleFactor = alice ? alice.uiScaleFactor() : 1.0
        console.log("[Alice] Screen:", Screen.width + "x" + Screen.height,
                    "DPR:", Screen.devicePixelRatio,
                    "uiScale:", Theme.scaleFactor.toFixed(2))
        if (alice) alice.initialize()
    }

    // Ctrl+Plus / Ctrl+Minus / Ctrl+0 UI zoom. Bindings through
    // Theme.dp() re-evaluate automatically on scaleFactor change.
    readonly property real uiScaleMin: 0.6
    readonly property real uiScaleMax: 2.0
    readonly property real uiScaleStep: 0.1
    function applyUiScale(v) {
        var clamped = Math.max(uiScaleMin, Math.min(uiScaleMax, v))
        Theme.scaleFactor = Math.round(clamped * 100) / 100
        if (alice) alice.setUiScaleFactor(Theme.scaleFactor)
    }
    // "Ctrl++" and "Ctrl+=" both mean zoom-in — on US layouts the
    // `+` key is physically Shift+=, which users hit habitually.
    Shortcut { sequences: ["Ctrl++", "Ctrl+="]; onActivated: root.applyUiScale(Theme.scaleFactor + root.uiScaleStep) }
    Shortcut { sequence: "Ctrl+-";              onActivated: root.applyUiScale(Theme.scaleFactor - root.uiScaleStep) }
    Shortcut { sequence: "Ctrl+0";              onActivated: root.applyUiScale(1.0) }

    // Mode: 0=OPS, 1=CFG
    property int currentMode: 0

    // Only CFG's DepthRenderer consumes the colormap — gate the backend
    // so OPS-only users don't pay the 1-5 ms/frame colorize cost.
    Binding {
        target: alice
        property: "showDepthOverlay"
        value: root.currentMode === 1
        when: alice !== null
    }

    function closeAllPopovers() {
        motorPopover.active = false
        depthPopover.active = false
        camPopover.active = false
        syncPopover.active = false
    }
    function togglePopover(target) {
        var wasActive = target.active
        closeAllPopovers()
        if (!wasActive) target.active = true
    }

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
            // preferredHeight (not height) so the ColumnLayout re-lays
            // out when Theme.toolbarHeight changes on zoom.
            Layout.fillWidth: true
            Layout.preferredHeight: Theme.toolbarHeight
            color: Theme.elevated

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

                // Focus modes (dimmed in CFG). NOTE: `currentMode` inside the
                // FocusModeSelector's own binding scope refers to the
                // component's property (the focus mode), not the root mode.
                // Use `root.currentMode` explicitly to avoid greying out the
                // selector when the user switches away from MF.
                FocusModeSelector {
                    opacity: root.currentMode === 0 ? 1.0 : 0.5
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
                        deviceName: alice ? alice.motorDeviceName : ""
                        popoverOpen: motorPopover.active
                        onClicked: root.togglePopover(motorPopover)
                    }
                    StatusBadge {
                        id: depthBadge
                        label: "Depth"; connected: alice ? alice.realSenseConnected : false
                        deviceName: alice ? alice.realSenseDeviceName : ""
                        popoverOpen: depthPopover.active
                        onClicked: root.togglePopover(depthPopover)
                    }
                    StatusBadge {
                        id: camBadge
                        label: "Cam"; connected: alice ? alice.captureCardConnected : false
                        deviceName: alice ? alice.captureCardDeviceName : ""
                        popoverOpen: camPopover.active
                        onClicked: root.togglePopover(camPopover)
                    }
                    StatusBadge {
                        id: syncBadge
                        label: "Sync"; connected: alice ? alice.syncClientConnected : false
                        isSync: true
                        popoverOpen: syncPopover.active
                        onClicked: root.togglePopover(syncPopover)
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

    // Popovers (positioned via onActiveChanged after their badge has laid out).
    // `anchorY` is the final resting position; the popover animates up 6px
    // from this point while fading in (see Theme.popoverSlideOffset).
    BadgePopover {
        id: motorPopover; title: "Motor"
        connected: alice ? alice.motorConnected : false
        deviceName: alice ? alice.motorDeviceName : ""
        deviceAddress: alice ? alice.motorDeviceAddress : ""
        connectedSinceMs: alice ? alice.motorConnectedSinceMs : 0
        lastSeenMs: alice ? alice.motorLastDisconnectMs : 0
        deviceList: alice ? alice.motorDevices : []
        anchorY: Theme.toolbarHeight
        onActiveChanged: if (active) { var pos = motorBadge.mapToItem(root.contentItem, 0, motorBadge.height + 4); x = Math.max(0, Math.min(pos.x, root.width - width)); anchorY = pos.y }
        onRestartClicked: { if (alice) alice.restartMotor() }
        onDisconnectClicked: { if (alice) { alice.disconnectMotor(); root.closeAllPopovers() } }
        onReconnectClicked: { if (alice) alice.reconnectMotor() }
        onDeviceSelected: (deviceId) => { if (alice) alice.selectMotorDevice(deviceId) }
    }
    BadgePopover {
        id: depthPopover; title: "Depth"
        connected: alice ? alice.realSenseConnected : false
        deviceName: alice ? alice.realSenseDeviceName : ""
        deviceAddress: alice ? alice.realSenseDeviceAddress : ""
        connectedSinceMs: alice ? alice.realSenseConnectedSinceMs : 0
        lastSeenMs: alice ? alice.realSenseLastDisconnectMs : 0
        deviceList: alice ? alice.realSenseDevices : []
        anchorY: Theme.toolbarHeight
        onActiveChanged: if (active) { var pos = depthBadge.mapToItem(root.contentItem, 0, depthBadge.height + 4); x = Math.max(0, Math.min(pos.x, root.width - width)); anchorY = pos.y }
        onRestartClicked: { if (alice) alice.restartDepth() }
        onDisconnectClicked: { if (alice) { alice.disconnectDepth(); root.closeAllPopovers() } }
        onReconnectClicked: { if (alice) alice.reconnectDepth() }
        onDeviceSelected: (deviceId) => { if (alice) alice.selectRealSenseDevice(deviceId) }
    }
    BadgePopover {
        id: camPopover; title: "Camera"
        connected: alice ? alice.captureCardConnected : false
        deviceName: alice ? alice.captureCardDeviceName : ""
        deviceAddress: alice ? alice.captureCardDeviceAddress : ""
        connectedSinceMs: alice ? alice.captureCardConnectedSinceMs : 0
        lastSeenMs: alice ? alice.captureCardLastDisconnectMs : 0
        deviceList: alice ? alice.captureCardDevices : []
        anchorY: Theme.toolbarHeight
        onActiveChanged: if (active) { var pos = camBadge.mapToItem(root.contentItem, 0, camBadge.height + 4); x = Math.max(0, Math.min(pos.x, root.width - width)); anchorY = pos.y }
        onRestartClicked: { if (alice) alice.restartCam() }
        onDisconnectClicked: { if (alice) { alice.disconnectCam(); root.closeAllPopovers() } }
        onReconnectClicked: { if (alice) alice.reconnectCam() }
        onDeviceSelected: (deviceId) => { if (alice) alice.selectCaptureCardDevice(deviceId) }
    }
    SyncPopover {
        id: syncPopover
        anchorY: Theme.toolbarHeight
        onActiveChanged: if (active) { var pos = syncBadge.mapToItem(root.contentItem, 0, syncBadge.height + 4); x = Math.max(0, Math.min(pos.x - width + syncBadge.width, root.width - width)); anchorY = pos.y }
    }

    // Close popovers on window resize
    onWidthChanged: closeAllPopovers()
    onHeightChanged: closeAllPopovers()

    // Click-outside handler — covers only the area BELOW the toolbar so that
    // clicking another status badge passes through to the badge (otherwise the
    // MouseArea would swallow the click and the new popover would never open).
    MouseArea {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: parent.height - Theme.toolbarHeight - 1
        z: 50
        visible: motorPopover.active || depthPopover.active || camPopover.active || syncPopover.active
        onClicked: root.closeAllPopovers()
    }

    // alice.initialize() is called in the onCompleted above
}
