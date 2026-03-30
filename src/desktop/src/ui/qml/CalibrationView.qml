import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: calibView

    property var calibrationPoints: []
    property bool testMode: false

    RowLayout {
        anchors.fill: parent
        anchors.margins: 12
        spacing: 12

        // Left panel: Motor control + recorded points (22%)
        ColumnLayout {
            Layout.preferredWidth: calibView.width * 0.22
            Layout.fillHeight: true
            spacing: 12

            Label {
                text: "MOTOR CONTROL"
                font.pixelSize: 11
                font.weight: Font.Bold
                font.letterSpacing: 1.5
                color: "#a09da6"
            }

            MotorSlider {
                Layout.fillWidth: true
                motorPos: alice ? alice.motorPosition : 0
                enabled: alice ? alice.motorConnected : false
                onMotorMoved: (pos) => { if (!alice) return; alice.setMotorPosition(pos) }
            }

            CheckBox {
                text: "Test mode"
                checked: testMode
                onToggled: testMode = checked
                Material.accent: "#d0bcff"
            }

            Button {
                text: "Record Point"
                Layout.fillWidth: true
                enabled: alice ? (alice.motorConnected && alice.realSenseConnected && !testMode &&
                         alice.depth > 0 && alice.depthConfidence >= 0.5) : false
                Material.background: "#6650a4"
                onClicked: {
                    if (!alice) return;
                    calibrationPoints.push({
                        depth: alice.depth,
                        motorPosition: alice.motorPosition,
                        confidence: alice.depthConfidence
                    })
                    calibrationPointsChanged()
                }
            }

            Label {
                text: calibrationPoints.length >= 3
                      ? calibrationPoints.length + " points recorded"
                      : (3 - calibrationPoints.length) + " more needed"
                color: calibrationPoints.length >= 3 ? "#64ff64" : "#ffc832"
                font.pixelSize: 12
            }

            // Recorded points list
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: "#1e1c22"
                radius: 4

                ListView {
                    anchors.fill: parent
                    anchors.margins: 4
                    model: calibrationPoints
                    clip: true
                    delegate: RowLayout {
                        width: parent ? parent.width : 0
                        Label {
                            text: modelData.depth.toFixed(2) + "m"
                            font.family: "RobotoMono"
                            font.pixelSize: 11
                            color: "#e6e1e5"
                        }
                        Item { Layout.fillWidth: true }
                        Label {
                            text: "→ " + modelData.motorPosition
                            font.family: "RobotoMono"
                            font.pixelSize: 11
                            color: "#d0bcff"
                        }
                    }
                }
            }
        }

        // Center panel: Previews (56%)
        ColumnLayout {
            Layout.preferredWidth: calibView.width * 0.56
            Layout.fillHeight: true
            spacing: 8

            // Camera preview — capture card (BMPCC) feed
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: "#000000"
                radius: 4

                VideoRenderer {
                    anchors.fill: parent
                    source: alice ? alice.captureFrame : null
                    visible: alice ? alice.captureCardConnected : false
                }

                Label {
                    anchors.centerIn: parent
                    text: "No camera"
                    font.pixelSize: 14
                    color: "#888888"
                    visible: alice ? !alice.captureCardConnected : true
                }

                Label {
                    anchors.top: parent.top
                    anchors.left: parent.left
                    anchors.margins: 8
                    text: "Camera Preview"
                    font.pixelSize: 11
                    color: "#a09da6"
                }
            }

            // Depth preview — RealSense RGB with depth readout and crosshair, 4:3 aspect
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: width * 3 / 4
                Layout.maximumHeight: 360
                color: "#000000"
                radius: 4
                clip: true

                // No-device placeholder
                Label {
                    anchors.centerIn: parent
                    text: "No depth"
                    font.pixelSize: 14
                    color: "#888888"
                    visible: !(alice && alice.realSenseConnected)
                }

                // RealSense RGB stream
                VideoRenderer {
                    id: calibDepthPanel
                    anchors.centerIn: parent
                    width: Math.min(parent.width, parent.height * 4 / 3)
                    height: width * 3 / 4
                    source: alice ? alice.colorFrame : null
                    visible: alice ? alice.realSenseConnected : false
                }

                // Depth readout text overlay
                Rectangle {
                    anchors.right: calibDepthPanel.right
                    anchors.bottom: calibDepthPanel.bottom
                    anchors.margins: 8
                    width: calibDepthReadout.implicitWidth + 12
                    height: calibDepthReadout.implicitHeight + 8
                    radius: 4
                    color: "#a0000000"
                    visible: alice ? (alice.realSenseConnected && alice.depth > 0) : false

                    Label {
                        id: calibDepthReadout
                        anchors.centerIn: parent
                        text: alice ? (alice.depth.toFixed(2) + "m (" + Math.round(alice.depthConfidence * 100) + "%)") : ""
                        font.family: "RobotoMono"
                        font.pixelSize: 12
                        color: alice ? (alice.depthConfidence > 0.7 ? "#64ff64" : "#ffc832") : "#ffc832"
                    }
                }

                // Crosshair for measurement position
                Item {
                    id: calibCrosshair
                    property real normX: 0.5
                    property real normY: 0.5
                    x: calibDepthPanel.x + normX * calibDepthPanel.width - 10
                    y: calibDepthPanel.y + normY * calibDepthPanel.height - 10
                    width: 20
                    height: 20
                    visible: alice ? alice.realSenseConnected : false

                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        anchors.verticalCenter: parent.verticalCenter
                        width: 20; height: 2; color: "#ffffff"
                    }
                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        anchors.verticalCenter: parent.verticalCenter
                        width: 2; height: 20; color: "#ffffff"
                    }
                    Rectangle {
                        anchors.centerIn: parent
                        width: 6; height: 6; radius: 3
                        color: "transparent"
                        border.color: "#ffffff"; border.width: 1
                    }
                }

                // Unified mouse area for click and drag on depth panel
                MouseArea {
                    anchors.fill: calibDepthPanel
                    cursorShape: Qt.CrossCursor

                    property bool dragging: false

                    onPressed: (mouse) => {
                        dragging = true
                        updatePosition(mouse.x, mouse.y)
                    }
                    onPositionChanged: (mouse) => {
                        if (!dragging) return
                        updatePosition(mouse.x, mouse.y)
                    }
                    onReleased: dragging = false

                    function updatePosition(mx, my) {
                        if (!alice) return
                        let nx = Math.max(0, Math.min(1, mx / calibDepthPanel.width))
                        let ny = Math.max(0, Math.min(1, my / calibDepthPanel.height))
                        calibCrosshair.normX = nx
                        calibCrosshair.normY = ny
                        alice.setMeasurementPosition(nx, ny)
                    }
                }

                Label {
                    anchors.top: parent.top
                    anchors.left: parent.left
                    anchors.margins: 8
                    text: "Depth Preview"
                    font.pixelSize: 11
                    color: "#a09da6"
                }
            }
        }

        // Right panel: Calibration graph (22%)
        Rectangle {
            Layout.preferredWidth: calibView.width * 0.22
            Layout.fillHeight: true
            color: "#1e1c22"
            radius: 4

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 8

                Label {
                    text: "CALIBRATION GRAPH"
                    font.pixelSize: 11
                    font.weight: Font.Bold
                    font.letterSpacing: 1.5
                    color: "#a09da6"
                }

                // Graph canvas
                Canvas {
                    id: graphCanvas
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    onPaint: {
                        var ctx = getContext("2d")
                        ctx.clearRect(0, 0, width, height)

                        // Background grid
                        ctx.strokeStyle = "#3b383e"
                        ctx.lineWidth = 0.5
                        for (var i = 0; i <= 4; i++) {
                            var x = (i / 4) * width
                            var y = (i / 4) * height
                            ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, height); ctx.stroke()
                            ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke()
                        }

                        // Plot points
                        if (calibrationPoints.length === 0) return

                        var maxDepth = 10.0
                        var maxMotor = 4095

                        ctx.fillStyle = "#d0bcff"
                        for (var j = 0; j < calibrationPoints.length; j++) {
                            var pt = calibrationPoints[j]
                            var px = (pt.motorPosition / maxMotor) * width
                            var py = height - (pt.depth / maxDepth) * height
                            ctx.beginPath()
                            ctx.arc(px, py, 5, 0, 2 * Math.PI)
                            ctx.fill()
                        }

                        // Connect with line
                        if (calibrationPoints.length >= 2) {
                            var sorted = calibrationPoints.slice().sort((a, b) => a.depth - b.depth)
                            ctx.strokeStyle = "#d0bcff"
                            ctx.lineWidth = 1.5
                            ctx.beginPath()
                            for (var k = 0; k < sorted.length; k++) {
                                var sp = sorted[k]
                                var sx = (sp.motorPosition / maxMotor) * width
                                var sy = height - (sp.depth / maxDepth) * height
                                if (k === 0) ctx.moveTo(sx, sy)
                                else ctx.lineTo(sx, sy)
                            }
                            ctx.stroke()
                        }
                    }
                }

                // Export button
                Button {
                    text: "Export Mapping"
                    Layout.fillWidth: true
                    enabled: calibrationPoints.length >= 3
                    Material.background: "#6650a4"
                    onClicked: exportDialog.open()
                }

                Button {
                    text: "Clear All"
                    Layout.fillWidth: true
                    flat: true
                    Material.foreground: "#f2b8b5"
                    onClicked: {
                        calibrationPoints = []
                        graphCanvas.requestPaint()
                    }
                }
            }
        }
    }

    onCalibrationPointsChanged: graphCanvas.requestPaint()

    // Switch to Manual Focus when entering calibration view
    onVisibleChanged: {
        if (visible && alice) {
            alice.focusMode = 0  // MF
        }
    }
}
