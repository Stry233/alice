import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

ColumnLayout {
    id: motorSlider
    property int motorPos: 0
    signal motorMoved(int pos)

    spacing: 0

    AliceSlider {
        id: slider
        Layout.fillWidth: true
        from: 0
        to: 4095
        stepSize: 1
        live: true

        // Re-establish the binding whenever the user isn't actively
        // dragging. Without this, any imperative write to slider.value
        // (preset click, scroll wheel) breaks the reactive binding to
        // motorSlider.motorPos, so subsequent motor moves from AF-C,
        // AF-F, or remote sync would no longer update the slider.
        Binding {
            target: slider
            property: "value"
            value: motorSlider.motorPos
            when: !slider.pressed
        }

        onMoved: motorSlider.motorMoved(Math.round(value))

        MouseArea {
            anchors.fill: parent
            acceptedButtons: Qt.NoButton
            onWheel: (wheel) => {
                var delta = wheel.angleDelta.y > 0 ? 10 : -10
                var newVal = Math.max(0, Math.min(4095, slider.value + delta))
                slider.value = newVal
                motorSlider.motorMoved(newVal)
            }
        }
    }

    RowLayout {
        Layout.fillWidth: true
        Layout.topMargin: Theme.dp(2)

        Label {
            text: "0"
            font.pixelSize: Theme.dp(18)
            color: Theme.textSecondary
        }
        Item { Layout.fillWidth: true }
        Label {
            text: Math.round(slider.value).toString()
            font.family: Theme.fontFamilyMono
            font.pixelSize: Theme.dp(24)
            font.weight: Font.Bold
            color: Theme.primary
        }
        Item { Layout.fillWidth: true }
        Label {
            text: "4095"
            font.pixelSize: Theme.dp(18)
            color: Theme.textSecondary
        }
    }

    Item {
        Layout.fillWidth: true
        implicitHeight: btnRow.height
        Layout.topMargin: Theme.dp(12)

        Row {
            id: btnRow
            anchors.horizontalCenter: parent.horizontalCenter
            width: parent.width
            spacing: Theme.dp(4)

            Repeater {
                model: [
                    { label: "1", pos: 0 },
                    { label: "2", pos: 1024 },
                    { label: "3", pos: 2048 },
                    { label: "4", pos: 3072 },
                    { label: "5", pos: 4095 }
                ]
                Rectangle {
                    id: presetBtn
                    required property var modelData
                    required property int index
                    width: (btnRow.width - 4 * btnRow.spacing) / 5
                    height: Theme.dp(38); radius: Theme.radiusSm
                    opacity: motorSlider.enabled ? 1.0 : 0.4
                    color: {
                        if (!motorSlider.enabled) return Theme.surface
                        if (presetMa.pressed) return Theme.surfaceActive
                        if (presetMa.containsMouse) return Theme.surfaceHover
                        return Theme.surface
                    }
                    border.width: 1
                    border.color: (motorSlider.enabled && presetMa.containsMouse) ? Theme.borderStrong : Theme.border
                    Behavior on opacity { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                    Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                    Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }

                    Text {
                        anchors.centerIn: parent
                        text: modelData.label
                        font.family: Theme.fontFamily
                        font.pixelSize: Theme.dp(18)
                        color: motorSlider.enabled ? Theme.textSecondary : Theme.textDisabled
                    }

                    MouseArea {
                        id: presetMa
                        anchors.fill: parent
                        enabled: motorSlider.enabled
                        cursorShape: motorSlider.enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
                        hoverEnabled: motorSlider.enabled
                        onClicked: {
                            slider.value = modelData.pos
                            motorSlider.motorMoved(modelData.pos)
                        }
                    }

                    ToolTip.text: motorSlider.enabled ? ("Position " + modelData.pos) : "Motor not connected"
                    ToolTip.visible: presetMa.containsMouse
                    ToolTip.delay: 300
                }
            }
        }
    }
}
