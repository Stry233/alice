import QtQuick
import QtQuick.Controls
import Alice.UI

// Text input with a crisp 1px border that lerps to primary on
// focus (DRD §E). No drop shadow, no Material underline.
TextField {
    id: control

    color: Theme.textPrimary
    selectionColor: Theme.primary
    selectedTextColor: "#ffffff"
    placeholderTextColor: Theme.textPlaceholder
    font.family: Theme.fontFamily
    font.pixelSize: Theme.fontSizeSmall

    implicitHeight: Theme.inputHeight
    leftPadding: Theme.dp(12)
    rightPadding: Theme.dp(12)

    background: Rectangle {
        color: control.enabled ? Theme.surface : Theme.elevated
        border.width: 1
        border.color: control.activeFocus
            ? Theme.primary
            : (control.hovered ? Theme.borderStrong : Theme.border)
        radius: Theme.radiusSm
        Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
    }
}
