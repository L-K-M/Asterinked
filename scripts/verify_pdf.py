"""Render the app's exported fixtures in PDFium, independently of PDFBox."""

from pathlib import Path

import pypdfium2 as pdfium
import pypdfium2.raw as pdfium_c

FIXTURES = Path(__file__).resolve().parents[1] / "app/build/test-output/raster-proof"
ROTATIONS = (0, 90, 180, 270)
# Fixture name -> page rotation. "encrypted" opens without a password but
# forbids printing; its export must stay encrypted with the same restriction.
# "highlight" adds a highlighter stroke across the text line; multiply
# blending must keep the text dark under it.
CASES = [(str(rotation), rotation) for rotation in ROTATIONS] + [("encrypted", 0), ("highlight", 0)]
NOT_ENCRYPTED = -1
PRINT_PERMISSION = 1 << 2
INK_POSITION = (0.23, 0.31)
# The "highlight" fixture's highlighter tap, as a fraction of the page.
TAP_POSITION = (0.7, 0.8)
TAP_REACH = 8
COLOR_THRESHOLD = 100
POSITION_TOLERANCE = 3


def render(path):
    document = pdfium.PdfDocument(path)
    try:
        page = document[0]
        text_page = page.get_textpage()
        text = text_page.get_text_range()
        bitmap = page.render(scale=2)
        image = bitmap.to_pil().convert("RGB")
        bitmap.close()
        text_page.close()
        page.close()
        return image, text
    finally:
        document.close()


def security(path):
    document = pdfium.PdfDocument(path)
    try:
        return pdfium_c.FPDF_GetSecurityHandlerRevision(document.raw), pdfium_c.FPDF_GetDocPermissions(document.raw)
    finally:
        document.close()


for name, rotation in CASES:
    original, before_text = render(FIXTURES / f"source-{name}.pdf")
    exported, after_text = render(FIXTURES / f"export-{name}.pdf")
    assert f"Original text {rotation}" in before_text
    source_revision, source_permissions = security(FIXTURES / f"source-{name}.pdf")
    revision, permissions = security(FIXTURES / f"export-{name}.pdf")
    if name == "encrypted":
        assert source_revision != NOT_ENCRYPTED, "Fixture premise broken: the source is not encrypted"
        assert not source_permissions & PRINT_PERMISSION, "Fixture premise broken: the source allows printing"
        assert revision != NOT_ENCRYPTED, "Export dropped the source's encryption"
        assert not permissions & PRINT_PERMISSION, "Export dropped the source's no-print restriction"
    else:
        assert revision == NOT_ENCRYPTED, f"Export of the plain {name} fixture was encrypted"
    assert before_text == after_text, f"Text changed in {name}"
    assert original.size == exported.size

    # Asymmetric placement catches errors hidden by a center probe.
    red = []
    dark = []
    yellow = []
    for y in range(exported.height):
        for x in range(exported.width):
            r, g, b = exported.getpixel((x, y))
            if r > 150 and g < COLOR_THRESHOLD and b < COLOR_THRESHOLD:
                red.append((x, y))
            if r > 200 and g > 180 and b < 140:
                yellow.append((x, y))
            if max(original.getpixel((x, y))) < COLOR_THRESHOLD:
                dark.append((x, y))

    assert red, f"Missing ink in {name}"
    actual_x = (min(x for x, _ in red) + max(x for x, _ in red)) / 2
    actual_y = (min(y for _, y in red) + max(y for _, y in red)) / 2
    expected_x = INK_POSITION[0] * exported.width
    expected_y = INK_POSITION[1] * exported.height
    assert abs(actual_x - expected_x) <= POSITION_TOLERANCE, (name, actual_x, expected_x)
    assert abs(actual_y - expected_y) <= POSITION_TOLERANCE, (name, actual_y, expected_y)
    # For "highlight" this also proves multiply blending: text under the marker stays dark.
    assert dark and all(max(exported.getpixel(pixel)) < COLOR_THRESHOLD for pixel in dark), f"Artwork changed in {name}"
    assert bool(yellow) == (name == "highlight"), f"Highlight presence wrong in {name}"
    if yellow:
        # The blending proof above only counts if the marker actually covers text.
        left, right = min(x for x, _ in yellow), max(x for x, _ in yellow)
        top, bottom = min(y for _, y in yellow), max(y for _, y in yellow)
        assert any(left <= x <= right and top <= y <= bottom for x, y in dark), f"Highlight in {name} covers no text"
        # A tap leaves a mark too: PDFium drops a path that only repeats one point.
        tap_x, tap_y = TAP_POSITION[0] * exported.width, TAP_POSITION[1] * exported.height
        assert any(abs(x - tap_x) <= TAP_REACH and abs(y - tap_y) <= TAP_REACH for x, y in yellow), f"Highlighter tap missing in {name}"
    exported.save(FIXTURES / f"export-{name}.png")
    print(f"{name}: ink aligned; text and artwork preserved")
