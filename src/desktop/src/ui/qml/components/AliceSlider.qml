import QtQuick
import QtQuick.Controls
import Alice.UI

// High-density slider per DRD §E: no ripple, no drop shadow, tight track,
// thumb grows exactly 2px on hover / press. Lives on top of the QQC2 Slider
// primitive so value/step/accessibility behaviour is untouched, only the
// visual is replaced.
Slider {
    id: control

    property int trackHeight: Theme.dp(4)
    property int thumbSize: Theme.dp(14)

    implicitWidth: Theme.dp(200)
    implicitHeight: Theme.dp(22)

    leftPadding: thumbSize / 2
    rightPadding: thumbSize / 2

    background: Rectangle {
        x: control.leftPadding
        y: control.topPadding + control.availableHeight / 2 - height / 2
        implicitWidth: Theme.dp(200)
        implicitHeight: control.trackHeight
        width: control.availableWidth
        height: control.trackHeight
        radius: height / 2
        color: Theme.border

        Rectangle {
            width: control.visualPosition * parent.width
            height: parent.height
            color: control.enabled ? Theme.primary : Theme.textDisabled
            radius: parent.radius
            Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
        }
    }

    handle: Rectangle {
        x: control.leftPadding + control.visualPosition * (control.availableWidth - width)
        y: control.topPadding + control.availableHeight / 2 - height / 2
        implicitWidth: control.thumbSize
        implicitHeight: control.thumbSize
        radius: width / 2
        color: control.pressed
            ? Qt.darker(Theme.primary, 1.2)
            : (control.enabled ? Theme.primary : Theme.textDisabled)
        border.width: 0
        antialiasing: true

        // +2px grow on hover/press — the DRD asks for an exact pixel delta.
        // We turn it into a scale so the center stays put and the Behavior
        // can interpolate smoothly.
        property real hoverScale: (control.thumbSize + 2.0) / control.thumbSize
        scale: (control.hovered || control.pressed) && control.enabled ? hoverScale : 1.0
        Behavior on scale { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
        Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
    }
}
