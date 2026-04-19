import QtQuick
import Alice.UI
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts

Item {
    id: logDisplay
    property var messages: []

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: Theme.dp(20)
        spacing: Theme.dp(8)

        // Header
        RowLayout {
            Label {
                text: "LOG"
                font.family: Theme.fontFamily
                font.pixelSize: Theme.sectionFontSize
                font.weight: Font.DemiBold
                font.letterSpacing: Theme.sectionLetterSpacing
                color: Theme.textSecondary
            }
            Item { Layout.fillWidth: true }
            Label {
                text: messages.length + " entries"
                font.pixelSize: Theme.fontSizeMicro
                color: Theme.textDisabled
            }
        }

        // Scrollable, selectable log
        ScrollView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true

            TextArea {
                id: logArea
                readOnly: true
                selectByMouse: true
                wrapMode: TextArea.NoWrap
                font.family: Theme.fontFamilyMono
                font.pixelSize: Theme.fontSizeMicro
                color: Theme.textSecondary
                selectionColor: Theme.primary
                selectedTextColor: "#ffffff"
                background: null
                leftPadding: 0; rightPadding: 0; topPadding: 0; bottomPadding: 0

                // Build rich text from messages
                textFormat: TextArea.RichText
                text: {
                    if (!messages || messages.length === 0) return ""
                    var lines = []
                    for (var i = 0; i < messages.length; i++) {
                        var msg = messages[i]
                        var line = msg

                        // Extract timestamp if present (format: [HH:MM:SS.mmm])
                        var tsMatch = msg.match(/^\[(\d{2}:\d{2}:\d{2}\.\d{3})\]\s*/)
                        var ts = ""
                        var rest = msg
                        if (tsMatch) {
                            ts = tsMatch[1]
                            rest = msg.substring(tsMatch[0].length)
                        }

                        // Determine severity and color
                        var tagColor = Theme.textDisabled  // default for timestamp
                        var msgColor = Theme.textSecondary
                        var tag = ""

                        if (rest.indexOf("[ERROR]") !== -1) {
                            msgColor = Theme.dangerText
                            tag = "ERROR"
                            rest = rest.replace("[ERROR]", "").trim()
                        } else if (rest.indexOf("[WARNING]") !== -1 || rest.indexOf("[WARN]") !== -1) {
                            msgColor = Theme.warning
                            tag = "WARN"
                            rest = rest.replace(/\[WARN(ING)?\]/, "").trim()
                        } else if (rest.indexOf("[DEBUG]") !== -1) {
                            msgColor = Theme.textDisabled
                            tag = "DEBUG"
                            rest = rest.replace("[DEBUG]", "").trim()
                        } else if (rest.indexOf("[INFO]") !== -1) {
                            msgColor = Theme.textSecondary
                            tag = "INFO"
                            rest = rest.replace("[INFO]", "").trim()
                        }

                        // Extract category [MOTOR], [SYSTEM], etc.
                        var catMatch = rest.match(/^\[([A-Z]+)\]\s*/)
                        var cat = ""
                        if (catMatch) {
                            cat = catMatch[1]
                            rest = rest.substring(catMatch[0].length)
                        }

                        // Build formatted line
                        var html = ""
                        if (ts) html += "<span style='color:" + Theme.textDisabled + ";'>" + ts + "</span> "
                        if (tag) {
                            var tagBg = tag === "ERROR" ? Theme.dangerMuted : tag === "WARN" ? Theme.warningMuted : "transparent"
                            html += "<span style='color:" + msgColor + ";font-weight:600;'>[" + tag + "]</span> "
                        }
                        if (cat) html += "<span style='color:" + Theme.primary + ";'>[" + cat + "]</span> "
                        html += "<span style='color:" + msgColor + ";'>" + rest + "</span>"

                        lines.push(html)
                    }
                    return "<pre style='margin:0;line-height:1.4;'>" + lines.join("<br>") + "</pre>"
                }

                // Start at top to show ASCII art, then auto-scroll after delay
                property bool autoScrollEnabled: false
                Component.onCompleted: {
                    logArea.cursorPosition = 0
                    scrollDelay.start()
                }
                Timer {
                    id: scrollDelay
                    interval: 3000
                    onTriggered: logArea.autoScrollEnabled = true
                }
                onTextChanged: {
                    if (autoScrollEnabled) {
                        Qt.callLater(function() {
                            logArea.cursorPosition = logArea.length
                        })
                    }
                }
            }
        }
    }
}
