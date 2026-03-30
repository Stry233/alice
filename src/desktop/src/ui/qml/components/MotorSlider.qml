import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

ColumnLayout {
    id: motorSlider
    property int motorPos: 0
    signal motorMoved(int pos)

    spacing: 4

    Slider {
        id: slider
        Layout.fillWidth: true
        from: 0
        to: 4095
        stepSize: 1
        value: motorSlider.motorPos
        live: true
        Material.accent: Theme.primary

        onMoved: motorSlider.motorMoved(Math.round(value))

        // Mouse wheel for fine adjustment
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

        Label {
            text: "0"
            font.pixelSize: 10
            color: Theme.textSecondary
        }
        Item { Layout.fillWidth: true }
        Label {
            text: Math.round(slider.value).toString()
            font.family: Theme.fontFamilyMono
            font.pixelSize: 13
            font.weight: Font.Bold
            color: Theme.primary
        }
        Item { Layout.fillWidth: true }
        Label {
            text: "4095"
            font.pixelSize: 10
            color: Theme.textSecondary
        }
    }

    // Quick preset buttons
    RowLayout {
        Layout.fillWidth: true
        spacing: 4

        Repeater {
            model: [
                { label: "1", pos: 0 },
                { label: "2", pos: 1024 },
                { label: "3", pos: 2048 },
                { label: "4", pos: 3072 },
                { label: "5", pos: 4095 }
            ]
            Button {
                required property var modelData
                text: modelData.label
                Layout.fillWidth: true
                flat: true
                font.pixelSize: 11
                implicitHeight: 28
                onClicked: {
                    slider.value = modelData.pos
                    motorSlider.motorMoved(modelData.pos)
                }
                ToolTip.text: "Position " + modelData.pos
                ToolTip.visible: hovered
            }
        }
    }
}
