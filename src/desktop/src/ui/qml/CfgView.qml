import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import Alice.UI

Item {
    id: cfgView

    property int currentTab: 0  // 0=Calibration, 1=Settings, 2=Connection

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // Sub-tab bar
        Rectangle {
            Layout.fillWidth: true
            height: 32
            color: Theme.surface

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 12
                spacing: 0

                Repeater {
                    model: ["Calibration", "Settings", "Connection"]

                    Item {
                        required property string modelData
                        required property int index

                        Layout.preferredWidth: implicitWidth
                        implicitWidth: tabLabel.implicitWidth + 32
                        Layout.fillHeight: true

                        Text {
                            id: tabLabel
                            anchors.centerIn: parent
                            text: modelData
                            font.family: Theme.fontFamily
                            font.pixelSize: Theme.fontSizeCaption
                            font.weight: currentTab === index ? Font.DemiBold : Font.Normal
                            color: currentTab === index ? Theme.textPrimary : Theme.textSecondary
                        }

                        Rectangle {
                            anchors.bottom: parent.bottom
                            width: parent.width; height: 2
                            color: currentTab === index ? Theme.primary : "transparent"
                        }

                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.PointingHandCursor
                            onClicked: currentTab = index
                        }
                    }
                }

                Item { Layout.fillWidth: true }
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // Content
        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: currentTab

            CalibrationView {}
            SettingsView {}
            ConnectionView {}
        }
    }
}
