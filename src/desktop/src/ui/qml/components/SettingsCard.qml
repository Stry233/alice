import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    property string title: ""
    property int columnSpan: 1
    default property alias content: contentColumn.children

    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    implicitHeight: mainColumn.implicitHeight + 2 * 12

    Layout.columnSpan: columnSpan
    Layout.fillWidth: true

    ColumnLayout {
        id: mainColumn
        anchors.fill: parent
        anchors.margins: 12
        spacing: 10

        Text {
            visible: title !== ""
            text: title.toUpperCase()
            font.family: Theme.fontFamily
            font.pixelSize: 10
            font.weight: Font.DemiBold
            font.letterSpacing: 1.0
            color: Theme.textPrimary
        }
        Rectangle {
            visible: title !== ""
            Layout.fillWidth: true
            height: 1
            color: Theme.border
        }
        ColumnLayout {
            id: contentColumn
            Layout.fillWidth: true
            spacing: 10
        }
    }
}
