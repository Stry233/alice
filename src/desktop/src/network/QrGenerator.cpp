#include "network/QrGenerator.h"
#include <QJsonObject>
#include <QJsonDocument>
#include <qrencode.h>

namespace alice {

QrGenerator::QrGenerator(QObject *parent) : QObject(parent) {}

QImage QrGenerator::generate(const QString &payload, int moduleSize) const {
    QByteArray data = payload.toUtf8();
    QRcode *qr = QRcode_encodeString(data.constData(), 0, QR_ECLEVEL_M, QR_MODE_8, 1);
    if (!qr) return {};

    int size = qr->width;
    int imgSize = size * moduleSize;
    QImage image(imgSize, imgSize, QImage::Format_RGB888);
    image.fill(Qt::white);

    for (int y = 0; y < size; ++y) {
        for (int x = 0; x < size; ++x) {
            if (qr->data[y * size + x] & 1) {
                // Fill a moduleSize × moduleSize block
                for (int dy = 0; dy < moduleSize; ++dy) {
                    for (int dx = 0; dx < moduleSize; ++dx) {
                        image.setPixel(x * moduleSize + dx, y * moduleSize + dy,
                                       qRgb(0, 0, 0));
                    }
                }
            }
        }
    }

    QRcode_free(qr);
    return image;
}

QImage QrGenerator::generateForServer(const QString &ip, int port,
                                       const QString &token, int moduleSize) const {
    QJsonObject obj;
    obj["ip"] = ip;
    obj["port"] = port;
    obj["token"] = token;
    QString payload = QJsonDocument(obj).toJson(QJsonDocument::Compact);
    return generate(payload, moduleSize);
}

} // namespace alice
