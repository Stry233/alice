import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Layouts

Row {
    id: modeSelector
    property int currentMode: 0
    property bool enabled: true
    signal modeChanged(int mode)

    spacing: Theme.dp(4)  // HTML gap:2px at 200% = 4

    Repeater {
        model: [
            { label: "MF", mode: 0, tooltip: "Manual Focus" },
            { label: "AF-S", mode: 1, tooltip: "Single Auto Focus" },
            { label: "AF-C", mode: 2, tooltip: "Continuous Auto Focus" },
            { label: "AF-F", mode: 3, tooltip: "Face Tracking" }
        ]

        Rectangle {
            required property var modelData

            property bool isActive: modeSelector.currentMode === modelData.mode
            property bool isHovered: focusMa.containsMouse && !isActive

            width: focusLabel.implicitWidth + Theme.dp(40); height: Theme.dp(42); radius: Theme.radiusSm
            color: isActive ? Theme.primary : (isHovered ? Theme.surfaceHover : Theme.surface)
            border.width: isActive ? 0 : 1
            border.color: Theme.border

            Behavior on color { ColorAnimation { duration: Theme.durationFast } }

            Text {
                id: focusLabel
                anchors.centerIn: parent
                text: modelData.label
                font.family: Theme.fontFamily
                font.pixelSize: Theme.fontSizeSmall
                font.weight: isActive ? Font.DemiBold : Font.Normal
                color: !modeSelector.enabled ? Theme.textDisabled
                     : isActive ? "#ffffff"
                     : Theme.textSecondary
            }

            MouseArea {
                id: focusMa
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                enabled: modeSelector.enabled
                hoverEnabled: true
                onClicked: modeSelector.modeChanged(modelData.mode)
            }

            ToolTip.text: modelData.tooltip
            ToolTip.visible: focusMa.containsMouse
            ToolTip.delay: 300
        }
    }
}
