"""Regenerate the multi-size Windows .ico used by AliceStudio.exe.

The .ico (`assets/icons/alice_app_icon.ico`) is committed to the repo
so that:

  1. The CMake build embeds it into AliceStudio.exe via
     src/alice_studio.rc.in -> `1 ICON "...alice_app_icon.ico"`.
     This is what gives File Explorer / Start Menu / Task Manager the
     app icon on Windows.

  2. release/package.sh copies the same file into the installer bundle
     so installer.nsi can reference it for MUI_ICON / MUI_UNICON and
     the Add/Remove Programs DisplayIcon entry.

Committing the .ico means `magick`/`convert` and `Pillow` stop being
build dependencies on the packaging host. Rerun this script only when
the source icon (`alice-studio-256.png`) changes.

    python release/windows/make_app_icon.py

Requires Pillow (`pip install Pillow`).
"""
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[2]
SRC = REPO_ROOT / "assets" / "icons" / "alice-studio-256.png"
OUT = REPO_ROOT / "assets" / "icons" / "alice_app_icon.ico"

# Shell surfaces Windows picks at, from smallest (16x16 list view) up to
# 256x256 ("extra large icons" / high-DPI start menu). Keeping the full
# ladder adds ~30 KB vs a single 256 which shell bilinear-downscales.
SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]


def main() -> None:
    Image.open(SRC).convert("RGBA").save(OUT, format="ICO", sizes=SIZES)
    print(f"wrote {OUT.relative_to(REPO_ROOT)} ({OUT.stat().st_size:,} bytes, {len(SIZES)} sizes)")


if __name__ == "__main__":
    main()
