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
    // Set by the parent when this badge's popover is currently open — drives
    // the "pressed" micro-state (5% darker fill, brighter border) so the user
    // can tell which trigger the popover belongs to. See DRD §A.
    property bool popoverOpen: false
    signal clicked()

    width: row.implicitWidth + Theme.dp(32)
    height: Theme.dp(38)

    Rectangle {
        id: bg
        anchors.fill: parent
        radius: Theme.radiusSm
        // Depend on `connected`, `mouseArea.containsMouse`, and `popoverOpen`
        // inside a single binding so all three state inputs reach the color
        // through a reactive channel (imperative assignment would break it).
        //
        //   Default                 →  surface tone for this state
        //   Hover (no popover open) →  +15% lighter fill
        //   Popover open            →  ~8% darker fill, same tone family —
        //                              communicates "I am the trigger"
        color: {
            if (connected) {
                if (popoverOpen) return Qt.darker(Theme.successMuted, 1.08)
                if (mouseArea.containsMouse) return Qt.lighter(Theme.successMuted, 1.15)
                return Theme.successMuted
            }
            if (popoverOpen) return Qt.darker(Theme.elevated, 1.08)
            if (mouseArea.containsMouse) return Theme.surfaceHover
            return Theme.elevated
        }
        border.color: connected
            ? (popoverOpen ? Theme.primary : Theme.success)
            : (popoverOpen ? Theme.primary : Theme.border)
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
        id: mouseArea
        anchors.fill: parent
        cursorShape: Qt.PointingHandCursor
        hoverEnabled: true
        onClicked: badge.clicked()
    }
}
