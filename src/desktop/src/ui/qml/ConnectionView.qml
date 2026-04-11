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

    // Disconnected state — centered
    ColumnLayout {
        visible: !connected
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.verticalCenter: parent.verticalCenter
        spacing: Theme.dp(16)

        // Server not running — show start prompt
        ColumnLayout {
            visible: alice ? !alice.syncServerRunning : true
            Layout.alignment: Qt.AlignHCenter
            spacing: Theme.dp(16)

            Text {
                Layout.alignment: Qt.AlignHCenter
                text: "LAN Connection"
                font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeH3; font.weight: Font.Bold
                color: Theme.textPrimary
            }
            Text {
                Layout.alignment: Qt.AlignHCenter
                text: "Connect your Android device over the local network"
                font.pixelSize: Theme.fontSizeSmall; color: Theme.textSecondary
            }
            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                width: startLabel.implicitWidth + Theme.dp(48); height: Theme.dp(44); radius: Theme.radiusSm
                color: Theme.primary
                Text { id: startLabel; anchors.centerIn: parent; text: "Start Server"; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold; color: "#ffffff" }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: { if (alice) alice.startSyncServer() } }
            }
        }

        // QR Code — server running
        Rectangle {
            Layout.alignment: Qt.AlignHCenter; width: Theme.dp(240); height: Theme.dp(240); color: "#ffffff"; radius: Theme.radiusSm
            visible: alice ? alice.syncServerRunning : false
            VideoRenderer { anchors.centerIn: parent; width: Theme.dp(200); height: Theme.dp(200); source: alice.qrCodeImage }
        }

        Label { Layout.alignment: Qt.AlignHCenter; text: alice ? alice.syncQrPayload : ""; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary; visible: alice ? alice.syncServerRunning : false }
        Label { Layout.alignment: Qt.AlignHCenter; text: "Scan with Alice Android"; font.pixelSize: Theme.fontSizeSmall; color: Theme.textDisabled; visible: alice ? alice.syncServerRunning : false }

        // Waiting indicator — proper aspect ratio, centered dot
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: waitRow.implicitWidth + Theme.dp(32)
            height: Theme.dp(36)
            radius: Theme.radiusSm
            color: Theme.warningMuted; border.width: 1; border.color: Qt.rgba(0.85, 0.51, 0.17, 0.3)
            visible: alice ? (alice.syncServerRunning && !connected) : false
            Row {
                id: waitRow; anchors.centerIn: parent; spacing: Theme.dp(8)
                Rectangle {
                    width: Theme.dp(10); height: Theme.dp(10); radius: Theme.dp(5)
                    color: Theme.warning
                    anchors.verticalCenter: parent.verticalCenter
                    SequentialAnimation on opacity { loops: Animation.Infinite; NumberAnimation { to: 0.4; duration: 800 } NumberAnimation { to: 1.0; duration: 800 } }
                }
                Text { text: "Waiting..."; font.pixelSize: Theme.fontSizeSmall; color: Theme.warning; anchors.verticalCenter: parent.verticalCenter }
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
            Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: Theme.dp(24); spacing: Theme.dp(20)

            Rectangle {
                Layout.fillWidth: true; height: 32; radius: Theme.radiusSm; color: Theme.successMuted; border.width: 1; border.color: Qt.rgba(0.082, 0.702, 0.443, 0.3)
                Row { anchors.centerIn: parent; spacing: 6; Rectangle { width: 6; height: 6; radius: 3; color: Theme.success } Text { text: "Android connected"; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold; color: Theme.textPrimary } }
            }

            GridLayout {
                columns: 2; columnSpacing: 12; rowSpacing: 4
                Text { text: "Client IP"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                Text { text: alice ? alice.syncQrPayload : ""; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }
                Text { text: "Uptime"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall }
                Text { text: "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }
            }

            Button { text: "Disconnect"; flat: true; Material.foreground: Theme.dangerText; onClicked: { if (alice) alice.stopSyncServer() } }
            Item { Layout.fillHeight: true }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // Right: TX quality
        ColumnLayout {
            Layout.preferredWidth: Theme.dp(400); Layout.fillHeight: true; Layout.margins: Theme.dp(24); spacing: Theme.dp(16)

            SectionHeader { text: "TX QUALITY" }

            ColumnLayout {
                spacing: Theme.dp(16); Layout.fillWidth: true

                ColumnLayout {
                    spacing: Theme.dp(6); Layout.fillWidth: true
                    RowLayout { Text { text: "Depth/Color"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Text { text: alice ? alice.txQualityDepth.toString() : "85"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary } }
                    AliceSlider { Layout.fillWidth: true; from: 10; to: 100; stepSize: 5; value: alice ? alice.txQualityDepth : 85; onMoved: { if (alice) alice.txQualityDepth = value } }
                }

                ColumnLayout {
                    spacing: Theme.dp(6); Layout.fillWidth: true
                    RowLayout { Text { text: "Camera"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Text { text: alice ? alice.txQualityCapture.toString() : "80"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary } }
                    AliceSlider { Layout.fillWidth: true; from: 10; to: 100; stepSize: 5; value: alice ? alice.txQualityCapture : 80; onMoved: { if (alice) alice.txQualityCapture = value } }
                }

                ColumnLayout {
                    spacing: Theme.dp(6); Layout.fillWidth: true
                    RowLayout { Text { text: "Max FPS"; color: Theme.textSecondary; font.pixelSize: Theme.fontSizeSmall; Layout.fillWidth: true } Text { text: alice ? alice.txMaxFps.toString() : "30"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary } }
                    AliceSlider { Layout.fillWidth: true; from: 5; to: 60; stepSize: 5; value: alice ? alice.txMaxFps : 30; onMoved: { if (alice) alice.txMaxFps = value } }
                }
            }

            Item { Layout.fillHeight: true }
        }
    }
}
