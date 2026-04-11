import QtQuick
import QtQuick.Controls
import Alice.UI

Rectangle {
    id: toggle
    property int currentMode: 0
    signal modeChanged(int mode)

    width: Theme.dp(78) * 2 + 3
    height: Theme.dp(42)
    radius: Theme.radiusSm
    color: "transparent"
    border.width: 1
    border.color: Theme.border

    Row {
        anchors.fill: parent
        anchors.margins: 1

        Rectangle {
            id: btnOps
            // Latched hover state — reading MouseArea.containsMouse directly in
            // a binding was flaky (brief hover flash then revert) because the
            // active-mode decoration overlay was briefly intercepting enter/exit
            // pairs on some frames. onEntered/onExited latch the hover bit so
            // the colour binding sees a stable signal.
            property bool isHovered: false

            width: Theme.dp(78)
            height: parent.height
            radius: Theme.radiusSm
            color: toggle.currentMode === 0
                ? Theme.primary
                : (isHovered ? Theme.surfaceHover : "transparent")
            Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            Rectangle { visible: toggle.currentMode === 0; anchors.right: parent.right; width: Theme.radiusSm; height: parent.height; color: Theme.primary }

            Text {
                id: opsLabel; anchors.centerIn: parent; text: "OPS"
                font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall
                font.weight: toggle.currentMode === 0 ? Font.DemiBold : Font.Normal
                color: toggle.currentMode === 0 ? "#ffffff" : Theme.textSecondary
                Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            }
            MouseArea {
                id: opsMa
                anchors.fill: parent
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onEntered: btnOps.isHovered = true
                onExited: btnOps.isHovered = false
                onClicked: toggle.modeChanged(0)
            }

            ToolTip.text: "Live camera & autofocus"
            ToolTip.visible: btnOps.isHovered
            ToolTip.delay: 300
        }

        Rectangle { width: 1; height: parent.height; color: Theme.border }

        Rectangle {
            id: btnCfg
            property bool isHovered: false

            width: Theme.dp(78)
            height: parent.height
            radius: Theme.radiusSm
            color: toggle.currentMode === 1
                ? Theme.primary
                : (isHovered ? Theme.surfaceHover : "transparent")
            Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            Rectangle { visible: toggle.currentMode === 1; anchors.left: parent.left; width: Theme.radiusSm; height: parent.height; color: Theme.primary }

            Text {
                id: cfgLabel; anchors.centerIn: parent; text: "CFG"
                font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall
                font.weight: toggle.currentMode === 1 ? Font.DemiBold : Font.Normal
                color: toggle.currentMode === 1 ? "#ffffff" : Theme.textSecondary
                Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            }
            MouseArea {
                id: cfgMa
                anchors.fill: parent
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onEntered: btnCfg.isHovered = true
                onExited: btnCfg.isHovered = false
                onClicked: toggle.modeChanged(1)
            }

            ToolTip.text: "Calibration, settings & sync"
            ToolTip.visible: btnCfg.isHovered
            ToolTip.delay: 300
        }
    }
}
