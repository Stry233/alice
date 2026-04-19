#pragma once

#include <QRasterWindow>
#include <QTimer>
#include <QPainter>
#include <QScreen>

namespace alice {

class SplashWindow : public QRasterWindow {
public:
    explicit SplashWindow() {
        setFlags(Qt::SplashScreen | Qt::FramelessWindowHint | Qt::WindowStaysOnTopHint);
        resize(520, 320);

        auto *scr = screen();
        if (scr) {
            auto geom = scr->availableGeometry();
            setPosition((geom.width() - width()) / 2, (geom.height() - height()) / 2);
        }

        QObject::connect(&animTimer_, &QTimer::timeout, [this]() {
            animOffset_ += animDirection_ * 4;
            if (animOffset_ >= 140) animDirection_ = -1;
            if (animOffset_ <= 0) animDirection_ = 1;
            update();
        });
        animTimer_.start(30);
    }

protected:
    void paintEvent(QPaintEvent *) override {
        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);

        int w = width(), h = height();

        // Background
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#1B2025"));
        p.drawRoundedRect(0, 0, w, h, 12, 12);

        // Border
        p.setPen(QPen(QColor("#394049"), 1));
        p.setBrush(Qt::NoBrush);
        p.drawRoundedRect(0, 0, w - 1, h - 1, 12, 12);

        // Gradient at top
        QLinearGradient topGrad(0, 0, 0, 120);
        topGrad.setColorAt(0, QColor(43, 149, 214, 20));
        topGrad.setColorAt(1, QColor(43, 149, 214, 0));
        p.setPen(Qt::NoPen);
        p.setBrush(topGrad);
        p.drawRoundedRect(0, 0, w, 120, 12, 12);

        // Stylized "A" logo
        p.setPen(QColor("#E1E8ED"));
        p.setFont(QFont("Inter", 52, QFont::Bold));
        p.drawText(QRect(0, 40, w, 70), Qt::AlignHCenter, "A");

        // Gradient dot accent
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#EB684D"));
        p.drawEllipse(QPoint(w / 2 + 24, 70), 4, 4);

        // App name
        QFont nameFont("Inter", 22, QFont::Bold);
        nameFont.setLetterSpacing(QFont::AbsoluteSpacing, 3);
        p.setFont(nameFont);
        p.setPen(QColor("#E1E8ED"));
        p.drawText(QRect(0, 130, w, 40), Qt::AlignHCenter, "Alice Studio");

        // Subtitle
        p.setFont(QFont("Inter", 10));
        p.setPen(QColor("#8A9BA8"));
        p.drawText(QRect(0, 172, w, 20), Qt::AlignHCenter,
                   "Autofocus Lens Interface for Cinema Equipment");

        // Loading bar track
        int barY = h - 48;
        int barX = (w - 200) / 2;
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#394049"));
        p.drawRoundedRect(barX, barY, 200, 3, 1, 1);

        // Loading bar indicator
        p.setBrush(QColor("#2B95D6"));
        p.drawRoundedRect(barX + animOffset_, barY, 60, 3, 1, 1);

        // Status
        p.setFont(QFont("Inter", 9));
        p.setPen(QColor("#5C6B7A"));
        p.drawText(QRect(0, barY - 16, w, 14), Qt::AlignHCenter, "Initializing...");

        // Version + copyright
        p.drawText(QRect(w - 60, h - 28, 44, 14), Qt::AlignRight, "v0.1");
        p.drawText(QRect(16, h - 28, 200, 14), Qt::AlignLeft, "\u00A9 2026 SelkaCraft");
    }

private:
    QTimer animTimer_;
    int animOffset_ = 0;
    int animDirection_ = 1;
};

} // namespace alice
