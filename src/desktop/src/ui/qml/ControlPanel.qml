import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import QtQuick.Dialogs

Rectangle {
    color: "#2b2930"

    FileDialog {
        id: fileDialog
        title: "Load Calibration Mapping"
        nameFilters: ["JSON files (*.json)", "All files (*)"]
        onAccepted: {
            if (!alice) return;
            alice.loadMappingFromFile(selectedFile)
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 16

        // Focus Mode selector
        Label {
            text: "FOCUS MODE"
            font.pixelSize: 11
            font.weight: Font.Bold
            font.letterSpacing: 1.5
            color: "#a09da6"
        }

        FocusModeSelector {
            Layout.fillWidth: true
            currentMode: alice ? alice.focusMode : 0
            enabled: alice ? alice.hasMapping : false
            onModeChanged: (mode) => { if (!alice) return; alice.focusMode = mode }
        }

        // Autofocus enable toggle
        RowLayout {
            Layout.fillWidth: true
            Label {
                text: "Autofocus"
                color: "#e6e1e5"
                Layout.fillWidth: true
            }
            Switch {
                checked: alice ? alice.autofocusEnabled : false
                onToggled: { if (!alice) return; alice.autofocusEnabled = checked }
                enabled: alice ? alice.hasMapping : false
                Material.accent: "#d0bcff"
            }
        }

        // Active indicator
        Rectangle {
            Layout.fillWidth: true
            height: 4
            radius: 2
            color: alice && alice.activelyFocusing ? "#64ff64" : "#3b383e"

            Behavior on color { ColorAnimation { duration: 200 } }
        }

        // Separator
        Rectangle { Layout.fillWidth: true; height: 1; color: "#3b383e" }

        // Motor control
        Label {
            text: "MOTOR POSITION"
            font.pixelSize: 11
            font.weight: Font.Bold
            font.letterSpacing: 1.5
            color: "#a09da6"
        }

        MotorSlider {
            Layout.fillWidth: true
            motorPos: alice ? alice.motorPosition : 0
            enabled: alice ? alice.motorConnected : false
            onMotorMoved: (pos) => {
                if (!alice) return;
                alice.focusMode = 0;  // Switch to MF
                alice.setMotorPosition(pos)
            }
        }

        // Separator
        Rectangle { Layout.fillWidth: true; height: 1; color: "#3b383e" }

        // Mapping controls
        Label {
            text: "CALIBRATION"
            font.pixelSize: 11
            font.weight: Font.Bold
            font.letterSpacing: 1.5
            color: "#a09da6"
        }

        // Active mapping indicator
        Label {
            visible: alice ? alice.hasMapping : false
            text: alice ? ("Active: " + alice.mappingName) : ""
            font.pixelSize: 12
            color: "#64ff64"
            Layout.fillWidth: true
            elide: Text.ElideRight
        }

        ComboBox {
            id: presetCombo
            Layout.fillWidth: true
            model: ["Select Preset...", "Linear", "Logarithmic", "Portrait", "Landscape", "Macro"]
            currentIndex: 0
            Material.accent: "#d0bcff"
            onActivated: (index) => {
                if (!alice) return;
                if (index > 0) {
                    alice.loadPreset(index - 1)
                }
            }
        }

        Button {
            text: "Load from File..."
            Layout.fillWidth: true
            flat: true
            onClicked: fileDialog.open()
        }

        Button {
            text: "Clear Mapping"
            Layout.fillWidth: true
            flat: true
            enabled: alice ? alice.hasMapping : false
            Material.foreground: "#f2b8b5"
            onClicked: { if (!alice) return; alice.clearMapping() }
        }

        // Spacer
        Item { Layout.fillHeight: true }
    }
}
