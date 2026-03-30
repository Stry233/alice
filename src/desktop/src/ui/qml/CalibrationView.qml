import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: calibView
    property var calibrationPoints: []

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // LEFT: Motor + Previews (200px)
        ColumnLayout {
            Layout.preferredWidth: Theme.sidebarNarrow
            Layout.fillHeight: true
            spacing: 0

            // Motor control
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: motorSection.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: motorSection; anchors.fill: parent; anchors.margins: 10; spacing: 6
                    SectionHeader { text: "MOTOR CONTROL" }
                    MotorSlider { Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0; enabled: alice ? alice.motorConnected : false; onMotorMoved: (pos) => { if (alice) alice.setMotorPosition(pos) } }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Camera preview
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: cameraPreviewCol.implicitHeight + 16; color: Theme.bg
                ColumnLayout {
                    id: cameraPreviewCol; anchors.fill: parent; anchors.margins: 8; spacing: 4
                    Text { text: "CAMERA"; font.pixelSize: 8; color: Theme.textDisabled }
                    Rectangle {
                        Layout.fillWidth: true; Layout.preferredHeight: width * 9 / 16; color: Theme.well; radius: Theme.radiusSm
                        VideoRenderer { anchors.fill: parent; source: alice ? alice.captureFrame : null; visible: alice ? alice.captureCardConnected : false }
                        Label { anchors.centerIn: parent; text: "No camera"; font.pixelSize: 10; color: Theme.textPlaceholder; visible: alice ? !alice.captureCardConnected : true }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth preview
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: depthPreviewCol.implicitHeight + 16; color: Theme.bg
                ColumnLayout {
                    id: depthPreviewCol; anchors.fill: parent; anchors.margins: 8; spacing: 4
                    RowLayout {
                        Text { text: "DEPTH"; font.pixelSize: 8; color: Theme.textDisabled; Layout.fillWidth: true }
                        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m" : "\u2014"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.success }
                    }
                    Rectangle {
                        Layout.fillWidth: true; Layout.preferredHeight: width * 3 / 4; color: Theme.well; radius: Theme.radiusSm
                        VideoRenderer { anchors.centerIn: parent; width: Math.min(parent.width, parent.height * 4 / 3); height: width * 3 / 4; source: alice ? alice.colorFrame : null; visible: alice ? alice.realSenseConnected : false }
                        Item {
                            property real normX: alice ? alice.measureX : 0.5; property real normY: alice ? alice.measureY : 0.5
                            x: normX * parent.width - 6; y: normY * parent.height - 6; width: 12; height: 12; visible: alice ? alice.realSenseConnected : false
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 12; height: 1; color: "#fff" }
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 12; color: "#fff" }
                        }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.CrossCursor; property bool dragging: false
                            onPressed: (mouse) => { dragging = true; updatePos(mouse.x, mouse.y) }
                            onPositionChanged: (mouse) => { if (dragging) updatePos(mouse.x, mouse.y) }
                            onReleased: dragging = false
                            function updatePos(mx, my) { if (alice) alice.setMeasurementPosition(Math.max(0, Math.min(1, mx / width)), Math.max(0, Math.min(1, my / height))) }
                        }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Actions
            ColumnLayout {
                Layout.fillWidth: true; Layout.margins: 8; spacing: 4
                CheckBox { text: "Test mode"; Material.accent: Theme.primary }
                Button {
                    text: "Record Point"; Layout.fillWidth: true; Material.background: Theme.primary
                    enabled: alice ? (alice.motorConnected && alice.realSenseConnected && alice.depth > 0 && alice.depthConfidence >= 0.5) : false
                    onClicked: {
                        if (!alice) return
                        calibrationPoints.push({ depth: alice.depth, motorPosition: alice.motorPosition, confidence: alice.depthConfidence })
                        calibrationPointsChanged()
                    }
                }
            }

            Item { Layout.fillHeight: true }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // CENTER: Data table (280px)
        CalibrationTable {
            Layout.preferredWidth: 280; Layout.fillHeight: true
            points: calibrationPoints
            onPointRemoved: (index) => { calibrationPoints.splice(index, 1); calibrationPointsChanged() }
            onExportRequested: exportDialog.open()
            onClearRequested: { calibrationPoints = []; calibrationPointsChanged() }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // RIGHT: Graph (flex)
        CalibrationGraph {
            Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: 12
            points: calibrationPoints
            currentMotorPos: alice ? alice.motorPosition : 0
        }
    }

    onVisibleChanged: { if (visible && alice) alice.focusMode = 0 }
}
