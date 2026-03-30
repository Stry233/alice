import QtQuick
import Alice.UI

Row {
    id: toggle
    property int currentMode: 0
    signal modeChanged(int mode)
    spacing: 0

    Repeater {
        model: [
            { label: "OPS", mode: 0 },
            { label: "CFG", mode: 1 }
        ]

        Rectangle {
            required property var modelData
            required property int index
            width: 40; height: 24
            color: toggle.currentMode === modelData.mode ? Theme.primary : "transparent"
            border.width: 1
            border.color: toggle.currentMode === modelData.mode ? Theme.primary : Theme.border
            radius: 0

            Text {
                anchors.centerIn: parent
                text: modelData.label
                font.family: Theme.fontFamily
                font.pixelSize: Theme.fontSizeCaption
                font.weight: toggle.currentMode === modelData.mode ? Font.DemiBold : Font.Normal
                color: toggle.currentMode === modelData.mode ? "#ffffff" : Theme.textSecondary
            }

            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onClicked: toggle.modeChanged(modelData.mode)
            }
        }
    }
}
