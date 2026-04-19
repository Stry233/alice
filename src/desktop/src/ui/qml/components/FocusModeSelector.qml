import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

RowLayout {
    id: modeSelector
    property int currentMode: 0
    property bool enabled: true
    signal modeChanged(int mode)

    spacing: 4

    Repeater {
        model: [
            { label: "MF", mode: 0, tooltip: "Manual Focus" },
            { label: "AF-S", mode: 1, tooltip: "Single Auto Focus" },
            { label: "AF-C", mode: 2, tooltip: "Continuous Auto Focus" },
            { label: "AF-F", mode: 3, tooltip: "Face Tracking" }
        ]

        Button {
            required property var modelData
            Layout.fillWidth: true
            checkable: true
            checked: currentMode === modelData.mode
            flat: !checked
            enabled: modeSelector.enabled

            Material.background: checked ? "#6650a4" : "transparent"

            contentItem: Label {
                text: modelData.label
                color: !modeSelector.enabled ? "#555555"
                     : checked ? "#ffffff"
                     : "#a09da6"
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
                font: parent.font
            }

            ToolTip.text: modelData.tooltip
            ToolTip.visible: hovered
            ToolTip.delay: 500

            onClicked: modeSelector.modeChanged(modelData.mode)
        }
    }
}
