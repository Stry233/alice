#pragma once

#include <QObject>
#include <QImage>
#include <QString>

namespace alice {

/**
 * QR code generator for the LAN sync handshake.
 * Encodes server IP, port, and session token into a QR code image.
 */
class QrGenerator : public QObject {
    Q_OBJECT

public:
    explicit QrGenerator(QObject *parent = nullptr);

    /**
     * Generate a QR code image from a JSON payload string.
     * @param payload JSON string (e.g. {"ip":"192.168.1.5","port":8765,"token":"abc123"})
     * @param moduleSize Pixel size per QR module (default 8)
     * @return QImage of the QR code, or null image on failure
     */
    Q_INVOKABLE QImage generate(const QString &payload, int moduleSize = 8) const;

    /**
     * Generate a QR code for the sync server connection info.
     * @param ip Local IP address
     * @param port Server port
     * @param token Session token
     * @param moduleSize Pixel size per QR module
     */
    Q_INVOKABLE QImage generateForServer(const QString &ip, int port,
                                          const QString &token, int moduleSize = 8) const;
};

} // namespace alice
