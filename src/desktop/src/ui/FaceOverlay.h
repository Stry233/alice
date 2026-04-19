#pragma once

#include <QQuickPaintedItem>
#include <QVariantList>

namespace alice {

/**
 * Face detection overlay renderer for QML.
 * Draws bounding boxes, eye positions, and focus crosshair.
 */
class FaceOverlay : public QQuickPaintedItem {
    Q_OBJECT
    Q_PROPERTY(QVariantList faces READ faces WRITE setFaces NOTIFY facesChanged)
    Q_PROPERTY(bool showCrosshair READ showCrosshair WRITE setShowCrosshair NOTIFY settingsChanged)

public:
    explicit FaceOverlay(QQuickItem *parent = nullptr);

    QVariantList faces() const { return faces_; }
    void setFaces(const QVariantList &faces);

    bool showCrosshair() const { return showCrosshair_; }
    void setShowCrosshair(bool show) { showCrosshair_ = show; emit settingsChanged(); update(); }

    void paint(QPainter *painter) override;

signals:
    void facesChanged();
    void settingsChanged();

private:
    QVariantList faces_;
    bool showCrosshair_ = false;
};

} // namespace alice
