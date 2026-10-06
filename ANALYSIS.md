# Asterinked analysis

Working backlog for Asterinked (pen annotation of PDFs on Android). It is
written for the next person or agent picking up work: every open item says
why it matters, where the code is, a concrete approach and how to prove it.
Read `AGENTS.md` first for build, CI and repo conventions.

Baseline: `main` at v0.2.0 (`333fa4b`), reviewed file by file on 2026-09-26.
The CI gate (`./gradlew testDebugUnitTest lintDebug assembleDebug` plus
`scripts/verify_pdf.py`) is green there. The PRs in section 1 were merged
on 2026-09-27; the backlog below applies to `main` after that merge.

Second pass (SWE-2 Max), 2026-10-06, at `main` = `d48a41d` (the #23
redesign). Every backlog item was re-verified against the code — all are
still open — and the pass added the N-prefixed findings folded into each
section below. Items tagged *(in flight: #N)* have an open PR from that
pass; leave them alone until the PR merges or closes.

IDs (B = bug, G = general, P = performance, F = feature, V = visual,
U = UX, D = delight, T = tests and tooling, N = second-pass findings) are
stable; keep them when updating this file, and move finished items to the
"Done" table instead of deleting them silently.

**Parallel work warning.** Several agents ran this same review in
2026-10-06; dozens of PRs beyond the ones listed below cover overlapping
items (quick re-save, hover cursor, loader animation, pill auto-hide,
draft recovery, eraser fixes, gesture undo…). Before starting any item,
check the open PR list for an existing attempt.

---

## 1. Status: merged pull requests

Eight focused PRs, each cut from `main`, each green in CI and reviewed by
the GLM bot until no valid important findings remained, plus #18, which
combined all eight, and #19, a CI fix. All merged on 2026-09-27: #19 as
`39d9bd4`, then #10–#17 together through #18's merge commit `2896ba6`.

| PR | Branch | Covers | Summary |
|---|---|---|---|
| [#10](https://github.com/L-K-M/Asterinked/pull/10) | `claude/peaceful-darwin-twgnu4` | P1, P2, P3 | Committed ink is recorded once per edit into a `RenderNode` and replayed through a view-sized GPU layer; per-stroke geometry cache; incremental live-stroke smoothing (`InkStrokeBuilder`). `InkIncrementalTest` pins export geometry to v0.1.0. |
| [#11](https://github.com/L-K-M/Asterinked/pull/11) | `claude/gesture-fixes` | B4, B5, B10, B14 (partly), F3 (partly) | Pan and pinch follow the finger centroid in both modes; no jump on finger lift; pan clamped immediately; animated double-tap zoom; swipe to turn pages at fit zoom; zoom kept across page turns; 12dp page margin; a palm stays inert after the pen lifts. |
| [#12](https://github.com/L-K-M/Asterinked/pull/12) | `claude/pdf-compat-errors` | B1, B2 (message), B3, B12 (partly) | Owner-restricted encrypted PDFs open; exports are re-encrypted with the same permissions (AES for 128-bit). `DocumentProblem` maps failures to plain messages from `strings.xml`. PDFium proof gains an encrypted fixture. Deeply nested PDFs (a `StackOverflowError` in PDFBox's parser) are reported instead of crashing (B18). |
| [#13](https://github.com/L-K-M/Asterinked/pull/13) | `claude/instant-page-turns` | P4, P7, P5 (partly), G8 (partly) | One `PdfRenderer` per document, LRU preview cache with neighbour prefetch, instant page turns without the busy state, coalesced draft writes, mipmapped previews. `EditorViewModel` takes a `DocumentOperations` + executor (test seam). |
| [#14](https://github.com/L-K-M/Asterinked/pull/14) | `claude/eraser` | F1, B13 | Stroke eraser tool, stylus eraser end and side button; undo/redo via per-page `(before, after)` snapshots (`InkHistory`). |
| [#15](https://github.com/L-K-M/Asterinked/pull/15) | `claude/open-with-share` | F4, F5, G4, B11 | VIEW/SEND intent filters (`singleTask`), incoming PDFs wait until the editor is idle, Share via `FileProvider`, sanitized `Draft.exportName` (also fixes path traversal from hostile display names). |
| [#16](https://github.com/L-K-M/Asterinked/pull/16) | `claude/ui-refresh` | V1–V5, V6 (partly), V7, B6, B7, B9, F3 (jump to page), F10 | Compact icon top bar, tool strip with swatches and width dots, floating page pill (tap to jump, Fit), page shadow, red/slate palette, designed adaptive launcher icon, remembered pen settings. |
| [#17](https://github.com/L-K-M/Asterinked/pull/17) | `claude/highlighter` | F2 | Highlighter kind: constant-width path multiplied with the page on screen and in the PDF (`/BM /Multiply`); kind persisted backward-compatibly; PDFium fixture proves text stays dark and that the marker really covers text. |
| [#18](https://github.com/L-K-M/Asterinked/pull/18) | `claude/integration` | all of the above, B16, B17 | One-step merge of #10–#17 with every conflict below resolved, the features ported into #16's new chrome (highlighter and eraser toggles in the tool strip, Share icon in the top bar), and highlights (committed and live) drawn under pen ink on screen and in the export. |
| [#19](https://github.com/L-K-M/Asterinked/pull/19) | `claude/ci-cached-fixtures` | CI | `main` CI turned red on the docs-only ANALYSIS.md commit: `testDebugUnitTest` came from the build cache without writing the PDFium fixtures. The fixtures are now declared task outputs, so a cache hit restores them. |

### 1.1 How the PRs fit together

The eight overlapped heavily (22 conflicting pairs, mostly `InkPageView`,
`MainActivity`, `EditorViewModel`, `DocumentService`). #18 merged them in
this order (lowest churn first), and its merge commits show each
resolution:

1. **#12** errors and encryption (document layer).
2. **#13** page turns (reshapes `EditorViewModel`, adds the test seam).
3. **#10** ink rendering.
4. **#11** gestures.
5. **#14** eraser.
6. **#17** highlighter.
7. **#15** open with and share.
8. **#16** UI refresh last, because it rewrites `MainActivity`.

The semantic interactions below explain design choices in the merged code
that no single PR shows on its own:

- **#10 + #14:** the eraser hides strokes live by skipping them in the draw
  loop. With #10 the committed ink is a cached `RenderNode`, so every change
  to the erased set must mark the ink node stale (`inkNodeStale = true`) or
  the hidden strokes stay visible until the gesture ends.
- **#10 + #17:** multiply blending must not happen inside #10's compositing
  layer (multiply against the layer's transparent pixels yields opaque
  colour and hides the text). #18 draws highlights directly on the view
  canvas (paths cached per stroke) before the cached pen layer, so
  highlights, including the one being drawn, always sit under pen ink. The export and the software path
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

## 1.5 In flight from the 2026-10-06 pass

Five PRs, each cut from `main` at `d48a41d`, left open for human review:

| PR | Branch | Covers | Summary |
|---|---|---|---|
| [#34](https://github.com/L-K-M/Asterinked/pull/34) | `swe/notice-actions` | U8, U4 (Open), N1 | `NoticeBar` gained one action slot (a new `notice_action` colour, held 10 s). After export the notice offers *Open* (ACTION_VIEW with a read grant, Asterinked excluded from the chooser). Export success is now reported separately from the draft write, so a save failure can no longer mask a landed PDF. |
| [#38](https://github.com/L-K-M/Asterinked/pull/38) | `swe/quick-save` | F6 | `Draft.destination` remembers the export URI while a persistable write grant survives; the top-bar button becomes one-tap "Save" with long-press for "save a copy elsewhere"; Ctrl+S inherits it. |
| [#39](https://github.com/L-K-M/Asterinked/pull/39) | `swe/hover-cursor` | U2, N35 | `InkPageView.onHoverEvent` draws a ring at the nib's landing point (ink colour and width; eraser radius and colour for eraser), hidden while writing, for fingers, and on hover exit. |
| [#46](https://github.com/L-K-M/Asterinked/pull/46) | `swe/small-ux` | N2, U7, V6 (part) | Disabled or hidden shortcut targets now return `super` instead of swallowing the event; page turns and undo/redo tick `CLOCK_TICK`; the input-mode hint shows once on first editor open (`hintedGestures` pref). |
| [#53](https://github.com/L-K-M/Asterinked/pull/53) | `swe/asterisk-loader` | N41 (part) | `AsteriskLoader` replaces the loading `ProgressBar`: the brand asterisk strokes itself in over a faint ghost, looping only while on screen. The welcome-screen one-shot variant is still open. |

---

## 2. Backlog: bugs

### B2. Password prompt for protected PDFs (with F11)
- **Why:** PDFs that need a password are refused (after #12, with a clear
  message). Common for bank statements and HR documents.
- **Where:** `document/PdfEngine.kt` (`load`), `document/DocumentService.kt`
  (`open`), `ui/MainActivity.kt`.
- **Approach:** On `DocumentProblem.PASSWORD_PROTECTED`, prompt for a
  password and open with `PDDocument.load(file, password, …)`. On API 35+
  hand the password to `PdfRenderer` (`PdfRenderer.Params` accepts one) and
  keep it in memory only. Below API 35, `setAllSecurityToBeRemoved(true)`
  and save a decrypted private copy for `PdfRenderer`, deleted when the
  document is replaced, so plaintext at rest exists only on old devices.
  Re-encrypt the export with the user password and the original
  permissions (mirror `keepProtection` from #12). Never persist the
  password.
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
- Fold in **N25**: `restore` calls `getJSONObject("savedInk")`; a draft
  written before `savedInk` existed throws and takes the broken path too.
  `optJSONObject` + empty default keeps old drafts loadable.

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

### B19. Highlighter width is not sanitized
- Pen widths go through `InkGeometry.strokeWidth` (finite and positive,
  else the default) on screen and in the export; highlighter strokes use
  `stroke.width` raw in `InkPageView.drawHighlight` and `PdfEngine.highlight`.
  Today widths only come from `HIGHLIGHT_WIDTHS`, and `org.json` refuses to
  write NaN, but its parser accepts unquoted `NaN` and `Infinity` literals
  (it falls back to `Double.valueOf`), so a corrupted or hand-edited
  `draft.json` can carry them through a restore. Sanitize on the read path
  as well: apply `strokeWidth` when drawing and exporting highlights, and
  add a `DocumentStore` round-trip test with zero, `NaN` and `Infinity`
  widths (from #18's reviews).

### B20. Small cleanups found in review
- Cancel `zoomAnimator` in `InkPageView.onDetachedFromWindow` (#11's
  double-tap animation keeps invalidating a detached view for ~220 ms).
- `followFingers`' comment says two fingers pan in touch-ink mode, but the
  first finger on the page starts a stroke there; fix the comment or add a
  second-finger cancel (see U1).
- `PdfEngineSecurityTest.deeplyNestedPdf` formats xref offsets with the
  default locale; use `Locale.ROOT`.

### N1. Export-then-draft-save failure shows the wrong error *(in flight: #34)*
`EditorViewModel.export` ran `service.export` and `service.saveDraft` in one
worker block; a failed draft write reported "Couldn't store this document"
and kept "Unexported notes" even though the PDF landed. #34 splits them:
export success publishes `EditorState.exported` (an `Open` action on the
notice), and the draft write reports through its own message.

### N2. `onKeyShortcut` swallows shortcuts it does not act on *(in flight: #46)*
Hidden or disabled targets (e.g. Ctrl+S on the welcome screen) returned
`true`, eating the event. #46 returns `super.onKeyShortcut` instead; the
same fix applies to page-up/down in `onKeyDown`.

### N21. A stylus touch outside the page freezes finger gestures
A stylus `ACTION_DOWN`/`ACTION_POINTER_DOWN` sets `penGesture = true` even
when it lands in the margin outside `pageRect` and never starts a stroke;
`if (penGesture) return true` then makes every finger inert until all
pointers lift. Gate `penGesture` on the stroke actually starting (or on the
touch landing inside `pageRect`). Real on tablets; cosmetic edge case.

### N22. A second incoming PDF during the replace-prompt is silently superseded
`MainActivity.show()` captures `uri = incoming` when showing
`confirmReplacing`; if another PDF arrives while the dialog is up, "Open
another" still opens the older URI. Open `incoming` at confirm time, not
the captured one.

### N23. A stroke in progress is silently dropped when export/share starts
`InkPageView.show` cancels the live stroke when `state.busy` goes true —
the half-drawn stroke vanishes with no undo. Commit the stroke before
publishing busy, or leave it visible-but-uncommitted. Low priority; the
window is one tap.

### N24. Zoom and pan reset on rotation
`zoom`/`panX`/`panY` are view fields and the view is recreated on config
change, so a zoomed writing position is lost. Persist the transform in
`onSaveInstanceState` (per document/page key). Minor.

### N26. Fling page turn drags the page a few dp first
At fit zoom the pan clamp still allows the 12 dp page margin, so a
horizontal swipe visibly wiggles the page before the fling fires. Clamp
horizontal pan to 0 at zoom == 1, or suppress pan during a fit-zoom
horizontal swipe. Cosmetic.

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
  state and effects stop mixing as the state grows. The pattern keeps
  accruing fields — #34 added `exported` with its own `acknowledgeExport`.
- **G9. Re-encryption choices (#12).** Two decisions for `keepProtection`:
  (a) derive the key length from the security handler version (`V >= 5` →
  256-bit AES, `V == 4` → 128) instead of the optional top-level `/Length`,
  which some AES-256 producers omit (PDFBox then reports 40); (b) for an
  encrypted source that grants every permission, strip encryption instead
  of re-locking the copy behind a random owner password nobody knows. Both
  need a fixture per case in `PdfEngineSecurityTest` (from #18's review).

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
- **P10. Live highlighter path.** While highlighting, `InkPageView` rebuilds
  an `InkStroke`, its centerline and a `Path` from all points every frame.
  Append to one reusable `Path` per sample instead (mirroring
  `InkStrokeBuilder` for pens); only matters for very long highlights.
  (N29: `InkStrokeBuilder.add` also allocates the tail list per sample —
  fine at stylus rates, fix if P10's path gets shared.)
- **N27. Eraser probes are O(strokes × samples).** `InkPageView.eraseAlong`
  refilters `strokes` per *historical* sample and `InkEraser.hits` re-probes
  every remaining stroke per step (with a bounds cache). Thousands of
  strokes plus a fast erase gesture can stutter. Cheap fix: compute the
  candidate list once per MotionEvent instead of per sample.
- **N28. `inkLayer` re-rasterizes every frame during pan/zoom.** Deliberate —
  re-recording keeps ink vector-crisp — but on a 200+ stroke page the GPU
  redraws all segments into the layer texture per frame while panning.
  Acceptable today; if jank shows on device, rasterize the ink node once
  per zoom *level* instead of per transform (P8 supersedes anyway).

---

## 5. Backlog: features

- **F3 (rest). Navigation.** A thumbnail strip (bottom sheet) that marks
  pages carrying ink, and a fit-width mode for landscape tablets. Thumbnails
  can reuse #13's renderer at a small size on the worker.
- **F6. Quick re-save *(in flight: #38).*** Remember the last export URI
  (take a persistable permission from `CreateDocument`) and offer "Save"
  next to "Save as…"; optionally overwrite the original when the provider
  granted write access.
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
- **N36. PDF outline / bookmarks.** PDFBox exposes
  `documentCatalog.documentOutline`; when present, offer it in the page
  dialog or a sheet. Turns the app into a decent reader for long PDFs.
- **N37. In-document text search.** `PDFTextStripper` per page on the
  worker, jump to hits. Medium effort, large reader value.
- **N38. Pen set per tool kind.** Pen and highlighter share the four swatch
  positions and the width choice; a user who writes red/medium and
  highlights yellow/large flips both rows on every tool switch. Remember
  colour+width per `InkKind` — one more preference, big daily win.
- **N39. Zoom indicator / quick 100%.** A transient "250%" label during
  pinch (photo-editor convention) costs a `TextView` and a fade-out.

---

## 6. Backlog: visual and UX

- **V6 (rest). Coach marks.** #16 replaces the permanent hint row with a
  toast on mode change (a notice since #23); a one-time first-run coach mark for swipe, pinch and
  double-tap would help new users. Cheap half landed *(in flight: #46)*: the
  mode-appropriate hint now shows once on first editor open
  (`hintedGestures` pref). Remaining: real coach marks pointing at the
  gestures, not just a one-line notice.
- **V9. Landscape fit-width** (with F3).
- **V10. Page pill auto-hide.** From #16: the floating pill covers the bottom
  centre of the page; strokes that pass under it work, but one cannot start
  inside it. Fade it out while the pen writes and after ~3 s of inactivity;
  bring it back on page change or a tap near the bottom edge. Needs a
  "writing started/ended" callback from `InkPageView`.
- **U1. Auto pen-only.** After the first stylus event, stop fingers from
  inking (palm safety) and say so once. Today touch-ink mode lets a resting
  palm draw.
- **U2. Hover cursor *(in flight: #39).*** On styluses that report hover,
  draw a ring of the current colour and width where the nib will land
  (`onHoverEvent`).
- **U3. Gesture shortcuts.** Two-finger tap = undo, three-finger tap = redo.
  Must coexist with the pinch detector (#11).
- **U4. Export feedback *(Open in flight: #34).*** After saving, "Open" and
  "Share" actions on the "PDF saved" notice. #34 ships the Open action;
  Share stays open (the share code path exists but its grant applies to the
  draft-copy URI, not the export URI — worth checking).
- **U5. Busy states.** Export disables everything including pan and zoom.
  Keep navigation available and show determinate progress for large
  documents.
- **U6. Undo scope.** Undo is per page; after turning the page the last edit
  elsewhere is out of reach. Consider global chronological undo that jumps
  back to the page, or at least show where the next undo applies.
- **U7 (rest). Haptics *(in flight: #46).*** Tool, colour, width and
  finger-mode changes tick (#23); #46 adds `CLOCK_TICK` on valid page turns
  and undo/redo (invalid turns stay silent).
- **V16. Phone landscape.** At about 411dp tall the bars leave a page roughly
  250dp high (#23 lowers the top bar and moves the page pill to the corner).
  A side rail for the tools, keyed on height below ~480dp, or fit-width (V9)
  would give writing room back. See also N34 for the cheaper half.
- **U8. Notice actions *(in flight: #34).*** `NoticeBar` gains one action
  slot held ~10 s, used for Open on save and — via `MessageAction.SAVE_COPY`
  on draft-write failures — Save copy when notes could not be stored.
  Still open: Try again on failed saves.
- **N32. Page-aware TalkBack description.** `InkPageView`'s content
  description never says which page is showing. Set
  `contentDescription = "Page N of M"` in `show()`; the string must move to
  `strings.xml` to be formatted, which also chips at B12.
- **N33. Jump to annotated pages.** In a long document with scattered notes
  there is no way to find them. Cheap: long-press ◀/▶ to jump to the
  previous/next page carrying ink. Bigger: the F3 thumbnail strip marks
  inked pages.
- **N34. Auto-hide chrome while writing.** Phone landscape leaves ~250 dp of
  page height (V16 wants a side rail; V10 hides the pill). A smaller step
  covering both: hide *all* chrome while a stroke is active and restore it
  on lift. Needs the same "writing started/ended" callback as V10.
- **N35. Eraser size affordance** — the ring appears only on touch-down.
  Resolved for free by U2's hover cursor (#39) on pens that report hover;
  finger eraser still has no affordance.

---

## 7. Delightful and quirky ideas

- **Ink-drawn asterisk loader *(half in flight: #53):*** the loading screen's
  `AsteriskLoader` strokes the mark in over a ghost underlay. Remaining:
  the welcome screen playing the same animation once, ideally through
  `InkGeometry`'s pressure taper for a real pen feel (the loader uses
  straight eased strokes — swapping in the taper would be the polish).
- **Review stamps:** a palette (✓ ✗ ? ! ✱) placed with one tap at pen size.
  PDFBox's ZapfDingbats has the glyphs — export is one `setFont` +
  `showText`, the screen side is a `drawText` (N45).
- **QuickShape:** hold the pen still at the end of a stroke to snap it into a
  straight line; auto-straighten highlighter strokes horizontally.
- **Text-snapping highlighter:** use PDFBox text positions to snap a
  highlight to the glyph boxes of the line under it.
- **Scribble to erase:** a fast zig-zag over strokes deletes them. The hook
  is `InkStrokeBuilder` tail analysis on the finished stroke (N46).
- **Lasso:** select strokes, then move, recolour or delete.
- **Session replay:** store timestamps per point and play back how a review
  was written. Needs a `draft.json` schema version first — pair with G6 so
  the format changes land together (N40).
- **"Next note" button** and an optional summary page listing annotated
  pages, appended on export. N33 is the cheap version (long-press ◀/▶
  jumps between inked pages).
- **Focus mode:** hide all chrome, tap the left or right edge to turn pages.
- **Page-flip micro-animation** on page turn. ~8 dp translate+fade behind an
  `AnimatorDurationScale` reduce-motion check — trivial code (N42).
- **Night reading (D-night):** render pages through an inverting colour
  matrix, adapt ink colours on screen only; exports stay normal.
- **Volume keys turn pages** (e-reader convention), opt-in.
- **Eyedropper:** pick ink colour from the document. The preview `Bitmap`
  already lives in `InkPageView`; a long-press-on-swatch eyedropper is
  ~100 lines (N44).
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
- **T4 (rest).** #23's `UiScreenshotTest` renders every screen state to
  `app/build/reports/screens/`; compare them against golden images with a
  tolerance so UI regressions fail CI instead of waiting for a look.
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
- Hover the stylus: the ring tracks the nib, grows with the chosen width,
  switches to the eraser circle, and vanishes while writing (#39).
- The loading asterisk draws itself, holds, fades to the ghost and loops;
  it stops ticking once the editor is up (#53).

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
| B17 | The highlight being drawn sat above pen ink until the pen lifted | #18 |
| B18 | Deeply nested PDFs crashed the app with `StackOverflowError` and left the imported copy | #12 |
| CI | `main` CI failed on docs-only commits (cached tests skipped the PDFium fixtures) | #19 |
| V1–V5, V7 | Compact chrome, inline swatches, modern controls, brand icon and palette, clear mode toggle | #16 |
| V8, V11–V15 | Redesign: design tokens, dark theme, status dot, two-row phone tool bar, outlined light swatches, uncrowded top bar, 48dp targets, notices instead of toasts, edge-to-edge | #23 |

---

## 11. Additional review findings (Inkling, 2026-10-06)

Thorough review of `main` at `d48a41d` (post-#23). Focused on bugs, performance, visual/layout, aesthetics, and delightful ideas. Seven focused PRs opened; none depend on other agents' open PRs.

### Bugs / hygiene fixed in PRs

- **B15** (`bugfix/draw-highlight-width`, #45): `PdfEngine.export` used `ROUND_CAP` (value 1) for `setLineJoinStyle`; added `ROUND_JOIN` constant and corrected naming.
- **B19** (`bugfix/draw-highlight-width`, #45): `DocumentStore.decodeInk` and `PdfEngine.highlight` used raw `stroke.width`; now sanitized with `InkGeometry.strokeWidth()`.
- **B20** (`bugfix/draw-highlight-width`, #45): `InkPageView.onDetachedFromWindow` did not cancel `zoomAnimator`; fixed. `followFingers` comment corrected to match touch-ink behavior.

### Features implemented in PRs

- **D-1** (`feature/animated-loader`, #48): `InkLoaderView` replaces static welcome asterisk with progressive ink drawing (`Paint` with `accent` color, round caps, 6dp width).
- **D-2** (`feature/quick-resave`, #47): `DocumentStore` stores previous `CreateDocument` URI (`SharedPreferences`). `MainActivity` quick-saves to it when available; falls back to picker.
- **D-3** (`feature/night-mode`, #49): `InkPageView.setNightMode()` applies inverted `ColorMatrixColorFilter` to screen drawing only (`preview`, `highlighter`, pen `paint`). Toggle in tool bar.
- **D-4** (`feature/focus-mode`, #51): Long-press on `fit` button hides `topBar`, `toolBar`, and `pagePill`; workspace expands to full canvas.
- **D-5** (`feature/page-flip`, #52): Brief 180ms fade transition (`alpha` animation) in `InkPageView.show()` on page change.
- **U8** (`feature/action-notice-bar`, #54): `NoticeBar.show()` accepts optional `actionLabel` and `action` callback; `MainActivity` passes `"Save copy"` for `notes_not_saved` errors.

### Remaining shovel-ready backlog (preserved for future agents)

- **B2**: Password prompt for protected PDFs (`DocumentProblem.PASSWORD_PROTECTED` → prompt, `PDDocument.load` with password, `PdfRenderer.Params` for API 35+, `setAllSecurityToBeRemoved` for older devices).
- **B8**: Broken draft recovery (`draft.broken-<timestamp>.json` with message; render failure keeps draft with `preview = null`).
- **B12**: Hard-coded strings (`InkPageView` description, `DocumentStore.import` fallback) should move to `strings.xml`.
- **B14**: Move `pageRect` computation from `onDraw` to `computeLayout()` (called from `onSizeChanged` and gesture handlers).
- **G1**: Apache NOTICE / licences screen (`res/xml/shared_files.xml` reference, static asset listing PDFBox-Android / BouncyCastle / AndroidX).
- **G5**: `dataExtractionRules` (`res/xml/data_extraction_rules.xml`) excluding `documents` and `pen` preferences from cloud backup.
- **G6**: Cache `PageSpec` in `draft.json` (schema version) instead of reloading PDFBox on every cold start.
- **G7**: Replace `message` / `shared` with single-consumer event channels (`SharedFlow` / event queue in model).
- **G9**: Derive AES key length from `StandardProtectionPolicy` version (`V >= 5` → 256-bit, `V == 4` → 128); strip encryption for fully unrestricted PDFs instead of re-locking.
- **P5**: Per-page draft writes; append-only journal or revision marker instead of full `savedInk` copy.
- **P6**: Quantize pen segment widths; emit polyline runs; update `InkIncrementalTest` reference.
- **P8**: Sharp zoom: render visible region at screen resolution (`PdfRenderer.Page.render` with clip) after zoom settles.
- **P9**: `androidx.graphics:graphics-core` front-buffered rendering; motion prediction (`input-motionprediction`).
- **P10**: Reuse `Path` for live highlighter instead of rebuilding every frame.
- **F3 (rest)**: Thumbnail strip (bottom sheet) marking pages with ink; reuse `PdfRenderer` at small size.
- **F6 (rest)**: Full quick-resave with last URI persistence; overwrite original when provider grants write access.
- **F7**: Editable export (`/Ink` annotations option); keep "burn in" as alternative.
- **F8**: Keyboard shortcuts (`Ctrl+Z`, `Ctrl+Shift+Z` / `Ctrl+Y`, `Ctrl+S`, `Ctrl+O`, `PgUp`/`PgDn`).
- **F9**: Multiple documents with draft index and per-document directories in `DocumentStore`.
- **F11**: Full password prompt (see B2).
- **F12**: Clear page (`InkHistory.record` undoable edit); close document (welcome after unsaved-notes prompt).
- **F13**: Page tools: insert blank note page; "add margin" by widening crop/media box.
- **F14**: Pressure curve customization (gamma, sensitivity slider) — changes `InkGeometry.widthAt`; update `InkIncrementalTest` reference.
- **F15**: Text notes (`/Text` annotations) as typed sticky notes.
- **V6**: One-time coach marks for swipe, pinch, double-tap.
- **V9**: Landscape fit-width mode (`fit-width` for tablets).
- **V10**: Page pill auto-hide while writing; fade out after ~3s inactivity; bring back on page change or bottom-edge tap.
- **U1**: Auto `PEN` mode switch after first stylus event in `TOUCH` mode.
- **U2**: Stylus hover cursor (`MotionEvent.ACTION_HOVER_MOVE`) — ring of current color/width.
- **U3**: Gesture shortcuts (`ScaleGestureDetector` coexists with two-finger tap undo / three-finger tap redo).
- **U4**: Export feedback: "Open" / "Share" actions on saved notice.
- **U5**: Busy states: disable controls during export; determinate progress for large documents.
- **U6**: Undo scope: global chronological undo (jumps back to page) or at least show next undo page.
- **U7**: Haptics for page turns and undo/redo (`HapticFeedbackConstants`).
- **V16**: Phone landscape side rail (`screenHeightDp < 480`) or fit-width (`V9`).
- **D-night (expanded)**: Full dark-theme redesign (`values-night/colors.xml`, adaptive launcher colors, theme-aware shadows).
- **D-stamps**: Review stamps (`✓ ✗ ? ! ✱`) at pen size.
- **D-shape**: QuickShape — snap straight line at end of stroke.
- **D-snap-highlight**: Highlight snaps to PDFBox text glyph boxes.
- **D-scribble-erase**: Fast zig-zag over strokes deletes them.
- **D-lasso**: Select strokes, then move/recolour/delete.
- **D-replay**: Store timestamps per point; play back review writing.
- **D-next-note**: "Next note" button; optional summary page on export.
- **D-margin-mag**: Margin magnifier for fine notes.
- **D-night-full**: Full theme redesign (see V-night / D-night expanded).
- **D-volume-keys**: Volume keys turn pages (opt-in).
- **D-eyedropper**: Pick ink colour from document.
- **D-focus-deep**: Deeper focus mode (hide status bar, use edge taps for navigation).

### Implementation notes for future agents

- All seven PRs (`bugfix/draw-highlight-width`, `feature/quick-resave`, `feature/animated-loader`, `feature/night-mode`, `feature/focus-mode`, `feature/page-flip`, `feature/action-notice-bar`) branch from `d48a41d` (current `main`) and are independent.
- `bugfix/draw-highlight-width` fixes hygiene items (B15, B19, B20) that other agents may have partially addressed; keep it in sync with any `InkGeometry` algorithm changes.
- `feature/quick-resave` uses `SharedPreferences` key `"export"` rather than per-document storage; migrate to `DocumentStore` directory when implementing F9 (multiple documents).
- `feature/night-mode` applies a screen-only `ColorMatrixColorFilter`; it does not modify theme tokens (`colors.xml`). A full dark-theme redesign should update `values-night/colors.xml` and adaptive launcher colors.
- `feature/animated-loader` creates `InkLoaderView`; future work could extend it to play during slow `PdfRenderer` loads (not just the welcome screen).
- `ANALYSIS.md` is the source of truth; `tmp.md` should stay discarded.
