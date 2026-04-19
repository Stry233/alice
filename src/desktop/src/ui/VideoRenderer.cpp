#include "ui/VideoRenderer.h"
#include <QPainter>

namespace alice {

VideoRenderer::VideoRenderer(QQuickItem *parent)
    : QQuickPaintedItem(parent) {
    setRenderTarget(QQuickPaintedItem::FramebufferObject);
}

void VideoRenderer::setSource(const QImage &image) {
    // Skip if the image data pointer and size are identical (same frame)
    if (image.constBits() == image_.constBits() &&
        image.size() == image_.size() && !image.isNull()) {
        return;
    }
    image_ = image;
    emit sourceChanged();
    update();
}

void VideoRenderer::paint(QPainter *painter) {
    if (image_.isNull()) return;

    // Maintain aspect ratio
    float scaleX = static_cast<float>(width()) / image_.width();
    float scaleY = static_cast<float>(height()) / image_.height();
    float scale = std::min(scaleX, scaleY);

    float drawW = image_.width() * scale;
    float drawH = image_.height() * scale;
    float offsetX = (width() - drawW) / 2.0f;
    float offsetY = (height() - drawH) / 2.0f;

    QRectF target(offsetX, offsetY, drawW, drawH);
    QRectF source(0, 0, image_.width(), image_.height());
    painter->drawImage(target, image_, source);
}

} // namespace alice
