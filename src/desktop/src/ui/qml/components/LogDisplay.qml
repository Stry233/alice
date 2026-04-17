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
                wrapMode: TextArea.Wrap
                font.family: Theme.fontFamilyMono
                font.pixelSize: Theme.fontSizeMicro
                color: Theme.textSecondary
                selectionColor: Theme.primary
                selectedTextColor: "#ffffff"
                background: null
                leftPadding: 0; rightPadding: 0; topPadding: 0; bottomPadding: 0

                // Build rich text from messages. The ASCII banner that
                // AppController seeds at startup has no timestamp prefix,
                // so we detect those contiguous lines at the top and render
                // them in a separate <pre> with a tighter line-height — the
                // default 1.4 leaves big gaps in block art. Everything after
                // the first timestamped entry gets the regular log spacing.
                textFormat: TextArea.RichText
                text: {
                    if (!messages || messages.length === 0) return ""

                    function escapeHtml(s) {
                        return s.replace(/&/g, "&amp;")
                                .replace(/</g, "&lt;")
                                .replace(/>/g, "&gt;")
                                .replace(/ /g, "&nbsp;")
                    }

                    function formatLogLine(msg) {
                        var tsMatch = msg.match(/^\[(\d{2}:\d{2}:\d{2}\.\d{3})\]\s*/)
                        var ts = ""
                        var rest = msg
                        if (tsMatch) {
                            ts = tsMatch[1]
                            rest = msg.substring(tsMatch[0].length)
                        }

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

                        var catMatch = rest.match(/^\[([A-Z]+)\]\s*/)
                        var cat = ""
                        if (catMatch) {
                            cat = catMatch[1]
                            rest = rest.substring(catMatch[0].length)
                        }

                        var html = ""
                        if (ts) html += "<span style='color:" + Theme.textDisabled + ";'>" + ts + "</span> "
                        if (tag) html += "<span style='color:" + msgColor + ";font-weight:600;'>[" + tag + "]</span> "
                        if (cat) html += "<span style='color:" + Theme.primary + ";'>[" + cat + "]</span> "
                        html += "<span style='color:" + msgColor + ";'>" + rest + "</span>"
                        return html
                    }

                    var bannerLines = []
                    var logLines = []
                    var inBanner = true
                    for (var i = 0; i < messages.length; i++) {
                        var msg = messages[i]
                        var looksLikeLog = msg.match(/^\[\d{2}:\d{2}:\d{2}\.\d{3}\]/)
                        if (looksLikeLog) inBanner = false

                        if (inBanner) {
                            var colored = "<span style='color:" + Theme.primary + ";'>"
                                        + escapeHtml(msg) + "</span>"
                            bannerLines.push(colored)
                        } else {
                            logLines.push(formatLogLine(msg))
                        }
                    }

                    var out = ""
                    if (bannerLines.length > 0) {
                        out += "<pre style='margin:0;line-height:0.95;'>"
                            + bannerLines.join("<br>") + "</pre>"
                    }
                    if (logLines.length > 0) {
                        out += "<pre style='margin:0;line-height:1.4;'>"
                            + logLines.join("<br>") + "</pre>"
                    }
                    return out
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
