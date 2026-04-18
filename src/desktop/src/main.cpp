#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQmlComponent>
#include <QQuickStyle>
#include <QFont>
#include <QFontDatabase>
#include <QIcon>
#include <QTimer>
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

    fprintf(stderr, "[Alice] Starting...\n");

    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-Regular.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-Medium.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-SemiBold.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/Inter-Bold.ttf");
    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/RobotoMono-Regular.ttf");

    // Bundled identity — prevents collision with a system-installed "Inter".
    QFont defaultFont("Alice Inter");
    defaultFont.setPixelSize(16);
    app.setFont(defaultFont);

    QSvgRenderer iconSvg(QString(":/qt/qml/Alice/UI/assets/icons/alice_app_icon.svg"));
    if (iconSvg.isValid()) {
        QPixmap iconPix(256, 256);
        iconPix.fill(Qt::transparent);
        QPainter ip(&iconPix);
        iconSvg.render(&ip);
        ip.end();
        app.setWindowIcon(QIcon(iconPix));
    }

    auto *splash = new alice::SplashWindow();
    splash->show();

    qputenv("QT_QUICK_CONTROLS_MATERIAL_VARIANT", "Dense");
    QQuickStyle::setStyle("Material");

    qmlRegisterType<alice::VideoRenderer>("Alice.Renderers", 1, 0, "VideoRenderer");
    qmlRegisterType<alice::DepthRenderer>("Alice.Renderers", 1, 0, "DepthRenderer");
    qmlRegisterType<alice::HistogramRenderer>("Alice.Renderers", 1, 0, "HistogramRenderer");

    auto *engine = new QQmlApplicationEngine();
    engine->addImportPath(u"qrc:/qt/qml"_s);
    engine->setOutputWarningsToStandardError(true);

    auto *controller = new alice::AppController();
    engine->rootContext()->setContextProperty("alice", controller);

    auto *sysMonitor = new alice::SystemMonitor();
    engine->rootContext()->setContextProperty("sysMonitor", sysMonitor);

    // Async QML load — compilation runs on a background thread so the
    // splash animation keeps ticking and the compositor never sees an
    // unresponsive window.
    auto *component = new QQmlComponent(engine);
    const QUrl url(u"qrc:/qt/qml/Alice/UI/src/ui/qml/Main.qml"_s);
    fprintf(stderr, "[Alice] Loading: %s\n", url.toString().toUtf8().constData());

    // Steps run through singleShot so the event loop can repaint the
    // splash between chunks.
    QTimer::singleShot(0, [=]() {
        splash->setStatus("Loading UI framework...");
        QTimer::singleShot(0, [=]() {
            splash->setStatus("Loading interface...");
            component->loadUrl(url, QQmlComponent::Asynchronous);
        });
    });

    QObject::connect(component, &QQmlComponent::statusChanged, [=](QQmlComponent::Status status) {
        if (status == QQmlComponent::Error) {
            fprintf(stderr, "[Alice] QML ERRORS:\n");
            for (const auto &error : component->errors()) {
                fprintf(stderr, "  %s\n", error.toString().toUtf8().constData());
            }
            QCoreApplication::exit(-1);
            return;
        }
        if (status != QQmlComponent::Ready) return;

        QObject *rootObj = component->create();
        if (!rootObj) {
            fprintf(stderr, "[Alice] Failed to create root object!\n");
            QCoreApplication::exit(-1);
            return;
        }
        fprintf(stderr, "[Alice] Window created successfully.\n");

        splash->hide();
        splash->deleteLater();

        QObject::connect(qApp, &QCoreApplication::aboutToQuit, controller, [=]() {
            delete rootObj;
            QThreadPool::globalInstance()->waitForDone(2000);
            fprintf(stderr, "[Alice] Shutting down...\n");
            controller->stopSyncServer();
        });
    });

    return app.exec();
}
