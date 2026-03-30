import QtQuick
import Alice.UI
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
            color: Theme.textPrimary
            Layout.alignment: Qt.AlignHCenter
        }

        Label {
            text: "Connect your Android device to this PC over the local network"
            font.pixelSize: 14
            color: Theme.textSecondary
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
                Material.background: alice && alice.syncServerRunning ? Theme.dangerText : Theme.primary
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
                color: alice && alice.syncServerRunning ? Theme.success : Theme.textSecondary
                font.pixelSize: 12
            }
        }

        // QR Code display
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: 280
            height: 280
            color: "#ffffff"
            radius: Theme.radiusLg
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
                color: Theme.textSecondary
            }
        }

        // Connection status
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: 300
            height: 60
            radius: Theme.radiusLg
            color: alice && alice.syncClientConnected ? Theme.successMuted : Theme.elevated
            visible: alice && alice.syncServerRunning

            RowLayout {
                anchors.centerIn: parent
                spacing: 12

                Rectangle {
                    width: 12; height: 12; radius: 6
                    color: alice && alice.syncClientConnected ? Theme.success : Theme.warning

                    SequentialAnimation on opacity {
                        running: alice ? (!alice.syncClientConnected && alice.syncServerRunning) : false
                        loops: Animation.Infinite
                        NumberAnimation { to: 0.3; duration: 800 }
                        NumberAnimation { to: 1.0; duration: 800 }
                    }
                }

                Label {
                    text: alice && alice.syncClientConnected ? "Android device connected" : "Waiting for connection..."
                    color: Theme.textPrimary
                    font.pixelSize: 14
                }
            }
        }

        // Transmission Quality settings
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: Math.min(parent.width * 0.6, 500)
            height: txColumn.implicitHeight + 32
            radius: Theme.radiusLg
            color: Theme.elevated

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
                    color: Theme.textSecondary
                }

                // Depth/Color Quality
                RowLayout {
                    Layout.fillWidth: true
                    Label { text: "Depth/Color Quality"; color: Theme.textPrimary; Layout.preferredWidth: 150 }
                    Slider {
                        Layout.fillWidth: true
                        from: 10; to: 100; stepSize: 5
                        value: alice ? alice.txQualityDepth : 85
                        onMoved: { if (alice) alice.txQualityDepth = value }
                        Material.accent: Theme.primary
                    }
                    Label {
                        text: alice ? alice.txQualityDepth.toString() : "85"
                        font.family: Theme.fontFamilyMono
                        font.pixelSize: 12
                        color: Theme.primary
                        Layout.preferredWidth: 30
                    }
                }

                // Camera Quality
                RowLayout {
                    Layout.fillWidth: true
                    Label { text: "Camera Quality"; color: Theme.textPrimary; Layout.preferredWidth: 150 }
                    Slider {
                        Layout.fillWidth: true
                        from: 10; to: 100; stepSize: 5
                        value: alice ? alice.txQualityCapture : 80
                        onMoved: { if (alice) alice.txQualityCapture = value }
                        Material.accent: Theme.primary
                    }
                    Label {
                        text: alice ? alice.txQualityCapture.toString() : "80"
                        font.family: Theme.fontFamilyMono
                        font.pixelSize: 12
                        color: Theme.primary
                        Layout.preferredWidth: 30
                    }
                }

                // Max FPS
                RowLayout {
                    Layout.fillWidth: true
                    Label { text: "Max FPS"; color: Theme.textPrimary; Layout.preferredWidth: 150 }
                    Slider {
                        Layout.fillWidth: true
                        from: 5; to: 60; stepSize: 5
                        value: alice ? alice.txMaxFps : 30
                        onMoved: { if (alice) alice.txMaxFps = value }
                        Material.accent: Theme.primary
                    }
                    Label {
                        text: alice ? alice.txMaxFps.toString() : "30"
                        font.family: Theme.fontFamilyMono
                        font.pixelSize: 12
                        color: Theme.primary
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

            Label { text: "Manual:"; color: Theme.textSecondary; font.pixelSize: 12 }
            Label {
                text: alice ? alice.syncQrPayload : ""
                color: Theme.primary
                font.family: Theme.fontFamilyMono
                font.pixelSize: 11
            }
        }
    }
}
