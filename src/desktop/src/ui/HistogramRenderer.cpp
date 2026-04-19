#include "ui/HistogramRenderer.h"
#include <QPainter>
#include <QPainterPath>
#include <QLinearGradient>
#include <algorithm>

namespace alice {

HistogramRenderer::HistogramRenderer(QQuickItem *parent)
    : QQuickPaintedItem(parent) {
    setRenderTarget(QQuickPaintedItem::FramebufferObject);
    setOpaquePainting(false);
}

void HistogramRenderer::setSource(const QImage &image) {
    image_ = image;
    dirty_ = true;
    emit sourceChanged();
    update();
}

void HistogramRenderer::computeHistogram() {
    histR_.fill(0);
    histG_.fill(0);
    histB_.fill(0);
    histMax_ = 1;

    if (image_.isNull()) return;

    QImage img = image_.convertToFormat(QImage::Format_RGB888);
    // Sample every Nth pixel for performance (skip rows/cols for large images)
    int step = std::max(1, (img.width() * img.height()) / 50000);
    int count = 0;

    for (int y = 0; y < img.height(); y += std::max(1, step / img.width())) {
        const uint8_t *line = img.constScanLine(y);
        for (int x = 0; x < img.width() * 3; x += 3 * std::max(1, step % img.width() + 1)) {
            if (x + 2 >= img.width() * 3) break;
            histR_[line[x]]++;
            histG_[line[x + 1]]++;
            histB_[line[x + 2]]++;
            count++;
        }
    }

    histMax_ = 1;
    totalSamples_ = count;
    for (int i = 0; i < 256; ++i) {
        histMax_ = std::max(histMax_, std::max({histR_[i], histG_[i], histB_[i]}));
    }

    // Detect overexposure: >2% of sampled pixels clipped at 255
    float threshold = count * 0.02f;
    clippedR_ = histR_[255] > threshold;
    clippedG_ = histG_[255] > threshold;
    clippedB_ = histB_[255] > threshold;

    dirty_ = false;
}

void HistogramRenderer::paint(QPainter *painter) {
    if (dirty_) computeHistogram();
    if (image_.isNull()) return;

    const float w = static_cast<float>(width());
    const float h = static_cast<float>(height());
    const float padding = 4.0f;
    const float plotW = w - 2 * padding;
    const float plotH = h - 2 * padding;

    painter->setRenderHint(QPainter::Antialiasing);

    // Background
    painter->setPen(Qt::NoPen);
    painter->setBrush(QColor(27, 32, 37, 200));
    painter->drawRoundedRect(QRectF(0, 0, w, h), 2, 2);

    // Grid lines (10%, 50%, 90% brightness marks)
    painter->setPen(QPen(QColor(57, 64, 73, 180), 0.5));
    for (float pct : {0.1f, 0.5f, 0.9f}) {
        float x = padding + pct * plotW;
        painter->drawLine(QPointF(x, padding), QPointF(x, padding + plotH));
    }

    // Draw each channel as a filled path
    auto drawChannel = [&](const std::array<int, 256> &hist, QColor color) {
        QPainterPath path;
        path.moveTo(padding, padding + plotH);

        for (int i = 0; i < 256; ++i) {
            float x = padding + (static_cast<float>(i) / 255.0f) * plotW;
            float val = static_cast<float>(hist[i]) / static_cast<float>(histMax_);
            // Use sqrt scaling for better shadow/highlight visibility (like pro tools)
            val = std::sqrt(val);
            float y = padding + plotH * (1.0f - val);
            path.lineTo(x, y);
        }

        path.lineTo(padding + plotW, padding + plotH);
        path.closeSubpath();

        // Fill with semi-transparent channel color
        color.setAlpha(60);
        painter->setPen(Qt::NoPen);
        painter->setBrush(color);
        painter->drawPath(path);

        // Stroke with brighter color
        color.setAlpha(180);
        painter->setPen(QPen(color, 1.0));
        painter->setBrush(Qt::NoBrush);

        // Redraw just the curve (not the bottom line)
        QPainterPath curvePath;
        curvePath.moveTo(padding, padding + plotH);
        for (int i = 0; i < 256; ++i) {
            float x = padding + (static_cast<float>(i) / 255.0f) * plotW;
            float val = std::sqrt(static_cast<float>(hist[i]) / static_cast<float>(histMax_));
            float y = padding + plotH * (1.0f - val);
            curvePath.lineTo(x, y);
        }
        painter->drawPath(curvePath);
    };

    // Draw in order: Blue (back), Green (mid), Red (front) — standard cinema order
    drawChannel(histB_, QColor(60, 120, 255));   // Blue
    drawChannel(histG_, QColor(80, 220, 80));    // Green
    drawChannel(histR_, QColor(255, 80, 80));    // Red

    // Overexposure warning — right side indicators
    if (clippedR_ || clippedG_ || clippedB_) {
        float indicatorX = w - 14;
        float indicatorY = padding + 2;
        float dotSize = 6;
        float spacing = 9;

        // Warning triangle icon
        painter->setPen(Qt::NoPen);
        painter->setBrush(QColor(217, 130, 43, 220));
        QPainterPath tri;
        tri.moveTo(indicatorX - 3, indicatorY - 2);
        tri.lineTo(indicatorX + 5, indicatorY - 2);
        tri.lineTo(indicatorX + 1, indicatorY - 8);
        tri.closeSubpath();
        painter->drawPath(tri);

        // Exclamation mark
        painter->setPen(QPen(QColor(0, 0, 0), 1.2));
        painter->drawLine(QPointF(indicatorX + 1, indicatorY - 6.5), QPointF(indicatorX + 1, indicatorY - 4.5));
        painter->drawPoint(QPointF(indicatorX + 1, indicatorY - 3.5));

        // Clipped channel dots below the triangle
        indicatorY += 2;
        if (clippedR_) {
            painter->setPen(Qt::NoPen);
            painter->setBrush(QColor(255, 80, 80, 220));
            painter->drawEllipse(QPointF(indicatorX + 1, indicatorY), dotSize / 2, dotSize / 2);
            indicatorY += spacing;
        }
        if (clippedG_) {
            painter->setPen(Qt::NoPen);
            painter->setBrush(QColor(80, 220, 80, 220));
            painter->drawEllipse(QPointF(indicatorX + 1, indicatorY), dotSize / 2, dotSize / 2);
            indicatorY += spacing;
        }
        if (clippedB_) {
            painter->setPen(Qt::NoPen);
            painter->setBrush(QColor(60, 120, 255, 220));
            painter->drawEllipse(QPointF(indicatorX + 1, indicatorY), dotSize / 2, dotSize / 2);
        }
    }
}

} // namespace alice
