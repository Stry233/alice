import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Alice.UI

ColumnLayout {
    spacing: Theme.dp(8)

    Label {
        text: "TELEMETRY"
        font.pixelSize: Theme.sectionFontSize
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }

    GridLayout {
        columns: 2
        columnSpacing: Theme.dp(20)
        rowSpacing: Theme.dp(4)

        Text { text: "Depth:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall }
        Text { text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }

        Text { text: "Confidence:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall }
        Text { text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"; color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }

        Text { text: "Motor:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall }
        Text { text: (alice ? alice.motorPosition : 0) + " / 4095"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }

        Text { text: "Target:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall }
        Text { text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"; color: Theme.primary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }

        Text { text: "Mode:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall }
        Text { text: ["Manual", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0] || "?"; color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }

        Text { text: "FPS:"; color: Theme.textSecondary; font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeSmall }
        Text { text: "30"; color: Theme.textPrimary; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall }
    }
}
