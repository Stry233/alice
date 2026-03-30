import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

ScrollView {
    id: settingsView

    GridLayout {
        width: settingsView.width - 32
        anchors.margins: 16
        columns: 3
        columnSpacing: 12
        rowSpacing: 12

        // Autofocus
        SettingsCard {
            title: "Autofocus"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                RowLayout { Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: "0.70"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                Slider { Layout.fillWidth: true; from: 0; to: 1; stepSize: 0.05; value: 0.7; Material.accent: Theme.primary }
                RowLayout { Text { text: "Smoothing"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Switch { Material.accent: Theme.primary; checked: true } }
                RowLayout { Text { text: "Response Speed"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: "50"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                Slider { Layout.fillWidth: true; from: 0; to: 100; stepSize: 5; value: 50; Material.accent: Theme.primary }
            }
        }

        // Motor
        SettingsCard {
            title: "Motor"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                RowLayout { Text { text: "Reverse Direction"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Switch { Material.accent: Theme.primary } }
                Text { text: "Calibration Offset"; color: Theme.textSecondary; font.pixelSize: 10 }
                SpinBox { from: -500; to: 500; value: 0; Layout.fillWidth: true }
                Text { text: "Dest Address (hex)"; color: Theme.textSecondary; font.pixelSize: 10 }
                RowLayout {
                    TextField { id: destField; text: "FFFF"; Layout.fillWidth: true; inputMask: "HHHH"; font.family: Theme.fontFamilyMono }
                    Button { text: "Set"; onClicked: { if (alice) alice.setMotorDestination(parseInt(destField.text, 16)) } }
                    Button { text: "Scan"; onClicked: { if (alice) alice.scanMotorAddress(parseInt(destField.text, 16)) } }
                }
            }
        }

        // Depth
        SettingsCard {
            title: "Depth Sensor"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                RowLayout { Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: "0.70"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                Slider { Layout.fillWidth: true; from: 0; to: 1; stepSize: 0.05; value: 0.7; Material.accent: Theme.primary }
                Text { text: "Min Distance (mm)"; color: Theme.textSecondary; font.pixelSize: 10 }
                SpinBox { from: 100; to: 1000; value: 200; Layout.fillWidth: true }
                Text { text: "Max Distance (mm)"; color: Theme.textSecondary; font.pixelSize: 10 }
                SpinBox { from: 1000; to: 10000; value: 5000; Layout.fillWidth: true }
            }
        }

        // Video (2-col span)
        SettingsCard {
            title: "Video"
            columnSpan: 2
            RowLayout {
                Layout.fillWidth: true; spacing: 16
                ColumnLayout {
                    Layout.fillWidth: true; spacing: 6
                    Text { text: "Depth Camera (RealSense)"; color: Theme.textPrimary; font.pixelSize: 10; font.weight: Font.DemiBold }
                    ComboBox { Layout.fillWidth: true; model: alice ? alice.realSenseDepthModes : []; textRole: "label"; Material.accent: Theme.primary
                        onActivated: (index) => { if (!alice) return; let m = alice.realSenseDepthModes[index]; alice.setRealSenseResolution(m.width, m.height, m.fps, m.width, m.height, m.fps) } }
                    Rectangle { Layout.fillWidth: true; Layout.preferredHeight: width * 3 / 4; Layout.maximumHeight: 80; color: Theme.well; radius: Theme.radiusSm
                        DepthRenderer { anchors.centerIn: parent; width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3); height: width * 3 / 4; source: alice ? alice.depthFrame : null; depth: alice ? alice.depth : 0; confidence: alice ? alice.depthConfidence : 0 } }
                }
                ColumnLayout {
                    Layout.fillWidth: true; spacing: 6
                    Text { text: "Camera (Capture Card)"; color: Theme.textPrimary; font.pixelSize: 10; font.weight: Font.DemiBold }
                    ComboBox { Layout.fillWidth: true; model: alice ? alice.captureCardFormats : []; textRole: "label"; Material.accent: Theme.primary
                        onActivated: (index) => { if (!alice) return; let f = alice.captureCardFormats[index]; alice.setCaptureCardResolution(f.width, f.height, f.maxFps) } }
                    Rectangle { Layout.fillWidth: true; Layout.preferredHeight: width * 9 / 16; Layout.maximumHeight: 80; color: Theme.well; radius: Theme.radiusSm
                        VideoRenderer { anchors.centerIn: parent; width: Math.min(parent.width - 4, (parent.height - 4) * 16 / 9); height: width * 9 / 16; source: alice ? alice.captureFrame : null } }
                }
            }
        }

        // System
        SettingsCard {
            title: "System"
            ColumnLayout {
                Layout.fillWidth: true; spacing: 10
                Text { text: "Log Verbosity"; color: Theme.textSecondary; font.pixelSize: 10 }
                ComboBox { model: ["ERROR", "WARNING", "INFO", "DEBUG"]; currentIndex: 2; Layout.fillWidth: true }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
                Button { text: "Reset All Settings"; flat: true; Material.foreground: Theme.dangerText }
            }
        }
    }
}
