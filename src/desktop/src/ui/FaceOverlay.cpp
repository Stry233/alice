#include "ui/FaceOverlay.h"
#include <QPainter>
#include <QPen>
#include <QVariantMap>

namespace alice {

FaceOverlay::FaceOverlay(QQuickItem *parent)
    : QQuickPaintedItem(parent) {
    setRenderTarget(QQuickPaintedItem::FramebufferObject);
}

void FaceOverlay::setFaces(const QVariantList &faces) {
    faces_ = faces;
    emit facesChanged();
    update();
}

void FaceOverlay::paint(QPainter *painter) {
    painter->setRenderHint(QPainter::Antialiasing);

    for (const auto &v : faces_) {
        QVariantMap face = v.toMap();
        QColor color = face.value("color", QColor(0, 200, 255)).value<QColor>();

        // Bounding box (normalized 0–1 coords)
        float left   = face.value("left", 0.0).toFloat() * static_cast<float>(width());
        float top    = face.value("top", 0.0).toFloat() * static_cast<float>(height());
        float right  = face.value("right", 0.0).toFloat() * static_cast<float>(width());
        float bottom = face.value("bottom", 0.0).toFloat() * static_cast<float>(height());

        QPen pen(color, 2);
        painter->setPen(pen);
        painter->setBrush(Qt::NoBrush);
        painter->drawRect(QRectF(left, top, right - left, bottom - top));

        // Eye positions
        if (face.contains("leftEyeX")) {
            float lex = face["leftEyeX"].toFloat() * static_cast<float>(width());
            float ley = face["leftEyeY"].toFloat() * static_cast<float>(height());
            painter->setBrush(color);
            painter->drawEllipse(QPointF(lex, ley), 3, 3);
        }
        if (face.contains("rightEyeX")) {
            float rex = face["rightEyeX"].toFloat() * static_cast<float>(width());
            float rey = face["rightEyeY"].toFloat() * static_cast<float>(height());
            painter->setBrush(color);
            painter->drawEllipse(QPointF(rex, rey), 3, 3);
        }

        // Tracking ID label
        int trackingId = face.value("trackingId", 0).toInt();
        painter->setPen(color);
        painter->drawText(QPointF(left + 4, top - 4),
                          QString("ID:%1").arg(trackingId));
    }

    // Focus crosshair
    if (showCrosshair_) {
        QPen crossPen(QColor(255, 255, 255, 180), 1, Qt::DashLine);
        painter->setPen(crossPen);
        float cx = static_cast<float>(width()) / 2;
        float cy = static_cast<float>(height()) / 2;
        painter->drawLine(QPointF(cx - 20, cy), QPointF(cx + 20, cy));
        painter->drawLine(QPointF(cx, cy - 20), QPointF(cx, cy + 20));
    }
}

} // namespace alice
