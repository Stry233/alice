import QtQuick
import Alice.UI
import QtQuick.Layouts

Item {
    id: badge
    property string label: ""
    property bool connected: false
    property string deviceName: ""
    property string deviceInfo: ""
    property string uptime: ""
    property bool isSync: false
    signal clicked()

    width: row.implicitWidth + Theme.dp(32)
    height: Theme.dp(38)

    Rectangle {
        id: bg
        anchors.fill: parent
        radius: Theme.radiusSm
        color: connected ? Theme.successMuted : Theme.elevated
        border.color: connected ? Theme.success : Theme.border
        border.width: 1
        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Theme.easingEnter } }
        Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Theme.easingEnter } }
    }

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: Theme.dp(8)
        Rectangle {
            width: Theme.dp(12); height: Theme.dp(12); radius: Theme.dp(6)
            color: connected ? Theme.success : Theme.danger
            SequentialAnimation on opacity {
                running: !connected && badge.isSync && alice && alice.syncServerRunning
                loops: Animation.Infinite
                NumberAnimation { to: 0.4; duration: 800 }
                NumberAnimation { to: 1.0; duration: 800 }
            }
        }
        Text {
            text: badge.label
            font.family: Theme.fontFamily
            font.pixelSize: Theme.fontSizeSmall
            color: connected ? Theme.textPrimary : Theme.textSecondary
        }
    }

    MouseArea {
        anchors.fill: parent
        cursorShape: Qt.PointingHandCursor
        hoverEnabled: true
        onClicked: badge.clicked()
        onEntered: bg.color = connected ? Qt.lighter(Theme.successMuted, 1.15) : Theme.surfaceHover
        onExited: bg.color = connected ? Theme.successMuted : Theme.elevated
    }
}
