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

            SettingsCard {
                title: "Autofocus"
                Layout.fillHeight: true
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)

                    RowLayout {
                        Text { text: "Confidence Threshold"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true }
                        Text { text: afConfSlider.value.toFixed(2); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary }
                    }
                    AliceSlider {
                        id: afConfSlider
                        Layout.fillWidth: true; from: 0.2; to: 1.0; stepSize: 0.05
                        value: alice ? alice.afConfidenceThreshold() : 0.7
                        onMoved: { if (alice) alice.setAfConfidenceThreshold(value) }
                    }

                    RowLayout {
                        Text { text: "AF Smoothing"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true }
                        Text { text: afAlphaSlider.value.toFixed(2); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary }
                    }
                    Text {
                        text: "Lower = smoother tracking, Higher = faster response"
                        font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
                        Layout.fillWidth: true; wrapMode: Text.WordWrap
                    }
                    AliceSlider {
                        id: afAlphaSlider
                        Layout.fillWidth: true; from: 0.05; to: 1.0; stepSize: 0.05
                        value: alice ? alice.afSmoothingAlpha() : 0.4
                        onMoved: { if (alice) alice.setAfSmoothingAlpha(value) }
                    }
                }
            }

            SettingsCard {
                title: "Motor"
                Layout.fillHeight: true
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)

                    RowLayout {
                        Text { text: "Reverse Direction"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true }
                        Switch {
                            Material.accent: Theme.primary
                            checked: alice ? alice.motorReversed() : false
                            onToggled: { if (alice) alice.setMotorReversed(checked) }
                        }
                    }

                    Text { text: "Calibration Offset"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                    AliceSpinBox {
                        from: -500; to: 500
                        value: alice ? alice.motorOffset() : 0
                        Layout.fillWidth: true
                        onValueModified: { if (alice) alice.setMotorOffset(value) }
                    }

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

                    RowLayout {
                        Text { text: "Min Distance (mm)"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true }
                        Text { text: depthMinSpin.value.toString(); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary }
                    }
                    AliceSpinBox {
                        id: depthMinSpin
                        from: 100; to: 2000; value: alice ? alice.depthMinDistance() : 200
                        Layout.fillWidth: true
                        onValueModified: { if (alice) alice.setDepthMinDistance(value) }
                    }

                    RowLayout {
                        Text { text: "Max Distance (mm)"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true }
                        Text { text: depthMaxSpin.value.toString(); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary }
                    }
                    AliceSpinBox {
                        id: depthMaxSpin
                        from: 1000; to: 10000; value: alice ? alice.depthMaxDistance() : 5000
                        Layout.fillWidth: true
                        onValueModified: { if (alice) alice.setDepthMaxDistance(value) }
                    }

                    RowLayout {
                        Text { text: "Depth Smoothing"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true }
                        Text { text: depthSmoothSlider.value.toFixed(0); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary }
                    }
                    Text {
                        text: "Kalman measurement noise (higher = smoother)"
                        font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
                        Layout.fillWidth: true; wrapMode: Text.WordWrap
                    }
                    AliceSlider {
                        id: depthSmoothSlider
                        Layout.fillWidth: true; from: 10; to: 500; stepSize: 10
                        value: alice ? alice.depthSmoothingValue() : 100
                        onMoved: { if (alice) alice.setDepthSmoothing(value) }
                    }
                }
            }

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

            // System
            SettingsCard {
                title: "System"
                Layout.alignment: Qt.AlignTop
                ColumnLayout {
                    Layout.fillWidth: true; spacing: Theme.dp(12)
                    Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
                    Rectangle {
                        Layout.fillWidth: true; Layout.preferredHeight: Theme.dp(40); radius: Theme.radiusSm
                        color: resetMa.pressed ? Qt.darker(Theme.dangerMuted, 1.15)
                             : (resetMa.containsMouse ? Theme.dangerMuted : "transparent")
                        border.width: 1; border.color: resetMa.containsMouse ? Theme.danger : Qt.rgba(0.86, 0.22, 0.22, 0.4)
                        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                        Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                        Text { anchors.centerIn: parent; text: "Reset All Settings"; font.pixelSize: Theme.fontSizeSmall; color: Theme.dangerText }
                        MouseArea {
                            id: resetMa; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor
                            onClicked: { if (alice) alice.resetAllSettings() }
                        }
                    }
                }
            }
        }
    }
}
