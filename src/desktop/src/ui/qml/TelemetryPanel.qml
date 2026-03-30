import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

Rectangle {
    color: Theme.surface

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // Left: Key metrics
        ColumnLayout {
            Layout.preferredWidth: 300
            Layout.fillHeight: true
            Layout.margins: 12
            spacing: 8

            Label {
                text: "TELEMETRY"
                font.pixelSize: 11
                font.weight: Font.Bold
                font.letterSpacing: 1.5
                color: Theme.textSecondary
            }

            GridLayout {
                columns: 2
                columnSpacing: 16
                rowSpacing: 4

                Label { text: "Depth:"; color: Theme.textSecondary; font.pixelSize: 12 }
                Label {
                    text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"
                    color: Theme.textPrimary
                    font.family: Theme.fontFamilyMono
                    font.pixelSize: 12
                }

                Label { text: "Confidence:"; color: Theme.textSecondary; font.pixelSize: 12 }
                Label {
                    text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"
                    color: alice && alice.depthConfidence > 0.7 ? Theme.success : Theme.warning
                    font.family: Theme.fontFamilyMono
                    font.pixelSize: 12
                }

                Label { text: "Motor:"; color: Theme.textSecondary; font.pixelSize: 12 }
                Label {
                    text: (alice ? alice.motorPosition : 0) + " / 4095"
                    color: Theme.textPrimary
                    font.family: Theme.fontFamilyMono
                    font.pixelSize: 12
                }

                Label { text: "Target:"; color: Theme.textSecondary; font.pixelSize: 12 }
                Label {
                    text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"
                    color: Theme.primary
                    font.family: Theme.fontFamilyMono
                    font.pixelSize: 12
                }

                Label { text: "Mode:"; color: Theme.textSecondary; font.pixelSize: 12 }
                Label {
                    text: ["Manual", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0] || "?"
                    color: alice && alice.activelyFocusing ? Theme.success : Theme.textPrimary
                    font.family: Theme.fontFamilyMono
                    font.pixelSize: 12
                }
            }

            Item { Layout.fillHeight: true }
        }

        // Separator
        Rectangle { Layout.fillHeight: true; width: 1; color: Theme.border }

        // Right: Log display
        LogDisplay {
            Layout.fillWidth: true
            Layout.fillHeight: true
            messages: alice ? alice.logMessages : []
        }
    }
}
