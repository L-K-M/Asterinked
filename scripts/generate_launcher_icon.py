"""Regenerate the adaptive launcher icon's foreground from media-sources/icon.png.

The artwork is full-bleed on white, so it is centred at ARTWORK_DP inside the
108dp adaptive-icon layer over a white background layer. Square masks crop a
little of its edge; circular masks keep the 66dp safe zone (asterisk and pen
tip). Requires Pillow (scripts/pdf-test-requirements.txt).

    python3 scripts/generate_launcher_icon.py
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "media-sources/icon.png"
RESOURCES = ROOT / "app/src/main/res"
LAYER_DP = 108
ARTWORK_DP = 76
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
WEBP_QUALITY = 92

artwork = Image.open(SOURCE).convert("RGBA")
for density, scale in DENSITIES.items():
    layer_px = round(LAYER_DP * scale)
    artwork_px = round(ARTWORK_DP * scale)
    layer = Image.new("RGBA", (layer_px, layer_px), (0, 0, 0, 0))
    offset = (layer_px - artwork_px) // 2
    layer.paste(artwork.resize((artwork_px, artwork_px), Image.LANCZOS), (offset, offset))
    target = RESOURCES / f"mipmap-{density}" / "ic_launcher_foreground.webp"
    target.parent.mkdir(parents=True, exist_ok=True)
    layer.save(target, "WEBP", quality=WEBP_QUALITY, method=6)
    print(f"{target.relative_to(ROOT)}: {layer_px}px, {target.stat().st_size} bytes")
