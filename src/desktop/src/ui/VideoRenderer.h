#pragma once

#include <QQuickPaintedItem>
#include <QImage>

namespace alice {

/**
 * Hardware-accelerated video renderer for QML.
 * Displays RGB frames from RealSense or UVC camera.
 */
class VideoRenderer : public QQuickPaintedItem {
    Q_OBJECT
    Q_PROPERTY(QImage source READ source WRITE setSource NOTIFY sourceChanged)

public:
    explicit VideoRenderer(QQuickItem *parent = nullptr);

    QImage source() const { return image_; }
    void setSource(const QImage &image);

    void paint(QPainter *painter) override;

signals:
    void sourceChanged();

private:
    QImage image_;
};

} // namespace alice
