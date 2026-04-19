import QtQuick
import Alice.UI

// Palantir / Blueprint-style face overlay for pro autofocus monitoring.
//
// Performance notes — every QObject here is created, bound, and torn
// down per Repeater model update. Earlier revisions used a nested
// `Leg` sub-component with a Repeater for dashed Predicted strokes;
// that's 30+ QObjects per face and at 15 Hz it was the dominant
// per-frame cost in AF-F mode — the UI thread saturated on one core
// (low aggregate CPU, but no QML paint budget left for the capture
// video, producing the sub-FPS monitor framerate the operator saw).
// This rewrite keeps ~15 QObjects per face: 8 plain Rectangle legs,
// 2 crosshair rectangles, one label panel, two Text elements for the
// drop shadow.
//
// Predicted state is now communicated solely by the colour swap to
// Theme.warning — three distinct Theme colours (Theme.primary for
// Selected, Theme.textSecondary for Secondary, Theme.warning for
// Predicted) read unambiguously as primary/secondary/coasting. A
// Shape-based dashed path is possible later but is not worth the
// overhead it puts on UI thread paint.
//
// Visual hierarchy — Primary dominates, everything else recedes so a
// dense crowd reads at a glance.
//
//   Primary   (selected / active AF target)
//     • 3 dp corner brackets + 12 dp centre crosshair, Theme.primary
//     • tag: "#NN  XX%"         (ID + confidence)
//
//   Secondary (tracked but not the AF target)
//     • 2 dp corner brackets, Theme.textSecondary
//     • tag: "#NN"              (ID only — confidence dropped)
//
//   Predicted (coasting — no fresh detection)
//     • 2 dp corner brackets, Theme.warning (colour signals state)
//     • tag: "#NN  EST"
//
// Coordinates in `faces` are normalised [0..1] in the source frame;
// callers supply the destination rect (vidX/Y/W/H) so the overlay
// works for both wide and std layouts.
Item {
    id: root

    property var faces: []
    property real vidX: 0
    property real vidY: 0
    property real vidW: width
    property real vidH: height

    signal faceClicked(int trackingId)

    // Dark slate panel at 75 % opacity — "Analytical Data Tag" spec.
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
            // NOTE: Item already has a `state` property, avoid shadowing.
            readonly property int  trackState : modelData.state !== undefined ? modelData.state : 0

            // Pixel-space bbox inside the overlay destination rect.
            readonly property real bx : root.vidX + modelData.x * root.vidW
            readonly property real by : root.vidY + modelData.y * root.vidH
            readonly property real bw : modelData.w * root.vidW
            readonly property real bh : modelData.h * root.vidH

            // Tracking state: 0 = EyeLocked, 1 = FaceOnly, 2 = Predicted, 3 = Lost
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

            // Invisible click target over the whole bbox. No Behaviors —
            // the user never sees it move, so animated transitions cost
            // QML scene-graph cycles for nothing.
            MouseArea {
                x: faceItem.bx; y: faceItem.by
                width: faceItem.bw; height: faceItem.bh
                cursorShape: Qt.PointingHandCursor
                onClicked: root.faceClicked(faceItem.faceId)
            }

            // Reticle container — 8 bracket legs + 2 crosshair
            // rectangles. All children inherit the transform, so a
            // face moving across the frame only animates this single
            // Item; the inner Rectangles ride along for free via the
            // scene-graph transform stack.
            Item {
                id: bracketBox
                x: faceItem.bx; y: faceItem.by
                width: faceItem.bw; height: faceItem.bh

                property color stroke: faceItem.reticleColor
                property int   sw    : faceItem.strokeW
                property int   len   : faceItem.cornerLen

                Behavior on x      { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y      { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on width  { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on height { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on stroke { ColorAnimation  { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                // Eight corner-bracket legs. Plain Rectangles — no Leg
                // sub-component, no Repeater, no visibility toggles.
                // antialiasing off because these are axis-aligned hairline
                // rects, AA only costs paint.
                //   TL-H / TL-V / TR-H / TR-V / BL-H / BL-V / BR-H / BR-V
                Rectangle { x: 0;                         y: 0;                            width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: 0;                         y: 0;                            width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.len; y: 0;                    width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.sw;  y: 0;                    width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: 0;                         y: bracketBox.height - bracketBox.sw;   width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: 0;                         y: bracketBox.height - bracketBox.len;  width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.len; y: bracketBox.height - bracketBox.sw;   width: bracketBox.len; height: bracketBox.sw;  color: bracketBox.stroke; antialiasing: false }
                Rectangle { x: bracketBox.width - bracketBox.sw;  y: bracketBox.height - bracketBox.len;  width: bracketBox.sw;  height: bracketBox.len; color: bracketBox.stroke; antialiasing: false }

                // Centre crosshair — positive-lock indicator. Only
                // drawn for the primary AF target. Delicate (12 dp × 1 dp)
                // so it reads as mechanical precision, not heavy HUD.
                Rectangle {
                    visible: faceItem.isSelected
                    x: (bracketBox.width  - Theme.dp(12)) * 0.5
                    y: (bracketBox.height - Theme.dp(1))  * 0.5
                    width:  Theme.dp(12); height: Theme.dp(1)
                    color: bracketBox.stroke
                    antialiasing: false
                }
                Rectangle {
                    visible: faceItem.isSelected
                    x: (bracketBox.width  - Theme.dp(1))  * 0.5
                    y: (bracketBox.height - Theme.dp(12)) * 0.5
                    width:  Theme.dp(1); height: Theme.dp(12)
                    color: bracketBox.stroke
                    antialiasing: false
                }
            }

            // Analytical Data Tag — panel + typography, tethered flush
            // to the top-left bracket. Flips to just-inside-top when
            // the face sits at the edge of the preview.
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

                Behavior on x           { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y           { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on border.color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

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
