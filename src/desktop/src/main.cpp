#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQmlComponent>
#include <QQuickStyle>
#include <QFont>
#include <QFontDatabase>
#include <QIcon>
#include <QTimer>
#include <QSplashScreen>
#include <QThreadPool>
#include <QPixmap>
#include <QPainter>
#include <QSvgRenderer>

#include "ui/AppController.h"
#include "core/system/SystemMonitor.h"
#include "ui/VideoRenderer.h"
#include "ui/DepthRenderer.h"
#include "ui/HistogramRenderer.h"
#include "ui/SplashWindow.h"

#include <cstdio>

using namespace Qt::StringLiterals;

int main(int argc, char *argv[])
{
    QApplication::setHighDpiScaleFactorRoundingPolicy(
        Qt::HighDpiScaleFactorRoundingPolicy::PassThrough);

    QApplication app(argc, argv);
    app.setOrganizationName("SelkaCraft");
    app.setOrganizationDomain("selkacraft.com");
    app.setApplicationName(ALICE_APP_NAME);
    app.setApplicationVersion(ALICE_APP_VERSION);

    // App icon set after splash painting below

    fprintf(stderr, "[Alice] Starting...\n");

    // Load fonts first (lightweight)
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-Regular.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-Medium.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-SemiBold.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-Bold.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/RobotoMono-Regular.ttf");

    // Reference the bundled family by its renamed identity ("Alice Inter",
    // set by release/rename_bundled_fonts.py on the .ttf name tables) so
    // it can't collide with a system-installed "Inter". Qt's resolver
    // would otherwise pick either font based on registration order and
    // yield subtly different rendering across machines.
    QFont defaultFont("Alice Inter");
    defaultFont.setPixelSize(16);
    app.setFont(defaultFont);

    // Splash screen — scale with screen DPI
    auto *primaryScreen = app.primaryScreen();
    qreal dpr = primaryScreen ? primaryScreen->devicePixelRatio() : 1.0;
    int sw = qRound(520 * dpr), sh = qRound(320 * dpr);
    int r = qRound(4 * dpr);  // consistent radius matching Theme.radiusSm

    // Font sizes scaled
    int titleSize = qRound(11 * dpr);
    int subtitleSize = qRound(8 * dpr);
    int metaSize = qRound(7 * dpr);
    int logoSize = qRound(48 * dpr);
    int logoW = qRound(44 * dpr), logoH = qRound(50 * dpr);
    int margin = qRound(20 * dpr);
    int barH = qRound(3 * dpr);
    int barW = qRound(200 * dpr);
    int footerH = qRound(64 * dpr);
    int accentH = qRound(3 * dpr);

    QPixmap splashPix(sw, sh);
    splashPix.setDevicePixelRatio(dpr);
    splashPix.fill(QColor("#2A3139"));
    {
        QPainter p(&splashPix);
        p.setRenderHint(QPainter::Antialiasing);
        // Use logical coordinates (dpr handled by pixmap)
        int w = 520, h = 320;

        // Top accent bar (primary blue)
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#2B95D6"));
        p.drawRect(0, 0, w, 3);

        // Footer (darker bg section)
        p.setBrush(QColor("#1B2025"));
        p.drawRect(0, h - 64, w, 64);
        p.setPen(QPen(QColor("#394049"), 1));
        p.drawLine(0, h - 64, w, h - 64);

        // Content block vertically centered in the area above footer
        // Available: 3 (accent) to h-64 (footer) = 253px
        // Block: logo(50) + gap(14) + title(28) + gap(8) + subtitle(16) = 116px
        int contentTop = 3 + (253 - 116) / 2;

        // Logo
        QSvgRenderer logoSvg(QString(":/qt/qml/Alice/UI/assets/icons/alice_logo.svg"));
        if (logoSvg.isValid()) {
            QRectF logoRect((w - 44) / 2.0, contentTop, 44, 50);
            logoSvg.render(&p, logoRect);
        }

        // Title
        QFont titleFont("Alice Inter", 11, QFont::Bold);
        titleFont.setLetterSpacing(QFont::AbsoluteSpacing, 4);
        titleFont.setCapitalization(QFont::AllUppercase);
        p.setFont(titleFont);
        p.setPen(QColor("#E1E8ED"));
        p.drawText(QRect(0, contentTop + 64, w, 28), Qt::AlignHCenter, ALICE_APP_NAME);

        // Subtitle
        p.setFont(QFont("Alice Inter", 8));
        p.setPen(QColor("#8A9BA8"));
        p.drawText(QRect(0, contentTop + 100, w, 16), Qt::AlignHCenter,
                   "Autofocus Lens Interface for Cinema Equipment");

        // Loading bar — in footer section
        int barY = h - 40;
        int barX = (w - 200) / 2;
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#394049"));
        p.drawRoundedRect(barX, barY, 200, 3, 1, 1);
        p.setBrush(QColor("#2B95D6"));
        p.drawRoundedRect(barX, barY, 70, 3, 1, 1);

        // Version — bottom right in footer
        p.setFont(QFont("Alice Inter", 7));
        p.setPen(QColor("#5C6B7A"));
        p.drawText(QRect(w - 60, h - 22, 44, 12), Qt::AlignRight, "v" ALICE_APP_VERSION);

        // Copyright — bottom left in footer
        p.drawText(QRect(16, h - 22, 200, 12), Qt::AlignLeft, "\u00A9 2026 SelkaCraft");
    }

    // App icon — render SVG crisp at 256x256
    QSvgRenderer iconSvg(QString(":/qt/qml/Alice/UI/assets/icons/alice_app_icon.svg"));
    if (iconSvg.isValid()) {
        QPixmap iconPix(256, 256);
        iconPix.fill(Qt::transparent);
        QPainter ip(&iconPix);
        iconSvg.render(&ip);
        ip.end();
        app.setWindowIcon(QIcon(iconPix));
    }

    QSplashScreen splash(splashPix);
    splash.show();
    splash.showMessage("Initializing...", Qt::AlignBottom | Qt::AlignHCenter, QColor("#5C6B7A"));
    app.processEvents();

    // Heavy initialization — keep pumping events so splash stays responsive
    splash.showMessage("Loading UI framework...", Qt::AlignBottom | Qt::AlignHCenter, QColor("#5C6B7A"));
    app.processEvents();

    qputenv("QT_QUICK_CONTROLS_MATERIAL_VARIANT", "Dense");
    QQuickStyle::setStyle("Material");
    app.processEvents();

    qmlRegisterType<alice::VideoRenderer>("Alice.Renderers", 1, 0, "VideoRenderer");
    qmlRegisterType<alice::DepthRenderer>("Alice.Renderers", 1, 0, "DepthRenderer");
    qmlRegisterType<alice::HistogramRenderer>("Alice.Renderers", 1, 0, "HistogramRenderer");

    splash.showMessage("Preparing engine...", Qt::AlignBottom | Qt::AlignHCenter, QColor("#5C6B7A"));
    app.processEvents();

    QQmlApplicationEngine engine;
    engine.addImportPath(u"qrc:/qt/qml"_s);
    engine.setOutputWarningsToStandardError(true);

    alice::AppController controller;
    engine.rootContext()->setContextProperty("alice", &controller);

    alice::SystemMonitor sysMonitor;
    engine.rootContext()->setContextProperty("sysMonitor", &sysMonitor);

    splash.showMessage("Loading interface...", Qt::AlignBottom | Qt::AlignHCenter, QColor("#5C6B7A"));
    app.processEvents();

    QQmlComponent component(&engine);
    const QUrl url(u"qrc:/qt/qml/Alice/UI/src/ui/qml/Main.qml"_s);
    fprintf(stderr, "[Alice] Loading: %s\n", url.toString().toUtf8().constData());
    component.loadUrl(url);

    if (component.isError()) {
        fprintf(stderr, "[Alice] QML ERRORS:\n");
        for (const auto &error : component.errors()) {
            fprintf(stderr, "  %s\n", error.toString().toUtf8().constData());
        }
        return -1;
    }

    if (!component.isReady()) {
        fprintf(stderr, "[Alice] Component not ready. Status: %d\n", component.status());
        return -1;
    }

    fprintf(stderr, "[Alice] Component ready, creating object...\n");
    QObject *rootObj = component.create();
    if (!rootObj) {
        fprintf(stderr, "[Alice] Failed to create root object!\n");
        return -1;
    }

    fprintf(stderr, "[Alice] Window created successfully.\n");

    // Close splash when main window is shown
    auto *mainWindow = qobject_cast<QWindow *>(rootObj);
    if (mainWindow)
        splash.finish(nullptr);  // Close immediately — main window is ready
    else
        splash.close();

    // initialize() is called from Main.qml Component.onCompleted

    // On quit: destroy QML tree FIRST (while controller is still valid),
    // then wait for in-flight threads, then stop devices.
    QObject::connect(&app, &QApplication::aboutToQuit, &controller, [&]() {
        // Destroy QML tree before stopping devices
        delete rootObj;
        rootObj = nullptr;

        // Wait for any in-flight QtConcurrent tasks (capture frame encoding)
        QThreadPool::globalInstance()->waitForDone(2000);

        fprintf(stderr, "[Alice] Shutting down...\n");
        controller.stopSyncServer();
    });

    int result = app.exec();

    return result;
}
