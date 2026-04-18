import QtQuick
import Alice.UI

// Face overlay for AF-F. Three visual tiers — all distinguished by
// colour alone (Shape-based dashed strokes cost too many QML cycles
// in the per-face Repeater to be worth it at 15 Hz):
//
//   Primary   — 3 dp brackets + centre crosshair, Theme.primary,
//                tag "#NN  XX%"
//   Secondary — 2 dp brackets, Theme.textSecondary, tag "#NN"
//   Predicted — 2 dp brackets, Theme.warning, tag "#NN  EST"
//
// Coordinates in `faces` are normalised; callers pass the destination
// rect (vidX/Y/W/H) so one overlay drives both wide and std layouts.
Item {
    id: root

    property var faces: []
    property real vidX: 0
    property real vidY: 0
    property real vidW: width
    property real vidH: height

    signal faceClicked(int trackingId)

    readonly property color panelBg: Qt.rgba(0x0F / 255.0, 0x14 / 255.0, 0x1A / 255.0, 0.75)

    Repeater {
        model: root.faces

        delegate: Item {
            id: faceItem
            anchors.fill: parent
            required property var modelData

            readonly property int  faceId     : modelData.id !== undefined ? modelData.id : -1
            readonly property bool isSelected : modelData.selected === true
            readonly property real confidence : modelData.confidence !== undefined ? modelData.confidence : 0
            // Item already exposes `state`, so we shadow under a new name.
            readonly property int  trackState : modelData.state !== undefined ? modelData.state : 0

            readonly property real bx : root.vidX + modelData.x * root.vidW
            readonly property real by : root.vidY + modelData.y * root.vidH
            readonly property real bw : modelData.w * root.vidW
            readonly property real bh : modelData.h * root.vidH

            // 0 = EyeLocked, 1 = FaceOnly, 2 = Predicted, 3 = Lost
            readonly property bool isPredicted: trackState === 2

            readonly property color reticleColor: {
                if (isPredicted) return Theme.warning
                if (isSelected)  return Theme.primary
                return Theme.textSecondary
            }

            readonly property int strokeW   : isSelected ? Theme.dp(3) : Theme.dp(2)
            readonly property int cornerLen : Math.max(Theme.dp(8),
                                                       Math.floor(Math.min(bw, bh) * 0.18))

            readonly property string labelText: {
                const id = "#" + faceId
                if (isPredicted) return id + "  EST"
                if (isSelected) {
                    const pct = Math.round(Math.max(0, Math.min(1, confidence)) * 100)
                    return id + "  " + pct + "%"
                }
                return id
            }

            MouseArea {
                x: faceItem.bx; y: faceItem.by
                width: faceItem.bw; height: faceItem.bh
                cursorShape: Qt.PointingHandCursor
                onClicked: root.faceClicked(faceItem.faceId)
            }

            // Container for brackets + crosshair. Transforming the
            // container rides the children along via the scene graph
            // at no per-child cost.
            Item {
                id: bracketBox
                x: faceItem.bx; y: faceItem.by
                width: faceItem.bw; height: faceItem.bh

                property color stroke: faceItem.reticleColor
                property int sw: faceItem.strokeW
                property int len: faceItem.cornerLen

                Behavior on x      { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y      { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on width  { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on height { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on stroke { ColorAnimation  { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                // Eight bracket legs: TL-H, TL-V, TR-H, TR-V, BL-H, BL-V, BR-H, BR-V.
                Rectangle { x: 0;                                 y: 0;                                  width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: 0;                                 y: 0;                                  width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.len; y: 0;                                  width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.sw;  y: 0;                                  width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: 0;                                 y: bracketBox.height - bracketBox.sw;  width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: 0;                                 y: bracketBox.height - bracketBox.len; width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.len; y: bracketBox.height - bracketBox.sw;  width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.sw;  y: bracketBox.height - bracketBox.len; width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }

                // Centre crosshair (primary target only).
                Rectangle {
                    visible: faceItem.isSelected
                    x: (bracketBox.width  - Theme.dp(12)) * 0.5
                    y: (bracketBox.height - Theme.dp(1))  * 0.5
                    width: Theme.dp(12); height: Theme.dp(1)
                    color: bracketBox.stroke
                    antialiasing: false
                }
                Rectangle {
                    visible: faceItem.isSelected
                    x: (bracketBox.width  - Theme.dp(1))  * 0.5
                    y: (bracketBox.height - Theme.dp(12)) * 0.5
                    width: Theme.dp(1); height: Theme.dp(12)
                    color: bracketBox.stroke
                    antialiasing: false
                }
            }

            // Tag — flips to just-inside-top when the face sits near
            // the edge of the preview rect.
            Rectangle {
                id: tag
                color: root.panelBg
                border.color: Qt.rgba(faceItem.reticleColor.r,
                                      faceItem.reticleColor.g,
                                      faceItem.reticleColor.b, 0.30)
                border.width: 1
                radius: Theme.radiusSm

                readonly property int hPad: Theme.dp(5)
                readonly property int vPad: Theme.dp(2)
                width:  tagText.implicitWidth  + hPad * 2
                height: tagText.implicitHeight + vPad * 2

                readonly property real aboveY: faceItem.by - height - Theme.dp(2)
                x: faceItem.bx
                y: aboveY >= root.vidY ? aboveY : faceItem.by + Theme.dp(2)

                Behavior on x            { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y            { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on border.color { ColorAnimation  { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                Text {
                    id: tagText
                    x: tag.hPad; y: tag.vPad
                    text: faceItem.labelText
                    color: faceItem.reticleColor
                    font.family: Theme.fontFamilyMono
                    font.pixelSize: Theme.dp(12)
                    font.weight: Font.DemiBold

                    Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                }
            }
        }
    }
}
