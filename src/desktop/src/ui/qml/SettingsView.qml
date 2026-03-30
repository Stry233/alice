import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: settingsView

    property int currentTab: 0

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // Left: Settings tabs (60%)
        ColumnLayout {
            Layout.preferredWidth: parent.width * 0.6
            Layout.fillHeight: true
            spacing: 0

            TabBar {
                id: settingsTabs
                Layout.fillWidth: true
                currentIndex: currentTab
                onCurrentIndexChanged: currentTab = currentIndex
                Material.accent: Theme.primary

                TabButton { text: "Autofocus" }
                TabButton { text: "Motor" }
                TabButton { text: "Depth" }
                TabButton { text: "Video" }
                TabButton { text: "Network" }
                TabButton { text: "System" }
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: currentTab

                // Autofocus tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Confidence Threshold"; color: Theme.textSecondary }
                        Slider {
                            Layout.fillWidth: true
                            from: 0.0; to: 1.0; stepSize: 0.05
                            value: 0.7
                            Material.accent: Theme.primary
                        }

                        Label { text: "Smoothing"; color: Theme.textSecondary }
                        Switch {
                            checked: true
                            Material.accent: Theme.primary
                        }

                        Label { text: "Response Speed"; color: Theme.textSecondary }
                        Slider {
                            Layout.fillWidth: true
                            from: 0; to: 100; stepSize: 5
                            value: 50
                            Material.accent: Theme.primary
                        }

                        Button {
                            text: "Reset Autofocus Settings"
                            flat: true
                            Material.foreground: Theme.dangerText
                        }
                    }
                }

                // Motor tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Reverse Direction"; color: Theme.textSecondary }
                        Switch {
                            Material.accent: Theme.primary
                        }

                        Label { text: "Calibration Offset"; color: Theme.textSecondary }
                        SpinBox {
                            from: -500; to: 500
                            value: 0
                            Layout.fillWidth: true
                        }

                        Label { text: "Destination Address (hex)"; color: Theme.textSecondary }
                        RowLayout {
                            TextField {
                                id: destField
                                text: "FFFF"
                                Layout.fillWidth: true
                                inputMask: "HHHH"
                                font.family: Theme.fontFamilyMono
                            }
                            Button {
                                text: "Set"
                                onClicked: { if (!alice) return; alice.setMotorDestination(parseInt(destField.text, 16)) }
                            }
                            Button {
                                text: "Scan"
                                onClicked: { if (!alice) return; alice.scanMotorAddress(parseInt(destField.text, 16)) }
                            }
                        }

                        Button {
                            text: "Reset Motor Settings"
                            flat: true
                            Material.foreground: Theme.dangerText
                        }
                    }
                }

                // Depth tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Confidence Threshold"; color: Theme.textSecondary }
                        Slider {
                            Layout.fillWidth: true
                            from: 0.0; to: 1.0; stepSize: 0.05
                            value: 0.7
                            Material.accent: Theme.primary
                        }

                        Label { text: "Min Distance (mm)"; color: Theme.textSecondary }
                        SpinBox { from: 100; to: 1000; value: 200; Layout.fillWidth: true }

                        Label { text: "Max Distance (mm)"; color: Theme.textSecondary }
                        SpinBox { from: 1000; to: 10000; value: 5000; Layout.fillWidth: true }

                        Button {
                            text: "Reset Depth Settings"
                            flat: true
                            Material.foreground: Theme.dangerText
                        }
                    }
                }

                // Video tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        // Depth Camera section
                        Label { text: "Depth Camera (RealSense)"; color: Theme.textSecondary; font.pixelSize: 14; font.weight: Font.Bold }

                        Label { text: "Depth Resolution"; color: Theme.textSecondary }
                        ComboBox {
                            Layout.fillWidth: true
                            model: alice ? alice.realSenseDepthModes : []
                            textRole: "label"
                            Material.accent: Theme.primary
                            onActivated: (index) => {
                                if (!alice) return
                                let mode = alice.realSenseDepthModes[index]
                                // For now, set depth and color to same resolution
                                alice.setRealSenseResolution(mode.width, mode.height, mode.fps, mode.width, mode.height, mode.fps)
                            }
                        }

                        // Depth preview (4:3 aspect ratio)
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: width * 3 / 4
                            Layout.maximumHeight: 200
                            color: Theme.well
                            radius: Theme.radiusLg
                            DepthRenderer {
                                anchors.centerIn: parent
                                width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3)
                                height: width * 3 / 4
                                source: alice ? alice.depthFrame : null
                                depth: alice ? alice.depth : 0
                                confidence: alice ? alice.depthConfidence : 0
                            }
                        }

                        Label { text: "Color Preview"; color: Theme.textSecondary }
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: width * 3 / 4
                            Layout.maximumHeight: 200
                            color: Theme.well
                            radius: Theme.radiusLg
                            VideoRenderer {
                                anchors.centerIn: parent
                                width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3)
                                height: width * 3 / 4
                                source: alice ? alice.colorFrame : null
                            }
                        }

                        // Separator
                        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                        // Capture Card section
                        Label { text: "Camera (Capture Card)"; color: Theme.textSecondary; font.pixelSize: 14; font.weight: Font.Bold }

                        Label { text: "Resolution"; color: Theme.textSecondary }
                        ComboBox {
                            Layout.fillWidth: true
                            model: alice ? alice.captureCardFormats : []
                            textRole: "label"
                            Material.accent: Theme.primary
                            onActivated: (index) => {
                                if (!alice) return
                                let fmt = alice.captureCardFormats[index]
                                alice.setCaptureCardResolution(fmt.width, fmt.height, fmt.maxFps)
                            }
                        }

                        // Camera preview (16:9 aspect ratio)
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: width * 9 / 16
                            Layout.maximumHeight: 200
                            color: Theme.well
                            radius: Theme.radiusLg
                            VideoRenderer {
                                anchors.centerIn: parent
                                width: Math.min(parent.width - 4, (parent.height - 4) * 16 / 9)
                                height: width * 9 / 16
                                anchors.margins: 2
                                source: alice ? alice.captureFrame : null
                            }
                        }
                    }
                }

                // Network tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Sync Server Port"; color: Theme.textSecondary }
                        SpinBox { from: 1024; to: 65535; value: 8765; Layout.fillWidth: true }

                        RowLayout {
                            Button {
                                text: alice && alice.syncServerRunning ? "Stop Server" : "Start Server"
                                Material.background: alice && alice.syncServerRunning ? Theme.dangerText : Theme.primary
                                onClicked: { if (!alice) return; alice.syncServerRunning ? alice.stopSyncServer() : alice.startSyncServer() }
                            }
                            Label {
                                text: alice && alice.syncServerRunning ? "Running" : "Stopped"
                                color: alice && alice.syncServerRunning ? Theme.success : Theme.textSecondary
                            }
                        }
                    }
                }

                // System tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Log Verbosity"; color: Theme.textSecondary }
                        ComboBox {
                            model: ["ERROR", "WARNING", "INFO", "DEBUG"]
                            currentIndex: 2
                            Layout.fillWidth: true
                        }

                        Button {
                            text: "Reset All Settings"
                            flat: true
                            Material.foreground: Theme.dangerText
                        }
                    }
                }
            }
        }

        // Separator
        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // Right: Live status (40%)
        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.margins: 16
            spacing: 12

            Label {
                text: "DEVICE STATUS"
                font.pixelSize: 11
                font.weight: Font.Bold
                font.letterSpacing: 1.5
                color: Theme.textSecondary
            }

            GridLayout {
                columns: 2
                columnSpacing: 16
                rowSpacing: 8

                Label { text: "Motor:"; color: Theme.textSecondary }
                Label {
                    text: alice && alice.motorConnected ? "Connected" : "Disconnected"
                    color: alice && alice.motorConnected ? Theme.success : Theme.dangerText
                }

                Label { text: "RealSense:"; color: Theme.textSecondary }
                Label {
                    text: alice && alice.realSenseConnected ? "Connected" : "Disconnected"
                    color: alice && alice.realSenseConnected ? Theme.success : Theme.dangerText
                }

                Label { text: "Sync:"; color: Theme.textSecondary }
                Label {
                    text: alice && alice.syncClientConnected ? "Client connected" :
                          alice && alice.syncServerRunning ? "Waiting for client" : "Server off"
                    color: alice && alice.syncClientConnected ? Theme.success : Theme.textSecondary
                }

                Label { text: "Depth:"; color: Theme.textSecondary }
                Label {
                    text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"
                    font.family: Theme.fontFamilyMono
                    color: Theme.textPrimary
                }

                Label { text: "Motor Pos:"; color: Theme.textSecondary }
                Label {
                    text: (alice ? alice.motorPosition : 0).toString()
                    font.family: Theme.fontFamilyMono
                    color: Theme.textPrimary
                }
            }

            Item { Layout.fillHeight: true }
        }
    }
}
