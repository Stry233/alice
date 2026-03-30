pragma Singleton
import QtQuick

QtObject {
    // Backgrounds
    readonly property color bg:            "#1B2025"
    readonly property color surface:       "#252C33"
    readonly property color surfaceHover:  "#2E363E"
    readonly property color surfaceActive: "#363F49"
    readonly property color elevated:      "#2A3139"
    readonly property color well:          "#181D22"

    // Borders
    readonly property color border:        "#394049"
    readonly property color borderStrong:  "#4A5361"

    // Text
    readonly property color textPrimary:   "#E1E8ED"
    readonly property color textSecondary: "#8A9BA8"
    readonly property color textDisabled:  "#5C6B7A"
    readonly property color textPlaceholder:"#6B7785"
    readonly property color textInverse:   "#1B2025"

    // Intent
    readonly property color primary:       "#2B95D6"
    readonly property color primaryHover:  "#48AFF0"
    readonly property color primaryMuted:  "#1A3A52"
    readonly property color success:       "#15B371"
    readonly property color successMuted:  "#0E3B2C"
    readonly property color warning:       "#D9822B"
    readonly property color warningMuted:  "#3D2B10"
    readonly property color danger:        "#DB3737"
    readonly property color dangerMuted:   "#3D1515"
    readonly property color dangerText:    "#FF7373"

    // Data visualization
    readonly property color dataBlue:      "#48AFF0"
    readonly property color dataGreen:     "#3DCC91"
    readonly property color dataOrange:    "#FFB366"
    readonly property color dataRed:       "#FF7373"

    // Spacing
    readonly property int spaceXs:  4
    readonly property int spaceSm:  8
    readonly property int spaceMd:  12
    readonly property int spaceLg:  16
    readonly property int spaceXl:  24

    // Border Radius
    readonly property int radiusNone: 0
    readonly property int radiusSm:   2
    readonly property int radiusMd:   3
    readonly property int radiusLg:   4

    // Typography
    readonly property string fontFamily:     "Inter"
    readonly property string fontFamilyMono: "RobotoMono"
    readonly property int fontSizeH1:      20
    readonly property int fontSizeH2:      16
    readonly property int fontSizeH3:      14
    readonly property int fontSizeBody:    13
    readonly property int fontSizeSmall:   12
    readonly property int fontSizeCaption: 11
    readonly property int fontSizeMicro:   10
    readonly property real sectionLetterSpacing: 1.2

    // Animation
    readonly property int durationFast:    100
    readonly property int durationNormal:  150
    readonly property int durationSlow:    200
    readonly property int easingEnter:  Easing.OutCubic
    readonly property int easingExit:   Easing.InCubic

    // Control sizes
    readonly property int toolbarHeight:  40
    readonly property int controlHeight:  28
    readonly property int inputHeight:    30

    // Popover
    readonly property int popoverWidth: 200
    readonly property int syncPopoverWidth: 180

    // Sidebar widths
    readonly property int sidebarNarrow: 200
    readonly property int sidebarStandard: 260
    readonly property int sidebarWide: 240
    readonly property int telemetryColumnWidth: 260

    // Breakpoints (compact: < 1280, standard: 1280-1599, wide: 1600+)
    readonly property int breakpointCompact: 1024
    readonly property int breakpointStandard: 1280
    readonly property int breakpointWide: 1600

    // Camera zoom
    readonly property real zoomMin: 1.0
    readonly property real zoomMax: 4.0
    readonly property real zoomStep: 0.25

    // Section header (font size)
    readonly property int sectionFontSize: 9
}
