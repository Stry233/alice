import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    height: 22
    color: Theme.surface

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 12
        anchors.rightMargin: 12
        spacing: 14

        Text { text: "Depth: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + "m" : "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text { text: "Conf: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"; color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text { text: "Motor: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: (alice ? alice.motorPosition : 0) + "/4095"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text { text: "Target: "; color: Theme.textSecondary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Text {
            text: alice && alice.activelyFocusing ? ["MF", "AF-S LOCKED", "AF-C LOCKED", "AF-F LOCKED"][alice.focusMode] : ["MF", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0]
            color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary
            font.family: Theme.fontFamilyMono; font.pixelSize: 9; font.weight: Font.DemiBold
        }

        Rectangle { width: 1; Layout.fillHeight: true; Layout.topMargin: 4; Layout.bottomMargin: 4; color: Theme.border }

        Text { text: "CPU " + Math.round(sysMonitor ? sysMonitor.cpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: "GPU " + Math.round(sysMonitor ? sysMonitor.gpuUsage : 0) + "%"; color: (sysMonitor && sysMonitor.gpuUsage > 80) ? Theme.warning : Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }
        Text { text: "MEM " + (sysMonitor ? sysMonitor.memoryFormatted : "0M"); color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 9 }

        Item { Layout.fillWidth: true }

        Text { text: alice ? alice.logMessages[alice.logMessages.length - 1] || "" : ""; color: Theme.textDisabled; font.family: Theme.fontFamilyMono; font.pixelSize: 9; elide: Text.ElideRight; Layout.maximumWidth: 300 }
    }
}
