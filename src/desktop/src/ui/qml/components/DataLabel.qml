import QtQuick
import QtQuick.Layouts
import Alice.UI

RowLayout {
    property string label: ""
    property string value: ""
    property color valueColor: Theme.textPrimary
    Layout.fillWidth: true
    spacing: Theme.spaceSm

    Text {
        text: label
        font.family: Theme.fontFamily
        font.pixelSize: Theme.fontSizeSmall
        color: Theme.textSecondary
        Layout.preferredWidth: 80
    }
    Text {
        text: value
        font.family: Theme.fontFamilyMono
        font.pixelSize: Theme.fontSizeSmall
        color: valueColor
        Layout.fillWidth: true
    }
}
