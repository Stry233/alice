import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    // implicitHeight (not just height) so ColumnLayout picks up the
    // dp()-based size when Layout.preferredHeight is left unset. Without
    // this the enclosing layout sees implicitHeight=0, renders the strip
    // at 0 px, and the Rectangle's own `height` is just ignored.
    implicitHeight: Theme.dp(44)
    height: implicitHeight
    color: Theme.surface

    // Strip font: HTML 9px at 200% = 18px. Ratio to strip height (44): 40.9%
    property int stripFont: Theme.dp(18)

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: Theme.dp(24)
        anchors.rightMargin: Theme.dp(24)
        spacing: Theme.dp(28)

        // Depth — label in secondary, value in primary
        Row {
            spacing: 0
            Text { text: "Depth: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
            Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + "m" : "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
        }

        Row {
            spacing: 0
            Text { text: "Conf: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
            Text {
                // Pad to 3 digits (space-padded) so "99%" and "100%" occupy
                // the same pixel width in monospace. Prevents the strip from
                // jiggling each time confidence crosses the 100/99 boundary.
                text: String(Math.round((alice ? alice.depthConfidence : 0) * 100)).padStart(3, "\u00A0") + "%"
                color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                font.family: Theme.fontFamilyMono; font.pixelSize: stripFont
                Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
            }
        }

        Row {
            spacing: 0
            Text { text: "Motor: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
            Text { text: (alice ? alice.motorPosition : 0) + "/4095"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
        }

        Row {
            spacing: 0
            Text { text: "Target: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
            Text {
                text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"
                color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont
            }
        }

        Text {
            text: alice && alice.activelyFocusing ? ["MF", "AF-S", "AF-C", "AF-F"][alice.focusMode] : ["MF", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
            color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary
            font.family: Theme.fontFamilyMono; font.pixelSize: stripFont; font.weight: Font.Bold
            Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
        }

        Rectangle { width: 1; Layout.fillHeight: true; Layout.topMargin: Theme.dp(8); Layout.bottomMargin: Theme.dp(8); color: Theme.border }

        Text { text: "CPU " + Math.round(sysMonitor ? sysMonitor.cpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }
        Text {
            text: "GPU " + Math.round(sysMonitor ? sysMonitor.gpuUsage : 0) + "%"
            color: (sysMonitor && sysMonitor.gpuUsage > 80) ? Theme.warning : Theme.textPrimary
            font.family: Theme.fontFamilyMono; font.pixelSize: stripFont
            Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
        }
        Text { text: "MEM " + (sysMonitor ? sysMonitor.memoryFormatted : "0M"); color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont }

        Item { Layout.fillWidth: true }

        Text {
            text: alice && alice.logMessages.length > 0 ? alice.logMessages[alice.logMessages.length - 1] : ""
            color: Theme.textDisabled; font.family: Theme.fontFamilyMono; font.pixelSize: stripFont
            elide: Text.ElideRight; Layout.maximumWidth: Theme.dp(400)
        }
    }
}
