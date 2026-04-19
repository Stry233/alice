#include "network/SyncClient.h"
#include <QJsonObject>
#include <cmath>

namespace alice {

SyncClient::SyncClient(QObject *parent) : QObject(parent) {
    reconnectTimer_.setSingleShot(true);
    connect(&reconnectTimer_, &QTimer::timeout, this, &SyncClient::attemptReconnect);
}

SyncClient::~SyncClient() {
    disconnect();
}

void SyncClient::connectToServer(const QString &ip, int port, const QString &token) {
    disconnect();

    token_ = token;
    serverUrl_ = QUrl(QString("ws://%1:%2").arg(ip).arg(port));
    reconnectAttempts_ = 0;

    socket_ = std::make_unique<QWebSocket>();
    connect(socket_.get(), &QWebSocket::connected,
            this, &SyncClient::onConnected);
    connect(socket_.get(), &QWebSocket::textMessageReceived,
            this, &SyncClient::onTextMessage);
    connect(socket_.get(), &QWebSocket::disconnected,
            this, &SyncClient::onDisconnected);
    connect(socket_.get(), &QWebSocket::errorOccurred,
            this, &SyncClient::onError);

    socket_->open(serverUrl_);
}

void SyncClient::disconnect() {
    reconnectTimer_.stop();
    authenticated_ = false;
    if (socket_) {
        socket_->close();
        socket_.reset();
    }
    emit connectionChanged(false);
}

void SyncClient::send(const SyncMessage &message) {
    if (!socket_ || !authenticated_) return;
    socket_->sendTextMessage(QString::fromUtf8(message.serialize()));
}

void SyncClient::onConnected() {
    // Send authentication
    auto authMsg = SyncMessage::authenticate(token_);
    authMsg.sender = "desktop";
    socket_->sendTextMessage(QString::fromUtf8(authMsg.serialize()));
    authenticated_ = true;
    reconnectAttempts_ = 0;
    emit connectionChanged(true);
}

void SyncClient::onTextMessage(const QString &message) {
    auto msg = SyncMessage::deserialize(message.toUtf8());
    if (msg) {
        emit messageReceived(*msg);
    }
}

void SyncClient::onDisconnected() {
    bool wasAuthenticated = authenticated_;
    authenticated_ = false;
    if (wasAuthenticated) {
        emit connectionChanged(false);
        attemptReconnect();
    }
}

void SyncClient::onError(QAbstractSocket::SocketError) {
    emit error(socket_ ? socket_->errorString() : "Unknown error");
    if (!authenticated_ && reconnectAttempts_ < kMaxReconnectAttempts) {
        attemptReconnect();
    }
}

void SyncClient::attemptReconnect() {
    if (reconnectAttempts_ >= kMaxReconnectAttempts) {
        emit error("Max reconnect attempts reached");
        return;
    }

    // Exponential backoff
    int delay = kBaseReconnectDelayMs * static_cast<int>(std::pow(2, reconnectAttempts_));
    reconnectAttempts_++;
    reconnectTimer_.start(delay);

    if (socket_) {
        socket_->open(serverUrl_);
    }
}

} // namespace alice
