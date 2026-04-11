import QtQuick
import QtQuick.Layouts
import Alice.UI
import Alice.Renderers 1.0

Rectangle {
    id: popover

    // Origin-slide presentation (see BadgePopover.qml for details).
    property bool active: false
    property real anchorY: 48

    visible: opacity > 0.01
    opacity: active ? 1.0 : 0.0
    y: anchorY - (active ? 0 : Theme.popoverSlideOffset)
    width: Theme.syncPopoverWidth
    implicitHeight: col.implicitHeight + 40
    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    z: 100

    Behavior on opacity { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
    Behavior on y { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

    property bool connected: alice ? alice.syncClientConnected : false
    property string serverAddress: {
        if (!alice) return ""
        var payload = alice.syncQrPayload
        if (!payload) return ""
        if (typeof payload === "string" && payload.indexOf("{") === 0) {
            try {
                var obj = JSON.parse(payload)
                return (obj.ip || obj.host || "") + ":" + (obj.port || "")
            } catch(e) { return payload }
        }
        return payload.toString()
    }

    onActiveChanged: {
        if (active && alice && !alice.syncServerRunning)
            alice.startSyncServer()
    }

    ColumnLayout {
        id: col
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.margins: 20
        spacing: 12

        // Connected state
        RowLayout {
            visible: connected; Layout.fillWidth: true
            Text { text: "Sync"; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeBody; font.weight: Font.DemiBold; color: Theme.textPrimary; Layout.fillWidth: true }
            Rectangle {
                width: Math.max(linkedRow.implicitWidth + 20, 70); height: 28; radius: Theme.radiusSm
                color: Theme.successMuted; border.width: 1; border.color: Theme.success
                Row { id: linkedRow; anchors.centerIn: parent; spacing: 6
                    Rectangle { width: 8; height: 8; radius: 4; color: Theme.success; anchors.verticalCenter: parent.verticalCenter }
                    Text { text: "Linked"; font.pixelSize: Theme.fontSizeMicro; font.weight: Font.DemiBold; color: Theme.success }
                }
            }
        }

        GridLayout {
            visible: connected; columns: 2; columnSpacing: 16; rowSpacing: 8; Layout.fillWidth: true
            Text { text: "Client"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
            Text { text: "Android"; color: Theme.textPrimary; font.pixelSize: Theme.fontSizeSmall }
            Text { text: "IP"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
            Text { text: popover.serverAddress; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }
        }

        Rectangle {
            visible: connected; Layout.fillWidth: true; height: 32; radius: Theme.radiusSm
            color: Theme.dangerMuted; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
            Text { anchors.centerIn: parent; text: "Disconnect"; font.pixelSize: Theme.fontSizeSmall; color: Theme.dangerText }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: { if (alice) alice.stopSyncServer() } }
        }

        // Disconnected state: QR code
        Item {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            Layout.fillWidth: true; Layout.preferredHeight: 160

            Rectangle {
                anchors.centerIn: parent; width: 150; height: 150; color: "#ffffff"; radius: Theme.radiusSm
                VideoRenderer {
                    anchors.centerIn: parent; width: 140; height: 140
                    source: alice.qrCodeImage
                }
            }
        }

        Text {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            text: popover.serverAddress
            font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary
            Layout.alignment: Qt.AlignHCenter
        }

        Text {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            text: "Scan with Alice Android"
            font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
            Layout.alignment: Qt.AlignHCenter
        }

        // Waiting bubble
        Rectangle {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            Layout.fillWidth: true; height: Theme.dp(28); radius: Theme.radiusSm
            color: Theme.warningMuted; border.width: 1; border.color: Qt.rgba(0.85, 0.51, 0.17, 0.3)
            Row {
                anchors.centerIn: parent; spacing: Theme.dp(6)
                Rectangle {
                    width: Theme.dp(8); height: Theme.dp(8); radius: Theme.dp(4); color: Theme.warning
                    anchors.verticalCenter: parent.verticalCenter
                    SequentialAnimation on opacity { loops: Animation.Infinite; NumberAnimation { to: 0.4; duration: 800 } NumberAnimation { to: 1.0; duration: 800 } }
                }
                Text { text: "Waiting..."; font.pixelSize: Theme.fontSizeMicro; color: Theme.warning; anchors.verticalCenter: parent.verticalCenter }
            }
        }
    }

    function toggle() { active = !active }
}
