import QtQuick
import QtQuick.Layouts
import Alice.UI

ColumnLayout {
    spacing: 4

    Label {
        text: "SYSTEM"
        font.pixelSize: Theme.sectionFontSize
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }

    RowLayout {
        Layout.fillWidth: true; spacing: 8
        Text { text: "CPU"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10; Layout.preferredWidth: 32 }
        Rectangle {
            Layout.fillWidth: true; height: 4; radius: 2; color: Theme.surface
            Rectangle { width: parent.width * Math.min(1, (sysMonitor ? sysMonitor.cpuUsage : 0) / 100); height: parent.height; radius: 2; color: (sysMonitor && sysMonitor.cpuUsage > 80) ? Theme.danger : Theme.success }
        }
        Text { text: Math.round(sysMonitor ? sysMonitor.cpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10; Layout.preferredWidth: 32; horizontalAlignment: Text.AlignRight }
    }

    RowLayout {
        Layout.fillWidth: true; spacing: 8
        Text { text: "GPU"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10; Layout.preferredWidth: 32 }
        Rectangle {
            Layout.fillWidth: true; height: 4; radius: 2; color: Theme.surface
            Rectangle { width: parent.width * Math.min(1, (sysMonitor ? sysMonitor.gpuUsage : 0) / 100); height: parent.height; radius: 2; color: (sysMonitor && sysMonitor.gpuUsage > 80) ? Theme.danger : Theme.warning }
        }
        Text { text: Math.round(sysMonitor ? sysMonitor.gpuUsage : 0) + "%"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10; Layout.preferredWidth: 32; horizontalAlignment: Text.AlignRight }
    }

    RowLayout {
        Layout.fillWidth: true; spacing: 8
        Text { text: "MEM"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10; Layout.preferredWidth: 32 }
        Rectangle {
            Layout.fillWidth: true; height: 4; radius: 2; color: Theme.surface
            Rectangle { width: parent.width * Math.min(1, (sysMonitor ? sysMonitor.memoryUsage : 0) / (8.0 * 1024 * 1024 * 1024)); height: parent.height; radius: 2; color: Theme.primary }
        }
        Text { text: sysMonitor ? sysMonitor.memoryFormatted : "0M"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10; Layout.preferredWidth: 32; horizontalAlignment: Text.AlignRight }
    }
}
