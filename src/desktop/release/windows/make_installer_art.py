"""Regenerate NSIS installer branding bitmaps from the app icon PNGs.

NSIS MUI2 expects two BMP assets that the installer.nsi references:

  welcome.bmp   164 x 314 logical   (rendered at 2x = 328 x 628 pixels)
  header.bmp    150 x  57 logical   (rendered at 2x = 300 x 114 pixels)

The bitmaps are intentionally rendered at 2x pixel density so that
NSIS's default MUI2 stretch-to-fit behaviour produces a sharp image at
both 100% and 150-200% Windows DPI scales (with ManifestDPIAware true
set in installer.nsi). Shipping a 1x BMP looks crisp on a standard
1080p monitor but bilinear-blurs on anything >=125% DPI — which covers
almost every laptop panel sold post-2018.

Both files are committed to release/windows/ so the packaging script
doesn't need ImageMagick or Pillow on the build host. Rerun only when
the source icons change or the branding needs a refresh.

    python release/windows/make_installer_art.py

Requires Pillow (`pip install Pillow`).
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw


HERE = Path(__file__).resolve().parent
REPO_ICONS = HERE.parent.parent / "assets" / "icons"

# NSIS MUI2 canonical dimensions. We render at SCALE * these so the BMP
# has enough source pixels for NSIS to stretch cleanly at common Windows
# DPI scales (100%, 125%, 150%, 200%).
SCALE = 2
WELCOME_LOGICAL = (164, 314)
HEADER_LOGICAL = (150, 57)


def _gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size, top)
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        r = int(top[0] * (1 - t) + bottom[0] * t)
        g = int(top[1] * (1 - t) + bottom[1] * t)
        b = int(top[2] * (1 - t) + bottom[2] * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))
    return img


def make_welcome() -> Image.Image:
    # 2x pixel resolution for DPI-aware sharpness; logical layout stays
    # 164x314 so the logo stays proportionally small.
    logical_w, logical_h = WELCOME_LOGICAL
    w, h = logical_w * SCALE, logical_h * SCALE

    img = _gradient((w, h), (15, 23, 42), (2, 6, 23))  # slate-900 -> slate-950

    # Logo: 48 logical px (96px at 2x). Previously 128 logical px, which
    # filled the whole panel — now occupies ~30% of panel width.
    icon_logical = 48
    icon_px = icon_logical * SCALE
    icon_src = Image.open(REPO_ICONS / "alice-studio-128.png").convert("RGBA")
    icon = icon_src.resize((icon_px, icon_px), Image.LANCZOS)
    icon_x = (w - icon.width) // 2
    icon_y = (h // 2) - icon.height // 2 - 40 * SCALE
    img.paste(icon, (icon_x, icon_y), icon)

    # Thin accent line below the icon. Uses the same blue-400 as the
    # accent in the app itself.
    draw = ImageDraw.Draw(img)
    accent_y = icon_y + icon.height + 16 * SCALE
    accent_half = 18 * SCALE
    draw.line(
        [(w // 2 - accent_half, accent_y), (w // 2 + accent_half, accent_y)],
        fill=(96, 165, 250),
        width=SCALE,
    )
    return img


def make_header() -> Image.Image:
    # 2x pixel resolution for sharpness; right-aligned icon replaces the
    # previous left-aligned one so it reads as a trademark in the header
    # rather than competing with the MUI2 page title on the left.
    logical_w, logical_h = HEADER_LOGICAL
    w, h = logical_w * SCALE, logical_h * SCALE

    img = Image.new("RGB", (w, h), (255, 255, 255))

    icon_logical = 24  # was 40 — now sits quietly in the corner
    icon_px = icon_logical * SCALE
    icon_src = Image.open(REPO_ICONS / "alice-studio-48.png").convert("RGBA")
    icon = icon_src.resize((icon_px, icon_px), Image.LANCZOS)
    # Right-align with an 8 logical-px margin on the right.
    margin_px = 8 * SCALE
    icon_x = w - icon.width - margin_px
    icon_y = (h - icon.height) // 2
    img.paste(icon, (icon_x, icon_y), icon)
    return img


def main() -> None:
    welcome_path = HERE / "welcome.bmp"
    header_path = HERE / "header.bmp"

    make_welcome().save(welcome_path, format="BMP")
    make_header().save(header_path, format="BMP")

    print(f"wrote {welcome_path.relative_to(HERE.parent.parent)} ({Image.open(welcome_path).size})")
    print(f"wrote {header_path.relative_to(HERE.parent.parent)}  ({Image.open(header_path).size})")


if __name__ == "__main__":
    main()
