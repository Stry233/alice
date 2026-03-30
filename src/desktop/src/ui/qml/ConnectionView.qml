import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: connectionView

    property bool connected: alice ? alice.syncClientConnected : false

    // Auto-start server on view entry
    onVisibleChanged: {
        if (visible && alice && !alice.syncServerRunning)
            alice.startSyncServer()
    }

    // Waiting state — centered
    ColumnLayout {
        visible: !connected
        anchors.centerIn: parent
        spacing: 10
        width: Math.min(parent.width * 0.6, 400)

        // QR Code
        Rectangle {
            Layout.alignment: Qt.AlignHCenter; width: 148; height: 148; color: "#ffffff"; radius: Theme.radiusSm
            visible: alice ? alice.syncServerRunning : false
            VideoRenderer { anchors.centerIn: parent; width: 120; height: 120; source: alice ? alice.qrCodeImage : null }
        }

        Label { Layout.alignment: Qt.AlignHCenter; text: alice ? alice.syncQrPayload : ""; font.family: Theme.fontFamilyMono; font.pixelSize: 11; color: Theme.primary; visible: alice ? alice.syncServerRunning : false }
        Label { Layout.alignment: Qt.AlignHCenter; text: "Scan with Alice Android"; font.pixelSize: 10; color: Theme.textDisabled; visible: alice ? alice.syncServerRunning : false }

        // Waiting indicator
        Rectangle {
            Layout.alignment: Qt.AlignHCenter; width: waitRow.implicitWidth + 24; height: 24; radius: Theme.radiusSm
            color: Theme.warningMuted; border.width: 1; border.color: Qt.rgba(0.85, 0.51, 0.17, 0.3)
            visible: alice ? (alice.syncServerRunning && !connected) : false
            Row { id: waitRow; anchors.centerIn: parent; spacing: 5
                Rectangle { width: 5; height: 5; radius: 3; color: Theme.warning; SequentialAnimation on opacity { loops: Animation.Infinite; NumberAnimation { to: 0.4; duration: 800 }; NumberAnimation { to: 1.0; duration: 800 } } }
                Text { text: "Waiting..."; font.pixelSize: 10; color: Theme.warning }
            }
        }

        Button { Layout.alignment: Qt.AlignHCenter; text: "Stop Server"; flat: true; Material.foreground: Theme.dangerText; visible: alice ? alice.syncServerRunning : false; onClicked: { if (alice) alice.stopSyncServer() } }
    }

    // Connected state — split
    RowLayout {
        visible: connected
        anchors.fill: parent
        spacing: 0

        // Left: sync telemetry
        ColumnLayout {
            Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 16; spacing: 12

            Rectangle {
                Layout.fillWidth: true; height: 32; radius: Theme.radiusSm; color: Theme.successMuted; border.width: 1; border.color: Qt.rgba(0.082, 0.702, 0.443, 0.3)
                Row { anchors.centerIn: parent; spacing: 6; Rectangle { width: 6; height: 6; radius: 3; color: Theme.success } Text { text: "Android connected"; font.pixelSize: 11; font.weight: Font.DemiBold; color: Theme.textPrimary } }
            }

            GridLayout {
                columns: 2; columnSpacing: 12; rowSpacing: 4
                Text { text: "Client IP"; color: Theme.textSecondary; font.pixelSize: 10 }
                Text { text: alice ? alice.syncQrPayload : ""; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
                Text { text: "Uptime"; color: Theme.textSecondary; font.pixelSize: 10 }
                Text { text: "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
            }

            Button { text: "Disconnect"; flat: true; Material.foreground: Theme.dangerText; onClicked: { if (alice) alice.stopSyncServer() } }
            Item { Layout.fillHeight: true }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // Right: TX quality
        ColumnLayout {
            Layout.preferredWidth: 240; Layout.fillHeight: true; Layout.margins: 16; spacing: 10

            SectionHeader { text: "TX QUALITY" }

            ColumnLayout {
                spacing: 10; Layout.fillWidth: true

                ColumnLayout {
                    spacing: 3; Layout.fillWidth: true
                    RowLayout { Text { text: "Depth/Color"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: alice ? alice.txQualityDepth.toString() : "85"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                    Slider { Layout.fillWidth: true; from: 10; to: 100; stepSize: 5; value: alice ? alice.txQualityDepth : 85; Material.accent: Theme.primary; onMoved: { if (alice) alice.txQualityDepth = value } }
                }

                ColumnLayout {
                    spacing: 3; Layout.fillWidth: true
                    RowLayout { Text { text: "Camera"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: alice ? alice.txQualityCapture.toString() : "80"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                    Slider { Layout.fillWidth: true; from: 10; to: 100; stepSize: 5; value: alice ? alice.txQualityCapture : 80; Material.accent: Theme.primary; onMoved: { if (alice) alice.txQualityCapture = value } }
                }

                ColumnLayout {
                    spacing: 3; Layout.fillWidth: true
                    RowLayout { Text { text: "Max FPS"; color: Theme.textSecondary; font.pixelSize: 10; Layout.fillWidth: true } Text { text: alice ? alice.txMaxFps.toString() : "30"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary } }
                    Slider { Layout.fillWidth: true; from: 5; to: 60; stepSize: 5; value: alice ? alice.txMaxFps : 30; Material.accent: Theme.primary; onMoved: { if (alice) alice.txMaxFps = value } }
                }
            }

            Item { Layout.fillHeight: true }
        }
    }
}
