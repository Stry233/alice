import QtQuick
import QtQuick.Layouts
import Alice.UI

Rectangle {
    property string title: ""
    default property alias content: contentColumn.children

    color: Theme.surface
    border.width: 1
    border.color: Theme.border
    radius: Theme.radiusSm
    implicitHeight: mainColumn.implicitHeight + 2 * Theme.spaceMd

    ColumnLayout {
        id: mainColumn
        anchors.fill: parent
        anchors.margins: Theme.spaceMd
        spacing: Theme.spaceSm

        Text {
            visible: title !== ""
            text: title.toUpperCase()
            font.family: Theme.fontFamily
            font.pixelSize: Theme.fontSizeCaption
            font.weight: Font.DemiBold
            font.letterSpacing: Theme.sectionLetterSpacing
            color: Theme.textSecondary
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
            spacing: Theme.spaceSm
        }
    }
}
