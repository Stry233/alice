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

    width: row.implicitWidth + 16
    height: 28

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
        spacing: 6
        Rectangle {
            width: 6; height: 6; radius: 3
            color: connected ? Theme.success : Theme.danger
            SequentialAnimation on opacity {
                running: !connected && badge.isSync
                loops: Animation.Infinite
                NumberAnimation { to: 0.4; duration: 800 }
                NumberAnimation { to: 1.0; duration: 800 }
            }
        }
        Text {
            text: badge.label
            font.family: Theme.fontFamily
            font.pixelSize: Theme.fontSizeCaption
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
