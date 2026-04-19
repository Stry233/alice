import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: connectionView

    ColumnLayout {
        anchors.centerIn: parent
        spacing: 32
        width: Math.min(parent.width * 0.6, 600)

        // Title
        Label {
            text: "LAN Connection"
            font.pixelSize: 24
            font.weight: Font.Bold
            color: "#e6e1e5"
            Layout.alignment: Qt.AlignHCenter
        }

        Label {
            text: "Connect your Android device to this PC over the local network"
            font.pixelSize: 14
            color: "#a09da6"
            wrapMode: Text.WordWrap
            Layout.alignment: Qt.AlignHCenter
            Layout.maximumWidth: 400
            horizontalAlignment: Text.AlignHCenter
        }

        // Server control
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            spacing: 16

            Button {
                text: alice && alice.syncServerRunning ? "Stop Server" : "Start Server"
                Material.background: alice && alice.syncServerRunning ? "#f2b8b5" : "#6650a4"
                onClicked: {
                    if (!alice) return;
                    if (alice.syncServerRunning) alice.stopSyncServer()
                    else alice.startSyncServer()
                }
            }

            Label {
                text: alice && alice.syncServerRunning
                      ? "Listening on " + alice.syncQrPayload
                      : "Server not running"
                color: alice && alice.syncServerRunning ? "#64ff64" : "#a09da6"
                font.pixelSize: 12
            }
        }

        // QR Code display
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: 280
            height: 280
            color: "#ffffff"
            radius: 8
            visible: alice && alice.syncServerRunning

            // QR code rendered via VideoRenderer (accepts QImage)
            VideoRenderer {
                anchors.centerIn: parent
                width: 256
                height: 256
                source: alice ? alice.qrCodeImage : null
            }

            Label {
                anchors.bottom: parent.bottom
                anchors.bottomMargin: -28
                anchors.horizontalCenter: parent.horizontalCenter
                text: "Scan with Alice Android app"
                font.pixelSize: 12
                color: "#a09da6"
            }
        }

        // Connection status
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: 300
            height: 60
            radius: 8
            color: alice && alice.syncClientConnected ? "#1b4332" : "#2b2930"
            visible: alice && alice.syncServerRunning

            RowLayout {
                anchors.centerIn: parent
                spacing: 12

                Rectangle {
                    width: 12; height: 12; radius: 6
                    color: alice && alice.syncClientConnected ? "#64ff64" : "#ffc832"

                    SequentialAnimation on opacity {
                        running: alice ? (!alice.syncClientConnected && alice.syncServerRunning) : false
                        loops: Animation.Infinite
                        NumberAnimation { to: 0.3; duration: 800 }
                        NumberAnimation { to: 1.0; duration: 800 }
                    }
                }

                Label {
                    text: alice && alice.syncClientConnected ? "Android device connected" : "Waiting for connection..."
                    color: "#e6e1e5"
                    font.pixelSize: 14
                }
            }
        }

        // Transmission Quality settings
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: Math.min(parent.width * 0.6, 500)
            height: txColumn.implicitHeight + 32
            radius: 8
            color: "#2b2930"

            ColumnLayout {
                id: txColumn
                anchors.fill: parent
                anchors.margins: 16
                spacing: 12

                Label {
                    text: "TRANSMISSION QUALITY"
                    font.pixelSize: 11
                    font.weight: Font.Bold
                    font.letterSpacing: 1.5
                    color: "#a09da6"
                }

                // Depth/Color Quality
                RowLayout {
                    Layout.fillWidth: true
                    Label { text: "Depth/Color Quality"; color: "#e6e1e5"; Layout.preferredWidth: 150 }
                    Slider {
                        Layout.fillWidth: true
                        from: 10; to: 100; stepSize: 5
                        value: alice ? alice.txQualityDepth : 85
                        onMoved: { if (alice) alice.txQualityDepth = value }
                        Material.accent: "#d0bcff"
                    }
                    Label {
                        text: alice ? alice.txQualityDepth.toString() : "85"
                        font.family: "RobotoMono"
                        font.pixelSize: 12
                        color: "#d0bcff"
                        Layout.preferredWidth: 30
                    }
                }

                // Camera Quality
                RowLayout {
                    Layout.fillWidth: true
                    Label { text: "Camera Quality"; color: "#e6e1e5"; Layout.preferredWidth: 150 }
                    Slider {
                        Layout.fillWidth: true
                        from: 10; to: 100; stepSize: 5
                        value: alice ? alice.txQualityCapture : 80
                        onMoved: { if (alice) alice.txQualityCapture = value }
                        Material.accent: "#d0bcff"
                    }
                    Label {
                        text: alice ? alice.txQualityCapture.toString() : "80"
                        font.family: "RobotoMono"
                        font.pixelSize: 12
                        color: "#d0bcff"
                        Layout.preferredWidth: 30
                    }
                }

                // Max FPS
                RowLayout {
                    Layout.fillWidth: true
                    Label { text: "Max FPS"; color: "#e6e1e5"; Layout.preferredWidth: 150 }
                    Slider {
                        Layout.fillWidth: true
                        from: 5; to: 60; stepSize: 5
                        value: alice ? alice.txMaxFps : 30
                        onMoved: { if (alice) alice.txMaxFps = value }
                        Material.accent: "#d0bcff"
                    }
                    Label {
                        text: alice ? alice.txMaxFps.toString() : "30"
                        font.family: "RobotoMono"
                        font.pixelSize: 12
                        color: "#d0bcff"
                        Layout.preferredWidth: 30
                    }
                }
            }
        }

        // Manual IP entry
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            visible: alice && alice.syncServerRunning
            spacing: 8

            Label { text: "Manual:"; color: "#a09da6"; font.pixelSize: 12 }
            Label {
                text: alice ? alice.syncQrPayload : ""
                color: "#d0bcff"
                font.family: "RobotoMono"
                font.pixelSize: 11
            }
        }
    }
}
