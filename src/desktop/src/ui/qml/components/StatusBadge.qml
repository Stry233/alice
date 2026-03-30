import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: badge
    property string label: ""
    property bool connected: false

    width: row.implicitWidth + 16
    height: 28
    radius: 14
    color: connected ? "#1b4332" : "#2b2930"
    border.color: connected ? "#64ff64" : "#3b383e"
    border.width: 1

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: 6

        Rectangle {
            width: 8; height: 8; radius: 4
            color: connected ? "#64ff64" : "#f2b8b5"
        }

        Label {
            text: badge.label
            font.pixelSize: 11
            color: connected ? "#e6e1e5" : "#a09da6"
        }
    }
}
