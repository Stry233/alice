import QtQuick
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
                Material.accent: "#d0bcff"

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

                        Label { text: "Confidence Threshold"; color: "#a09da6" }
                        Slider {
                            Layout.fillWidth: true
                            from: 0.0; to: 1.0; stepSize: 0.05
                            value: 0.7
                            Material.accent: "#d0bcff"
                        }

                        Label { text: "Smoothing"; color: "#a09da6" }
                        Switch {
                            checked: true
                            Material.accent: "#d0bcff"
                        }

                        Label { text: "Response Speed"; color: "#a09da6" }
                        Slider {
                            Layout.fillWidth: true
                            from: 0; to: 100; stepSize: 5
                            value: 50
                            Material.accent: "#d0bcff"
                        }

                        Button {
                            text: "Reset Autofocus Settings"
                            flat: true
                            Material.foreground: "#f2b8b5"
                        }
                    }
                }

                // Motor tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Reverse Direction"; color: "#a09da6" }
                        Switch {
                            Material.accent: "#d0bcff"
                        }

                        Label { text: "Calibration Offset"; color: "#a09da6" }
                        SpinBox {
                            from: -500; to: 500
                            value: 0
                            Layout.fillWidth: true
                        }

                        Label { text: "Destination Address (hex)"; color: "#a09da6" }
                        RowLayout {
                            TextField {
                                id: destField
                                text: "FFFF"
                                Layout.fillWidth: true
                                inputMask: "HHHH"
                                font.family: "RobotoMono"
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
                            Material.foreground: "#f2b8b5"
                        }
                    }
                }

                // Depth tab
                ScrollView {
                    ColumnLayout {
                        width: settingsView.width * 0.55
                        spacing: 16
                        anchors.margins: 20

                        Label { text: "Confidence Threshold"; color: "#a09da6" }
                        Slider {
                            Layout.fillWidth: true
                            from: 0.0; to: 1.0; stepSize: 0.05
                            value: 0.7
                            Material.accent: "#d0bcff"
                        }

                        Label { text: "Min Distance (mm)"; color: "#a09da6" }
                        SpinBox { from: 100; to: 1000; value: 200; Layout.fillWidth: true }

                        Label { text: "Max Distance (mm)"; color: "#a09da6" }
                        SpinBox { from: 1000; to: 10000; value: 5000; Layout.fillWidth: true }

                        Button {
                            text: "Reset Depth Settings"
                            flat: true
                            Material.foreground: "#f2b8b5"
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
                        Label { text: "Depth Camera (RealSense)"; color: "#a09da6"; font.pixelSize: 14; font.weight: Font.Bold }

                        Label { text: "Depth Resolution"; color: "#a09da6" }
                        ComboBox {
                            Layout.fillWidth: true
                            model: alice ? alice.realSenseDepthModes : []
                            textRole: "label"
                            Material.accent: "#d0bcff"
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
                            color: "#000000"
                            radius: 4
                            DepthRenderer {
                                anchors.centerIn: parent
                                width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3)
                                height: width * 3 / 4
                                source: alice ? alice.depthFrame : null
                                depth: alice ? alice.depth : 0
                                confidence: alice ? alice.depthConfidence : 0
                            }
                        }

                        Label { text: "Color Preview"; color: "#a09da6" }
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: width * 3 / 4
                            Layout.maximumHeight: 200
                            color: "#000000"
                            radius: 4
                            VideoRenderer {
                                anchors.centerIn: parent
                                width: Math.min(parent.width - 4, (parent.height - 4) * 4 / 3)
                                height: width * 3 / 4
                                source: alice ? alice.colorFrame : null
                            }
                        }

                        // Separator
                        Rectangle { Layout.fillWidth: true; height: 1; color: "#3b383e" }

                        // Capture Card section
                        Label { text: "Camera (Capture Card)"; color: "#a09da6"; font.pixelSize: 14; font.weight: Font.Bold }

                        Label { text: "Resolution"; color: "#a09da6" }
                        ComboBox {
                            Layout.fillWidth: true
                            model: alice ? alice.captureCardFormats : []
                            textRole: "label"
                            Material.accent: "#d0bcff"
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
                            color: "#000000"
                            radius: 4
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

                        Label { text: "Sync Server Port"; color: "#a09da6" }
                        SpinBox { from: 1024; to: 65535; value: 8765; Layout.fillWidth: true }

                        RowLayout {
                            Button {
                                text: alice && alice.syncServerRunning ? "Stop Server" : "Start Server"
                                Material.background: alice && alice.syncServerRunning ? "#f2b8b5" : "#6650a4"
                                onClicked: { if (!alice) return; alice.syncServerRunning ? alice.stopSyncServer() : alice.startSyncServer() }
                            }
                            Label {
                                text: alice && alice.syncServerRunning ? "Running" : "Stopped"
                                color: alice && alice.syncServerRunning ? "#64ff64" : "#a09da6"
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

                        Label { text: "Log Verbosity"; color: "#a09da6" }
                        ComboBox {
                            model: ["ERROR", "WARNING", "INFO", "DEBUG"]
                            currentIndex: 2
                            Layout.fillWidth: true
                        }

                        Button {
                            text: "Reset All Settings"
                            flat: true
                            Material.foreground: "#f2b8b5"
                        }
                    }
                }
            }
        }

        // Separator
        Rectangle { Layout.fillHeight: true; width: 1; color: "#3b383e" }

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
                color: "#a09da6"
            }

            GridLayout {
                columns: 2
                columnSpacing: 16
                rowSpacing: 8

                Label { text: "Motor:"; color: "#a09da6" }
                Label {
                    text: alice && alice.motorConnected ? "Connected" : "Disconnected"
                    color: alice && alice.motorConnected ? "#64ff64" : "#f2b8b5"
                }

                Label { text: "RealSense:"; color: "#a09da6" }
                Label {
                    text: alice && alice.realSenseConnected ? "Connected" : "Disconnected"
                    color: alice && alice.realSenseConnected ? "#64ff64" : "#f2b8b5"
                }

                Label { text: "Sync:"; color: "#a09da6" }
                Label {
                    text: alice && alice.syncClientConnected ? "Client connected" :
                          alice && alice.syncServerRunning ? "Waiting for client" : "Server off"
                    color: alice && alice.syncClientConnected ? "#64ff64" : "#a09da6"
                }

                Label { text: "Depth:"; color: "#a09da6" }
                Label {
                    text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"
                    font.family: "RobotoMono"
                    color: "#e6e1e5"
                }

                Label { text: "Motor Pos:"; color: "#a09da6" }
                Label {
                    text: (alice ? alice.motorPosition : 0).toString()
                    font.family: "RobotoMono"
                    color: "#e6e1e5"
                }
            }

            Item { Layout.fillHeight: true }
        }
    }
}
