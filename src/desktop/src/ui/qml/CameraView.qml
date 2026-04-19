import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: cameraView

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // Center: Video feed + depth panel stacked vertically
        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            // Camera feed with overlays
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true

                // RGB video feed — capture card (BMPCC) only
                VideoRenderer {
                    id: videoFeed
                    anchors.fill: parent
                    source: alice ? alice.captureFrame : null
                    visible: alice ? alice.captureCardConnected : false
                }

                // Placeholder when no capture card is connected
                Rectangle {
                    anchors.fill: parent
                    color: "#1a1a1a"
                    visible: alice ? !alice.captureCardConnected : true

                    Label {
                        anchors.centerIn: parent
                        text: "No camera"
                        font.pixelSize: 18
                        color: "#888888"
                    }
                }

                // Face detection overlay (crosshair disabled — depth panel has its own)
                FaceOverlay {
                    id: faceOverlay
                    anchors.fill: parent
                    faces: alice ? alice.trackedFaces() : []
                    showCrosshair: false
                    visible: alice ? alice.focusMode === 3 : false  // FACE_TRACKING
                }

                // Tap-to-focus handler
                MouseArea {
                    anchors.fill: parent
                    onClicked: (mouse) => {
                        if (!alice) return;
                        let nx = mouse.x / width
                        let ny = mouse.y / height
                        alice.processTap(nx, ny)
                    }
                }

                // RGB Histogram overlay (top-right corner of camera preview)
                HistogramRenderer {
                    anchors.top: parent.top
                    anchors.right: parent.right
                    anchors.margins: 12
                    width: 200
                    height: 120
                    source: alice ? (alice.captureCardConnected ? alice.captureFrame : alice.colorFrame) : null
                    visible: source !== null
                    opacity: 0.9
                }
            }

                // Depth panel — always visible, 4:3 aspect ratio matching RealSense 640x480
            Item {
                Layout.fillWidth: true
                Layout.preferredHeight: width * 3 / 4
                Layout.maximumHeight: 240

                // Dark background when no RealSense connected
                Rectangle {
                    anchors.fill: parent
                    color: "#1a1a1a"
                    visible: alice ? (!alice.realSenseConnected && alice.depth <= 0) : true

                    Label {
                        anchors.centerIn: parent
                        text: "No depth"
                        font.pixelSize: 14
                        color: "#888888"
                    }
                }

                // Number-only depth display when no RealSense but depth data is available (remote sync)
                Rectangle {
                    anchors.fill: parent
                    color: "#1a1a1a"
                    visible: alice ? (!alice.realSenseConnected && alice.depth > 0) : false

                    Column {
                        anchors.centerIn: parent
                        spacing: 8

                        Label {
                            anchors.horizontalCenter: parent.horizontalCenter
                            text: alice ? alice.depth.toFixed(2) + "m" : ""
                            font.family: "RobotoMono"
                            font.pixelSize: 36
                            font.bold: true
                            color: alice ? (alice.depthConfidence > 0.7 ? "#64ff64" : "#ffc832") : "#ffc832"
                        }

                        Label {
                            anchors.horizontalCenter: parent.horizontalCenter
                            text: alice ? Math.round(alice.depthConfidence * 100) + "% confidence" : ""
                            font.family: "RobotoMono"
                            font.pixelSize: 14
                            color: "#aaaaaa"
                        }
                    }
                }

                // RealSense RGB stream (no depth colormap overlay)
                VideoRenderer {
                    id: depthPanel
                    anchors.centerIn: parent
                    width: Math.min(parent.width, parent.height * 4 / 3)
                    height: width * 3 / 4
                    source: alice ? alice.colorFrame : null
                    visible: alice ? alice.realSenseConnected : false
                }

                // Depth readout text overlay
                Rectangle {
                    anchors.right: depthPanel.right
                    anchors.bottom: depthPanel.bottom
                    anchors.margins: 8
                    width: depthReadout.implicitWidth + 12
                    height: depthReadout.implicitHeight + 8
                    radius: 4
                    color: "#a0000000"
                    visible: alice ? (alice.realSenseConnected && alice.depth > 0) : false

                    Label {
                        id: depthReadout
                        anchors.centerIn: parent
                        text: alice ? (alice.depth.toFixed(2) + "m (" + Math.round(alice.depthConfidence * 100) + "%)") : ""
                        font.family: "RobotoMono"
                        font.pixelSize: 12
                        color: alice ? (alice.depthConfidence > 0.7 ? "#64ff64" : "#ffc832") : "#ffc832"
                    }
                }

                // Crosshair for measurement position
                // Positioned via normX/normY bindings; mouse area covers depth panel
                // to handle both click and drag without binding conflicts.
                Item {
                    id: crosshair
                    property real normX: alice ? alice.measureX : 0.5
                    property real normY: alice ? alice.measureY : 0.5
                    x: depthPanel.x + normX * depthPanel.width - 10
                    y: depthPanel.y + normY * depthPanel.height - 10
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
                    anchors.fill: depthPanel
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
                        let nx = Math.max(0, Math.min(1, mx / depthPanel.width))
                        let ny = Math.max(0, Math.min(1, my / depthPanel.height))
                        alice.setMeasurementPosition(nx, ny)
                    }
                }
            }
        }

        // Right: Control panel
        ControlPanel {
            Layout.preferredWidth: 280
            Layout.fillHeight: true
        }
    }
}
