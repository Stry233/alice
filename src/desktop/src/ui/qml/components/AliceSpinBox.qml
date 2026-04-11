import QtQuick
import QtQuick.Controls
import Alice.UI

// Minimal, high-density spin box per DRD §E. Custom up/down indicators are
// plain text glyphs that swap background tone on press; no Material ripple
// or elevation.
SpinBox {
    id: control

    implicitHeight: Theme.inputHeight
    editable: true
    font.family: Theme.fontFamilyMono
    font.pixelSize: Theme.fontSizeSmall

    contentItem: TextInput {
        z: 2
        text: control.displayText
        font: control.font
        color: Theme.textPrimary
        selectionColor: Theme.primary
        selectedTextColor: "#ffffff"
        horizontalAlignment: Qt.AlignHCenter
        verticalAlignment: Qt.AlignVCenter
        readOnly: !control.editable
        validator: control.validator
        inputMethodHints: Qt.ImhFormattedNumbersOnly
    }

    background: Rectangle {
        color: Theme.surface
        border.width: 1
        border.color: control.activeFocus
            ? Theme.primary
            : (control.hovered ? Theme.borderStrong : Theme.border)
        radius: Theme.radiusSm
        Behavior on border.color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
    }

    up.indicator: Rectangle {
        x: control.mirrored ? 0 : parent.width - width
        height: parent.height
        implicitWidth: Theme.dp(28)
        implicitHeight: Theme.dp(28)
        color: control.up.pressed ? Theme.surfaceActive
             : (control.up.hovered ? Theme.surfaceHover : "transparent")
        Text {
            anchors.fill: parent
            text: "+"
            font.family: Theme.fontFamily
            font.pixelSize: Theme.fontSizeBody
            color: Theme.textPrimary
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
    }

    down.indicator: Rectangle {
        x: control.mirrored ? parent.width - width : 0
        height: parent.height
        implicitWidth: Theme.dp(28)
        implicitHeight: Theme.dp(28)
        color: control.down.pressed ? Theme.surfaceActive
             : (control.down.hovered ? Theme.surfaceHover : "transparent")
        Text {
            anchors.fill: parent
            text: "\u2212"  // true minus sign
            font.family: Theme.fontFamily
            font.pixelSize: Theme.fontSizeBody
            color: Theme.textPrimary
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
    }
}
