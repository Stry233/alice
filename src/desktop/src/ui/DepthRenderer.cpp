#include "ui/DepthRenderer.h"
#include <QPainter>
#include <QFont>

namespace alice {

DepthRenderer::DepthRenderer(QQuickItem *parent)
    : QQuickPaintedItem(parent) {
    setRenderTarget(QQuickPaintedItem::FramebufferObject);
}

void DepthRenderer::setSource(const QImage &image) {
    image_ = image;
    emit sourceChanged();
    update();
}

void DepthRenderer::paint(QPainter *painter) {
    if (image_.isNull()) return;

    // Draw colorized depth
    QRectF target(0, 0, width(), height());
    painter->drawImage(target, image_);

    // Draw depth readout overlay
    if (depth_ > 0.0f) {
        QFont font("RobotoMono", 12);
        painter->setFont(font);

        QString text = QString("%1m (%2%)")
                           .arg(depth_, 0, 'f', 2)
                           .arg(static_cast<int>(confidence_ * 100));

        // Background
        QFontMetrics fm(font);
        QRect textRect = fm.boundingRect(text);
        textRect.moveBottomRight(QPoint(static_cast<int>(width()) - 8,
                                        static_cast<int>(height()) - 8));
        textRect.adjust(-6, -4, 6, 4);

        painter->setPen(Qt::NoPen);
        painter->setBrush(QColor(0, 0, 0, 160));
        painter->drawRoundedRect(textRect, 4, 4);

        painter->setPen(confidence_ > 0.7f ? QColor(100, 255, 100) : QColor(255, 200, 50));
        painter->drawText(textRect, Qt::AlignCenter, text);
    }
}

} // namespace alice
