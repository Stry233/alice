#pragma once

#include <QObject>
#include <QWebSocket>
#include <QTimer>
#include <QUrl>
#include <memory>

#include "network/SyncProtocol.h"

namespace alice {

/**
 * WebSocket client for connecting to a remote Alice sync server.
 * Used when this desktop instance acts as a remote, or for testing.
 */
class SyncClient : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool connected READ isConnected NOTIFY connectionChanged)

public:
    explicit SyncClient(QObject *parent = nullptr);
    ~SyncClient() override;

    bool isConnected() const { return authenticated_; }

public slots:
    /**
     * Connect to a sync server.
     * @param ip   Server IP address
     * @param port Server port
     * @param token Session token from QR code
     */
    void connectToServer(const QString &ip, int port, const QString &token);

    /** Disconnect from the server. */
    void disconnect();

    /** Send a message to the server. */
    void send(const SyncMessage &message);

signals:
    void connectionChanged(bool connected);
    void messageReceived(const SyncMessage &message);
    void error(const QString &message);

private slots:
    void onConnected();
    void onTextMessage(const QString &message);
    void onDisconnected();
    void onError(QAbstractSocket::SocketError error);
    void attemptReconnect();

private:
    std::unique_ptr<QWebSocket> socket_;
    QTimer reconnectTimer_;
    QString token_;
    QUrl serverUrl_;
    bool authenticated_ = false;
    int reconnectAttempts_ = 0;
    static constexpr int kMaxReconnectAttempts = 5;
    static constexpr int kBaseReconnectDelayMs = 1000;
};

} // namespace alice
