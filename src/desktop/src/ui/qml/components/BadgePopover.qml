import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    id: popover
    property string title: ""
    property bool connected: false
    property string statusText: connected ? "Connected" : "Offline"
    property string deviceName: ""
    property string deviceAddress: ""
    property string uptime: connected ? "—" : "—"

    signal reconnectClicked()
    signal disconnectClicked()
    signal restartClicked()

    visible: false
    opacity: visible ? 1.0 : 0.0
    // Adaptive width
    width: Math.max(Theme.popoverWidth, col.implicitWidth + Theme.dp(48))
    implicitHeight: col.implicitHeight + Theme.dp(40)
    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    z: 100

    Behavior on opacity { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

    ColumnLayout {
        id: col
        anchors.fill: parent
        anchors.margins: Theme.dp(20)
        spacing: Theme.dp(16)

        // Header: title + status chip
        RowLayout {
            Layout.fillWidth: true
            spacing: Theme.dp(12)
            Text {
                text: popover.title
                font.family: Theme.fontFamily
                font.pixelSize: Theme.fontSizeSmall  // 20px at 200% (HTML 12px→24, but matching toolbar)
                font.weight: Font.DemiBold
                color: Theme.textPrimary
                Layout.fillWidth: true
            }
            // Status chip — sized to always fit either "Connected" or "Offline"
            Rectangle {
                // Measure both texts and use the wider one
                property real chipTextW: Math.max(connectedMeasure.implicitWidth, offlineMeasure.implicitWidth)
                Text { id: connectedMeasure; text: "Connected"; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeMicro; font.weight: Font.DemiBold; visible: false }
                Text { id: offlineMeasure; text: "Offline"; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeMicro; font.weight: Font.DemiBold; visible: false }

                width: Theme.dp(10) + Theme.dp(6) + chipTextW + Theme.dp(24)
                height: Theme.dp(28); radius: Theme.radiusSm
                color: connected ? Theme.successMuted : Theme.dangerMuted
                border.width: 1
                border.color: connected ? Theme.success : Qt.rgba(0.86, 0.22, 0.22, 0.4)
                Row {
                    anchors.centerIn: parent
                    spacing: Theme.dp(6)
                    Rectangle {
                        width: Theme.dp(10); height: Theme.dp(10); radius: Theme.dp(5)
                        color: connected ? Theme.success : Theme.danger
                        anchors.verticalCenter: parent.verticalCenter
                    }
                    Text {
                        text: popover.statusText
                        font.family: Theme.fontFamily
                        font.pixelSize: Theme.fontSizeMicro
                        font.weight: Font.DemiBold
                        color: connected ? Theme.success : Theme.dangerText
                    }
                }
            }
        }

        // Data grid
        GridLayout {
            Layout.fillWidth: true
            columns: 2
            columnSpacing: Theme.dp(16)
            rowSpacing: Theme.dp(8)

            Text { text: "Device"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeMicro; font.family: Theme.fontFamily }
            Text { text: popover.deviceName || "—"; color: connected ? Theme.textPrimary : Theme.textDisabled; font.pixelSize: Theme.fontSizeMicro }

            Text { text: connected ? "Address" : "Last seen"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeMicro; font.family: Theme.fontFamily }
            Text { text: connected ? (popover.deviceAddress || "—") : (popover.uptime || "—"); color: connected ? Theme.primary : Theme.textDisabled; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro }

            Text { visible: connected; text: "Uptime"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeMicro; font.family: Theme.fontFamily }
            Text { visible: connected; text: popover.uptime || "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro }
        }

        // Buttons (connected)
        RowLayout {
            Layout.fillWidth: true; spacing: Theme.dp(8)
            visible: connected
            Rectangle {
                Layout.fillWidth: true; height: Theme.dp(30); radius: Theme.radiusSm
                color: Theme.elevated; border.width: 1; border.color: Theme.border
                Text { anchors.centerIn: parent; text: "Restart"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textPrimary }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.restartClicked() }
            }
            Rectangle {
                Layout.fillWidth: true; height: Theme.dp(30); radius: Theme.radiusSm
                color: Theme.dangerMuted; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
                Text { anchors.centerIn: parent; text: "Disconnect"; font.pixelSize: Theme.fontSizeMicro; color: Theme.dangerText }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.disconnectClicked() }
            }
        }

        // Button (disconnected)
        Rectangle {
            Layout.fillWidth: true; height: Theme.dp(30); radius: Theme.radiusSm; visible: !connected
            color: Theme.primaryMuted; border.width: 1; border.color: Qt.rgba(0.17, 0.58, 0.84, 0.4)
            Text { anchors.centerIn: parent; text: "Reconnect"; font.pixelSize: Theme.fontSizeMicro; color: Theme.primaryHover }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.reconnectClicked() }
        }
    }

    function toggle() { visible = !visible }
}
