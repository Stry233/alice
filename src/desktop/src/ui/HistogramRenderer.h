#pragma once

#include <QQuickPaintedItem>
#include <QImage>
#include <array>

namespace alice {

/**
 * Industry-standard RGB histogram renderer for cinema monitoring.
 * Displays separate R, G, B channel distributions with semi-transparent
 * overlay, matching professional tools like DaVinci Resolve.
 */
class HistogramRenderer : public QQuickPaintedItem {
    Q_OBJECT
    Q_PROPERTY(QImage source READ source WRITE setSource NOTIFY sourceChanged)
    Q_PROPERTY(bool visible READ isVisible WRITE setVisible NOTIFY visibleChanged)

public:
    explicit HistogramRenderer(QQuickItem *parent = nullptr);

    QImage source() const { return image_; }
    void setSource(const QImage &image);

    void paint(QPainter *painter) override;

signals:
    void sourceChanged();

private:
    void computeHistogram();

    QImage image_;
    std::array<int, 256> histR_{};
    std::array<int, 256> histG_{};
    std::array<int, 256> histB_{};
    int histMax_ = 1;
    int totalSamples_ = 0;
    bool clippedR_ = false;
    bool clippedG_ = false;
    bool clippedB_ = false;
    bool dirty_ = true;
};

} // namespace alice
