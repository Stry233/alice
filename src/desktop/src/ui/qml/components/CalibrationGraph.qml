import QtQuick
import QtQuick.Layouts
import Alice.UI

Item {
    id: graph
    property var points: []
    property int currentMotorPos: 0
    property real currentDepth: 0
    property string fitType: "Cubic Spline"

    onPointsChanged: canvas.requestPaint()
    onCurrentMotorPosChanged: canvas.requestPaint()
    onCurrentDepthChanged: canvas.requestPaint()

    ColumnLayout {
        anchors.fill: parent
        spacing: Theme.dp(12)

        // Title
        Text { text: "CALIBRATION CURVE"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; font.letterSpacing: Theme.sectionLetterSpacing; color: Theme.textSecondary }

        // Legend — separate row, wraps naturally
        Flow {
            Layout.fillWidth: true
            spacing: Theme.dp(16)
            Row { spacing: Theme.dp(6); Rectangle { width: Theme.dp(12); height: Theme.dp(4); color: Theme.primary; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Interpolated"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textSecondary } }
            Row { spacing: Theme.dp(6); Rectangle { width: Theme.dp(10); height: Theme.dp(10); color: Theme.primaryHover; radius: Theme.dp(5); anchors.verticalCenter: parent.verticalCenter } Text { text: "Sampled"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textSecondary } }
            Row { spacing: Theme.dp(6); Rectangle { width: Theme.dp(12); height: Theme.dp(4); color: Theme.warning; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Motor pos"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textSecondary } }
            Row { spacing: Theme.dp(6); Rectangle { width: Theme.dp(12); height: Theme.dp(4); color: Theme.success; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Depth"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textSecondary } }
        }

        // Graph canvas
        Canvas {
            id: canvas
            Layout.fillWidth: true; Layout.fillHeight: true

            property real marginLeft: Theme.dp(50)
            property real marginBottom: Theme.dp(30)
            property real marginTop: Theme.dp(10)
            property real marginRight: Theme.dp(10)
            // Auto-scale Y axis: covers all points + current depth, min 5m
            property real maxDepth: {
                var m = 5.0
                for (var i = 0; i < points.length; i++)
                    if (points[i].depth > m) m = points[i].depth
                // Also include current depth so the line stays visible
                if (currentDepth > m) m = currentDepth
                // Round up to next whole number with 10% headroom
                return Math.ceil(m * 1.1)
            }
            property int maxMotor: 4095

            onWidthChanged: requestPaint()
            onHeightChanged: requestPaint()

            onPaint: {
                var ctx = getContext("2d")
                var w = width; var h = height
                if (w <= 0 || h <= 0) return
                var gx = marginLeft; var gy = marginTop
                var gw = w - marginLeft - marginRight; var gh = h - marginTop - marginBottom
                ctx.clearRect(0, 0, w, h)

                var tickFont = Theme.dp(10) + "px monospace"
                var labelFont = Theme.dp(11) + "px 'Inter', sans-serif"

                // Background
                ctx.fillStyle = Theme.well
                ctx.fillRect(gx, gy, gw, gh)

                // Y axis ticks + grid (depth)
                ctx.font = tickFont
                ctx.strokeStyle = Theme.border; ctx.lineWidth = 0.5
                for (var i = 0; i <= 4; i++) {
                    var yy = gy + (i / 4) * gh
                    ctx.beginPath(); ctx.moveTo(gx, yy); ctx.lineTo(gx + gw, yy); ctx.stroke()
                    // Y label left of graph area
                    ctx.fillStyle = Theme.textSecondary
                    ctx.textAlign = "right"
                    ctx.fillText((maxDepth - (i / 4) * maxDepth).toFixed(1) + "m", gx - Theme.dp(6), yy + Theme.dp(4))
                }

                // X axis ticks + grid (motor)
                for (var ix = 0; ix <= 4; ix++) {
                    var xx = gx + (ix / 4) * gw
                    ctx.beginPath(); ctx.moveTo(xx, gy); ctx.lineTo(xx, gy + gh); ctx.stroke()
                    // X label below graph area
                    ctx.fillStyle = Theme.textSecondary
                    ctx.textAlign = "center"
                    ctx.fillText(Math.round((ix / 4) * maxMotor).toString(), xx, gy + gh + Theme.dp(16))
                }

                // Axis titles
                ctx.font = labelFont
                ctx.fillStyle = Theme.textDisabled
                ctx.textAlign = "center"
                ctx.fillText("Motor Position", gx + gw / 2, h - Theme.dp(2))

                ctx.save()
                ctx.translate(Theme.dp(10), gy + gh / 2)
                ctx.rotate(-Math.PI / 2)
                ctx.fillText("Depth (m)", 0, 0)
                ctx.restore()

                // Current motor position (vertical dashed line — orange)
                if (currentMotorPos > 0) {
                    var curX = gx + (currentMotorPos / maxMotor) * gw
                    ctx.strokeStyle = Theme.warning; ctx.lineWidth = 1
                    ctx.setLineDash([Theme.dp(6), Theme.dp(4)])
                    ctx.beginPath(); ctx.moveTo(curX, gy); ctx.lineTo(curX, gy + gh); ctx.stroke()
                    ctx.setLineDash([])
                }

                // Current depth (horizontal dashed line — green)
                if (currentDepth > 0 && currentDepth <= maxDepth) {
                    var curY = gy + (1 - currentDepth / maxDepth) * gh
                    ctx.strokeStyle = Theme.success; ctx.lineWidth = 1
                    ctx.setLineDash([Theme.dp(6), Theme.dp(4)])
                    ctx.beginPath(); ctx.moveTo(gx, curY); ctx.lineTo(gx + gw, curY); ctx.stroke()
                    ctx.setLineDash([])
                    // Label
                    ctx.font = tickFont
                    ctx.fillStyle = Theme.success
                    ctx.textAlign = "left"
                    ctx.fillText(currentDepth.toFixed(2) + "m", gx + gw + Theme.dp(4), curY + Theme.dp(4))
                }

                if (points.length === 0) return

                // Sort by motor position (left to right)
                var sorted = points.slice().sort(function(a, b) { return a.motorPosition - b.motorPosition })

                // Draw interpolated line
                if (sorted.length >= 2) {
                    ctx.strokeStyle = Theme.primary; ctx.lineWidth = Theme.dp(3); ctx.globalAlpha = 0.8
                    ctx.beginPath()
                    for (var j = 0; j < sorted.length; j++) {
                        var px = gx + (sorted[j].motorPosition / maxMotor) * gw
                        var py = gy + (1 - sorted[j].depth / maxDepth) * gh
                        if (j === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py)
                    }
                    ctx.stroke(); ctx.globalAlpha = 1.0
                }

                // Draw sample points
                for (var k = 0; k < sorted.length; k++) {
                    var pt = sorted[k]
                    var ptx = gx + (pt.motorPosition / maxMotor) * gw
                    var pty = gy + (1 - pt.depth / maxDepth) * gh
                    var r = pt.confidence > 0.7 ? Theme.dp(6) : Theme.dp(4)
                    ctx.fillStyle = pt.confidence > 0.7 ? Theme.primaryHover : Theme.warning
                    ctx.beginPath(); ctx.arc(ptx, pty, r, 0, 2 * Math.PI); ctx.fill()
                    ctx.strokeStyle = Theme.bg; ctx.lineWidth = 1; ctx.stroke()
                }

                ctx.textAlign = "left" // reset
            }

            // Hover detection for dots
            MouseArea {
                anchors.fill: parent
                hoverEnabled: true
                property int hoveredIndex: -1

                onPositionChanged: (mouse) => {
                    if (points.length === 0) { hoveredIndex = -1; dotTooltip.visible = false; return }
                    var gx = canvas.marginLeft; var gy = canvas.marginTop
                    var gw = canvas.width - canvas.marginLeft - canvas.marginRight
                    var gh = canvas.height - canvas.marginTop - canvas.marginBottom

                    var closest = -1; var closestDist = Theme.dp(20)
                    for (var i = 0; i < points.length; i++) {
                        var px = gx + (points[i].motorPosition / canvas.maxMotor) * gw
                        var py = gy + (1 - points[i].depth / canvas.maxDepth) * gh
                        var dist = Math.sqrt(Math.pow(mouse.x - px, 2) + Math.pow(mouse.y - py, 2))
                        if (dist < closestDist) { closest = i; closestDist = dist }
                    }

                    hoveredIndex = closest
                    if (closest >= 0) {
                        var pt = points[closest]
                        dotTooltip.visible = true
                        dotTooltip.x = Math.min(mouse.x + Theme.dp(12), canvas.width - dotTooltip.width - Theme.dp(8))
                        dotTooltip.y = Math.max(Theme.dp(8), mouse.y - dotTooltip.height - Theme.dp(8))
                        ttDepth.text = "Depth: " + pt.depth.toFixed(3) + " m"
                        ttMotor.text = "Motor: " + pt.motorPosition
                        ttConf.text = "Conf: " + Math.round(pt.confidence * 100) + "%"
                    } else {
                        dotTooltip.visible = false
                    }
                }
                onExited: { hoveredIndex = -1; dotTooltip.visible = false }
            }

            // Tooltip overlay
            Rectangle {
                id: dotTooltip; visible: false; z: 10
                width: ttCol.implicitWidth + Theme.dp(20)
                height: ttCol.implicitHeight + Theme.dp(16)
                radius: Theme.radiusSm
                color: Qt.rgba(0.15, 0.17, 0.20, 0.95)
                border.width: 1; border.color: Theme.border

                Column {
                    id: ttCol; anchors.centerIn: parent; spacing: Theme.dp(3)
                    Text { id: ttDepth; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro; color: Theme.textPrimary }
                    Text { id: ttMotor; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro; color: Theme.primary }
                    Text { id: ttConf; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro; color: Theme.success }
                }
            }
        }

        // Controls bar
        RowLayout {
            Layout.fillWidth: true; spacing: Theme.dp(12)

            // Fit badge — use Row inside with padding for auto-sizing
            Row {
                spacing: 0
                Rectangle {
                    height: Theme.dp(32); radius: Theme.radiusSm; color: Theme.surface; border.width: 1; border.color: Theme.border
                    width: fitLabel.implicitWidth + fitValue.implicitWidth + Theme.dp(36)
                    Row {
                        anchors.centerIn: parent; spacing: Theme.dp(6)
                        Text { id: fitLabel; text: "Fit:"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textSecondary }
                        Text { id: fitValue; text: graph.fitType; font.pixelSize: Theme.fontSizeMicro; font.weight: Font.DemiBold; color: Theme.primary }
                    }
                }
            }

            // Range badge
            Row {
                spacing: 0
                visible: points.length >= 2
                Rectangle {
                    height: Theme.dp(32); radius: Theme.radiusSm; color: Theme.surface; border.width: 1; border.color: Theme.border
                    width: rangeLabel.implicitWidth + rangeValue.implicitWidth + Theme.dp(36)
                    Row {
                        anchors.centerIn: parent; spacing: Theme.dp(6)
                        Text { id: rangeLabel; text: "Range:"; font.pixelSize: Theme.fontSizeMicro; color: Theme.textSecondary }
                        Text {
                            id: rangeValue
                            property var sorted: { var s = points.slice().sort(function(a,b){return a.depth-b.depth}); return s }
                            text: sorted.length >= 2 ? sorted[0].depth.toFixed(2) + " \u2013 " + sorted[sorted.length-1].depth.toFixed(2) + " m" : ""
                            font.pixelSize: Theme.fontSizeMicro; color: Theme.textPrimary
                        }
                    }
                }
            }

            Item { Layout.fillWidth: true }
        }
    }
}
