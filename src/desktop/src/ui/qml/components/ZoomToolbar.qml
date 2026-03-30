import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    id: toolbar
    property real zoomLevel: 1.0
    signal zoomIn()
    signal zoomOut()
    signal zoomTo(real level)
    signal fitRequested()

    color: Qt.rgba(0.106, 0.125, 0.145, 0.92)
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    width: row.implicitWidth + 12
    height: 28

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: 2

        Image {
            source: "qrc:/qt/qml/Alice/UI/assets/icons/zoom_out.svg"
            width: 14; height: 14; sourceSize: Qt.size(14, 14)
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.zoomOut() }
        }

        Item {
            width: 70; height: 14
            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width; height: 3; radius: 2; color: Theme.border
                Rectangle {
                    width: parent.width * Math.max(0, (toolbar.zoomLevel - Theme.zoomMin) / (Theme.zoomMax - Theme.zoomMin))
                    height: parent.height; radius: 2; color: Theme.primary
                }
            }
            Rectangle {
                id: handle
                x: parent.width * Math.max(0, (toolbar.zoomLevel - Theme.zoomMin) / (Theme.zoomMax - Theme.zoomMin)) - 5
                anchors.verticalCenter: parent.verticalCenter
                width: 10; height: 10; radius: 5
                color: Theme.textPrimary; border.width: 1; border.color: Theme.border
            }
            MouseArea {
                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                onPositionChanged: (mouse) => {
                    let ratio = Math.max(0, Math.min(1, mouse.x / parent.width))
                    toolbar.zoomTo(Theme.zoomMin + ratio * (Theme.zoomMax - Theme.zoomMin))
                }
                onClicked: (mouse) => {
                    let ratio = Math.max(0, Math.min(1, mouse.x / parent.width))
                    toolbar.zoomTo(Theme.zoomMin + ratio * (Theme.zoomMax - Theme.zoomMin))
                }
            }
        }

        Image {
            source: "qrc:/qt/qml/Alice/UI/assets/icons/zoom_in.svg"
            width: 14; height: 14; sourceSize: Qt.size(14, 14)
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.zoomIn() }
        }

        Rectangle { width: 1; height: 12; color: Theme.border }

        Text {
            text: Math.round(toolbar.zoomLevel * 100) + "%"
            font.family: Theme.fontFamilyMono; font.pixelSize: 9
            color: Theme.textSecondary
            Layout.preferredWidth: 32; horizontalAlignment: Text.AlignHCenter
        }

        Rectangle { width: 1; height: 12; color: Theme.border }

        Text {
            text: "FIT"
            font.family: Theme.fontFamily; font.pixelSize: 9; color: Theme.textPrimary
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.fitRequested() }
        }
    }
}
