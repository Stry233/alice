import QtQuick
import QtQuick.Layouts
import Alice.UI
import Alice.Renderers 1.0

Rectangle {
    id: popover
    visible: false
    width: Theme.syncPopoverWidth
    implicitHeight: col.implicitHeight + 20
    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    z: 100

    property bool connected: alice ? alice.syncClientConnected : false

    onVisibleChanged: {
        if (visible && alice && !alice.syncServerRunning)
            alice.startSyncServer()
    }

    ColumnLayout {
        id: col
        anchors.fill: parent
        anchors.margins: 10
        spacing: 8

        RowLayout {
            visible: connected; Layout.fillWidth: true
            Text { text: "Sync"; font.family: Theme.fontFamily; font.pixelSize: 12; font.weight: Font.DemiBold; color: Theme.textPrimary; Layout.fillWidth: true }
            Rectangle {
                width: linkedRow.implicitWidth + 10; height: 18; radius: Theme.radiusSm
                color: Theme.successMuted; border.width: 1; border.color: Theme.success
                RowLayout { id: linkedRow; anchors.centerIn: parent; spacing: 4
                    Rectangle { width: 5; height: 5; radius: 3; color: Theme.success }
                    Text { text: "Linked"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.success }
                }
            }
        }

        GridLayout {
            visible: connected; columns: 2; columnSpacing: 10; rowSpacing: 4; Layout.fillWidth: true
            Text { text: "Client"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: "Android"; color: Theme.textPrimary; font.pixelSize: 10 }
            Text { text: "IP"; color: Theme.textSecondary; font.pixelSize: 10 }
            Text { text: alice ? alice.syncQrPayload : ""; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
        }

        Rectangle {
            visible: connected; Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
            color: Theme.dangerMuted; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
            Text { anchors.centerIn: parent; text: "Disconnect"; font.pixelSize: 10; color: Theme.dangerText }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: { if (alice) alice.stopSyncServer() } }
        }

        VideoRenderer {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            Layout.alignment: Qt.AlignHCenter; width: 120; height: 120
            source: alice ? alice.qrCodeImage : null
            Rectangle { anchors.fill: parent; color: "#ffffff"; z: -1; radius: Theme.radiusSm }
        }

        Text {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            text: alice ? alice.syncQrPayload : ""
            font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary; Layout.alignment: Qt.AlignHCenter
        }

        Text {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            text: "Scan with Alice Android"
            font.pixelSize: 9; color: Theme.textDisabled; Layout.alignment: Qt.AlignHCenter
        }

        Rectangle {
            visible: !connected && (alice ? alice.syncServerRunning : false)
            Layout.fillWidth: true; height: 22; radius: Theme.radiusSm
            color: Theme.warningMuted; border.width: 1; border.color: Qt.rgba(0.85, 0.51, 0.17, 0.3)
            Row {
                anchors.centerIn: parent; spacing: 5
                Rectangle {
                    width: 5; height: 5; radius: 3; color: Theme.warning
                    SequentialAnimation on opacity { loops: Animation.Infinite; NumberAnimation { to: 0.4; duration: 800 }; NumberAnimation { to: 1.0; duration: 800 } }
                }
                Text { text: "Waiting..."; font.pixelSize: 9; color: Theme.warning }
            }
        }
    }

    function toggle() { visible = !visible }
}
