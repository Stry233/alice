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

        // Header — title sits at the same Y (top: dp(20)) as the MOTOR POSITION
        // and CALIBRATION CURVE titles in the surrounding view so they line
        // up horizontally across the left/center/right columns.
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: Theme.dp(56)
            color: Theme.bg
            RowLayout {
                anchors.left: parent.left; anchors.right: parent.right
                anchors.top: parent.top
                anchors.leftMargin: Theme.dp(20); anchors.rightMargin: Theme.dp(20)
                anchors.topMargin: Theme.dp(20)
                spacing: Theme.dp(8)
                Text {
                    text: "RECORDED POINTS"
                    font.family: Theme.fontFamily
                    font.pixelSize: Theme.sectionFontSize
                    font.weight: Font.DemiBold
                    font.letterSpacing: Theme.sectionLetterSpacing
                    color: Theme.textSecondary
                    Layout.fillWidth: true
                }
                Text {
                    text: points.length + " points"
                    font.pixelSize: Theme.fontSizeSmall
                    color: points.length >= 3 ? Theme.success : Theme.warning
                    Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Column headers
        Rectangle {
            Layout.fillWidth: true; height: Theme.dp(36); color: Theme.surface
            RowLayout {
                anchors.fill: parent; anchors.leftMargin: Theme.dp(16); anchors.rightMargin: Theme.dp(16); spacing: Theme.dp(8)
                Text { text: "#"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.preferredWidth: Theme.dp(40) }
                Text { text: "Depth"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.fillWidth: true }
                Text { text: "Motor"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.fillWidth: true }
                Text { text: "Conf"; font.pixelSize: Theme.sectionFontSize; font.weight: Font.DemiBold; color: Theme.textDisabled; Layout.preferredWidth: Theme.dp(80) }
                Item { Layout.preferredWidth: Theme.dp(40) }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        ListView {
            Layout.fillWidth: true; Layout.fillHeight: true; clip: true; model: points
            delegate: Rectangle {
                required property var modelData
                required property int index
                width: table.width; height: Theme.dp(40)
                color: index % 2 === 0 ? "transparent" : Qt.rgba(1, 1, 1, 0.015)

                RowLayout {
                    anchors.fill: parent; anchors.leftMargin: Theme.dp(16); anchors.rightMargin: Theme.dp(16); spacing: Theme.dp(8)
                    Text { text: (index + 1).toString(); font.pixelSize: Theme.fontSizeSmall; color: Theme.textDisabled; Layout.preferredWidth: Theme.dp(40) }
                    Text { text: modelData.depth.toFixed(2) + " m"; font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.textPrimary; Layout.fillWidth: true }
                    Text { text: modelData.motorPosition.toString(); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeSmall; color: Theme.primary; Layout.fillWidth: true }
                    // Confidence bar
                    Row {
                        Layout.preferredWidth: Theme.dp(80); spacing: Theme.dp(6)
                        Rectangle {
                            width: Theme.dp(40); height: Theme.dp(6); radius: Theme.radiusSm; color: Theme.surface; anchors.verticalCenter: parent.verticalCenter
                            Rectangle { width: parent.width * modelData.confidence; height: parent.height; radius: 2; color: modelData.confidence > 0.7 ? Theme.success : Theme.warning }
                        }
                        Text { text: Math.round(modelData.confidence * 100).toString(); font.family: Theme.fontFamilyMono; font.pixelSize: Theme.sectionFontSize; color: Theme.textSecondary }
                    }
                    Text { text: "\u00D7"; font.pixelSize: Theme.fontSizeSmall; color: Theme.textDisabled; Layout.preferredWidth: Theme.dp(40); horizontalAlignment: Text.AlignHCenter
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: table.pointRemoved(index) }
                    }
                }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Footer — fixed height dp(72) so it visually matches the Record Point
        // footer in CalibrationView (same dp(40) button inside dp(16) margins).
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: Theme.dp(72)
            Layout.maximumHeight: Theme.dp(72)
            color: Theme.bg
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: Theme.dp(16); anchors.rightMargin: Theme.dp(16)
                anchors.topMargin: Theme.dp(16); anchors.bottomMargin: Theme.dp(16)
                spacing: Theme.dp(8)
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: Theme.dp(40)
                    Layout.alignment: Qt.AlignVCenter
                    radius: Theme.radiusSm
                    color: Theme.primary
                    opacity: points.length >= 3 ? 1.0 : 0.4
                    Behavior on opacity { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                    Text { anchors.centerIn: parent; text: "Export Mapping"; font.pixelSize: Theme.fontSizeSmall; font.weight: Font.DemiBold; color: "#fff" }
                    MouseArea { anchors.fill: parent; enabled: points.length >= 3; cursorShape: Qt.PointingHandCursor; onClicked: table.exportRequested() }
                }
                Rectangle {
                    Layout.preferredWidth: Theme.dp(100)
                    Layout.preferredHeight: Theme.dp(40)
                    Layout.alignment: Qt.AlignVCenter
                    radius: Theme.radiusSm
                    color: clearMa.pressed ? Qt.darker(Theme.dangerMuted, 1.15)
                         : (clearMa.containsMouse ? Theme.dangerMuted : "transparent")
                    border.width: 1; border.color: clearMa.containsMouse ? Theme.danger : Qt.rgba(0.86, 0.22, 0.22, 0.4)
                    Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                    Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                    Text { anchors.centerIn: parent; text: "Clear"; font.pixelSize: Theme.fontSizeSmall; color: Theme.dangerText }
                    MouseArea { id: clearMa; anchors.fill: parent; hoverEnabled: true; cursorShape: Qt.PointingHandCursor; onClicked: table.clearRequested() }
                }
            }
        }
    }
}
