"""Rewrite the internal family names of the bundled app fonts.

Qt's font resolver matches on family name, not on the loaded file. If
the host system has its own font called "Inter" (very common — it's a
popular open-source UI font that e.g. GitHub Desktop and Figma install
system-wide), then referencing `font.family: "Inter"` in QML can resolve
to EITHER the bundled or the system font, and the choice is not
documented or stable across Qt versions. The visible symptom is
inconsistent rendering: the same UI looks different on different
machines, or changes overnight after an OS font update.

The reliable fix is to give our bundled copies a family name that can't
appear elsewhere. This script rewrites the `name` table of each TTF so
Qt sees them as "Alice Inter" / "Alice Mono" — which we then reference
everywhere in code. The glyph data is untouched; only the metadata
identifying the family changes.

Name-table IDs we touch (per OpenType spec):
    1  Font Family name          (primary — what Qt matches on)
    4  Full font name            (shown in font pickers)
    6  PostScript name           (used by printer drivers, must be ASCII)
   16  Typographic family        (if present; Qt prefers this when set)
   21  WWS family                (modern siblings-aware family)

Run once after checking out the repo, or whenever you bump the bundled
fonts:

    python release/rename_bundled_fonts.py

Requires fontTools (`pip install fonttools`).
"""
from __future__ import annotations

from pathlib import Path

from fontTools.ttLib import TTFont

HERE = Path(__file__).resolve().parent
FONT_DIR = HERE.parent / "assets" / "fonts"

# filename -> (new family display name, new postscript basename)
RENAMES = {
    "Inter-Regular.ttf":      ("Alice Inter",       "AliceInter-Regular"),
    "Inter-Medium.ttf":       ("Alice Inter",       "AliceInter-Medium"),
    "Inter-SemiBold.ttf":     ("Alice Inter",       "AliceInter-SemiBold"),
    "Inter-Bold.ttf":         ("Alice Inter",       "AliceInter-Bold"),
    "RobotoMono-Regular.ttf": ("Alice Mono",        "AliceMono-Regular"),
}

FAMILY_NAME_IDS = (1, 16, 21)
FULL_NAME_IDS = (4,)
POSTSCRIPT_NAME_IDS = (6,)


def _replace_name(font: TTFont, name_id: int, new_value: str) -> None:
    """Overwrite every language variant of the given name ID."""
    # Build the list we want to keep, minus the IDs we're rewriting.
    keep = [n for n in font["name"].names if n.nameID != name_id]
    font["name"].names = keep

    # Standard pair of platforms: Mac (0/0/0) and Windows (3/1/0x409)
    # covers basically every reader. Writing both shields us from
    # platform-specific font pickers falling back to some old name.
    font["name"].setName(new_value, name_id, 3, 1, 0x409)  # Windows, Unicode BMP, en-US
    font["name"].setName(new_value, name_id, 1, 0, 0x0)    # Mac, Roman, English


def rewrite(path: Path, family: str, postscript: str) -> None:
    font = TTFont(str(path))

    for nid in FAMILY_NAME_IDS:
        _replace_name(font, nid, family)

    # Full name convention: "<family> <subfamily>" but the subfamily
    # portion already lives in the postscript name, so we can just show
    # the derived postscript name with a space in it.
    full_display = postscript.replace("-", " ")
    for nid in FULL_NAME_IDS:
        _replace_name(font, nid, full_display)

    # PostScript names are ASCII-only and must not contain spaces.
    for nid in POSTSCRIPT_NAME_IDS:
        _replace_name(font, nid, postscript)

    font.save(str(path))
    print(f"  {path.name:<28}  family -> {family!r}")


def main() -> None:
    missing = [f for f in RENAMES if not (FONT_DIR / f).exists()]
    if missing:
        raise SystemExit(f"Missing bundled font files: {missing} (in {FONT_DIR})")

    print(f"Rewriting family names in {FONT_DIR.relative_to(HERE.parent)}:")
    for filename, (family, postscript) in RENAMES.items():
        rewrite(FONT_DIR / filename, family, postscript)


if __name__ == "__main__":
    main()
