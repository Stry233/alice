pragma Singleton
import QtQuick

QtObject {
    // ── DPI-adaptive scale ─────────────────────────────────────────
    // Set by Main.qml. Base values are HTML demo at 200% zoom.
    // dp() scales further for screens larger than 1080p.
    property real scaleFactor: 1.0
    function dp(base) { return Math.round(base * scaleFactor) }

    // ── Colors ─────────────────────────────────────────────────────
    readonly property color bg:            "#1B2025"
    readonly property color surface:       "#252C33"
    readonly property color surfaceHover:  "#2E363E"
    readonly property color surfaceActive: "#363F49"
    readonly property color elevated:      "#2A3139"
    readonly property color well:          "#181D22"

    readonly property color border:        "#394049"
    readonly property color borderStrong:  "#4A5361"

    readonly property color textPrimary:   "#E1E8ED"
    readonly property color textSecondary: "#8A9BA8"
    readonly property color textDisabled:  "#5C6B7A"
    readonly property color textPlaceholder:"#6B7785"
    readonly property color textInverse:   "#1B2025"

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

    readonly property color dataBlue:      "#48AFF0"
    readonly property color dataGreen:     "#3DCC91"
    readonly property color dataOrange:    "#FFB366"
    readonly property color dataRed:       "#FF7373"

    // ── Spacing (2x HTML base) ─────────────────────────────────────
    readonly property int spaceXs:  dp(8)
    readonly property int spaceSm:  dp(16)
    readonly property int spaceMd:  dp(24)
    readonly property int spaceLg:  dp(32)
    readonly property int spaceXl:  dp(48)

    // ── Border Radius (2x HTML) ────────────────────────────────────
    readonly property int radiusNone: 0
    readonly property int radiusSm:   dp(3)
    readonly property int radiusMd:   dp(4)
    readonly property int radiusLg:   dp(6)

    // ── Typography (2x HTML CSS values) ────────────────────────────
    readonly property string fontFamily:     "Inter"
    readonly property string fontFamilyMono: "RobotoMono"
    readonly property int fontSizeH1:      dp(36)
    readonly property int fontSizeH2:      dp(28)
    readonly property int fontSizeH3:      dp(24)
    readonly property int fontSizeBody:    dp(22)
    readonly property int fontSizeSmall:   dp(20)
    readonly property int fontSizeCaption: dp(18)
    readonly property int fontSizeMicro:   dp(16)
    readonly property real sectionLetterSpacing: dp(2.4)

    // ── Animation ──────────────────────────────────────────────────
    // Strict token mapping per Interaction & Motion spec:
    //   Fast (100ms)  — hover color swaps, button fills, text colour lerps
    //   Normal (150ms)— opacity fades, popovers, reticle translation
    //   Slow (200ms)  — spatial movements (tab indicators, origin slides)
    readonly property int durationFast:    100
    readonly property int durationNormal:  150
    readonly property int durationSlow:    200
    readonly property int easingEnter:  Easing.OutCubic
    readonly property int easingExit:   Easing.InCubic
    readonly property int easingMotion: Easing.InOutCubic

    // Vertical offset applied to popovers when they slide in from their
    // triggering badge (DRD §1.1 "Rule of Tethering"). Kept in one place
    // so all contextual overlays share the same tether distance.
    readonly property int popoverSlideOffset: 6

    // ── Control sizes (2x HTML) ────────────────────────────────────
    readonly property int toolbarHeight:  dp(64)
    readonly property int controlHeight:  dp(32)
    readonly property int inputHeight:    dp(36)

    // ── Popover ────────────────────────────────────────────────────
    readonly property int popoverWidth: dp(400)
    readonly property int syncPopoverWidth: dp(360)

    // ── Sidebar widths (2x HTML) ───────────────────────────────────
    readonly property int sidebarNarrow: dp(320)
    readonly property int sidebarStandard: dp(520)
    readonly property int sidebarWide: dp(480)
    readonly property int telemetryColumnWidth: dp(440)

    // ── Breakpoints (logical, not scaled) ──────────────────────────
    readonly property int breakpointCompact: 1024
    readonly property int breakpointStandard: 1280
    readonly property int breakpointWide: 1600

    // ── Camera zoom ────────────────────────────────────────────────
    readonly property real zoomMin: 1.0
    readonly property real zoomMax: 4.0
    readonly property real zoomStep: 0.25

    // ── Section header ─────────────────────────────────────────────
    readonly property int sectionFontSize: dp(18)
}
