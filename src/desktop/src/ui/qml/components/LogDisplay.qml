import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

Item {
    id: logDisplay
    property var messages: []

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 12
        spacing: 4

        RowLayout {
            Label {
                text: "LOG"
                font.pixelSize: 11
                font.weight: Font.Bold
                font.letterSpacing: 1.5
                color: "#a09da6"
            }
            Item { Layout.fillWidth: true }
            Label {
                text: messages.length + " entries"
                font.pixelSize: 10
                color: "#a09da6"
            }
        }

        ListView {
            id: logList
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            model: messages

            delegate: Label {
                required property string modelData
                required property int index
                width: logList.width
                text: modelData
                font.family: "RobotoMono"
                font.pixelSize: 11
                wrapMode: Text.NoWrap
                elide: Text.ElideRight

                color: {
                    if (modelData.indexOf("[ERROR]") !== -1) return "#f2b8b5"
                    if (modelData.indexOf("[WARNING]") !== -1) return "#ffc832"
                    if (modelData.indexOf("[DEBUG]") !== -1) return "#6b6774"
                    return "#a09da6"
                }
            }

            // Auto-scroll to bottom
            onCountChanged: {
                Qt.callLater(function() {
                    logList.positionViewAtEnd()
                })
            }
        }
    }
}
