# Asterinked fresh review (2026-10-06, main @ d48a41d)

Second pass over the whole codebase after the v0.2.0 redesign (#10–#23). The
existing ANALYSIS.md backlog is accurate; this file adds what a fresh read
found on top of it, then picks the implementation batch. IDs reuse ANALYSIS.md
ones where the item is already tracked; new ones are N-prefixed.

## New findings

### Bugs / correctness

- **N1. Export succeeds, draft save fails → wrong error.** `EditorViewModel.export`
  runs `service.export` then `service.saveDraft(saved)` in one worker block; if
  the write fails the user sees "couldn't store" even though the PDF was saved,
  and the status stays "Unexported notes". Split: apply `saved` state after a
  successful export, treat the draft-write failure as the (already handled)
  background-save path. Low priority, confusing message only.
- **N2. `onKeyShortcut` swallows keys it doesn't act on.** MainActivity returns
  true even when the target button is hidden or disabled, so Ctrl+Z on the
  welcome screen eats the event instead of passing it on. Return false when
  nothing was clicked. Trivial.
- **N4. Restore is brittle to older drafts.** `DocumentStore.restore` uses
  `getJSONObject("savedInk")`; a draft written before `savedInk` existed (or
  hand-trimmed) throws and hits the B8 path. `optJSONObject` + empty default.
  Fold into B8.
- **N14. Rotation after Share loses the sheet.** `state.shared` is acknowledged
  immediately; after rotation the new observer gets `shared == null`, so the
  user must tap Share again (a second full export). Keep the pending share
  across recreation, or re-launch from `onCreate` when a shared file exists.
- **N20. Fling page-turn lets the page wiggle first.** At fit zoom the pan
  clamp still allows the 12dp margin, so a horizontal swipe visibly drags the
  page a few dp before the fling fires. Suppress pan while a fit-zoom
  horizontal swipe is in progress, or clamp pan to 0 at zoom == 1 for the
  margin axis. Cosmetic.

### Performance

- **N6. Eraser cost per sample is O(strokes × probes).** `eraseAlong` refilters
  `strokes` for every historical sample and `InkEraser.hits` re-probes every
  stroke per step; bounds cache helps, but with thousands of strokes a fast
  erase gesture can stutter. Cheap fix: build the candidate list once per
  MotionEvent (not per historical sample), skip strokes already in `erasing`
  inside `hits` instead of pre-filtering. (P10-adjacent.)
- **N17. `InkStrokeBuilder.add` allocates a tail list per sample.** Fine at
  120 Hz input; noted only because P10 already tracks the highlighter variant.

### UX / visual

- **U7 rest / N25.** Page turns and undo/redo still have no haptics; the tool
  bar ticks. One `CLOCK_TICK` on page turn and `CONFIRM` on undo would match.
- **N26. No onboarding for gestures.** V6 tracks coach marks; cheap 80%
  version: the input hint notice already exists — show it once on first
  document open (not only on mode toggle) via a `seenHint` pref.
- Existing backlog confirms: V10 (pill auto-hide), U1 (auto pen-only), U3
  (2/3-finger tap undo/redo), U4/U8 (notice actions), B8 (broken draft loop),
  B12/B14/B15/B19/B20 (small fixes), G5 (dataExtractionRules), G6 (cache page
  boxes), P5/P6 (draft writes, export size), F12 (clear page/close doc).

### Delight candidates (new)

- **D-recent.** Welcome screen "continue where you left off" is F9; until then,
  the restore already does this silently — a one-line "Restored <name>" notice
  on cold start would make the behavior discoverable.
- **D-zen.** Focus mode (hide bars, edge taps turn pages) is one
  `showScreen`-style state away; pairs with V10's pill auto-hide.

## Implementation batch (this session)

Picked for value × isolation (disjoint files, so PRs don't conflict):

1. **B8+N4 — broken draft recovery** (`document/*` + tests). Corrupt/missing
   draft is renamed aside once instead of erroring every launch; render
   failure keeps the ink and retries the preview.
2. **B19+B15+B20 — small review cleanups** (`InkPageView`, `PdfEngine`,
   `DocumentStore` read path, test locale). Sanitize highlighter width on
   restore/draw/export; ROUND_JOIN; cancel zoomAnimator on detach; fix the
   followFingers comment.
3. **G5 — dataExtractionRules** (manifest + xml). Keeps README's "not in
   backups" claim true for device-to-device transfer on Android 12+.
4. **U3 — two-finger tap = undo, three-finger tap = redo** (`InkPageView` +
   callbacks + gesture tests). Ships after #2 to avoid InkPageView conflicts.

Deferred deliberately: B2/F11 password prompt (needs product decision on
password UX), F7 editable export (format decision), P8 sharp zoom (device
verification needed), B14 layout refactor (touches the same onDraw as U3).
