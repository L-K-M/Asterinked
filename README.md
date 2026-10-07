# Asterinked

> [!IMPORTANT]
> LLM disclosure: This codebase was written with substantial help from large language models: AI coding agents working from the [`AGENTS.md`](AGENTS.md) brief in this repo.

**Latest release:** v<!-- version -->0.2.0<!-- /version --> · [Download](https://github.com/L-K-M/Asterinked/releases/latest)

Pen-based PDF annotation for Android 10 and newer.

1. **Open PDF** through Android's document picker, or open or share a PDF to
   Asterinked from another app (Files, email, a browser download).
2. Write with a stylus. Pressure changes line width; fingers pan and pinch to zoom.
   Pick ink colour and pen width in the tool bar at the bottom; they stay as you
   left them.
3. Switch between pen, highlighter and eraser in the tool bar. Highlights
   multiply with the page, so the text stays readable on screen and in the
   export. The eraser removes whole strokes; a stylus side button erases too.
   Turn on the hand icon to write with a finger. Undo and redo sit at the left
   of the tool bar.
4. Change pages with the arrows on the page pill (tap the page number to jump),
   which steps aside while you write and returns after a short pause,
   then **Save copy** to export the whole document, or tap the share icon to send
   an annotated copy straight to another app. Sharing does not mark notes as
   exported. With a keyboard: Ctrl+Z, Ctrl+Shift+Z, Ctrl+S, Ctrl+O, Page Up
   and Page Down.

The app follows the system's dark theme and text size; the PDF page stays white.

Ink becomes vector page content in the exported PDF. Original text remains
selectable; existing artwork and pages are preserved. Repeated exports do not
accumulate duplicate ink. The imported file stays untouched unless you explicitly
choose it as an export destination through a document provider.

The current PDF and completed strokes are stored privately on the device and
restored after reopening. Opening another PDF replaces that draft; export notes
you want to keep first. Drafts are not cloud-synced or included in Android backups.

## Build

Install JDK 17 and the Android SDK (CI uses the runner's preinstalled SDK).
Set `ANDROID_HOME` or `sdk.dir` in `local.properties`, then run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # exactly what CI runs
python3 -m pip install -r scripts/pdf-test-requirements.txt
python3 scripts/verify_pdf.py                         # re-renders test exports
scripts/build.sh                                      # release APK staged into dist/
scripts/install.sh                                    # build + install + launch
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. PR builds also publish
it as the `asterinked-debug-<sha>` GitHub Actions artifact; `scripts/build.sh`
stages `dist/asterinked-v<version>-<variant>.apk`. Both build types are signed
with the checked-in `app/debug.keystore` — sideload-only, deliberate
(docs/decisions/0001). Dependency versions live in `gradle/libs.versions.toml`.

## Design

```text
Activity / pen view
        |
   EditorViewModel       InkGeometry (shared preview/export curves)
        |
   DocumentService
        |
   DocumentStore         PdfEngine
   (picker + drafts)     (PdfRenderer + PDFBox)
```

PDF and storage work runs on one background worker. Imported PDFs are copied to
private storage so document providers need not offer seekable input. Export is
built in a temporary file before the destination is opened. Provider write failures
leave the local draft intact, but may leave an incomplete destination file; retry
to a new destination.

The pen pipeline takes inspiration from BangniDraw's pointer-ID tracking,
historical input samples and pressure handling. It uses a small vector geometry
implementation, with the same segments for preview and PDF export. PDF coordinate
mapping accounts for crop offsets and all four page rotations.

## Current limits

- PDFs that need a password to open are rejected. PDFs that open without one
  but carry owner restrictions work if they allow changes; the exported copy
  stays encrypted with the same restrictions (a new random owner password,
  AES for 128-bit keys). Digital signatures are not preserved as valid
  signatures after modification.
- Exported ink is permanent page content; reopen it to add more notes, not to
  erase previous strokes. Undo/redo applies to strokes in the current draft.
- Preview resolution is capped; high zoom can soften the underlying page. Ink
  remains vector in the output.
- The active, unfinished stroke and redo history are not restored after process
  death. Completed strokes are queued for disk immediately.

## Verification

Tests cover pressure geometry, palm/cancel input, draft recovery, PDF text and
vector preservation, page targeting, repeated saves and crop/rotation mapping.
The Python check renders actual exports with PDFium and checks ink placement and
preserved text/artwork for all rotations. Robolectric cannot run Android's
PdfRenderer; this separate check also runs in CI.

Device check: open a multipage PDF, write with light/heavy pressure while resting
your palm, zoom, undo/redo, rotate the device, background/reopen, save, then open
the exported PDF in another viewer. Check ink alignment and text selection.
Real stylus latency and hardware palm rejection require a physical device.

## Dependencies

App source: [Unlicense](LICENSE). AndroidX and
[PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) are Apache-2.0;
their bundled notices and dependency licenses continue to apply.
