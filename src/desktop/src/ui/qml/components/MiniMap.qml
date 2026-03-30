import QtQuick
import Alice.UI

Rectangle {
    id: minimap
    property real zoomLevel: 1.0
    property real panX: 0.0
    property real panY: 0.0

    visible: zoomLevel > 1.0
    width: Theme.dp(60); height: Theme.dp(40)
    color: Qt.rgba(0.106, 0.125, 0.145, 0.85)
    border.width: 1; border.color: Theme.border; radius: Theme.radiusSm

    Rectangle {
        anchors.fill: parent; anchors.margins: 2
        color: "transparent"; border.width: 1; border.color: Theme.border; radius: 1

        Rectangle {
            property real viewW: Math.min(1.0, 1.0 / minimap.zoomLevel)
            property real viewH: Math.min(1.0, 1.0 / minimap.zoomLevel)

            x: parent.width * panX * (1 - viewW)
            y: parent.height * panY * (1 - viewH)
            width: parent.width * viewW
            height: parent.height * viewH
            color: Qt.rgba(0.17, 0.58, 0.84, 0.1)
            border.width: 1; border.color: Theme.primary; radius: 1
        }
    }
}
