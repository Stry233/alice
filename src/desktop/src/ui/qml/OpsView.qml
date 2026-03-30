import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.Renderers 1.0

Item {
    id: opsView

    // Responsive modes: compact (<1280), standard (1280-1599), wide (1600+)
    readonly property bool compactMode: width < Theme.breakpointStandard
    readonly property bool wideMode: width >= Theme.breakpointWide

    // Camera zoom/pan state
    property real zoomLevel: 1.0
    property real panX: 0.5
    property real panY: 0.5

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            // LEFT sidebar (wide mode only — controls move here)
            ColumnLayout {
                visible: wideMode
                Layout.preferredWidth: Theme.sidebarWide
                Layout.fillHeight: true
                spacing: 0

                // Motor
                Rectangle {
                    Layout.fillWidth: true; Layout.preferredHeight: motorCol.implicitHeight + 20
                    color: Theme.bg; border.width: 0

                    ColumnLayout {
                        id: motorCol
                        anchors.fill: parent; anchors.margins: 10; spacing: 8

                        SectionHeader { text: "MOTOR POSITION" }
                        MotorSlider {
                            Layout.fillWidth: true
                            motorPos: alice ? alice.motorPosition : 0
                            enabled: alice ? alice.motorConnected : false
                            onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                        }
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                // Depth feed
                Item {
                    Layout.fillWidth: true; Layout.fillHeight: true

                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 10; spacing: 6

                        RowLayout {
                            SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                            Text {
                                text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m " + Math.round(alice.depthConfidence * 100) + "%" : "—"
                                font.family: Theme.fontFamilyMono; font.pixelSize: 11; font.weight: Font.DemiBold
                                color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                            }
                        }

                        Item {
                            Layout.fillWidth: true; Layout.fillHeight: true

                            Rectangle {
                                anchors.fill: parent; color: Theme.well; radius: Theme.radiusSm

                                VideoRenderer {
                                    anchors.centerIn: parent
                                    width: Math.min(parent.width, parent.height * 4 / 3)
                                    height: width * 3 / 4
                                    source: alice ? alice.colorFrame : null
                                    visible: alice ? alice.realSenseConnected : false
                                }

                                // Crosshair
                                Item {
                                    property real normX: alice ? alice.measureX : 0.5
                                    property real normY: alice ? alice.measureY : 0.5
                                    x: normX * parent.width - 8; y: normY * parent.height - 8
                                    width: 16; height: 16; visible: alice ? alice.realSenseConnected : false
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 16; height: 1; color: "#fff" }
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 16; color: "#fff" }
                                }

                                MouseArea {
                                    anchors.fill: parent; cursorShape: Qt.CrossCursor
                                    property bool dragging: false
                                    onPressed: (mouse) => { dragging = true; updatePos(mouse.x, mouse.y) }
                                    onPositionChanged: (mouse) => { if (dragging) updatePos(mouse.x, mouse.y) }
                                    onReleased: dragging = false
                                    function updatePos(mx, my) {
                                        if (!alice) return
                                        alice.setMeasurementPosition(Math.max(0, Math.min(1, mx / width)), Math.max(0, Math.min(1, my / height)))
                                    }
                                }
                            }
                        }
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                // Calibration quick access
                Rectangle {
                    Layout.fillWidth: true; Layout.preferredHeight: calibCol.implicitHeight + 20
                    color: Theme.bg

                    ColumnLayout {
                        id: calibCol
                        anchors.fill: parent; anchors.margins: 10; spacing: 6

                        SectionHeader { text: "CALIBRATION" }
                        Label { visible: alice ? alice.hasMapping : false; text: alice ? ("Active: " + alice.mappingName) : ""; font.pixelSize: 10; color: Theme.success }
                        ComboBox {
                            Layout.fillWidth: true; model: ["Select Preset...", "Linear", "Logarithmic", "Portrait", "Landscape", "Macro"]
                            Material.accent: Theme.primary
                            onActivated: (index) => { if (alice && index > 0) alice.loadPreset(index - 1) }
                        }
                    }
                }
            }

            Rectangle { visible: wideMode; Layout.fillHeight: true; width: 1; color: Theme.border }

            // CENTER: Camera feed (always present, flex)
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true

                Rectangle {
                    anchors.fill: parent; color: Theme.well

                    VideoRenderer {
                        id: cameraFeed
                        anchors.fill: parent
                        source: alice ? alice.captureFrame : null
                        visible: alice ? alice.captureCardConnected : false
                    }

                    // Placeholder
                    Label {
                        anchors.centerIn: parent; text: "No camera"; font.pixelSize: 18; color: Theme.textPlaceholder
                        visible: alice ? !alice.captureCardConnected : true
                    }

                    // Face overlay
                    FaceOverlay {
                        anchors.fill: parent
                        faces: alice ? alice.trackedFaces() : []
                        showCrosshair: false
                        visible: alice ? alice.focusMode === 3 : false
                    }

                    // AF status overlay (top-left)
                    Rectangle {
                        anchors.top: parent.top; anchors.left: parent.left; anchors.margins: 8
                        width: afLabel.implicitWidth + 16; height: 20; radius: Theme.radiusSm
                        color: alice && alice.activelyFocusing ? Qt.rgba(0.055, 0.231, 0.173, 0.9) : Qt.rgba(0.106, 0.125, 0.145, 0.9)
                        border.width: 1; border.color: alice && alice.activelyFocusing ? Theme.success : Theme.border
                        visible: alice ? alice.focusMode > 0 : false
                        Text {
                            id: afLabel; anchors.centerIn: parent
                            text: alice && alice.activelyFocusing ? ["", "AF-S LOCKED", "AF-C LOCKED", "AF-F LOCKED"][alice.focusMode] : ["", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
                            font.pixelSize: 10; font.weight: Font.DemiBold; color: alice && alice.activelyFocusing ? Theme.success : Theme.textSecondary
                        }
                    }

                    // Histogram (top-right)
                    HistogramRenderer {
                        anchors.top: parent.top; anchors.right: parent.right; anchors.margins: 8
                        width: 130; height: 75
                        source: alice ? (alice.captureCardConnected ? alice.captureFrame : alice.colorFrame) : null
                        visible: source !== null; opacity: 0.92
                    }

                    // Zoom toolbar (bottom-left)
                    ZoomToolbar {
                        anchors.bottom: parent.bottom; anchors.left: parent.left; anchors.margins: 8
                        zoomLevel: opsView.zoomLevel
                        onZoomIn: opsView.zoomLevel = Math.min(Theme.zoomMax, opsView.zoomLevel + Theme.zoomStep)
                        onZoomOut: opsView.zoomLevel = Math.max(Theme.zoomMin, opsView.zoomLevel - Theme.zoomStep)
                        onZoomTo: (level) => { opsView.zoomLevel = Math.max(Theme.zoomMin, Math.min(Theme.zoomMax, level)) }
                        onFitRequested: opsView.zoomLevel = 1.0
                    }

                    // MiniMap (bottom-right)
                    MiniMap {
                        anchors.bottom: parent.bottom; anchors.right: parent.right; anchors.margins: 8
                        zoomLevel: opsView.zoomLevel; panX: opsView.panX; panY: opsView.panY
                    }

                    // Tap-to-focus / zoom scroll
                    MouseArea {
                        anchors.fill: parent; z: -1
                        onClicked: (mouse) => { if (!alice || opsView.zoomLevel > 1.0) return; alice.processTap(mouse.x / width, mouse.y / height) }
                        onWheel: (wheel) => {
                            if (wheel.angleDelta.y > 0) opsView.zoomLevel = Math.min(Theme.zoomMax, opsView.zoomLevel + 0.1)
                            else opsView.zoomLevel = Math.max(Theme.zoomMin, opsView.zoomLevel - 0.1)
                        }
                    }
                }
            }

            Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

            // RIGHT sidebar
            ColumnLayout {
                Layout.preferredWidth: compactMode ? Theme.sidebarNarrow : (wideMode ? Theme.telemetryColumnWidth : Theme.sidebarStandard)
                Layout.fillHeight: true
                spacing: 0

                // Compact: motor + numeric depth only
                // Standard: motor + depth feed + calibration
                // Wide: telemetry + system + log
                Loader {
                    Layout.fillWidth: true; Layout.fillHeight: true
                    sourceComponent: compactMode ? compactRightComponent : (wideMode ? wideRightComponent : standardRightComponent)
                }
            }
        }

        // Bottom strip
        BottomStrip { Layout.fillWidth: true }
    }

    // Compact mode right sidebar (1024-1279px): motor + numeric depth only
    Component {
        id: compactRightComponent

        ColumnLayout {
            spacing: 0

            // Motor
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: cmpMotorCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: cmpMotorCol; anchors.fill: parent; anchors.margins: 10; spacing: 8
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth numeric readout (no video feed)
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: cmpDepthCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: cmpDepthCol; anchors.fill: parent; anchors.margins: 10; spacing: 8
                    SectionHeader { text: "DEPTH" }
                    Text {
                        text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + " m" : "—"
                        font.family: Theme.fontFamilyMono; font.pixelSize: 24; font.weight: Font.Bold
                        color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                        Layout.alignment: Qt.AlignHCenter
                    }
                    Text {
                        text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "% confidence"
                        font.family: Theme.fontFamilyMono; font.pixelSize: 11; color: Theme.textSecondary
                        Layout.alignment: Qt.AlignHCenter
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Focus mode indicator
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: 40; color: Theme.bg
                Text {
                    anchors.centerIn: parent
                    text: alice && alice.activelyFocusing ? ["MF", "AF-S LOCKED", "AF-C LOCKED", "AF-F LOCKED"][alice.focusMode] : ["MF", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
                    font.family: Theme.fontFamilyMono; font.pixelSize: 13; font.weight: Font.DemiBold
                    color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary
                }
            }

            Item { Layout.fillHeight: true }
        }
    }

    // Standard mode right sidebar content
    Component {
        id: standardRightComponent

        ColumnLayout {
            spacing: 0

            // Motor
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: stdMotorCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: stdMotorCol; anchors.fill: parent; anchors.margins: 10; spacing: 8
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth feed
            Item {
                Layout.fillWidth: true; Layout.fillHeight: true
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: 10; spacing: 6
                    RowLayout {
                        SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                        Text {
                            text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m " + Math.round(alice.depthConfidence * 100) + "%" : "—"
                            font.family: Theme.fontFamilyMono; font.pixelSize: 11; font.weight: Font.DemiBold
                            color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                        }
                    }
                    Item {
                        Layout.fillWidth: true; Layout.fillHeight: true
                        Rectangle {
                            anchors.fill: parent; color: Theme.well; radius: Theme.radiusSm
                            VideoRenderer {
                                anchors.centerIn: parent; width: Math.min(parent.width, parent.height * 4 / 3); height: width * 3 / 4
                                source: alice ? alice.colorFrame : null; visible: alice ? alice.realSenseConnected : false
                            }
                            Item {
                                property real normX: alice ? alice.measureX : 0.5; property real normY: alice ? alice.measureY : 0.5
                                x: normX * parent.width - 8; y: normY * parent.height - 8; width: 16; height: 16; visible: alice ? alice.realSenseConnected : false
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 16; height: 1; color: "#fff" }
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 16; color: "#fff" }
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
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Calibration
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: stdCalibCol.implicitHeight + 20; color: Theme.bg
                ColumnLayout {
                    id: stdCalibCol; anchors.fill: parent; anchors.margins: 10; spacing: 6
                    SectionHeader { text: "CALIBRATION" }
                    Label { visible: alice ? alice.hasMapping : false; text: alice ? ("Active: " + alice.mappingName) : ""; font.pixelSize: 10; color: Theme.success }
                    ComboBox { Layout.fillWidth: true; model: ["Select Preset...", "Linear", "Logarithmic", "Portrait", "Landscape", "Macro"]; Material.accent: Theme.primary; onActivated: (index) => { if (alice && index > 0) alice.loadPreset(index - 1) } }
                }
            }
        }
    }

    // Wide mode right sidebar content
    Component {
        id: wideRightComponent

        ColumnLayout {
            spacing: 0

            // Telemetry
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: telGrid.implicitHeight + 20; color: Theme.bg
                TelemetryGrid { id: telGrid; anchors.fill: parent; anchors.margins: 10 }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // System
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: sysPanel.implicitHeight + 20; color: Theme.bg
                SystemMonitorPanel { id: sysPanel; anchors.fill: parent; anchors.margins: 10 }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Log
            LogDisplay { Layout.fillWidth: true; Layout.fillHeight: true; messages: alice ? alice.logMessages : [] }
        }
    }
}
