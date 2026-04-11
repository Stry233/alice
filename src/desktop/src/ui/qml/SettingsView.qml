import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    ScrollView {
        id: settingsView
        anchors.fill: parent
        anchors.margins: Theme.dp(32)

        GridLayout {
            width: settingsView.width - Theme.dp(64)
            x: Theme.dp(32)
            columns: 3
            columnSpacing: Theme.dp(24)
            rowSpacing: Theme.dp(24)

            // Autofocus — force same height as Motor
            SettingsCard {
                title: "Autofocus"
                Layout.fillHeight: true
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)
                    RowLayout { Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Text { text: "0.70"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary } }
                    AliceSlider { Layout.fillWidth: true; from: 0; to: 1; stepSize: 0.05; value: 0.7 }
                    RowLayout { Text { text: "Smoothing"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Switch { Material.accent: Theme.primary; checked: true } }
                    RowLayout { Text { text: "Response Speed"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Text { text: "50"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary } }
                    AliceSlider { Layout.fillWidth: true; from: 0; to: 100; stepSize: 5; value: 50 }
                }
            }

            // Motor — force same height as Autofocus
            SettingsCard {
                title: "Motor"
                Layout.fillHeight: true
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)
                    RowLayout { Text { text: "Reverse Direction"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Switch { Material.accent: Theme.primary } }
                    Text { text: "Calibration Offset"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                    AliceSpinBox { from: -500; to: 500; value: 0; Layout.fillWidth: true }
                    Text { text: "Dest Address (hex)"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                    RowLayout {
                        spacing: Theme.dp(8)
                        AliceTextField { id: destField; text: "FFFF"; Layout.fillWidth: true; inputMask: "HHHH"; font.family: Theme.fontFamilyMono }
                        Rectangle {
                            id: setBtn
                            width: setLabel.implicitWidth + Theme.dp(24); height: Theme.dp(34); radius: Theme.radiusSm
                            color: setMa.pressed ? Theme.surfaceActive : (setMa.containsMouse ? Theme.surfaceHover : Theme.surface)
                            border.width: 1; border.color: setMa.containsMouse ? Theme.borderStrong : Theme.border
                            Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                            Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                            Text { id: setLabel; anchors.centerIn: parent; text: "Set"; font.pixelSize: Theme.fontSizeSmall; color: Theme.textPrimary }
                            MouseArea { id: setMa; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: { if (alice) alice.setMotorDestination(parseInt(destField.text, 16)) } }
                        }
                        Rectangle {
                            id: scanBtn
                            width: scanLabel.implicitWidth + Theme.dp(24); height: Theme.dp(34); radius: Theme.radiusSm
                            color: scanMa.pressed ? Theme.surfaceActive : (scanMa.containsMouse ? Theme.surfaceHover : Theme.surface)
                            border.width: 1; border.color: scanMa.containsMouse ? Theme.borderStrong : Theme.border
                            Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                            Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                            Text { id: scanLabel; anchors.centerIn: parent; text: "Scan"; font.pixelSize: Theme.fontSizeSmall; color: Theme.textPrimary }
                            MouseArea { id: scanMa; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: { if (alice) alice.scanMotorAddress(parseInt(destField.text, 16)) } }
                        }
                    }
                }
            }

            // Depth Sensor
            SettingsCard {
                title: "Depth Sensor"
                Layout.fillHeight: true
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)
                    RowLayout { Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Text { text: "0.70"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary } }
                    AliceSlider { Layout.fillWidth: true; from: 0; to: 1; stepSize: 0.05; value: 0.7 }
                    Text { text: "Min Distance (mm)"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                    AliceSpinBox { from: 100; to: 1000; value: 200; Layout.fillWidth: true }
                    Text { text: "Max Distance (mm)"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                    AliceSpinBox { from: 1000; to: 10000; value: 5000; Layout.fillWidth: true }
                }
            }

            // Video (2-col span) — larger preview
            SettingsCard {
                title: "Video"
                columnSpan: 2
                RowLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(24)
                    ColumnLayout {
                        Layout.fillWidth: true; spacing: Theme.dp(10)
                        Text { text: "Depth Camera (RealSense)"; color: Theme.textPrimary; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold }
                        ComboBox { Layout.fillWidth: true; model: alice ? alice.realSenseDepthModes : []; textRole: "label"; Material.accent: Theme.primary
                            onActivated: (index) => { if (!alice) return; let m = alice.realSenseDepthModes[index]; alice.setRealSenseResolution(m.width, m.height, m.fps, m.width, m.height, m.fps) } }
                        Rectangle { Layout.fillWidth: true; Layout.preferredHeight: width * 3 / 4; Layout.maximumHeight: Theme.dp(160); color: Theme.well; radius: Theme.radiusSm
                            DepthRenderer { anchors.centerIn: parent; width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3); height: width * 3 / 4; source: alice.depthFrame; depth: alice ? alice.depth : 0; confidence: alice ? alice.depthConfidence : 0 } }
                    }
                    ColumnLayout {
                        Layout.fillWidth: true; spacing: Theme.dp(10)
                        Text { text: "Camera (Capture Card)"; color: Theme.textPrimary; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold }
                        ComboBox { Layout.fillWidth: true; model: alice ? alice.captureCardFormats : []; textRole: "label"; Material.accent: Theme.primary
                            onActivated: (index) => { if (!alice) return; let f = alice.captureCardFormats[index]; alice.setCaptureCardResolution(f.width, f.height, f.maxFps) } }
                        Rectangle { Layout.fillWidth: true; Layout.preferredHeight: width * 9 / 16; Layout.maximumHeight: Theme.dp(160); color: Theme.well; radius: Theme.radiusSm
                            VideoRenderer { anchors.centerIn: parent; width: Math.min(parent.width - 4, (parent.height - 4) * 16 / 9); height: width * 9 / 16; source: alice.captureFrame } }
                    }
                }
            }

            // System — top-aligned with Video row
            SettingsCard {
                title: "System"
                Layout.alignment: Qt.AlignTop
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)
                    Text { text: "Log Verbosity"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                    ComboBox { model: ["ERROR", "WARNING", "INFO", "DEBUG"]; currentIndex: 2; Layout.fillWidth: true }
                    Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
                    Button { text: "Reset All Settings"; flat: true; Material.foreground: Theme.dangerText }
                }
            }
        }
    }
}
