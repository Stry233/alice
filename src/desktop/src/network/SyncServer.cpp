#include "network/SyncServer.h"

#include <QNetworkInterface>
#include <QJsonDocument>
#include <QJsonObject>
#include <QRandomGenerator>
#include <QUuid>

namespace alice {

SyncServer::SyncServer(QObject *parent) : QObject(parent) {
    generateSessionToken();

    heartbeatTimer_.setInterval(SyncProtocolConstants::kHeartbeatIntervalMs);
    connect(&heartbeatTimer_, &QTimer::timeout, this, &SyncServer::sendHeartbeat);
}

SyncServer::~SyncServer() {
    stop();
}

QString SyncServer::localAddress() const {
    // Find first non-loopback IPv4 address
    for (const auto &iface : QNetworkInterface::allAddresses()) {
        if (!iface.isLoopback() && iface.protocol() == QAbstractSocket::IPv4Protocol) {
            return iface.toString();
        }
    }
    return "127.0.0.1";
}

int SyncServer::port() const {
    return server_ ? server_->serverPort() : 0;
}

QString SyncServer::qrPayload() const {
    QJsonObject obj;
    obj["ip"] = localAddress();
    obj["port"] = port();
    obj["token"] = sessionToken_;
    return QJsonDocument(obj).toJson(QJsonDocument::Compact);
}

void SyncServer::start(int listenPort) {
    if (server_ && server_->isListening()) return;

    server_ = std::make_unique<QWebSocketServer>(
        "Alice Sync", QWebSocketServer::NonSecureMode, this);

    if (!server_->listen(QHostAddress::Any, static_cast<quint16>(listenPort))) {
        emit error(QString("Failed to start server: %1").arg(server_->errorString()));
        return;
    }

    connect(server_.get(), &QWebSocketServer::newConnection,
            this, &SyncServer::onNewConnection);

    heartbeatTimer_.start();
    emit runningChanged();
}

void SyncServer::stop() {
    heartbeatTimer_.stop();

    // Close authenticated client with proper close frame
    if (authenticatedClient_) {
        authenticatedClient_->close(QWebSocketProtocol::CloseCodeNormal, "Server stopping");
        authenticatedClient_ = nullptr;
    }
    for (auto *client : pendingClients_) {
        client->close();
        client->deleteLater();
    }
    pendingClients_.clear();

    if (server_) {
        server_->close();
        server_.reset();
    }

    emit runningChanged();
    emit clientChanged();
}

void SyncServer::broadcast(const SyncMessage &message) {
    if (!authenticatedClient_) return;
    authenticatedClient_->sendTextMessage(QString::fromUtf8(message.serialize()));
}

void SyncServer::sendBinaryMessage(const QByteArray &data) {
    if (authenticatedClient_) {
        authenticatedClient_->sendBinaryMessage(data);
    }
}

void SyncServer::sendStateUpdate(int motorPosition, float depth, float confidence,
                                  const QString &focusMode, bool enabled,
                                  bool activelyFocusing, int facesDetected,
                                  bool motorConnected, bool realSenseConnected) {
    broadcast(SyncMessage::stateUpdate(
        motorPosition, depth, confidence, focusMode, enabled,
        activelyFocusing, facesDetected, motorConnected, realSenseConnected));
}

void SyncServer::onNewConnection() {
    while (server_->hasPendingConnections()) {
        auto *socket = server_->nextPendingConnection();
        fprintf(stderr, "[SyncServer] New connection from %s:%d\n",
                socket->peerAddress().toString().toUtf8().constData(),
                socket->peerPort());
        connect(socket, &QWebSocket::textMessageReceived,
                this, &SyncServer::onTextMessage);
        connect(socket, &QWebSocket::disconnected,
                this, &SyncServer::onClientDisconnected);
        pendingClients_.append(socket);
    }
}

void SyncServer::onTextMessage(const QString &message) {
    auto *socket = qobject_cast<QWebSocket *>(sender());
    if (!socket) return;

    fprintf(stderr, "[SyncServer] Received message: %s\n",
            message.left(200).toUtf8().constData());

    auto msg = SyncMessage::deserialize(message.toUtf8());
    if (!msg) {
        fprintf(stderr, "[SyncServer] Failed to deserialize message!\n");
        return;
    }
    fprintf(stderr, "[SyncServer] Parsed type: %d, sender: %s\n",
            static_cast<int>(msg->type), msg->sender.toUtf8().constData());

    // Handle authentication for pending clients
    if (pendingClients_.contains(socket)) {
        if (msg->type == SyncMessageType::Authenticate) {
            QString token = msg->payload["token"].toString();
            fprintf(stderr, "[SyncServer] Auth attempt: received='%s' expected='%s'\n",
                    token.toUtf8().constData(), sessionToken_.toUtf8().constData());
            if (token == sessionToken_) {
                pendingClients_.removeOne(socket);

                // Replace existing authenticated client
                if (authenticatedClient_) {
                    authenticatedClient_->close();
                    authenticatedClient_->deleteLater();
                }
                authenticatedClient_ = socket;
                emit clientConnected();
                emit clientChanged();
            } else {
                socket->close(QWebSocketProtocol::CloseCodePolicyViolated, "Invalid token");
                socket->deleteLater();
                pendingClients_.removeOne(socket);
            }
        }
        return;
    }

    // Forward authenticated messages
    if (socket == authenticatedClient_) {
        emit messageReceived(*msg);
    }
}

void SyncServer::onClientDisconnected() {
    auto *socket = qobject_cast<QWebSocket *>(sender());
    if (!socket) return;

    pendingClients_.removeOne(socket);
    if (socket == authenticatedClient_) {
        authenticatedClient_ = nullptr;
        emit clientDisconnected();
        emit clientChanged();
    }
    socket->deleteLater();
}

void SyncServer::sendHeartbeat() {
    if (!authenticatedClient_) return;
    static auto startTime = QDateTime::currentMSecsSinceEpoch();
    auto uptime = QDateTime::currentMSecsSinceEpoch() - startTime;
    broadcast(SyncMessage::heartbeat(uptime, "AliceDesktop/0.2"));
}

void SyncServer::generateSessionToken() {
    sessionToken_ = QUuid::createUuid().toString(QUuid::WithoutBraces).left(12);
}

} // namespace alice
