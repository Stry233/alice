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

            // Tracking state: 0 = EyeLocked, 1 = FaceOnly, 2 = Predicted, 3 = Lost
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
            //
            // Position and size animate on a 100ms OutCubic curve to smooth
            // detector jitter without lagging a moving subject. The state
            // crossfade (selected ↔ predicted) uses 150ms so the colour and
            // stroke weight shift is perceptible rather than an abrupt snap.
            // `strokeColor` / `strokeWidth` are writable intermediary props
            // so the change cascades into every bracket rectangle via their
            // parent.* bindings — otherwise each bracket would need its own
            // Behavior block.
            Rectangle {
                id: bboxRect
                x: faceItem.bx; y: faceItem.by
                width: faceItem.bw; height: faceItem.bh
                color: "transparent"
                radius: 2
                antialiasing: true

                property color strokeColor: faceItem.boxColor
                property int strokeWidth: faceItem.isSelected ? 3 : (faceItem.isPredicted ? 1 : 2)
                property int bracketStroke: faceItem.isSelected ? 3 : 2
                property int bracketLen: Math.max(6, Math.floor(Math.min(width, height) * 0.18))

                border.color: strokeColor
                border.width: strokeWidth
                opacity: faceItem.isPredicted ? 0.7 : 1.0

                Behavior on x { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on width { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on height { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on strokeColor { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                Behavior on strokeWidth { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                Behavior on bracketStroke { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                Behavior on opacity { NumberAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }

                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.faceClicked(faceItem.faceId)
                }

                // Four L-shaped corner brackets drawn inside the bbox; these
                // give the box a "reticle" feel similar to pro AF overlays
                // and stay legible for small faces. Shown for predicted
                // state to reinforce the "coasting" visual cue.
                // top-left
                Rectangle { x: 0; y: 0; width: parent.bracketLen; height: parent.bracketStroke; color: parent.strokeColor }
                Rectangle { x: 0; y: 0; width: parent.bracketStroke; height: parent.bracketLen; color: parent.strokeColor }
                // top-right
                Rectangle { x: parent.width - parent.bracketLen; y: 0; width: parent.bracketLen; height: parent.bracketStroke; color: parent.strokeColor }
                Rectangle { x: parent.width - parent.bracketStroke; y: 0; width: parent.bracketStroke; height: parent.bracketLen; color: parent.strokeColor }
                // bottom-left
                Rectangle { x: 0; y: parent.height - parent.bracketStroke; width: parent.bracketLen; height: parent.bracketStroke; color: parent.strokeColor }
                Rectangle { x: 0; y: parent.height - parent.bracketLen; width: parent.bracketStroke; height: parent.bracketLen; color: parent.strokeColor }
                // bottom-right
                Rectangle { x: parent.width - parent.bracketLen; y: parent.height - parent.bracketStroke; width: parent.bracketLen; height: parent.bracketStroke; color: parent.strokeColor }
                Rectangle { x: parent.width - parent.bracketStroke; y: parent.height - parent.bracketLen; width: parent.bracketStroke; height: parent.bracketLen; color: parent.strokeColor }
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
                Behavior on color { ColorAnimation { duration: Theme.durationNormal; easing.type: Easing.OutCubic } }
                Behavior on x { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
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

            // Eye markers — small squares at the absolute eye positions. A
            // matching 100ms Behavior keeps eye overlays in step with bbox
            // motion so they don't appear to drift independently.
            Rectangle {
                visible: modelData.leftEyeX !== undefined
                x: root.vidX + (modelData.leftEyeX !== undefined ? modelData.leftEyeX : 0) * root.vidW - 4
                y: root.vidY + (modelData.leftEyeY !== undefined ? modelData.leftEyeY : 0) * root.vidH - 4
                width: 8; height: 8; radius: 2
                color: "transparent"
                border.color: "#00ffc8"
                border.width: 1.5
                Behavior on x { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            }
            Rectangle {
                visible: modelData.rightEyeX !== undefined
                x: root.vidX + (modelData.rightEyeX !== undefined ? modelData.rightEyeX : 0) * root.vidW - 4
                y: root.vidY + (modelData.rightEyeY !== undefined ? modelData.rightEyeY : 0) * root.vidH - 4
                width: 8; height: 8; radius: 2
                color: "transparent"
                border.color: "#00ffc8"
                border.width: 1.5
                Behavior on x { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                Behavior on y { NumberAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
            }
        }
    }
}
