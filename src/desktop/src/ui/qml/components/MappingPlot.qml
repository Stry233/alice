import QtQuick
import Alice.UI

// Mini mapping visualization with ticks, labels, and hover tooltip
Item {
    id: plot

    // N/A text when no mapping
    Text {
        anchors.centerIn: parent; text: "N/A"
        font.pixelSize: Theme.fontSizeMicro; color: Theme.textDisabled
        visible: !alice || !alice.hasMapping
    }

    Canvas {
        id: plotCanvas
        property real side: Math.min(parent.width, parent.height) - Theme.dp(16)
        width: side; height: side
        anchors.centerIn: parent
        visible: alice ? alice.hasMapping : false

        property string mappingName: alice ? alice.mappingName : ""
        onMappingNameChanged: requestPaint()
        onWidthChanged: if (width > 0) requestPaint()
        onHeightChanged: if (height > 0) requestPaint()

        onPaint: {
            var ctx = getContext("2d")
            var s = Math.min(width, height)
            if (s <= 0) return
            ctx.clearRect(0, 0, width, height)

            var marginL = Theme.dp(28)  // left: room for Y tick labels
            var marginR = Theme.dp(8)
            var marginT = Theme.dp(12) // top: room for top dots
            var marginB = Theme.dp(18) // bottom: room for X tick labels
            var gs = Math.min(s - marginL - marginR, s - marginT - marginB)
            var gx = marginL
            var gy = marginT

            // Grid
            var tickFont = Theme.dp(8) + "px monospace"
            ctx.font = tickFont
            ctx.strokeStyle = Theme.border; ctx.lineWidth = 0.5
            for (var i = 0; i <= 4; i++) {
                var ly = gy + (i / 4) * gs
                ctx.beginPath(); ctx.moveTo(gx, ly); ctx.lineTo(gx + gs, ly); ctx.stroke()

                var lx = gx + (i / 4) * gs
                ctx.beginPath(); ctx.moveTo(lx, gy); ctx.lineTo(lx, gy + gs); ctx.stroke()
            }

            // Get actual mapping points
            var points = alice ? alice.mappingPoints() : []
            if (points.length < 2) {
                // Y axis ticks only
                ctx.fillStyle = Theme.textDisabled; ctx.textAlign = "right"
                for (var t = 0; t <= 4; t++)
                    ctx.fillText((5.0 - t * 1.25).toFixed(1), gx - Theme.dp(4), gy + (t / 4) * gs + Theme.dp(3))
                ctx.textAlign = "center"
                for (var tx = 0; tx <= 4; tx++)
                    ctx.fillText(Math.round(tx / 4 * 4095).toString(), gx + (tx / 4) * gs, gy + gs + Theme.dp(12))
                return
            }

            // Find ranges
            var minD = points[0].depth, maxD = points[0].depth
            var minM = points[0].motorPosition, maxM = points[0].motorPosition
            for (var j = 1; j < points.length; j++) {
                if (points[j].depth < minD) minD = points[j].depth
                if (points[j].depth > maxD) maxD = points[j].depth
                if (points[j].motorPosition < minM) minM = points[j].motorPosition
                if (points[j].motorPosition > maxM) maxM = points[j].motorPosition
            }
            var dRange = maxD - minD || 1
            var mRange = maxM - minM || 1

            // Y axis ticks
            ctx.fillStyle = Theme.textDisabled; ctx.textAlign = "right"
            for (var yi = 0; yi <= 4; yi++) {
                var dv = maxD - (yi / 4) * dRange
                ctx.fillText(dv.toFixed(1), gx - Theme.dp(4), gy + (yi / 4) * gs + Theme.dp(3))
            }
            // X axis ticks
            ctx.textAlign = "center"
            for (var xi = 0; xi <= 4; xi++) {
                var mv = Math.round(minM + (xi / 4) * mRange)
                ctx.fillText(mv.toString(), gx + (xi / 4) * gs, gy + gs + Theme.dp(12))
            }

            // Sort by motor position
            points.sort(function(a, b) { return a.motorPosition - b.motorPosition })

            // Draw curve
            ctx.strokeStyle = Theme.primary; ctx.lineWidth = 2
            ctx.beginPath()
            for (var k = 0; k < points.length; k++) {
                var px = gx + ((points[k].motorPosition - minM) / mRange) * gs
                var py = gy + (1 - (points[k].depth - minD) / dRange) * gs
                if (k === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py)
            }
            ctx.stroke()

            // Draw points
            ctx.fillStyle = Theme.primaryHover
            for (var m = 0; m < points.length; m++) {
                var ppx = gx + ((points[m].motorPosition - minM) / mRange) * gs
                var ppy = gy + (1 - (points[m].depth - minD) / dRange) * gs
                ctx.beginPath(); ctx.arc(ppx, ppy, Theme.dp(4), 0, 2 * Math.PI); ctx.fill()
            }

            ctx.textAlign = "left" // reset
        }
    }

    // Hover detection
    MouseArea {
        anchors.fill: plotCanvas
        hoverEnabled: true
        visible: alice ? alice.hasMapping : false

        onPositionChanged: (mouse) => {
            var points = alice ? alice.mappingPoints() : []
            if (points.length === 0) { plotTip.visible = false; return }

            var s = plotCanvas.side
            var marginL = Theme.dp(28)
            var marginR = Theme.dp(8)
            var marginT = Theme.dp(12)
            var marginB = Theme.dp(18)
            var gs = Math.min(s - marginL - marginR, s - marginT - marginB)
            var gx = marginL
            var gy = marginT

            var minD = points[0].depth, maxD = points[0].depth
            var minM = points[0].motorPosition, maxM = points[0].motorPosition
            for (var j = 1; j < points.length; j++) {
                if (points[j].depth < minD) minD = points[j].depth
                if (points[j].depth > maxD) maxD = points[j].depth
                if (points[j].motorPosition < minM) minM = points[j].motorPosition
                if (points[j].motorPosition > maxM) maxM = points[j].motorPosition
            }
            var dRange = maxD - minD || 1; var mRange = maxM - minM || 1

            var closest = -1; var closestDist = Theme.dp(16)
            for (var i = 0; i < points.length; i++) {
                var px = gx + ((points[i].motorPosition - minM) / mRange) * gs
                var py = gy + (1 - (points[i].depth - minD) / dRange) * gs
                var dist = Math.sqrt(Math.pow(mouse.x - px, 2) + Math.pow(mouse.y - py, 2))
                if (dist < closestDist) { closest = i; closestDist = dist }
            }

            if (closest >= 0) {
                var pt = points[closest]
                plotTip.visible = true
                plotTip.x = Math.min(mouse.x + Theme.dp(10), plotCanvas.width - plotTip.width - Theme.dp(4))
                plotTip.y = Math.max(Theme.dp(4), mouse.y - plotTip.height - Theme.dp(4))
                tipDepth.text = pt.depth.toFixed(2) + " m"
                tipMotor.text = "Motor: " + pt.motorPosition
            } else {
                plotTip.visible = false
            }
        }
        onExited: plotTip.visible = false
    }

    // Tooltip
    Rectangle {
        id: plotTip; visible: false; z: 10
        width: tipCol.implicitWidth + Theme.dp(16)
        height: tipCol.implicitHeight + Theme.dp(12)
        radius: Theme.radiusSm
        color: Qt.rgba(0.15, 0.17, 0.20, 0.95)
        border.width: 1; border.color: Theme.border

        Column {
            id: tipCol; anchors.centerIn: parent; spacing: Theme.dp(2)
            Text { id: tipDepth; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro; color: Theme.success }
            Text { id: tipMotor; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro; color: Theme.primary }
        }
    }
}
