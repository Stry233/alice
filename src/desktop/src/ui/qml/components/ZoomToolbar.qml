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
    width: row.implicitWidth + Theme.dp(24)
    height: Theme.dp(44)

    RowLayout {
        id: row
        anchors.centerIn: parent
        spacing: Theme.dp(8)

        Image {
            source: "qrc:/qt/qml/Alice/UI/assets/icons/zoom_out.svg"
            width: Theme.dp(24); height: Theme.dp(24); sourceSize: Qt.size(Theme.dp(24), Theme.dp(24))
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.zoomOut() }
        }

        Item {
            width: Theme.dp(120); height: Theme.dp(24)
            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width; height: Theme.dp(6); radius: Theme.dp(3); color: Theme.border
                Rectangle {
                    width: parent.width * Math.max(0, (toolbar.zoomLevel - Theme.zoomMin) / (Theme.zoomMax - Theme.zoomMin))
                    height: parent.height; radius: Theme.dp(3); color: Theme.primary
                }
            }
            Rectangle {
                x: parent.width * Math.max(0, (toolbar.zoomLevel - Theme.zoomMin) / (Theme.zoomMax - Theme.zoomMin)) - Theme.dp(10)
                anchors.verticalCenter: parent.verticalCenter
                width: Theme.dp(20); height: Theme.dp(20); radius: Theme.dp(10)
                color: Theme.textPrimary; border.width: 1; border.color: Theme.border
            }
            MouseArea {
                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                preventStealing: true
                onPressed: (mouse) => {
                    let ratio = Math.max(0, Math.min(1, mouse.x / width))
                    toolbar.zoomTo(Theme.zoomMin + ratio * (Theme.zoomMax - Theme.zoomMin))
                }
                onPositionChanged: (mouse) => {
                    let ratio = Math.max(0, Math.min(1, mouse.x / width))
                    toolbar.zoomTo(Theme.zoomMin + ratio * (Theme.zoomMax - Theme.zoomMin))
                }
            }
        }

        Image {
            source: "qrc:/qt/qml/Alice/UI/assets/icons/zoom_in.svg"
            width: Theme.dp(24); height: Theme.dp(24); sourceSize: Qt.size(Theme.dp(24), Theme.dp(24))
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.zoomIn() }
        }

        Rectangle { width: 1; height: Theme.dp(24); color: Theme.border }

        Text {
            text: Math.round(toolbar.zoomLevel * 100) + "%"
            font.family: Theme.fontFamilyMono; font.pixelSize: Theme.fontSizeMicro
            color: Theme.textSecondary
            Layout.preferredWidth: Theme.dp(48); horizontalAlignment: Text.AlignHCenter
        }

        Rectangle { width: 1; height: Theme.dp(24); color: Theme.border }

        Text {
            text: "FIT"
            font.family: Theme.fontFamily; font.pixelSize: Theme.fontSizeMicro; color: Theme.textPrimary
            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: toolbar.fitRequested() }
        }
    }
}
