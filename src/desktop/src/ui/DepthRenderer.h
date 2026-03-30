#pragma once

#include <QQuickPaintedItem>
#include <QImage>

namespace alice {

/**
 * Depth map visualization renderer for QML.
 * Displays colorized depth frames with measurement overlay.
 */
class DepthRenderer : public QQuickPaintedItem {
    Q_OBJECT
    Q_PROPERTY(QImage source READ source WRITE setSource NOTIFY sourceChanged)
    Q_PROPERTY(float depth READ depth WRITE setDepth NOTIFY depthChanged)
    Q_PROPERTY(float confidence READ confidence WRITE setConfidence NOTIFY depthChanged)

public:
    explicit DepthRenderer(QQuickItem *parent = nullptr);

    QImage source() const { return image_; }
    void setSource(const QImage &image);

    float depth() const { return depth_; }
    void setDepth(float d) { depth_ = d; emit depthChanged(); update(); }
    float confidence() const { return confidence_; }
    void setConfidence(float c) { confidence_ = c; emit depthChanged(); update(); }

    void paint(QPainter *painter) override;

signals:
    void sourceChanged();
    void depthChanged();

private:
    QImage image_;
    float depth_ = 0.0f;
    float confidence_ = 0.0f;
};

} // namespace alice
