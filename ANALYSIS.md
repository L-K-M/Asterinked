# Asterinked analysis

Working backlog for Asterinked (pen annotation of PDFs on Android). It is
written for the next person or agent picking up work: every open item says
why it matters, where the code is, a concrete approach and how to prove it.
Read `AGENTS.md` first for build, CI and repo conventions.

Baseline: `main` at v0.2.0 (`333fa4b`), reviewed file by file on 2026-09-26.
The CI gate (`./gradlew testDebugUnitTest lintDebug assembleDebug` plus
`scripts/verify_pdf.py`) is green there.

IDs (B = bug, G = general, P = performance, F = feature, V = visual,
U = UX, D = delight, T = tests and tooling) are stable; keep them when
updating this file, and move finished items to the "Done" table instead of
deleting them silently.

---

## 1. Status: implemented in open pull requests

Eight focused PRs, each cut from `main`, each green in CI and reviewed by
the GLM bot until no valid important findings remained, plus #18, which
combines all eight. They wait for a human merge.

| PR | Branch | Covers | Summary |
|---|---|---|---|
| [#10](https://github.com/L-K-M/Asterinked/pull/10) | `claude/peaceful-darwin-twgnu4` | P1, P2, P3 | Committed ink is recorded once per edit into a `RenderNode` and replayed through a view-sized GPU layer; per-stroke geometry cache; incremental live-stroke smoothing (`InkStrokeBuilder`). `InkIncrementalTest` pins export geometry to v0.1.0. |
| [#11](https://github.com/L-K-M/Asterinked/pull/11) | `claude/gesture-fixes` | B4, B5, B10, B14 (partly), F3 (partly) | Pan and pinch follow the finger centroid in both modes; no jump on finger lift; pan clamped immediately; animated double-tap zoom; swipe to turn pages at fit zoom; zoom kept across page turns; 12dp page margin; a palm stays inert after the pen lifts. |
| [#12](https://github.com/L-K-M/Asterinked/pull/12) | `claude/pdf-compat-errors` | B1, B2 (message), B3, B12 (partly) | Owner-restricted encrypted PDFs open; exports are re-encrypted with the same permissions (AES for 128-bit). `DocumentProblem` maps failures to plain messages from `strings.xml`. PDFium proof gains an encrypted fixture. |
| [#13](https://github.com/L-K-M/Asterinked/pull/13) | `claude/instant-page-turns` | P4, P7, P5 (partly), G8 (partly) | One `PdfRenderer` per document, LRU preview cache with neighbour prefetch, instant page turns without the busy state, coalesced draft writes, mipmapped previews. `EditorViewModel` takes a `DocumentOperations` + executor (test seam). |
| [#14](https://github.com/L-K-M/Asterinked/pull/14) | `claude/eraser` | F1, B13 | Stroke eraser tool, stylus eraser end and side button; undo/redo via per-page `(before, after)` snapshots (`InkHistory`). |
| [#15](https://github.com/L-K-M/Asterinked/pull/15) | `claude/open-with-share` | F4, F5, G4, B11 | VIEW/SEND intent filters (`singleTask`), incoming PDFs wait until the editor is idle, Share via `FileProvider`, sanitized `Draft.exportName` (also fixes path traversal from hostile display names). |
| [#16](https://github.com/L-K-M/Asterinked/pull/16) | `claude/ui-refresh` | V1–V5, V6 (partly), V7, B6, B7, B9, F3 (jump to page), F10 | Compact icon top bar, tool strip with swatches and width dots, floating page pill (tap to jump, Fit), page shadow, red/slate palette, designed adaptive launcher icon, remembered pen settings. |
| [#17](https://github.com/L-K-M/Asterinked/pull/17) | `claude/highlighter` | F2 | Highlighter kind: constant-width path multiplied with the page on screen and in the PDF (`/BM /Multiply`); kind persisted backward-compatibly; PDFium fixture proves text stays dark and that the marker really covers text. |
| [#18](https://github.com/L-K-M/Asterinked/pull/18) | `claude/integration` | all of the above, B16 | Optional one-step merge: #10–#17 merged into `main` with every conflict below resolved, the features ported into #16's new chrome (highlighter and eraser toggles in the tool strip, Share icon in the top bar), and highlights drawn under pen ink in the export too. Merging it marks the eight as merged. |

### 1.1 Recommended merge order and integration notes

All eight merge cleanly into `main` on their own, but they overlap heavily
with each other (22 conflicting pairs, mostly `InkPageView`, `MainActivity`,
`EditorViewModel`, `DocumentService`). #18 is the worked answer: it merges
them in the order below, and its merge commits show each resolution. Either
merge #18 alone, or merge the eight in this order (lowest churn first) and
use #18's merge commits as the reference:

1. **#12** errors and encryption (document layer).
2. **#13** page turns (reshapes `EditorViewModel`, adds the test seam).
3. **#10** ink rendering.
4. **#11** gestures.
5. **#14** eraser.
6. **#17** highlighter.
7. **#15** open with and share.
8. **#16** UI refresh last, because it rewrites `MainActivity`.

Rebase each PR on `main` after its predecessor merges and rerun the full
gate. Semantic interactions to handle while resolving (not just text
conflicts):

- **#10 + #14:** the eraser hides strokes live by skipping them in the draw
  loop. With #10 the committed ink is a cached `RenderNode`, so every change
  to the erased set must mark the ink node stale (`inkNodeStale = true`) or
  the hidden strokes stay visible until the gesture ends.
- **#10 + #17:** multiply blending must not happen inside #10's compositing
  layer (multiply against the layer's transparent pixels yields opaque
  colour and hides the text). #18 draws highlights directly on the view
  canvas (paths cached per stroke) before the cached pen layer, so
  highlights always sit under pen ink. The export and the software path
  must then use the same order (`strokes.sortedBy { it.kind !=
  HIGHLIGHTER }` in `PdfEngine.export`), otherwise a highlight added after a
  blue note turns it dark teal in the PDF only (B16, fixed in #18 with tests).
- **#10 + #13:** keep #13's "blank white page while the preview renders"
  branch in front of #10's cached ink drawing.
- **#12 + #13 / #15:** `DocumentService` should both implement
  `DocumentOperations` (#13) and wrap each step in `during(stage)` (#12);
  the model's `failureMessage` (#13) becomes #12's `messageFor`. `share()`
  from #15 belongs on `DocumentOperations` too, wrapped in
  `during(EXPORT_FAILED)`.
- **#13 + #14:** `changeInk` keeps #13's coalesced `saveDraft` and #14's
  `withHistory` state; `goToPage` publishes `withHistory(...)`.
- **#12 + #17:** `PdfEngine.export` keeps both `keepProtection` and the
  highlighter branch. `PdfEngineRasterTest` and `verify_pdf.py` merge their
  case lists into one: rotations plus `encrypted` plus `highlight`.
- **#16 + #14 / #15 / #17:** the new UI needs homes for Eraser and
  Highlighter, Share and #15's incoming-intent handling. #18 adds a second
  segment (Highlighter · Eraser toggles) next to pen/finger, swaps the
  swatches to highlight tints while the marker is on, puts Share as an icon
  before Save copy, and keeps the tool hints as toasts. The highlighter
  choice persists in the `pen` preferences; the eraser only survives
  rotation, so a fresh launch always writes. `configurePen()` passes kind
  and tool through to `InkPageView`.

---

## 2. Backlog: bugs

### B2. Password prompt for protected PDFs (with F11)
- **Why:** PDFs that need a password are refused (after #12, with a clear
  message). Common for bank statements and HR documents.
- **Where:** `document/PdfEngine.kt` (`load`), `document/DocumentService.kt`
  (`open`), `ui/MainActivity.kt`.
- **Approach:** On `DocumentProblem.PASSWORD_PROTECTED`, prompt for a
  password; `PDDocument.load(file, password, …)`, then
  `setAllSecurityToBeRemoved(true)` and save a decrypted private copy for
  `PdfRenderer` (which only accepts passwords from API 35). Re-encrypt the
  export with the user password and the original permissions (mirror
  `keepProtection` from #12). Never persist the password.
- **Acceptance:** JVM test with a user-password fixture: wrong password →
  retry prompt state; right password → pages inspected and export
  re-encrypted (loads only with the password). Add a PDFium fixture if
  pypdfium2 can open with a password (`PdfDocument(path, password=…)`).

### B8. A broken draft fails on every launch
- **Why:** If `draft.json` is corrupt or the private PDF is missing,
  `restore()` throws on every cold start and the same error repeats
  forever. A transient render failure (OOM on a huge page) takes the same
  path, so blind deletion would lose ink.
- **Where:** `document/DocumentStore.kt` (`restore`),
  `document/DocumentService.kt` (`restore`), `ui/EditorViewModel.kt` (`init`).
- **Approach:** Split the failure: JSON parse or missing source → rename to
  `draft.broken-<timestamp>.json`, keep at most one, and tell the user;
  render failure → keep the draft, restore with `preview = null` (the
  blank-page path from #13) and retry the render.
- **Acceptance:** Robolectric: corrupt JSON restores to an empty editor with
  one message and no message on the next launch; a draft whose render
  throws still restores its ink.

### B12. Remaining hard-coded strings
- `InkPageView` content description, `"Document.pdf"` fallback in
  `DocumentStore.import`, the `require` messages left in `DocumentService`.
  Move to `strings.xml`; the view needs a `Context` string lookup.

### B14. Layout math inside `onDraw`
- `InkPageView.onDraw` still computes `pageRect` (and, before #11, clamps
  pan). Touch mapping uses `pageRect` from the last frame. Move page layout
  into `computeLayout()` called from `onSizeChanged`, `show` and gesture
  handlers; `onDraw` only reads it. Low risk once #10 and #11 are merged.

### B15. Naming slip in export
- `stream.setLineJoinStyle(ROUND_CAP)` in `PdfEngine.export` works because
  both constants are `1`. Add `ROUND_JOIN`.

### B17. Live highlight draws above pen ink until the pen lifts
- **Why:** after #18 committed highlights sit under pen ink, but the stroke
  being drawn is painted last, over the pen layer, and drops under it on
  lift. A small visible jump when highlighting across handwriting.
- **Where:** `ui/InkPageView.kt` (`onDraw`: the `liveStroke` block).
- **Approach:** when `activeKind == HIGHLIGHTER`, draw the live path inside
  the pre-layer block (hardware path) or before the pen pass (software
  path), instead of after `drawCommittedInk`.
- **Acceptance:** extend `highlightsSitUnderPenInkWhateverTheirOrder` with a
  live stroke (DOWN/MOVE without UP) crossing a committed blue stroke; the
  crossing pixel stays blue.

---

## 3. Backlog: general

- **G1. Apache NOTICE and licences screen.** The APK ships AndroidX licence
  files but no PDFBox or BouncyCastle NOTICE, and there is no licences
  screen; Apache-2.0 §4(d) asks for NOTICE text with binaries. Add an
  "Open-source licences" entry (static asset listing PDFBox-Android,
  BouncyCastle, AndroidX) reachable from an overflow or long-press on the
  title. Verify with `unzip -l` on the release APK.
- **G2. Multiple documents** (see F9).
- **G3. Close and clear** (see F12).
- **G5. `dataExtractionRules`.** Lint flags `allowBackup=false` without
  `android:dataExtractionRules` (Android 12+ still does device-to-device
  transfer). Decide explicitly (probably exclude `files/documents` and the
  `pen` preferences from cloud backup, allow D2D) and add
  `res/xml/data_extraction_rules.xml`; keep README's backup sentence true.
- **G6. Cache page boxes in the draft.** `DocumentService.restore` reloads
  the whole PDF with PDFBox only to recompute `PageSpec`s on every cold
  start. Store them in `draft.json` (with a schema version) and fall back to
  `inspect` when absent. Measure cold start before and after.
- **G7. One-shot events.** `EditorState.message` (and `shared` in #15) must
  be acknowledged manually. Replace with a single-consumer event channel
  (for example a `Channel`/`SharedFlow` or an event queue in the model) so
  state and effects stop mixing as the state grows.

---

## 4. Backlog: performance

- **P5. Draft writes are O(total ink).** Even coalesced (#13), each write
  serializes all pages twice (`ink` and `savedInk`) with `org.json` boxing.
  Options, simplest first: store `savedInk` as a revision marker instead of
  a copy; write one file per page and rewrite only the changed page; or an
  append-only journal compacted on export. Acceptance: a benchmark test with
  5k strokes shows per-stroke write time independent of other pages.
- **P6. Heavy exports.** Every pen segment is its own `w m l S` path. Quantize
  widths (for example 1/16 of the nominal width) and emit runs of equal-width
  segments as one polyline, using the same quantized geometry on screen to
  keep preview == export. Update `InkIncrementalTest`'s reference
  deliberately (see AGENTS.md). Acceptance: content-stream bytes for a
  200-stroke page drop by more than 5x; PDFium proof still aligned.
- **P8. Sharp zoom.** Previews are capped at 2048px, so text softens at high
  zoom. After zoom or pan settles (~150 ms), render only the visible region
  at screen resolution (`PdfRenderer.Page.render` with a transform and
  clip) on the worker and draw it over the base bitmap. Must go through the
  single worker and respect #13's staleness checks.
- **P9. Lower latency.** `androidx.input:input-motionprediction` to draw a
  predicted, never-committed tail; later `androidx.graphics:graphics-core`
  front-buffered rendering for the live stroke. Verify on a device with a
  high-speed camera or `adb shell dumpsys gfxinfo`.

---

## 5. Backlog: features

- **F3 (rest). Navigation.** A thumbnail strip (bottom sheet) that marks
  pages carrying ink, and a fit-width mode for landscape tablets. Thumbnails
  can reuse #13's renderer at a small size on the worker.
- **F6. Quick re-save.** Remember the last export URI (take a persistable
  permission from `CreateDocument`) and offer "Save" next to "Save as…";
  optionally overwrite the original when the provider granted write access.
- **F7. Editable export.** Option to write standard `/Ink` annotations
  instead of page content, so recipients can hide or delete them and
  Asterinked can re-import them for editing. Keep "burn in" as the other
  option. Note: then `canModifyAnnotations` rather than `canModify` is the
  relevant permission, which would admit more restricted PDFs.
- **F8. Keyboard shortcuts** for tablets and Chromebooks: Ctrl+Z,
  Ctrl+Shift+Z / Ctrl+Y, Ctrl+S, Ctrl+O, PgUp/PgDn/arrows. Override
  `MainActivity.onKeyShortcut`/`onKeyDown`; test with Robolectric key events.
- **F9. Multiple documents.** A recent list with one draft per document and
  a "continue where you left off" card on the welcome screen. Needs a draft
  index and per-document directories in `DocumentStore`.
- **F11. Password prompt** (see B2).
- **F12. Clear page / close document.** Clear page as one undoable edit
  (`InkHistory.record`); close document returns to the welcome screen after
  the unsaved-notes prompt.
- **F13. Page tools.** Insert a blank note page after the current page;
  "add margin" by widening the crop and media box on the right. Both are
  PDFBox edits applied at export plus a page list change in the draft.
- **F14. Pressure curve.** Width is linear in pressure with a 0.4pt floor
  (`InkGeometry.widthAt`), a 5.5x range on Medium, so light writing looks
  spidery. Add a gamma curve with a higher floor and one sensitivity slider.
  This changes export geometry: update the pinned reference in
  `InkIncrementalTest` on purpose.
- **F15. Text notes.** Typed sticky notes as `/Text` annotations for
  comments that must be legible.

---

## 6. Backlog: visual and UX

- **V6 (rest). Coach marks.** #16 replaces the permanent hint row with a
  toast on mode change; a one-time first-run coach mark for swipe, pinch and
  double-tap would help new users.
- **V8. Dark theme.** Dark chrome with a white page for night reading
  (the page itself stays white; see D-night for inverted pages).
- **V9. Landscape fit-width** (with F3).
- **V10. Page pill auto-hide.** From #16: the floating pill covers the bottom
  centre of the page; strokes that pass under it work, but one cannot start
  inside it. Fade it out while the pen writes and after ~3 s of inactivity;
  bring it back on page change or a tap near the bottom edge. Needs a
  "writing started/ended" callback from `InkPageView`.
- **V12. Phone tool strip overflow.** With #18's highlighter and eraser
  segment the strip is wider than a 411dp phone; the width dots need a
  scroll. Options: move the widths into a popover on the selected swatch, or
  wrap into two rows below ~480dp. Verify with the Robolectric screenshot
  approach (T4) at `w411dp`.
- **V13. Highlight width dots are faint.** The width dots preview the ink
  colour, so light highlight tints (yellow on the white strip) barely show.
  Give dots a 1dp `ICON`-coloured outline when the fill is light (luminance
  above ~0.7), in `ChoiceDot`.
- **V14. Phone top bar crowding.** Open, undo, redo, share and the Save copy
  pill leave about 100dp for the title on a phone. Candidates: fold Share
  into a split Save button or an overflow menu below 480dp.
- **V11. Status dot.** A small filled dot beside the title for unexported
  notes, instead of (or in addition to) #16's red status line.
- **U1. Auto pen-only.** After the first stylus event, stop fingers from
  inking (palm safety) and say so once. Today touch-ink mode lets a resting
  palm draw.
- **U2. Hover cursor.** On styluses that report hover, draw a ring of the
  current colour and width where the nib will land (`onHoverEvent`).
- **U3. Gesture shortcuts.** Two-finger tap = undo, three-finger tap = redo.
  Must coexist with the pinch detector (#11).
- **U4. Export feedback.** After saving, a snackbar with "Open" and "Share"
  instead of a plain toast.
- **U5. Busy states.** Export disables everything including pan and zoom.
  Keep navigation available and show determinate progress for large
  documents.
- **U6. Undo scope.** Undo is per page; after turning the page the last edit
  elsewhere is out of reach. Consider global chronological undo that jumps
  back to the page, or at least show where the next undo applies.
- **U7. Haptics.** A light tick on page turn, undo/redo and tool switch.

---

## 7. Delightful and quirky ideas

- **Ink-drawn asterisk loader:** the loading and welcome screens draw the red
  asterisk stroke by stroke with the app's own pressure taper.
- **Review stamps:** a palette (✓ ✗ ? ! ✱) placed with one tap at pen size.
- **QuickShape:** hold the pen still at the end of a stroke to snap it into a
  straight line; auto-straighten highlighter strokes horizontally.
- **Text-snapping highlighter:** use PDFBox text positions to snap a
  highlight to the glyph boxes of the line under it.
- **Scribble to erase:** a fast zig-zag over strokes deletes them.
- **Lasso:** select strokes, then move, recolour or delete.
- **Session replay:** store timestamps per point and play back how a review
  was written.
- **"Next note" button** and an optional summary page listing annotated
  pages, appended on export.
- **Focus mode:** hide all chrome, tap the left or right edge to turn pages.
- **Page-flip micro-animation** on page turn.
- **Night reading (D-night):** render pages through an inverting colour
  matrix, adapt ink colours on screen only; exports stay normal.
- **Volume keys turn pages** (e-reader convention), opt-in.
- **Eyedropper:** pick ink colour from the document.
- **Margin magnifier:** a zoomed writing strip for small notes without
  zooming the whole page.

---

## 8. Tests, tooling and deferred review follow-ups

G8 (test gaps) is partly done: #11 tests gestures, #12 the error mapping,
#13 adds the `DocumentOperations` fake. The rest is T3, T5 and T6.

- **T1.** `verify_pdf.py`: assert that plain fixtures' *sources* are
  unencrypted before blaming an export (deferred from #12's review).
- **T2.** `EditorViewModelPagingTest`: pin the prefetch order (previous page
  before next page, so the next page survives a one-preview cache; deferred
  from #13's review), for example with a size-limited fake cache.
- **T3.** Model tests for undo/redo and erase through `EditorViewModel` once
  #13's `DocumentOperations` seam and #14's `InkHistory` are both on `main`
  (only `InkHistory` itself is tested today).
- **T4.** A screenshot test harness: Robolectric `@GraphicsMode(NATIVE)`
  renders of the welcome, phone and tablet editor, compared against golden
  images with a tolerance, so UI regressions show in CI.
- **T5.** `EditorViewModelTest` busy-waits with `Thread.sleep`; move it to
  #13's queue executor.
- **T6.** An end-to-end test through `DocumentService`: import a fixture,
  add ink, export, re-open the export as a new document, export again, and
  assert the second export carries exactly one layer of ink (only
  `PdfEngine` covers repeated exports today).
- **T7.** `MainActivityChromeTest.penSettingsSurviveARestart` clicks the
  swatches while they are hidden on the welcome screen. Add a variant that
  injects an open document first and asserts the strip `isShown` before
  clicking (deferred from #16's review).

## 9. Device verification checklist

Robolectric cannot run `PdfRenderer`, hardware canvases, real styluses or
launchers. Before a release, check on a device (ideally one with an active
stylus, for example a Samsung S Pen tablet):

- Writing latency and smoothness on a page with 200+ strokes (#10), including
  panning while writing.
- Pinch, two-finger pan in touch mode, double-tap zoom, swipe page turns, and
  a palm resting and sliding off while writing (#11).
- Page turns feel instant after the first render; memory stays bounded while
  flipping through a 300-page PDF (#13).
- Stylus eraser end and side button erase (#14); the eraser ring size.
- Open a PDF from Files, Gmail and a browser download; share to Gmail and
  Drive; rotate during the unsaved-notes prompt (#15).
- Themed and adaptive launcher icon; 40–44dp tool targets; page pill over the
  page (#16).
- Highlights look the same on screen and in Acrobat or Chrome's PDF viewer
  (#17), including a highlight drawn across earlier pen notes (under the
  ink in both, #18).
- An owner-restricted PDF exports and still refuses printing in Acrobat (#12).

---

## 10. Done

| ID | What | Where |
|---|---|---|
| B1 | Owner-restricted encrypted PDFs open and keep restrictions on export | #12 |
| B2 (message) | Clear message for password-protected PDFs | #12 |
| B3 | Plain-language errors instead of exception text | #12 |
| B4, B5 | Pan/pinch follow the fingers in both modes; no jump on lift | #11 |
| B6 | Disabled colour control looked enabled | #16 |
| B7 | Welcome flashed on cold start with a draft | #16 |
| B9 | "All notes exported" for a note-free document | #16 |
| B10 | Page margin in dp | #11 |
| B11 | Export name gained repeated "-annotated" | #15 |
| B13 | Stylus eraser end and side button | #14 |
| G4 | Single editor instance for incoming intents (`singleTask`) | #15 |
| P1–P3 | Ink rendering cost per frame, per edit, per sample | #10 |
| P4, P7 | Page turns and preview aliasing | #13 |
| F1 | Eraser with undoable edits | #14 |
| F2 | Highlighter | #17 |
| F3 (part) | Swipe, double-tap zoom, zoom kept across pages, jump to page | #11, #16 |
| F4, F5 | Open with / share to Asterinked; share annotated copy | #15 |
| F10 | Remember pen settings | #16 |
| B16 | Export drew highlights over earlier pen ink while the screen drew them under | #18 |
| V1–V5, V7 | Compact chrome, inline swatches, modern controls, brand icon and palette, clear mode toggle | #16 |
