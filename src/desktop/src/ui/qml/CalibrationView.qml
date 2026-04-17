import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import QtQuick.Dialogs
import Alice.Renderers 1.0

Item {
    id: calibView
    property var calibrationPoints: []

    FileDialog {
        id: exportDialog
        title: "Export Calibration Mapping"
        fileMode: FileDialog.SaveFile
        nameFilters: ["JSON files (*.json)"]
        defaultSuffix: "json"
        onAccepted: {
            if (!alice) return
            alice.saveMappingToFile(selectedFile, calibView.calibrationPoints,
                                    "Calibration")
        }
    }

    // Force QML to detect array changes by reassigning
    function addPoint(pt) {
        var arr = calibrationPoints.slice()
        arr.push(pt)
        calibrationPoints = arr
    }
    function removePoint(idx) {
        var arr = calibrationPoints.slice()
        arr.splice(idx, 1)
        calibrationPoints = arr
    }
    function clearPoints() {
        calibrationPoints = []
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // LEFT: Motor + Previews — dp(400)
        ColumnLayout {
            Layout.preferredWidth: Theme.dp(400)
            Layout.minimumWidth: Theme.dp(400)
            Layout.maximumWidth: Theme.dp(400)
            Layout.fillHeight: true
            spacing: 0

            // Motor position — name matches OpsView.
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: motorSection.implicitHeight + Theme.dp(40)
                Layout.maximumHeight: motorSection.implicitHeight + Theme.dp(40)
                color: Theme.bg
                ColumnLayout {
                    id: motorSection; anchors.fill: parent
                    anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(20)
                    spacing: Theme.dp(10)
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true
                        motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (alice) alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Camera preview — fills height, max capped, high priority
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredHeight: Theme.dp(200)
                Layout.maximumHeight: Theme.dp(250)

                ColumnLayout {
                    id: cameraPreviewCol; anchors.fill: parent
                    anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(16)
                    spacing: Theme.dp(10)
                    SectionHeader { text: "CAMERA" }
                    Rectangle {
                        Layout.fillWidth: true; Layout.fillHeight: true
                        color: Theme.well; radius: Theme.radiusSm; clip: true

                        // Zoomable camera container
                        property real zoomLevel: 1.0
                        property real panX: 0.5
                        property real panY: 0.5

                        Item {
                            id: calibCamContainer
                            property real zw: parent.width * parent.zoomLevel
                            property real zh: parent.height * parent.zoomLevel
                            width: zw; height: zh
                            x: -(zw - parent.width) * parent.panX
                            y: -(zh - parent.height) * parent.panY

                            VideoRenderer {
                                anchors.fill: parent
                                source: alice.captureFrame
                                property bool hasFeed: alice ? alice.captureCardConnected : false
                                visible: true
                                opacity: hasFeed ? 1.0 : 0.0
                                scale: hasFeed ? 1.0 : 0.95
                                transformOrigin: Item.Center
                                Behavior on opacity { OpacityAnimator { duration: 350; easing.type: Easing.OutCubic } }
                                Behavior on scale { ScaleAnimator { duration: 350; easing.type: Easing.OutCubic } }
                            }
                            Label {
                                anchors.centerIn: parent; text: "No camera"
                                font.pixelSize: Theme.fontSizeMicro; color: Theme.textPlaceholder
                                property bool hasFeed: alice ? alice.captureCardConnected : false
                                visible: true
                                opacity: hasFeed ? 0.0 : 1.0
                                Behavior on opacity { OpacityAnimator { duration: 250; easing.type: Easing.OutCubic } }
                            }
                        }

                        MouseArea {
                            anchors.fill: parent
                            property bool isDragging: false
                            property real dragStartX: 0; property real dragStartY: 0
                            property real panStartX: 0; property real panStartY: 0
                            cursorShape: parent.zoomLevel > 1.0 ? Qt.OpenHandCursor : Qt.ArrowCursor

                            onPressed: (mouse) => {
                                if (parent.zoomLevel > 1.0) {
                                    isDragging = true; dragStartX = mouse.x; dragStartY = mouse.y
                                    panStartX = parent.panX; panStartY = parent.panY
                                }
                            }
                            onPositionChanged: (mouse) => {
                                if (isDragging) {
                                    parent.panX = Math.max(0, Math.min(1, panStartX - (mouse.x - dragStartX) / width))
                                    parent.panY = Math.max(0, Math.min(1, panStartY - (mouse.y - dragStartY) / height))
                                }
                            }
                            onReleased: isDragging = false
                            onWheel: (wheel) => {
                                if (wheel.angleDelta.y > 0) parent.zoomLevel = Math.min(4.0, parent.zoomLevel + 0.1)
                                else parent.zoomLevel = Math.max(1.0, parent.zoomLevel - 0.1)
                                if (parent.zoomLevel <= 1.0) { parent.panX = 0.5; parent.panY = 0.5 }
                            }
                        }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth preview — fills height, max capped, high priority
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredHeight: Theme.dp(200)
                Layout.maximumHeight: Theme.dp(250)

                ColumnLayout {
                    id: depthPreviewCol; anchors.fill: parent
                    anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(16)
                    spacing: Theme.dp(10)
                    RowLayout {
                        SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                        Row {
                            spacing: Theme.dp(6)
                            Text {
                                text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m" : "\u2014"
                                font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.Bold
                                color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                                Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                            }
                            Text {
                                visible: alice ? alice.depth > 0 : false
                                text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"
                                font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro
                                color: Theme.textSecondary
                                anchors.baseline: parent.children[0].baseline
                            }
                        }
                    }
                    Rectangle {
                        id: calibDepthRect
                        Layout.fillWidth: true; Layout.fillHeight: true; color: Theme.well; radius: Theme.radiusSm

                        // Letterbox the color feed so crosshair coordinates map
                        // to the actual video pixels (matches OpsView behaviour).
                        property real vidAspect: 16.0 / 9.0
                        property real fitW: Math.min(width, height * vidAspect)
                        property real fitH: fitW / vidAspect
                        property real vidX: (width - fitW) / 2
                        property real vidY: (height - fitH) / 2

                        VideoRenderer {
                            x: calibDepthRect.vidX; y: calibDepthRect.vidY
                            width: calibDepthRect.fitW; height: calibDepthRect.fitH
                            source: alice.colorFrame
                            property bool hasFeed: alice ? alice.realSenseConnected : false
                            visible: true
                            opacity: hasFeed ? 1.0 : 0.0
                            scale: hasFeed ? 1.0 : 0.95
                            transformOrigin: Item.Center
                            Behavior on opacity { OpacityAnimator { duration: 350; easing.type: Easing.OutCubic } }
                            Behavior on scale { ScaleAnimator { duration: 350; easing.type: Easing.OutCubic } }
                        }

                        // Double-stroke reticle matching OpsView (with lock pulse).
                        Item {
                            id: calibReticle
                            property real normX: Math.max(0, Math.min(1, alice ? alice.measureX : 0.5))
                            property real normY: Math.max(0, Math.min(1, alice ? alice.measureY : 0.5))
                            property real cx: calibDepthRect.vidX + normX * calibDepthRect.fitW
                            property real cy: calibDepthRect.vidY + normY * calibDepthRect.fitH
                            x: cx - 12; y: cy - 12
                            width: 24; height: 24
                            visible: alice ? alice.realSenseConnected : false

                            Behavior on x { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                            Behavior on y { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                            onNormXChanged: calibLockPulse.restart()
                            onNormYChanged: calibLockPulse.restart()

                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 24; height: 3; color: "#000000"; opacity: 0.6 }
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 3; height: 24; color: "#000000"; opacity: 0.6 }
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 22; height: 1; color: "#ffffff" }
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 22; color: "#ffffff" }
                            Rectangle { id: calibInnerRing; anchors.centerIn: parent; width: 8; height: 8; radius: 4; color: "transparent"; border.width: 1.5; border.color: "#ffffff"; transformOrigin: Item.Center }
                            Rectangle { anchors.centerIn: parent; width: 10; height: 10; radius: 5; color: "transparent"; border.width: 1; border.color: "#000000"; opacity: 0.5 }

                            SequentialAnimation {
                                id: calibLockPulse
                                NumberAnimation { target: calibInnerRing; property: "scale"; to: 0.75; duration: Theme.durationFast; easing.type: Easing.OutCubic }
                                NumberAnimation { target: calibInnerRing; property: "scale"; to: 1.0; duration: Theme.durationNormal; easing.type: Easing.OutBack }
                            }
                        }

                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.CrossCursor; property bool isDragging: false
                            onPressed: (mouse) => { isDragging = true; updatePos(mouse.x, mouse.y, true) }
                            onPositionChanged: (mouse) => { if (isDragging) updatePos(mouse.x, mouse.y, false) }
                            onReleased: isDragging = false
                            function updatePos(mx, my, isJump) {
                                if (!alice) return
                                var nx = Math.max(0, Math.min(1, (mx - calibDepthRect.vidX) / calibDepthRect.fitW))
                                var ny = Math.max(0, Math.min(1, (my - calibDepthRect.vidY) / calibDepthRect.fitH))
                                if (isJump) alice.processTap(nx, ny)
                                else alice.setMeasurementPosition(nx, ny)
                            }
                        }
                    }
                }
            }
            // Absorb overflow when both previews are at max height
            Item { Layout.fillWidth: true; Layout.fillHeight: true; Layout.preferredHeight: 0 }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Record Point footer — fixed height dp(72) so it matches the
            // Export Mapping footer in CalibrationTable (which uses the same
            // Layout.margins dp(16) + dp(40)-tall button footprint).
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Theme.dp(72)
                Layout.maximumHeight: Theme.dp(72)
                color: Theme.bg
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: Theme.dp(16); anchors.rightMargin: Theme.dp(16)
                    anchors.topMargin: Theme.dp(16); anchors.bottomMargin: Theme.dp(16)
                    spacing: Theme.dp(8)
                    CheckBox {
                        text: "Test mode"
                        Material.accent: Theme.primary
                        font.pixelSize: Theme.fontSizeMicro
                        Layout.alignment: Qt.AlignVCenter
                    }
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: Theme.dp(40)
                        Layout.alignment: Qt.AlignVCenter
                        radius: Theme.radiusSm
                        color: (alice && alice.motorConnected && alice.realSenseConnected && alice.depth > 0 && alice.depthConfidence >= 0.5) ? Theme.primary : Theme.surface
                        opacity: (alice && alice.motorConnected && alice.realSenseConnected && alice.depth > 0 && alice.depthConfidence >= 0.5) ? 1.0 : 0.4
                        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                        Behavior on opacity { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                        Text { anchors.centerIn: parent; text: "Record Point"; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold; color: "#fff" }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            enabled: alice ? (alice.motorConnected && alice.realSenseConnected && alice.depth > 0 && alice.depthConfidence >= 0.5) : false
                            onClicked: {
                                if (!alice) return
                                calibView.addPoint({ depth: alice.depth, motorPosition: alice.motorPosition, confidence: alice.depthConfidence })
                            }
                        }
                    }
                }
            }
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // CENTER: Data table
        CalibrationTable {
            Layout.preferredWidth: Theme.dp(560)
            Layout.minimumWidth: Theme.dp(360)
            Layout.maximumWidth: Theme.dp(600)
            Layout.fillHeight: true
            points: calibrationPoints
            onPointRemoved: (index) => calibView.removePoint(index)
            onExportRequested: exportDialog.open()
            onClearRequested: calibView.clearPoints()
        }

        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // RIGHT: Graph (flex)
        CalibrationGraph {
            Layout.fillWidth: true; Layout.fillHeight: true; Layout.margins: Theme.dp(20)
            points: calibrationPoints
            currentMotorPos: alice ? alice.motorPosition : 0
            currentDepth: alice ? alice.depth : 0
        }
    }

    onVisibleChanged: { if (visible && alice) alice.focusMode = 0 }
}
