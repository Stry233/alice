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
    property string uptime: ""

    signal reconnectClicked()
    signal disconnectClicked()
    signal restartClicked()

    visible: false
    width: Theme.popoverWidth
    implicitHeight: col.implicitHeight + 20
    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    z: 100

    ColumnLayout {
        id: col
        anchors.fill: parent
        anchors.margins: 10
        spacing: 10

        RowLayout {
            Layout.fillWidth: true
            Text { text: popover.title; font.family: Theme.fontFamily; font.pixelSize: 12; font.weight: Font.DemiBold; color: Theme.textPrimary; Layout.fillWidth: true }
            Rectangle {
                width: statusRow.implicitWidth + 10; height: 18; radius: Theme.radiusSm
                color: connected ? Theme.successMuted : Theme.dangerMuted
                border.width: 1; border.color: connected ? Theme.success : Qt.rgba(0.86, 0.22, 0.22, 0.4)
                RowLayout {
                    id: statusRow; anchors.centerIn: parent; spacing: 4
                    Rectangle { width: 5; height: 5; radius: 3; color: connected ? Theme.success : Theme.danger }
                    Text { text: popover.statusText; font.family: Theme.fontFamily; font.pixelSize: 9; font.weight: Font.DemiBold; color: connected ? Theme.success : Theme.dangerText }
                }
            }
        }

        GridLayout {
            Layout.fillWidth: true
            columns: 2; columnSpacing: 10; rowSpacing: 4
            Text { text: "Device"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: popover.deviceName || "—"; color: connected ? Theme.textPrimary : Theme.textDisabled; font.pixelSize: 10 }
            Text { text: connected ? "Address" : "Last seen"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: connected ? popover.deviceAddress : popover.uptime; color: connected ? Theme.primary : Theme.textDisabled; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
            Text { visible: connected; text: "Uptime"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { visible: connected; text: popover.uptime; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
        }

        RowLayout {
            Layout.fillWidth: true; spacing: 4; visible: connected
            Rectangle {
                Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
                color: Theme.elevated; border.width: 1; border.color: Theme.border
                Text { anchors.centerIn: parent; text: "Restart"; font.pixelSize: 10; color: Theme.textPrimary }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.restartClicked() }
            }
            Rectangle {
                Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
                color: Theme.dangerMuted; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
                Text { anchors.centerIn: parent; text: "Disconnect"; font.pixelSize: 10; color: Theme.dangerText }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.disconnectClicked() }
            }
        }

        Rectangle {
            Layout.fillWidth: true; height: 22; radius: Theme.radiusSm; visible: !connected
            color: Theme.primaryMuted; border.width: 1; border.color: Qt.rgba(0.17, 0.58, 0.84, 0.4)
            Text { anchors.centerIn: parent; text: "Reconnect"; font.pixelSize: 10; color: Theme.primaryHover }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: popover.reconnectClicked() }
        }
    }

    function toggle() { visible = !visible }
}
