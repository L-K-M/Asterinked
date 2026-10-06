"""Render the app's exported fixtures in PDFium, independently of PDFBox."""

from pathlib import Path
import re

import pypdfium2 as pdfium
import pypdfium2.raw as pdfium_c

FIXTURES = Path(__file__).resolve().parents[1] / "app/build/test-output/raster-proof"
ROTATIONS = (0, 90, 180, 270)
# Fixture name -> page rotation. "encrypted" opens without a password but
# forbids printing; its export must stay encrypted with the same restriction.
# "highlight" adds a highlighter stroke across the text line; multiply
# blending must keep the text dark under it.
# ISO 32000: security version, handler revision, crypt method, key bits.
AES_CASES = {
    "aesv2-no-length": (4, 4, b"AESV2", 128),
    "aesv3-no-length": (5, 6, b"AESV3", 256),
}
CASES = [(str(rotation), rotation) for rotation in ROTATIONS] + [("encrypted", 0), ("highlight", 0)] + [(name, 0) for name in AES_CASES]
NOT_ENCRYPTED = -1
PRINT_PERMISSION = 1 << 2
MODIFY_PERMISSION = 1 << 3
EXTRACT_PERMISSION = 1 << 4
FILL_FORM_PERMISSION = 1 << 8
AES_DENIED_PERMISSIONS = PRINT_PERMISSION | EXTRACT_PERMISSION | FILL_FORM_PERMISSION
BITS_PER_BYTE = 8
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


def security(path):
    document = pdfium.PdfDocument(path)
    try:
        assert pdfium_c.FPDF_DocumentHasValidCrossReferenceTable(document.raw), f"Broken xref in {path.name}"
        return (
            pdfium_c.FPDF_GetSecurityHandlerRevision(document.raw),
            pdfium_c.FPDF_GetDocPermissions(document.raw),
            pdfium_c.FPDF_GetDocUserPermissions(document.raw),
        )
    finally:
        document.close()


def aes_policy(path):
    # PDFium exposes revision and permissions, but no cipher/key-length getter.
    # Inspect the referenced, unencrypted dictionary of these generated fixtures;
    # successful PDFium decryption/rendering then proves the advertised AES works.
    pdf = path.read_bytes()
    reference = re.search(rb"/Encrypt\s+(\d+)\s+(\d+)\s+R\b", pdf)
    assert reference, f"Missing encryption reference in {path.name}"
    object_header = rb"(?m)^" + reference[1] + rb"\s+" + reference[2] + rb"\s+obj\s*<<"
    dictionary = re.search(object_header + rb"(.*?)\nendobj\b", pdf, re.DOTALL)
    assert dictionary, f"Missing encryption dictionary in {path.name}"
    dictionary = dictionary[1]
    crypt_filter = re.search(rb"/CF\s*<<\s*/StdCF\s*<<(.*?)>>\s*>>", dictionary, re.DOTALL)
    assert crypt_filter, f"Missing standard crypt filter in {path.name}"
    method = re.search(rb"/CFM\s*/(\w+)\b", crypt_filter[1])
    filter_length = re.search(rb"/Length\s+(\d+)\b", crypt_filter[1])
    assert method and filter_length, f"Incomplete crypt filter in {path.name}"
    top_level = dictionary[:crypt_filter.start()] + dictionary[crypt_filter.end():]
    assert re.search(rb"/StmF\s*/StdCF\b", top_level), f"Streams are not AES-encrypted in {path.name}"
    assert re.search(rb"/StrF\s*/StdCF\b", top_level), f"Strings are not AES-encrypted in {path.name}"
    version = re.search(rb"/V\s+(\d+)\b", top_level)
    assert version, f"Missing security version in {path.name}"
    length = re.search(rb"/Length\s+(\d+)\b", top_level)
    return int(version[1]), method[1], int(length[1]) if length else None, int(filter_length[1])


for name, rotation in CASES:
    source_path = FIXTURES / f"source-{name}.pdf"
    export_path = FIXTURES / f"export-{name}.pdf"
    original, before_text = render(source_path)
    exported, after_text = render(export_path)
    assert f"Original text {rotation}" in before_text
    source_revision, source_permissions, source_user_permissions = security(source_path)
    revision, permissions, user_permissions = security(export_path)
    security_summary = ""
    if name == "encrypted" or name in AES_CASES:
        assert source_revision != NOT_ENCRYPTED, "Fixture premise broken: the source is not encrypted"
        assert not source_permissions & PRINT_PERMISSION, "Fixture premise broken: the source allows printing"
        assert revision != NOT_ENCRYPTED, "Export dropped the source's encryption"
        assert not permissions & PRINT_PERMISSION, "Export dropped the source's no-print restriction"
        assert source_permissions == source_user_permissions, f"{name} must open as user, not owner"
        assert permissions == user_permissions == source_user_permissions, f"Permission bits changed in {name}"
        security_summary = f"permissions {permissions:#010x} preserved; "
        if name in AES_CASES:
            version, expected_revision, method, bits = AES_CASES[name]
            assert source_revision == expected_revision, f"Wrong source revision in {name}: {source_revision}"
            assert aes_policy(source_path) == (version, method, None, bits // BITS_PER_BYTE), f"Missing-Length AES premise broken in {name}"
            assert revision == expected_revision, f"AES-{bits} downgraded in {name}: R={revision}"
            export_version, export_method, export_bits, _ = aes_policy(export_path)
            assert (export_version, export_method, export_bits) == (version, method, bits), f"AES strength changed in {name}"
            assert not source_user_permissions & AES_DENIED_PERMISSIONS, f"Source restrictions missing in {name}"
            assert source_user_permissions & MODIFY_PERMISSION, f"Source forbids editing in {name}"
            security_summary = f"AES-{bits} (V={version}, R={revision}); " + security_summary
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
    exported.save(FIXTURES / f"export-{name}.png")
    print(f"{name}: {security_summary}ink aligned; text and artwork preserved")
