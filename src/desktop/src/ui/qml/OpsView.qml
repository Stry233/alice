import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import QtQuick.Dialogs
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
                Layout.minimumWidth: Theme.sidebarWide
                Layout.maximumWidth: Theme.sidebarWide
                Layout.fillHeight: true
                spacing: 0

                // Motor — content-driven height
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: motorCol.implicitHeight + Theme.dp(40)
                    Layout.maximumHeight: Layout.preferredHeight
                    color: Theme.bg; border.width: 0

                    ColumnLayout {
                        id: motorCol
                        anchors.fill: parent
                        anchors.leftMargin: Theme.dp(24); anchors.rightMargin: Theme.dp(24)
                        anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(16)
                        spacing: Theme.dp(12)

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

                // Depth feed — max height capped for 16:9 fill
                Item {
                    Layout.fillWidth: true; Layout.fillHeight: true; Layout.preferredHeight: Theme.dp(240)
                    Layout.minimumHeight: Theme.dp(120)
                    Layout.maximumHeight: Theme.dp(323)

                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: Theme.dp(20); spacing: Theme.dp(10)

                        RowLayout {
                            SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                            Row {
                                spacing: Theme.dp(6)
                                Text {
                                    id: wideDepthText
                                    text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m" : "—"
                                    font.family: Theme.fontFamilyMono; font.pixelSize: Theme.dp(22); font.weight: Font.Bold
                                    color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                                    Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                                }
                                Text {
                                    id: wideConfText
                                    visible: alice ? alice.depth > 0 : false
                                    // Reserve a fixed 4-char ("100%") slot so the
                                    // "99%"→"100%" transition doesn't widen the row and
                                    // push the green depth number left.
                                    horizontalAlignment: Text.AlignRight
                                    width: wideConfMetrics.advanceWidth
                                    text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"
                                    font.family: Theme.fontFamilyMono; font.pixelSize: Theme.dp(18); font.weight: Font.Normal
                                    color: Theme.textSecondary
                                    anchors.baseline: wideDepthText.baseline
                                    TextMetrics {
                                        id: wideConfMetrics
                                        font.family: Theme.fontFamilyMono
                                        font.pixelSize: Theme.dp(18)
                                        text: "100%"
                                    }
                                }
                            }
                        }

                        Item {
                            id: wideDepthRow
                            Layout.fillWidth: true; Layout.fillHeight: true

                            // LiDAR waveform sidebar — ~15 % of the depth
                            // panel width, gated on the autofocus pipeline
                            // being ready (mapping loaded + RealSense
                            // streaming). Shown in every focus mode
                            // including MF so the operator always has a
                            // live oscilloscope view of what the lens is
                            // pointed at vs. where it's actually focused.
                            readonly property bool lidarVisible:
                                alice && alice.hasMapping && alice.realSenseConnected
                            readonly property int lidarTargetWidth:
                                Math.max(Theme.dp(92),
                                         Math.round(wideDepthRow.width * 0.15))
                            // Animated width actually applied to the
                            // sidebar (and symmetrically subtracted from
                            // the depth preview's rightMargin). Drives
                            // both the slide-in of the waveform and the
                            // shrink of the depth preview so they stay
                            // synchronised frame-for-frame.
                            readonly property int lidarWidth: lidarVisible ? lidarTargetWidth : 0
                            readonly property int lidarRightMargin: lidarWidth > 0
                                ? (lidarWidth + Theme.dp(6))
                                : 0

                            Rectangle {
                                id: wideDepthRect
                                anchors.top: parent.top
                                anchors.bottom: parent.bottom
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.rightMargin: wideDepthRow.lidarRightMargin
                                // Shrink/grow together with the sibling
                                // waveform. Theme.durationNormal +
                                // OutCubic matches the rest of the app's
                                // panel transitions (BadgePopover slide,
                                // ModeToggle colour crossfade, etc.).
                                Behavior on anchors.rightMargin {
                                    NumberAnimation {
                                        duration: Theme.durationNormal
                                        easing.type: Easing.OutCubic
                                    }
                                }
                                color: Theme.well; radius: Theme.radiusSm

                                // Use the VideoRenderer to detect actual aspect ratio
                                // Default 16:9; updates when renderer gets its first frame
                                property real vidAspect: 16.0 / 9.0
                                property real fitW: Math.min(width, height * vidAspect)
                                property real fitH: fitW / vidAspect
                                property real vidX: (width - fitW) / 2
                                property real vidY: (height - fitH) / 2

                                // Fade + scale enter/exit matching Android CameraPreview.kt:
                                //   in  — 350ms fade + scale 0.95 → 1.0 OutCubic
                                //   out — 350ms fade + scale 1.0 → 0.95 OutCubic
                                //
                                // Runs via OpacityAnimator / ScaleAnimator which
                                // execute on the scene graph's render thread rather
                                // than the main GUI thread, so the animation stays
                                // smooth even while the backend is mid-burst handling
                                // the camera-connect signal, logging, and sync
                                // broadcasts. `visible` is held true unconditionally
                                // because Animators write to the scene graph opacity
                                // node directly, which means the QML `opacity`
                                // property jumps to its target immediately — a
                                // `visible: opacity > 0.01` guard would therefore hide
                                // the item before the render-thread fade could play.
                                VideoRenderer {
                                    id: wideRsFeed
                                    x: wideDepthRect.vidX; y: wideDepthRect.vidY
                                    width: wideDepthRect.fitW; height: wideDepthRect.fitH
                                    source: alice.colorFrame
                                    property bool hasFeed: alice ? alice.realSenseConnected : false
                                    visible: true
                                    opacity: hasFeed ? 1.0 : 0.0
                                    scale: hasFeed ? 1.0 : 0.95
                                    transformOrigin: Item.Center
                                    Behavior on opacity { OpacityAnimator { duration: 350; easing.type: Easing.OutCubic } }
                                    Behavior on scale { ScaleAnimator { duration: 350; easing.type: Easing.OutCubic } }
                                }

                                // Face overlay — the detector runs on the RealSense color
                                // feed, so this is the only place where the bboxes are
                                // in-register with what the user sees.
                                FaceOverlay {
                                    anchors.fill: parent
                                    vidX: wideDepthRect.vidX
                                    vidY: wideDepthRect.vidY
                                    vidW: wideDepthRect.fitW
                                    vidH: wideDepthRect.fitH
                                    faces: alice ? alice.trackedFacesList : []
                                    visible: alice ? (alice.focusMode === 3 && alice.realSenseConnected) : false
                                    onFaceClicked: (tid) => { if (alice) alice.selectFace(tid) }
                                }

                                // Double-stroke reticle. Hidden in AF-F mode —
                                // the face tracker retargets the measurement
                                // point automatically, so the manual crosshair
                                // would just fight with it.
                                //
                                // Motion: translates to a new (cx, cy) over
                                // 150ms OutCubic. When the normalized target
                                // changes, the center ring briefly scales down
                                // to ~0.75× (rack-focus feel) then snaps back
                                // with a slight overshoot to confirm lock.
                                Item {
                                    id: wideReticle
                                    property real normX: Math.max(0, Math.min(1, alice ? alice.measureX : 0.5))
                                    property real normY: Math.max(0, Math.min(1, alice ? alice.measureY : 0.5))
                                    property real cx: wideDepthRect.vidX + normX * wideDepthRect.fitW
                                    property real cy: wideDepthRect.vidY + normY * wideDepthRect.fitH
                                    x: cx - 12; y: cy - 12
                                    width: 24; height: 24
                                    visible: alice ? (alice.realSenseConnected && alice.focusMode !== 3) : false

                                    Behavior on x { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                                    Behavior on y { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                                    onNormXChanged: wideLockPulse.restart()
                                    onNormYChanged: wideLockPulse.restart()

                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 24; height: 3; color: "#000000"; opacity: 0.6 }
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 3; height: 24; color: "#000000"; opacity: 0.6 }
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 22; height: 1; color: "#ffffff" }
                                    Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 22; color: "#ffffff" }
                                    Rectangle { id: wideInnerRing; anchors.centerIn: parent; width: 8; height: 8; radius: 4; color: "transparent"; border.width: 1.5; border.color: "#ffffff"; transformOrigin: Item.Center }
                                    Rectangle { anchors.centerIn: parent; width: 10; height: 10; radius: 5; color: "transparent"; border.width: 1; border.color: "#000000"; opacity: 0.5 }

                                    SequentialAnimation {
                                        id: wideLockPulse
                                        NumberAnimation { target: wideInnerRing; property: "scale"; to: 0.75; duration: Theme.durationFast; easing.type: Easing.OutCubic }
                                        NumberAnimation { target: wideInnerRing; property: "scale"; to: 1.0; duration: Theme.durationNormal; easing.type: Easing.OutBack }
                                    }
                                }

                                MouseArea {
                                    anchors.fill: parent
                                    enabled: alice ? alice.focusMode !== 3 : true  // no manual tap in AF-F
                                    cursorShape: enabled ? Qt.CrossCursor : Qt.ArrowCursor
                                    property bool isDragging: false
                                    // Press → processTap so AF-C's focusX_/Y_ gate updates.
                                    // Drag → setMeasurementPosition so inspecting depth
                                    // elsewhere doesn't hijack AF-C's target.
                                    onPressed: (mouse) => { isDragging = true; updatePos(mouse.x, mouse.y, true) }
                                    onPositionChanged: (mouse) => { if (isDragging) updatePos(mouse.x, mouse.y, false) }
                                    onReleased: isDragging = false
                                    function updatePos(mx, my, isJump) {
                                        if (!alice) return
                                        var nx = Math.max(0, Math.min(1, (mx - wideDepthRect.vidX) / wideDepthRect.fitW))
                                        var ny = Math.max(0, Math.min(1, (my - wideDepthRect.vidY) / wideDepthRect.fitH))
                                        if (isJump) alice.processTap(nx, ny)
                                        else alice.setMeasurementPosition(nx, ny)
                                    }
                                }
                            }

                            LidarWaveform {
                                id: wideLidar
                                anchors.top: parent.top
                                anchors.bottom: parent.bottom
                                anchors.right: parent.right
                                width: wideDepthRow.lidarWidth
                                // visible tracks opacity so the fade-out
                                // animation plays through before unmount.
                                visible: opacity > 0.01
                                opacity: wideDepthRow.lidarVisible ? 1.0 : 0.0
                                Behavior on width {
                                    NumberAnimation {
                                        duration: Theme.durationNormal
                                        easing.type: Easing.OutCubic
                                    }
                                }
                                Behavior on opacity {
                                    NumberAnimation {
                                        duration: Theme.durationNormal
                                        easing.type: Easing.OutCubic
                                    }
                                }

                                measuredDepth: alice ? alice.depth : 0
                                // focusDepthMeters() is Q_INVOKABLE without
                                // NOTIFY — poll it alongside the column.
                                Timer {
                                    interval: 100
                                    running: wideLidar.opacity > 0
                                    repeat: true
                                    triggeredOnStart: true
                                    onTriggered: {
                                        if (!alice) return
                                        wideLidar.column = alice.depthColumnAtFocus(400)
                                        wideLidar.focusDepth = alice.focusDepthMeters()
                                    }
                                }
                            }
                        }
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                // Calibration (flex — shorter than depth)
                Item {
                    id: wideCalibPanel
                    Layout.fillWidth: true; Layout.fillHeight: true; Layout.preferredHeight: 200
                    Layout.minimumHeight: 80
                    ColumnLayout {
                        id: calibCol
                        anchors.fill: parent; anchors.margins: Theme.dp(20); spacing: Theme.dp(10)
                        SectionHeader { text: "CALIBRATION" }

                        Text {
                            text: alice && alice.hasMapping ? alice.mappingName : "No mapping"
                            font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold
                            color: alice && alice.hasMapping ? Theme.success : Theme.textDisabled
                        }

                        // Mapping visualization
                        Rectangle {
                            Layout.fillWidth: true; Layout.fillHeight: true
                            color: Theme.well; radius: Theme.radiusSm
                            visible: wideCalibPanel.height > Theme.dp(260)
                            MappingPlot { anchors.fill: parent }
                        }

                        // Preset selector
                        Rectangle {
                            Layout.fillWidth: true; height: Theme.dp(40); radius: Theme.radiusSm
                            color: Theme.surface; border.width: 1; border.color: Theme.border

                            Text {
                                anchors.left: parent.left; anchors.leftMargin: Theme.dp(16); anchors.verticalCenter: parent.verticalCenter
                                text: alice && alice.hasMapping ? alice.mappingName : "Select Preset..."
                                font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall
                                color: alice && alice.hasMapping ? Theme.textPrimary : Theme.textSecondary
                            }
                            Text {
                                anchors.right: parent.right; anchors.rightMargin: Theme.dp(16); anchors.verticalCenter: parent.verticalCenter
                                text: "\u25BE"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: widePresetMenu.popup()
                            }
                            FileDialog {
                                id: wideFileDialog
                                title: "Load Calibration Mapping"
                                nameFilters: ["JSON files (*.json)", "All files (*)"]
                                onAccepted: { if (alice) { alice.loadMappingFromFile(selectedFile); widePresetMenu.currentText = "From file" } }
                            }
                            Menu {
                                id: widePresetMenu
                                property string currentText: ""
                                MenuItem { text: "Select Preset..."; onTriggered: { widePresetMenu.currentText = ""; if (alice) alice.clearMapping() } }
                                MenuSeparator {}
                                MenuItem { text: "Linear"; onTriggered: { widePresetMenu.currentText = "Linear"; if (alice) alice.loadPreset(0) } }
                                MenuItem { text: "Logarithmic"; onTriggered: { widePresetMenu.currentText = "Logarithmic"; if (alice) alice.loadPreset(1) } }
                                MenuItem { text: "Portrait"; onTriggered: { widePresetMenu.currentText = "Portrait"; if (alice) alice.loadPreset(2) } }
                                MenuItem { text: "Landscape"; onTriggered: { widePresetMenu.currentText = "Landscape"; if (alice) alice.loadPreset(3) } }
                                MenuItem { text: "Macro"; onTriggered: { widePresetMenu.currentText = "Macro"; if (alice) alice.loadPreset(4) } }
                                MenuSeparator {}
                                MenuItem { text: "From file..."; onTriggered: wideFileDialog.open() }
                            }
                        }
                    }
                }
            }

            Rectangle { visible: wideMode; Layout.fillHeight: true; width: 1; color: Theme.border }

            // CENTER: Camera feed (always present, flex)
            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.minimumWidth: 400

                Rectangle {
                    anchors.fill: parent; color: Theme.well; clip: true

                    // Zoomable camera container — uses width/height * zoom, positioned by pan
                    Item {
                        id: cameraContainer
                        property real zw: parent.width * opsView.zoomLevel
                        property real zh: parent.height * opsView.zoomLevel

                        width: zw; height: zh
                        x: -(zw - parent.width) * opsView.panX
                        y: -(zh - parent.height) * opsView.panY

                        VideoRenderer {
                            id: cameraFeed
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

                        // Placeholder — fades in the opposite direction via an
                        // OpacityAnimator so it also stays smooth while the main
                        // thread is busy with the capture-card signal burst.
                        Label {
                            id: cameraPlaceholder
                            anchors.centerIn: parent; text: "No camera"; font.pixelSize: Theme.fontSizeH2; color: Theme.textPlaceholder
                            property bool hasFeed: alice ? alice.captureCardConnected : false
                            visible: true
                            opacity: hasFeed ? 0.0 : 1.0
                            Behavior on opacity { OpacityAnimator { duration: 250; easing.type: Easing.OutCubic } }
                        }
                    }

                    // AF status chip (top-left) — matches HTML padding:2px 8px at 200%.
                    // Colour / border lerp between idle → focusing so the status
                    // transition doesn't pop. `visible` is gated by opacity so
                    // the chip fades out when the user drops back to MF instead
                    // of snapping off-screen.
                    Rectangle {
                        id: afChip
                        property bool showChip: alice ? alice.focusMode > 0 : false
                        anchors.top: parent.top; anchors.left: parent.left; anchors.margins: Theme.dp(12)
                        width: afLabel.implicitWidth + Theme.dp(32); height: Theme.dp(32); radius: Theme.radiusSm
                        color: alice && alice.activelyFocusing ? Qt.rgba(0.055, 0.231, 0.173, 0.9) : Qt.rgba(0.106, 0.125, 0.145, 0.9)
                        border.width: 1; border.color: alice && alice.activelyFocusing ? Theme.success : Theme.border
                        visible: opacity > 0.01
                        opacity: showChip ? 1.0 : 0.0
                        scale: showChip ? 1.0 : 0.92
                        transformOrigin: Item.TopLeft
                        Behavior on opacity { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                        Behavior on scale { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                        Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                        Behavior on border.color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                        Behavior on width { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                        Text {
                            id: afLabel; anchors.centerIn: parent
                            // Keep the last non-MF label while the chip fades out so
                            // the user sees "AF-S" dissolving, not an empty pill.
                            property string lastLabel: "AF-S"
                            text: {
                                var m = alice ? alice.focusMode : 0
                                if (m > 0) {
                                    lastLabel = ["", "AF-S", "AF-C", "AF-F"][m]
                                    return lastLabel
                                }
                                return lastLabel
                            }
                            font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold
                            color: alice && alice.activelyFocusing ? Theme.success : Theme.textSecondary
                            Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                        }
                    }

                    // Histogram overlay (top-right) — tied strictly to the capture card
                    // so that disconnecting the camera clears (hides) the histogram
                    // instead of silently falling back to the RealSense color feed.
                    Rectangle {
                        anchors.top: parent.top; anchors.right: parent.right; anchors.margins: Theme.dp(8)
                        width: Theme.dp(220); height: Theme.dp(130); radius: Theme.radiusSm
                        color: Qt.rgba(0.106, 0.125, 0.145, 0.92)
                        border.width: 1; border.color: Theme.border
                        visible: alice ? alice.captureCardConnected : false

                        Text {
                            id: histTitle
                            anchors.top: parent.top; anchors.left: parent.left
                            anchors.margins: Theme.dp(6)
                            text: "HISTOGRAM"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
                        }

                        HistogramRenderer {
                            id: histRenderer
                            anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
                            anchors.top: histTitle.bottom
                            anchors.margins: Theme.dp(6); anchors.topMargin: Theme.dp(4)
                            // Fall back to alice.emptyFrame (a default-constructed QImage)
                            // instead of null so the binding always assigns a valid
                            // QImage — null triggered a QML warning even though the
                            // enclosing rectangle is hidden when disconnected.
                            source: alice.captureCardConnected ? alice.captureFrame : alice.emptyFrame
                        }
                    }

                    // Zoom toolbar (bottom-left, z:10 above the drag MouseArea)
                    ZoomToolbar {
                        z: 10
                        anchors.bottom: parent.bottom; anchors.left: parent.left; anchors.margins: 8
                        zoomLevel: opsView.zoomLevel
                        onZoomIn: opsView.zoomLevel = Math.min(Theme.zoomMax, opsView.zoomLevel + Theme.zoomStep)
                        onZoomOut: opsView.zoomLevel = Math.max(Theme.zoomMin, opsView.zoomLevel - Theme.zoomStep)
                        onZoomTo: (level) => { opsView.zoomLevel = Math.max(Theme.zoomMin, Math.min(Theme.zoomMax, level)) }
                        onFitRequested: opsView.zoomLevel = 1.0
                    }

                    // MiniMap (bottom-right, z:10 above drag MouseArea)
                    MiniMap {
                        z: 10
                        anchors.bottom: parent.bottom; anchors.right: parent.right; anchors.margins: 8
                        zoomLevel: opsView.zoomLevel; panX: opsView.panX; panY: opsView.panY
                    }

                    // Zoom scroll + drag-to-pan (no click-to-focus on camera)
                    MouseArea {
                        anchors.fill: parent; z: 1
                        property bool dragging: false
                        property real dragStartX: 0
                        property real dragStartY: 0
                        property real panStartX: 0
                        property real panStartY: 0
                        cursorShape: opsView.zoomLevel > 1.0 ? Qt.OpenHandCursor : Qt.ArrowCursor

                        onPressed: (mouse) => {
                            if (opsView.zoomLevel > 1.0) {
                                dragging = true
                                dragStartX = mouse.x; dragStartY = mouse.y
                                panStartX = opsView.panX; panStartY = opsView.panY
                                cursorShape = Qt.ClosedHandCursor
                            }
                        }
                        onPositionChanged: (mouse) => {
                            if (dragging) {
                                let dx = (mouse.x - dragStartX) / width
                                let dy = (mouse.y - dragStartY) / height
                                opsView.panX = Math.max(0, Math.min(1, panStartX - dx))
                                opsView.panY = Math.max(0, Math.min(1, panStartY - dy))
                            }
                        }
                        onReleased: { dragging = false; cursorShape = opsView.zoomLevel > 1.0 ? Qt.OpenHandCursor : Qt.ArrowCursor }
                        onWheel: (wheel) => {
                            if (wheel.angleDelta.y > 0) opsView.zoomLevel = Math.min(Theme.zoomMax, opsView.zoomLevel + 0.1)
                            else opsView.zoomLevel = Math.max(Theme.zoomMin, opsView.zoomLevel - 0.1)
                            if (opsView.zoomLevel <= 1.0) { opsView.panX = 0.5; opsView.panY = 0.5 }
                        }
                    }
                }
            }

            Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

            // RIGHT sidebar
            ColumnLayout {
                Layout.preferredWidth: compactMode ? Theme.sidebarNarrow : (wideMode ? Theme.telemetryColumnWidth : Theme.sidebarStandard)
                Layout.minimumWidth: compactMode ? Theme.sidebarNarrow : (wideMode ? Theme.telemetryColumnWidth : Theme.sidebarStandard)
                Layout.maximumWidth: compactMode ? Theme.sidebarNarrow : (wideMode ? Theme.telemetryColumnWidth : Theme.sidebarStandard)
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

    // Compact mode right sidebar (1200–1279px): motor + numeric depth +
    // system monitor + scrolling log. The previous layout had an almost-empty
    // focus-mode indicator panel that wasted vertical real estate; we now
    // use that space for CPU/GPU/MEM bars and the log tail, mirroring the
    // wide-mode sidebar at a smaller density.
    Component {
        id: compactRightComponent

        ColumnLayout {
            spacing: 0

            // Motor — dp(40) of vertical padding (dp(20) top + dp(20) bottom)
            // so the slider handle has breathing room at small window heights.
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: cmpMotorCol.implicitHeight + Theme.dp(40)
                Layout.maximumHeight: cmpMotorCol.implicitHeight + Theme.dp(40)
                color: Theme.bg
                ColumnLayout {
                    id: cmpMotorCol; anchors.fill: parent
                    anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(20)
                    spacing: Theme.dp(10)
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth numeric readout (no video feed). Shows current depth, confidence,
            // and the active focus mode/AF state so the user doesn't need a dedicated
            // focus-mode panel further down.
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: cmpDepthCol.implicitHeight + Theme.dp(40)
                Layout.maximumHeight: cmpDepthCol.implicitHeight + Theme.dp(40)
                color: Theme.bg
                ColumnLayout {
                    id: cmpDepthCol; anchors.fill: parent
                    anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(20)
                    spacing: Theme.dp(8)
                    RowLayout {
                        Layout.fillWidth: true
                        SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                        Text {
                            text: alice && alice.activelyFocusing ? ["MF", "AF-S", "AF-C", "AF-F"][alice.focusMode] : ["MF", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
                            font.family: Theme.fontFamilyMono
                            font.pixelSize: Theme.fontSizeMicro
                            font.weight: Font.DemiBold
                            color: alice && alice.activelyFocusing ? Theme.success : Theme.textSecondary
                            Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                        }
                    }
                    Text {
                        text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + " m" : "\u2014"
                        font.family: Theme.fontFamilyMono; font.pixelSize: Theme.dp(36); font.weight: Font.Bold
                        color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                        Layout.alignment: Qt.AlignHCenter
                        Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                    }
                    Text {
                        // Space-pad to 3 digits so this label stops the whole
                        // panel from reflowing when confidence flickers across
                        // the 100/99 boundary.
                        text: String(Math.round((alice ? alice.depthConfidence : 0) * 100)).padStart(3, "\u00A0") + "% confidence"
                        font.family: Theme.fontFamilyMono; font.pixelSize: Theme.dp(18); color: Theme.textSecondary
                        Layout.alignment: Qt.AlignHCenter
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // System monitor — fits in sidebarNarrow width, shows CPU/GPU/MEM bars.
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: Theme.dp(160)
                Layout.maximumHeight: Theme.dp(180)
                color: Theme.bg
                SystemMonitorPanel {
                    anchors.fill: parent
                    anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(16)
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Log tail — fills the remaining vertical space.
            LogDisplay {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.minimumHeight: Theme.dp(120)
                messages: alice ? alice.logMessages : []
            }
        }
    }

    // Standard mode right sidebar content
    Component {
        id: standardRightComponent

        ColumnLayout {
            spacing: 0

            // Motor — content-driven height
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: stdMotorCol.implicitHeight + Theme.dp(40)
                Layout.maximumHeight: Layout.preferredHeight
                color: Theme.bg
                ColumnLayout {
                    id: stdMotorCol; anchors.fill: parent
                    anchors.leftMargin: Theme.dp(24); anchors.rightMargin: Theme.dp(24)
                    anchors.topMargin: Theme.dp(20); anchors.bottomMargin: Theme.dp(16)
                    spacing: Theme.dp(12)
                    SectionHeader { text: "MOTOR POSITION" }
                    MotorSlider {
                        Layout.fillWidth: true; motorPos: alice ? alice.motorPosition : 0
                        enabled: alice ? alice.motorConnected : false
                        onMotorMoved: (pos) => { if (!alice) return; alice.focusMode = 0; alice.setMotorPosition(pos) }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Depth feed — max height capped to avoid wasted space beyond 16:9 fill
            Item {
                Layout.fillWidth: true; Layout.fillHeight: true; Layout.preferredHeight: Theme.dp(240)
                Layout.minimumHeight: Theme.dp(120)
                Layout.maximumHeight: Theme.dp(346)
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: Theme.dp(20); spacing: Theme.dp(10)
                    RowLayout {
                        SectionHeader { text: "DEPTH"; Layout.fillWidth: true }
                        Row {
                            spacing: Theme.dp(6)
                            Text {
                                id: stdDepthText
                                text: alice && alice.depth > 0 ? alice.depth.toFixed(2) + "m" : "—"
                                font.family: Theme.fontFamilyMono; font.pixelSize: Theme.dp(22); font.weight: Font.Bold
                                color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                                Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                            }
                            Text {
                                id: confText1
                                visible: alice ? alice.depth > 0 : false
                                // Fixed 4-char slot — see wideConfText above.
                                horizontalAlignment: Text.AlignRight
                                width: stdConfMetrics.advanceWidth
                                text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"
                                font.family: Theme.fontFamilyMono; font.pixelSize: Theme.dp(18); font.weight: Font.Normal
                                color: Theme.textSecondary
                                anchors.baseline: stdDepthText.baseline
                                TextMetrics {
                                    id: stdConfMetrics
                                    font.family: Theme.fontFamilyMono
                                    font.pixelSize: Theme.dp(18)
                                    text: "100%"
                                }
                            }
                        }
                    }
                    Item {
                        id: stdDepthRow
                        Layout.fillWidth: true; Layout.fillHeight: true

                        readonly property bool lidarVisible:
                            alice && alice.hasMapping && alice.realSenseConnected
                        readonly property int lidarTargetWidth:
                            Math.max(Theme.dp(92),
                                     Math.round(stdDepthRow.width * 0.15))
                        readonly property int lidarWidth: lidarVisible ? lidarTargetWidth : 0
                        readonly property int lidarRightMargin: lidarWidth > 0
                            ? (lidarWidth + Theme.dp(6))
                            : 0

                        Rectangle {
                            id: stdDepthRect
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.rightMargin: stdDepthRow.lidarRightMargin
                            Behavior on anchors.rightMargin {
                                NumberAnimation {
                                    duration: Theme.durationNormal
                                    easing.type: Easing.OutCubic
                                }
                            }
                            color: Theme.well; radius: Theme.radiusSm

                            property real vidAspect: 16.0 / 9.0
                            property real fitW: Math.min(width, height * vidAspect)
                            property real fitH: fitW / vidAspect
                            property real vidX: (width - fitW) / 2
                            property real vidY: (height - fitH) / 2

                            VideoRenderer {
                                id: stdRsFeed
                                x: stdDepthRect.vidX; y: stdDepthRect.vidY
                                width: stdDepthRect.fitW; height: stdDepthRect.fitH
                                source: alice.colorFrame
                                property bool hasFeed: alice ? alice.realSenseConnected : false
                                visible: true
                                opacity: hasFeed ? 1.0 : 0.0
                                scale: hasFeed ? 1.0 : 0.95
                                transformOrigin: Item.Center
                                Behavior on opacity { OpacityAnimator { duration: 350; easing.type: Easing.OutCubic } }
                                Behavior on scale { ScaleAnimator { duration: 350; easing.type: Easing.OutCubic } }
                            }
                            // Face overlay (std layout) — same coordinate space as the RealSense preview above.
                            FaceOverlay {
                                anchors.fill: parent
                                vidX: stdDepthRect.vidX
                                vidY: stdDepthRect.vidY
                                vidW: stdDepthRect.fitW
                                vidH: stdDepthRect.fitH
                                faces: alice ? alice.trackedFacesList : []
                                visible: alice ? (alice.focusMode === 3 && alice.realSenseConnected) : false
                                onFaceClicked: (tid) => { if (alice) alice.selectFace(tid) }
                            }
                            // Crosshair — hidden in AF-F since face tracking owns the measurement point.
                            // Shares animation model with wideReticle above.
                            Item {
                                id: stdReticle
                                property real normX: Math.max(0, Math.min(1, alice ? alice.measureX : 0.5))
                                property real normY: Math.max(0, Math.min(1, alice ? alice.measureY : 0.5))
                                property real cx: stdDepthRect.vidX + normX * stdDepthRect.fitW
                                property real cy: stdDepthRect.vidY + normY * stdDepthRect.fitH
                                x: cx - 12; y: cy - 12
                                width: 24; height: 24
                                visible: alice ? (alice.realSenseConnected && alice.focusMode !== 3) : false

                                Behavior on x { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                                Behavior on y { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                                onNormXChanged: stdLockPulse.restart()
                                onNormYChanged: stdLockPulse.restart()

                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 24; height: 3; color: "#000000"; opacity: 0.6 }
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 3; height: 24; color: "#000000"; opacity: 0.6 }
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 22; height: 1; color: "#ffffff" }
                                Rectangle { anchors.horizontalCenter: parent.horizontalCenter; anchors.verticalCenter: parent.verticalCenter; width: 1; height: 22; color: "#ffffff" }
                                Rectangle {
                                    id: stdInnerRing
                                    anchors.centerIn: parent; width: 8; height: 8; radius: 4
                                    color: "transparent"; border.width: 1.5; border.color: "#ffffff"
                                    transformOrigin: Item.Center
                                }
                                Rectangle {
                                    anchors.centerIn: parent; width: 10; height: 10; radius: 5
                                    color: "transparent"; border.width: 1; border.color: "#000000"; opacity: 0.5
                                }

                                SequentialAnimation {
                                    id: stdLockPulse
                                    NumberAnimation { target: stdInnerRing; property: "scale"; to: 0.75; duration: Theme.durationFast; easing.type: Easing.OutCubic }
                                    NumberAnimation { target: stdInnerRing; property: "scale"; to: 1.0; duration: Theme.durationNormal; easing.type: Easing.OutBack }
                                }
                            }
                            // Drag area — disabled in AF-F so a stray click doesn't fight the tracker.
                            MouseArea {
                                anchors.fill: parent
                                enabled: alice ? alice.focusMode !== 3 : true
                                cursorShape: enabled ? Qt.CrossCursor : Qt.ArrowCursor
                                property bool isDragging: false
                                onPressed: (mouse) => { isDragging = true; updatePos(mouse.x, mouse.y, true) }
                                onPositionChanged: (mouse) => { if (isDragging) updatePos(mouse.x, mouse.y, false) }
                                onReleased: isDragging = false
                                function updatePos(mx, my, isJump) {
                                    if (!alice) return
                                    var nx = Math.max(0, Math.min(1, (mx - stdDepthRect.vidX) / stdDepthRect.fitW))
                                    var ny = Math.max(0, Math.min(1, (my - stdDepthRect.vidY) / stdDepthRect.fitH))
                                    if (isJump) alice.processTap(nx, ny)
                                    else alice.setMeasurementPosition(nx, ny)
                                }
                            }
                        }

                        LidarWaveform {
                            id: stdLidar
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.right: parent.right
                            width: stdDepthRow.lidarWidth
                            visible: opacity > 0.01
                            opacity: stdDepthRow.lidarVisible ? 1.0 : 0.0
                            Behavior on width {
                                NumberAnimation {
                                    duration: Theme.durationNormal
                                    easing.type: Easing.OutCubic
                                }
                            }
                            Behavior on opacity {
                                NumberAnimation {
                                    duration: Theme.durationNormal
                                    easing.type: Easing.OutCubic
                                }
                            }

                            measuredDepth: alice ? alice.depth : 0
                            Timer {
                                interval: 100
                                running: stdLidar.opacity > 0
                                repeat: true
                                triggeredOnStart: true
                                onTriggered: {
                                    if (!alice) return
                                    stdLidar.column = alice.depthColumnAtFocus(400)
                                    stdLidar.focusDepth = alice.focusDepthMeters()
                                }
                            }
                        }
                    }
                }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Calibration (flex weight ~2 — shorter than depth)
            Item {
                id: stdCalibPanel
                Layout.fillWidth: true; Layout.fillHeight: true; Layout.preferredHeight: 200
                Layout.minimumHeight: 80
                ColumnLayout {
                    anchors.fill: parent; anchors.margins: Theme.dp(20); spacing: Theme.dp(10)
                    SectionHeader { text: "CALIBRATION" }

                    Text {
                        text: alice && alice.hasMapping ? alice.mappingName : "No mapping"
                        font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold
                        color: alice && alice.hasMapping ? Theme.success : Theme.textDisabled
                    }

                    // Mapping visualization
                    Rectangle {
                        Layout.fillWidth: true; Layout.fillHeight: true
                        color: Theme.well; radius: Theme.radiusSm
                        visible: stdCalibPanel.height > Theme.dp(260)
                        MappingPlot { anchors.fill: parent }
                    }

                    // Preset selector — height matches HTML padding:4px 8px + font:10px at 200% = 40px
                    Rectangle {
                        Layout.fillWidth: true; height: Theme.dp(40); radius: Theme.radiusSm
                        color: Theme.surface; border.width: 1; border.color: Theme.border

                        Text {
                            anchors.left: parent.left; anchors.leftMargin: Theme.dp(16); anchors.verticalCenter: parent.verticalCenter
                            text: alice && alice.hasMapping ? alice.mappingName : "Select Preset..."
                            font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall
                            color: alice && alice.hasMapping ? Theme.textPrimary : Theme.textSecondary
                        }
                        Text {
                            anchors.right: parent.right; anchors.rightMargin: Theme.dp(16); anchors.verticalCenter: parent.verticalCenter
                            text: "\u25BE"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
                        }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            onClicked: stdPresetMenu.popup()
                        }
                        FileDialog {
                            id: stdFileDialog
                            title: "Load Calibration Mapping"
                            nameFilters: ["JSON files (*.json)", "All files (*)"]
                            onAccepted: { if (alice) { alice.loadMappingFromFile(selectedFile); stdPresetMenu.currentText = "From file" } }
                        }
                        Menu {
                            id: stdPresetMenu
                            property string currentText: ""
                            MenuItem { text: "Select Preset..."; onTriggered: { stdPresetMenu.currentText = ""; if (alice) alice.clearMapping() } }
                            MenuSeparator {}
                            MenuItem { text: "Linear"; onTriggered: { stdPresetMenu.currentText = "Linear"; if (alice) alice.loadPreset(0) } }
                            MenuItem { text: "Logarithmic"; onTriggered: { stdPresetMenu.currentText = "Logarithmic"; if (alice) alice.loadPreset(1) } }
                            MenuItem { text: "Portrait"; onTriggered: { stdPresetMenu.currentText = "Portrait"; if (alice) alice.loadPreset(2) } }
                            MenuItem { text: "Landscape"; onTriggered: { stdPresetMenu.currentText = "Landscape"; if (alice) alice.loadPreset(3) } }
                            MenuItem { text: "Macro"; onTriggered: { stdPresetMenu.currentText = "Macro"; if (alice) alice.loadPreset(4) } }
                            MenuSeparator {}
                            MenuItem { text: "From file..."; onTriggered: stdFileDialog.open() }
                        }
                    }
                }
            }
        }
    }

    // Wide mode right sidebar content
    Component {
        id: wideRightComponent

        ColumnLayout {
            spacing: 0

            // Telemetry (fixed max)
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: Theme.dp(260)
                Layout.minimumHeight: Theme.dp(250); Layout.maximumHeight: Theme.dp(280); color: Theme.bg
                TelemetryGrid { id: telGrid; anchors.fill: parent; anchors.margins: Theme.dp(20) }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // System (fixed max height)
            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: Theme.dp(160)
                Layout.maximumHeight: Theme.dp(220); color: Theme.bg
                SystemMonitorPanel { id: sysPanel; anchors.fill: parent; anchors.margins: Theme.dp(20) }
            }
            Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

            // Log (fills remaining space)
            LogDisplay { Layout.fillWidth: true; Layout.fillHeight: true; Layout.minimumHeight: Theme.dp(60); messages: alice ? alice.logMessages : [] }
        }
    }
}
