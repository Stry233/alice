import QtQuick
import Alice.UI

// Vertical "LiDAR waveform" — a real-time oscilloscope-style overlay
// for the depth column directly under the focus crosshair.
//
// Viewport: LINEAR scale, scrolling. The green focus-depth line is
// pinned to the vertical centre of the panel; the whole point cloud
// scrolls past it as the motor moves. The visible window is a narrow
// ±windowMeters/2 range around the current focus depth — much finer
// detail than a fixed 0.3–6 m ruler, which is what the operator
// needs when nudging focus by a few centimetres.
//
// Layout (near at bottom, far at top — world-space intuition):
//
//     far ┌───────────────┐  ← focus + window/2
//         │     · · ·     │
//         │ · · · · · ·   │  ← amber dashed: measured depth (crosshair)
//         │               │
//         │  ─ · · · · ·  │  ← bright green, centred: focus depth
//         │    · · · ·    │
//         │                │
//    near └───────────────┘  ← focus - window/2
//
// When the two lines overlap the lens is correctly focused on the
// subject under the crosshair. When focus drifts, the waveform
// scrolls and the amber line shifts toward the edge — a glance
// reveals the direction and magnitude of the focus error.
Item {
    id: root

    property real measuredDepth: 0     // meters; 0 = no valid reading
    property real focusDepth: -1       // meters; -1 = no mapping, hide everything
    property var  column: []           // array of depth values (meters)

    // Visible window size in metres — total span, centred on focusDepth.
    // 2 m (±1 m) gives pro-grade precision without clipping common
    // focus offsets; the operator reads errors with ~5 cm resolution
    // on a typical 300-px-tall panel.
    readonly property real windowMeters: 2.0

    // Tick spacing (metres). Minor ticks every `tickMinor`, major
    // ticks every `tickMajor` — major ticks carry the numeric label.
    // Ticks are drawn at ABSOLUTE depths (e.g. 1.00 m, 1.25 m, 1.50 m),
    // so they scroll past the viewport as focus changes — matches the
    // physical rotation of a focus ruler.
    readonly property real tickMinor: 0.1
    readonly property real tickMajor: 0.5

    // y(depth): linear around the centred focus depth. Near (smaller
    // depth) maps to the bottom of the panel so the world-space axis
    // matches: things closer to the camera are "lower".
    function depthToY(d, h) {
        if (focusDepth <= 0) return -1  // off-screen sentinel
        var offset = d - focusDepth     // negative = closer, positive = farther
        var y = (h * 0.5) - (offset / windowMeters) * h
        return y
    }

    // Well background matches the adjacent depth preview.
    Rectangle {
        anchors.fill: parent
        color: Theme.well
        radius: Theme.radiusSm
        border.width: 1
        border.color: Theme.border
    }

    // Plot area — fills everything above the footer, no header chrome.
    Canvas {
        id: plot
        anchors.top: parent.top
        anchors.topMargin: Theme.dp(6)
        anchors.bottom: footer.top
        anchors.bottomMargin: Theme.dp(4)
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.leftMargin: Theme.dp(6)
        anchors.rightMargin: Theme.dp(6)
        clip: true

        property var    colBind     : root.column
        property real   measuredBind: root.measuredDepth
        property real   focusBind   : root.focusDepth
        onColBindChanged     : requestPaint()
        onMeasuredBindChanged: requestPaint()
        onFocusBindChanged   : requestPaint()
        onWidthChanged       : requestPaint()
        onHeightChanged      : requestPaint()
        Component.onCompleted: requestPaint()

        onPaint: {
            var ctx = getContext("2d")
            if (!ctx) return
            ctx.reset()

            var w = width, h = height

            // Nothing to draw until the motor-derived focus is valid.
            // The operator should see an empty well rather than noise
            // at an arbitrary "nominal" depth.
            if (root.focusDepth <= 0) return

            var cy = h * 0.5

            // ── Grid: tick lines at ABSOLUTE depths (scrolls) ───────
            // Ticks are positioned at fixed physical distances
            // (1.00 m, 1.10 m, …) and their Y positions come from the
            // same depthToY() the point cloud uses. That means they
            // SCROLL with focus — as the motor ramps in, the whole
            // ruler slides past the viewport, which gives the operator
            // an immediate sense of how much the focus is moving.
            var halfWin = root.windowMeters * 0.5
            var loD = Math.max(0.01, root.focusDepth - halfWin)
            var hiD = root.focusDepth + halfWin
            var majorStart = Math.ceil(loD / root.tickMajor) * root.tickMajor
            var minorStart = Math.ceil(loD / root.tickMinor) * root.tickMinor
            ctx.lineWidth = 1
            ctx.font = Theme.dp(9) + "px " + Theme.fontFamilyMono
            ctx.textBaseline = "middle"

            // Minor ticks first (drawn behind majors).
            ctx.strokeStyle = Qt.rgba(Theme.border.r, Theme.border.g,
                                      Theme.border.b, 0.4)
            ctx.setLineDash([1, 4])
            for (var d = minorStart; d <= hiD + 1e-4; d += root.tickMinor) {
                if (Math.abs((d / root.tickMajor) - Math.round(d / root.tickMajor)) < 1e-3) continue
                var ym = root.depthToY(d, h)
                if (ym < 0 || ym > h) continue
                ctx.beginPath()
                ctx.moveTo(0, ym + 0.5)
                ctx.lineTo(w, ym + 0.5)
                ctx.stroke()
            }

            // Major ticks + labels.
            ctx.strokeStyle = Theme.border
            ctx.fillStyle = Theme.textDisabled
            ctx.setLineDash([2, 3])
            for (var d2 = majorStart; d2 <= hiD + 1e-4; d2 += root.tickMajor) {
                var y2 = root.depthToY(d2, h)
                if (y2 < 0 || y2 > h) continue
                ctx.beginPath()
                ctx.moveTo(0, y2 + 0.5)
                ctx.lineTo(w, y2 + 0.5)
                ctx.stroke()
                var labelY = y2 - Theme.dp(6)
                if (labelY < 0) labelY = y2 + Theme.dp(8)
                ctx.fillText(d2.toFixed(2) + "m", 2, labelY)
            }
            ctx.setLineDash([])

            // ── Point cloud: true "top-view" ────────────────────────
            // Each sample comes from a DIFFERENT lateral position in
            // the scene (horizontal slice across the frame width).
            // The dot's panel-X is its POSITION in that slice — left
            // edge of the plot = left edge of the frame, right = right.
            // So a scene with a person on the left at 1.5 m and a wall
            // on the right at 3 m shows up as two clusters of dots at
            // different heights — a genuine "view from above" of the
            // environment at the crosshair's height.
            //
            // A tiny ±1 px x-jitter breaks up perfect vertical lines
            // when the scene has uniform horizontal bands (e.g. a
            // perpendicular wall) so the trace doesn't read as
            // unnaturally regimented.
            var col = root.column
            if (col && col.length > 0) {
                ctx.fillStyle = Theme.textSecondary
                var n = col.length
                var denom = n - 1 > 0 ? n - 1 : 1
                for (var k = 0; k < n; ++k) {
                    var depth = col[k]
                    if (!depth || depth <= 0) continue
                    var yp = Math.round(root.depthToY(depth, h))
                    if (yp < 0 || yp > h) continue
                    var xp = Math.round((k / denom) * (w - 1))
                    var jx = Math.round(Math.sin(k * 2.399963))  // -1, 0, or 1
                    ctx.fillRect(xp + jx, yp, 1, 1)
                }
            }

            // ── Measured depth (amber, dashed) ──────────────────────
            // Drawn before the focus line so green paints on top when
            // the two coincide. Clipped to panel edges via a caret
            // indicator when the offset exceeds the viewing window.
            if (root.measuredDepth > 0) {
                var my = root.depthToY(root.measuredDepth, h)
                if (my >= 0 && my <= h) {
                    ctx.strokeStyle = Theme.warning
                    ctx.lineWidth = Theme.dp(1.5)
                    ctx.setLineDash([4, 3])
                    ctx.beginPath()
                    ctx.moveTo(0, my + 0.5)
                    ctx.lineTo(w, my + 0.5)
                    ctx.stroke()
                    ctx.setLineDash([])
                    ctx.fillStyle = Theme.warning
                    ctx.beginPath()
                    ctx.moveTo(0, my)
                    ctx.lineTo(Theme.dp(5), my - Theme.dp(3))
                    ctx.lineTo(Theme.dp(5), my + Theme.dp(3))
                    ctx.closePath()
                    ctx.fill()
                } else {
                    // Off-window: draw a caret at the nearer edge
                    // pointing out of the visible range so the
                    // operator knows a reading exists but is past the
                    // window's ±1 m coverage.
                    ctx.fillStyle = Theme.warning
                    var edge = my < 0 ? 0 : h
                    var dir  = my < 0 ? +1 : -1
                    ctx.beginPath()
                    ctx.moveTo(Theme.dp(2), edge)
                    ctx.lineTo(Theme.dp(8), edge + dir * Theme.dp(4))
                    ctx.lineTo(Theme.dp(8), edge - dir * Theme.dp(4))
                    ctx.closePath()
                    ctx.fill()
                }
            }

            // ── Focus depth (bright green, fixed at centre) ─────────
            ctx.strokeStyle = Theme.success
            ctx.lineWidth = Theme.dp(2)
            ctx.beginPath()
            ctx.moveTo(0, cy + 0.5)
            ctx.lineTo(w, cy + 0.5)
            ctx.stroke()
            ctx.fillStyle = Theme.success
            ctx.beginPath()
            ctx.moveTo(w, cy)
            ctx.lineTo(w - Theme.dp(5), cy - Theme.dp(3))
            ctx.lineTo(w - Theme.dp(5), cy + Theme.dp(3))
            ctx.closePath()
            ctx.fill()
        }

        // 30 Hz safety repaint — column/focus bindings push paints on
        // change, but the tick labels depend on focusDepth which may
        // be updated as a property at any time; this ensures the view
        // stays in sync even across irregular update cadences.
        Timer {
            interval: 33
            running: root.visible
            repeat: true
            onTriggered: plot.requestPaint()
        }
    }

    // Footer: focus (green, primary) then measured (amber) — ordering
    // matches the visual hierarchy above.
    Column {
        id: footer
        anchors.bottom: parent.bottom
        anchors.bottomMargin: Theme.dp(6)
        anchors.left: parent.left
        anchors.right: parent.right
        spacing: Theme.dp(2)

        Row {
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: Theme.dp(4)
            visible: root.focusDepth > 0
            Rectangle {
                width: Theme.dp(6); height: Theme.dp(6); radius: width / 2
                color: Theme.success
                anchors.verticalCenter: parent.verticalCenter
            }
            Text {
                text: root.focusDepth > 0 ? root.focusDepth.toFixed(2) + "m" : "—"
                color: Theme.textPrimary
                font.family: Theme.fontFamilyMono
                font.pixelSize: Theme.fontSizeMicro
            }
        }
        Row {
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: Theme.dp(4)
            Rectangle {
                width: Theme.dp(6); height: Theme.dp(6); radius: width / 2
                color: Theme.warning
                anchors.verticalCenter: parent.verticalCenter
            }
            Text {
                text: root.measuredDepth > 0 ? root.measuredDepth.toFixed(2) + "m" : "—"
                color: Theme.textSecondary
                font.family: Theme.fontFamilyMono
                font.pixelSize: Theme.fontSizeMicro
            }
        }
    }
}
