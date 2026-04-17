import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Alice.UI

// OPS / CFG grouped-button toggle.
//
// Previously sized with a hand-computed `Theme.dp(78) * 2 + 3` and a
// fixed per-button width — which drifted by 1 px at fractional scales
// and could clip the label when the user's Ctrl+Plus zoom pushed the
// text past dp(78). Now each button measures its own content via
// Layout.preferredWidth and the outer rectangle follows RowLayout's
// implicitWidth. No magic constants survive the scale change.
Rectangle {
    id: toggle
    property int currentMode: 0
    signal modeChanged(int mode)

    // Minimum per-button width: enough for a comfortable "OPS" tap
    // target. Buttons grow beyond this if the rendered text plus
    // padding is wider (never clips).
    readonly property int minButtonWidth: Theme.dp(78)
    readonly property int buttonHPad: Theme.dp(16)

    // Outer width follows the inner RowLayout plus the 1 px border on
    // each side (anchors.margins: 1 below).
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

        // Internal mini-component: one of the two toggle pills. Both
        // sides share the same geometry math so a change to padding or
        // min-width only has to be made once.
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

        // 1 px vertical separator — sits inside the RowLayout so it
        // counts toward implicitWidth.
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
