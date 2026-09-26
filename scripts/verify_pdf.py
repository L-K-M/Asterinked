"""Render the app's exported fixtures in PDFium, independently of PDFBox."""

from pathlib import Path

import pypdfium2 as pdfium

FIXTURES = Path(__file__).resolve().parents[1] / "app/build/test-output/raster-proof"
ROTATIONS = (0, 90, 180, 270)
INK_POSITION = (0.23, 0.31)
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


for rotation in ROTATIONS:
    original, before_text = render(FIXTURES / f"source-{rotation}.pdf")
    exported, after_text = render(FIXTURES / f"export-{rotation}.pdf")
    assert f"Original text {rotation}" in before_text
    assert before_text == after_text, f"Text changed at rotation {rotation}"
    assert original.size == exported.size

    # Asymmetric placement catches errors hidden by a center probe.
    red = []
    dark = []
    for y in range(exported.height):
        for x in range(exported.width):
            r, g, b = exported.getpixel((x, y))
            if r > 150 and g < COLOR_THRESHOLD and b < COLOR_THRESHOLD:
                red.append((x, y))
            if max(original.getpixel((x, y))) < COLOR_THRESHOLD:
                dark.append((x, y))

    assert red, f"Missing ink at rotation {rotation}"
    actual_x = (min(x for x, _ in red) + max(x for x, _ in red)) / 2
    actual_y = (min(y for _, y in red) + max(y for _, y in red)) / 2
    expected_x = INK_POSITION[0] * exported.width
    expected_y = INK_POSITION[1] * exported.height
    assert abs(actual_x - expected_x) <= POSITION_TOLERANCE, (rotation, actual_x, expected_x)
    assert abs(actual_y - expected_y) <= POSITION_TOLERANCE, (rotation, actual_y, expected_y)
    assert dark and all(max(exported.getpixel(pixel)) < COLOR_THRESHOLD for pixel in dark), f"Artwork changed at {rotation}"
    exported.save(FIXTURES / f"export-{rotation}.png")
    print(f"Rotation {rotation}: ink aligned; text and artwork preserved")
