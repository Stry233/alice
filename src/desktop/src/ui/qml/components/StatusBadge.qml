import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: badge
    property string label: ""
    property bool connected: false

    width: row.implicitWidth + 16
    height: 28
    radius: Theme.radiusSm
    color: connected ? Theme.successMuted : Theme.elevated
    border.color: connected ? Theme.success : Theme.border
    border.width: 1

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: 6

        Rectangle {
            width: 8; height: 8; radius: 4
            color: connected ? Theme.success : Theme.dangerText
        }

        Label {
            text: badge.label
            font.pixelSize: 11
            color: connected ? Theme.textPrimary : Theme.textSecondary
        }
    }
}
