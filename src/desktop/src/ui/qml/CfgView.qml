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

        // Sub-tab bar — height dp(48), font dp(22), indicator dp(4)
        //
        // A single indicator Rectangle slides between tab positions. Its
        // x/width are synced imperatively via `syncIndicator()` whenever a
        // tab's geometry settles or the current tab changes; this avoids
        // relying on QML change notifications for a JS array whose reference
        // may not change even when its contents do.
        Rectangle {
            Layout.fillWidth: true
            height: Theme.dp(48)
            color: Theme.surface

            Item {
                id: tabBar
                anchors.fill: parent
                anchors.leftMargin: Theme.dp(24)

                function syncIndicator() {
                    var item = tabRepeater.itemAt(cfgView.currentTab)
                    if (!item || item.width <= 0) return
                    tabIndicator.x = item.x
                    tabIndicator.width = item.width
                }

                RowLayout {
                    anchors.fill: parent
                    spacing: 0

                    Repeater {
                        id: tabRepeater
                        model: ["Calibration", "Settings", "Connection"]

                        Item {
                            id: tabItem
                            required property string modelData
                            required property int index

                            Layout.preferredWidth: implicitWidth
                            implicitWidth: tabLabel.implicitWidth + Theme.dp(64)
                            Layout.fillHeight: true

                            Text {
                                id: tabLabel
                                anchors.centerIn: parent
                                text: modelData
                                font.family: Theme.fontFamily
                                font.pixelSize: Theme.fontSizeSmall
                                font.weight: currentTab === index ? Font.DemiBold : Font.Normal
                                color: currentTab === index ? Theme.textPrimary : Theme.textSecondary
                                Behavior on color { ColorAnimation { duration: Theme.durationFast; easing.type: Easing.OutCubic } }
                            }

                            onXChanged: if (index === cfgView.currentTab) tabBar.syncIndicator()
                            onWidthChanged: if (index === cfgView.currentTab) tabBar.syncIndicator()
                            Component.onCompleted: if (index === cfgView.currentTab) tabBar.syncIndicator()

                            MouseArea {
                                anchors.fill: parent
                                cursorShape: Qt.PointingHandCursor
                                onClicked: currentTab = index
                            }
                        }
                    }

                    Item { Layout.fillWidth: true }
                }

                // Single sliding underline indicator (DRD §B). x/width are
                // pushed imperatively by syncIndicator(); Behavior still
                // animates direct assignments, so every click slides.
                Rectangle {
                    id: tabIndicator
                    anchors.bottom: parent.bottom
                    height: Theme.dp(4)
                    color: Theme.primary
                    radius: 1
                    Behavior on x { NumberAnimation { duration: Theme.durationSlow; easing.type: Easing.InOutCubic } }
                    Behavior on width { NumberAnimation { duration: Theme.durationSlow; easing.type: Easing.InOutCubic } }
                }

                Connections {
                    target: cfgView
                    function onCurrentTabChanged() { tabBar.syncIndicator() }
                }

                Component.onCompleted: Qt.callLater(tabBar.syncIndicator)
            }
        }

        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

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
