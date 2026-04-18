#pragma once

#include <QRasterWindow>
#include <QTimer>
#include <QPainter>
#include <QScreen>
#include <QString>
#include <QSvgRenderer>

namespace alice {

class SplashWindow : public QRasterWindow {
public:
    explicit SplashWindow() {
        setFlags(Qt::SplashScreen | Qt::FramelessWindowHint | Qt::WindowStaysOnTopHint);
        resize(520, 320);

        if (auto *scr = screen()) {
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

    void setStatus(const QString &text) {
        status_ = text;
        update();
    }

protected:
    void paintEvent(QPaintEvent *) override {
        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);

        const int w = width(), h = height();

        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#2A3139"));
        p.drawRect(0, 0, w, h);

        p.setBrush(QColor("#2B95D6"));
        p.drawRect(0, 0, w, 3);

        p.setBrush(QColor("#1B2025"));
        p.drawRect(0, h - 64, w, 64);
        p.setPen(QPen(QColor("#394049"), 1));
        p.drawLine(0, h - 64, w, h - 64);

        const int contentTop = 3 + (253 - 116) / 2;

        QSvgRenderer logoSvg(QString(":/qt/qml/Alice/UI/assets/icons/alice_logo.svg"));
        if (logoSvg.isValid()) {
            QRectF logoRect((w - 44) / 2.0, contentTop, 44, 50);
            logoSvg.render(&p, logoRect);
        }

        QFont titleFont("Alice Inter", 11, QFont::Bold);
        titleFont.setLetterSpacing(QFont::AbsoluteSpacing, 4);
        titleFont.setCapitalization(QFont::AllUppercase);
        p.setFont(titleFont);
        p.setPen(QColor("#E1E8ED"));
        p.drawText(QRect(0, contentTop + 64, w, 28), Qt::AlignHCenter, QStringLiteral(ALICE_APP_NAME));

        p.setFont(QFont("Alice Inter", 8));
        p.setPen(QColor("#8A9BA8"));
        p.drawText(QRect(0, contentTop + 100, w, 16), Qt::AlignHCenter,
                   "Autofocus Lens Interface for Cinema Equipment");

        const int barY = h - 40;
        const int barX = (w - 200) / 2;
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#394049"));
        p.drawRoundedRect(barX, barY, 200, 3, 1, 1);
        p.setBrush(QColor("#2B95D6"));
        p.drawRoundedRect(barX + animOffset_, barY, 60, 3, 1, 1);

        p.setFont(QFont("Alice Inter", 8));
        p.setPen(QColor("#5C6B7A"));
        p.drawText(QRect(0, barY - 20, w, 14), Qt::AlignHCenter, status_);

        p.setFont(QFont("Alice Inter", 7));
        p.drawText(QRect(w - 60, h - 22, 44, 12), Qt::AlignRight, "v" ALICE_APP_VERSION);
        p.drawText(QRect(16, h - 22, 200, 12), Qt::AlignLeft, "\u00A9 2026 SelkaCraft");
    }

private:
    QTimer animTimer_;
    int animOffset_ = 0;
    int animDirection_ = 1;
    QString status_ = "Initializing...";
};

} // namespace alice
