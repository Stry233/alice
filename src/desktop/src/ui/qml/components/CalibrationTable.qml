import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    id: table
    property var points: []
    signal pointRemoved(int index)
    signal exportRequested()
    signal clearRequested()

    color: Theme.bg

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Header
        Rectangle {
            Layout.fillWidth: true; height: 28; color: Theme.bg
            RowLayout {
                anchors.fill: parent; anchors.leftMargin: 8; anchors.rightMargin: 8; spacing: 4
                Text { text: "RECORDED POINTS"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; font.letterSpacing: Theme.sectionLetterSpacing; color: Theme.textSecondary; Layout.fillWidth: true }
                Text { text: points.length + " points"; font.pixelSize: 10; color: points.length >= 3 ? Theme.success : Theme.warning }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Column headers
        Rectangle {
            Layout.fillWidth: true; height: 22; color: Theme.surface
            RowLayout {
                anchors.fill: parent; anchors.leftMargin: 8; anchors.rightMargin: 8; spacing: 4
                Text { text: "#"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.preferredWidth: 20 }
                Text { text: "Depth"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.fillWidth: true }
                Text { text: "Motor"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.fillWidth: true }
                Text { text: "Conf"; font.pixelSize: 9; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.preferredWidth: 44 }
                Item { Layout.preferredWidth: 20 }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Rows
        ListView {
            Layout.fillWidth: true; Layout.fillHeight: true; clip: true; model: points
            delegate: Rectangle {
                required property var modelData
                required property int index
                width: table.width; height: 26
                color: index % 2 === 0 ? "transparent" : Qt.rgba(1, 1, 1, 0.015)

                RowLayout {
                    anchors.fill: parent; anchors.leftMargin: 8; anchors.rightMargin: 8; spacing: 4
                    Text { text: (index + 1).toString(); font.pixelSize: 10; color: Theme.textDisabled; Layout.preferredWidth: 20 }
                    Text { text: modelData.depth.toFixed(2) + " m"; font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.textPrimary; Layout.fillWidth: true }
                    Text { text: modelData.motorPosition.toString(); font.family: Theme.fontFamilyMono; font.pixelSize: 10; color: Theme.primary; Layout.fillWidth: true }
                    // Confidence bar
                    Row {
                        Layout.preferredWidth: 44; spacing: 3
                        Rectangle {
                            width: 24; height: 3; radius: 2; color: Theme.surface; anchors.verticalCenter: parent.verticalCenter
                            Rectangle { width: parent.width * modelData.confidence; height: parent.height; radius: 2; color: modelData.confidence > 0.7 ? Theme.success : Theme.warning }
                        }
                        Text { text: Math.round(modelData.confidence * 100).toString(); font.family: Theme.fontFamilyMono; font.pixelSize: 9; color: Theme.textSecondary }
                    }
                    Text { text: "\u00D7"; font.pixelSize: 12; color: Theme.textDisabled; Layout.preferredWidth: 20; horizontalAlignment: Text.AlignHCenter
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: table.pointRemoved(index) }
                    }
                }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Footer
        RowLayout {
            Layout.fillWidth: true; Layout.margins: 8; spacing: 4
            Rectangle {
                Layout.fillWidth: true; height: 24; radius: Theme.radiusSm; color: Theme.primary; opacity: points.length >= 3 ? 1.0 : 0.4
                Text { anchors.centerIn: parent; text: "Export Mapping"; font.pixelSize: 10; font.weight: Font.DemiBold; color: "#fff" }
                MouseArea { anchors.fill: parent; enabled: points.length >= 3; cursorShape: Qt.PointingHandCursor; onClicked: table.exportRequested() }
            }
            Rectangle {
                width: 60; height: 24; radius: Theme.radiusSm; color: "transparent"; border.width: 1; border.color: Qt.rgba(0.86, 0.22, 0.22, 0.4)
                Text { anchors.centerIn: parent; text: "Clear"; font.pixelSize: 10; color: Theme.dangerText }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: table.clearRequested() }
            }
        }
    }
}
