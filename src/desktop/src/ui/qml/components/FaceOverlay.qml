import QtQuick
import Alice.UI

// Renders detected / tracked face bounding boxes (plus eye markers and an id
// label) over a video preview. Coordinates in `faces` are normalised [0..1]
// in the source frame; callers provide the destination rect (vidX/Y/W/H) so
// the overlay works for both the wide and std layouts where the RealSense
// color preview doesn't fill its container.
Item {
    id: root

    // Input: QVariantList where each element is a dict with keys:
    //   id, x, y, w, h (normalized), centerX, centerY, confidence,
    //   selected, state, color, leftEyeX, leftEyeY, rightEyeX, rightEyeY
    property var faces: []

    // Destination rectangle in local coordinates. Defaults to the whole item.
    property real vidX: 0
    property real vidY: 0
    property real vidW: width
    property real vidH: height

    // Called when the user clicks a face bbox — wire to alice.selectFace(id).
    signal faceClicked(int trackingId)

    Repeater {
        model: root.faces

        delegate: Item {
            id: faceItem
            anchors.fill: parent
            required property var modelData

            readonly property int    faceId       : modelData.id !== undefined ? modelData.id : -1
            readonly property bool   isSelected   : modelData.selected === true
            readonly property real   confidence   : modelData.confidence !== undefined ? modelData.confidence : 0
            // NOTE: Item already has a `state` property (the state-machine name),
            // so call the tracker state something else to avoid shadowing.
            readonly property int    trackState   : modelData.state !== undefined ? modelData.state : 0

            // Pixel-space bbox inside the overlay.
            readonly property real bx : root.vidX + modelData.x * root.vidW
            readonly property real by : root.vidY + modelData.y * root.vidH
            readonly property real bw : modelData.w * root.vidW
            readonly property real bh : modelData.h * root.vidH

            // Tracking state (mirrors TrackingState enum in SubjectTracker.h):
            //   0 = EyeLocked, 1 = FaceOnly, 2 = Predicted, 3 = Lost
            readonly property bool isPredicted: trackState === 2
            readonly property bool isEyeLocked: trackState === 0

            // Colour: selected → primary, predicted → muted orange, otherwise per-track color.
            readonly property color boxColor: {
                if (isSelected) return Theme.primary
                if (isPredicted) return Qt.rgba(1.0, 0.6, 0.2, 0.85)
                if (modelData.color !== undefined && modelData.color !== "")
                    return modelData.color
                return "#00c8ff"
            }

            // Main bbox — solid for fresh detections, dashed corners for predicted.
            Rectangle {
                x: faceItem.bx; y: faceItem.by
                width: faceItem.bw; height: faceItem.bh
                color: "transparent"
                border.color: faceItem.boxColor
                border.width: faceItem.isSelected ? 3 : (faceItem.isPredicted ? 1 : 2)
                opacity: faceItem.isPredicted ? 0.7 : 1.0
                radius: 2
                antialiasing: true

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.faceClicked(faceItem.faceId)
                }

                // Four L-shaped corner brackets drawn inside the bbox; these
                // give the box a "reticle" feel similar to pro AF overlays
                // and stay legible for small faces. Shown for predicted
                // state to reinforce the "coasting" visual cue.
                readonly property int bracketLen: Math.max(6, Math.floor(Math.min(width, height) * 0.18))
                readonly property int bracketW: faceItem.isSelected ? 3 : 2
                // top-left
                Rectangle { x: 0; y: 0; width: parent.bracketLen; height: parent.bracketW; color: faceItem.boxColor }
                Rectangle { x: 0; y: 0; width: parent.bracketW; height: parent.bracketLen; color: faceItem.boxColor }
                // top-right
                Rectangle { x: parent.width - parent.bracketLen; y: 0; width: parent.bracketLen; height: parent.bracketW; color: faceItem.boxColor }
                Rectangle { x: parent.width - parent.bracketW; y: 0; width: parent.bracketW; height: parent.bracketLen; color: faceItem.boxColor }
                // bottom-left
                Rectangle { x: 0; y: parent.height - parent.bracketW; width: parent.bracketLen; height: parent.bracketW; color: faceItem.boxColor }
                Rectangle { x: 0; y: parent.height - parent.bracketLen; width: parent.bracketW; height: parent.bracketLen; color: faceItem.boxColor }
                // bottom-right
                Rectangle { x: parent.width - parent.bracketLen; y: parent.height - parent.bracketW; width: parent.bracketLen; height: parent.bracketW; color: faceItem.boxColor }
                Rectangle { x: parent.width - parent.bracketW; y: parent.height - parent.bracketLen; width: parent.bracketW; height: parent.bracketLen; color: faceItem.boxColor }
            }

            // ID / confidence label. Anchored above the bbox when there is
            // room, otherwise placed inside the top of the bbox so it never
            // vanishes off-screen or stacks on the next face's label. The
            // confidence is clamped to [0,100] as a last-line defence against
            // any accidental out-of-range values flowing through from a
            // misread model tensor.
            Rectangle {
                readonly property real labelW: idLabel.implicitWidth + 10
                readonly property real labelH: idLabel.implicitHeight + 4
                readonly property real rawAboveY: faceItem.by - labelH - 2
                readonly property real fallbackInsideY: faceItem.by + 2
                x: Math.min(Math.max(faceItem.bx, root.vidX),
                            root.vidX + root.vidW - labelW)
                y: rawAboveY >= root.vidY ? rawAboveY : fallbackInsideY
                width: labelW
                height: labelH
                color: faceItem.isSelected ? Theme.primary : Qt.rgba(0, 0, 0, 0.72)
                radius: 2
                Text {
                    id: idLabel
                    anchors.centerIn: parent
                    text: {
                        var pct = Math.round(Math.max(0, Math.min(1, faceItem.confidence)) * 100)
                        return "#" + faceItem.faceId + "  " + pct + "%"
                    }
                    color: "#ffffff"
                    font.pixelSize: 11
                    font.family: Theme.fontFamilyMono
                }
            }

            // Eye markers — small squares at the absolute eye positions.
            Rectangle {
                visible: modelData.leftEyeX !== undefined
                x: root.vidX + (modelData.leftEyeX !== undefined ? modelData.leftEyeX : 0) * root.vidW - 4
                y: root.vidY + (modelData.leftEyeY !== undefined ? modelData.leftEyeY : 0) * root.vidH - 4
                width: 8; height: 8; radius: 2
                color: "transparent"
                border.color: "#00ffc8"
                border.width: 1.5
            }
            Rectangle {
                visible: modelData.rightEyeX !== undefined
                x: root.vidX + (modelData.rightEyeX !== undefined ? modelData.rightEyeX : 0) * root.vidW - 4
                y: root.vidY + (modelData.rightEyeY !== undefined ? modelData.rightEyeY : 0) * root.vidH - 4
                width: 8; height: 8; radius: 2
                color: "transparent"
                border.color: "#00ffc8"
                border.width: 1.5
            }
        }
    }
}
