# Asterinked analysis

Working backlog for Asterinked (pen annotation of PDFs on Android). It is
written for the next person or agent picking up work: every open item says
why it matters, where the code is, a concrete approach and how to prove it.
Read `AGENTS.md` first for build, CI and repo conventions.

Full-review baseline: `d48a41d`, 2026-10-06, consolidated from `tmp.md`.
Main has since advanced to `d9a9bef`; its ANALYSIS delta and verified source
facts are folded in below. Other PRs and their reviews were not inspected.
This is not a full code/device re-review of that main revision. Earlier baseline:
v0.2.0 (`333fa4b`), reviewed file by file on 2026-09-26 with green
tests/lint/debug/PDFium checks. Section 1 retains the 2026-09-27 history;
branch implementations remain distinct from main.

### Evidence and limits

- Reviewed document reliability, ink/input/rendering and UI/accessibility
  sources at `d48a41d`. Baseline JVM tests, lint, debug APK and R8 release APK
  passed locally. The fresh isolated checkout restored the full test result
  from Gradle's cache; `UiScreenshotTest` then ran explicitly.
- PDFium independently passed all six export fixtures: four rotations,
  owner-restricted encryption and multiply-blended highlighting.
- Inspected all 21 generated screen states: narrow phones, compact landscape,
  tablets, RTL, 200% text, dark theme, dialogs, errors and busy states.
- No physical device was connected. Stylus latency, hardware `RenderNode`
  rendering, palm rejection, real document providers, launchers, IME and
  TalkBack/accessibility-service behavior remain unproved.
- Lint includes missing extraction rules, library trust-manager warnings and
  optional KTX suggestions. The APK requests no network permission; those
  library warnings alone do not establish an exploitable network defect.
- Shared-checkout EOF/timeout and transient review-toolchain root-disk
  pressure are environment failures, not app bugs. The isolated baseline
  succeeded.

Preserve the quiet, page-first neutral/red design, dark chrome, 48dp targets,
private draft recovery and vector export. Correctness and writing space
take precedence over adding permanent controls.

IDs (B = bug, G = general, P = performance, F = feature, V = visual,
U = UX, D = delight, T = tests/tooling, R = unverified risk,
N = additional October findings) are stable. D-1 through D-5 and the other
October aliases are retained in section 11, not new competing backlogs.
Keep them when updating this file; move finished items to the "Done" table
instead of deleting them silently.

---

## Current main delta: `d9a9bef`

- **Build repaired on main:** `829fca1`/`920e4e5` did not compile because
  `NoticeBar.WRAP` was undefined. #55 (`aa36354`) supplied a verified one-line
  repair, carried as a shared dependency in the eight feature branches.
  Main `d9a9bef` now contains the equivalent repair. #55 itself remains open;
  no further implementation is needed for this compile defect.
- **U8 partly on main:** NoticeBar's optional action slot and MainActivity's
  storage-error Save copy callback now exist. Do not implement them again.
  Control timeouts, callback re-entry, export destination identity, theming
  and 48dp verification remain separate follow-ups below.
- **Locale/effect fragility:** MainActivity identifies storage failure with
  English `contains("couldn’t be stored")`. This source fact belongs to
  B-storage-recovery-effect/G7, not to an unverified external review claim.
- All nine implementation PRs remain open. Application changes use those
  branches; only this consolidated backlog is published directly to main.

## Current status: eight implementations awaiting human merge

All eight implementations exist on separate branches. PRs remain open for
human review and merging; none is merged or main-done. Do not duplicate this
work. Every listed head passes local full tests, lint, debug/release builds,
PDFium and CI. Completed reviews have no unresolved applicable important
findings. Verification snapshot: 2026-10-06 23:11 UTC. Each latest change has
a clean Sol peer review and completed GLM review; #30–#44 also completed two
GLM rounds on distinct revisions. #50/#55 completed one full GLM round.
Acceptance below remains the merge contract, not hardware validation.

| Implementation | Covered IDs | PR | Branch/head | CI/review |
|---|---|---|---|---|
| Process-owned document worker | B-session-worker | [#40](https://github.com/L-K-M/Asterinked/pull/40) | `sol61/document-worker-lifetime` / `f8a1a9b` | CI pass; Sol + 2 GLM rounds; steady, open |
| Checked draft commits | B-atomic-commit | [#42](https://github.com/L-K-M/Asterinked/pull/42) | `sol61/checked-draft-commits` / `8a25a3a` | CI pass; Sol + 2 GLM rounds; backup claims refuted; steady, open |
| AES-strength preservation | G9(a) | [#33](https://github.com/L-K-M/Asterinked/pull/33) | `sol61/pdf-aes-strength` / `afff2ee` | CI pass; Sol + 2 GLM rounds; RC4/constant claims refuted; steady, open |
| Continuous rendered-geometry eraser | B-eraser-geometry, B-eraser-sweep, P-eraser, N27 | [#43](https://github.com/L-K-M/Asterinked/pull/43) | `sol61/precise-swept-eraser` / `6fd473f` | CI pass; cold duplication fixed; Sol + 2 GLM rounds; steady, open |
| Synchronous page transforms | B14 | [#50](https://github.com/L-K-M/Asterinked/pull/50) | `sol61/synchronous-page-layout` / `612ced7` | CI pass; cleanup finding fixed; clean Sol + full GLM; steady, open |
| Delta-based undo history | P-history, T3 | [#44](https://github.com/L-K-M/Asterinked/pull/44) | `sol61/linear-undo-history` / `c154343` | CI pass; Sol + 2 minor-only GLM rounds; steady, open |
| Contrast-qualified swatches | V-swatch-contrast | [#30](https://github.com/L-K-M/Asterinked/pull/30) | `sol61/swatch-contrast` / `74c75f5` | CI pass; test feedback applied; Sol + 2 GLM rounds; steady, open |
| Ink-aware page navigation | D-next, F3 (note navigation), N33, R-page-counter-label | [#41](https://github.com/L-K-M/Asterinked/pull/41) | `sol61/annotated-page-navigation` / `c17027d` | CI pass; ownership finding fixed; Sol + 2 minor-only GLM rounds; steady, open |
| Equivalent build repair already on main | B-notice-wrap | [#55](https://github.com/L-K-M/Asterinked/pull/55) | `sol61/notice-layout-build` / `aa36354` | CI pass; clean Sol + full GLM; open; equivalent fix in `d9a9bef` |

Source paths below are relative to `app/src/main/java/ch/lkmc/asterinked/`;
line references are `d48a41d` evidence unless explicitly marked current-main.

### B-notice-wrap. Compile defect resolved on main
- **Evidence:** Reproduced `829fca1` compiler failure: unresolved
  `NoticeBar.WRAP`. This is an app build defect, unlike transient root-disk
  or review-toolchain failures.
- **Scope/status:** #55 at `aa36354`, shared repair carried by the other
  own branches. Equivalent fix landed on main as `d9a9bef`; no duplicate work.
- **Acceptance:** Tests, lint, debug/release builds and six-fixture PDFium proof
   pass on the repair branch. The dependency is verified on all eight feature
   branches. #55 remains open as requested; the main defect is already resolved.

### B-session-worker. Serialize document work across editor sessions
- **Evidence:** `ui/EditorViewModel.kt:52,194-204,258-262` owns a worker per
  model; clearing suppresses callbacks but queued writes survive. All use
  the same store files (`document/DocumentStore.kt:84-93`). A queues ink
  behind a slow render, finishes, B restores older disk state and saves,
  then A overwrites B or prunes B's source. `singleTask` is insufficient.
- **Scope:** Process-owned serial document worker; B's restore follows A's
  writes and service close. A store mutex alone allows stale sequential
  overwrites. Preserve the `DocumentOperations` boundary.
- **Acceptance:** Controlled two-session test: clear A with queued work,
  launch/edit B, drain all work, restore B's source/ink; A emits no callbacks.

### B-atomic-commit. Check durability before pruning source PDFs
- **Evidence:** `document/DocumentStore.kt:84-93` treats `AtomicFile.finishWrite()`
  as checked success, but Android can log sync/close/rename failure instead.
  Replace A with B, fail final rename, prune A: restore discards `.new` and
  old JSON now references missing A. See the [Android implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/core/java/android/util/AtomicFile.java).
- **Scope:** Checked durable commit behind the store, observable success and
  throwing synchronization; prune only after success, retain legacy recovery.
- **Acceptance:** Inject write, sync, close and rename failures; previous
  JSON/source restore and replacement reports failure. Check API 29 and 35.
  Imported-source durability remains a separate unverified risk (R-import-durability).
  Checked file commits do not establish directory-fsync or sudden-power-loss
  guarantees; those require separate proof.

### G9(a). Preserve AES strength when top-level `/Length` is absent
- **Evidence:** `document/PdfEngine.kt:158-166` uses `PDEncryption.length`;
  pinned PDFBox defaults missing `/Length` to 40 although its handler knows
  AESV2/AESV3 crypt filters. Both AES-128 and AES-256 can downgrade to RC4.
- **Scope:** Derive strength from handler version/crypt filter (`V == 5`:
  AES-256; `V == 4`: inspect the AES crypt-filter method); retain permission bits.
  G9(b)'s all-permissions policy is a separate open product decision.
  The pinned PDFBox exposes no public V5 constant; do not reference an absent
  API while expressing the handler-version rule.
- **Acceptance:** AESV2 and AESV3 sources without `/Length` retain AES strength,
  restrictions, readable content and visible ink after export; PDFium proof
  alongside `PdfEngineSecurityTest`, not PDFBox-only agreement.
  PDFBox's existing crypt-filter `/Length` bit-unit serialization quirk remains;
  fixture inputs use standard byte units. PDFium accepts the exports, but
  interoperability with stricter viewers still needs device/viewer proof.
  A proposed RC4-export regression is disproved: a valid PDFium-readable
  RC4-128 input without top-level `/Length` fails in PDFBox with
  `InvalidPasswordException` before export. Track that import compatibility
  issue as B-rc4-import, not as a failure of this AES export fix.

### B-eraser-geometry / B-eraser-sweep / P-eraser / N27. Erase visible swept ink
- **Evidence:** `ink/InkEraser.kt:27-46,51-54,66-67` tests raw sample lines and
  nominal width, unlike smoothed pressure geometry (`ink/Ink.kt:51-59,78`).
  `(0,0) -> (100,0) -> (100,100)`, width 2/radius 5, misses visible
  `(87.5,12.5)` but hits the empty raw corner `(100,0)`. Width 3.6 at pressure
  0.1 renders at 0.4, so nominal widths also remove untouched nearby ink.
- **Sweep proof:** Radius 10 moving `(0,0)` to `(20,0)` contacts a width-2 dot
  at `(5,10.9)`; probes at x=0/10/20 are about 11.99 away and miss. The
  256-step cap worsens long-move gaps. Historical samples allocate/rescan on
  the UI thread (`ui/InkPageView.kt:451-456`).
- **Scope:** Continuous capsule/segment tests on cached rendered geometry,
  sanitized constant highlighter width, conservative swept-bounds rejection
  once per candidate and reusable collections. Keep identity and export
  geometry unchanged; defer spatial indexing until measured need. Preserve
  `InkIncrementalTest`. The eraser sanitizes malformed marker widths, but
  end-to-end decode/display/export consistency still needs B19; valid widths agree.
  N27's per-historical-sample candidate/probe rescans are the same workstream;
  its earlier once-per-MotionEvent candidate-list proposal is absorbed here.
  Follow-up `3bcc097` reuses renderer-cached segments and a width override,
  without copying geometry; 162 tests pass. Exact enclosing near misses still
  scan linearly. Any accelerator requires physical-device evidence first.
- **Acceptance:** Both corners, pressure near misses, dots/highlight edges,
  equal-but-distinct identities, grazing/endpoints/just-outside misses and
  long thin-stroke crossings. Benchmark dense historical moves, hits and
  large enclosing near misses. Warm work is O(candidate strokes + surviving
  segments), independent of sweep length; enclosing near misses still scan.

**Measured JVM evidence, not device latency:** reported dense fixture has
1,000 strokes, 300,400 renderer-cached segments and 20% highlighter. JIT was
warmed; values are medians from a desktop JVM, not an Android device.

| Revision | Cold contact | Cold allocation | Warm 4-unit move | Warm 480-unit sweep |
|---|---|---|---|---|
| `d48a41d` raw/probed geometry | 1.29ms | 0.91MB | 0.17ms | 5.62ms |
| `22e3fcf` first exact implementation | 17.36ms | 38.62MB | 4.11ms | 3.83ms |
| `3bcc097` renderer-cache reuse, width override | 7.22ms | 60.6KB | 4.27ms | 4.04ms |

First-move raw-point reads fall from 60,700 to zero; reported warm allocation
is 1.4KB. A prior narrower 200-warm-sweep measurement on a 1,000-stroke
fixture reported sample allocation 8.4KB→1.4KB and timing 8.0ms→6.5ms; retain
it as historical protocol-specific evidence, not interchangeable with the
table's modes. The exact algorithm fixes misses/false hits, but is slower
than the old approximation in some modes. No blanket "eraser is faster" or
hardware frame-time/stylus claim follows from these JVM results.

### B14. Compute the page transform before input
- **Evidence:** `ui/InkPageView.kt:184,209-214,343,448-449,476-477` changes
  dimensions/pan/zoom before `onDraw` updates `pageRect`. Switch a 600 x 800
  mdpi view from a 400 x 600 page to 600 x 400, then tap `(100,300)` before
  redraw: about `(68,148)` instead of `(92,96)`.
- **Scope:** Synchronous shared layout after `show`, size, pan and zoom changes;
  rendering and input only read it. No smoothing/export geometry changes.
- **Acceptance:** Ink and eraser immediately after page turn, resize and zoom,
  without helper redraws; different aspect ratios and rotations. Fit-width,
  focus and loupe follow-ups must consume the same transform.
  Also collapse below page margins during ink/erase, terminate with CANCEL
  or UP, restore size and reuse the pointer: no canceled ink or stale erasures.
  Peer review found this cleanup gap; five failing regressions now pass.

### P-history. Replace quadratic undo snapshots with reversible deltas
- **Evidence:** `ink/InkHistory.kt:10-18` retains page-list prefixes;
  `ui/EditorViewModel.kt:107-108,175-177` copies lists and stack concatenation
  copies history. 5k strokes imply about 12.5m retained references, roughly
  50MB at four bytes/reference; 10k imply about 200MB, excluding points,
  geometry and bitmaps. These are structural estimates, not measured heap.
- **Scope:** Add/remove deltas carrying original positions, deque stacks,
  per-page order, stale-history protection and restored-session semantics.
- **Acceptance:** Model add/erase/undo/redo, equal-but-distinct identity and
  stale-state rejection; 1k/2k/4k edits show linear retained references.
  Branch history evidence is **structural reference accounting**, not device
  latency or measured retained heap. Preserve T3 and U6's scope contract.
  At 5,000 additions, retained strong stroke slots fall from 12,502,500 to
  5,000. Weak boundaries preserve exact-list stale checks through collection.
  Page-list copying, diffing and reconstruction remain O(page strokes) per edit.

### V-swatch-contrast. Make every enabled swatch boundary discernible
- **Evidence:** `ui/ChoiceDot.kt:71-73,107-110,134-136` and
  `ui/Components.kt:174-177` use a 1.6:1 threshold versus a 3:1 non-text
  target. Light yellow/pink reach 1.22/1.71; dark graphite/blue/red reach
  1.15/2.58/2.87. Existing outline tokens reach only 1.60 light/2.16 dark.
- **Scope:** Theme-aware qualified boundary token and 3:1 threshold; review
  shared width-dot fallback. Keep document colours and distinct selection.
- **Acceptance:** All enabled boundaries in both themes; screenshots include
  dark highlighter and non-graphite selected pens, plus large text.

### D-next / F3 (note navigation) / N33. Jump between annotated pages
- **Evidence/opportunity:** Draft ink already identifies annotated pages;
  navigating a long review need not wait for thumbnails or PDF text parsing.
- **Scope:** Small annotation count and Previous note / Next note in the page
  dialog, no permanent toolbar row. Destinations derive from current ink.
- **Acceptance:** Skip blank pages without wrapping, reopen after undo/last-note
  erase for fresh destinations, and retain accessible boundaries/labels.
  Counter is a labelled button; content scrolls; replacement dismisses the
  dialog. A delayed dismissal cannot clear ownership of a reopened dialog:
  peer review reproduced this gap and the regression now passes. Real IME
  and TalkBack interaction remain device checks. Optional summary export is D-summary.
  N33's long-press ◀/▶ proposal is an optional later access route through the
  same destinations, not another implementation of note navigation.

### Other implementation branches recorded by main, awaiting merge/status reconciliation

The `829fca1` ANALYSIS additions record two October passes at `d48a41d`
(post-#23): five branches from SWE-2 Max and seven from Inkling, described as
independent of other open work. These are source-document records, not newly
inspected PRs, reviews or live status. "Fixed/implemented in a PR" does not
mean main-done. Reconcile overlaps and confirm status before pickup. These
upstream backlog records are retained without independently inspecting their PRs.
This also applies to unlisted attempts at pill hiding, draft recovery,
eraser work and gesture undo; do not create another overlapping branch.

| PR | Recorded branch | Canonical coverage | Recorded implementation; remaining boundary | Latest checks/review |
|---|---|---|---|---|
| [#34](https://github.com/L-K-M/Asterinked/pull/34) | `swe/notice-actions` | N1, U4 (Open), U8 (partial) | Upstream records one action, `notice_action` colour/10s hold, Open after save and `MessageAction.SAVE_COPY` for draft-write failures; separate landed export via `EditorState.exported`, ACTION_VIEW/read grant excludes Asterinked. Main's existing slot/storage callback is distinct from that recorded implementation. | Upstream record, not independently verified |
| [#38](https://github.com/L-K-M/Asterinked/pull/38) | `swe/quick-save` | F6, D-2 overlap | `Draft.destination` while persistable write grant survives; one-tap Save, long-press copy elsewhere, Ctrl+S. Reconcile with #47, provider failure and per-document identity. | Upstream record, not independently verified |
| [#39](https://github.com/L-K-M/Asterinked/pull/39) | `swe/hover-cursor` | D-preview/U2, N35 (hover) | `onHoverEvent` ink-width/colour or eraser-radius/colour ring; hidden during writing, finger input and hover exit. Finger-size affordance remains open. | Upstream record, not independently verified |
| [#46](https://github.com/L-K-M/Asterinked/pull/46) | `swe/small-ux` | N2, U7, V6 (part) | Hidden/disabled key targets return `super`; `CLOCK_TICK` on valid page/undo/redo actions; first-editor input hint via `hintedGestures`. Real coach marks remain open. | Upstream record, not independently verified |
| [#53](https://github.com/L-K-M/Asterinked/pull/53) | `swe/asterisk-loader` | D-loader/N41 (loading) | `AsteriskLoader` replaces loading ProgressBar; eased straight strokes over ghost, visible-only loop. Welcome one-shot/taper polish overlap #48. | Upstream record, not independently verified |
| [#45](https://github.com/L-K-M/Asterinked/pull/45) | `bugfix/draw-highlight-width` | B15, B19 (decode/export), B20 (detach/comment) | `ROUND_JOIN`, `InkGeometry.strokeWidth()` in decode/highlight export, detach animator cancellation and corrected touch comment. Locale xref cleanup and full width-boundary proof remain. | Upstream record, not independently verified |
| [#47](https://github.com/L-K-M/Asterinked/pull/47) | `feature/quick-resave` | F6/D-2 | Previous CreateDocument URI in SharedPreferences key `"export"`, picker fallback. Global preference must migrate to per-document storage for F9; reconcile #38. | Upstream record, not independently verified |
| [#48](https://github.com/L-K-M/Asterinked/pull/48) | `feature/animated-loader` | D-loader/D-1 (welcome) | `InkLoaderView` progressive welcome mark: accent Paint, round caps, 6dp width. Slow-PdfRenderer/loading extension overlaps #53; unify scope before integration. | Upstream record, not independently verified |
| [#49](https://github.com/L-K-M/Asterinked/pull/49) | `feature/night-mode` | D-night/D-3 | `setNightMode()` screen-only inverted ColorMatrixColorFilter on preview/highlighter/pen paint; toolbar toggle, no theme-token changes. Keep export semantics and V-night audit distinct. | Upstream record, not independently verified |
| [#51](https://github.com/L-K-M/Asterinked/pull/51) | `feature/focus-mode` | D-focus/D-4 | Long-press Fit hides topBar/toolBar/pagePill and expands workspace. Escape, input mapping and optional immersive edge navigation remain acceptance checks. | Upstream record, not independently verified |
| [#52](https://github.com/L-K-M/Asterinked/pull/52) | `feature/page-flip` | D-flip/D-5 | 180ms alpha fade on page change in `InkPageView.show()`; reduced-motion, immediate pen contact and coordinate proof still required. | Upstream record, not independently verified |
| [#54](https://github.com/L-K-M/Asterinked/pull/54) | `feature/action-notice-bar` | U8 (partial) | Optional actionLabel/callback and notes_not_saved Save copy. Slot/storage callback now exist on main; confirm remaining scope before integration. | Upstream record, not independently verified |

### Completed review scorecard

- Fixed and regression-tested the real dialog-ownership and collapsed-view
  cleanup findings. Fixed first-contact eraser geometry duplication with
  shared renderer geometry; retained the slower exact near-miss tradeoff above.
- Refuted invented golden-test failures, base-first AtomicFile recovery,
  export of RC4 input rejected before loading, and an absent V5 library
  constant using source, independent fixtures and green checks.
- Minor follow-ups remain optional: idempotent session close if its ownership
  contract broadens; stricter retention-walker/fake-executor test contracts;
  explicit dialog layout parameters/comments; randomized eraser geometry
  coverage and helper/screenshot-path cleanup. Existing lifecycle tests
  already drain sessions. Do not expose the process worker solely for tests
  or shorten schedule probes without preserving failure sensitivity.
- `InkHistory.record` scans its shared prefix, so pure appends still take
  O(page strokes); the proposed O(new strokes) documentation is incorrect.
  Note destinations are already generated from ascending page indices;
  defensive sorting is not an unresolved navigation defect.
- Inherited notice-action styling, accessibility timeout, callback ordering
  and string-based effect selection remain the separate backlog below.

## Open queue: priority order

Pick work here after checking the awaiting-merge boundary above. Entries
with only a risk/hypothesis require proof before a behavior change.

| Rank | Work | Canonical detail |
|---|---|---|
| 0 | Build unblock: B-notice-wrap | Awaiting #55 above; main `829fca1` cannot compile, no duplicate fix |
| 1 | Draft/export protection: B8/N25, B-share-lifetime, N23; B19 partial branch awaiting #45 | Section 2; separate landed export N1 (awaiting #34), failed draft and live ink |
| 2 | Input/state: B-pen-takeover/N21, B-canceled-navigation, B-multitouch-swipe, B-quick-scale, B-stylus-secondary, B-pending-pdf/N22, B-page-dialog, B12/N32, B-storage-recovery-effect | Section 2; tests first, no geometry redesign; N2 awaits #46 |
| 3 | Release/privacy obligations: T8, G5, G1; helper errors G10 and B-rc4-import compatibility | Sections 2, 3 and 8; RC4 failure occurs before export |
| 4 | Performance: P-prefetch, P-page-tree, P-live-chunks/P10, P5/G6, P6, P8 | Section 4; P9 needs a device baseline |
| 5 | Writing space/product/accessibility: V16/F3/V9, V17, V10/N34, U5/U8/U4 (remaining), F-presets/N38, F16/N36/N37, F-accessible-text; N24/N26/N39 and remaining features/UX | Sections 2, 5–7; respect awaiting F6/hover/focus/night/loader/flip/haptic branches |
| 6 | Exploratory risks and remaining verification, including N28 | Sections 4, 8–9; R-* and T-keyboard are not confirmed bugs |

## 1. Historical status: merged pull requests

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

## 2. Backlog: bugs

### B8 / N25. Recover invalid drafts separately from failed previews
- **Evidence:** Corrupt `draft.json` or a missing private PDF repeats the
  same restore error every launch. `document/DocumentService.kt:64-68` also
  requires rendering before publishing valid ink: a transient failure leaves
  welcome with no exportable draft; reopening the original replaces it with
  an empty import. Blind deletion would lose recoverable ink.
  N25: `restore` requires `getJSONObject("savedInk")`; valid older drafts
  written before that field existed also enter the broken-draft path.
- **Where:** `document/DocumentStore.kt` (`restore`),
  `document/DocumentService.kt` (`restore`), `ui/EditorViewModel.kt` (`init`).
- **Scope:** Invalid JSON/metadata or missing source → quarantine as
  `draft.broken-<timestamp>.json`, retain at most one and notify once. Render
  failure → publish inspected metadata/ink with `preview = null`, actionable
  error and retry; preserve export and relaunch recovery. Depends on checked
  commits/session ordering above; do not conflate corruption with rendering.
  Decode absent legacy `savedInk` with `optJSONObject`/empty default, retaining
  ink conservatively as unexported; do not quarantine a valid old schema.
- **Proof:** Corrupt JSON yields empty editor, one notice, none next launch;
  fake failing renderer retains exportable ink and survives relaunch; old
  drafts without `savedInk` restore their notes and correct dirty state.

### N1. Report a landed export separately from failed draft storage (awaiting #34)
- **Evidence:** Documented `EditorViewModel.export` runs `service.export` and
  `service.saveDraft` in one worker block. A failed draft write says "Couldn't
  store this document" and leaves "Unexported notes" even though the PDF landed.
- **Scope/status:** #34 separates export success (`EditorState.exported`/Open
  action) from the draft-write message. Retain acceptance; no duplicate fix.
- **Proof:** Successful provider write then failed draft save reports both
  outcomes honestly, preserves draft recovery and offers the correct landed
  URI. Newer edits remain dirty. Depends on U5 revision semantics, G7 and
  R-export-destination-identity; a successful export is not proof of saved draft.

### B-share-lifetime. Preserve issued share attachments
- **Evidence:** `document/DocumentService.kt:82-98` deletes the prior share
  directory before another export succeeds; even a failed second share
  invalidates a delayed recipient's URI. `ShareTest` expects that deletion.
- **Scope:** Immutable per-share copies with bounded, documented expiry;
  failed/new attempts cannot invalidate recent URIs. Update the stale test
  contract; retain `FileProvider` permissions and sanitized names.
- **Proof:** Share A, successful B, failed C; A remains FileProvider-readable
  with unchanged bytes. Expired files are eventually reclaimed.

### B19. Sanitize highlighter width at every boundary (partial work awaiting #45)
- **Evidence:** Pen uses finite-positive `InkGeometry.strokeWidth`; highlights
  use raw width in `InkPageView.drawHighlight`/`PdfEngine.highlight`.
  `DocumentStore.kt:127` converts to Float; `PdfEngine.kt:175` rejects
  negatives. Valid JSON `1e300` overflows to infinity; `org.json` also parses
  unquoted `NaN`/`Infinity` via `Double.valueOf` though it refuses to write them.
  UI constants in `HIGHLIGHT_WIDTHS` do not protect restored or edited drafts.
- **Scope:** Shared finite-positive policy at decode, draw and export, with
  explicit highlighter fallback. Coordinate with the awaiting eraser branch.
  Main's analysis records #45 decode/export use of `InkGeometry.strokeWidth()`;
  reconcile its fallback and display coverage with #43 instead of recreating
  the same fix. Keep `InkGeometry` algorithm compatibility while integrating.
- **Proof:** `DocumentStore` decode/round-trip tests with zero, negative,
  overflowing, NaN and Infinity widths; restore/draw/export succeed, normal
  widths and Multiply blending are unchanged.

### B-storage-recovery-effect. Route storage recovery by typed effect
- **Evidence:** Current-main `829fca1` handler routes Save copy by English
  `contains("couldn’t be stored")`. The slot/callback exist; classification is
  coupled to translated presentation text and message acknowledgement.
- **Scope:** Typed storage-failure effect/action through the model/application
  boundary, tied to document/revision identity; localize text independently.
  This is G7/B12 locale/effect fragility, not another U8 action-slot feature.
- **Proof:** Storage failure exposes recovery in every locale; unrelated errors
  do not. Rotation/acknowledgement cannot lose or duplicate the action, and
  document replacement cannot export the wrong draft. Reuse U8's existing slot.

### B-pen-takeover / N21. Freeze navigation only for actual writing contact
- **Evidence:** `ui/InkPageView.kt:341-352,360-367,424-430` accepts writing
  before animation cancellation. Double-tap zoom runs another 220ms beneath
  a stationary pen; detectors retain finger-tap history across pen input.
  N21's separate source scenario: stylus DOWN/POINTER_DOWN outside `pageRect`
  latches `penGesture` without starting ink; fingers become inert until all
  pointers lift. Tablet impact is a hypothesis, not physical-device proof.
- **Scope:** Pen/eraser ownership cancels navigation animation/recognition;
  defer navigation requests while writing. Depends on B14's shared transform.
  Only latch writing ownership after contact can start a stroke on the page;
  preserve palm suppression once real writing has started.
- **Proof:** Stationary pen/eraser during zoom maps stably; finger tap, pen
  tap, finger tap cannot form a double tap. Margin stylus contact without ink
  does not freeze valid finger gestures; contact ending releases ownership.

### B-canceled-navigation. Canceled finger input must not navigate
- **Evidence:** `ui/InkPageView.kt:131-143,307-310,325-329,365-367` checks
  `FLAG_CANCELED` only for writing; flagged tap/fling still reaches navigation.
- **Scope/proof:** End canceled navigation recognition without dropping an
  unrelated stylus. Flagged tap/swipe does nothing; canceled pen commits
  nothing; canceled palm preserves surviving stylus ink. Use T-instant-input.

### B-multitouch-swipe. Do not turn a small pinch into a page swipe
- **Evidence:** `ui/InkPageView.kt:116-117,137-143,360-367` suppresses swipe
  only after `onScaleBegin`; sub-minimum spans and constant-span two-finger
  translation can end in the last finger's page-turn fling.
- **Scope/proof:** Latch multi-pointer participation for the whole contact
  sequence; exclude it from one-finger swipes. Test small pinch and two-finger
  translation with no turn, plus ordinary one-finger swipe still working.

### B-quick-scale. Preserve the double-tap-and-drag anchor
- **Evidence:** `ui/InkPageView.kt:122-123,364-366,389-391,399-404` pans with
  the dragging finger before quick-scale around the original fixed tap.
- **Scope/proof:** Separate anchored quick-scale from pan/pinch; suppress
  translation. From a zoomed page away from clamps, tapped page point stays
  fixed while scale changes; pinch centroid and finger-lift behavior survive.
  Depends on B14; test without redraw/wait helpers.

### B-stylus-secondary. Recognize the second standard barrel button
- **Evidence:** `ui/InkPageView.kt:348,558` checks `BUTTON_STYLUS_PRIMARY` and
  legacy `BUTTON_SECONDARY`, omitting `BUTTON_STYLUS_SECONDARY`; contact writes
  instead of erasing. This is a remaining B13 gap, not a new eraser feature.
- **Scope/proof:** All supported barrel-button bits erase without adding ink;
  ordinary contact writes. Dynamic in-contact switching is separate D-clutch.

### B-pending-pdf / N22. Resolve the latest incoming document at confirmation
- **Evidence:** `ui/MainActivity.kt:140-145,572-580,677-683`: dirty-draft
  confirmation for A leaves incoming B pending; declining A never rechecks
  B until another state update or recreation.
  N22 adds the accept path: `MainActivity.show()` captures `uri = incoming`
  when opening `confirmReplacing`, so "Open another" can still open stale A
  after B arrives. Resolve current pending identity when confirming.
- **Scope/proof:** Re-enter guarded pending processing on dismissal. Deliver
  A then B, decline/cancel A: immediately offer B, retain ink and open no URI
  twice; accept replacement uses the latest pending URI and no stale capture.
  Keep existing unsaved-notes protection and G7 effect semantics.

### N23. Do not drop live ink when export/share starts
- **Evidence:** Main's analysis records `InkPageView.show` canceling the live
  stroke as soon as `state.busy` becomes true; half-drawn ink vanishes without
  history. The small trigger window does not remove the input-loss scenario.
- **Scope:** Finish active ink once before taking the export/share snapshot
  and publishing busy; replacement cancellation remains a distinct policy.
  Coordinate input ownership and U5 export revisions at the high-level boundary.
- **Proof:** Start pen/marker, invoke export/share before UP: ink stays visible,
  enters history once and is included in the intended snapshot; failed export
  retains recoverable ink, later UP adds no duplicate or connecting segment.
  Depends on B-pen-takeover, B14 and delta-history integration.

### B-page-dialog. Preserve the edited destination on rotation
- **Evidence:** `ui/MainActivity.kt:147-156,661-665` dismisses PageDialog;
  saved state omits visibility/input (`ui/PageDialog.kt:31-44`).
- **Scope/proof:** Save visibility, text and selection tied to document
  identity. Edit/rotate retains input without navigating; document replacement
  invalidates it. Coordinate with D-next and T-keyboard.

### N24. Preserve the writing viewport across rotation
- **Evidence:** Recorded `zoom`/`panX`/`panY` are view fields lost on config
  recreation; the zoomed writing position resets despite document recovery.
- **Scope:** Save/restore the transform per document/page key; validate against
  changed page/usable bounds, never reuse it for a replaced document.
- **Proof:** Zoom/pan, rotate, restore viewport and tap before draw with correct
  page coordinates. Replacement and different-size pages do not inherit stale
  state. Depends on B14 and F9's stable identity, not smoothing changes.

### B12 / N32. Localize descriptions and make input instructions truthful
- **Evidence:** `ui/InkPageView.kt:148-160` always says fingers pan although
  touch mode writes/erases; `ui/MainActivity.kt:506-511` says fingers write
  with Eraser selected. Remaining literals include the page description,
  `"Document.pdf"` in `DocumentStore.import` and `DocumentService` requires.
  N32 notes that the description also omits current page identity.
- **Scope/proof:** `strings.xml` and Context lookups; tool/mode/page-aware
  instructions. Pen, marker and eraser announce correct behavior in stylus-
  only and finger modes; format "Page N of M" through `strings.xml` during
  `show()`, test fallbacks/long translations. Actual PDF text remains the
  separate F-accessible-text boundary.

### N2. Let hidden/disabled shortcut targets decline keys (awaiting #46)
- **Evidence:** Documented Ctrl+S on welcome returned true despite no action;
  disabled/hidden targets swallowed `onKeyShortcut` events.
- **Scope/status:** #46 returns `super` when no action occurs; verify equivalent
  PgUp/PgDn `onKeyDown` behavior. F8 core already exists; do not add it again.
- **Proof:** Hidden/disabled actions neither execute nor consume the key,
  enabled actions execute once; reuse T-keyboard and the branch's tests.

### B15. Naming slip in export
- `stream.setLineJoinStyle(ROUND_CAP)` in `PdfEngine.export` works because
  both constants are `1`. Explicit `ROUND_JOIN` is recorded on awaiting #45;
  preserve it and verify unchanged PDF geometry/PDFium, not another fix.

### B20. Small cleanups found in review
- Cancel `zoomAnimator` in `InkPageView.onDetachedFromWindow` (#11's
  double-tap animation keeps invalidating a detached view for ~220 ms).
  This cancellation and the `followFingers` comment correction are recorded
  on awaiting #45; do not recreate those changes.
- `PdfEngineSecurityTest.deeplyNestedPdf` formats xref offsets with the
  default locale; use `Locale.ROOT`. Check under a non-Latin-digit locale.
- Second-finger cancellation is already implemented/tested (Done); detach
  cleanup is distinct from B-pen-takeover's active-input cancellation.

### N26. Suppress fit-page wiggle before a page-turn fling
- **Evidence:** Recorded fit-zoom clamp allows the 12dp page margin, so a
  horizontal swipe first drags paper a few dp before turning. Cosmetic.
- **Scope/proof:** Clamp horizontal fit-page pan to zero or suppress it for a
  recognized one-finger turn; choose one policy after reproducing. Horizontal
  swipe does not wiggle, zoomed pan/pinch and multi-pointer suppression survive.
  Depends on B14, B-multitouch-swipe and real gesture/screenshot comparison.

### B-rc4-import. Load valid RC4-128 sources without top-level `/Length`
- **Evidence:** Supplied fixture is valid/readable in PDFium, but pinned PDFBox
  rejects missing-`/Length` RC4-128 with `InvalidPasswordException` before
  export. This disproves the proposed RC4-export regression; it establishes
  an import compatibility gap, not an AES-export policy failure.
- **Scope:** Investigate pinned parser/security-handler defaults with a load
  regression before changing import behavior. Preserve restrictions and actual
  password errors; do not modify G9(a)'s validated AES fix to address this.
- **Proof:** RC4-128 with and without top-level `/Length` loads identical
  source content; genuinely wrong passwords still fail. Export after successful
  import retains the intended permissions and ink, checked independently.
  Root-cause/compatibility work belongs at `PdfEngine.load`, not UI string routing.

### B2 / F11. Password prompt for protected PDFs
- **Why/where:** Password-required bank/HR PDFs are refused with #12's clear
  message. `document/PdfEngine.kt` (`load`), `DocumentService.kt` (`open`),
  `ui/MainActivity.kt`.
- **Scope:** On `DocumentProblem.PASSWORD_PROTECTED`, prompt and call
  `PDDocument.load(file, password, …)`. API 35+: password through
  `PdfRenderer.Params`, memory only. Below 35: `setAllSecurityToBeRemoved(true)`
  private copy for rendering, delete on replacement; plaintext at rest only
  on old devices. Re-encrypt with user password and original permissions;
  never persist passwords. Depends on G9(a) and temporary-source lifecycle.
- **Proof:** Wrong password retries; right password inspects pages and exports
  a copy that loads only with the password and retains restrictions. PDFium
  password fixture via `PdfDocument(path, password=…)` if supported.
  Define cold-start reauthentication and temporary plaintext lifetime before
  implementation; a password prompt must never quarantine a valid ink draft.

---

## 3. Backlog: general

- **G1. Binary notices and licences screen.** Confirmed release archive has
  AndroidX licence files but no central PDFBox/BouncyCastle NOTICE or screen;
  PDFBox font assets have their own notices. Inventory transitive libraries
  and fonts against exact upstream licences/NOTICE (Apache-2.0 §4(d)); bundle
  assets and a quiet About/Open-source licences entry, via overflow or title
  long-press. Proof: `unzip -l` finds required texts; readable in both themes
  and large text. Depends on the inventory, not assumptions about licence names.
  The additional main record cites `res/xml/shared_files.xml`; treat that as
  an inventory lead, not proof of bundled notices or a licences screen.
- **G2. Multiple documents** (see F9).
- **G3. Close and clear** (see F12).
- **G5. Explicit backup/transfer policy.** Confirmed `allowBackup=false`
  without `android:dataExtractionRules`; lint flags it. Decide cloud and
  Android 12+ device-transfer treatment for PDFs, ink and preferences, add
  `res/xml/data_extraction_rules.xml`, align README. Earlier proposal:
  exclude `files/documents`/`pen` from cloud, allow D2D; not an adopted policy.
  Proof: manifest/resource checks plus real backup/transfer verification.
- **G6. Cache page boxes in the draft.** `DocumentService.restore` reloads
  the whole PDF with PDFBox only to recompute `PageSpec`s on every cold
  start. Store them in `draft.json` (with a schema version) and fall back to
  `inspect` when absent. Validate cached boxes; preserve old drafts and B8
  recovery. Proof: old-schema restore, no redundant inspect with valid cache,
  cold-start measurements before/after. Depends on checked draft commits.
  Coordinate N40/D-replay timing metadata in the same schema-version design.
- **G7. One-shot events.** `EditorState.message` (and `shared` in #15) must
  be acknowledged manually. Replace with a single-consumer event channel
  (for example a `Channel`/`SharedFlow` or an event queue in the model) so
  state and effects stop mixing. Define unread-message retention before
  choosing transport; rotation must neither duplicate share nor lose unread
  recovery notices. Coordinate with B-pending-pdf and U8. N1's awaiting #34
  adds `exported`/`acknowledgeExport`, another manually acknowledged field;
  current-main B-storage-recovery-effect also couples recovery to English text.
- **G9(b). All-permissions encryption policy.** G9(a) is awaiting merge above.
  For an encrypted source granting every permission, decide whether to strip
  encryption instead of re-locking behind a random unknown owner password.
  This is a product decision, not the confirmed AES downgrade. Proof:
  dedicated all-permissions fixture in `PdfEngineSecurityTest`, documented
  policy and preserved content; do not relax restricted documents.
- **G10. Install helper reports launch failure.** Confirmed
  `scripts/install.sh:59` suppresses `adb shell monkey` output/errors with
  `|| true`. Report installation and launch separately; retain `--no-launch`.
  Proof: stub adb's nonzero launch is actionable, never implied success.
- **G11. Changelog matches shipped history.** Confirmed CHANGELOG only has
  0.1.0 while README/build declare 0.2.0. Add actual released changes from
  main's history and an Unreleased section for later merged user-visible work.
  Proof: published version/history agreement; awaiting branches stay distinct,
   no version bump. Depends on actual release and merge history.

---

## 4. Backlog: performance

Complexity and capacity estimates below are not device measurements.
P-eraser and P-history are implemented on awaiting branches, not open work.

- **P-prefetch. Skip speculative previews that cannot stay cached.**
  `document/DocumentService.kt:37-42,112-113` budgets a heap fraction; Letter
  preview is about 12.4MiB versus about 10.7MiB cache on a 64MiB heap, so it
  evicts itself. `ui/EditorViewModel.kt:223-228` still prefetches both neighbours
  on the autosave/export worker. Make speculation capacity-aware. Proof:
  size-limited fake skips speculation but renders visible pages; sufficient
  cache speeds forward turns. Measure small-heap device queue delay. Depends
  on the process-owned worker and T2's order contract.
- **P-page-tree. Iterate export pages once.** `document/PdfEngine.kt:103-105`
  uses `getPage(index)` per annotated page; pinned PDFBox repeatedly builds/
  scans flat-node children, giving quadratic all-page traversal. Iterate
  once and look up ink by index; explicitly validate draft page keys. Proof:
  dense-ink and last-page-only flat PDFs, page targeting, allocation/time
  scaling and PDFium proof. No export geometry change.
- **P-live-chunks / P10. Reuse settled live and committed geometry.**
  `ui/InkPageView.kt:238-240,257-261,531-538` replays all settled live segments
  and re-records all committed pens per edit. P10 additionally rebuilds the
  entire live highlighter `InkStroke`, centerline and `Path` every frame.
  Cache unchanged/chunked pen geometry and mutable tail; incrementally append
  highlighter samples to a reusable path, mirroring `InkStrokeBuilder`.
  Highlighter must remain one Multiply draw, including self-overlap; pen
  chunking cannot change blending. Proof: every-prefix geometry parity,
  preview/export agreement and hardware early/late-frame/edit-boundary traces.
  Long highlights are the P10 target. Depends on `InkIncrementalTest`, B19,
  hardware validation and preserving software fallback.
  **N29:** `InkStrokeBuilder.add` allocates its tail list per sample. The
  source record considers this small at stylus rates; measure allocation and
  only reuse the tail if the incremental highlighter shares this path or a
  profile proves need. Preserve every-prefix results and mutable-tail ownership.
- **N28. Hardware ink-layer raster cost during pan/zoom.** Source record:
  `InkPageView.inkLayer` redraws vector ink into its GPU layer texture per
  transformed frame, preserving crispness; no measured device jank establishes
  a defect. Proof first: hardware traces at 200+ strokes while panning/zooming.
  If material, trial raster reuse per zoom level with correct scale/clip and
  no stale erase/undo pixels. Coordinate with P8/P-live-chunks; sharp PDF tiles
  alone do not prove ink-layer cost disappears. Keep software fallback and
  hardware invalidation checks; this is an exploratory rendering tradeoff.
- **P5. Draft writes are O(total ink).** Even coalesced (#13), each write
  serializes all pages twice (`ink` and `savedInk`) with `org.json` boxing.
  Options, simplest first: store `savedInk` as a revision marker instead of
  a copy; write one file per page and rewrite only the changed page; or an
  append-only journal compacted on export. Acceptance: a benchmark test with
  5k strokes shows write cost tied to the changed page, independent of other
  pages; dirty/export recovery remains correct. Depends on checked commits,
  session ordering and an explicit schema migration; choose the smallest option.
- **P6. Heavy exports.** Every pen segment is its own `w m l S` path. Quantize
  widths (for example 1/16 of the nominal width) and emit runs of equal-width
  segments as one polyline, using the same quantized geometry on screen to
  keep preview == export. Update `InkIncrementalTest`'s reference
  deliberately (see AGENTS.md). Benchmark bytes/render time first. Acceptance
  target: over 5x fewer content-stream bytes for a 200-stroke page, aligned
  PDFium proof. Depends on deliberate geometry approval; unlike eraser/B14,
  this changes the export contract.
- **P8. Sharp zoom.** Previews are capped at 2048px, so text softens at high
  zoom. After zoom or pan settles (~150 ms), render only the visible region
  at screen resolution (`PdfRenderer.Page.render` with a transform and
  clip) on the worker and draw it over the base bitmap. Must go through the
  single worker and respect document/page/viewport staleness. Proof: stale
  jobs never replace a newer viewport, memory stays bounded, device zoom text
  improves. Depends on B14, worker ordering and fit-width; prerequisite for loupe.
- **P9. Lower latency.** `androidx.input:input-motionprediction` to draw a
  predicted, never-committed tail; later `androidx.graphics:graphics-core`
  front-buffered rendering for live ink. Establish a physical-device baseline
  before selecting either; compare high-speed-camera latency and
  `adb shell dumpsys gfxinfo` traces. Predictions never enter drafts/export,
  cancellation has no ghost tail. Depends on hardware access, P-live-chunks
  and pinned catalog dependencies; no current real-device latency claim.

---

## 5. Backlog: features

- **F3 (rest). Navigation.** A thumbnail strip (bottom sheet) that marks
  pages carrying ink, and a fit-width mode for landscape tablets. Thumbnails
  reuse #13's renderer at small size on the worker; defer them until the
  awaiting D-next branch establishes cheap ink-aware navigation. Proof:
  bounded thumbnail memory, correct ink markers after undo/erase and model-
  routed jumps without losing strokes. Fit-width scope/proof is V16/V9 below.
- **F6 / D-2. Quick re-save (implementations awaiting #38/#47).** Remember
  the last export URI (take a persistable permission from `CreateDocument`)
  and offer "Save" next to "Save as…";
  original overwrite only as an explicit option with write access. Proof:
  permission loss, provider partial write/cancellation and failed replacement
  retain recoverable draft; new edits stay dirty. Depends on U5's revision
  semantics and real-provider verification, not assumed atomic SAF writes.
  #38 records grant-aware `Draft.destination`, long-press copy elsewhere and
  Ctrl+S; #47 records a global SharedPreferences `"export"` URI/picker fallback.
  Select/reconcile one destination model, including replacement and
  F9 per-document migration. Do not create a third implementation; optional
  original overwrite and failure recovery remain explicit acceptance work.
- **F7. Editable export.** Option to write standard `/Ink` annotations
  instead of page content, so recipients can hide or delete them and
  Asterinked can re-import them for editing. Keep "burn in" as the other
  option. Note: then `canModifyAnnotations` rather than `canModify` is the
  relevant permission, admitting more restricted PDFs. Define annotation
  appearance, pressure/highlighter representation and re-import identity
  before enabling it. Proof: interoperability, restrictions, rotation/crop
  placement and repeated import/export without duplicate ink. Depends on a
  persisted annotation model; do not silently change the default export.
- **F9. Multiple documents.** A recent list with one draft per document and
  a "continue where you left off" card on the welcome screen. Needs a draft
  index and per-document directories in `DocumentStore` (G2). Proof: switching
  preserves source/ink/page position, deleted-provider recovery and bounded
   cleanup; process death restores the selected draft. Depends on checked
   commits, session ordering and an explicit migration from the single draft.
   Use stable document identity throughout view/cache/history keys; current
   page-view keys assume globally unique imported filenames.
- **F11. Password prompt** (see B2).
- **F12. Clear page / close document.** Clear page as one undoable edit
  (`InkHistory.record`); close document returns to the welcome screen after
  the unsaved-notes prompt (G3). Proof: clear/undo restores identity/order;
  decline close keeps ink, accepted close does not resurrect a draft.
  Depends on delta history and checked draft/session lifecycle.
- **F13. Page tools.** Insert a blank note page after the current page;
  "add margin" by widening the crop and media box on the right. Both are
  PDFBox edits applied at export plus a page list change in the draft.
  Scope one operation first through document services, with resumable page
  metadata. Proof: preview/export parity at four rotations/crop offsets,
  history and restored drafts target the same pages. Depends on B14, G6 and
  schema design; not a UI-only page-list mutation.
- **F14. Pressure curve.** Width is linear in pressure with a 0.4pt floor
  (`InkGeometry.widthAt`), a 5.5x range on Medium, so light writing looks
  spidery. Add a gamma curve with a higher floor and one sensitivity slider.
  Treat preference for the curve as a device/user hypothesis. This changes
  export geometry: deliberately revise `InkIncrementalTest`; define how old
  strokes retain their appearance. Proof: pressure extremes/nonfinite input,
  preview/export agreement and physical-pen evaluation. D-straight is separate.
- **F15. Text notes.** Typed sticky notes as `/Text` annotations for
  legible comments. Scope page position/body and editable draft persistence;
  define annotation permissions/appearance before adding a tool. Proof:
  Unicode, rotation/crop, save/reopen, undo and viewer interoperability.
  Depends on F7's annotation boundary and keyboard/IME checks.
- **F16 / N36 / N37. Search text and PDF outline navigation.** Start with embedded
  bookmarks, then cancellable background text search with page-number results.
  No implied OCR for scans. Bounded per-document cache; keep current ink/page
  while searching and route selection through model navigation. Proof:
  Unicode, empty/scanned-only results, cancellation and document replacement
  without lost strokes. Depends on document-service text extraction, worker
  ordering and accessible results; shares extraction with F-accessible-text.
  Concrete extraction hooks from main's analysis: PDFBox
  `documentCatalog.documentOutline` for bookmarks, `PDFTextStripper` per page
  for worker-side search. Offer outline in the page dialog/sheet, results via
  model jumps; raw PDFBox stays below document services.
- **F-presets / N38. Independent pen/highlighter settings.** Confirmed
  `ui/MainActivity.kt:127-130,454-482` shares colour/width indices across
  different palettes and physical widths. Split per-tool preferences and
  migrate existing values conservatively. Proof: fine blue pen and broad pink
  marker each survive switching, rotation and restart. Foundation for D-palette,
  not a request to implement named presets at the same time.
  N38 identifies four shared swatch positions; persist colour/width per
  `InkKind`, not one palette index reused across different tools.
- **N39. Transient zoom percentage and quick baseline reset.** Proposal:
  show a temporary "250%" label during pinch, fade after settling; secondary
  tap resets to 100%, defined as the chosen fit-mode baseline, not ambiguous
  physical-size fidelity. No permanent toolbar row. Proof: readable percentage
  at clamps, reset preserves fit mode, 200% text/RTL and accessibility do not
  obscure paper or move ink under a pen. Depends on B14, V16 fit modes and
  B-pen-takeover; define focus/gesture policy before implementation.
- **F-accessible-text. Screen-reader document access.** Confirmed
  `ui/InkPageView.kt:150,217` exposes generic description/bitmap, not readable
  PDF text or accessible pan/zoom. Extract text/reading order in document
  services and provide a reading view plus labelled zoom/navigation actions.
  Proof: TalkBack reads a text fixture and navigates without pinch; scanned
  pages explicitly lack a text layer. Depends on F16's extraction boundary,
  B12 descriptions and physical accessibility-service testing.

---

## 6. Backlog: visual and UX

- **V16 / F3 / V9. Compact landscape and fit-width.** Observed
  `editor-compact-landscape.png` (680 x 360dp) leaves about 113dp-high paper;
  whole-page fit wastes side space on wider landscape screens. Earlier #23
  lowered the top bar/moved the pill corner (at about 411dp high, paper was
  roughly 250dp). `MainActivity.kt:240-245,634,710` and
  `DesignTokens.kt:27,32-33`: 64→56dp minimum alone cannot beat a 48dp target
  plus 24dp inset requiring 72dp. Scope explicit fit-page/fit-width, top-aligned
  vertical pan, and compact content-padding reduction. Tool rail/focus are
  separate follow-ups; use measured usable bounds, not `screenWidthDp` or an
  unproved ~480dp height cutoff. Proof: portrait, 680 x 360dp, tablet landscape,
  200% text; same inset/font yields smaller chrome, no target shrink; stylus
  placement after resize/pan and page turns preserve mode. Depends on B14;
  D-focus/V-compact-rail build on this; thumbnails remain F3's separate slice.
- **V-compact-rail. Reflow tools for low usable height.** V16 supplies the
  compact-landscape evidence; benefit beyond fit-width is still a hypothesis.
  Scope existing controls in one side rail only when measured width/height
  accommodate 48dp targets; preserve portrait wrapping and chosen fit mode,
  never reflow during contact. Proof: 680 x 360dp, tablet landscape, RTL and
  200% text, no overlap and more writable height with correct stylus mapping.
  Depends on V16, B14 and T4; compare against fit-width before adding chrome.
- **V17. Narrow header must expose document status.** Observed 320dp
  `editor-narrow-phone.png`: `Quart....pdf` and `Unexporte...` truncate while
  Save copy retains its label. Scope compact save treatment from space left
  for title/status, icon+tooltip when needed, full accessible filename/status.
  Proof: readable status, 48dp targets, non-overlap at 200% text, RTL and long
  translations. Depends on shared components/layout checks.
- **V10. Keep the page pill off writing starts.** Narrow/small screenshots
  cover bottom text; existing strokes pass underneath but new taps cannot.
  Fade during writing and optionally after ~3s inactivity, restore on page
  change/bottom-edge gesture, or reserve a gutter outside fitted paper. Choose
  one policy; never relocate midway through a gesture. Proof: bottom-centre
  continuous stroke and fresh tap work; keyboard/accessible navigation and
  rotation recovery remain usable. Depends on writing start/end callbacks
  and B14; share chrome transitions with D-focus.
- **N34. Temporarily hide chrome while writing.** V16/V10 motivate the
  original all-chrome-on-contact proposal. Scope an opt-in fade while a stroke
  is active, restore on lift/cancel, using the writing-start/end callback.
  Keep layout/input mapping fixed during contact; viewport expansion belongs
  to explicit D-focus before writing. Proof: no nib jump, bottom-page access,
  cancellation/rotation restore controls and keyboard/accessible escape works.
  Depends on B14, B-pen-takeover, V10 and focus transition policy; do not create
  another independent chrome-state system.
- **U5. Non-blocking export.** Baseline busy screenshots disable tools/navigation;
  notices cover lower paper. Scope
  replacement separately from export: immutable snapshot, pan/zoom allowed,
  progress tied to actual work; editing only with defined export revisions.
  Proof: slow fake export, navigation works, newer edits stay dirty, failure
  retains draft. Depends on N1's honest outcomes, N23's live-stroke handoff,
  G7, checked commits and real-provider proof.
- **U8 (rest). Existing notice actions need lifecycle/timeouts and retry.**
  Slot and storage Save copy handler exist in `829fca1`; do not re-add them.
  Scope timeout pause during accessibility focus and persistent storage-error
  visibility until acknowledged/resolved; use existing slot for failed-save
  retry. #34's recorded 10s action hold is not proof of adequate recovery time.
  Proof: actions/timeouts survive rotation and busy transitions, focus does not
  lose recovery, acknowledgement clears the right notice. B-storage-recovery-effect
  covers localized effect routing; R-notice-action-reentry, destination identity
  and T-notice-ui cover separate callback/URI/theme/target questions.
- **V6 (rest). First-document guide.** Welcome omits stylus-only/finger mode,
  swipe/pinch/double-tap and tappable page number; hand icon has no mode label.
  #16's mode toast became a notice in #23. Scope one short dismissible guide,
  persisted dismissal and reachable help; explain U1's mode tradeoff. Proof:
  shows once, no repeat on reopen/rotation, accessible and theme-aware. Depends
  on B12's truthful instructions; avoid a permanent hint row.
  The first-editor mode hint/`hintedGestures` preference already has an
  implementation awaiting #46. Remaining coach marks should point at gestures
  and expose help; reconcile the guide with that hint rather than duplicating it.
- **U1. Auto pen-only.** After the first stylus event, stop fingers from
  inking (palm safety) and say so once. Today touch-ink mode lets a resting
  palm draw. Scope an explicit opt-out and mode persistence policy, not a
  change to second-finger cancellation (already Done). Proof: fingers never
  commit after takeover, intentional finger mode remains reachable; physical
  palm/stylus verification. Depends on B-pen-takeover and V6.
- **U2. Hover cursor** is consolidated into D-preview below.
- **N35 (finger rest). Make eraser size discoverable without hover.**
  Awaiting #39 supplies the pen-hover affordance; finger erasing still reveals
  its ring only on contact. Scope a labelled current-size preview in the
  existing secondary eraser control, no invented finger-hover event or new row.
  Proof: effective size is understandable at current zoom in both themes and
  at large text; selection/cancel creates no ink. Depends on shared eraser
  radius policy and UI measurement; device usability remains unproved.
- **U3. Gesture shortcuts.** Two-finger tap = undo, three-finger tap = redo.
  Scope a completed short tap recognizer, never early edits. Proof: no undo
  during pinch/pan, canceled contacts or palm suppression; intentional taps
  make one history change. Depends on canceled/multi-pointer navigation fixes
  and U1, plus physical-device gesture evaluation.
- **U4. Export feedback (Open awaiting #34; Share remains open).** After saving,
  "Open" and "Share" actions on the "PDF saved" notice use the existing U8 slot.
  Scope a compact follow-up
  choice if both are needed. Proof: correct export URI, permission failures
  actionable and rotation does not launch twice. Depends on B-share-lifetime/G7.
  Recorded Open uses ACTION_VIEW/read grant and excludes Asterinked from the
  chooser. Existing Share grants the draft-copy URI, not automatically the
  chosen export destination; verify R-export-destination-identity before wiring it.
- **U6. Undo scope.** Undo is per page; after turning the page the last edit
  elsewhere is out of reach. Consider global chronological undo that jumps
  back, or first label where the next undo applies. Choose/document the scope
  before changing behavior. Proof: undo targets the advertised page across
  turns and restored sessions. Depends on awaiting P-history; retain per-page
  semantics until that separate product decision.
- **U7 (rest). Haptics (page/undo/redo implementation awaiting #46).** Tool,
  colour, width and finger-mode changes tick (#23). Recorded #46 adds
  `HapticFeedbackConstants.CLOCK_TICK` on valid turns/undo/redo, invalid turns
  silent. Preserve it; remaining verification covers short successful feedback,
  respect opt-out/system settings. Proof: no tick on disabled/failed actions,
  service accessibility remains independent. Depends on device evaluation.
- **V-night / D-night-full. Theme audit, separate from page inversion.**
  Main's additional "full dark-theme" seed cites `values-night/colors.xml`,
  adaptive launcher colours and theme-aware shadows. Dark chrome already
  exists (#23); scope remaining theme/contrast gaps in new controls/icons,
  preserving neutral/red identity. Inventory before changing tokens; page
  ColorMatrix inversion stays D-night. Proof: both themes, launcher/shadows,
  large text and contrast screenshots plus device icon review. Depends on
  existing design tokens, generated-icon conventions and T4, not a blanket
  redesign justified by the display-only #49 branch.

---

## 7. Delightful and quirky ideas

Proposals, not confirmed defects or measured demand. Earlier seeds remain
below with descriptive stable IDs. D-next is already awaiting merge above;
U2 is the same item as D-preview. Basic hover, loader, night, focus and flip
implementations are recorded on awaiting branches above; their acceptance
and remaining slices stay here. Do not start duplicate base implementations.

### D-focus / D-4. Focus mode with a discoverable escape (base awaiting #51)
- **Evidence:** V16's measured compact writing space; original hide-chrome seed.
- **Scope:** Deliberate toggle hides top/bottom chrome; retain one accessible
  48dp escape, stable paper mapping and Back-to-exit. Left/right edge taps
  turn pages only in navigation mode. Incoming replacement exits focus;
  export errors expose recovery/escape controls.
- **Proof:** Enter/exit, rotation/process recreation, incoming PDF and export
  error preserve usable controls; stylus coordinates never jump.
- **Depends:** B14, V16 fit-width and V10 chrome/gesture policy.
  **D-focus-deep** retains the optional immersive extension: hide the status
  bar and support edge navigation while preserving a 48dp escape/system Back.
  Prove cutout/inset, bar reveal, process/rotation restoration and RTL edge
  policy on device; depends on R-rtl-swipe and existing focus state, not a new
  independently implemented mode. #51's base long-press Fit is still unmerged.

### D-preview / U2 / N35. Hover nib and eraser preview (awaiting #39)
- **Evidence:** Hover-capable pens can report `onHoverEvent`; no device proof yet.
- **Scope:** Lightweight ring at transformed real hover position, current
  colour/width or eraser radius; never commit points. Clear on exit/page/detach.
- **Proof:** Synthetic hover changes no ink/dirty state, transforms place it
  correctly; supported physical pens validate cursor behavior.
- **Depends:** B14 and shared tool/width policy (B19), hardware access.
  Reuse #39's `onHoverEvent` implementation; verify page/detach cleanup and
  eraser-end/button switching against this acceptance. N35's non-hover finger
  affordance remains the separate scoped follow-up in section 6.

### D-straight / D-shape. QuickShape / hold-to-straighten
- **Evidence:** Original line-snap seed; highlighter is the narrower first slice.
- **Scope:** Stationary highlighter end previews a straight line; lift accepts,
  movement resumes freehand. Set dwell/motion thresholds before implementation.
  Horizontal auto-straightening and pen QuickShape remain later opt-in slices;
  F14's pressure-curve change is separate.
- **Proof:** Export matches preview, cancellation creates no edit, undo is
  one edit, ordinary handwriting never unexpectedly snaps.
- **Depends:** Incremental highlighter path, B19, grouped history; preserve
  `InkIncrementalTest` unless a later pen-geometry change is deliberate.

### D-stamps. Quiet vector review stamps
- **Evidence:** Original ✓ ✗ ? ! ✱ palette; no demand measurement.
- **Scope:** Secondary palette, one-tap placement at pen size, existing swatch
  recolouring; each vector stamp is one grouped undo edit, no new toolbar row.
- **Proof:** Rotation/crop/export fixtures, eraser and undo semantics, themed
  palette and unchanged permanent chrome size.
- **Depends:** Stamp representation/grouped history, rendered-geometry eraser,
  B14 and export-contract tests; do not rasterize stamps into the PDF.
  **N45** records a glyph-rendering route: PDFBox ZapfDingbats with
  `setFont`/`showText`, Canvas `drawText`. Verify every glyph's coverage,
  font/licence obligations and appearance/eraser parity before selecting it;
  the vector-ink proposal and grouped-edit contract remain the baseline.

### D-palette. Named pen presets and a live sample
- **Evidence:** F-presets confirms tool-switch surprises; named Review/Sign/
  Highlight sets are a separate convenience hypothesis.
- **Scope:** Optional tool/colour/width presets with compact recent switch and
  live stroke sample in settings; add no unlimited colour picker initially.
- **Proof:** Exact restore, pen/marker independence, labels/sample usable
  without colour perception and no new permanent toolbar row.
- **Depends:** F-presets migration, shared settings controls and B19.

### D-text-snap / D-snap-highlight. Snap highlighting to PDF text lines
- **Evidence:** Original seed uses PDFBox glyph positions; scans may have no text.
- **Scope:** Opt-in snap to the line's glyph boxes via document services;
  retain freehand fallback, no implied OCR. Define multi-line selection first.
- **Proof:** Crop/rotation/Unicode fixtures align screen/export; scanned or
  ambiguous text falls back, cancellation/undo remain one coherent edit.
- **Depends:** F16 text-position extraction, B14, B19 and worker ordering.

### D-scribble / D-scribble-erase / N46. Optional zig-zag erase gesture
- **Evidence:** Original fast-zig-zag seed; false positives are unmeasured.
- **Scope:** Explicitly enabled recognizer decides only at contact end and
  deletes intersected whole strokes as one edit; unmatched input stays ink.
- **Proof:** Deliberate zig-zags erase, handwriting/canceled strokes do not,
  undo restores exact order/identity. Evaluate false positives on a real pen.
- **Depends:** Rendered swept eraser, grouped history and U1 palm policy.
  Main's concrete hook is finished-stroke `InkStrokeBuilder` tail analysis;
  recognition must wait for completion, preserving ordinary incremental ink.

### D-lasso. Select and transform existing ink
- **Evidence:** Original move/recolour/delete seed; current editing is stroke-wise.
- **Scope:** Secondary selection mode, closed lasso, whole-stroke selection;
  one move, recolour or delete transaction, with explicit cancel.
- **Proof:** Rotation/crop transforms, export parity, undo restores geometry/
  order/identity and cancellation changes no stored ink.
- **Depends:** B14, rendered geometry, grouped history and P5 draft persistence.

### D-replay / N40. Replay a writing session
- **Evidence:** Original seed; existing points need optional timing metadata.
- **Scope:** Opt-in relative timestamps per point, schema-compatible old drafts,
  display-only playback with a bounded storage policy; never rewrite ink.
- **Proof:** Timing order survives restore, old drafts load, playback/export
  keep geometry identical and timing cannot inflate storage without bound.
- **Depends:** P5/versioned storage and live-geometry replay; defer until
  ordinary persistence/performance is reliable.
  Pair the `draft.json` schema version with G6's cached page boxes and N25's
  legacy decoding; do not make separate incompatible format migrations.

### D-summary. Optional annotated-page summary on export
- **Evidence:** Original D-next seed also proposed an appended page list.
- **Scope:** Opt-in summary of annotated source page numbers, appended only
  to the exported copy; no draft/source page mutation or recursive summaries.
- **Proof:** Last-note erase/undo updates list; repeat export creates exactly
  one summary and source-page/rotation targeting stays unchanged.
- **Depends:** D-next's ink-page derivation and document-service append support.
  D-next-note is the source alias for both this summary and awaiting #41's
  navigation; the summary is not implemented merely because navigation is.

### D-flip / D-5 / N42. Small page-turn animation (base awaiting #52)
- **Evidence:** Original micro-animation seed; no measured navigation benefit.
- **Scope:** Brief cached-preview transition, disabled for reduced motion;
  pen contact ends it immediately and never animates the input transform.
- **Proof:** No extra render/blocking turn, stable first pen tap, canceled or
  rapid turns settle on correct page; device frames stay within baseline budget.
- **Depends:** Preview cache, B14, B-pen-takeover and physical frame traces.
  #52 records a 180ms alpha fade in `show()`. N42 additionally seeds roughly
  8dp translate+fade of the cached visual behind an `AnimatorDurationScale`
  reduced-motion check. Reconcile one transition after validating the base;
  never translate the input transform or stack two competing animations.

### D-night / D-3. Night reading with honest export (base awaiting #49)
- **Evidence:** Dark chrome exists but PDF paper remains original; original seed.
- **Scope:** Reversible page-display/inverting colour matrix, contrast-adjusted
  displayed ink only; clearly state exports retain original colours.
- **Proof:** Scans/images/colour diagrams recognizable; toggling cannot change
  stored colours, exported content/geometry or permission policy. Compare
  semantics/rendering, allowing ordinary PDF IDs and encryption randomness.
- **Depends:** Isolated display transform and screenshot/device colour review;
  keep this separate from theme resources and content colour constants.
  Recorded #49 filters preview/highlighter/pen paint through
  `setNightMode()` and adds a toolbar toggle; it does not modify theme tokens.
  V-night/D-night-full covers the separate launcher/shadow/theme audit.

### D-volume / D-volume-keys. Opt-in volume-key page turns
- **Evidence:** Original e-reader convention, not an established user preference.
- **Scope:** Enable explicitly, route previous/next through model navigation;
  ordinary volume behavior remains outside the opted-in editor context.
- **Proof:** Disabled mode leaves keys alone, boundaries do not double-turn,
  text/dialog focus and busy replacement cannot consume unintended presses.
- **Depends:** Existing F8 key handling, T-keyboard and device key verification.

### D-eyedropper / N44. Pick document colour for ink
- **Evidence:** Original colour-pick seed; current swatches are fixed constants.
- **Scope:** Secondary pick mode samples original page pixels at the shared
  transform, not night-transformed colours; cancel adds no ink. Define how one
  picked colour is persisted without corrupting palette indices.
- **Proof:** Crop/rotation/zoom point samples agree, night mode does not alter
  result, undo/cancel and restart preserve the chosen colour correctly.
- **Depends:** B14, F-presets/custom-colour schema and renderer sampling boundary.
  Concrete seed: long-press an existing swatch to enter picking mode; the
  preview Bitmap already lives in `InkPageView`. Sample that untransformed
  source through the shared page mapping and expose a labelled accessible
  entry.

### D-magnifier / D-margin-mag. A writing loupe for tiny margins
- **Evidence:** Original zoomed-writing-strip seed; no hardware latency proof.
- **Scope:** Optional magnified strip driven by page coordinates, original
  context still visible and strip outside pen path; never duplicate input.
- **Proof:** Input maps identically with/without loupe; compare physical-device
  stroke latency before release, with no added delay against measured baseline.
- **Depends:** P8 sharp visible-region rendering first, B14 and hardware access.

### D-erase-preview. Preview every targeted stroke
- **Evidence:** Whole-stroke removal can extend beyond the visible eraser ring.
- **Scope:** Indicate the full targeted visible strokes, including parts outside
  the ring; transient preview, one undoable commit, cancellation restores all.
- **Proof:** Hit/near-miss, cancel and undo tests plus hardware RenderNode
  invalidation/restoration, not software pixels alone.
- **Depends:** Awaiting eraser/history branches and canceled-contact semantics.

### D-clutch. Switch barrel-button action during contact
- **Evidence:** Current tool is latched at contact start; B-stylus-secondary
  concerns button recognition, while this is new in-contact behavior.
- **Scope:** Visible mode switch, split ink paths at button transitions, never
  connect across erased regions; one contact is one grouped history edit.
- **Proof:** Write→erase→write, cancellation and undo preserve exact prior ink
  with no connecting segment; test supported physical button event streams.
- **Depends:** B-stylus-secondary, B-pen-takeover, rendered eraser and grouped
  history transactions. Resolve device event behavior before implementation.

### D-loader / D-1 / N41. Draw the brand asterisk with ink (bases awaiting #48/#53)
- **Evidence:** Original welcome/loading seed; the red asterisk and pressure
  taper already define the identity, animation benefit is unmeasured.
- **Scope:** Cached asterisk strokes reveal in sequence on welcome/loading;
  bounded animation, static reduced-motion fallback, no document-ink mutation.
- **Proof:** Theme/large-text screenshots, exact taper, no idle work after
  completion/detach, loading/error transitions cannot strand the animation.
- **Depends:** Existing ink geometry, design/motion tokens and lifecycle hooks;
  low priority after writing space and reliability.
  #48's welcome `InkLoaderView` uses accent Paint, round caps and 6dp width;
  #53's loading `AsteriskLoader` uses eased straight strokes over a ghost,
  visible-only loop. Reconcile welcome one-shot versus loading loop
  and slow-PdfRenderer extension before another implementation. Pressure-taper
  polish remains open: use `InkGeometry`, retaining static/reduced-motion and
  draw/hold/fade-to-ghost lifecycle checks.

---

## 8. Tests, tooling and deferred review follow-ups

G8 (test gaps) is partly done: #11 tests gestures, #12 the error mapping,
#13 adds the `DocumentOperations` fake. T3's model history coverage awaits
#44; remaining original gaps are T5/T6. Branch results do not establish
combined-main or hardware coverage.

- **T8. Tag releases need the independent PDFium gate.** Confirmed
  `.github/workflows/release.yml:61-79` runs tests/lint, assembles/uploads
  release APK, but omits `scripts/verify_pdf.py`; a tag need not point at
  normal-CI-verified code. Install pinned Python test requirements and verify
  before staging/uploading artifacts; publishing depends on verified build.
  Proof: workflow order plus deliberately failed verifier blocks publication.
  No version/signing changes; AGENTS calls this a hard gate.
- **T1.** `verify_pdf.py`: assert that plain fixtures' *sources* are
  unencrypted before blaming an export (deferred from #12's review).
- **T2.** `EditorViewModelPagingTest`: pin the prefetch order (previous page
  before next page, so the next page survives a one-preview cache; deferred
  from #13's review), for example with a size-limited fake cache.
- **T3, awaiting #44.** Model-level add/erase/undo/redo coverage now verifies
  dirty flags, page isolation, replacement, autosave, ordering and identity.
  Keep it when integrating history/eraser; do not add duplicate implementation tests.
- **T4 (rest). Screenshot cache output and focused regression gates.** #23
  renders to `app/build/reports/screens/`, but `app/build.gradle.kts:56-58`
  declares only PDFium fixtures as outputs; fresh cache hits restore no
  screenshots. Declare screen output or a dedicated non-cached visual task.
  Proof: clean cache-hit checkout has inspectable images; stable-chrome golden
  comparisons with tolerance/focused assertions catch intentional regressions
  without brittle whole-screen text rasterization. Add combined large-font
  error/dialog and dark-highlighter states when touching those areas; current
  21 screens separate some dimensions and do not prove real-IME usability.
- **T5.** `EditorViewModelTest` busy-waits with `Thread.sleep`; move it to
  #13's queue executor. Proof: explicitly drain worker/main posts, no sleeps
  or polling, preserving the same behavioral checks.
- **T6.** An end-to-end test through `DocumentService`: import a fixture,
  add ink, export, re-open the export as a new document, export again, and
  assert the second export carries exactly one layer of ink (only
  `PdfEngine` covers repeated exports today).
- **T7.** `MainActivityChromeTest.penSettingsSurviveARestart` clicks the
  hidden welcome swatches. Several pen/eraser/exclusivity/finger-mode tests
  likewise click hidden/disabled tools. Publish an editor state first;
  assert `isShown`/`isEnabled` before interactions and restart checks (extends
  #16's deferred test). Verify visible behavior, not hidden click callbacks.
- **T-instant-input. Stop helpers masking transform/animation failures.**
  `InkPageViewGestureTest:206-209` redraws; `:238-246` waits 600ms for animation.
  #50 covers immediate transforms and collapsed-viewport cleanup. Add remaining
  navigation/takeover sequences without those aids;
  preserve real event times and test sub-minimum pinch spans intentionally.
  `ScaleGestureDetector` ordinarily ignores spans below about 27mm/~170px
  at mdpi. Existing helpers are not timing-correctness proof.
- **T-keyboard. Verify implemented shortcuts and page-dialog keys.**
  `MainActivity.kt:161-181`, `PageDialog.kt:67-95`: F8 is done, not a feature
  to add again. Test exact modifier combinations, unintended extra modifiers,
  physical Enter, IME Go, invalid destinations, cancellation and dialog focus.
  Robolectric key tests plus physical keyboard/IME check; coordinate with
  B-page-dialog and awaiting D-next, preserve ordinary text-entry behavior.
  Arrow-key page navigation from the original F8 proposal remains unfinished;
  define focus/modifier behavior before adding it. #41 covers IME Go and invalid
  destinations, so reuse those tests rather than duplicating them.
  N2's hidden/disabled shortcut fix awaits #46; preserve its refusal/consumption
  regressions instead of implementing core shortcuts or that fix again.
- **T-notice-ui. Verify the existing action control.** Current-main U8 now has
  a slot; after #55 unblocks compilation, measure action/text at 320dp,
  200% text, RTL and both themes. Verify 48dp target/no overlap, contrast and
  theme-token use, with focused screenshots and real TalkBack focus. This is
  validation of existing controls, not another action-slot implementation.

### Unverified concerns: reproduce before implementation

- **R-notice-action-reentry. A callback may replace its own notice.** With
  current-main U8's slot, callback A can show B before A's click handler finishes.
  Test that old cleanup cannot hide B/clear its callback and that A executes
  once; retain new notice identity through rotation/busy transitions. Change
  ownership cleanup only after reproducing failure; coordinate with G7/U8.
- **R-export-destination-identity. Bind actions to the actual landed copy.**
  U4's recorded Share grant targets a draft-copy URI, not necessarily the
  export destination; N1/#34's Open must carry immutable exported URI and
  document/revision identity. Test delayed action after another export or
  replacement, read grants and permission loss: intended bytes open/share,
  no stale action exports the new draft. Storage Save copy routing must also
  retain its failed-draft identity. This is an unverified lifecycle/provider
  concern distinct from confirmed English routing fragility.
- **R-notice-overflow. Recovery text truncation.** `NoticeBar.kt:31,45-49,89`
  has a five-line limit. Render every error/recovery string at 320dp/200% text
  in both themes; only add expansion if instructions are actually lost.
  Depends on U5/U8 accessibility dismissal/action policy.
- **R-page-counter-width. Large page counts crowd actions.**
  `MainActivity.kt:276-299,558-559` leaves counter width unrestricted. Measure
  `9999 / 10000` at 200% text alongside three 48dp actions, RTL and insets;
  if overlap occurs, bound visual width while retaining accessible full position.
- **R-page-counter-label, addressed by awaiting #41.** Baseline counter has
  only a position description/tooltip. #41 supplies a button role and explicit
  navigation-purpose description, verified structurally. Real TalkBack activation
  remains a device check, not further unimplemented UI work.
- **R-rtl-swipe. Reading-order policy is undefined.**
  `InkPageView.kt:137-143` fixes swipe direction while arrows may mirror.
  Define physical versus reading-order behavior first, then compare RTL/LTR
  arrow/key/swipe destinations. No confirmed defect until policy/proof exist.
- **R-import-durability. Imported source may lack a durability sync.**
  `DocumentStore.kt:63` closes without explicit sync. Fault/crash or power-loss
  evidence must show lost source before calling it confirmed data loss.
  Probe ordering with checked commits above; draft-rename failure is already
  confirmed separately, not proof of this concern.

## 9. Device verification checklist

Robolectric cannot run `PdfRenderer`, hardware canvases, real styluses or
launchers. Before a release, check on a device (ideally one with an active
stylus, for example a Samsung S Pen tablet). None of these physical checks
was established by the October review or by branch JVM/structural benchmarks:

- Writing latency and smoothness on a page with 200+ strokes (#10), including
  panning while writing.
- Pinch, two-finger pan in touch mode, double-tap zoom, swipe page turns, and
  a palm resting and sliding off while writing (#11).
- Page turns feel instant after the first render; memory stays bounded while
  flipping through a 300-page PDF (#13).
- Stylus eraser end and side button erase (#14); the eraser ring size.
  Include primary, standard secondary and legacy barrel-button bits, hover,
  cancel restoration and hardware RenderNode invalidation after hit/undo.
  After #39 integration, hover ring follows nib/width, switches to eraser,
  disappears during writing, finger contact and hover exit/page replacement.
- Open a PDF from Files, Gmail and a browser download; share to Gmail and
  Drive; rotate during the unsaved-notes prompt (#15).
- Themed and adaptive launcher icon; at least 48dp tool targets; page pill over the
  page (#16).
- Highlights look the same on screen and in Acrobat or Chrome's PDF viewer
  (#17), including a highlight drawn across earlier pen notes (under the
  ink in both, #18).
- An owner-restricted PDF exports and still refuses printing in Acrobat (#12).
  Include AESV2/AESV3 omitted-`/Length` cases after G9(a) integration.
- TalkBack reading/navigation, tool descriptions, page-jump button role,
  notice focus/dismissal/actions, large text and real IME/keyboard dialog input.
- Slow/failed provider exports, revoked permissions, delayed share recipients,
  imported-source crash recovery and backup/device-transfer policy.
- Dense-page eraser/history/live-stroke frame and memory traces, including
  small heaps and long highlights. JVM eraser timings and reference counts
  establish neither physical latency nor hardware draw behavior.
- After loader integration (#48/#53), welcome plays once; loading draws,
  holds, fades to ghost and loops only while visible, with no ticking after
  editor/error/detach. Respect reduced motion and slow real PdfRenderer work.

---

## 10. Done

| ID | What | Where |
|---|---|---|
| B-notice-wrap | Notice action layout uses framework WRAP_CONTENT constants and compiles | Main `d9a9bef`; equivalent open #55 / `aa36354` |
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
| B20 (second-finger cancel) | Touch-ink stroke already cancels on second finger, regression tested | `InkPageView.kt:319-320`, `InkPageViewGestureTest:35-46` at `d48a41d` |
| G4 | Single editor instance for incoming intents (`singleTask`) | #15 |
| P1–P3 | Ink rendering cost per frame, per edit, per sample | #10 |
| P4, P7 | Page turns and preview aliasing | #13 |
| F1 | Eraser with undoable edits | #14 |
| F2 | Highlighter | #17 |
| F3 (part) | Swipe, double-tap zoom, zoom kept across pages, jump to page | #11, #16 |
| F4, F5 | Open with / share to Asterinked; share annotated copy | #15 |
| F8 (core) | Ctrl+Z, Ctrl+Shift+Z/Ctrl+Y, Ctrl+S, Ctrl+O and PgUp/PgDn are implemented; modifier verification and proposed arrow keys remain T-keyboard | October baseline `d48a41d`, `MainActivity.kt:161-192` |
| F10 | Remember pen settings | #16 |
| U8 (slot/storage callback, source only) | Optional action slot and storage-error Save copy handler exist; locale, lifecycle, UI and provider checks remain open | Main `829fca1`; build repair awaits #55 |
| B16 | Export drew highlights over earlier pen ink while the screen drew them under | #18 |
| B17 | The highlight being drawn sat above pen ink until the pen lifted | #18 |
| B18 | Deeply nested PDFs crashed the app with `StackOverflowError` and left the imported copy | #12 |
| CI | `main` CI failed on docs-only commits (cached tests skipped the PDFium fixtures) | #19 |
| V1–V5, V7 | Compact chrome, inline swatches, modern controls, brand icon and palette, clear mode toggle | #16 |
| V8, V11–V15 | Redesign: design tokens, dark theme, status dot, two-row phone tool bar, outlined light swatches, uncrowded top bar, 48dp targets, notices instead of toasts, edge-to-edge | #23 |

---

## 11. Stable aliases from main's October additions

The two added October passes reviewed `d48a41d`, not the advanced main build.
Their repeated backlog is consolidated into canonical entries above; the
branch tables retain implementation history and overlap details. Older
"all still open"/F8 statements defer to the core-shortcut Done row, T-keyboard's
arrow/modifier follow-ups and the explicit awaiting-merge boundary. Dark-theme
work remains an audit of existing chrome, separate from page-display inversion.

| Retained IDs | Canonical entry/status |
|---|---|
| N1 | Export outcome versus draft failure; implementation awaiting #34 |
| N2 | Hidden/disabled key consumption; implementation awaiting #46, verify via T-keyboard |
| N21 | B-pen-takeover: margin contact must not falsely latch writing ownership |
| N22 | B-pending-pdf: confirmation must resolve latest incoming URI, plus dismissal processing |
| N23 | Live-stroke handoff at export/share, alongside U5 revision semantics |
| N24 | Persist viewport across rotation with document/page identity |
| N25 | B8: absent legacy savedInk is not corruption |
| N26 | Fit-page fling wiggle; cosmetic, reproduce before changing clamp |
| N27 | P-eraser/B-eraser-geometry/B-eraser-sweep; implementation awaiting #43 |
| N28 | Hardware ink-layer raster tradeoff; physical proof before caching changes |
| N29 | P-live-chunks/P10: sample-tail allocation, profile before optimization |
| N32 | B12: localized page-aware tool/mode description |
| N33, D-next-note | D-next/F3 navigation awaiting #41; D-summary remains a separate export feature |
| N34 | Automatic temporary chrome fade while writing; share V10/D-focus state |
| N35 | D-preview/U2 pen hover awaiting #39; finger-size affordance remains open |
| N36, N37 | F16 bookmarks/text search through document services |
| N38 | F-presets per-InkKind preferences, foundation for D-palette |
| N39 | Transient zoom percentage/quick defined 100% reset |
| N40 | D-replay schema timing; coordinate G6/P5/N25 |
| N41, D-1 | D-loader: loading awaiting #53, welcome awaiting #48; pressure-taper polish open |
| N42, D-5 | D-flip: base fade awaiting #52; translate/reduced-motion proof retained |
| N44 | D-eyedropper: preview-Bitmap sampling, long-press swatch seed |
| N45 | D-stamps: verify glyph-rendering route before selecting over vector ink |
| N46, D-scribble-erase | D-scribble: finished-stroke tail-analysis hook |
| D-2 | F6: overlapping quick-save implementations awaiting #38/#47 |
| D-3 | D-night display transform awaiting #49; exported semantics remain original |
| D-4 | D-focus base awaiting #51; safe escape/input acceptance retained |
| D-shape | D-straight QuickShape, distinct from F14 pressure curve |
| D-snap-highlight | D-text-snap glyph-box highlighting |
| D-margin-mag | D-magnifier, dependent on P8 sharp zoom |
| D-volume-keys | D-volume opt-in key handling |
| D-night-full, V-night | Theme/launcher/shadow audit; existing dark theme retained |
| D-focus-deep | D-focus's optional immersive status-bar/edge-navigation extension |

Legacy source notes suggesting discarding `tmp.md` are superseded by this
task's preservation requirement; `ANALYSIS.md` is the consolidated backlog.
