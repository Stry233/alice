import QtQuick
import QtQuick.Window

Window {
    id: splash
    width: 520; height: 320
    flags: Qt.SplashScreen | Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint
    color: "transparent"
    visible: true
    x: (Screen.width - width) / 2
    y: (Screen.height - height) / 2

    // Auto-close safety net
    Timer { interval: 8000; running: true; onTriggered: splash.visible = false }

    Rectangle {
        anchors.fill: parent
        radius: 12
        color: "#1B2025"
        border.width: 1
        border.color: "#394049"

        // Subtle gradient at top
        Rectangle {
            anchors.top: parent.top; anchors.left: parent.left; anchors.right: parent.right
            height: 120; radius: 12
            gradient: Gradient {
                GradientStop { position: 0.0; color: Qt.rgba(0.17, 0.58, 0.84, 0.08) }
                GradientStop { position: 1.0; color: "transparent" }
            }
        }

        // Logo
        Image {
            id: logo
            anchors.horizontalCenter: parent.horizontalCenter
            y: 55
            width: 64; height: 72
            sourceSize: Qt.size(128, 144)
            source: "qrc:/qt/qml/Alice/UI/assets/icons/alice_logo.svg"
            fillMode: Image.PreserveAspectFit
        }

        // App name
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            y: logo.y + logo.height + 14
            text: "Alice Studio"
            font.family: "Inter"
            font.pixelSize: 28
            font.weight: Font.Bold
            font.letterSpacing: 3
            color: "#E1E8ED"
        }

        // Subtitle
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            y: logo.y + logo.height + 48
            text: "Autofocus Lens Interface for Cinema Equipment"
            font.family: "Inter"
            font.pixelSize: 12
            color: "#8A9BA8"
        }

        // Loading bar
        Rectangle {
            anchors.bottom: parent.bottom; anchors.bottomMargin: 44
            anchors.horizontalCenter: parent.horizontalCenter
            width: 200; height: 2; radius: 1
            color: "#394049"

            Rectangle {
                height: parent.height; radius: 1
                color: "#2B95D6"; width: 60

                SequentialAnimation on x {
                    loops: Animation.Infinite
                    NumberAnimation { from: 0; to: 140; duration: 1200; easing.type: Easing.InOutQuad }
                    NumberAnimation { from: 140; to: 0; duration: 1200; easing.type: Easing.InOutQuad }
                }
            }
        }

        // Status
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom; anchors.bottomMargin: 54
            text: "Initializing..."
            font.family: "Inter"; font.pixelSize: 11
            color: "#5C6B7A"
        }

        // Version
        Text {
            anchors.right: parent.right; anchors.bottom: parent.bottom
            anchors.margins: 16
            text: "v0.2"
            font.family: "Inter"; font.pixelSize: 11
            color: "#5C6B7A"
        }

        // Copyright
        Text {
            anchors.left: parent.left; anchors.bottom: parent.bottom
            anchors.margins: 16
            text: "\u00A9 2025 SelkaCraft"
            font.family: "Inter"; font.pixelSize: 11
            color: "#5C6B7A"
        }
    }
}
