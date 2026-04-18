import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Alice.UI

// OPS / CFG grouped-button toggle. Each button measures its own
// content via Layout.preferredWidth; outer width follows RowLayout's
// implicitWidth so fractional zoom scales don't clip the label.
Rectangle {
    id: toggle
    property int currentMode: 0
    signal modeChanged(int mode)

    readonly property int minButtonWidth: Theme.dp(78)
    readonly property int buttonHPad: Theme.dp(16)

    // +2 accounts for the 1 px border on each side.
    implicitWidth: inner.implicitWidth + 2
    implicitHeight: Theme.dp(42)
    width: implicitWidth
    height: implicitHeight

    radius: Theme.radiusSm
    color: "transparent"
    border.width: 1
    border.color: Theme.border

    RowLayout {
        id: inner
        anchors.fill: parent
        anchors.margins: 1
        spacing: 0

        component ModeButton: Rectangle {
            id: btn
            required property int modeIndex
            required property string label
            required property string tooltip
            property bool isHovered: false
            readonly property bool isActive: toggle.currentMode === modeIndex

            Layout.preferredWidth: Math.max(toggle.minButtonWidth,
                                            txt.implicitWidth + toggle.buttonHPad * 2)
            Layout.fillHeight: true
            radius: Theme.radiusSm
            color: isActive
                ? Theme.primary
                : (isHovered ? Theme.surfaceHover : "transparent")
            Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }

            Text {
                id: txt
                anchors.centerIn: parent
                text: btn.label
                font.family: Theme.fontFamily
                font.pixelSize: Theme.fontSizeSmall
                font.weight: btn.isActive ? Font.DemiBold : Font.Normal
                color: btn.isActive ? "#ffffff" : Theme.textSecondary
                Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            }
            MouseArea {
                anchors.fill: parent
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onEntered: btn.isHovered = true
                onExited: btn.isHovered = false
                onClicked: toggle.modeChanged(btn.modeIndex)
            }

            ToolTip.text: btn.tooltip
            ToolTip.visible: btn.isHovered
            ToolTip.delay: 300
        }

        ModeButton {
            modeIndex: 0
            label: "OPS"
            tooltip: "Live camera & autofocus"
        }

        Rectangle {
            Layout.preferredWidth: 1
            Layout.fillHeight: true
            color: Theme.border
        }

        ModeButton {
            modeIndex: 1
            label: "CFG"
            tooltip: "Calibration, settings & sync"
        }
    }
}
