import QtQuick
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
            width: Theme.dp(78)
            height: parent.height
            radius: Theme.radiusSm
            color: toggle.currentMode === 0 ? Theme.primary : "transparent"
            Rectangle { visible: toggle.currentMode === 0; anchors.right: parent.right; width: Theme.radiusSm; height: parent.height; color: Theme.primary }

            Text {
                id: opsLabel; anchors.centerIn: parent; text: "OPS"
                font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall
                font.weight: toggle.currentMode === 0 ? Font.DemiBold : Font.Normal
                color: toggle.currentMode === 0 ? "#ffffff" : Theme.textSecondary
            }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toggle.modeChanged(0) }
        }

        Rectangle { width: 1; height: parent.height; color: Theme.border }

        Rectangle {
            id: btnCfg
            width: Theme.dp(78)
            height: parent.height
            radius: Theme.radiusSm
            color: toggle.currentMode === 1 ? Theme.primary : "transparent"
            Rectangle { visible: toggle.currentMode === 1; anchors.left: parent.left; width: Theme.radiusSm; height: parent.height; color: Theme.primary }

            Text {
                id: cfgLabel; anchors.centerIn: parent; text: "CFG"
                font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall
                font.weight: toggle.currentMode === 1 ? Font.DemiBold : Font.Normal
                color: toggle.currentMode === 1 ? "#ffffff" : Theme.textSecondary
            }
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toggle.modeChanged(1) }
        }
    }
}
