#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQmlComponent>
#include <QQuickStyle>
#include <QFont>
#include <QFontDatabase>
#include <QIcon>

#include "ui/AppController.h"
#include "ui/VideoRenderer.h"
#include "ui/DepthRenderer.h"
#include "ui/FaceOverlay.h"
#include "ui/HistogramRenderer.h"

#include <cstdio>

using namespace Qt::StringLiterals;

int main(int argc, char *argv[])
{
    QGuiApplication app(argc, argv);
    app.setOrganizationName("SelkaCraft");
    app.setOrganizationDomain("selkacraft.com");
    app.setApplicationName("Alice Studio");
    app.setApplicationVersion("0.2");

    app.setWindowIcon(QIcon(":/qt/qml/Alice/UI/assets/alice_icon.png"));

    fprintf(stderr, "[Alice] Starting...\n");

    QFontDatabase::addApplicationFont(":/qt/qml/Alice/UI/assets/fonts/RobotoMono-Regular.ttf");
    QQuickStyle::setStyle("Material");

    qmlRegisterType<alice::VideoRenderer>("Alice.Renderers", 1, 0, "VideoRenderer");
    qmlRegisterType<alice::DepthRenderer>("Alice.Renderers", 1, 0, "DepthRenderer");
    qmlRegisterType<alice::FaceOverlay>("Alice.Renderers", 1, 0, "FaceOverlay");
    qmlRegisterType<alice::HistogramRenderer>("Alice.Renderers", 1, 0, "HistogramRenderer");

    QQmlApplicationEngine engine;
    engine.addImportPath(u"qrc:/qt/qml"_s);
    engine.setOutputWarningsToStandardError(true);

    alice::AppController controller;
    engine.rootContext()->setContextProperty("alice", &controller);

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
    // initialize() is called from Main.qml Component.onCompleted

    // Stop sync server before event loop exits so close frame gets sent
    QObject::connect(&app, &QGuiApplication::aboutToQuit, &controller, [&controller]() {
        fprintf(stderr, "[Alice] Shutting down sync server...\n");
        controller.stopSyncServer();
    });

    int result = app.exec();

    // Destroy QML tree before controller to prevent "alice is null" errors
    delete rootObj;

    return result;
}
