import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Alice.UI

ColumnLayout {
    property string text: ""
    spacing: Theme.spaceXs

    Label {
        text: parent.text.toUpperCase()
        font.family: Theme.fontFamily
        font.pixelSize: Theme.fontSizeCaption
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }
    Rectangle {
        Layout.fillWidth: true
        height: 1
        color: Theme.border
    }
}
