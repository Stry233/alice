import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

Rectangle {
    color: "#1e1c22"

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
                color: "#a09da6"
            }

            GridLayout {
                columns: 2
                columnSpacing: 16
                rowSpacing: 4

                Label { text: "Depth:"; color: "#a09da6"; font.pixelSize: 12 }
                Label {
                    text: alice && alice.depth > 0 ? alice.depth.toFixed(3) + " m" : "—"
                    color: "#e6e1e5"
                    font.family: "RobotoMono"
                    font.pixelSize: 12
                }

                Label { text: "Confidence:"; color: "#a09da6"; font.pixelSize: 12 }
                Label {
                    text: Math.round((alice ? alice.depthConfidence : 0) * 100) + "%"
                    color: alice && alice.depthConfidence > 0.7 ? "#64ff64" : "#ffc832"
                    font.family: "RobotoMono"
                    font.pixelSize: 12
                }

                Label { text: "Motor:"; color: "#a09da6"; font.pixelSize: 12 }
                Label {
                    text: (alice ? alice.motorPosition : 0) + " / 4095"
                    color: "#e6e1e5"
                    font.family: "RobotoMono"
                    font.pixelSize: 12
                }

                Label { text: "Target:"; color: "#a09da6"; font.pixelSize: 12 }
                Label {
                    text: alice && alice.targetMotorPosition >= 0 ? alice.targetMotorPosition.toString() : "—"
                    color: "#d0bcff"
                    font.family: "RobotoMono"
                    font.pixelSize: 12
                }

                Label { text: "Mode:"; color: "#a09da6"; font.pixelSize: 12 }
                Label {
                    text: ["Manual", "AF-S", "AF-C", "AF-F"][alice ? alice.focusMode : 0] || "?"
                    color: alice && alice.activelyFocusing ? "#64ff64" : "#e6e1e5"
                    font.family: "RobotoMono"
                    font.pixelSize: 12
                }
            }

            Item { Layout.fillHeight: true }
        }

        // Separator
        Rectangle { Layout.fillHeight: true; width: 1; color: "#3b383e" }

        // Right: Log display
        LogDisplay {
            Layout.fillWidth: true
            Layout.fillHeight: true
            messages: alice ? alice.logMessages : []
        }
    }
}
