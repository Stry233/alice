import QtQuick
import QtQuick.Layouts
import Alice.UI

Item {
    id: graph
    property var points: []
    property int currentMotorPos: 0
    property string fitType: "Cubic Spline"

    ColumnLayout {
        anchors.fill: parent
        spacing: 8

        // Header with legend
        RowLayout {
            Layout.fillWidth: true
            Text { text: "CALIBRATION CURVE"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; font.letterSpacing: Theme.sectionLetterSpacing; color: Theme.textSecondary; Layout.fillWidth: true }
            Row {
                spacing: 12
                Row { spacing: 3; Rectangle { width: 8; height: 2; color: Theme.primary; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Interpolated"; font.pixelSize: 9; color: Theme.textSecondary } }
                Row { spacing: 3; Rectangle { width: 6; height: 6; color: Theme.primaryHover; radius: 3; anchors.verticalCenter: parent.verticalCenter } Text { text: "Sampled"; font.pixelSize: 9; color: Theme.textSecondary } }
                Row { spacing: 3; Rectangle { width: 8; height: 2; color: Theme.warning; radius: 1; anchors.verticalCenter: parent.verticalCenter } Text { text: "Current"; font.pixelSize: 9; color: Theme.textSecondary } }
            }
        }

        // Graph canvas
        Canvas {
            id: canvas
            Layout.fillWidth: true; Layout.fillHeight: true

            property real marginLeft: 40
            property real marginBottom: 24
            property real marginTop: 8
            property real marginRight: 8
            property real maxDepth: 5.0
            property int maxMotor: 4095

            onPaint: {
                var ctx = getContext("2d")
                var w = width; var h = height
                var gx = marginLeft; var gy = marginTop
                var gw = w - marginLeft - marginRight; var gh = h - marginTop - marginBottom
                ctx.clearRect(0, 0, w, h)
                ctx.font = "8px monospace"

                // Background
                ctx.fillStyle = Theme.well
                ctx.fillRect(gx, gy, gw, gh)

                // Grid
                ctx.strokeStyle = Theme.border; ctx.lineWidth = 0.5
                for (var i = 0; i <= 4; i++) {
                    var yy = gy + (i / 4) * gh
                    ctx.beginPath(); ctx.moveTo(gx, yy); ctx.lineTo(gx + gw, yy); ctx.stroke()
                    ctx.fillStyle = Theme.textDisabled
                    ctx.fillText((maxDepth - (i / 4) * maxDepth).toFixed(1), 2, yy + 3)

                    var xx = gx + (i / 4) * gw
                    ctx.beginPath(); ctx.moveTo(xx, gy); ctx.lineTo(xx, gy + gh); ctx.stroke()
                    ctx.fillStyle = Theme.textDisabled
                    ctx.fillText(Math.round((i / 4) * maxMotor).toString(), xx - 10, h - 4)
                }

                // Axis labels
                ctx.fillStyle = Theme.textDisabled
                ctx.fillText("Motor Position", gx + gw / 2 - 30, h - 1)

                if (points.length === 0) return

                // Current motor position line
                var curX = gx + (currentMotorPos / maxMotor) * gw
                ctx.strokeStyle = Theme.warning; ctx.lineWidth = 1; ctx.setLineDash([4, 3])
                ctx.beginPath(); ctx.moveTo(curX, gy); ctx.lineTo(curX, gy + gh); ctx.stroke()
                ctx.setLineDash([])

                // Sort points by depth
                var sorted = points.slice().sort(function(a, b) { return a.depth - b.depth })

                // Draw interpolated line
                if (sorted.length >= 2) {
                    ctx.strokeStyle = Theme.primary; ctx.lineWidth = 1.5; ctx.globalAlpha = 0.8
                    ctx.beginPath()
                    for (var j = 0; j < sorted.length; j++) {
                        var px = gx + (sorted[j].motorPosition / maxMotor) * gw
                        var py = gy + (1 - sorted[j].depth / maxDepth) * gh
                        if (j === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py)
                    }
                    ctx.stroke(); ctx.globalAlpha = 1.0
                }

                // Draw points
                for (var k = 0; k < sorted.length; k++) {
                    var pt = sorted[k]
                    var ptx = gx + (pt.motorPosition / maxMotor) * gw
                    var pty = gy + (1 - pt.depth / maxDepth) * gh
                    var r = pt.confidence > 0.7 ? 4 : 3
                    ctx.fillStyle = pt.confidence > 0.7 ? Theme.primaryHover : Theme.warning
                    ctx.beginPath(); ctx.arc(ptx, pty, r, 0, 2 * Math.PI); ctx.fill()
                    ctx.strokeStyle = Theme.bg; ctx.lineWidth = 1; ctx.stroke()
                }
            }
        }

        // Controls bar
        RowLayout {
            Layout.fillWidth: true; spacing: 8

            Rectangle {
                height: 22; width: fitRow.implicitWidth + 16; radius: Theme.radiusSm; color: Theme.surface; border.width: 1; border.color: Theme.border
                RowLayout { id: fitRow; anchors.centerIn: parent; spacing: 4
                    Text { text: "Fit:"; font.pixelSize: 10; color: Theme.textSecondary }
                    Text { text: graph.fitType; font.pixelSize: 10; font.weight: Font.DemiBold; color: Theme.primary }
                }
            }

            Rectangle {
                visible: points.length >= 2
                height: 22; width: rangeRow.implicitWidth + 16; radius: Theme.radiusSm; color: Theme.surface; border.width: 1; border.color: Theme.border
                RowLayout { id: rangeRow; anchors.centerIn: parent; spacing: 4
                    Text { text: "Range:"; font.pixelSize: 10; color: Theme.textSecondary }
                    Text {
                        property var sorted: { var s = points.slice().sort(function(a,b){return a.depth-b.depth}); return s }
                        text: sorted.length >= 2 ? sorted[0].depth.toFixed(2) + " \u2013 " + sorted[sorted.length-1].depth.toFixed(2) + " m" : ""
                        font.pixelSize: 10; color: Theme.textPrimary
                    }
                }
            }

            Item { Layout.fillWidth: true }
        }
    }

    onPointsChanged: canvas.requestPaint()
    onCurrentMotorPosChanged: canvas.requestPaint()
}
