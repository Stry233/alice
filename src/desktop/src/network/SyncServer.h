#pragma once

#include <QObject>
#include <QWebSocketServer>
#include <QWebSocket>
#include <QTimer>
#include <QList>
#include <memory>

#include "network/SyncProtocol.h"

namespace alice {

/**
 * WebSocket server for LAN state synchronization.
 * The PC host runs this server; the Android app connects as a client.
 */
class SyncServer : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool running READ isRunning NOTIFY runningChanged)
    Q_PROPERTY(bool hasClient READ hasClient NOTIFY clientChanged)
    Q_PROPERTY(QString localAddress READ localAddress NOTIFY runningChanged)
    Q_PROPERTY(int port READ port NOTIFY runningChanged)
    Q_PROPERTY(QString sessionToken READ sessionToken CONSTANT)

public:
    explicit SyncServer(QObject *parent = nullptr);
    ~SyncServer() override;

    bool isRunning() const { return server_ && server_->isListening(); }
    bool hasClient() const { return authenticatedClient_ != nullptr; }
    QString localAddress() const;
    int port() const;
    QString sessionToken() const { return sessionToken_; }

    /** Get the QR payload JSON: {"ip":"...","port":...,"token":"..."} */
    QString qrPayload() const;

public slots:
    void start(int port = SyncProtocolConstants::kDefaultPort);
    void stop();

    /** Broadcast a sync message to the connected client. */
    void broadcast(const SyncMessage &message);

    /** Send a binary message to the connected client. */
    void sendBinaryMessage(const QByteArray &data);

    /** Send a state update snapshot. */
    void sendStateUpdate(int motorPosition, float depth, float confidence,
                         const QString &focusMode, bool enabled,
                         bool activelyFocusing, int facesDetected,
                         bool motorConnected, bool realSenseConnected);

signals:
    void runningChanged();
    void clientChanged();
    void messageReceived(const SyncMessage &message);
    void clientConnected();
    void clientDisconnected();
    void error(const QString &message);

private slots:
    void onNewConnection();
    void onTextMessage(const QString &message);
    void onClientDisconnected();
    void sendHeartbeat();

private:
    void generateSessionToken();

    std::unique_ptr<QWebSocketServer> server_;
    QWebSocket *authenticatedClient_ = nullptr;
    QList<QWebSocket *> pendingClients_;
    QTimer heartbeatTimer_;
    QTimer stateUpdateTimer_;
    QString sessionToken_;
};

} // namespace alice
