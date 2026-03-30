import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Alice.UI

Item {
    property string text: ""
    implicitHeight: label.implicitHeight
    Layout.fillWidth: true

    Text {
        id: label
        text: parent.text.toUpperCase()
        font.family: Theme.fontFamily
        font.pixelSize: Theme.sectionFontSize
        font.weight: Font.DemiBold
        font.letterSpacing: Theme.sectionLetterSpacing
        color: Theme.textSecondary
    }
}
