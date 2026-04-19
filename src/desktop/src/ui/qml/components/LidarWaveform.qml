import QtQuick
import Alice.UI

// Vertical LiDAR waveform — real-time oscilloscope for the depth
// column under the focus crosshair.
//
// Layout (near at bottom, far at top — world-space intuition):
//
//     far ┌───────────────┐  ← focus + window/2
//         │     · · ·     │
//         │ · · · · · ·   │  ← amber dashed: measured (crosshair)
//         │  ─ · · · · ·  │  ← bright green, centred: focus depth
//         │    · · · ·    │
//    near └───────────────┘  ← focus - window/2
//
// Overlap of the two lines = correct focus. Offset = direction and
// magnitude of the focus error.
Item {
    id: root

    property real measuredDepth: 0
    property real focusDepth: -1
    property var  column: []

    // ±1 m window — fine enough to read ~5 cm focus errors on a
    // typical 300-px panel, wide enough to cover common offsets.
    readonly property real windowMeters: 2.0
    readonly property real tickMinor: 0.1
    readonly property real tickMajor: 0.5

    // Ticks are at absolute depths, so the ruler scrolls past the
    // viewport as focus moves — matches a physical focus ruler.
    function depthToY(d, h) {
        if (focusDepth <= 0) return -1
        var offset = d - focusDepth
        return (h * 0.5) - (offset / windowMeters) * h
    }

    Rectangle {
        anchors.fill: parent
        color: Theme.well
        radius: Theme.radiusSm
        border.width: 1
        border.color: Theme.border
    }

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

        property var colBind: root.column
        property real measuredBind: root.measuredDepth
        property real focusBind: root.focusDepth
        onColBindChanged: requestPaint()
        onMeasuredBindChanged: requestPaint()
        onFocusBindChanged: requestPaint()
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
        Component.onCompleted: requestPaint()

        onPaint: {
            var ctx = getContext("2d")
            if (!ctx) return
            ctx.reset()

            var w = width, h = height
            if (root.focusDepth <= 0) return

            var cy = h * 0.5

            // Grid — ticks at absolute depths so they scroll with focus.
            var halfWin = root.windowMeters * 0.5
            var loD = Math.max(0.01, root.focusDepth - halfWin)
            var hiD = root.focusDepth + halfWin
            var majorStart = Math.ceil(loD / root.tickMajor) * root.tickMajor
            var minorStart = Math.ceil(loD / root.tickMinor) * root.tickMinor
            ctx.lineWidth = 1
            ctx.font = Theme.dp(9) + "px " + Theme.fontFamilyMono
            ctx.textBaseline = "middle"

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

            // Top-view point cloud. Each sample is at a different
            // lateral position in the scene — dots at panel-X =
            // frame-X, so a person left-of-frame and a wall behind
            // them render as two clusters at different heights.
            // ±1 px x-jitter breaks up unnaturally regimented lines
            // when the scene has uniform horizontal bands.
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
                    var jx = Math.round(Math.sin(k * 2.399963))
                    ctx.fillRect(xp + jx, yp, 1, 1)
                }
            }

            // Amber (measured) — drawn before green so the focus line
            // paints on top when they coincide.
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
                } else {
                    // Off-window caret at the nearer edge.
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

            ctx.strokeStyle = Theme.success
            ctx.lineWidth = Theme.dp(2)
            ctx.beginPath()
            ctx.moveTo(0, cy + 0.5)
            ctx.lineTo(w, cy + 0.5)
            ctx.stroke()
        }

        // 30 Hz safety repaint — focusDepth can be reassigned without
        // tripping the onChanged hook on some update cadences.
        Timer {
            interval: 33
            running: root.visible
            repeat: true
            onTriggered: plot.requestPaint()
        }
    }

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
