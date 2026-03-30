import QtQuick
import QtQuick.Layouts
import Alice.UI

ColumnLayout {
    spacing: 4

    Label {
        text: "TELEMETRY"
        font.pixelSize: Theme.sectionFontSize
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }

    GridLayout {
        columns: 2
        columnSpacing: 10
        rowSpacing: 2

        Text { text: "Depth:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Confidence:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"; color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Motor:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: (alice ? alice.motorPosition : 0) + " / 4095"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Target:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "Mode:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: ["Manual", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0] || "?"; color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }

        Text { text: "FPS:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: 10 }
        Text { text: "30"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: 10 }
    }
}
