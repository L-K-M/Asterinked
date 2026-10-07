# Asterinked analysis

Working backlog for Asterinked (pen annotation of PDFs on Android). It is
written for the next person or agent picking up work: every open item says
why it matters, where the code is, a concrete approach and how to prove it.
Read `AGENTS.md` first for build, CI and repo conventions.

Full-review baseline: `d48a41d`, 2026-10-06, consolidated from `tmp.md`.
Main has since advanced to `d6649ac`; its ANALYSIS delta and verified source
facts are folded in below. Other agents' PRs and reviews were not inspected.
This is not a full code/device re-review of that main revision. Earlier baseline:
v0.2.0 (`333fa4b`), reviewed file by file on 2026-09-26 with green
tests/lint/debug/PDFium checks. Section 1 retains the 2026-09-27 history;
branch implementations remain distinct from main.

Continuation: 2026-10-07. The six PRs below are this pass's implementations,
left open for human review and merging. Upstream records remain intact;
implementation on a branch is not completion on main.

Second fresh review: 2026-10-06, also at `d48a41d` (eight parallel reviewers
read the code and the 21 `UiScreenshotTest` screens; an adversarial verifier
re-checked every bug, performance and general finding against the code, the
PDFBox-Android 2.0.27 bytecode and pypdfium2). Its 96 entries, refinements,
corrections and refuted list are folded in below; duplicates are merged into
the existing entries and its implementations are listed in "Review pass".
Its verdicts: confirmed; plausible (mechanism real, impact needs a device or
is speculative); unverified (visual, UX, aesthetics and feature findings,
resting on screenshots and code reading only). Its line numbers are also
`d48a41d`; open PRs move lines in InkPageView, EditorViewModel,
DocumentService and MainActivity, so locate code by function name.

## Continuation pass: six implementations awaiting human merge

| PR | Branch/head | Coverage | Verification/review boundary |
|---|---|---|---|
| [#24](https://github.com/L-K-M/Asterinked/pull/24) | `fix/broken-draft-recovery` / `8fff63c` | B8/N25 | CI pass; 2 earlier completed reviews, findings fixed; 2 latest HTTP-429 failures leave a review gap |
| [#27](https://github.com/L-K-M/Asterinked/pull/27) | `fix/review-cleanups` / `d0712d7` | B19, B15, B20, N2 | CI pass; 3 completed rounds, no applicable important findings; latest review has zero suggestions; steady |
| [#28](https://github.com/L-K-M/Asterinked/pull/28) | `fix/data-extraction-rules` / `0deb816` | G5 | CI pass; 2 completed rounds, backup blocker refuted, remaining feedback outside scope; steady |
| [#32](https://github.com/L-K-M/Asterinked/pull/32) | `feature/gesture-undo-redo` / `4f15e45` | U3; gesture haptics | CI pass; no completed GLM review, repeated integration failures; review gap |
| [#36](https://github.com/L-K-M/Asterinked/pull/36) | `feature/pill-autohide` / `ad4ffcb` | V10 (writing only) | CI pass; 3 completed reviews, lifecycle finding fixed; 2 subsequent rounds without applicable important findings; steady |
| [#37](https://github.com/L-K-M/Asterinked/pull/37) | `test/viewmodel-queue-executor` / `a712b4b` | T5 | CI pass; 2 completed rounds without applicable important findings; steady |

- **#24:** Quarantine malformed metadata, invalid page metadata or missing
  sources once; retain drafts for read, PDF inspection and memory failures.
  Restore valid ink without its failed preview and retry it live. Missing
  `savedInk` means unexported notes. Inspection preservation has regression
  proof; do not classify all parser failures as permanent corruption.
- **#27:** Sanitize decoded/displayed/exported widths; keep valid widths and
  smoothing unchanged. Name the round join, cancel detached zoom animation,
  clarify second-finger cancellation and use locale-neutral xref offsets.
  Shortcut targets decline keys when hidden/disabled. PgUp/PgDn and full
  modifier/focus coverage remain T-keyboard.
- **#28:** Keep `allowBackup=false`, exclude cloud-storage domains, and include
  only `files/documents` and `pen.xml` for permitted D2D transfer. Availability
  is manufacturer-dependent, not universal migration support.
  [Android documents the OEM exception and missing-section defaults](https://developer.android.com/identity/data/autobackup).
- **#32:** Validate every fingertip's ID, history, release coordinates and
  cancellation before two-/three-finger taps change history. Centroid/span
  alone miss opposite motion and reject natural lift jitter. Both input modes
  retain navigation; haptics fire only when history is available.
- **#36:** Hide the pill during accepted pen/erase contact, return after 1.5s
  or page change. Cancel timers/animations outside the editor; screen-transition
  cancellation cannot reschedule it. Hidden means INVISIBLE, not transparent.
  Idle hiding/edge summoning remain optional follow-ups.
  Review's display-name collision claim does not apply: `draft.source.name`
  is a generated private UUID filename. Writing callbacks follow accepted
  pointer assignment and balance cancellation; rejected contacts do not hide it.
- **#37:** Drive restore and pending open through an injected queue executor,
  record their ordering, and drain cancelled work; no real-service polling.

All six branches include main `d6649ac`. An isolated integration in the order
#24 → #27 → #28 → #32 → #36 → #37 merges without conflicts and passes
`testDebugUnitTest lintDebug assembleDebug assembleRelease` plus all six
PDFium fixtures. Callback/test placement was adjusted to prevent two textual
conflicts. This proves that combination locally, not physical stylus latency
or future main integrations. Preserve every callback, finite-width handling
and preview retry, and recheck after integrating into changed main.

The latest #24 and #32 revisions have a GLM review gap after repeated HTTP 429
integration failures; retries stopped under the outage rule. Earlier findings
were addressed. Failed attempts are not approval. Other completed rounds
reported inherited notice-action defects outside these PRs; those remain
B-storage-recovery-effect, U8 and T-notice-ui, not unrelated fixes in each branch.
No feature PR is merged by this task.

Final verification snapshot: 2026-10-07 06:52 UTC. All six exact heads pass CI
and are open/mergeable against the reviewed main baseline. #27/#28/#36/#37
meet the review stopping rule. #24/#32 retain the explicit outage gaps above.
Deferred minor #36 feedback: use scheduler-backed event times in the stylus
test helper if it gains gesture-timing assertions; keep one downTime per
gesture rather than resetting it for every event. Current pill tests depend
on looper timers, not MotionEvent timestamps. See T-instant-input.

### Evidence and limits

This pass additionally reproduced read-preservation, pill-lifecycle and gesture
edge cases before fixes, and verified the six-branch integration above. Inspected
light/dark writing-mode screenshots show unchanged page placement while the
pill is hidden; landscape and 200% text screens retain the V16 writing-space
limitations. Original UI tests committed ink to a nonexistent stand-in PDF;
they now cancel contact to avoid queued writes contaminating another test.
The earlier consolidated review's evidence is retained below as its snapshot.

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

The 2026-10-06 review added B21-B43, G12-G14, P11-P25, F8 (rest), F17-F32,
V18-V36, U9-U15, D6-D9 and T9-T14. Entries it found to duplicate existing
ones were merged into them; those IDs survive only as aliases (section 11),
and in a heading only where an open PR uses them (V18, U13). Its IDs that
collided with existing ones were renumbered: its G10 is G13, G11 is G14,
F16 is F32, V17 is V36, D3 is D7, D4 is D8 and D5 is D9 (numeric D IDs are
kept distinct from the aliases D-1 to D-5); its T8 duplicated T8 and is
merged there. Its D1 and D2 merged into D-flip and D-straight. New entries
carry an Effort size: S, M or L.

---

## Review pass: fourteen implementations awaiting human merge

Implementations of 2026-10-06 review items, each on its own branch and open
against main. None is merged, so none is in Done; each covered
entry is marked "(awaiting #N)". "Steady state" means the GLM review rounds
met the stopping rule (two rounds without important findings); "in review"
means rounds were still running on 2026-10-07.

| PR | Branch | Items | State |
|---|---|---|---|
| [#25](https://github.com/L-K-M/Asterinked/pull/25) | `opus/broken-draft-recovery` | B8: corrupt draft set aside as `draft.broken.json`, render failure on restore keeps ink and retries, saved page clamped; round-1 fix: `inspect` reports NO_PAGES from the pages found, not `/Count` (B34's NO_PAGES part) | Open; steady state after 2 GLM rounds |
| [#26](https://github.com/L-K-M/Asterinked/pull/26) | `opus/highlight-export-hygiene` | P11 (one shared multiply ExtGState per page), B15 (`ROUND_JOIN`) | Open; steady state |
| [#29](https://github.com/L-K-M/Asterinked/pull/29) | `opus/undo-across-pages` | U6 (document-wide chronological undo that turns to the edited page), T3 (undo part) | Open; steady state |
| [#31](https://github.com/L-K-M/Asterinked/pull/31) | `opus/finger-tap-undo` | U3 (two-finger tap undo, three-finger tap redo) | Open; steady state |
| [#35](https://github.com/L-K-M/Asterinked/pull/35) | `opus/stylus-hover-cursor` | U2/D-preview (hover ring in ink colour and width, highlighter preview disc, eraser ring) | Open; steady state |
| [#56](https://github.com/L-K-M/Asterinked/pull/56) | `opus/palm-guard` | B21 (palm guard for tool bar and page pill clicks while the pen writes) | Open; steady state |
| [#57](https://github.com/L-K-M/Asterinked/pull/57) | `opus/reopen-keeps-notes` | B22 (identical PDF keeps its draft, ink, page and undo history; SHA-256 while importing; no schema change) | Open; steady state |
| [#58](https://github.com/L-K-M/Asterinked/pull/58) | `opus/finger-first-without-stylus` | U9 (finger drawing by default without a stylus; one-time explanation after a finger drag in pen mode) | Open; steady state |
| [#59](https://github.com/L-K-M/Asterinked/pull/59) | `opus/dark-theme-polish` | V36 (its V17), V18 (part of V-swatch-contrast), V19, V20, V21, V22 | Open; steady state |
| [#60](https://github.com/L-K-M/Asterinked/pull/60) | `opus/highlight-tap-export` | B26 (a highlighter tap exports as one zero-length line; PDFium proof gains a tap) | Open; steady state |
| [#61](https://github.com/L-K-M/Asterinked/pull/61) | `opus/per-tool-ink` | U13 (F-presets/N38: pen and highlighter keep their own colour and width) | Open; steady state |
| [#62](https://github.com/L-K-M/Asterinked/pull/62) | `opus/notices-let-pen-through` | U10 (stylus writes through notices; tool hints shown twice) | Open; steady state |
| [#63](https://github.com/L-K-M/Asterinked/pull/63) | `opus/keep-stroke-on-setting-change` | B27 (a colour, width or tool change mid-stroke no longer discards the stroke) | Open; steady state |
| [#64](https://github.com/L-K-M/Asterinked/pull/64) | `opus/hold-to-straighten` | D-straight/D-shape line slice (resting the pen 600 ms after at least 24dp straightens the stroke from its start; moving then drags the end; pen and highlighter, not the eraser; loops stay) | Open; in review |

Overlaps with other open PRs, recorded without choosing between them:

- **B8:** #25 and #24 both implement draft recovery (quarantine file names
  differ: `draft.broken.json` versus `draft.broken-<timestamp>.json`).
- **B15:** #26, #27 and recorded #45 all name `ROUND_JOIN`.
- **History:** #29 (U6, T3 undo part) and #44 (P-history, T3) both rewrite
  `InkHistory`; integrate one onto the other and keep both test sets.
- **U3:** #31 and #32 both add two-/three-finger tap undo/redo.
- **Hover:** #35 and #39 both add `onHoverEvent` hover rings (D-preview/U2);
  #58 also overrides `onHoverEvent` (a hovering stylus or eraser counts as a
  seen pen). #56's palm guard deliberately ignores hover (B21); B32 proposes
  hover-based palm suppression on top of the same handler.
- **Swatch contrast:** #59's V18 and #30 (V-swatch-contrast) both change the
  dark-theme swatch boundary.
- **Live-stroke cancellation:** #63 removes `cancelStroke()` for settings
  changes only; N23's busy/export cancellation remains open.

Open follow-ups from these PRs' review rounds are recorded in their entries:
B22 (blank provider display name), U10 (finger strokes on notices in finger
mode), D-preview/U2 (button events while hovering), B21 and U9 (decided
trade-offs) and U8 (rest) (text-matched Save copy routing on main).

---

## Recorded main delta: `d9a9bef`

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
- The preceding analysis recorded nine open implementation PRs. Its snapshot
  is preserved below without re-inspecting those PRs. Application changes
  stay on branches; only this consolidated backlog goes directly to main.

## Retained upstream status: eight implementations awaiting human merge

The preceding main analysis records eight implementations on separate branches.
This is its 2026-10-06 snapshot, not a new check of other agents' PRs.
PRs were recorded open for
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

Source paths below are relative to `app/src/main/java/ch/lkmc/asterinked/`
or, for `*Test.kt` and `EditorScreens.kt`,
`app/src/test/java/ch/lkmc/asterinked/`;
`res/...` and `AndroidManifest.xml` are under `app/src/main/`. Line
references are `d48a41d` evidence unless explicitly marked current-main.
JVM constraint behind many acceptance criteria: Robolectric cannot construct
`PdfRenderer` (NoSuchMethodError, an Error that `DocumentService.during()`
does not catch), so any acceptance that would run `DocumentService.open`,
`restore` or `render` end to end uses a fake `DocumentOperations`, a pure
helper or the PDFium proof instead.

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
- **2026-10-06 review (its B24, confirmed, medium; timing outcomes need a device
  or "Don't keep activities"):** Each EditorViewModel builds its own
  DocumentService (own AtomicFile and PdfEngine) and executor
  (EditorViewModel.kt:52). On Android 10 and 11 Back finishes the root activity
  and clears the model; onCleared only calls `shutdown()`, so the old worker
  still runs a queued export, its `saveDraft(saved)` or an import and drops the
  results (`if (cleared) return@post`, :237). (a) Save copy on a long PDF, Back,
  relaunch, write: the old export's older `saved` draft overwrites the new
  strokes on disk until the next edit, so a kill loses them, and "PDF saved"
  never shows. (b) The old worker imports B while the new session writes A: A's
  saveDraft deletes B.pdf (DocumentStore.kt:93), then the old worker commits a
  draft pointing at B and the next cold start fails (B8). (c) Two AtomicFiles
  write the same draft.json.new; on API 30+ rename failures are only logged. On
  API 31+ Back on a launcher root moves the task back instead, so only the
  developer option reaches it there. Where: ui/EditorViewModel.kt:47-52,
  232-244, 258-263; document/DocumentStore.kt:77-94; AndroidManifest.xml:3-8.
- **Its concrete form of the same scope (compare with #40 before reusing):**
  `internal open class AsterinkedApp : Application()` (`android:name` in the
  manifest) with `val documents by lazy { DocumentService(this) }` and `val
  worker: ExecutorService by lazy { Executors.newSingleThreadExecutor() }` (plus
  B23's draftWriter if its step 2 lands). The public
  `EditorViewModel(application)` constructor reads them; keep the internal
  injected constructor for tests. onCleared keeps `cleared = true` and queues
  `service.close()` (the renderer reopens lazily) but no longer calls
  `worker.shutdown()`; FIFO then orders the new model's restore after the old
  export, its saved-draft write and any import. Use the same class as T5's
  activity-test seam rather than two mechanisms. Accepted side effect: a
  relaunch shows the loading screen until the old export finishes. Pitfalls: no
  top-level `object` holding a Context (Robolectric creates an Application per
  test while statics persist); each test's Application leaks one idle thread
  (harmless); the LruCache and renderer become shared, fine because close()
  reopens lazily. G13's sweep is only safe after this lands, and U5's cancel
  flag becomes required (an abandoned open would otherwise always complete).
- **Its acceptance:** Robolectric with one QueueExecutor and one recording fake
  shared by two models: model1 adds a stroke and starts an export; clear it
  through a ViewModelStore (`store.clear()`); create model2; drain: the fake saw
  export, `saveDraft(saved)` and restore in that order, and model2's
  `statusText()` is `R.string.saved`. Two default-constructed models get the
  same worker. Device (Android 10 or 11 emulator): Save copy on a 300-page PDF,
  Back, relaunch, write, kill the process, relaunch; the strokes survive.
  Effort: M.

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
- **2026-10-06 review (its B33 and P24, both confirmed, low):** InkEraser's KDoc
  (InkEraser.kt:10) says it tests the centerline, but `touches()` (:38-49) walks
  `stroke.points`. Second example: samples (0,0), (100,100), (200,0), width 2,
  radius 5; the drawn peak is (100,75), so a probe at (100,95) hits (5 from the
  raw vertex, about 19 from visible ink) while a probe at (100,72) misses
  (overlaps ink, 19.8 from the raw polyline). Small in practice with dense
  stylus samples next to the 10dp radius; shows with sparse batched samples and
  sharp corners such as check marks. P24: eraseAlong allocates `strokes.filter {
  it !in erasing }` per historical and current sample (InkPageView.kt:451);
  `InkEraser.hits` builds probes as `List<Pair<Float, Float>>` plus a `listOf`
  concatenation (InkEraser.kt:27-35) and runs touches(), with an IdentityHashMap
  lookup, for every stroke-probe pair before any rejection (about 2 probes per
  sample, so modest). Its approach matches #43: a polyline provider
  (renderer-cached chords for pens, centerline for highlighters, via an identity
  map), cached bounds over that polyline, `reach = radius + width / 2`; a skip
  predicate instead of a pre-filtered list, a reusable FloatArray of probes, one
  swept-rectangle rejection per stroke; change both signatures together. If the
  approximation were kept instead, fix the KDoc. Where: ink/InkEraser.kt:10,
  27-55; ui/InkPageView.kt:445-459. Effort: S each if not taken from #43. Its acceptance, to check
  against #43: InkEraserTest with r=5 has no hit at (100,95) and a hit at
  (100,72); existing InkEraserTest and InkPageViewTest eraser tests stay green.
  P24's randomized comparison of hits() against a ported copy of the old
  implementation only fits a results-preserving optimization; against #43's
  corrected geometry use the randomized geometry coverage listed as a minor
  follow-up in the scorecard below.

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
  dimensions/pan/zoom before `onDraw` updates `pageRect`. `onDraw` itself
  still calls `clampPan()` (:213) and applies `alignTopPending` (:209-212),
  so pan state still changes inside onDraw. Switch a 600 x 800
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
- **2026-10-06 review (its P23, confirmed, low: about 2 MB at 1000 strokes on
  one page):** The class comment says a snapshot costs one reference, but
  addStroke builds `strokes + stroke` (EditorViewModel.kt:108), `record()` keeps
  before and after (InkHistory.kt:16-19) and `undo[page].orEmpty() + Edit`
  copies the edit list per record; n strokes retain n + 1 lists with n(n+1)/2
  references, released only when another document opens. Where:
  ink/InkHistory.kt:3-8, 16-19, 26-38, 49; ui/EditorViewModel.kt:104-109,
  175-178. Its approach differs from #44's
  deltas: cap retained edits (about 200) in an ArrayDeque and fix the KDoc; past
  the cap stop (canUndo false) rather than a dropLast(1) fallback, which would
  turn undoing an erase into deleting the newest stroke; store diffs for an
  uncapped bound. Recommendation: #44's deltas give the uncapped linear bound
  without losing history; the cap is only a fallback. Either way design against
  #29's document-wide history (U6), which also rewrites `InkHistory` and
  overlaps #44. Cap acceptance if used: after 500 single-stroke edits, 200 undos
  return the recorded `before` instances and the 201st is unavailable; an erase
  just past the cap never becomes a stroke deletion. Effort: S.

### V-swatch-contrast / V18. Make every enabled swatch boundary discernible (awaiting #30; V18 dark Graphite edge awaiting #59, overlapping)
- **Evidence:** `ui/ChoiceDot.kt:71-73,107-110,134-136` and
  `ui/Components.kt:174-177` use a 1.6:1 threshold versus a 3:1 non-text
  target. Light yellow/pink reach 1.22/1.71; dark graphite/blue/red reach
  1.15/2.58/2.87. Existing outline tokens reach only 1.60 light/2.16 dark.
- **Scope:** Theme-aware qualified boundary token and 3:1 threshold; review
  shared width-dot fallback. Keep document colours and distinct selection.
- **Acceptance:** All enabled boundaries in both themes; screenshots include
  dark highlighter and non-graphite selected pens, plus large text.
- **V18 (2026-10-06 review, unverified, low; #59):** In editor-phone-dark.png
  Graphite ink (25,38,46) on surface #16181C is 1.2:1, so ChoiceDot adds its 1dp
  edge in `outline` #4A4F59, only about 2.2:1 against the bar: the selected
  swatch reads as a hollow ring and the unselected default ink as "no colour".
  Where: ui/ChoiceDot.kt:98-111, 128-138; ui/Components.kt:176;
  res/values-night/colors.xml:11. Its approach differs from #30's threshold
  change: a `swatch_edge` token (values: `@color/outline` #C3C9D3, so light
  highlighter swatches look the same; values-night about #6B7280, about 3.7:1 on
  #16181C) as `Components.choice`'s outlineColor, drawn at 1.5dp (EDGE_WIDTH_DP)
  for dots with radius >= 8dp, MIN_CONTRAST unchanged. Acceptance: Robolectric
  NATIVE with night qualifiers, unselected Graphite dot drawn at 48dp, edge ring
  sampled at radius 10.5dp has contrast >= 3.0 against surface; regenerate
  editor-phone-dark.png. Effort: S. Whichever of #30 and #59 lands first, rebase
  the other and keep both acceptance sets.
- **Width dots (its V30, unverified, low):** Width dots fall back to
  on_surface_variant only under 1.6:1 against the bar. Highlighter yellow
  (1.3:1), blue (1.5:1) and green (1.3:1) fall back to grey, but pink
  (255,168,207) is 1.71:1, so the Fine dot is a 6dp pale-pink dot at 1.7:1 with
  no outline (outlines only below 1.6); across the four tints the fallback looks
  random. Where: ui/MainActivity.kt:467-473; ui/ChoiceDot.kt:71-73, 136.
  Approach: `fun blendsIn(color: Int, minContrast: Double = MIN_CONTRAST)`;
  configurePen uses `WIDTH_PREVIEW_MIN_CONTRAST = 2.0` for width dots, so all
  four light-theme highlighter tints preview as on_surface_variant; pens keep
  their colour (graphite 15:1, blue/red/green at least 5:1 on light; blue 2.6:1,
  red 2.9:1, green 3.0:1 on dark); swatches keep 1.6 unless #30's 3:1 rule
  lands. Acceptance: MainActivityChromeTest with a document open first (T7):
  Highlighter and Pink give every width `ChoiceDot.fill == on_surface_variant`;
  Pen and Blue give `fill == COLORS[1]`, also with night qualifiers. Effort: S.

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
- **2026-10-06 review's alternative UI (its F3 step 1, unverified, UX and
  features reviews):** Nothing shows which pages carry notes; the pill ("3 /
  12") hints at neither ink nor unexported changes. Proposal: pass `annotated:
  List<Int>` (draft.ink entries with non-empty lists, sorted) to showPageDialog;
  under the field a "Pages with notes" caption and a HorizontalScrollView of
  48dp chips with 1-based numbers, current page selected, each calling
  `go(index)` and dismissing; hide the row when empty; label chips for TalkBack
  ("Page 5, has notes"); Ctrl+[ and Ctrl+] in onKeyShortcut for previous and
  next annotated page. Pitfalls: pages emptied by the eraser must not appear;
  hundreds of chips scroll rather than grow the dialog. Acceptance: Robolectric
  `showPageDialog(activity, ui, current = 0, count = 12, annotated = listOf(2,
  7)) { chosen = it }` shows chips "3" and "8", clicking "8" sets `chosen = 7`
  and dismisses, an empty list hides the caption; with ink on pages 2 and 7,
  Ctrl+] on page 2 moves to 7. Effort: S. Recommendation: treat chips and Ctrl+[
  / Ctrl+] as additions to #41's destinations after it lands, not a second
  note-navigation model. Its step 2 (overview grid) is in F3 (rest).

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
| 1 | Draft/export protection: B-share-lifetime, N23, B23, B25, B37, B34, B35; integrate B8/N25, B19, B22, B26 | Section 2; #24/#25 implement recovery, #27 width fixes, #57 same-PDF reopen, #60 highlight taps; N1 remains the separate landed-export contract |
| 2 | Input/state: B-pen-takeover/N21, B30, B32, B29, B-canceled-navigation, B-multitouch-swipe, B-quick-scale, B-stylus-secondary, B-pending-pdf/N22, B-page-dialog, B12/N32, B-storage-recovery-effect, B39, B38, B42; integrate B21, B27, U9, U10 | Section 2 and 6; tests first, no geometry redesign; N2 awaits #46; B21/B27/U9/U10 await #56/#63/#58/#62 |
| 3 | Release/privacy obligations: T8, T9, T10, G1, B41; integrate G5; helper errors G10, T11 and B-rc4-import compatibility | Sections 2, 3 and 8; G5 implemented in #28; RC4 failure occurs before export |
| 4 | Performance: P-prefetch, P-page-tree, P-live-chunks/P10, N29, P14, P16, P17, P5/G6, P6, P8, P20, P25; integrate P11 | Section 4; P9, N28, P19 and P21 need device baselines; P11 awaits #26 |
| 5 | Writing space/product/accessibility: V16/F3/V9, V24, V17, V10/N34, V25, U5/U8/U4 (remaining), F-presets/N38, F32, F16/N36/N37, F8 (rest), U11, U12, U14, V26, F-accessible-text; N24/N39 and remaining features/UX | Sections 2, 5–7; respect awaiting F6/hover/focus/night/loader/flip/haptic/undo/dark-theme/per-tool/straighten branches |
| 6 | Exploratory risks and remaining verification, including N26/N28, G12-G14 and T12-T14 | Sections 3, 4, 8–9; R-* and T-keyboard are not confirmed bugs |

The 2026-10-06 review's value-for-effort order: B21 (#56), B22 (#57), U9
(#58), B23, B-session-worker (its B24, #40), B27 (#63), U10 (#62), F32,
N1 (its B36, #34), N24 (its B28), F8 (rest), U11, F-presets (its U13, #61),
T8, T10.

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

### B8 / N25. Recover invalid drafts separately from failed previews (awaiting #24 and #25, overlapping)
- **Evidence:** Corrupt `draft.json` or a missing private PDF repeats the
  same restore error every launch. `document/DocumentService.kt:64-68` also
  requires rendering before publishing valid ink: a transient failure leaves
  welcome with no exportable draft; reopening the original replaces it with
  an empty import. Blind deletion would lose recoverable ink.
  N25: `restore` requires `getJSONObject("savedInk")`; an absent baseline fails
  decoding. The earlier report claimed a historical schema without this field;
  the tested fact is compatibility with missing metadata, not that history.
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
  #24 provides this pass's implementation and regression tests. Read and PDF
  inspection failures retain JSON/ink; a failing preview retries live. Earlier
  quarantine of inspection failures was reproduced and corrected.
- **#25 (2026-10-06 review's implementation):** a corrupt draft is set aside as
  `draft.broken.json`; a render failure on restore keeps the ink and retries;
  the saved page is clamped. Round-1 fix: `inspect` reports NO_PAGES from the
  pages actually found instead of the tree's `/Count`, so an empty `/Kids` list
  is refused (B34's NO_PAGES part). It overlaps #24; integrate one and carry the
  other's tests.
- **Coordination points for whichever lands:** (1) Exclude PASSWORD_PROTECTED
  (B2's cold-start case) from the set-aside path. (2) An OutOfMemoryError while
  parsing a valid but very large draft is not corruption and must not be set
  aside (P5 maps it to DRAFT_UNREADABLE with its own handling). (3) The nullable
  `OpenDocument.preview` for render failures is the seam B25 (crash-loop
  marker), B39 and G6 (editor before first render) reuse. (4) G13's sweep must
  leave files/documents orphans alone, because B8 may recover one. (5) A draft
  that fails to parse, including one with literal NaN or Infinity (B19), is set
  aside once instead of failing every launch, and the saved-page clamp also
  covers B34's truncation.

### B25. A page that crashes PdfRenderer natively loops the app on every launch
- **Why:** Severity medium (plausible, lowered from high; whether a framework
  PDFium build crashes natively on a given file needs a device). PdfRenderer
  runs PDFium in-process. A SIGSEGV or abort on a hostile or damaged page, or an
  lmkd kill while rendering a huge page, is not an exception, so `during()`
  never sees it. goToPage persists `draft.page` before rendering
  (EditorViewModel.kt:99-100), so the stored draft points at the poisoned page
  and every cold start runs restore, renderPage, crash. Open cannot rescue it:
  picker results wait for restore (`pendingPickerResult`) and incoming intents
  wait for `!busy`. Only Clear storage escapes, which deletes all ink. A
  poisoned neighbour loops the same way through `prefetchAround` after a
  successful restore. open() itself does not loop, because its draft is saved
  only after the page-0 render (DocumentService.kt:54-56). B8 (#24/#25) covers
  thrown exceptions only.
- **Where:** document/DocumentService.kt:11, 64-71, 112-113;
  document/PdfEngine.kt:55-94; ui/EditorViewModel.kt:66-75, 169-173, 207-230.
- **Approach:** A render marker. Before every `engine.render` (which also covers
  the PdfRenderer constructor inside `rendererFor`) write
  `files/documents/rendering` containing `<source name>:<page>` (plain write, no
  fsync), and delete it in `finally`. In restore(), before any render: ignore a
  marker that names another source; if it names `draft.page`, return
  OpenDocument with `preview = null` (B8's nullable preview); if it names a
  neighbour, add that page to a skip-once set. render() and prefetch skip pages
  in the set; an explicit visit clears the entry and renders again. Show B39's
  page-level string (`page_not_shown`: "Couldn't show this page. You can still
  write on it, and your notes are safe.") rather than a second wording. Clear
  the set in open(). Accepted trade-off: a benign kill mid-render skips one
  automatic render. If B34's renderer page-count check is added to restore, run
  it after the marker check, because it opens PdfRenderer too.
- **Acceptance:** Robolectric DocumentServiceRestoreTest: save a draft with ink
  for a one-page fixture, write the marker for page 0, call restore(): it
  returns the ink with `preview == null` and the marker is gone; finishing
  without NoSuchMethodError also proves no render was attempted.
  EditorViewModelPagingTest: the fake reports page 1 as skipped; `goToPage(1)`
  does not render automatically and shows the message. Device: `kill -9` the
  process while a large page renders, relaunch: the editor opens on a blank page
  with the message instead of crashing.
- **Effort:** M

### B39. A page that fails to render says the file isn't a PDF
- **Why:** Severity low (plausible; narrowed: PDFium renders what it can of
  damaged content and rarely throws from Page.render; the realistic failures are
  OutOfMemoryError, which `toProblem` maps to OUT_OF_MEMORY before the stage
  matters, and an out-of-range index, B34). `DocumentService.render` is wrapped
  in `during(NOT_A_PDF)` (DocumentService.kt:71) and renderVisible publishes
  `messageFor(error)` (EditorViewModel.kt:214). When one page of an open
  document fails, a user whose other pages render, and whose blank page still
  accepts ink, reads "This file isn't a PDF, or it is damaged." or "This PDF is
  too large to handle on this device." and may abandon a working document.
- **Where:** ui/EditorViewModel.kt:207-216; res/values/strings.xml.
- **Approach:** Handle it in the model, not in the stage tag (a new
  DocumentProblem passed to during() would not change the OOM mapping). In
  renderVisible's failure branch, log the error and publish `page_not_shown` for
  every Throwable, OOM included. open() and restore keep their mapping, because
  there a failed first render does mean the document is unusable (B8 handles
  restore's render failure). Share the string with B25. Revisiting the page
  already retries, since nothing is cached for it.
- **Acceptance:** EditorViewModelPagingTest: a fake whose `render` throws for
  page 5; `goToPage(5)`, drain: the message equals the new string, busy is
  false, preview is null, and a following addStroke on page 5 is recorded.
- **Effort:** S

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
- **2026-10-06 review (its B36, confirmed, low):** `perform({
  service.export(draft, uri); service.saveDraft(saved) })`
  (EditorViewModel.kt:144) treats the two steps as one; the user sees
  `error_draft_not_saved` (strings.xml:57) or `error_out_of_space`, savedInk
  stays stale and each retry writes another copy ("Report-annotated (1).pdf").
  Narrowed trigger: ENOSPC is unlikely because export deletes its cache temp, as
  large as the PDF, before the draft write; an OutOfMemoryError while
  serializing the first post-export draft (savedInk == ink, so the full ink
  twice; see P5) is the likely one. Where: ui/EditorViewModel.kt:136-147.
- **Its minimal approach (fallback if #34 is not taken):** `perform({
  service.export(draft, uri) }) { publish(current.copy(draft = saved, busy =
  false, message = <pdf_saved>)); saveDraft(saved) }`. The coalesced saveDraft
  posts `notes_not_saved` on its own failure, which is accurate; calling it
  after publish keeps `unsavedDraft` at `saved`, not an older snapshot. A failed
  write leaves the on-disk draft dirty, the safe direction. With B23 step 2 the
  barrier still orders this write after the export. Acceptance, also a check for
  #34: an EditorViewModelPagingTest-style fake whose saveDraft throws
  `DocumentException(DRAFT_NOT_SAVED)` when `savedInk == ink` and ink is not
  empty; add a stroke, `export(uri)`, drain: export ran once,
  `state.draft!!.dirty == false`, and an observer saw `pdf_saved` before
  `notes_not_saved` (on main only `error_draft_not_saved`). Effort: S.

### B-share-lifetime. Preserve issued share attachments
- **Evidence:** `document/DocumentService.kt:82-98` deletes the prior share
  directory before another export succeeds; even a failed second share
  invalidates a delayed recipient's URI. `ShareTest` expects that deletion.
- **Scope:** Immutable per-share copies with bounded, documented expiry;
  failed/new attempts cannot invalidate recent URIs. Update the stale test
  contract; retain `FileProvider` permissions and sanitized names.
- **Proof:** Share A, successful B, failed C; A remains FileProvider-readable
  with unchanged bytes. Expired files are eventually reclaimed.
  Choose the expiry together with G13's sweep (it recommends one day for
  shared/ and no deletion in open()), so one retention constant governs both.

### B37. Failed or interrupted exports leave an empty or partial file at the destination
- **Why:** Severity low, confirmed (bugs-document and general merged).
  `ActivityResultContracts.CreateDocument` makes DocumentsUI create the document
  before it returns the URI, so a 0-byte "Report-annotated.pdf" exists before
  the export starts. It stays empty when `engine.export` fails (EXPORT_FAILED,
  OUT_OF_MEMORY, StackOverflowError), when the process dies during a long
  export, or when a picker result arrives after process death and restore found
  no draft (`export()` returns silently at `current.draft ?: return`,
  EditorViewModel.kt:141). If writePdf fails mid-copy after the "wt" truncation,
  a partial file remains, and an existing copy chosen for overwrite is
  destroyed. Users may send the broken file, and the next save becomes "(1)".
  Also, `pendingPickerResult` is a single slot: perform() runs `success` before
  `completed`, so show(), MainActivity.show and `model.open(incoming)` can run
  while `restoring` is still true and overwrite it (needs a pending incoming PDF
  and a pending picker result at once). README documents incomplete files only
  for provider write failures.
- **Where:** document/DocumentService.kt:101-110;
  document/DocumentStore.kt:71-75; ui/EditorViewModel.kt:63-75, 136-147;
  ui/MainActivity.kt:115-117.
- **Approach:** (1) `DocumentStore.discardFailedExport(uri, partial: Boolean)`,
  called from `DocumentService.export` on failure, which then rethrows the
  original DocumentException. Before writePdf: delete only when
  `DocumentsContract.isDocumentUri(context, uri)` and the queried
  `OpenableColumns.SIZE` is exactly 0; a null or unknown size (common for cloud
  providers) means keep it, and an existing file chosen for overwrite is never
  touched because the PDF is built before the destination opens. After writePdf
  started: delete, because the content is already truncated. Wrap
  `DocumentsContract.deleteDocument` in runCatching (providers without
  FLAG_SUPPORTS_DELETE throw). The reviewers conflict on the mid-write case (one
  said never touch the DESTINATION_UNWRITABLE path); recommended: delete there
  too, since the file is truncated garbage either way, and update README's
  incomplete-files sentence. (2) In `EditorViewModel.export`, when no draft
  exists after restore, discard the empty destination (expose it on
  DocumentOperations, for example `discardDestination(uri)`) and show
  `error_export_failed` instead of returning silently. (3) Replace
  `pendingPickerResult` with a list. Deferred product decision: persisting the
  in-flight URI in SavedStateHandle and resuming the export after process death;
  if adopted, re-export only while the destination is still 0 bytes. Coordinate
  with F6's quick re-save, which writes to remembered destinations.
- **Acceptance:** Robolectric with a fake DocumentsProvider
  (`Robolectric.setupContentProvider`) that reports SIZE and records
  `android:deleteDocument` calls: exporting a draft whose source is not a PDF
  throws EXPORT_FAILED and records exactly one delete; with SIZE 100 and a
  failing build, no delete; writePdf throwing mid-stream deletes. These paths
  use PDFBox only, so they run in Robolectric. Model test: a picker result
  pending during a failed restore yields `error_export_failed` and one discard.
  Device: start Save copy, kill the process during the export, relaunch: no
  empty file is left.
- **Effort:** M

### B22. Reopening the same PDF discards its editable notes (awaiting #57)
- **Why:** Severity high, unverified (UX review). Users return to a document by
  tapping it again in Files or Gmail. Every open imports a new UUID copy and
  saves a fresh Draft (page 0, no ink), and `saveDraft` then deletes every other
  PDF (DocumentStore.kt:93). With unexported notes the prompt says "Open another
  PDF?" (strings.xml:33-34) for the same file, and "Open another" throws the
  notes away. With exported notes there is no prompt at all: editable strokes,
  undo history and the page position vanish silently and survive only baked into
  the export, where they can no longer be erased.
- **Where:** ui/MainActivity.kt:574-580, 648-655, 667-684;
  ui/EditorViewModel.kt:77-87; document/DocumentService.kt:48-62;
  document/DocumentStore.kt:56-69, 93; res/values/strings.xml:33-36.
- **Approach:** Recognise the document by its content. In `DocumentStore.import`
  wrap the input in a DigestInputStream (SHA-256), so hashing needs no extra
  pass. Add `digest: String?` to Draft and an optional `sha256` key to
  draft.json; for older drafts compute it once from the source file during
  restore on the worker. Split `DocumentService.open` into import and decide
  steps. If the digest matches the current draft's, delete the new copy and
  return the current OpenDocument (ink, savedInk, page and undo history kept;
  update `name` if the display name changed) with an INFO notice "This PDF is
  already open. Your notes are still here." For incoming VIEW/SEND URIs move the
  unsaved-notes prompt after the import: the model holds the imported copy as a
  pending replacement, the activity prompts only if the content differs, and
  "Keep editing" deletes the pending copy. Pitfalls: `DocumentStore.saveDraft`
  deletes every PDF except the current source, so a draft write between import
  and decision would delete the pending copy (keep it outside files/documents or
  exempt it); a pending copy left by process death is swept by the next
  saveDraft; design the prompt state together with B-pending-pdf and U12; F9
  should later key drafts by this digest. #57 records "SHA-256 while importing;
  no schema change"; compare its storage of the digest with this plan before
  building on it.
- **Acceptance:** `DocumentService.open` renders through PdfRenderer, so test in
  layers. DocumentStore test with `ShadowContentResolver.registerInputStream`:
  two URIs with identical bytes give the same digest, a third differs; a pure
  decision function returns "keep current" for a match. EditorViewModel test
  with a fake DocumentOperations: a dirty draft plus a same-content open keeps
  the ink and the history and posts the INFO message. MainActivityIntentTest: a
  VIEW intent for the same bytes while dirty shows no "Open another PDF?"
  dialog.
- **Open follow-up (#57 review):** a provider that returns a blank or whitespace
  DISPLAY_NAME should count as "no name" (in `DocumentStore.import`, `?.takeIf {
  it.isNotBlank() }`), so a same-PDF reopen keeps the draft's name and writes
  nothing. Test: a provider cursor with `"  "` as DISPLAY_NAME leaves the draft
  name unchanged and no draft write is recorded. Lands naturally with B42's
  column lookup.
- **Effort:** M

### B23. Draft writes wait behind page renders on the single worker
- **Why:** Severity medium, confirmed (general, bugs-document and performance
  findings merged; the verifier lowered "high" because data loss needs process
  death inside the window). Draft persistence shares the single FIFO worker with
  rendering: goToPage queues saveDraft (EditorViewModel.kt:99), then
  renderVisible (:100), then two prefetches (:101). (1) Data loss: a stroke
  drawn while a heavy page renders gets its write queued behind up to three
  PdfRenderer calls, which take seconds on scans or CAD drawings on low-end
  devices; swiping the app away within that window loses the note without a
  message. A PDFium hang blocks every later write while the editor keeps
  accepting ink. Nothing flushes in onStop. (2) Latency: an uncached page
  (page-dialog jump, fast flipping, small heap) stays blank until the whole
  draft (all pages, ink and savedInk, see P5) is serialized and fsynced, and the
  neighbour prefetch waits too. README.md:91-92 says completed strokes are
  "queued for disk immediately", which is true only in queue order. P-prefetch
  covers the speculative-render half of the same queue.
- **Where:** ui/EditorViewModel.kt:52, 92-102, 136-147, 194-205, 207-230,
  232-244, 258-263; document/DocumentService.kt:13-17 (interface comment);
  README.md:91-92.
- **Approach:** Step 1 (S, safe alone): in `goToPage` only, order `if (preview
  == null) renderVisible(next)`, then `saveDraft(next)`, then
  `prefetchAround(next)`. Do not move the prefetches ahead of the write (one
  reviewer's proposal): that pushes the write behind two full renders and widens
  the durability window. Coalescing is unchanged because `unsavedDraft` is set
  synchronously. Step 2 (M): a second single-thread executor `draftWriter`,
  injected like `worker` (the secondary constructor creates it; tests pass a
  second queue executor). `saveDraft()` keeps the `unsavedDraft` coalescing but
  posts to draftWriter. `perform()` for open and export starts its worker task
  with a barrier, `draftWriter.submit {}.get()`. Every edit path (addStroke,
  eraseStrokes, undo, redo, goToPage) returns early while busy, so nothing can
  be queued after the barrier: a stale snapshot never lands after open() or
  export() commits, and the stray-PDF cleanup inside `DocumentStore.saveDraft`
  never runs during an import. No generation counter is needed (another
  reviewer's proposal; the barrier suffices). restore() runs while the initial
  state is busy, so it is safe too. onCleared: shut down draftWriter first, then
  queue `draftWriter.awaitTermination` plus `service.close()` on the PDF worker.
  Update the DocumentOperations comment ("saveDraft may run on a second thread,
  never at the same time as open, restore or export") and the README sentence.
  With B-session-worker (#40) both executors become process-scoped; check #40's
  worker ownership before adding the second executor.
- **Acceptance:** EditorViewModelPagingTest with an ordered event log in
  FakeDocuments: after `settle()`, `goToPage(5)` yields render:5, save:5, then
  the prefetches; `pagesFlippedPastAreNeitherRenderedNorSaved` still sees
  exactly one save. With two queue executors: `goToPage(1)` then `addStroke()`;
  running only the draft-writer queue persists the stroke while the render tasks
  are still pending; a write queued before `open(B)` completes before the fake's
  open runs. Device: open a heavy PDF, turn the page, write, swipe the app away
  within 1 s, relaunch; the note is there.
- **Effort:** S (step 1), M (step 2)

### B19. Sanitize highlighter width at every boundary (awaiting #27; recorded overlap #45)
- **Evidence:** Pen uses finite-positive `InkGeometry.strokeWidth`; highlights
  use raw width in `InkPageView.drawHighlight`/`PdfEngine.highlight`.
  `DocumentStore.kt:127` converts to Float; `PdfEngine.kt:175` rejects
  negatives. Valid JSON `1e300` overflows to infinity when converted to Float.
  This pass observed JSONObject rejecting unquoted `NaN`/`Infinity` during
  object insertion; the earlier universal parser-acceptance claim was incorrect.
  UI constants in `HIGHLIGHT_WIDTHS` do not protect restored or edited drafts.
- **Corrected premise (2026-10-06 review, its own testing):** Android's
  org.json,
  also in Robolectric, throws JSONException "Forbidden numeric value: NaN" while
  parsing a draft containing NaN or Infinity literals, so those fail the whole
  draft (with B8's #24/#25 it is set aside once). Restorable bad widths are
  therefore zero, negative, or finite literals such as `1e300` that overflow
  to Infinity in `toFloat()` (`decodeInk`, verified at `d48a41d`); all arise
  only from hand edits. Impact for in-memory or overflowed non-finite widths:
  PDFBox-Android 2.0.27's `PDAbstractContentStream.writeOperand(float)` throws
  IllegalArgumentException ("... is not a finite number"), so every export
  and share fails with EXPORT_FAILED, and the InkEraser reach
  (`radius + stroke.width / 2f`, InkEraser.kt:39, every kind) erases an
  Infinity-width stroke from anywhere and a NaN one never. Severity very low;
  keep as hardening. Its approach: sanitize once in `DocumentStore.decodeInk`
  (pens through `InkGeometry.strokeWidth`, highlighters through a kind-aware
  default, the middle of HIGHLIGHT_WIDTHS, 12) and apply the same helper in
  `PdfEngine.highlight`, `InkPageView.drawHighlight` and the InkEraser reach;
  if P5's streaming reader lands, keep JsonReader strict. Where:
  document/PdfEngine.kt:175; ink/InkEraser.kt:39; ui/InkPageView.kt:525-529;
  document/DocumentStore.kt:127. Its acceptance: hand-written draft.json
  files with highlighter width 0 and -3 restore and export with the default
  width; one with NaN fails to parse (pinning today's behaviour); an
  in-memory Infinity-width stroke, once sanitized, is not hit by a distant
  probe. Effort: S.
- **Scope:** Shared finite-positive policy at decode, draw and export, with
  explicit highlighter fallback. Coordinate with the awaiting eraser branch.
  Main's analysis records #45 decode/export use of `InkGeometry.strokeWidth()`;
  reconcile its fallback and display coverage with #43 instead of recreating
  the same fix. Keep `InkGeometry` algorithm compatibility while integrating.
  #27 includes decode, on-screen highlighting and export sanitation, a valid
  width control, detached-animation cleanup and locale-neutral xref fixtures.
- **Proof:** Zero, negative and overflowing decoded widths normalize; valid
  widths remain unchanged. Direct in-memory nonfinite highlighting must draw
  and export safely. Unquoted nonfinite object literals may fail parsing and
  require B8 recovery, not a promise of successful restore. Preserve Multiply
  blending and the existing finite-positive fallback; there is no upper clamp.

### B26. A highlighter tap or stationary highlight vanishes from exports in PDFium viewers (awaiting #60)
- **Why:** Severity low, confirmed (lowered from medium: it needs a stationary
  highlighter gesture, which the hint "Drag across text" does not invite). DOWN
  plus UP at one spot records two identical samples (InkPageView.kt:350, :324);
  InkStrokeBuilder yields two zero-length segments, `centerline` returns [p, p,
  p], and `PdfEngine.highlight` writes `x y m x y l x y l S`. Reproduced with
  pypdfium2 (PDFium 153.0.7999.0) on a 12pt round-cap stroke: `m l S` paints
  about 500 px, `m l l S` paints 0, with or without `/BM /Multiply`; a 1e-5
  offset paints. So tap marks vanish in Chrome, Android PdfRenderer viewers and
  Asterinked itself when the export is reopened, while the screen shows a round
  mark. The `line.size == 1` branch (PdfEngine.kt:178) is dead code, since
  centerline is never shorter than 2 points. The PDFium fixture uses two
  distinct points (PdfEngineRasterTest.kt:69), so CI cannot catch it.
- **Where:** document/PdfEngine.kt:170-182 (highlight); ink/Ink.kt:34-38
  (centerline); document/PdfEngineRasterTest.kt:69; scripts/verify_pdf.py.
- **Approach:** Leave `InkGeometry.segments` and `centerline` unchanged (the
  pinned export contract). In `PdfEngine.highlight`: if every centerline point
  has the first point's x and y, or the polyline's total length is under about
  1e-3 (PDFBox writes at most 5 decimals, so nearly equal points collapse in the
  stream), emit exactly `moveTo(p); lineTo(p); stroke()`, which PDFium was shown
  to render; a filled circle of radius width/2 via `drawDot` inside the multiply
  state is the alternative that holds up in more viewers. Otherwise emit the
  polyline with consecutive duplicate points dropped. Remove the dead branch.
  The screen path (`InkPageView.highlightPath`) already renders a dot.
  Coordinate with P11 (#26), which regroups highlight output in the same
  function.
- **Acceptance:** PdfEngineRasterTest's "highlight" case gains a highlighter
  stroke `[InkPoint(x, y2, 1f), InkPoint(x, y2, 1f)]` on blank paper, and
  verify_pdf.py requires yellow pixels within width/2 of its position (fails on
  main). PdfEngineExportTest parses the content with PDFStreamParser and asserts
  that no stroked subpath consists only of repeated identical points.
- **Effort:** S

### B34. A page tree with a wrong /Count puts ink on the wrong page or fails every export (NO_PAGES part awaiting #25)
- **Why:** Severity low, confirmed (bugs-document and general merged; rare
  malformed input, but then ink lands silently on the wrong page). Three notions
  of page index disagree. `inspect` maps `document.pages` (PageIterator walks
  /Kids and never reads /Count; PdfEngine.kt:46). `export` calls
  `document.getPage(index)` (:105), which descends by each node's /Count and
  throws "1-based index not found" or "Index out of bounds".
  COSParser.checkPages repairs /Count only when the trailer was rebuilt, so a
  file with a valid xref keeps wrong counts (checked in the 2.0.27 bytecode).
  PDFium walks kids in order but trusts a positive root /Count as the page
  count. Scenario A: root /Count 3, /Kids [A, P3], A = /Count 1 /Kids [P1, P2]:
  inspect and PDFium show P1, P2, P3, but getPage(1) is P3 and getPage(2)
  throws, so ink on page 2 lands on page 3 and ink on page 3 makes every export
  fail with EXPORT_FAILED. Scenario B: root /Count 2 with three kids: the pill
  shows 3 pages, openPage(2) fails, the user writes on the blank page anyway,
  and every export throws. #25's round-1 fix makes inspect report NO_PAGES from
  the leaves actually found instead of the root /Count (`numberOfPages == 0`,
  :45), so an empty /Kids list is refused; the rest is open.
- **Where:** document/PdfEngine.kt:43-53 (inspect), 55 (render), 101-107
  (export); document/DocumentService.kt:48-70 (open, restore).
- **Approach:** Build the leaf list once with the iterator: `val leaves =
  document.pages.toList()`. In export use `leaves.getOrNull(index) ?: throw
  DocumentException(DocumentProblem.EXPORT_FAILED)`; never drop ink silently,
  and never call getPage(i) in a loop (O(n) per call; the same change is
  P-page-tree's fix, so land them together). Then reconcile with PDFium: add
  `PdfEngine.pageCount(source)` = `rendererFor(source).pageCount`, and in open()
  and restore(), before saveDraft, use `usable = min(leaves.size,
  rendererCount)` from a pure helper. The verifiers conflict: one recommends
  refusing a mismatch with NOT_A_PDF, the other truncating. Recommended:
  truncate to `usable` and log a warning; refuse with NOT_A_PDF only when
  `usable` is 0. Truncation never lets the user write on a leaf PDFium cannot
  show, so no ink can become invisible (the refusal argument), and the extra
  leaves stay untouched in the export instead of refusing a file other viewers
  open. #25's saved-page clamp also covers a page count that shrinks under
  truncation. For well-formed trees iterator order equals getPage order, so
  existing fixtures and verify_pdf.py stay byte-identical. pageCount opens
  PdfRenderer, which Robolectric cannot construct, so test the pure helper; in
  restore run it after B25's marker check.
- **Acceptance:** PdfEngineExportTest with raw-byte fixtures built like
  `PdfEngineSecurityTest.deeplyNestedPdf` (Locale.ROOT offsets). Three leaves
  with MediaBox widths 300, 400 and 500 under an intermediate node with /Count 1
  over two kids: inspect returns widths [300, 400, 500]; export ink on indices 1
  and 2 and reload: the 400- and 500-wide leaves (found through the iterator)
  gain stroke operators and the 300-wide leaf does not (fails on main). Root
  /Count 2 with three kids: export on index 2 adds the stroke to the third kid.
  Unit test of the pure usable-pages helper. Optional verify_pdf.py fixture
  confirming under PDFium that the ink is on page 2.
- **Effort:** S

### B35. Ink leaks onto other pages that share a /Contents array
- **Why:** Severity low, confirmed (rare input). With AppendMode.APPEND, the
  PDPageContentStream constructor (2.0.27 bytecode) reuses an existing /Contents
  COSArray instance: it adds the new stream, inserts a leading `q` stream for
  resetContext, then calls `setItem(CONTENTS, array)`. When several pages
  reference the same indirect array, which deduplicating producers emit for
  repeated template pages, ink written on page 1 appears on every page sharing
  it, and each annotated page adds another `q`. Pages that share a content
  stream rather than an array are unaffected.
- **Where:** document/PdfEngine.kt:105-109.
- **Approach:** Before constructing PDPageContentStream: `val contents =
  page.cosObject.getDictionaryObject(COSName.CONTENTS); if (contents is
  COSArray) page.cosObject.setItem(COSName.CONTENTS, COSArray().apply {
  addAll(contents) })`. The items keep their indirect references; an array
  referenced only from here becomes unreachable and is not written. Pages that
  share the page dictionary itself cannot be separated.
- **Acceptance:** PdfEngineExportTest: a hand-built two-page PDF where both
  pages use `/Contents 5 0 R` and object 5 is `[6 0 R]`. Export ink on page 0
  and reload: page 1 still has exactly one content stream and no stroke
  operators, page 0 has the ink (fails on main).
- **Effort:** S

### B38. DRM- or certificate-protected PDFs are reported as damaged
- **Why:** Severity low, confirmed (refusing is correct, since PDFium cannot
  render them either; only the wording misleads). `PdfEngine.load` catches only
  InvalidPasswordException. PDFBox throws a plain IOException for non-standard
  /Encrypt filters ("No security handler for filter ...": FileOpen DRM, common
  on standards and journal PDFs, or LockLizard) and for certificate encryption
  /Adobe.PubSec ("Provided decryption material is not compatible with the
  document ..."); both strings are in 2.0.27 and are rethrown unwrapped. They
  fall through to `during(NOT_A_PDF)` (DocumentService.kt:51), so the user reads
  "This file isn't a PDF, or it is damaged." and re-downloads an intact file.
  Distinct from B-rc4-import (a standard handler PDFBox wrongly rejects).
- **Where:** document/PdfEngine.kt:146-150; document/DocumentProblem.kt:8-21;
  ui/EditorViewModel.kt:271-284; res/values/strings.xml:52.
- **Approach:** Add `DocumentProblem.UNSUPPORTED_PROTECTION` with the string
  "This PDF uses a kind of protection Asterinked can't open, such as DRM or a
  certificate." In `PdfEngine.load` add `catch (error: IOException)` after the
  InvalidPasswordException catch: map to UNSUPPORTED_PROTECTION when the message
  starts with "No security handler for filter" or "Provided decryption material
  is not compatible" (a stack frame in `com.tom_roush.pdfbox.pdmodel.encryption`
  also works in release, because proguard-rules.pro keeps `com.tom_roush.**`);
  rethrow anything else unchanged. The exhaustive `when` in userMessage forces
  the string mapping.
- **Acceptance:** PdfEngineSecurityTest gains two raw-bytes fixtures: a trailer
  `/Encrypt << /Filter /FOPN_foweb /V 1 /R 2 /O <...> /U <...> /P -4 >>` with an
  /ID, and `/Filter /Adobe.PubSec /SubFilter /adbe.pkcs7.s5 /Recipients [<00>]`.
  For both, inspect throws `DocumentException(UNSUPPORTED_PROTECTION)` and the
  service keeps no copy (as in
  `openingThroughTheServiceReportsTheProblemAndKeepsNoCopy`); garbage bytes
  still map to NOT_A_PDF. The pinned messages make a PDFBox upgrade that changes
  them fail loudly.
- **Effort:** S

### B42. Import trusts column 0 as the display name and has no size check
- **Why:** Severity low (plausible; depends on provider behaviour).
  `DocumentStore.import` reads `cursor.getString(0)` (DocumentStore.kt:58),
  trusting the provider to honour the projection; providers that return all
  columns put `_id` or similar first, so the title and export name become
  something like "1234-annotated.pdf". The copy has no size limit and no
  free-space check, so a multi-GB pick fills internal storage before ENOSPC maps
  to OUT_OF_SPACE, briefly starving the whole device. Cancellation and progress
  for long imports are U5.
- **Where:** document/DocumentStore.kt:56-69.
- **Approach:** The certain fix first: read DISPLAY_NAME and SIZE through
  `cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)` and
  `OpenableColumns.SIZE`, treating -1 or null as unknown, and treat a blank or
  whitespace name as none (B22's open follow-up). Optional: fail before copying
  with OUT_OF_SPACE when a known SIZE exceeds `StatFs(filesDir).availableBytes`
  minus a margin, and when SIZE is unknown count bytes in a manual copy loop
  against a MAX_IMPORT_BYTES constant (for example 1 GiB); the existing catch
  deletes the partial copy.
- **Acceptance:** Robolectric DocumentStore import test with
  `Robolectric.setupContentProvider`: a cursor with columns [_id, _display_name,
  _size] yields the right name. With the optional cap, a SIZE above it throws
  `DocumentException(OUT_OF_SPACE)` and leaves no file in documents/.
- **Effort:** S

### B41. Intent intake accepts any URI scheme, ignores ClipData-only shares silently, and can crash on API 29-32
- **Why:** Severity low; confirmed for the file:// and crash parts, plausible
  for the silent-ignore part (conforming senders always set EXTRA_STREAM, and
  file:// streams throw FileUriExposedException in senders targeting API 24+).
  MainActivity is exported and `receive()` (MainActivity.kt:648-655) accepts any
  URI; the SEND filter has no scheme restriction. (1) A SEND or VIEW, explicit
  or implicit, with `file:///data/user/0/ch.lkmc.asterinked/files/documents/...`
  or `file:///proc/self/fd/<n>` is opened by `ContentResolver.openInputStream`
  with the app's own permissions (confused deputy): another app can make
  Asterinked import its private files, replacing the draft behind at most the
  unsaved-notes prompt. Nothing leaks back to the sender. (2) On API 29-32
  `IntentCompat.getParcelableExtra` unparcels the whole extras Bundle, so an
  extra of a class the app lacks throws BadParcelableException in onCreate or
  onNewIntent and crashes the activity (API 33+ unparcels lazily). (3) A SEND
  carrying the PDF only in ClipData, or a VIEW with null data, falls back to the
  previous `incoming`, and nothing tells the user the PDF did not arrive.
- **Where:** ui/MainActivity.kt:136, 140-145, 646-655; AndroidManifest.xml:11,
  16-26.
- **Approach:** Extract `internal fun incomingUri(intent: Intent): Uri?`. Wrap
  the extras read in `try/catch (RuntimeException)` and log
  (BadParcelableException is a RuntimeException). For ACTION_SEND fall back to
  `intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri`. Accept only
  `ContentResolver.SCHEME_CONTENT` whose authority is not
  `"$packageName$FILE_AUTHORITY_SUFFIX"`. When a VIEW or SEND yields no
  acceptable URI, show a notice (new string: "Couldn't receive that PDF. Try
  sharing it again or open it from Files.") instead of staying silent. Keep the
  `?: incoming` fallback for MAIN relaunches and the
  FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY early return. Optionally add `<data
  android:scheme="content" />` to the SEND filter. Keep the check at the intent
  entry point, not in `DocumentStore.import`: PdfEngineSecurityTest:87 and other
  tests open file:// URIs through DocumentService on purpose. F29's drops reuse
  this validation.
- **Acceptance:** MainActivityIntentTest: an explicit VIEW with
  `Uri.fromFile(File(app.filesDir, "documents/draft.json"))` causes no import
  (the new notice shows, documents/ is unchanged); a `content://<pkg>.files/...`
  URI is refused too; SEND with ClipData only attempts the import
  (missing-provider error notice); SEND without a stream shows the new notice.
  Unit test of `incomingUri` with an Intent subclass whose `getParcelableExtra`
  throws BadParcelableException: it returns null and nothing crashes.
- **Effort:** S

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
  The shovel-ready EditorAction approach is recorded under U8 (rest).

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
  At stylus/eraser DOWN, freeze a running zoom animation (`cancel()`), never
  `end()` it: jumping to the target would move the content away from the nib
  (2026-10-06 verifier). Recompute the layout before mapping the first sample.
  B30 (start ink when an off-page nib enters the page) shares this DOWN path;
  design the two together.

### B30. A pen stroke that lands just off the page edge is dropped entirely
- **Why:** Severity low, confirmed (lowered from medium: most PDFs have white
  margins inside pageRect, so only strokes begun in the grey band are lost). A
  stylus or eraser DOWN starts ink only when `pageRect.contains(x, y)`
  (InkPageView.kt:343); otherwise :355 consumes the event, and every later MOVE
  of that gesture ends there too, because activePointer stays NO_POINTER. A nib
  that lands 1px outside the page (the grey band at fit, or the 12dp margin when
  panned to an edge) and then writes across the page records nothing, with no
  feedback. Repro in the 600x800 test view with a 400x600 page (page spans x
  41.3..558.7): stylus DOWN (20,300), MOVE (150,300), UP (250,300) gives zero
  strokes. Erasing that starts off-page fails the same way.
- **Where:** ui/InkPageView.kt:337-355 (onTouchEvent), 441-490 (track,
  eraseAlong, addSamples).
- **Approach:** Remember the pointer ID of a stylus or eraser that goes down
  outside pageRect (`pendingPenPointer`). On ACTION_MOVE while activePointer ==
  NO_POINTER, `findPointerIndex` it, scan its historical samples and then the
  current one for the first inside pageRect, and start the stroke there with the
  same setup as :344-351 (active colour, width and kind; activeErasing from tool
  type and buttonState; requestUnbufferedDispatch;
  requestDisallowInterceptTouchEvent). Feed only the samples from that history
  index on (give addSamples and eraseAlong a start index); for the eraser take
  `eraserAt` from the first inside sample. Clear the pending ID on its
  POINTER_UP or UP, on CANCEL and on DOWN. TOUCH-mode fingers that start
  off-page keep panning. Per B-pen-takeover/N21, latch writing ownership only
  when the stroke actually starts.
- **Acceptance:** InkPageViewTest: the repro commits one stroke whose first
  point maps to screen x=150 (about 84 page units). The same drag with
  TOOL_TYPE_ERASER across an existing stroke erases it. A stylus tap entirely
  outside the page commits nothing.
- **Effort:** S

### B21. A resting palm clicks tool bar and page pill buttons while the pen writes (awaiting #56)
- **Why:** Severity high, unverified (UX review); reproducible in Robolectric,
  the final feel needs a stylus tablet without firmware palm rejection.
  ViewGroups split motion events (targetSdk >= 11), so while the stylus is down
  on InkPageView, a palm on the tool bar or the page pill gets its own DOWN/UP
  and fires a click on lift. InkPageView's palm logic (`penGesture`,
  InkPageView.kt:340-358) only sees pointers that land on the page. A
  right-hander writing the last lines on a portrait tablet rests the palm on the
  tool bar (editor-tablet.png: page ends at y about 1137, tool bar at y about
  1190-1260): lifting the hand changes colour, redoes, or turns the page, and a
  page turn runs `cancelStroke()` (InkPageView.kt:167-168), discarding the
  stroke being written.
- **Where:** ui/MainActivity.kt:213-218 (pill placement), 282-299 (pill
  controls), 306-401 (buildToolBar); ui/Components.kt (click listeners in
  iconButton, toggleButton, segment, choice, primaryButton, primaryIconButton);
  ui/InkPageView.kt:167-168, 337-358.
- **Approach:** A PalmGuard owned by MainActivity. Override
  `MainActivity.dispatchTouchEvent` (it sees the unsplit event with every
  pointer). Track whether a TOOL_TYPE_STYLUS or TOOL_TYPE_ERASER pointer is down
  and the uptime of the last stylus lift. Mark a finger pointer as a palm when
  it goes down while the pen is down, within about 300 ms (PALM_GRACE_MS) of a
  pen lift, or when a stylus lands while that finger is still down outside the
  page. Every control built by the Components factories, and the page counter,
  gets an OnTouchListener that returns false and records `palmPress` from the
  guard on DOWN and UP; the click action runs only when `palmPress` is false,
  then resets it. Keyboard shortcuts (onKeyShortcut calls performClick),
  TalkBack and Switch Access never pass through onTouch and keep working.
  Pitfalls: deliberate two-handed taps with no pen down must still work; clear
  the state on ACTION_CANCEL; leave InkPageView's own palm logic alone; land B27
  (#63) as well, so a click that does get through no longer discards the live
  stroke.
- **Decided trade-off (#56):** the review proposed also overriding
  `dispatchGenericMotionEvent` and counting stylus hover (HOVER_ENTER/MOVE) as
  "pen in range"; #56 deliberately does not, so a left-hand tap while the right
  hand hovers the pen still works. Hover remains available to B32 (#35, #39 and
  #58 also handle hover).
- **Acceptance:** MainActivityChromeTest: publish `EditorScreens.editing()`;
  through `activity.dispatchTouchEvent` send DOWN (stylus, page centre),
  POINTER_DOWN (finger at the Red swatch's window coordinates), POINTER_UP
  (finger), UP (stylus); idle the looper; Graphite stays selected. Repeat over
  Next page: `draft.page` is unchanged. Controls: a lone finger tap on Red
  selects it; Ctrl+Z still undoes. Device check while writing at the page
  bottom.
- **Effort:** M

### B32. A palm that lands before the pen pans the page in pen mode, and can turn it
- **Why:** Severity low (plausible: frequency depends on firmware palm
  rejection; needs a stylus device). `penGesture` (InkPageView.kt:340, :358)
  makes fingers inert only after a stylus has touched. A palm that lands first
  is a TOOL_TYPE_FINGER DOWN in PEN mode and goes through the gesture path:
  while zoomed it pans with every palm MOVE, so the page shifts under the nib
  just before writing; at fit a quick sideways palm slide that lifts before the
  pen lands turns the page through onFling. When the stylus POINTER_DOWN arrives
  the panning stops but is not undone, and a framework palm cancel
  (ACTION_CANCEL with FLAG_CANCELED, Android 13+) also leaves it (:307-311; see
  B-canceled-navigation). Tests cover only the pen-first order
  (`movingPalmDoesNotPanUnderTheStylus`,
  `palmSlidingAwayAfterWritingNeitherTurnsNorPans`).
- **Where:** ui/InkPageView.kt:137-145, 304-369; onHoverEvent (new, or shared
  with D-preview/U2).
- **Approach:** Prevent rather than snap back. Track stylus hover (onHoverEvent
  with TOOL_TYPE_STYLUS or ERASER; #35 and #39 add hover handling, so share it).
  While the pen hovered within about 300 ms, fingers in PEN mode neither pan nor
  fling. Fallback: save zoom and pan at a finger DOWN and restore them when the
  gesture ends in ACTION_CANCEL with FLAG_CANCELED, or when a stylus lands
  within the grace period before any scale began; restoring needs the layout
  recomputed at once (B14). Treating a large `getTouchMajor` as a palm is
  optional and needs device tuning. Keep U3's two-finger tap (#31, #32) from
  firing on such palms.
- **Acceptance:** InkPageViewGestureTest: pinch to zoom and record
  `pageAt(300,400)`; stylus HOVER_ENTER, then finger DOWN at (400,500), MOVEs to
  (430,560) over 100 ms, stylus DOWN at (300,400), then the UPs:
  `pageAt(300,400)` is unchanged and no page turned. Fallback variant without
  hover: a finger gesture ending in CANCEL with FLAG_CANCELED restores the
  viewport. Device check with a real palm on a stylus tablet.
- **Effort:** M

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
- **2026-10-06 review (its B31, confirmed, low: a secondary one-finger zoom
  gesture):** Quick scale is on and the comment at InkPageView.kt:128 says it
  should work. After the second tap's DOWN each MOVE goes through followFingers
  (:365), which pans by the finger delta (:389-393); onScale then zooms around
  the anchored focus, scaling that pan again. Dragging down 200px zooms about 3x
  and moves the tapped content hundreds of pixels below the anchor, often off a
  phone screen; Maps, Photos and Chrome keep the anchor still. Where:
  ui/InkPageView.kt:115-146, 360-367, 374-397. Approach: a `quickScale` flag set
  in `GestureDetector.onDoubleTap`, which fires during the second DOWN inside
  `gestureDetector.onTouchEvent` (:367), after the ACTION_DOWN reset at :360-363
  that clears it; in followFingers skip the pan while `quickScale &&
  event.pointerCount == 1`, still updating lastFocus so a second finger does not
  jump; clear on UP and CANCEL; keep the onDoubleTapEvent toggle on UP when
  `!gestureScaled`. Check together with U3's tap detection (#31, #32).
  Acceptance: InkPageViewGestureTest: finger tap at (300,400); 100 ms later DOWN
  at (300,400); MOVE to (300,600) in 10 steps 16 ms apart; UP: `unitsPer100px`
  decreased and `pageAt(300,400)` equals the pre-gesture value within 2 page
  units (fails on main by a wide margin); `doubleTapZoomsAroundTheTapAndBack`
  stays green; feel check on a device. Effort: S.

### B-stylus-secondary. Recognize the second standard barrel button
- **Evidence:** `ui/InkPageView.kt:348,558` checks `BUTTON_STYLUS_PRIMARY` and
  legacy `BUTTON_SECONDARY`, omitting `BUTTON_STYLUS_SECONDARY`; contact writes
  instead of erasing. This is a remaining B13 gap, not a new eraser feature.
- **Scope/proof:** All supported barrel-button bits erase without adding ink;
  ordinary contact writes. Dynamic in-contact switching is separate D-clutch.
  A configurable button action (erase, highlight, nothing, select) is F28.

### B29. System Back gesture zones cover the page edges with gesture navigation
- **Why:** Severity medium (plausible: needs a device; whether stylus input is
  exempt varies by Android version and OEM). The workspace pads only
  `systemBars | displayCutout` left and right (MainActivity.kt:232-236), which
  are 0 with gesture navigation, so the page sits 12dp from the screen edges
  (editor-tablet.png: page at x about 12..788 of 800) inside the default Back
  zone. A finger pan started near the edge while zoomed (PEN mode), a page-turn
  swipe started at the right edge, or a TOUCH-mode stroke there becomes Back:
  the view gets ACTION_CANCEL (cancelStroke drops the stroke), a committed
  swipe backgrounds the task, and with targetSdk 37 the predictive-back preview
  appears mid-gesture. There is no `setSystemGestureExclusionRects` anywhere.
- **Where:** ui/MainActivity.kt:232-236 (insets listener), 626-644
  (applyInsets); ui/InkPageView.kt (onLayout or onSizeChanged, new code).
- **Approach:** Read `WindowInsetsCompat.Type.systemGestures()` in the root
  insets listener and pass the left and right widths to InkPageView. In onLayout
  or onSizeChanged call `ViewCompat.setSystemGestureExclusionRects` with one
  strip per edge of that width and at most 200dp tall (the system ignores
  anything beyond 200dp per edge; say so in a comment), placed over the page's
  vertical centre or the last touch Y. Do not exclude the full height. Clear the
  rects while the editor screen is not showing. Trade-off: Back cannot start
  from those strips, the norm for drawing canvases.
- **Acceptance:** Robolectric: after `EditorScreens.publish(activity,
  editing())`, `ViewCompat.getSystemGestureExclusionRects(page)` is non-empty
  and each rect lies inside the view and is at most 200dp tall. Device with
  gesture navigation: zoom in and start a finger pan 5dp from the left edge: the
  page pans and the app stays in front; in TOUCH mode a stroke started at the
  edge inks.
- **Effort:** S

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
- **2026-10-06 review (its B40, confirmed, low), the picker-prompt variant:**
  show() offers an incoming PDF only when `confirming == null`
  (MainActivity.kt:575), and the dismiss listener only clears the field (:682).
  With a dirty draft, tap Open ("Open another PDF?"), leave the prompt up, open
  B from Files with Asterinked: onNewIntent stores B, but the visible prompt is
  the identically worded picker one. "Open anyway" launches the picker; if the
  user picks X, open(X) finishes clean and show() immediately opens B, replacing
  X without asking. "Keep editing" leaves B pending until an unrelated state
  change, so a replace prompt pops up after the next stroke. Where:
  ui/MainActivity.kt:112-114, 140-145, 572-580, 667-684.
- **Its approach:** Track which prompt is up (`enum PromptFor { PICKER, INCOMING
  }`) next to `confirming`. In onNewIntent, if the PICKER prompt is showing,
  dismiss it before calling show(), so the incoming prompt replaces it (the
  newest request wins). In the dismiss listener, after `confirming = null`, post
  `model.state.value?.let(::show)` so a pending PDF is offered at once. In the
  openPdf callback, clear `incoming` when a non-null URI arrives, so an explicit
  pick wins. Keep the rotation behaviour (incoming is cleared only when the user
  decides). Design together with B22 and U12, which also reshape
  confirmReplacing. Acceptance: Robolectric `EditorScreens.publish(activity,
  editing(exported = false))`, click Open, deliver a VIEW intent for
  `content://missing-provider/b.pdf` through the controller's newIntent:
  `ShadowAlertDialog.getLatestAlertDialog()` is a new dialog; Open anyway
  attempts B's import (error notice as in MainActivityIntentTest) and
  `shadowOf(activity).nextStartedActivity` is null. Second test: Keep editing on
  the picker prompt is followed at once by the incoming prompt, with no other
  state change. T5's activity-test seam adds the remaining prompt cases. Effort:
  S.

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
  #63 (B27) removes the same `cancelStroke()` for settings changes only; the
  busy/export cancellation in `show()` is unchanged and stays N23's scope.
  A page turn also runs `cancelStroke()` (InkPageView.kt:167-168; see B21).

### B27. Tapping a colour, width or tool while writing discards the stroke in progress (awaiting #63)
- **Why:** Severity low, confirmed. `configure()` and the `tool` setter both
  call `cancelStroke()` (InkPageView.kt:155, :111). Because ViewGroups split
  multi-touch, a second touch on a swatch, width dot, tool segment or the finger
  toggle while the pen is down reaches pickColor, pickWidth, selectTool or
  toggleFingerDrawing, then configurePen and `page.configure`, and the live
  stroke or erase disappears uncommitted. A palm resting on the tool bar
  triggers it too (B21). activeColor, activeWidth, activeKind and activeErasing
  are captured at DOWN, so finishing with them is safe; `configure()` is called
  only from user actions and onCreate, never from show().
- **Where:** ui/InkPageView.kt:108-113, 154-161, 492-500;
  ui/MainActivity.kt:454-527.
- **Approach:** Remove `cancelStroke()` from `configure()` and from the `tool`
  setter, so new settings apply from the next stroke. Rewrite the finishStroke
  comment (:494-496) that relies on configure() cancelling: the seeded geometry
  stays valid because the builder uses the captured activeWidth. A TOUCH-to-PEN
  switch during a finger stroke lets that stroke finish (the activePointer
  branch ignores the mode).
- **Acceptance:** InkPageViewTest: stylus DOWN and MOVE, then
  `view.configure(InputMode.PEN, Color.RED, 4f) {...}`, MOVE, UP: exactly one
  stroke is committed with the original colour, width and all samples. The same
  with `view.tool = InkTool.ERASER` mid-stroke.
- **Effort:** S

### B-page-dialog. Preserve the edited destination on rotation
- **Evidence:** `ui/MainActivity.kt:147-156,661-665` dismisses PageDialog;
  saved state omits visibility/input (`ui/PageDialog.kt:31-44`).
- **Scope/proof:** Save visibility, text and selection tied to document
  identity. Edit/rotate retains input without navigating; document replacement
  invalidates it. Coordinate with D-next and T-keyboard.
  The 2026-10-06 review confirms the mechanism: `onDestroy` dismisses the
  dialog on every recreation (rotation, split-screen resize, unfolding) and
  the typed number is lost; save a visibility flag and the field text in
  `onSaveInstanceState` alongside N24's viewport.

### N24. Preserve the writing viewport across rotation
- **Evidence:** Recorded `zoom`/`panX`/`panY` are view fields lost on config
  recreation; the zoomed writing position resets despite document recovery.
- **Scope:** Save/restore the transform per document/page key; validate against
  changed page/usable bounds, never reuse it for a replaced document.
- **Proof:** Zoom/pan, rotate, restore viewport and tap before draw with correct
  page coordinates. Replacement and different-size pages do not inherit stale
  state. Depends on B14 and F9's stable identity, not smoothing changes.
- **2026-10-06 review (its B28, confirmed, low; UX reviewer rated it medium):**
  The manifest declares no configChanges (AndroidManifest.xml:11), so rotation,
  multi-window and freeform resizing, unfolding, a uiMode change and attaching a
  keyboard all recreate the activity. buildLayout creates a new InkPageView
  whose `pageKey` and `documentKey` are null, so the first show() takes the
  reset branch (InkPageView.kt:173-176): zoom 1, pan 0. `onSaveInstanceState`
  stores only the tool and the incoming URI (MainActivity.kt:153-157), and the
  view has no id. Where: ui/InkPageView.kt:83-86, 163-180;
  ui/MainActivity.kt:119-138, 147-157.
- **Its approach:** InkPageView exposes `viewport(): Bundle` and
  `restoreViewport(Bundle)` holding pageKey, zoom (relative to fit, so it
  carries across orientations) and the page-unit point under the view centre
  (from pageRect); never pixel pan. `MainActivity.onSaveInstanceState` stores
  it, and onCreate hands it to the view before `model.state.observe`. When the
  first show()'s key matches, keep the zoom and mark the centre pending; once a
  size is known (onDraw today, B14's shared layout later) set pan so the saved
  point is centred, then clampPan. Drop the saved viewport when the key differs.
  Prefer the Bundle to keeping the viewport in EditorViewModel (the UX
  reviewer's option): the Bundle also survives process death, where the draft
  restores the same page. Optional: reopen the page dialog (B-page-dialog).
  Acceptance: Robolectric MainActivity test: publish `EditorScreens.editing()`,
  pinch-zoom with MotionEvents, record `unitsPer100px` and `pageAt(view centre)`
  as in InkPageViewGestureTest, call `scenario.recreate()` (and a landscape
  configuration change), measure again: both match within 2 page units, or the
  point is clamped at an edge; a different page published after the recreate
  starts at fit. Effort: M.

### B12 / N32. Localize descriptions and make input instructions truthful
- **Evidence:** `ui/InkPageView.kt:148-160` always says fingers pan although
  touch mode writes/erases; `ui/MainActivity.kt:506-511` says fingers write
  with Eraser selected. Remaining user-visible literals: the page description
  (InkPageView.kt:150) and the `"Document.pdf"`/`"Document-annotated.pdf"`
  fallbacks (DocumentStore.kt:59, MainActivity.kt:736).
  N32 notes that the description also omits current page identity.
- **Correction (2026-10-06 review):** the `require` and IOException messages
  (DocumentService.kt:67 "The saved page is invalid.", DocumentStore.kt:100
  "The saved PDF is missing.", DocumentStore.kt:62 and 73) run inside
  `during()`, which wraps them in DocumentException, and `messageFor` uses
  only `toProblem()` and strings.xml (EditorViewModel.kt:247-250,
  DocumentProblem.kt:30-35); they reach only the log and need no
  translation. The view description is closed by U11 (stateDescription,
  strings.xml text without gesture instructions) and the file-name fallbacks
  by G14; move B12 to Done when both land, keeping the tool/mode-aware
  instruction text below.
- **Scope/proof:** `strings.xml` and Context lookups; tool/mode/page-aware
  instructions. Pen, marker and eraser announce correct behavior in stylus-
  only and finger modes; format "Page N of M" through `strings.xml` during
  `show()`, test fallbacks/long translations. Actual PDF text remains the
  separate F-accessible-text boundary.

### N2. Let hidden/disabled shortcut targets decline keys (awaiting #27; recorded overlap #46)
- **Evidence:** Documented Ctrl+S on welcome returned true despite no action;
  disabled/hidden targets swallowed `onKeyShortcut` events.
- **Scope/status:** #46 returns `super` when no action occurs; verify equivalent
  PgUp/PgDn `onKeyDown` behavior. F8 core already exists; do not add it again.
- **Proof:** Hidden/disabled actions neither execute nor consume the key,
  enabled actions execute once; reuse T-keyboard and the branch's tests.
  #27 independently supplies `onKeyShortcut` refusal. Do not add this fix a
  third time; `onKeyDown` page keys remain separate verification.

### B15. Naming slip in export (awaiting #26 and #27, overlapping; recorded #45)
- `stream.setLineJoinStyle(ROUND_CAP)` in `PdfEngine.export` works because
  both constants are `1`. Explicit `ROUND_JOIN` is recorded on awaiting #45;
  preserve it and verify unchanged PDF geometry/PDFium, not another fix.
  #27 also names the join without changing its numeric value, and so does
  #26 (with P11). Three branches carry the same one-line change.

### B20. Small cleanups found in review
- Cancel `zoomAnimator` in `InkPageView.onDetachedFromWindow` (#11's
  double-tap animation keeps invalidating a detached view for ~220 ms).
  This cancellation and the `followFingers` comment correction are recorded
  on awaiting #45; do not recreate those changes.
- `PdfEngineSecurityTest.deeplyNestedPdf` formats xref offsets with the
  default locale; use `Locale.ROOT`. Check under a non-Latin-digit locale.
- Second-finger cancellation is already implemented/tested (Done); detach
  cleanup is distinct from B-pen-takeover's active-input cancellation.
  #27 includes detach cancellation, the touch comment and `Locale.ROOT`.
  (The 2026-10-06 review confirms this bullet was always wrong: the cancel at
  InkPageView.kt:319-320 dates from the first commit b2d2d76 and
  `twoFingersPanInTouchMode` (:35-47) asserts it, so the followFingers
  comment (:371-373) is accurate.)
- **A page turn freezes a running zoom animation (2026-10-06 review,
  confirmed, low; about a 220 ms window):** show() calls
  `cancelZoomAnimation()` on a page change (InkPageView.kt:169), and
  `ValueAnimator.cancel()` leaves zoom at the last animated value, so Fit and
  then PgDn or Next within 220 ms leaves the next page at an in-between zoom
  (in Robolectric's paused looper, at the pre-Fit zoom). The pen start path
  (341-352) never touches the animator, so a stroke started during a
  double-tap or Fit animation maps its first samples through a pageRect that
  changes every frame. Where: ui/InkPageView.kt:163-180, 249-254, 341-352,
  424-437. Approach: add `finishZoomAnimation()` (`zoomAnimator?.end()`,
  which jumps to the target and fires the final update) and use it in show()
  on a page change; at stylus/eraser DOWN freeze instead (B-pen-takeover);
  keep cancel() on a finger DOWN, where the user takes over; cancel on
  detach (bullet 1). V34 changes the same animator's curve. Acceptance:
  InkPageViewGestureTest: double tap to zoom, then `resetZoom()` and
  immediately `show(state(page = 1))` without idling, then idle:
  `unitsPer100px` equals fit; a stylus DOWN right after `resetZoom()` leaves
  the zoom unchanged from the frame on screen, and the stroke's page points
  match that frame's mapping. Effort: S.

### N26. Verify near-fit motion before changing page-turn clamps
- **Correction:** The earlier 12dp wiggle claim is unproved. At exact fit the
  fitted dimension is `viewSize - 2 * margin`; the current `maxPan` expression
  gives zero, and the shorter dimension also clamps to zero. The margin itself
  does not permit dragging fitted paper.
- **Scope/proof:** Reproduce unwanted motion near `FIT_ZOOM_TOLERANCE` (1.01)
  with real gestures before changing clamps. If confirmed, preserve zoomed
  pan/pinch and multi-pointer suppression. Depends on B14 and B-multitouch-swipe.
  Keep N26 as a risk, not a confirmed cosmetic defect.

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
  `android.graphics.pdf.LoadParams` passed to
  `PdfRenderer(ParcelFileDescriptor, LoadParams)`, memory only (corrected:
  there is no `PdfRenderer.Params`). API 31-34 with SDK extension S 13:
  `android.graphics.pdf.PdfRendererPreV(ParcelFileDescriptor, LoadParams)`
  (api-versions.xml: `sdks="31:13,33:13,34:13,35:13,0:35"`). Otherwise
  (API 29-30, or 31-34 without the extension): `setAllSecurityToBeRemoved(true)`
  private copy for rendering, delete on replacement; plaintext at rest only
  on those devices. Re-encrypt with user password and original permissions;
  never persist passwords. Depends on G9(a) and temporary-source lifecycle.
- **Rendering seam (2026-10-06 review, confirmed, medium):** put rendering
  behind a small internal interface (pageCount; openPage returning width,
  height and render) with three implementations: `PdfRenderer(fd, LoadParams)`
  on SDK_INT >= 35; `PdfRendererPreV(fd, LoadParams)` when SDK_INT >= 31 and
  `SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13`, annotated
  `@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)` so
  lintDebug (a CI gate) passes NewApi; the decrypted private copy otherwise.
  Keep the password only in PdfEngine memory, never in Draft or draft.json.
- **Cold start (missed by the original scope):** the password is never
  persisted, so after process death restore(), inspect and load hit
  InvalidPasswordException, which becomes PASSWORD_PROTECTED under
  `during(DRAFT_UNREADABLE)`. `DocumentService.restore` returns a distinct
  `NeedsPassword(draft)` outcome instead of throwing; the model shows the
  prompt and calls `unlock(password)` before inspect and render; B8's
  set-aside path (#24/#25) must exclude this case. Where:
  document/PdfEngine.kt:82-94, 146-150; document/DocumentService.kt:64-69.
- **Proof:** Wrong password retries; right password inspects pages and exports
  a copy that loads only with the password and retains restrictions. PDFium
  password fixture via `PdfDocument(path, password=…)` if supported.
  Define cold-start reauthentication and temporary plaintext lifetime before
  implementation; a password prompt must never quarantine a valid ink draft.
  JVM: save a draft for a user-password fixture
  (`PdfEngineSecurityTest.protectedPdf(user = "pw")`): restore() returns
  NeedsPassword and draft.json is unchanged; a wrong password keeps
  prompting; the right one inspects the pages (render needs a fake or a
  device). Device checks on API 33 with S-extension 13 and on API 35: the
  PDF renders and files/documents holds no decrypted copy.

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
  - **Inventory (2026-10-06 review, plausible, low):** the debug APK holds
    exactly three AndroidX LICENSE.txt files (annotation, collection,
    lifecycle-common); the PDFBox-Android 2.0.27 AAR and the Bouncy Castle 1.72
    jars contain no licence or NOTICE entries, so no packaging change can
    surface them. The planned list (PDFBox-Android, Bouncy Castle, AndroidX)
    misses the Kotlin stdlib and kotlinx-coroutines (Apache-2.0, pulled in by
    lifecycle), Liberation Sans (SIL OFL 1.1,
    assets/com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf) and
    the Adobe CMap and glyph-list resources (BSD-3-Clause headers). README
    credits only AndroidX and PDFBox-Android (README.md:107-111). Narrowed:
    bcprov contains `org/bouncycastle/LICENSE.class` (licence text inside a
    class, kept by the keep rule but unreadable to users), the font carries its
    copyright and an OFL reference in name IDs 0, 13 and 14, and the CMap files
    carry their headers inline.
  - **Approach:** app/src/main/assets/licenses/ with the Apache-2.0 text once,
    the Apache PDFBox 2.0.27 NOTICE, PDFBox-Android's copyright line, the Bouncy
    Castle MIT licence with its copyright, the full OFL-1.1 text with the
    Liberation copyright, the Adobe BSD-3 notice and the Kotlin and kotlinx
    NOTICE, plus an index file mapping component to file. Show it from the
    "Open-source licences" entry (F25's menu is a natural home). Update README
    "Dependencies". CI guard: a small Python zipfile check after assembleRelease
    that fails if any expected assets/licenses/* entry is missing. Note in
    AGENTS.md that each new dependency needs an entry.
  - **Acceptance:** the release APK listing contains every assets/licenses/*
    file; a Robolectric test opens the licences screen and finds "Apache PDFBox"
    and "Bouncy Castle"; deleting one file makes the CI guard fail.
- **G2. Multiple documents** (see F9).
- **G3. Close and clear** (see F12).
- **G5. Explicit backup/transfer policy.** Confirmed `allowBackup=false`
  without `android:dataExtractionRules`; lint flags it. Decide cloud and
  Android 12+ device-transfer treatment for PDFs, ink and preferences, add
  `res/xml/data_extraction_rules.xml`, align README. Earlier proposal:
  exclude `files/documents`/`pen` from cloud, allow D2D; not an adopted policy.
  Proof: manifest/resource checks plus real backup/transfer verification.
  #28 implements cloud exclusion and document/pen-only D2D includes while
  retaining `allowBackup=false`. OEM-dependent transfer is documented; actual
  migration remains device verification, not an unimplemented rules task.
- **G6. Cache page boxes in the draft.** `DocumentService.restore` reloads
  the whole PDF with PDFBox only to recompute `PageSpec`s on every cold
  start. Store them in `draft.json` (with a schema version) and fall back to
  `inspect` when absent. Validate cached boxes; preserve old drafts and B8
  recovery. Proof: old-schema restore, no redundant inspect with valid cache,
  cold-start measurements before/after. Depends on checked draft commits.
  Coordinate N40/D-replay timing metadata in the same schema-version design.
  - **Baseline and early editor (2026-10-06 review, confirmed, low):** grep
    finds no `reportFullyDrawn` and no Trace sections in app/src/main, so
    "measure cold start" has no baseline. Restore runs everything in series on
    the worker before anything is shown: the full JSON decode (ink and
    savedInk), a PDFBox load and page-tree walk (inspect), a second xref parse
    by PDFium when the renderer opens, and a 2048px render. Where:
    document/DocumentService.kt:11, 64-69; ui/EditorViewModel.kt:66-75;
    ui/MainActivity.kt:535-545, 596-600. Approach: wrap the restore stages in
    `Trace.beginSection`/`endSection` ("restore.draft", "restore.inspect",
    "restore.render") and call `reportFullyDrawn()` once from MainActivity.show
    when the first editor state with a preview, or the welcome screen, appears.
    Once PageSpecs are cached (also cache B34's usable page count, R-rtl-swipe's
    reading direction and F16's outline and labels if they land), let restore
    return an OpenDocument with `preview = null` (B8's nullable preview) and
    render afterwards through renderVisible, so ink and chrome appear before
    PdfRenderer opens; the blank-page path already handles `preview == null`.
    Acceptance: device `adb shell am start -W -n
    ch.lkmc.asterinked.debug/ch.lkmc.asterinked.ui.MainActivity` and logcat
    "Fully drawn", before and after; Perfetto shows the three sections; JVM
    EditorViewModelPagingTest with a `FakeDocuments.restore` that returns
    `preview = null` publishes `busy = false` with the draft, then the preview
    after the worker runs.
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
- **G12. MainActivity handles SharedPreferences and FileProvider itself.**
  - **Why:** Severity low (plausible; an optional refactor with no user-visible
    defect; whether UI-only preferences count as a "low-level mechanism" under
    AGENTS.md is debatable). The activity reads and writes the "pen" prefs
    directly (MainActivity.kt:72, 126-130, 477-482), with key names and coerceIn
    sanitising, and turns a cache File into a FileProvider URI (686-697) with an
    authority suffix defined in three places (manifest, MainActivity.kt:735,
    ShareTest.kt:80), while the matching path constant lives in DocumentService.
    Three test classes hard-code the prefs file name "pen"
    (MainActivityLayoutTest.kt:41, MainActivityChromeTest.kt:35,
    UiScreenshotTest.kt:46); renaming either breaks only at runtime.
  - **Where:** ui/MainActivity.kt:72, 126-130, 477-482, 686-697, 734-735;
    ui/EditorViewModel.kt:41, 153-157; document/DocumentService.kt:28-29,
    130-132.
  - **Approach:** (1) `DocumentOperations.share` returns a content Uri built in
    DocumentService with `FileProvider.getUriForFile(context,
    "${context.packageName}$FILE_AUTHORITY_SUFFIX", output)`, the suffix
    constant next to SHARED_DIRECTORY; `EditorState.shared` becomes `Uri?`;
    sendToShareSheet only builds the chooser; update the
    EditorViewModelPagingTest fake. (2) `PenSettings(colorIndex, widthIndex,
    mode, kind)` and `PenSettingsStore(context)` in ui/ with load() (sanitising
    as today) and save(); MainActivity holds one PenSettings value instead of
    four vars; tests use `PenSettingsStore.FILE_NAME`. Keep the file name "pen"
    and the existing keys so installed users keep their settings, and keep the
    eraser out of persistence. F-presets/U13 (#61, per-tool colours), U10 (#62,
    hint counters), U9 (#58, `stylusSeen`) and F28 add keys here. The review
    advised doing this first; since #58, #61 and #62 already add keys directly,
    do it after they land and move their keys in the same change.
  - **Acceptance:** ShareTest: `service.share(draft)` returns a content:// Uri
    with authority `"${app.packageName}.files"` whose stream yields the PDF.
    PenSettingsStoreTest round-trips, clamps out-of-range indices like today,
    and loads a prefs file written with the old keys. `grep -n
    "getSharedPreferences\|FileProvider"
    app/src/main/java/ch/lkmc/asterinked/ui/MainActivity.kt` is empty.
    **Effort:** S.
- **G13. Export temp files, PDFBox scratch files and shared copies outlive the
  process.** (The 2026-10-06 review's G10.)
  - **Why:** Severity low, confirmed (everything stays app-private and the
    system purges cacheDir under storage pressure). `DocumentService.export`
    writes cache/annotated-*.pdf (DocumentService.kt:102), and PDFBox's
    ScratchFile spills to cache/PDFBox*.tmp (setupMixed with cacheDir,
    PdfEngine.kt:146-147); both are removed only in `finally` or `close()`, so a
    process killed during a long export leaves files as large as the document.
    cache/shared/<uuid>/Name-annotated.pdf, an annotated copy of a possibly
    sensitive document, stays until the next share (the documented policy in
    `DocumentService.share`'s KDoc), even after another document is opened.
  - **Where:** document/DocumentService.kt:34-37, 48-69, 87-110;
    document/PdfEngine.kt:146-147.
  - **Approach:** Move export temp and PDFBox scratch files into cacheDir/work/
    (`PdfEngine(File(cacheDir, "work").apply { mkdirs() })` and
    `File.createTempFile("annotated-", ".pdf", workDir)`), so a sweep cannot hit
    unrelated cache files. Add `DocumentService.sweep()`, run as the first
    worker task (start of restore()): empty work/; delete shared/ folders whose
    lastModified is older than a SHARE_RETENTION constant, and update the share
    KDoc. Leave files/documents orphans to saveDraft and B8: an unreferenced PDF
    may be the only copy B8 wants to recover. The reviewers differ on retention
    (1 hour plus deleting shared/ in open(), versus a day); recommended: 1 day
    and no deletion in open(), because some recipients read the URI late (mail
    apps reading on send). Design the retention together with B-share-lifetime's
    per-share copies. Include F27's cache/print/ if it lands. Only safe after
    B-session-worker (#40): with per-ViewModel workers a sweep can delete
    another worker's in-flight temp file.
  - **Acceptance:** Robolectric test that calls `sweep()` directly (restore()
    would reach PdfRenderer): cacheDir/work/annotated-1.pdf,
    cacheDir/work/PDFBox1.tmp and cache/shared/<uuid>/x.pdf dated two days ago
    are removed, while a share made a minute ago and the draft's source remain.
    A model test asserts that sweep is the first worker call. **Effort:** S.
- **G14. Localization readiness: English file names, a fragment string, no
  pseudo-locale run.** (The 2026-10-06 review's G11.)
  - **Why:** Severity low (plausible; latent, because the app ships only
    res/values). The export suffix "-annotated" and the fallbacks "Document",
    "Document.pdf" and "Document-annotated.pdf" are Kotlin constants
    (DocumentStore.kt:37, 43-44, 59; MainActivity.kt:736), so every translated
    build names files in English. The page dialog builds "[field] of 12" from
    the fragment `of_pages`, placed after the field in code (strings.xml:30-31,
    PageDialog.kt:42-46); languages that put the total first cannot reorder it.
    Nothing runs a pseudo-locale. Withdrawn by the verifier:
    `page_out_of_range`'s "1" is translatable text, and the field's ASCII digits
    are deliberate (PageDialog.kt:41) while TYPE_CLASS_NUMBER is already
    locale-aware. The measured Save label is V17.
  - **Where:** document/DocumentStore.kt:37, 43-44, 59; ui/MainActivity.kt:736;
    res/values/strings.xml:30-31; ui/PageDialog.kt:42-46;
    app/build.gradle.kts:30-34.
  - **Approach:** Before the first translation: move the suffix and fallbacks to
    strings.xml and pass them into `Draft.exportName` (Draft has no Context, so
    `exportName(suffix, fallback)`, or let DocumentService supply them); the
    add-the-suffix-once rule must still strip the legacy English "-annotated"
    (B11 regression). Replace `of_pages` with a full sentence that takes both
    values (V21 also touches that row's alignment). Enable
    `isPseudoLocalesEnabled = true` on the debug build type and add
    UiScreenshotTest and MainActivityLayoutTest cases with en-rXA and ar-rXB; if
    Robolectric's resource table lacks pseudo-locales, check on an emulator with
    Developer options. This also closes B12's file-name fallback part.
  - **Acceptance:** ShareTest: a name already carrying the translated or the
    legacy suffix stays single-suffixed. Screenshot tests for en-rXA and ar-rXB
    at 360dp. **Effort:** M.

---

## 4. Backlog: performance

Complexity and capacity estimates below are not device measurements.
P-eraser and P-history are implemented on awaiting branches, not open work;
P11 awaits #26. The 2026-10-06 review's ms and MB figures are estimates
unless it says measured.

- **P-prefetch. Skip speculative previews that cannot stay cached.**
  `document/DocumentService.kt:37-42,112-113` budgets a heap fraction; Letter
  preview is about 12.4MiB versus about 10.7MiB cache on a 64MiB heap, so it
  evicts itself. `ui/EditorViewModel.kt:223-228` still prefetches both neighbours
  on the autosave/export worker. Make speculation capacity-aware. Proof:
  size-limited fake skips speculation but renders visible pages; sufficient
  cache speeds forward turns. Measure small-heap device queue delay. Depends
  on the process-owned worker and T2's order contract.
  - **Wrong metric and no trim (2026-10-06 review's P18, confirmed, low):**
    since API 26 bitmap pixels live in native memory, so `Runtime.maxMemory() /
    6` bounds the wrong thing: a 128 MB heap growth limit fits one 13 MB
    preview (prefetch renders the previous page and evicts it at once with the
    next), while a 512 MB tablet heap keeps six previews (about 78 MB native)
    when at most three are useful. There is no onTrimMemory anywhere, so 40-80
    MB of native bitmaps stay while the app is in the background. Where: also
    ui/MainActivity.kt (no onTrimMemory). Approach: count capacity in previews
    from a pure `previewCapacity(lowRam, totalMem)`: 3 normally, 1 when
    `ActivityManager.isLowRamDevice()` or `MemoryInfo.totalMem` < 2 GiB
    (`sizeOf = 1`); when capacity < 3, prefetch only page + 1; add
    `trimPreviews()` to DocumentOperations, run on the worker from
    `MainActivity.onTrimMemory(level >= TRIM_MEMORY_UI_HIDDEN)`, evicting
    everything except the visible page; prefetch again in onStart. Never
    `recycle()` an evicted bitmap, because `EditorState.preview` may still
    reference it. F3's thumbnail cache must use entry counts too. Acceptance:
    JVM unit test for previewCapacity; EditorViewModelPagingTest with a fake
    reporting capacity 1 renders only page + 1 after goToPage; Robolectric
    `activity.onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` makes the fake record
    trimPreviews; device `dumpsys meminfo` native heap drops by about two
    previews after backgrounding. Effort: M. B23 covers the draft writes queued
    behind these renders.
- **P-page-tree. Iterate export pages once.** `document/PdfEngine.kt:103-105`
  uses `getPage(index)` per annotated page; pinned PDFBox repeatedly builds/
  scans flat-node children, giving quadratic all-page traversal. Iterate
  once and look up ink by index; explicitly validate draft page keys. Proof:
  dense-ink and last-page-only flat PDFs, page targeting, allocation/time
  scaling and PDFium proof. No export geometry change.
  The same `document.pages.toList()` change fixes B34's export mismatch with
  wrong `/Count` trees (getPage descends by /Count, the iterator by /Kids);
  land them together with B34's raw-byte fixtures.
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
  - **Committed ink per edit (2026-10-06 review's P12, plausible, medium;
    lowered from high; needs a device trace):** show() sets `inkNodeStale`
    whenever the page's stroke list instance changes (InkPageView.kt:186-193):
    every addStroke, eraseStrokes, undo, redo and page turn. drawCommittedInk
    (256-263) then re-records drawStrokes over every pen stroke, one
    setStrokeWidth plus drawLine or drawCircle per segment (531-540), and
    re-renders inkLayer (266). Segments are at most 0.75pt (SEGMENT_LENGTH,
    Ink.kt:21), so a dense handwritten page carries 30k-80k segments: an
    estimated 6-20 ms of UI-thread record time per commit plus a full
    RenderThread replay, right after pen-up when the next stroke starts.
    eraseAlong (453) re-records on every sample that hides another stroke. P1-P3
    (Done) fixed per-frame cost; per-edit cost still scales with all ink on the
    page. Where: ui/InkPageView.kt:68, 186-193, 256-281, 293-302, 451-453,
    531-540; ink/Ink.kt:21, 51-61. Approach: measure first with Perfetto on a
    200+ stroke page. If pen-up frames are over budget, start with an
    append-only fast path: a "base" chunk RenderNode and a "recent" one;
    addStroke re-records only "recent" and folds it into a new base past about
    64 strokes. Then content-defined chunks: runs of consecutive PEN strokes
    (keeps z-order) with a boundary after a stroke whose
    `System.identityHashCode(stroke) and 31 == 0`, capped at 64 strokes, so
    inserting or removing a stroke only touches its own chunk (fixed index
    ranges would shift every later chunk). Each chunk node gets
    `setClipToBounds(true)` and `setPosition` to the union of its segment bounds
    padded by maxWidth/2 + 1 (round caps and dots included) and records with
    `translate(-left, -top)`. inkNode records only `drawRenderNode(chunk)` per
    chunk and is re-recorded only when the chunk list changes, so HWUI damages
    only the changed chunk and quick-rejects off-screen chunks at high zoom. Put
    the planning in a pure, JVM-testable `InkChunks.plan(previousChunks,
    strokes, hidden)`. While erasing, re-record only chunks holding newly hidden
    strokes. Keep highlights outside (multiply, #10 + #17), keep the stale
    handling for a cancelled erase (#10 + #14), discard every chunk in
    onDetachedFromWindow, and leave the software drawStrokes path unchanged for
    Robolectric. Acceptance: JVM InkChunksTest: appending 1 stroke to a
    1000-stroke page changes exactly 1 chunk; hiding or removing one stroke in
    the middle changes at most 2; undo back to an earlier list instance reuses
    all its chunks; the flattened chunk order equals stroke order; the NATIVE
    pixel tests in InkPageViewTest still pass; device `adb shell dumpsys gfxinfo
    ch.lkmc.asterinked.debug framestats` while writing short strokes on a
    200-stroke page shows no frame over 16 ms at pen-up. Effort: L.
  - **Live stroke and committed highlights per frame (its P22, plausible, low;
    speculative until profiled: recording 800 drawLine ops costs well under 1
    ms):** on hardware canvases onDraw draws all committed highlights directly
    with MULTIPLY (InkPageView.kt:222-227) and drawInk over every `live.settled`
    segment (238-241) on each input frame, so recording is O(highlights + live
    segments): a 600pt signature (about 800 segments) re-records about 800 ops
    per frame at 120 Hz. MULTIPLY is not a coefficient blend mode, so GPUs
    without advanced-blend support need a destination read per such draw. Where:
    ui/InkPageView.kt:218-247, 284-302. Approach: profile first; only if
    live-stroke record time grows visibly with stroke length, record the settled
    live segments in blocks of 256 into RenderNodes (page units, same paint),
    draw them with drawRenderNode, re-issue only the open block and the tail per
    frame, and discard the blocks in cancelStroke (opaque same-colour ink hides
    block boundaries). A page-plus-highlights layer (multiply is correct inside
    it because the page is opaque, unlike #10's transparent pen layer) is a
    separate experiment with N28's pan cost; keep the B16/B17 order (page,
    committed highlights, live highlight, pen ink, live pen) and fill white
    inside the layer when `preview == null`. Acceptance: device gfxinfo or
    Perfetto during one continuous 10 s scribble shows UI-thread record time per
    frame staying flat; on a page with 30 highlights GPU frame time while
    writing drops; JVM: NATIVE pixel tests unchanged, plus a unit test of the
    block-splitting index math. Effort: M.
  - **Live highlighter path (P10 refinement, confirmed, low):** besides the
    per-frame rebuild, finishStroke seeds only `geometryCache` with the builder
    output (InkPageView.kt:497), so show() still runs `highlightPath`
    (centerline plus segments again) for the committed highlight (190, 516-523),
    and `geometryCache.update` (188) computes `InkGeometry.segments` for
    HIGHLIGHTER strokes that drawStrokes never reads (298-299). Where:
    ui/InkPageView.kt:188-191, 284-289 (L288), 298-299, 481, 492-500, 516-523;
    ink/Ink.kt:34-38. Approach: keep `liveHighlight = Path()` and
    `appendedSettled = 0`; in addSamples, when `activeKind == HIGHLIGHTER`,
    `lineTo(segment.end)` for each new settled segment (moveTo plus a
    zero-length lineTo for the first point, as highlightPath does); in
    drawHighlights copy it into a reusable draw path
    (`drawPath.set(liveHighlight)`), append the tail ends and draw once (it must
    stay one path: drawing settled part and tail separately with multiply
    darkens their overlap); finishStroke stores the final path in
    `pendingHighlightPaths[stroke]`, and show() uses it before calling
    highlightPath; give InkGeometryCache a compute that returns `emptyList()`
    for HIGHLIGHTER strokes. Acceptance: Robolectric NATIVE
    `aHighlightBeingDrawnAlsoSitsUnderPenInk` and
    `highlighterTintsThePageButKeepsDarkContentDark` pass; a live highlight and
    the same highlight after pen-up produce identical pixels, with no darker
    junction; JVM with a counting compute lambda, committing a highlight
    computes no pen geometry for it.
- **N29. InkStrokeBuilder computes and discards a tail span per sample.**
  `InkStrokeBuilder.add` allocates its tail list per sample. The
  source record considers this small at stylus rates; measure allocation and
  only reuse the tail if the incremental highlighter shares this path or a
  profile proves need. Preserve every-prefix results and mutable-tail ownership.
  - **2026-10-06 review (its P15, confirmed, low; a constant factor on all bulk
    geometry work):** `add` (Ink.kt:109-125, L122) allocates a new ArrayList and
    `appendSpan(end, point, point)` on every accepted sample, but only the last
    tail is ever read (by `segments()` at :125 and by onDraw once per frame).
    With sample spacing s the tail is about s/2 long, so 50% of allocated
    segments are wasted at s <= 0.75pt (a 240 Hz stylus) and about 33% above.
    The cost is not only live: `InkGeometry.segments` runs through this builder
    on every uncached page show, inside `centerline()` for every highlight, and
    in `PdfEngine.export` for every stroke (125). Where: ink/Ink.kt:24-28,
    96-126 (L114, L122, L125); document/PdfEngine.kt:125. Approach: make the
    tail lazy: add() stores the tail inputs (end and point, or the first-point
    dot) and clears a cached tail; the `tail` getter computes and caches
    `appendSpan(ArrayList(), tailStart, tailEnd, tailEnd, width)` or
    `listOf(dot)` on first access; `segments()` reads the getter. Return the
    same list instance between adds (onDraw reads it once per frame), and keep
    sanitize-rejected samples from touching the cache. Geometry does not change,
    so the InkIncrementalTest export contract holds. Acceptance:
    `batchGeometryMatchesTheOriginalAlgorithm`,
    `everyLivePrefixMatchesBatchGeometry` and
    `invalidSamplesAreSkippedLikeTheBatchPath` pass unchanged; a counter-based
    test (more robust than an allocated-bytes threshold): a 2000-sample stroke
    computes samples - 1 settled spans and exactly one tail. Effort: S.
    Recommendation: because the batch paths (page show, export) also pay it and
    the change is geometry-neutral under InkIncrementalTest, the lazy tail does
    not need to wait for a live-stroke profile; P14 builds on it.
- **N28. Hardware ink-layer raster cost during pan/zoom.** Source record:
  `InkPageView.inkLayer` redraws vector ink into its GPU layer texture per
  transformed frame, preserving crispness; no measured device jank establishes
  a defect. Proof first: hardware traces at 200+ strokes while panning/zooming.
  If material, trial raster reuse per zoom level with correct scale/clip and
  no stale erase/undo pixels. Coordinate with P8/P-live-chunks; sharp PDF tiles
  alone do not prove ink-layer cost disappears. Keep software fallback and
  hardware invalidation checks; this is an exploratory rendering tradeoff.
  - **2026-10-06 review (its P13, plausible, medium; GPU and bandwidth cost
    needs a device; most of the pan cost is re-rasterising the ink, which this
    does not remove):** drawCommittedInk re-records inkLayer whenever
    `recordedLayerRect != pageRect` (InkPageView.kt:266), true on every pan,
    pinch and zoomAnimator frame, so HWUI re-renders the offscreen layer and
    composites it: one extra view-sized RGBA pass per frame (about 14.8 MB
    written plus read per frame on a 2560x1450 workspace). drawCommittedInk runs
    on every hardware canvas (228) and inkLayer always holds a drawRenderNode
    op, so a view-sized layer exists on pages without ink. It spans the whole
    view even when the page covers 37% of it (about 470 of 1280 px wide in the
    tablet-landscape screenshot). followFingers (389-394) and zoomAround
    (399-407) invalidate on every MOVE even when clampPan leaves pan and zoom
    unchanged (a swipe at fit, a pinch past MAX_ZOOM). Where:
    ui/InkPageView.kt:68-69, 218-229, 256-281, 389-394, 399-407.
  - **Its approach (different from the raster-reuse trial above):** (1) skip the
    layer and call `inkLayer.discardDisplayList()` when no visible PEN stroke
    remains (count strokes hidden by an active erase). (2) and (3) ship only
    together: while a finger pan, pinch or zoomAnimator runs (a `transforming`
    flag set on the first pan or scale change, cleared on ACTION_UP/CANCEL and
    on animator end or cancel), draw inkNode directly on the view canvas inside
    the same clip, translate and scale, and re-record the layer once when the
    gesture ends; and size the layer to pageRect intersected with the view,
    rounded out (`inkLayer.setPosition(l, t, r, b)`, record
    `translate(pageRect.left - l, pageRect.top - t)`). Sizing alone would change
    the layer size on every pan frame of a partly off-screen page and reallocate
    its texture each time, which is worse than today. (4) In followFingers and
    zoomAround compare pan and zoom before and after clampPan and invalidate
    only on change. Keep the highlight-then-pen order (B17) on both branches;
    the double-tap animation takes the direct path too. Recommendation: (1) and
    (4) are cheap, device-independent wins; (2)+(3) and per-zoom raster reuse
    both need the hardware traces first, and only one of them should land.
  - **Its acceptance:** Robolectric: a one-finger drag across the page at fit in
    PEN mode leaves the view clean (a test-only invalidation counter), and
    pinching past MAX_ZOOM stops invalidating. Device: Perfetto or `dumpsys
    gfxinfo` during `adb shell input swipe` pans at 2.5x on a 200-stroke page
    shows no committedInkLayer update during the pan and fewer janky frames;
    "Layers" memory in gfxinfo drops on pages without ink. Effort: M.
- **P5. Draft writes are O(total ink).** Even coalesced (#13), each write
  serializes all pages twice (`ink` and `savedInk`) with `org.json` boxing.
  Options, simplest first: store `savedInk` as a revision marker instead of
  a copy; write one file per page and rewrite only the changed page; or an
  append-only journal compacted on export. Acceptance: a benchmark test with
  5k strokes shows write cost tied to the changed page, independent of other
  pages; dirty/export recovery remains correct. Depends on checked commits,
  session ordering and an explicit schema migration; choose the smallest option.
  - **Memory failure mode (2026-10-06 review; encoder finding confirmed, medium;
    memory and debounce findings plausible, low):** beyond write time, saveDraft
    builds two full org.json trees (per point a JSONArray from `listOf(x, y,
    p)`: three boxed Floats, an Arrays$ArrayList and a JSONArray with its own
    ArrayList), then `json.toString()`, then `toByteArray()`; restore reads the
    whole file into one String and builds the same tree with boxed Doubles.
    Estimated 60-100 MB of short-lived garbage per write for a 100k-point
    document and a peak of about 0.6 KB per point (unmeasured). An
    OutOfMemoryError then shows "Your latest notes couldn't be stored" after
    every stroke, fails an export after the PDF was written (N1), or on restore
    maps to OUT_OF_MEMORY ("This PDF is too large to handle on this device"),
    which wrongly blames the PDF. `InkPageView.addSamples` keeps every
    historical sample without decimation, so point counts are high. saveDraft
    catches only Exception, so an OOM skips failWrite; harmless in practice (on
    API 30+ the next startWrite truncates draft.json.new and openRead ignores
    it; API 29's legacy AtomicFile restores the .bak). Where:
    document/DocumentStore.kt:77-102 (L86, L98), 104-129;
    document/DocumentService.kt:64-75, 120-128.
  - **Its approach:** (1) Measure first: peak allocation for a 400k-point draft
    in a JVM test (Runtime totalMemory deltas or
    `ThreadMXBean.getThreadAllocatedBytes`). (2) Stream both directions with
    `android.util.JsonWriter` over
    `BufferedWriter(OutputStreamWriter(draftFile.startWrite(), UTF_8))` and
    `android.util.JsonReader` over a buffered reader, in the same key layout
    (`{source, name, page, ink: {"<page>": [{color, width, points: [[x, y, p],
    ...], kind?}]}, savedInk}`); keep omitting "kind" for pens and the
    unknown-kind fallback to PEN. Write coordinates with `value(Number)` on the
    boxed Float, since `value(double)` prints the widened double (312.45679
    becomes 312.4567871...); read with `nextDouble().toFloat()`. The output is
    not byte-identical (org.json writes whole floats as "1", JsonWriter as
    "1.0"; both parse). Flush before finishWrite, and use `catch (error:
    Throwable) { failWrite(output); throw error }`. (3) Keep the reader strict:
    org.json already rejects NaN and Infinity while parsing (B19), so a lenient
    reader would change behaviour. (4) In `DocumentService.restore` map an
    OutOfMemoryError from `store.restore()` to DRAFT_UNREADABLE rather than
    OUT_OF_MEMORY, and make sure B8 (#24/#25) does not set a valid large draft
    aside. (5) Then store savedInk as a revision marker (the first option
    above), which halves the size and makes P16 moot. (6) Write debouncing only
    if profiling after streaming still shows write cost: saveDraft re-posts a
    main-thread Runnable after DRAFT_IDLE_MS (about 1500 ms), capped at about 5
    s after the first unsaved change, plus `flushDraft()` before open, export
    and share, in onStop and in onCleared. Pitfalls: a delayed write for the old
    document that lands after open() points draft.json back at the old source
    and makes saveDraft delete the new PDF (flush or cancel before open);
    export's `saveDraft(saved)` must clear `unsavedDraft`, or a later debounced
    pre-export draft overwrites savedInk; keep the error notice; note the
    durability window (up to about 5 s of ink lost on a foreground crash) in
    AGENTS.md. Debouncing pulls against B23's short loss window, so prefer B23
    plus streaming.
  - **Its acceptance:** DocumentStorePersistenceTest: existing fixtures
    round-trip (new writer to old org.json reader, and an old-format file to the
    new reader). Golden test: a checked-in JSON written by the old encoder
    restores to an equal Draft, and the new encoder's output parsed with
    org.json equals the old output structurally; compare decoded Drafts, never
    text. A stroke list whose iterator throws OutOfMemoryError mid-write leaves
    restore() returning the previous draft and no draft.json.new. A 200k-point
    draft round-trips. Allocation: at least 2x less than org.json for a
    100k-point draft (the reviewer's "under 3x the file size" is likely
    unreachable with Float.toString and boxing). Device: a synthetic large draft
    keeps saving and restoring on a 256 MB-heap device. Effort: M.
- **P6. Heavy exports.** Every pen segment is its own `w m l S` path. Quantize
  widths (for example 1/16 of the nominal width) and emit runs of equal-width
  segments as one polyline, using the same quantized geometry on screen to
  keep preview == export. Update `InkIncrementalTest`'s reference
  deliberately (see AGENTS.md). Benchmark bytes/render time first. Acceptance
  target: over 5x fewer content-stream bytes for a 200-stroke page, aligned
  PDFium proof. Depends on deliberate geometry approval; unlike eraser/B14,
  this changes the export contract.
  - **Lossless first step (2026-10-06 review, confirmed, low):** finger and
    mouse input use the constant TOUCH_PRESSURE (InkPageView.kt:478), so every
    segment of a finger stroke has the same width up to float rounding, and
    light stylus pressure clamps to MIN_WIDTH 0.4 (Ink.kt:78) in runs. Such runs
    can be one `w m l l ... S` path without touching `InkGeometry.segments`, so
    the InkIncrementalTest contract stays. Quantization is still needed for
    varying-pressure stylus strokes; this step mainly helps touch-ink users.
    Where: document/PdfEngine.kt:125-136; ui/InkPageView.kt:478, 531-540;
    ink/Ink.kt:47, 58, 78. Approach: in `PdfEngine.export` group consecutive
    non-dot segments where `abs(width - runWidth) <= 1e-4 * runWidth` and each
    start equals the previous end; emit each run as one
    `setLineWidth(runWidth)`, a moveTo, one lineTo per segment and a single
    `stroke()`; dots stay filled circles; skip setLineWidth when unchanged. A
    round-join polyline equals the union of round-capped segments only for
    opaque paint, and all ink colours are opaque. Leave screen drawing unchanged
    unless the same rule is applied to both (it would also cut P-live-chunks'
    display-list ops). Acceptance: PdfEngineExportTest: a 200-segment stroke
    with every pressure at 0.65 exports exactly one `S` (strokeOps helper) and
    its content stream shrinks by more than 50%; a varying-pressure stroke keeps
    one S per segment unless widths match; verify_pdf.py ink placement unchanged
    at all four rotations (compare renders, not bytes, because joints anti-alias
    slightly differently).
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
- **P11. Export adds a new ExtGState and its own q/Q group for every highlight
  (awaiting #26).**
  - **Why:** Severity low, confirmed. `PdfEngine.highlight` creates a fresh
    PDExtendedGraphicsState per highlighter stroke (PdfEngine.kt:174).
    `PDResources.add` reuses a key only when the same COSDictionary instance is
    already present (containsValue; checked with javap), so each highlight adds
    /gs1, /gs2, ... with a linear scan per add, each wrapped in its own `q ...
    gs ... Q`. `PDPage.getResources` returns the inherited dictionary when a
    page has none, so highlights of every page pile up in one shared dictionary.
  - **Where:** document/PdfEngine.kt:116-124, 168-182.
  - **Approach:** One PDExtendedGraphicsState (blendMode = MULTIPLY) per export,
    so `PDResources.add` returns the same /gsN per page. Strokes are already
    sorted highlights first, so emit a page's highlights inside one `q / gs /
    ... / Q` block, setting colour, width and `S` per highlight (each still
    multiplies separately); pen ink follows after `Q` with Normal blending. B26
    (#60) changes the per-highlight path in the same function.
  - **Acceptance:** PdfEngineExportTest: a page with 50 highlights has exactly
    one ExtGState entry with /BM /Multiply and exactly one `gs` operator in the
    appended stream. `export_highlighterIsOneMultipliedPath` and
    `export_highlightsSitUnderPenInkWhateverTheirOrder` still pass; the
    "highlight" case in verify_pdf.py is unchanged. **Effort:** S.
- **P14. Page turns recompute stroke geometry on the UI thread, and the cache
  forgets the page you came from.**
  - **Why:** Severity low, confirmed (the mechanism is certain; the frame cost
    is a few ms on typical pages and more on dense ones).
    `InkGeometryCache.update` (Ink.kt:142-147, L145) replaces its entries with
    only the current list (`InkIncrementalTest.cacheSmoothsOnlyNewStrokes` even
    asserts "Removed strokes are forgotten"), so flipping A, B, A recomputes A
    in full in `InkPageView.show` (188) on the main thread, and highlightPaths
    (189-191) drops the previous page's paths too. Each segment costs two
    allocations, a quadratic evaluation and hypot calls: a 30k-80k segment page
    means 60k-160k allocations in the page-turn frame, followed by
    P-live-chunks' full re-record. #13's instant page turns hold for the bitmap
    but not for dense ink.
  - **Where:** ink/Ink.kt:133-147; ui/InkPageView.kt:57, 185-193;
    ink/InkIncrementalTest.kt:37-56.
  - **Approach:** Do N29's lazy tail first, which lowers this cost directly. (1)
    Retain the last N = 3 update() generations: an ArrayDeque of
    IdentityHashMaps, newest first, dropping the oldest on push; the same for
    highlight Paths. Change `cacheSmoothsOnlyNewStrokes` on purpose: strokes are
    forgotten after N generations. Keep identity keys
    (`structurallyEqualStrokesKeepSeparateEntries`). Memory is bounded to about
    3 pages of segments (about 56 B each). (2) Optional: warm page plus and
    minus 1 from a `MessageQueue.IdleHandler` in about 2 ms slices
    (System.nanoTime budget) through `cache.seed`; remove it on page change,
    detach or stroke start, and return false once done. The cache stays
    main-thread-confined, so no locking. To test the warming from Robolectric,
    let InkPageView accept the compute function (internal constructor parameter
    or test hook).
  - **Acceptance:** JVM with a counting compute lambda: update(A); update(B);
    update(A) computes A once; after N + 1 other generations A is computed
    again. Robolectric: show page 0 while page 1 has strokes, idle the main
    looper, show page 1: the counting compute is not called for page-1 strokes
    during show(). Device: in a Perfetto trace of a turn on a 200-stroke page,
    Choreographer#doFrame stays under 8 ms. **Effort:** M.
- **P16. After a restore, every state publish deep-compares all ink with
  savedInk on the main thread.**
  - **Why:** Severity low, confirmed (lowered from medium: it costs ms per
    publish only with 100k+ stored points, and only when savedInk is non-empty,
    since an empty savedInk fails the size check at once). `Draft.dirty`
    compares the maps structurally (DocumentStore.kt:23). Within a session
    export shares list instances (EditorViewModel.kt:143), so ArrayList.equals
    short-circuits on identity; `DocumentStore.restore` decodes ink and savedInk
    separately (119-129), so after every cold start equal pages fail the
    identity shortcut and every InkStroke and InkPoint is compared (three
    Float.compare per point). `statusText()` (MainActivity.kt:763-771) runs it
    on every non-busy publish, and twice when a notice shows, because
    acknowledgeMessage re-publishes from inside show() (566). Restore also keeps
    two full copies of every exported stroke in memory.
  - **Where:** document/DocumentStore.kt:23, 96-101, 119-129;
    ui/MainActivity.kt:544, 563-567, 673, 763-771.
  - **Approach:** Canonicalise in `DocumentStore.restore`, on the worker: if
    `saved[page] == ink[page]`, use `ink[page]`; otherwise share equal strokes
    by index (`ink[page].getOrNull(i)?.takeIf { it == s } ?: s`), since saved
    strokes are usually a prefix of the current ones. Data-class equals checks
    identity first, so later checks cost O(pages) or O(strokes). Keep
    `Draft.dirty` structural (`DraftDirtyTest.undoToSavedEquality_isClean` and
    erase-back-to-saved depend on it). Sharing is safe because lists and strokes
    are immutable. Optional: memoize dirty `by lazy` (Draft is immutable). If
    P5's savedInk revision marker lands, this becomes moot.
  - **Acceptance:** DocumentStorePersistenceTest: save a draft whose savedInk
    equals ink, restore it, and `assertSame(restored.ink[p],
    restored.savedInk[p])` for every page; a dirty draft whose savedInk is a
    prefix shares the prefix strokes (assertSame). DraftDirtyTest passes
    unchanged. **Effort:** S.
- **P17. Every state publish requests a layout pass through the status line and
  page counter.**
  - **Why:** Severity low, confirmed (each pass is small, but it lands on the
    frame where the next stroke starts). `showStatus` (MainActivity.kt:606-622)
    builds a new GradientDrawable (or a mutated ic_success) on every call and
    calls `setCompoundDrawablesRelative`, which always requests layout (L621).
    `counter.text` is set even when unchanged, and the counter is WRAP_CONTENT
    with only a minWidth (284-292), so `TextView.checkForRelayout` requests
    layout too; `title.text` rebuilds its MIDDLE-ellipsized layout. Every stroke
    commit and page turn therefore triggers a root measure and layout traversal.
  - **Where:** ui/MainActivity.kt:284-292, 543-561, 606-622.
  - **Approach:** Keep the last shown values (`shownStatus: Int?` with a "never
    shown" sentinel, `shownTitle`, `shownCounter`); return early from showStatus
    when unchanged, and set title and counter text and contentDescription only
    when the string differs. Create the dot and check drawables once, lazily (a
    theme or font-scale change recreates the activity, so cached drawables are
    safe; if V27 lands, size them from textSize at creation). Keep the TalkBack
    description in step with the text. Nothing else in show() requests layout
    (SegmentedControl.setEnabled only toggles children).
  - **Acceptance:** Robolectric: `EditorScreens.publish(editing())`, settle,
    then publish a state with one more stroke on the same page (still unsaved)
    through the same seam: `window.decorView.isLayoutRequested` is false right
    after. MainActivityLayoutTest and UiScreenshotTest output are unchanged.
    **Effort:** S.
- **P19. Prefetched previews are uploaded to the GPU in the page-turn frame.**
  - **Why:** Severity low (plausible; needs a device; the gain is uncertain
    because Skia's pinned upload is not mipmapped). A prefetched 2048px
    ARGB_8888 preview (about 13 MB) with `setHasMipMap(true)` (PdfEngine.kt:66)
    is uploaded and mipmapped by RenderThread in the first frame that draws it,
    which is the page-turn frame: several ms on mid-range devices and a likely
    dropped frame at 90-120 Hz.
  - **Where:** document/DocumentService.kt:112-113; document/PdfEngine.kt:55-72;
    ui/EditorViewModel.kt:223-230.
  - **Approach:** An experiment behind measurement: call
    `bitmap.prepareToDraw()` on prefetched bitmaps only (the documented
    asynchronous RenderThread upload; safe from the worker; a no-op without a
    hardware renderer, so Robolectric is unaffected). Compare page-turn
    DrawFrame slices before and after, and keep it only if the turn frame
    improves without GPU resource-cache churn (two uploaded neighbours add about
    35 MB of GPU memory).
  - **Acceptance:** Device: in a Perfetto trace of a turn to a prefetched page,
    the texture-upload slices move out of the turn's DrawFrame into an earlier
    idle slot and the turn frame stays within the vsync budget. JVM, if routed
    through a DocumentOperations hook: the fake receives it for prefetched pages
    only. **Effort:** S.
- **P20. The page area is filled four times before the page bitmap on every
  writing frame.**
  - **Why:** Severity low, confirmed (pure fill rate, small). Four opaque fills
    cover the workspace: windowBackground `@color/surface` (styles.xml:11, not
    overridden at night), the root column's surface background
    (MainActivity.kt:196), the workspace's canvas background while editing (595)
    and InkPageView's own canvas background (InkPageView.kt:149). HWUI does no
    occlusion culling, so every writing frame repaints all four in the damaged
    area; two are exact duplicates.
  - **Where:** res/values/styles.xml:11; ui/MainActivity.kt:196, 595;
    ui/InkPageView.kt:149.
  - **Approach:** Drop InkPageView's `setBackgroundColor` (the workspace paints
    canvas while editing, and the page view is INVISIBLE on the welcome and
    loading screens) and the column's surface background, relying on
    windowBackground for the bars, including behind the transparent edge-to-edge
    system bars; check values-night/styles.xml. D-flip's alpha fades (#52's and
    the review's directional slide) rely on the view's own opaque background; if
    both land, recheck the fade on the hardware path or drop its alpha.
  - **Acceptance:** Robolectric: `InkPageView.background` is null and only one
    surface background remains above the window. UiScreenshotTest renders every
    state pixel-identical, light and dark. Device: with Debug GPU overdraw on,
    the page margins show 2x (green) instead of 4x (red). **Effort:** S.
- **P21. Exports rewrite the whole PDF; an incremental save would cost
  O(changes).**
  - **Why:** Severity low (plausible; the gain is unmeasured and the correctness
    risk is high). Every export and share loads the source and calls
    `document.save` (PdfEngine.kt:142), re-serializing every object, and
    `DocumentStore.writePdf` copies the temp file again. For a 50-100 MB scanned
    PDF with one note, export time grows with the document while the UI is busy
    (U5). PDFBox 2 writes neither object streams nor xref streams, so modern
    sources come back larger. `saveIncremental(OutputStream,
    Set<COSDictionary>)` exists in 2.0.27 (javap). The time saving is partial,
    because an incremental save still copies the original bytes.
  - **Where:** document/PdfEngine.kt:101-144;
    document/DocumentService.kt:87-110.
  - **Approach:** For unencrypted sources only: collect every page dictionary
    that got ink (APPEND with resetContext turns /Contents into an array on it),
    plus its effective /Resources and /ExtGState dictionaries, walking up to the
    Pages node when inherited; write with saveIncremental to a separate temp
    file, never the source. Keep the full save with keepProtection for encrypted
    sources (re-encryption needs new keys, which an incremental update cannot
    do). Share the touched-object collection with F30's signed-PDF path. Measure
    export time on a device before committing to it.
  - **Acceptance:** PdfEngineExportTest: a source with a 5 MB embedded image,
    exported with one stroke, gives `output.length() < source.length() + 64 KB`
    and its first `source.length()` bytes equal the source. The existing export
    tests (text preserved, rotation and crop, repeated export without duplicated
    ink, encrypted export) pass, and verify_pdf.py passes for every case, plus a
    large-image fixture. **Effort:** L.
- **P25. The APK carries about 4 MB of unused Bouncy Castle data files.**
  - **Why:** Severity low, confirmed (install size only; measured in
    app-debug.apk; no release APK existed locally, but R8 passes Java resources
    through). bcprov's Java resources are packaged verbatim:
    `org/bouncycastle/pqc/crypto/picnic/lowmc.properties` (3.64 MB raw, 1.57 MB
    compressed), four SIKE parameter files and the CertPathReviewer messages,
    4.15 MB compressed of the 21.6 MB APK. `isShrinkResources` only shrinks
    res/. PDFBox's standard security handler, the only one the app can open,
    never uses Picnic or SIKE. The keep rule also retains Bouncy Castle TLS
    trust managers, which lint flags (TrustAllX509TrustManager, T11), in an app
    without INTERNET permission.
  - **Where:** app/build.gradle.kts:5-51 (no packaging block);
    app/proguard-rules.pro:8.
  - **Approach:** Inside `android { }`: `packaging { resources { excludes +=
    setOf("/org/bouncycastle/pqc/**/*.properties",
    "/org/bouncycastle/x509/CertPathReviewerMessages*.properties") } }`. Leave
    the class keep rule as proguard-rules.pro asks until a device pass, and
    record a follow-up to narrow it to the JCE provider and the classes PDFBox
    loads reflectively (keep `org/bouncycastle/LICENSE.class` or ship G1's
    licence asset). Optional: a Bouncy Castle 1.78+ constraint in
    libs.versions.toml (1.72 has published CVEs; none is reachable here, but
    scanners flag them). JVM tests use the jars, not the APK, so they cannot
    prove the exclusion is safe.
  - **Acceptance:** `unzip -l` of the release APK shows no
    `org/bouncycastle/pqc/` .properties entries and the APK is about 4 MB
    smaller (compare `scripts/build.sh` output before and after). Device: the
    "encrypted" raster fixture opens and exports on the release build, and the
    export still refuses printing. **Effort:** S.

---

## 5. Backlog: features

- **F3 (rest). Navigation.** A thumbnail strip (bottom sheet) that marks
  pages carrying ink, and a fit-width mode for landscape tablets. Thumbnails
  reuse #13's renderer at small size on the worker; defer them until the
  awaiting D-next branch establishes cheap ink-aware navigation. Proof:
  bounded thumbnail memory, correct ink markers after undo/erase and model-
  routed jumps without losing strokes. Fit-width scope/proof is V16/V9 below.
  - **Overview grid instead of a strip (2026-10-06 review's F3 step 2,
    unverified; its step 1, annotated-page chips, is under D-next):** Where:
    ui/PageDialog.kt:30-100; ui/MainActivity.kt:161-171, 284-291, 661-665;
    document/PdfEngine.kt:55-72, 195 (fixed 2048px render);
    document/DocumentService.kt:38-42, 112-115; ui/EditorViewModel.kt:223-230.
    Approach: engine `render(source, page, longEdge)`; thumbnails at a 240px
    long edge (about 230 KB each) in a separate LruCache in DocumentService
    keyed "source:page:thumb", sized by entry count (for example 60), not by a
    heap share (P-prefetch: bitmap pixels are native). Add
    `DocumentOperations.thumbnail(draft, page)` and `cachedThumbnail`. Model:
    `requestThumbnails(visibleRange)` queues work on the single worker behind a
    generation counter, like visiblePage, so scrolling or closing drops stale
    renders; goToPage closes the sheet first so the visible-page render keeps
    priority. UI: tapping the counter opens a full-height dialog with an
    `android.widget.GridView` and a BaseAdapter (the framework recycles views,
    so no RecyclerView dependency); 3 columns on phones, 5-6 on tablets;
    PageDialog's number field stays at the top; the current page is outlined in
    accent. Ink: extract `InkPageView.drawInk`/`drawHighlight` into an
    InkPainter used by the software path and the thumbnails (shared with F26),
    so tiles match the page. Badges from a pure `pagesWithUnexportedInk(draft)`
    (`ink[p] != savedInk[p]`): a small red asterisk for unexported notes, a grey
    dot for exported ink, and D9's marks. Single-page documents keep the counter
    inert (V28); 500+ pages are fine because GridView binds only visible cells;
    a failed render shows a blank tile. Acceptance: a counter click opens the
    overview; with a fake the adapter requests thumbnails only for visible
    positions; tapping cell 7 calls `goToPage(6)` and dismisses; a
    `pagesWithUnexportedInk` unit test; a UiScreenshotTest "overview" shot;
    device: memory stays bounded while scrolling a 300-page overview. Effort: M.
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
  F32 handles annotations already present in the source.
- **F8 (rest). Mouse wheel, trackpad, arrow keys and key-event tests.** The
  shortcuts F8 named are implemented (Done: Ctrl+Z, Ctrl+Shift+Z, Ctrl+Y,
  Ctrl+S, Ctrl+O in `onKeyShortcut`, Page Up/Down in `onKeyDown`, all listed in
  `onProvideKeyboardShortcuts` and routed through the buttons' `isShown &&
  isEnabled`, MainActivity.kt:159-193; README.md:22-24; confirmed by all four
  verifiers).
  - **Why:** Merged from four reviews (general finding confirmed, low). (1)
    InkPageView has no onGenericMotionEvent, so on ChromeOS, DeX or a tablet
    with a keyboard cover, ACTION_SCROLL from a mouse wheel or two-finger
    trackpad scroll does nothing: no pan when zoomed, no page turn at fit, no
    Ctrl+wheel zoom (the convention in Chrome's PDF viewer and Drawboard). (2)
    On Android 14+ touchpad two-finger swipes arrive as fake finger pointers
    classified CLASSIFICATION_TWO_FINGER_SWIPE; in TOUCH mode the start
    condition (InkPageView.kt:342) treats them as ink, so scrolling with the
    touchpad draws a line (unverified, UX review). (3) Arrow keys, Space, Home
    and End, zoom keys (Ctrl+=, Ctrl+-, Ctrl+0), Ctrl+G (Go to page) and tool
    keys are missing. (4) Ctrl+O does nothing on the welcome screen: the handler
    requires `open.isShown`, and the top bar is GONE there (the welcome screen's
    own Open button, MainActivity.kt:427, is not the target); keep N2's
    hidden-target refusal for the other shortcuts. (5) No test sends a key
    event, so the `isShown && isEnabled` routing (MainActivity.kt:169, 179) is
    unverified (T-keyboard).
  - **Where:** ui/MainActivity.kt:159-193, 427, 657-665;
    ui/InkPageView.kt:148-152, 197-199, 304-437 (no onGenericMotionEvent or
    onKeyDown), 558; README.md ("With a keyboard").
  - **Approach:** (a) `InkPageView.onGenericMotionEvent` for ACTION_SCROLL from
    SOURCE_CLASS_POINTER: read AXIS_VSCROLL and AXIS_HSCROLL. With META_CTRL_ON
    call `zoomAround(zoom * 1.1^v, event.x, event.y)`. When zoomed, pan by `v *
    ViewConfiguration.getScaledVerticalScrollFactor()` (and h horizontally),
    clampPan and invalidate. At fit, accumulate notches and call `onTurnPage(+1
    or -1)` per full notch with a 250 ms cooldown, because wheels emit bursts.
    Return true. (b) On API 34+ never ink events whose `getClassification()` is
    TWO_FINGER_SWIPE, MULTI_FINGER_SWIPE or PINCH; route them to pan and zoom;
    keep the filter in a small pure function, since Robolectric's
    `MotionEvent.obtain` cannot set a classification. (c) Keys go in
    `InkPageView.onKeyDown` with `isFocusedByDefault = true`, not in
    `Activity.onKeyDown`: the activity sees unhandled keys before ViewRootImpl's
    focus search, so handling arrows there breaks D-pad and Tab navigation of
    the bars for keyboard and Switch Access users (one reviewer proposed
    MainActivity.onKeyDown guarded by currentFocus; the in-view handler is the
    safer form). DPAD_LEFT and DPAD_RIGHT, and SPACE and Shift+SPACE, call
    `onTurnPage` at fit and pan by about 15% of the view when zoomed (direction
    per R-rtl-swipe's rule); DPAD_UP and DPAD_DOWN pan when zoomed; MOVE_HOME
    and MOVE_END call a new `onGoToPage(0 or last)`. Return false otherwise. (d)
    In onKeyShortcut: Ctrl+EQUALS, PLUS or NUMPAD_ADD call `page.zoomBy(1.25f)`
    and Ctrl+MINUS `zoomBy(0.8f)` (a new public method that animates around the
    view centre); Ctrl+0 calls `fit.performClick()`; Ctrl+G calls
    `counter.performClick()`; Ctrl+O calls `requestOpen()` whenever the app is
    idle, on any screen. (e) Optional: single letters without modifiers and with
    `repeatCount == 0` (P, H, E select a tool; 1-4 a swatch); a mouse
    right-button drag erases in TOUCH mode (STYLUS_BUTTONS already contains
    BUTTON_SECONDARY); a primary-button mouse drag in PEN mode keeps panning, as
    today. (f) List every new key in onProvideKeyboardShortcuts and the README.
    V26 makes keyboard focus visible.
  - **Acceptance:** InkPageViewGestureTest: a synthetic ACTION_SCROLL
    (SOURCE_MOUSE, `PointerCoords.setAxisValue(AXIS_VSCROLL, -1f)`) at fit calls
    `onTurnPage(1)` once per burst, and two quick notches still turn only once;
    after a pinch zoom the same event pans (pageAt at the centre moves) without
    turning; with META_CTRL_ON the page point under the cursor stays put while
    zoom grows. A unit test of the classification filter. A new
    MainActivityKeyboardTest (with T5's seam): Ctrl+Z via
    `dispatchKeyShortcutEvent` removes the newest stroke; Ctrl+S is ignored
    while busy; PAGE_DOWN moves to page 4 of 12; with the page focused
    DPAD_RIGHT turns the page, and with Undo focused it moves focus and leaves
    the page alone; Ctrl+= changes and Ctrl+0 restores `unitsPer100px`; Ctrl+O
    on the welcome screen launches the picker. Device: a Chromebook (wheel,
    touchpad scroll, pinch) and an Android 14+ tablet with a touchpad keyboard.
    **Effort:** M.
- **F9. Multiple documents.** A recent list with one draft per document and
  a "continue where you left off" card on the welcome screen. Needs a draft
  index and per-document directories in `DocumentStore` (G2). Proof: switching
  preserves source/ink/page position, deleted-provider recovery and bounded
   cleanup; process death restores the selected draft. Depends on checked
   commits, session ordering and an explicit migration from the single draft.
   Use stable document identity throughout view/cache/history keys; current
   page-view keys assume globally unique imported filenames.
  - **Cleanup trap and concrete plan (2026-10-06 review, unverified):**
    `DocumentStore.saveDraft` deletes every PDF in files/documents except the
    current source (DocumentStore.kt:93), so a naive second document next to the
    first is wiped on the next stroke. Where: document/DocumentStore.kt:51-54
    (single draft.json), 93; ui/MainActivity.kt:672-684 (confirmReplacing);
    res/values/strings.xml (unsaved_prompt). Layout:
    files/documents/<id>/source.pdf and draft.json, plus
    files/documents/index.json with entries `{id, name, lastOpened, pageCount,
    dirty, sha256}`, written through AtomicFile (checked commits per
    B-atomic-commit). Split DocumentStore into a per-document `DraftStore(dir)`
    and a `DocumentIndex`; scope the stale-PDF cleanup to the document's own
    directory; import into a fresh <id>/ that is deleted on failure. One-time
    migration in restore: if documents/draft.json exists, create <id>/ and move
    draft.json and its source with `File.renameTo` (atomic on one filesystem);
    if both copies exist after a crash between the renames, prefer the
    per-document one. Dedupe by B22's content digest ("Continue your notes on
    Report.pdf" instead of a second copy). UI: when the index is non-empty, the
    welcome screen becomes Recent (name, page count, the red unexported dot,
    relative time); tapping opens the private copy instantly with no URI grant
    needed; long-press offers Remove, confirmed when dirty. Open no longer
    replaces anything, so confirmReplacing is needed only for Remove. Eviction:
    cap at about 20 documents or 500 MB, never evict a dirty draft
    automatically, and tell the user when the cap blocks an import. Clear
    InkHistory and evict previews on a switch (`DocumentService.key` already
    includes the source name). A cold launch reopens the most recent document.
    Update README's draft and backup sentences and G5's rules (#28). Export
    contract unaffected. Acceptance: DocumentStore tests: the legacy layout
    migrates with ink intact, including a simulated crash between the renames;
    saving document A never deletes B's source; the index round-trips.
    EditorViewModel with a fake that lists and opens recents: switching keeps
    both drafts' ink. MainActivity: the welcome screen lists two recents and the
    dirty one shows the unexported marker; a UiScreenshotTest "recents" shot.
    Device: open two PDFs from Files, switch, kill the app, reopen.
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
  - **Outline and printed page labels (2026-10-06 review's F22, unverified,
    value medium):** textbooks, papers and manuals carry bookmarks and page
    labels (front matter i to xii, then 1); users think "Chapter 7" or "page 152
    as printed", but the page field takes only the physical index
    (TYPE_CLASS_NUMBER): printed 152 may be index 164, and "xii" cannot be
    entered. Where: document/PdfEngine.kt:43-53; ui/PageDialog.kt:30-43;
    ui/MainActivity.kt:558-559. Approach: inspect also returns `outline:
    List<OutlineEntry(title, page, depth)>`: walk
    `documentCatalog.documentOutline` recursively to depth 4, resolve each item
    with `item.findDestinationPage(document)` and an index from one iteration of
    `document.pages` (B34), skip entries that do not resolve, cap at 2000
    entries, guard against cycles and strip control characters from titles. It
    also returns `labels: List<String>?` from
    `documentCatalog.pageLabels?.labelsByPageIndices`. Carry both as
    DocumentNavigation in OpenDocument and EditorState, not in Draft; recompute
    on restore or cache with G6. UI: the Go to page dialog lists Contents under
    the field when an outline exists (a ListView indented by depth, the current
    section marked). When labels exist the field takes text: match a label
    case-insensitively first, then numbers as labels, then fall back to the
    physical index; show the page's label under the field. The pill counter
    stays "12 / 400" so it stays narrow. Both move into F3's overview once it
    exists. Acceptance: JVM PdfEngine test: a fixture with a two-level outline
    and labels (roman i-iii, then 1...) gives entries with the correct page
    indices and labels ["i", "ii", "iii", "1", ...]; a broken destination is
    skipped, not thrown; Robolectric: the dialog shows Contents, typing "ii"
    jumps to index 1, tapping "Chapter 2" calls goToPage with its index. Effort:
    M.
  - **Search with hits overlaid (its F24, unverified, value medium):** "Where
    did the contract mention the deadline?" cannot be answered without flipping
    through every page; PDFTextStripper ships in pdfbox-android, and D-text-snap
    needs the same glyph boxes. Where: document/PdfEngine.kt (no text access);
    document/DocumentService.kt:18-31 (DocumentOperations);
    ui/MainActivity.kt:240-273. Approach: engine `pageText(source, page):
    PageText(chars, boxes)` through a PDFTextStripper subclass (start and end
    page = page + 1) that collects TextPosition per character in writeString.
    Map glyph boxes to display units with `PageSpec.pdfToDisplay()` (F23) from
    the text matrix in user space; do not rely on PDFBox's rotation-adjusted
    getters, because PDFBox 2 also shifts by the crop box origin. Service:
    `search(draft, query, onHit)` walks pages on the worker from the current
    page with wrap-around; normalise with NFKC and case folding, split
    ligatures, collapse whitespace and join hyphenated line ends; cache PageText
    in a small LRU; cancel through a generation counter when the query changes
    or search closes. UI: a menu entry (F25) swaps the top bar for a search bar
    (field, "3 of 17", previous, next, close). InkPageView draws hit rects as
    translucent accent overlays, stronger for the current hit, on screen only:
    never in Draft, never exported. Jumping pans so the hit is visible at the
    current zoom. With no text layer (scans): "This PDF has no searchable text."
    Acceptance: JVM: a PDFBox fixture with "deadline" on pages 1 and 3 (page 3
    rotated 90 with a crop offset) returns pages [0, 2], and the rects contain
    the glyphs' known display positions; normalisation unit tests cover
    ligatures and hyphenation; EditorViewModel with a fake: next and previous
    move pages and a new query cancels the old one; NATIVE pixel test: the
    overlay is drawn at the rect; an export test shows that search state does
    not change the exported bytes; device: search latency on a 300-page PDF.
    Effort: L.
- **F-presets / N38 / U13. Independent pen/highlighter settings (awaiting
  #61).** Confirmed
  `ui/MainActivity.kt:127-130,454-482` shares colour/width indices across
  different palettes and physical widths. Split per-tool preferences and
  migrate existing values conservatively. Proof: fine blue pen and broad pink
  marker each survive switching, rotation and restart. Foundation for D-palette,
  not a request to implement named presets at the same time.
  N38 identifies four shared swatch positions; persist colour/width per
  `InkKind`, not one palette index reused across different tools.
  - **U13 (2026-10-06 review, unverified, medium; #61):** configurePen indexes
    COLORS or HIGHLIGHT_COLORS with the same number (MainActivity.kt:457, 474):
    pick the pink highlighter and switch back to Pen, and the pen is now red;
    pick a Bold pen, and the highlighter becomes 18pt. The common graphite pen
    plus yellow highlighter pairing costs two extra taps on every switch;
    GoodNotes, Samsung Notes, Xodo and Flexcil remember settings per tool.
    Where: ui/MainActivity.kt:102-104, 127-130, 454-483, 491-527, 750-758. Its
    conservative migration: the pen keeps the existing prefs keys penColor and
    penWidth, so no migration is needed; add highlightColor and highlightWidth
    (defaults 0 = Yellow and 1 = 12pt). Replace colorIndex and widthIndex with a
    small per-kind holder that configurePen reads through `kind`; pickColor and
    pickWidth write the current kind's slot. When the eraser has interrupted the
    highlighter, the swatches keep showing highlight tints and still change the
    highlighter, as today. configurePen persists all four values (through G12's
    PenSettingsStore if it lands first). D8 then shows each tool's own colour.
    Acceptance: MainActivityChromeTest with an editor state injected first (T7):
    pick a Red pen, switch to Highlighter: Yellow is selected; pick a Green,
    Bold highlighter, switch to Pen: Red and Medium are selected; restart the
    activity: both tools come back with their own settings;
    `penSettingsSurviveARestart` still passes. Effort: S.
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
  U11 adds TalkBack page actions and announcements without text access.
- **F17. New blank note (plain, lined, grid, dots) with Add page.**
  - **Why:** Value high, unverified (features review). Asterinked can only
    annotate a PDF the user already has; sketching, meeting notes or working a
    maths problem first needs a blank PDF made elsewhere (welcome.png shows a
    single "Open PDF" button). A generated blank PDF gets export, share,
    highlighter and drafts for free. F13's insert-blank-page tool for imported
    PDFs is the related page-list change.
  - **Where:** ui/MainActivity.kt:404-443 (welcome);
    document/DocumentService.kt:48-62; document/DocumentStore.kt:56-69;
    ui/InkPageView.kt:170-177 (documentKey from the source name).
  - **Approach:** `PdfEngine.createBlank(destination, paper: Paper, pattern:
    Pattern, pages = 1)` with enums, not booleans: `PDRectangle.A4` or `LETTER`,
    the default from the Locale country (US, CA, MX and PH use Letter); the
    pattern is light-grey vector page content (ruled lines every 8 mm, a 5 mm
    grid, 5 mm dots, or plain). `DocumentOperations.create(paper, pattern):
    OpenDocument`: DocumentService writes files/documents/<uuid>.pdf, builds
    `Draft(source, name = "Note <localized date>.pdf")`, runs inspect, render
    and saveDraft, and deletes the file on failure as open() does.
    `EditorViewModel.createNote` mirrors open() (respects `restoring` and busy,
    clears history). The welcome screen gets a secondary "New note" button under
    Open PDF; from the editor the same entry goes through confirmReplacing until
    F9. The pattern choice is a four-option dialog remembered in the "pen"
    prefs. Add page: on the last page of a generated note the next arrow becomes
    "+", which rewrites the private source with one more page of the same size
    and pattern into a new uuid file; saveDraft switches the source (the store
    deletes the old file) and the state gets `pages + spec`. Ink keys stay valid
    because pages are only appended. Store `"origin": "note"` in draft.json as
    an optional key (older versions ignore unknown keys; a missing key means
    import); with origin note, exportName drops the "-annotated" suffix.
    Pitfall: `InkPageView.show` resets zoom when `source.name` changes
    (documentKey); keep a stable document id for documentKey or accept the
    reset. Later, only after F9: register for `Intent.ACTION_CREATE_NOTE` (the
    Notes role, API 34) so a USI pen's tail button opens a new note.
  - **Acceptance:** JVM: createBlank, then inspect returns a single 595x842 (A4)
    or 612x792 (Letter) PageSpec and the content stream holds the expected
    number of pattern lines; adding a page keeps page 0's content and increases
    the count; a DocumentStore round-trip keeps origin, and an old draft without
    the key restores as an import. Robolectric: the welcome screen shows "New
    note", and EditorViewModel with a fake lands in the editor after create.
    PDFium: a "note" case in verify_pdf.py renders grey lines with aligned red
    ink. **Effort:** M.
- **F18. Annotate photos and screenshots by wrapping images into a one-page
  PDF.**
  - **Why:** Value high, unverified (features review). A photo of a worksheet, a
    whiteboard, a receipt to sign or a screenshot cannot be annotated: the
    picker filters to application/pdf (MainActivity.kt:669) and the SEND filter
    accepts PDFs only (AndroidManifest.xml:21-25). The editor only needs a PDF,
    so the fix is cheap.
  - **Where:** AndroidManifest.xml:21-25; ui/MainActivity.kt:669;
    document/DocumentStore.kt:56-69; document/DocumentService.kt:48-62.
  - **Approach:** In `DocumentStore.import` read `resolver.getType(uri)`; for
    image/jpeg, png, webp and heic (when decodable) build a PDF instead of
    copying the bytes. Decode with ImageDecoder (applies EXIF orientation) with
    a target size that keeps the long edge at 3000 px or less (a 12 MP photo
    would be 48 MB as ARGB). Page size in points is pixels x 72 / 150. Embed
    photos with `JPEGFactory.createFromImage(document, bitmap, 0.9f)` and PNG
    screenshots with LosslessFactory, so text stays crisp and alpha becomes an
    SMask; draw a white background first for transparent PNGs. Rename
    IMG_1234.jpg to IMG_1234.pdf so exportName gives IMG_1234-annotated.pdf. Add
    `DocumentProblem.IMAGE_UNREADABLE` with "Couldn't read this image."
    Manifest: add image/* to the SEND filter only (on VIEW it would put
    Asterinked in every photo "Open with" list, a product decision). Picker:
    `arrayOf(PDF_MIME, "image/*")`; update `welcome_caption`. Optional:
    SEND_MULTIPLE of images makes a multi-page PDF, one page per image.
    Pitfalls: HEIC decodes only where the codec exists; fall back to
    BitmapFactory plus ExifInterface where ImageDecoder is unavailable
    (Robolectric). Export contract unaffected. B41's intent validation applies
    to the new MIME types too.
  - **Acceptance:** Robolectric NATIVE: importing a PNG fixture gives a one-page
    PDF whose aspect ratio equals the image's and which contains one image
    XObject; an EXIF-rotated JPEG gives a portrait page (on a device if
    Robolectric cannot apply EXIF). MainActivityIntentTest: SEND with an
    image/png stream is accepted. PDFium: an "image" fixture renders a known
    colour quadrant in the right place with ink on top. **Effort:** M.
- **F19. Reusable handwritten signature placed with one tap.**
  - **Why:** Value high, unverified (features review). Signing is the most
    common reason to put a pen on a PDF on a phone. Today the signature is
    handwritten on every document at page zoom, often with a finger; at fit on a
    phone a signature line is about 5 mm tall, so it comes out oversized unless
    the user zooms first. Nothing is reusable.
  - **Where:** ui/InkPageView.kt:338-353; ui/EditorViewModel.kt:104-109;
    ink/InkHistory.kt; document/DocumentStore.kt:104-129 (stroke encoding).
  - **Approach:** Storage: a SignatureStore in document/ writes
    files/signature.json; extract the stroke-list encoding from
    `DocumentStore.encodeInk`/`decodeInk` into shared functions (after P5's
    streaming change if that lands first). Strokes are normalised so their
    bounding box starts at (0,0), in points. The file is private; G5's
    extraction rules (#28) must exclude it from device-to-device transfer too.
    Capture: a dialog hosts an InkPageView on a blank 360x120 pt PageSpec with
    `preview = null` (the blank-page path draws white), in InputMode.TOUCH
    because many people sign with a finger; buttons Clear, Cancel, Save; ink
    uses the current swatch. Place: a "Sign" entry (F25 menu) arms a one-shot
    place mode in InkPageView, and the next tap calls `onPlace(pagePoint)`
    instead of inking. A pure function scales the strokes to about a third of
    the page width, centres them on the tap, clamps them inside the page and
    scales the width by the same factor. `EditorViewModel.addStrokes(list)`
    appends everything as one edit (in #29's or #44's history), so one undo
    removes the whole signature; moving or resizing waits for D-lasso. Label it
    "Handwritten signature" and keep README's note that digital signatures are
    not created. Export: plain PEN strokes, so PdfEngine and the InkGeometry
    contract are unchanged.
  - **Acceptance:** JVM: SignatureStore round-trip; the scaling function keeps
    the aspect ratio and scales width. EditorViewModel with the
    EditorViewModelPagingTest fakes: placing adds N strokes, one undo removes
    all N, redo restores them. InkPageView gesture test: in place mode a stylus
    tap at (300,400) reports onPlace at the page point `pageAt(300,400)` gives
    and commits no stroke. **Effort:** M.
- **F20. Partial eraser that cuts strokes instead of deleting them whole.**
  - **Why:** Value medium, unverified (UX and features reviews merged). The
    eraser removes whole strokes: fixing one letter of a cursive word (usually a
    single stroke), trimming an overshooting underline or shortening a highlight
    means erasing and redrawing it. Samsung Notes, GoodNotes, Notability and
    Xodo offer a partial eraser next to the stroke eraser.
  - **Where:** ink/InkEraser.kt:27-36 (hits returns whole strokes);
    ui/InkPageView.kt:445-465; ui/EditorViewModel.kt:111-120;
    ui/MainActivity.kt:719-723.
  - **Approach:** Two algorithms were proposed; recommended is the exact cut
    (features review) over resampling and dropping points inside the disc (UX
    review), because it handles sparse strokes without resampling every point.
    `InkEraser.cut(stroke, probes, radius): List<InkStroke>`: walk consecutive
    point pairs, compute each segment's parameter intervals inside any probe
    disc (segment-circle intersection, reach = radius + width/2 as in
    touches()), split there inserting boundary points with interpolated
    pressure, keep the runs outside, drop runs shorter than 0.5pt unless the
    original was a dot, and return the same instance when nothing is hit. View:
    during the gesture keep replacements in an `IdentityHashMap<InkStroke,
    List<InkStroke>>`, draw fragments instead of originals (`inkNodeStale =
    true`), and on lift call `onReplace(replacements)`. Model:
    `replaceStrokes(map)` puts fragments in the original's position, `after =
    strokes.flatMap { map[it] ?: listOf(it) }`, recorded as one edit with
    eraseStrokes' identity semantics (fit #29's or #44's history). Mode: tapping
    the already selected eraser segment toggles Stroke and Area, shows a notice
    ("Erases parts of strokes"), swaps the icon and is remembered in prefs; the
    stylus eraser end follows the mode. Pitfalls: each fragment re-smooths from
    its own first sample, so the curve can shift by up to half a sample spacing
    near a cut (acceptable; the geometry algorithm and InkIncrementalTest stay
    unchanged), but the visible gap must not exceed the disc; highlighter
    fragments keep constant width; cap or merge tiny fragments from scribbling;
    reuse the cached bounds so long gestures do not become O(n^2); build on
    #43's rendered-geometry hit testing. Export unchanged: fragments are
    ordinary strokes.
  - **Acceptance:** InkEraserTest: a two-point stroke from x=0 to x=100 cut at
    x=50 with r=5 gives two strokes, one ending at or before 45 + w/2 and one
    starting at or after 55 - w/2, with interpolated pressure; a disc that
    misses returns the same instance. EditorViewModel test with the fake: one
    gesture is one undo, which brings back the single original. NATIVE pixel
    test in InkPageViewTest: pixels at the cut are white and both sides are
    dark. **Effort:** M.
- **F23. Follow internal links with a finger tap, then go back.**
  - **Why:** Value medium, unverified (features review). Papers and reports are
    full of internal links: table-of-contents entries, "see Figure 3", citation
    numbers, footnotes. Tapping them does nothing, so following a reference
    means guessing a page number and remembering where one came from.
  - **Where:** ui/InkPageView.kt:127-146 (GestureDetector handles only double
    tap and fling); document/PdfEngine.kt:24-35 (PageSpec has only
    displayToPdf), 43-53.
  - **Approach:** inspect collects per-page `PageLink(rect in display units,
    target page)` from PDAnnotationLink with a PDActionGoTo or a direct /Dest;
    resolve PDPageDestination directly and PDNamedDestination through
    `documentCatalog.findNamedDestinationPage`; convert the rect with a new
    `PageSpec.pdfToDisplay()`, the inverse of displayToPdf (F16's search reuses
    it). Ignore URI links in v1 (or show the host and ask before ACTION_VIEW).
    View: in PEN mode finger taps go through
    `GestureDetector.onSingleTapConfirmed`, which waits out the double-tap
    timeout and so does not fight double-tap zoom; a tap inside a link rect
    calls `onFollowLink(page)`. Stylus taps keep writing dots and never follow
    links. In TOUCH mode fingers ink, so links are not followed; document it.
    Model: `followLink(page)` keeps `returnTo` (the current page) in memory,
    then goToPage. MainActivity shows a "Back to page 3" notice action through
    U8's action slot (on main) and registers an OnBackPressedCallback while
    `returnTo` is set. Check against U3's tap gestures (#31, #32).
  - **Acceptance:** JVM: a fixture with a link on page 0 to page 2 at a known
    rect, at rotations 0, 90, 180 and 270 with PdfEngineRasterTest's crop
    offsets: the returned display rect maps back through displayToPdf to the PDF
    rect. InkPageViewGestureTest with real event times: a finger single tap
    inside the rect fires `onFollowLink(2)` after the timeout; a stylus tap at
    the same spot commits a stroke and follows nothing; double tap still zooms.
    Robolectric: system Back returns to the original page. **Effort:** M.
- **F25. Document menu behind the title as the home for secondary actions.**
  - **Why:** Value medium, unverified (features review). Licences (G1), close
    and clear (F12), print, search, contents, export options, signature and
    pointer all need an entry point, and the top bar has no room: at 320dp the
    title is already cut to "Quart....pdf" beside Share and Save copy (V17), and
    an overflow icon would squeeze it further.
  - **Where:** ui/MainActivity.kt:240-273 (the heading column is not clickable).
  - **Approach:** Make the heading column (title and status) one button: a
    ripple background, contentDescription "Document options, <name>", and a
    small chevron as the title's end compound drawable so it adds no width. On
    click show an `android.widget.PopupMenu` anchored to the title (framework,
    keyboard and TalkBack accessible, RTL-aware). Items are enabled by state and
    added as features land: Pages, Contents, Search, Sign, Print, Save pages
    with notes, Share this page as image, Close document (F12), Open-source
    licences (G1). Disable the title while busy, like the other controls
    (`ready` in show()). The Menu key opens it; Ctrl+F can open search later.
  - **Acceptance:** MainActivityChromeTest: clicking the title opens a PopupMenu
    (`ShadowPopupMenu.getLatestPopupMenu()`) with the expected items; nothing
    opens while busy. UiScreenshotTest narrow phone: the title shows at least as
    many characters as today (compare the title layout's ellipsis start). A
    TalkBack label assertion on the heading. **Effort:** S.
- **F26. Export options: only pages with notes, or this page as an image.**
  - **Why:** Value medium, unverified (features review). Sending only the pages
    one wrote on (3 commented pages of an 80-page contract) or the current page
    as an image to a chat app, where a PDF arrives as an opaque attachment, is
    impossible: the only output is the full annotated PDF.
  - **Where:** document/PdfEngine.kt:101-143 (always writes every page);
    document/DocumentService.kt:87-110; ui/MainActivity.kt:268, 686-697;
    res/xml/shared_files.xml.
  - **Approach:** Pages with notes: `PdfEngine.export` takes `pages:
    PageSelection` (enum ALL, WITH_NOTES). After drawing ink, remove pages
    without ink in descending order (`document.removePage`), and drop AcroForm
    fields whose widgets were only on removed pages and outline items that point
    to them. Offer it only when `currentAccessPermission.canAssembleDocument()`
    (inspect reports it). Name: "Report-annotated-pages.pdf". Every stroke is
    included, so setting `savedInk = ink` stays truthful. Page image: render the
    current page with PdfRenderer at 2x the preview, long edge capped near 2400
    px; draw ink on a software Canvas through an InkPainter extracted from
    InkPageView's software path (shared with F3's thumbnails), highlights first
    with BlendMode.MULTIPLY over the opaque page bitmap so the image matches the
    screen; write the PNG to cache/shared/<uuid>/Report-p3.png (the existing
    FileProvider path) and let sendToShareSheet take the MIME type as a
    parameter. Entries go in the F25 menu; Save copy stays one tap. The
    InkGeometry contract is unchanged. D-summary is a separate opt-in addition
    to the full export.
  - **Acceptance:** PdfEngineExportTest: a three-page fixture with ink on page 1
    only, exported WITH_NOTES, has one page whose text is page 1's sentinel; a
    source without assembly permission makes inspect report `canAssemble =
    false`. Robolectric NATIVE: InkPainter over a fake white page bitmap puts
    dark pixels at the ink position and keeps text dark under a yellow highlight
    (mirroring verify_pdf.py's checks). Device: the PdfRenderer render for the
    image. **Effort:** M.
- **F27. Print the annotated copy, honouring a no-print restriction.**
  - **Why:** Value low, unverified (features review). There is no Print: users
    save a copy, open it in another viewer and print from there, and the share
    sheet does not offer a print target on every device. Android's PrintManager
    prints PDFs natively.
  - **Where:** document/DocumentService.kt:87-99 (share() deletes the whole
    shared/ directory); document/PdfEngine.kt:43-44, 158-164 (permissions read;
    no-print kept on export).
  - **Approach:** Build the annotated copy as share() does, but under
    cache/print/<uuid>/, because share() deletes the entire shared/ directory
    (line 89; B-share-lifetime) and a share during printing would delete the
    job's file. Then `PrintManager.print(draft.exportName, adapter, null)`.
    Adapter: onLayout returns
    `PrintDocumentInfo.Builder(name).setContentType(CONTENT_TYPE_DOCUMENT).setPageCount(pages.size)`;
    onWrite copies the file into the descriptor and honours the
    CancellationSignal; onFinish deletes the folder. Keep the copy in a pure
    `copyForPrint(file, out, cancel)`, because WriteResultCallback's constructor
    is hidden and cannot be subclassed in tests. inspect reports `canPrint`
    (AccessPermission.canPrint); when false the menu item is disabled with "The
    author of this PDF doesn't allow printing", consistent with #12 keeping that
    restriction. Entry: the F25 menu. Add cache/print/ to G13's sweep.
  - **Acceptance:** JVM: inspect of the owner-restricted no-print fixture
    (PdfEngineRasterTest's "encrypted" case) reports `canPrint = false` and the
    plain fixture true; copyForPrint copies the bytes and stops when cancelled.
    Robolectric: the menu item is disabled for no-print and enabled otherwise.
    Device: print to "Save as PDF" and to a real printer. **Effort:** S.
- **F28. Choose what the stylus side button does.**
  - **Why:** Value low, unverified (features review). The side button always
    erases (`activeErasing` includes any STYLUS_BUTTONS press,
    InkPageView.kt:348, 557-558). S Pen users whose grip rests on the button
    erase mid-sentence, and there is no way to turn that off or to use the
    button for the highlighter or, later, the lasso. B-stylus-secondary
    (recognising the standard secondary bit) and D-clutch (switching during
    contact) are separate.
  - **Where:** ui/InkPageView.kt:348, 557-558; ui/MainActivity.kt:454-483
    (configurePen).
  - **Approach:** `enum StylusButtonAction { ERASE, HIGHLIGHT, SELECT, NONE }`,
    default ERASE, persisted in the "pen" prefs as "stylusButton" and passed
    through `configure()` together with both the pen and the highlight ink for
    the current index (per-tool values after F-presets/#61). At stroke start in
    InkPageView: ERASE sets activeErasing as today; HIGHLIGHT sets `activeKind =
    HIGHLIGHTER` with the highlight colour and width; NONE ignores the button;
    SELECT starts the lasso (D-lasso) once it exists. TOOL_TYPE_ERASER (the
    eraser end) always erases. UI: a long-press on the eraser segment opens a
    single-choice dialog "Pen side button: Erase, Highlight, Nothing", also
    reachable from the F25 menu.
  - **Acceptance:** InkPageViewTest: stylus DOWN with BUTTON_STYLUS_PRIMARY and
    action HIGHLIGHT commits one HIGHLIGHTER stroke in the highlight colour;
    NONE commits a PEN stroke; ERASE still erases (existing test).
    MainActivityChromeTest: the setting survives a restart. Device: an S Pen and
    a USI pen (some report BUTTON_SECONDARY). **Effort:** S.
- **F29. Drop a PDF onto the window in split screen or on ChromeOS.**
  - **Why:** Value low, unverified (UX review). There is no OnDragListener
    anywhere, so dragging a PDF from Files, Gmail or Chrome's downloads onto
    Asterinked in split screen or a ChromeOS window does nothing. Xodo,
    Drawboard and Samsung Notes accept drops, and on Chromebooks it is the
    natural way to open a file.
  - **Where:** ui/MainActivity.kt:195-238, 574-580, 648-655.
  - **Approach:** `root.setOnDragListener`: on ACTION_DRAG_STARTED accept only
    if `event.clipDescription.hasMimeType("application/pdf")`; outline the
    workspace in the accent colour between ENTERED and EXITED. On ACTION_DROP
    call `requestDragAndDropPermissions(event)`, take
    `clipData.getItemAt(0).uri`, run it through B41's URI validation, set
    `incoming`, and reuse the confirmReplacing path in show() (B-pending-pdf).
    Hold the DragAndDropPermissions object until `model.open` finishes (release
    it when busy ends, or on Keep editing). Ignore drops while busy and use only
    the first PDF of a multi-item drop. Pitfall: the permission dies with the
    activity, so a rotation during the prompt makes the read fail with the
    existing SOURCE_UNREADABLE message.
  - **Acceptance:** Extract the drop handling into `receiveDrop(clip: ClipData)`
    and test it with Robolectric: a ClipData with a PDF content URI on an idle,
    non-dirty editor sets incoming and starts model.open (the state turns busy);
    a non-PDF MIME type is refused at DRAG_STARTED. Device: drag from Files in
    split screen and on ChromeOS. **Effort:** S.
- **F30. Signed or certified PDFs: warn that signatures break, optionally keep
  the signed revision.**
  - **Why:** Severity low (plausible; the verifier reclassified it from bug to
    feature: export always writes a copy, so the signed original stays intact).
    export() always rewrites the file (`document.save`, PdfEngine.kt:142), so
    every /ByteRange signature in the copy becomes invalid (Acrobat: "document
    altered or corrupted since it was signed"), and inspect checks only the
    encryption permission `canModify()`, not /Perms /DocMDP. Burned-in page
    content is a modification that validators flag even with an incremental
    update (DocMDP P=1 and P=2 certifications fail regardless); an incremental
    update only preserves the signed revision. Both
    `saveIncremental(OutputStream, Set<COSDictionary>)` and
    `getSignatureDictionaries()` exist in 2.0.27.
  - **Where:** document/PdfEngine.kt:43-45, 101-143, 158-164.
  - **Approach:** Step 1 (S): inspect reports `signed`
    (`document.signatureDictionaries.isNotEmpty()` or a catalog /Perms /DocMDP)
    next to the PageSpec list (in F32's DocumentFacts); the model shows a
    one-time notice: "Signatures in this PDF won't be valid in the annotated
    copy. The original file is unchanged." Step 2 (L, optional, designed
    together with F7): for signed sources write via `saveIncremental(out,
    touched)` with the page dictionaries, the new content streams and the
    ExtGState resources marked (share the collection with P21); skip
    keepProtection on that path, because protect() would re-key the document;
    allow encrypted sources only after a fixture proves that new objects are
    encrypted with the original handler. Refusing DocMDP P=1 outright is a
    product decision.
  - **Acceptance:** Step 1: a fixture signed with PDFBox `addSignature` and a
    dummy SignatureInterface returning fixed bytes makes inspect report `signed
    = true`, and the model shows the notice once. Step 2: the export starts with
    the exact source bytes, contains two %%EOF markers and carries the added
    stroke stream; unsigned exports stay byte-identical; a verify_pdf.py fixture
    shows the ink under PDFium; device check in Acrobat Reader's signature
    panel. **Effort:** S (step 1), L (step 2).
- **F31. Opt-in wallpaper (Material You) colours that leave the brand red
  alone.**
  - **Why:** Value low, unverified (aesthetics review). The palette is fixed
    slate and red, with no way to follow the wallpaper on Android 12+. `accent`
    also does two jobs: the brand and attention colour (welcome asterisk
    MainActivity.kt:413, unexported dot :612, error text PageDialog.kt:47) and
    the interactive emphasis (progress line :208, spinner :449, field focus,
    finger toggle). A dynamic palette must change only the second.
  - **Where:** res/values/colors.xml:21-28; ui/MainActivity.kt:119-138, 208,
    413, 449, 612; ui/PageDialog.kt:47.
  - **Approach:** Depends on V35's theme-attribute tokens. Split accent into
    asterBrand (always #D3111C, #FF5C64 at night) and asterAccent. values-v31
    defines ThemeOverlay.Asterinked.Dynamic. Light: canvas `system_neutral2_50`,
    surface `system_neutral1_10`, surface_track `system_neutral2_100`,
    on_surface `system_neutral1_900`, on_surface_variant `system_neutral2_700`,
    outline `system_neutral2_300`, outline_variant `system_neutral2_100`, accent
    `system_accent1_600`, accent_container `system_accent1_100`,
    on_accent_container `system_accent1_800`. values-night-v31: canvas
    `system_neutral1_1000`, surface `system_neutral1_900`, surface_raised and
    track `system_neutral1_800`, on_surface `system_neutral1_50`,
    on_surface_variant `system_neutral2_200`, accent `system_accent1_200`,
    container `system_accent1_800`, on container `system_accent1_100`. Tune the
    steps on a device. Store the choice under a new prefs key, apply it with
    `theme.applyStyle` before buildLayout, and `recreate()` when it changes.
    Where the switch lives is a product decision (it fits F25's menu). The
    launcher icon, splash and welcome asterisk stay brand red. Preserve the
    neutral/red identity this file asks for.
  - **Acceptance:** Robolectric `@Config(sdk = [35])` with the preference on:
    asterAccent resolves to the value of `android.R.color.system_accent1_600`
    and asterBrand is still #D3111C; with it off every token is unchanged.
    Device check on Android 12+ with two very different wallpapers, light and
    dark. **Effort:** L.
- **F32. Show existing annotations and form values, and keep ink above them in
  the export.** (The 2026-10-06 review's F16.)
  - **Why:** Value high, unverified (features review; the PdfRenderer behaviour
    comes from the API and needs a device check). Android's PdfRenderer draws
    page content only: before API 35 it never passes PDFium's annotation flag,
    and API 35's RenderParams can opt in only text and highlight annotations. So
    a form filled in another app shows empty fields, and stamps, sticky notes,
    other apps' highlights and visible signature widgets are invisible. The
    export keeps those annotations, and every viewer draws annotations above
    page content, which is where `PdfEngine.export` appends the ink. A user who
    filled a form in Drive and signs in Asterinked writes over an apparently
    empty Name box; the export shows the typed value and the handwriting on top
    of each other, and opaque field backgrounds hide the ink. The preview no
    longer matches the export, the app's core promise.
  - **Where:** document/PdfEngine.kt:43-53 (inspect ignores `page.annotations`),
    55-72 (render, RENDER_MODE_FOR_DISPLAY), 101-143 (export appends ink,
    /Annots untouched); document/DocumentStore.kt:93.
  - **Approach:** Stopgap first (S): inspect reports which pages carry visible
    annotations with a normal appearance stream (skip Link and Popup and the
    Hidden and NoView flags) and whether AcroForm NeedAppearances is set, in a
    new `DocumentFacts` carried by OpenDocument and EditorState (not in
    PageSpec, the geometry type). On open show one INFO notice: "This PDF has
    form entries or comments that Asterinked can't show yet. They stay in the
    saved copy, above your notes." Full fix (M), one shared
    `flattenAnnotations(document, page)`: `acroForm.refreshAppearances()` if
    NeedAppearances is set, then `acroForm.flatten()` (both in pdfbox-android
    2.0.27); for each remaining non-Link annotation, wrap its appearance stream
    into page content (saveGraphicsState, transform by the PDF 32000 section
    12.5.5 matrix, that is the BBox transformed by the appearance /Matrix and
    mapped onto /Rect, drawForm, restore), then remove it from
    `page.annotations`. Display copy: when such annotations exist, write a
    flattened private copy under cacheDir/display/<source name> (never
    files/documents, where saveDraft deletes every other PDF) and regenerate it
    on restore if missing; an owner-restricted source may be saved with
    `setAllSecurityToBeRemoved(true)`, since the copy never leaves the app;
    `rendererFor` opens it when present. Export: apply the same function on
    every page that carries ink before appending ink, giving annotations,
    highlights, pen in that order, as on screen; pages without ink keep live,
    editable annotations. keepProtection still runs after flattening
    (PdfEngine.kt:141). Pitfalls: rotated pages combined with the NoRotate flag;
    one widget shared across pages; FreeText without an appearance stream
    (nothing to draw, skip it). F23's links are Link annotations and stay live.
    F7 (writing ink as annotations) must define how both coexist.
  - **Acceptance:** PdfEngineExportTest: page 0 has an opaque Square annotation
    (appearance from `constructAppearances()`) and an AcroForm text field with a
    value over the ink spot; page 1 has an annotation and no ink. After export,
    page 0's /Annots holds no Square or widget and its content draws the
    appearance (`Do`) before the ink operators; page 1 keeps its annotation.
    PDFium: a new "annotated" case in PdfEngineRasterTest and verify_pdf.py: red
    ink at INK_POSITION is visible above the opaque fill, and the fill colour is
    present elsewhere (pypdfium2 draws annotations by default). The display copy
    cannot be checked through PdfRenderer in Robolectric; check on Android 10
    and 15 devices that filled values show on screen. **Effort:** S (stopgap), M
    (flattening).

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
  V24 (tighter tool-bar gaps) is a cheap first step for the 680 x 360dp case;
  the side rail stays the larger option for heights below about 480dp.
- **V24. Phone landscape: keep the tool bar on one row by tightening its gaps.**
  - **Why:** Severity medium, from the 2026-10-06 review's own reading of
    editor-compact-landscape.png (680x360dp). The single tool row measures
    696dp: history 96 + Space.XL 24 + tools 144 + Space.S 8 + finger 48 +
    Space.XL 24 + colours 192 + Space.L 16 + widths 144 (wideRow,
    MainActivity.kt:324-331). The bar has 672dp (680 minus the Space.XS padding
    on each side, MainActivity.kt:636), so onMeasure falls back to DOUBLE
    (383-387): two 56dp rows in a window whose workspace is only 136dp tall. One
    row gives the page back 56dp.
  - **Where:** ui/MainActivity.kt:306-401 (buildToolBar: wideRow,
    singleRowWidth, onMeasure), 711 (gap), 716 (ToolBarRows).
  - **Approach:** Add a tight single-row arrangement between SINGLE and DOUBLE:
    `ToolBarRows { SINGLE, SINGLE_TIGHT, DOUBLE, TRIPLE }`. `wideRow()` takes
    the inter-group gaps as parameters; the tight variant uses Space.M (12dp)
    for both Space.XL gaps, which alone gives exactly 672dp, and Space.M for the
    Space.L gap before the widths, giving 668dp with 4dp of slack. Measure
    `tightRowWidth` once the same way as `singleRowWidth` (UNSPECIFIED width,
    TOOL_ROW height) rather than computing it by hand, and choose `available >=
    singleRowWidth -> SINGLE; available >= tightRowWidth -> SINGLE_TIGHT;
    available >= doubleRowWidth -> DOUBLE; else TRIPLE`. Reflow reuses the
    controls as today, so selection and listeners survive. Side insets from
    three-button navigation (often 48dp on one side in landscape) push it back
    to two rows, which is correct. Never reflow during contact (V-compact-rail's
    rule).
  - **Acceptance:** MainActivityLayoutTest at w680dp-h360dp-land-mdpi (the
    existing case at :53): the tool bar's height equals one Size.TOOL_ROW plus
    its vertical padding and insets, and assertControlsFit passes. At a width
    just below tightRowWidth the bar falls back to DOUBLE. The reflow tests from
    a16fa83 still pass. Regenerate editor-compact-landscape.png. **Effort:** S.
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
  - **Measured approach (2026-10-06 review's V23, unverified, medium; the
    general verifier confirmed the truncation in editor-narrow-phone.png at font
    scale 1.0):** the fixed chrome takes about 234dp (4dp start padding, 48dp
    Open, 12dp heading padding, 48dp Share, 4dp margin, about 104dp for the
    "Save copy" pill, 12dp end padding), leaving about 86dp, below
    MainActivityLayoutTest's own MIN_TITLE_DP of 96. The icon-only Save appears
    only when `fontScale >= 1.5 && screenWidthDp < 680`
    (MainActivity.kt:265-271), a heuristic rather than a measurement. At 360dp
    with font scale 1.3 the label grows to about 124dp and the title gets about
    108dp; editor-small-phone.png already shows "Quarterl...iew.pdf" at 360dp
    with normal font; translations about 40% longer (German) leave the title a
    few characters. Where: ui/MainActivity.kt:240-273 (crowded rule at 265-271);
    ui/MainActivityLayoutTest.kt:91-99, 196. Approach: build both
    `ui.primaryButton(R.string.save_copy, REGULAR)` and
    `ui.primaryIconButton(R.drawable.ic_save, R.string.save_copy)` and make the
    bar an anonymous LinearLayout subclass, like buildToolBar's onMeasure:
    measure first with the labelled button; if the heading's measured width is
    below TITLE_MIN_WIDTH_DP = 120, hide the label variant, show the icon
    variant and measure again. Cache the decision per width spec so passes
    cannot flip-flop. Keep a list of both views: show() sets isEnabled on both,
    and onKeyShortcut clicks whichever isShown (N2's hidden-target refusal must
    still hold). Keep `marginStart = Space.XS`. The fontScale rule becomes a
    fast path or goes. Tablets and the 411dp phone keep the label; RTL needs no
    change. Raise the test's MIN_TITLE_DP to 120 with it. Acceptance:
    MainActivityLayoutTest cases at w320dp-h640dp-port-mdpi (font 1.0) and
    w360dp-h640dp-port-mdpi with `RuntimeEnvironment.setFontScale(1.3f)`:
    `title.width >= 120dp`, `status.layout.getEllipsisCount(0) == 0`, a view
    with contentDescription "Save copy" isShown, and assertControlsFit passes;
    at w411dp the labelled button is still shown; regenerate
    editor-narrow-phone.png. Effort: M. F25 makes the heading a menu button
    without adding width.
- **V10. Keep the page pill off writing starts (writing auto-hide awaiting
  #36).** Narrow/small screenshots cover bottom text; existing strokes pass
  underneath but new taps cannot.
  Corrected for orientation (2026-10-06 review): since #23 the pill sits at
  BOTTOM|END when `screenWidthDp > screenHeightDp` (MainActivity.kt:212-218),
  in the corner beside the page at fit, so it covers the page's bottom centre
  only in portrait (editor-tablet.png: pill at y about 1128-1175 over a page
  ending at about 1137) and, in any orientation, once zoomed in.
  Fade during writing and optionally after ~3s inactivity, restore on page
  change/bottom-edge gesture, or reserve a gutter outside fitted paper. Choose
  one policy; never relocate midway through a gesture. Proof: bottom-centre
  continuous stroke and fresh tap work; keyboard/accessible navigation and
  rotation recovery remain usable. Depends on writing start/end callbacks
  and B14; share chrome transitions with D-focus.
  #36 implements writing/erasing auto-hide with a 1.5s return and page-change
  reset; no viewport relayout. Two lifecycle regressions fail before the fix
  and pass after it, with light/dark writing screenshots inspected. Fresh
  contact while the pill is initially visible can still hit its controls;
  a reserved gutter or idle/summon policy remains a separate optional slice.
  - **The layout half that needs no hiding (2026-10-06 review, unverified,
    visual review):** maxPan lets the page bottom rise only to pageMargin
    (12dp) above the view edge, while the pill sits 16-64dp above that edge
    (48dp tall plus a Space.L margin). So at every zoom the bottom-centre
    band of the page (about 52dp tall and 220dp wide in portrait; the
    bottom-end corner in landscape) can never be moved to where a stroke can
    start. At fit the centring also ignores the pill: in editor-tablet.png
    the page's white ends at y=1137 while the pill spans y=1128-1176,
    although 54dp of free canvas sits above the page; editor-small-phone.png
    and editor-narrow-phone.png show the pill over the last lines of text.
    Auto-hide stays the answer where the page is too tall to move. Where:
    ui/InkPageView.kt:214, 409-422, 542; ui/MainActivity.kt:212-218.
    Approach: add `var bottomObstruction: Int` (px) to InkPageView;
    MainActivity sets it from a `pagePill` layout-change listener:
    `workspace.height - pagePill.top + dp(Space.XS)` while the pill isShown,
    otherwise 0; in landscape pass the pill's Rect and apply it only if it
    overlaps the page horizontally. (a) When zoomed (`pageHeight > height -
    2 * pageMargin`), clamp panY asymmetrically with the lower bound
    `-(maxPan(pageHeight, height) + bottomObstruction - pageMargin)`, so the
    page bottom can be pushed above the pill. (b) At fit, a pure
    `fitOffsetY(pageHeight, viewHeight, margin, obstruction)` keeps the page
    centred when `(viewHeight - pageHeight) / 2 >= obstruction`, and
    otherwise moves the page up until its bottom sits at `viewHeight -
    obstruction`, as long as the top gap stays at least `margin`; pages too
    tall for that (small phones) stay centred. Use it in onDraw and
    clampPan, ideally together with B14's shared layout (#50). Invalidate
    when the obstruction changes; with #36's auto-hide the obstruction is 0
    while the pill is hidden, and the layout must not change during contact.
    alignTopPending and the swipe check (`zoom <= FIT_ZOOM_TOLERANCE`) are
    unaffected. Acceptance: InkPageViewGestureTest asserting in page units,
    at w800dp-h1280dp-mdpi with `bottomObstruction = 68dp`: a stylus tap 2dp
    above the obstruction at the horizontal centre lands outside the page at
    fit (the page bottom clears the pill); at DOUBLE_TAP_ZOOM after a
    maximal upward two-finger pan, a stylus tap 4dp above the obstruction
    lands at `y >= displayHeight - 2pt`; regenerate editor-tablet.png with
    no overlap between page and pill; device: check how the extra overscroll
    feels.
- **N34. Temporarily hide chrome while writing.** V16/V10 motivate the
  original all-chrome-on-contact proposal. Scope an opt-in fade while a stroke
  is active, restore on lift/cancel, using the writing-start/end callback.
  Keep layout/input mapping fixed during contact; viewport expansion belongs
  to explicit D-focus before writing. Proof: no nib jump, bottom-page access,
  cancellation/rotation restore controls and keyboard/accessible escape works.
  Depends on B14, B-pen-takeover, V10 and focus transition policy; do not create
  another independent chrome-state system.
- **V25. In landscape, notices cover the page middle and clip error text.**
  - **Why:** Severity medium, unverified (visual review,
    editor-compact-landscape.png). In landscape the pill moves to the bottom-end
    corner, but the notice still floats 72dp up (Space.L + Size.TOUCH + Space.S,
    MainActivity.kt:641-643) to clear a centred pill. In the 136dp-tall
    workspace of a 680x360 window, FrameLayout gives the notice at most 64dp, so
    a one-line hint covers 16-64dp from the top, the middle third of the page. A
    two-line error needs exactly 64dp (12 + 12 padding plus two lines of about
    20dp); at font scale 1.15 or above, or with any three-line message, the text
    is cut mid-line with no ellipsis, because maxLines has no ellipsize
    (NoticeBar.kt:31). This is a concrete case of R-notice-overflow. On the
    891x411 phone the notice crosses the middle of a 243dp canvas. Separately,
    the 16dp side margins miss the page's 12dp margins by 4dp (editor-error.png:
    notice x 33-790, page x 24-798).
  - **Where:** ui/MainActivity.kt:212-218, 223-226, 640-643; ui/NoticeBar.kt:31.
  - **Approach:** Keep the `landscape` flag from buildLayout. In applyInsets,
    when the pill sits at END: set the notice's bottomMargin to `dp(Space.L)`
    and its relative marginEnd (so RTL works) to `pagePill.width + 2 *
    dp(Space.L)`; the notice then sits beside the pill at the bottom, keeping
    gravity BOTTOM|CENTER_HORIZONTAL in the remaining width. Re-run applyInsets
    from a pagePill layout-change listener, because the pill width depends on
    font scale. Portrait keeps the lift above the pill. Set `message.ellipsize =
    TextUtils.TruncateAt.END` (recovery text that matters must then still fit;
    see R-notice-overflow and U8's action button, which shares the row).
    Optional: Space.M side margins so the notice lines up with the page edges.
    Coordinate with U10 (#62, stylus passes through notices) and V36 (#59, dark
    notice colours).
  - **Acceptance:** MainActivityLayoutTest at w680dp-h360dp-land-mdpi with
    `setFontScale(1.3f)`: publish `editing()` with
    `EditorMessage(error_source_unreadable, Tone.ERROR)` and measure: the notice
    rect does not intersect the pill rect, the notice top is at or below the
    workspace top, and `message.height >= message.layout.height` (no clipped
    lines). At w411dp portrait the notice bottom is still above the pill top.
    Add an editor-compact-landscape-error screenshot. **Effort:** S.
- **U5. Non-blocking export.** Baseline busy screenshots disable tools/navigation;
  notices cover lower paper. Scope
  replacement separately from export: immutable snapshot, pan/zoom allowed,
  progress tied to actual work; editing only with defined export revisions.
  Proof: slow fake export, navigation works, newer edits stay dirty, failure
  retains draft. Depends on N1's honest outcomes, N23's live-stroke handoff,
  G7, checked commits and real-provider proof.
  - **Opening cannot be cancelled or tracked (2026-10-06 review, unverified, UX
    review; the cancel part of a plausible general finding):** opening copies
    the whole file (`copyTo`) before anything appears; a 150 MB scan from Drive
    or a slow network share leaves the user on "Opening..." or a disabled editor
    with an indeterminate progress line for minutes, with no Cancel and Open
    disabled while busy. Back does not cancel: on Android 12+ it only
    backgrounds the task, and the open finishes and replaces the draft; on 10-11
    it finishes the activity, but onCleared only shuts the executor down, so the
    queued open still runs saveDraft and the next launch shows the abandoned
    document (with B-session-worker's process-scoped worker this becomes the
    rule, so a cancel flag is required). A provider whose stream never delivers
    (a stalled cloud provider, or a hostile SEND with its own content URI)
    blocks `copyTo` forever with every control disabled; removing the task from
    Recents does escape (the verifier corrected "only force-stop helps"). Where:
    ui/EditorViewModel.kt:77-87, 232-244, 258-263;
    document/DocumentStore.kt:56-69; document/DocumentService.kt:48-62;
    ui/MainActivity.kt:445-452, 545-556. Approach: `DocumentOperations.open`
    takes a cancellation check and a progress callback. `DocumentStore.import`
    queries `OpenableColumns.SIZE` (B42) and copies in a loop that checks an
    AtomicBoolean owned by the model and reports progress (at most about 10
    times a second, posted to the main thread); on cancel it throws
    CancellationException and deletes the partial file. To unblock a stalled
    read, also keep the open InputStream in an AtomicReference and close it from
    the main thread (on Android, libcore's close signals threads blocked on that
    file descriptor; device check), and report an IOException after a cancel as
    cancelled, never as SOURCE_UNREADABLE. EditorState gains `progress: Float?`;
    the loading screen and the editor's progress line become determinate when
    the size is known, and a "Cancel" text button appears on the loading screen
    and in the status line. `EditorViewModel.cancelOpen()` sets the flag and
    restores the previous state with no message; onCleared sets it too; check it
    again after inspect and render, right before `store.saveDraft`, so an
    abandoned open never commits. Keeping navigation available during export
    (the rest of U5) is unchanged. Acceptance: DocumentStore Robolectric test
    with a slow registered InputStream: cancelling mid-copy throws and leaves no
    stray file in documents/; for the blocked-read case use a custom InputStream
    whose `read()` blocks on a latch that `close()` releases
    (`PipedInputStream.close()` by the reader does not wake a blocked read);
    EditorViewModelPagingTest-style test with the queue executor and a fake
    whose open() checks the flag: open, cancelOpen, run the worker task: the
    previous draft is shown, `busy = false`, no message; onCleared during an
    open: saveDraft is never called with the new draft; device: pick an uncached
    multi-hundred-MB file from Drive and tap Cancel. Effort: M.
- **U8 (rest). Existing notice actions need lifecycle/timeouts and retry.**
  Slot and storage Save copy handler exist in `829fca1`; do not re-add them.
  Scope timeout pause during accessibility focus and persistent storage-error
  visibility until acknowledged/resolved; use existing slot for failed-save
  retry. #34's recorded 10s action hold is not proof of adequate recovery time.
  Proof: actions/timeouts survive rotation and busy transitions, focus does not
  lose recovery, acknowledgement clears the right notice. B-storage-recovery-effect
  covers localized effect routing; R-notice-action-reentry, destination identity
  and T-notice-ui cover separate callback/URI/theme/target questions.
  - **Text-matched Save copy (open follow-up from the review rounds):**
    MainActivity decides to offer "Save copy" by matching the message text
    (`it.text.contains("couldn’t be stored")`, main `92173fa`
    MainActivity.kt:565), which breaks with any wording change or translation.
    Approach: give EditorMessage an action enum (for example
    `EditorAction.SAVE_COPY`) set by EditorViewModel where it posts
    `notes_not_saved`, and map it to the label and handler in MainActivity;
    NoticeBar's existing `show(text, tone, actionLabel, action)` stays as is.
    Acceptance: a model test shows the action is set for `notes_not_saved` and
    absent for other messages; a MainActivity test shows the Save copy button
    for that message (also under a non-English locale) and not for others.
    Effort: S. This is the concrete fix for B-storage-recovery-effect.
- **V6 (rest). First-document guide.** Welcome omits stylus-only/finger mode,
  swipe/pinch/double-tap and tappable page number; hand icon has no mode label.
  #16's mode toast became a notice in #23. Scope one short dismissible guide,
  persisted dismissal and reachable help; explain U1's mode tradeoff. Proof:
  shows once, no repeat on reopen/rotation, accessible and theme-aware. Depends
  on B12's truthful instructions; avoid a permanent hint row.
  The first-editor mode hint/`hintedGestures` preference already has an
  implementation awaiting #46. Remaining coach marks should point at gestures
  and expose help; reconcile the guide with that hint rather than duplicating it.
  The tool-hint part (hints repeating on every switch and covering the page)
  is U10 (#62); U9 (#58) adds the no-stylus explanation. V6 (rest) keeps only
  the one-time first-run coach mark for swipe, pinch and double-tap.
- **U1. Auto pen-only.** After the first stylus event, stop fingers from
  inking (palm safety) and say so once. Today touch-ink mode lets a resting
  palm draw. Scope an explicit opt-out and mode persistence policy, not a
  change to second-finger cancellation (already Done). Proof: fingers never
  commit after takeover, intentional finger mode remains reachable; physical
  palm/stylus verification. Depends on B-pen-takeover and V6.
- **U2. Hover cursor (awaiting #35 and #39, overlapping)** is consolidated
  into D-preview below.
- **N35 (finger rest). Make eraser size discoverable without hover.**
  Awaiting #39 supplies the pen-hover affordance; finger erasing still reveals
  its ring only on contact. Scope a labelled current-size preview in the
  existing secondary eraser control, no invented finger-hover event or new row.
  Proof: effective size is understandable at current zoom in both themes and
  at large text; selection/cancel creates no ink. Depends on shared eraser
  radius policy and UI measurement; device usability remains unproved.
- **U3. Gesture shortcuts (awaiting #31 and #32, overlapping).** Two-finger tap
  = undo, three-finger tap = redo.
  Scope a completed short tap recognizer, never early edits. Proof: no undo
  during pinch/pan, canceled contacts or palm suppression; intentional taps
  make one history change. Depends on canceled/multi-pointer navigation fixes
  and U1, plus physical-device gesture evaluation.
  #32 implements the recognizer in both modes with per-pointer movement,
  history/release and cancellation checks. Lift jitter, opposite movement,
  release-only movement and cancelled contact regressions pass. Gesture
  haptics exist there; U7's toolbar/page feedback remains distinct.
  #31 is a second implementation of the same recognizer (two-finger tap undo,
  three-finger tap redo); integrate one and carry the other's regressions.
  B-quick-scale, B32 and F23 add tap/gesture paths that must not fire it.
- **U4. Export feedback (Open awaiting #34; Share remains open).** After saving,
  "Open" and "Share" actions on the "PDF saved" notice use the existing U8 slot.
  Scope a compact follow-up
  choice if both are needed. Proof: correct export URI, permission failures
  actionable and rotation does not launch twice. Depends on B-share-lifetime/G7.
  Recorded Open uses ACTION_VIEW/read grant and excludes Asterinked from the
  chooser. Existing Share grants the draft-copy URI, not automatically the
  chosen export destination; verify R-export-destination-identity before wiring it.
- **U6. Undo scope (awaiting #29).** Undo is per page; after turning the page
  the last edit
  elsewhere is out of reach. Consider global chronological undo that jumps
  back, or first label where the next undo applies. Choose/document the scope
  before changing behavior. Proof: undo targets the advertised page across
  turns and restored sessions. Depends on awaiting P-history; retain per-page
  semantics until that separate product decision.
  #29 implements document-wide chronological undo that turns to the edited
  page (with T3's undo part); merging it is that product decision. It and
  #44 (P-history) both rewrite `InkHistory`, so integrate one onto the other.
  - **Same-page, off-screen case (2026-10-06 review, unverified, UX review):**
    when zoomed in, the last stroke is often off screen because the user panned
    after writing; Undo removes it with no visible change, so the user taps Undo
    again and loses a second stroke, and Redo restores something they cannot
    see. Check whether #29 also brings an off-screen change on the same page
    into view; add this if not. Where: ui/InkPageView.kt:163-195;
    ui/EditorViewModel.kt:122-134. Approach: in `InkPageView.show`, when the
    page key is unchanged and the stroke list changed, diff the old and new
    lists by identity (added plus removed strokes) and take the union of their
    bounds; if `zoom > fit` and those bounds do not intersect the visible part
    of the page, animate the pan (a clamped ValueAnimator like animateZoom) to
    centre them. New and erased strokes are always on screen, so in practice
    only undo and redo trigger it. Acceptance: InkPageViewGestureTest: zoom in
    at the top-left, then show() a state where a stroke at the bottom-right was
    removed: after idling 600 ms, `pageAt(view centre)` lies within that
    stroke's bounds plus or minus 40 units; removing a visible stroke does not
    pan.
- **U7 (rest). Haptics (page/undo/redo implementation awaiting #46).** Tool,
  colour, width and finger-mode changes tick (#23). Recorded #46 adds
  `HapticFeedbackConstants.CLOCK_TICK` on valid turns/undo/redo, invalid turns
  silent. Preserve it; remaining verification covers short successful feedback,
  respect opt-out/system settings. Proof: no tick on disabled/failed actions,
  service accessibility remains independent. Depends on device evaluation.
- **U9. Without a stylus, finger or mouse input on the page does nothing and
  nothing explains why (awaiting #58).**
  - **Why:** Severity high, unverified (UX review). The first run defaults to
    InputMode.PEN (MainActivity.kt:129). On a phone or Chromebook without a
    stylus, a finger or mouse (TOOL_TYPE_MOUSE) drag on the page shifts it a few
    dp (at fit, maxPan is just the 12dp margin), draws nothing and shows no
    message. The welcome text ("Open a PDF and write directly on its pages")
    never says a pen is needed; the only explanation (touch_hint, input_hint)
    appears after the user happens to tap the unlabelled hand icon. Picking
    Highlighter or Eraser shows "Drag across text to highlight it" or "Drag
    across strokes to erase them", which a finger cannot do in PEN mode.
  - **Where:** ui/MainActivity.kt:129, 491-512, 533; ui/InkPageView.kt:341-342,
    355-368; res/values/strings.xml:17, 20-23.
  - **Approach:** (a) On first launch only (no inputMode key in the "pen"
    prefs), choose TOUCH when no input device supports SOURCE_STYLUS
    (`InputDevice.getDeviceIds().mapNotNull(InputDevice::getDevice).none {
    it.supportsSource(InputDevice.SOURCE_STYLUS) }`), otherwise PEN; put it in a
    pure function that takes a stylus-detection lambda. (b) Safety net: an
    InkPageView callback `onFingerInPenMode`, fired when a single-finger or
    mouse gesture in PEN mode ends without a scale, fling or double tap, and
    only while no stylus has been seen (pref `stylusSeen`, set on the first
    TOOL_TYPE_STYLUS or TOOL_TYPE_ERASER event). MainActivity then shows one
    notice, once: "Your finger moves the page. Turn on Draw with finger (hand
    icon) to write without a pen.", briefly pulses the hand toggle, and can
    offer a "Turn on" action through U8's action slot, which is now on main. (c)
    Make the highlighter and eraser hints depend on the mode ("Drag your pen
    across..." in PEN mode). Pitfall: some touchscreens advertise SOURCE_STYLUS
    without a pen, which is why (b) is needed. Pair with U1: the first stylus
    event switches an automatically chosen TOUCH mode back to PEN and says so.
    V6's guide and #46's first-editor hint cover the same first-run moment; keep
    one message per moment.
  - **Decided (#58):** a pen paired after the first launch does not switch the
    default mode; the hand button does (documented in `initialInputMode`). #58
    also adds `onHoverEvent`, overlapping #35 and #39 (D-preview).
  - **Acceptance:** MainActivityChromeTest with cleared prefs: publish
    `EditorScreens.editing()`, send a single-finger drag to the page:
    `notice.shown` equals the new string; a second drag shows no notice; with
    `stylusSeen = true` none appears. Unit test of the default-mode function: no
    stylus gives TOUCH, a stylus gives PEN, a stored pref always wins. Device: a
    phone without a pen and a Chromebook with a mouse. **Effort:** M.
- **U10. Notices swallow the first pen stroke, and tool hints repeat on every
  switch (awaiting #62).**
  - **Why:** Severity medium, unverified (visual, UX and aesthetics reviews
    merged). Choosing Highlighter or Eraser, or toggling finger drawing, shows a
    hint notice across the bottom of the page for at least 3 s, longer with
    accessibility timeouts (editor-highlighter.png: notice at y 1254-1349, page
    ends at 1327, so it hides the last text lines). NoticeBar is clickable
    (NoticeBar.kt:52), so a stylus DOWN inside its band (72-120dp above the
    workspace bottom, nearly full width) is consumed: the stroke the user just
    picked the tool for is lost and only dismisses the notice. Success and error
    notices behave the same. A reviewer alternating pen and highlighter sees the
    same sentence dozens of times.
  - **Where:** ui/NoticeBar.kt:39-57, 55-73; ui/MainActivity.kt:223-226, 491-512
    (503, 511), 533, 640-643, 719-723.
  - **Approach:** (1) Pass-through: override `NoticeBar.dispatchTouchEvent`;
    when the DOWN pointer's tool type is TOOL_TYPE_STYLUS or TOOL_TYPE_ERASER,
    return false for every event of that gesture, and on DOWN call `dismiss()`
    when `tone == INFO` (success and error notices keep their timers).
    FrameLayout then offers the DOWN to the next child under the point, the
    InkPageView (loading and welcome are GONE), so the stroke starts normally.
    Finger taps still dismiss; U8's action button on main must still take finger
    taps. (2) Hint count: persist one counter per hint in the "pen" prefs (key
    `"hintShown_" + ToolChoice.name`) and show the hint only while the count is
    below 2. Make `NoticeBar.show` return Boolean (on main it returns Unit;
    false when it returns early because an error is visible) and increment the
    counter only when the hint actually showed. The reviewers differ on the
    finger-drawing hint (counted, or always shown because it changes what
    touches do); recommended: always shown, since U9 adds its own one-time
    notice. Optional follow-up: fade any notice while a stroke is in progress,
    using V10's writing-started callback (N34). This covers the hint part of V6.
  - **Open follow-up (#62 review):** in finger-drawing mode a finger stroke that
    starts on a notice is still swallowed (pen strokes pass through). Product
    decision: a finger tap is the only touch that dismisses a notice; telling a
    tap from a stroke start there (for example pass through once the finger
    exceeds touch slop) is open. Test: in TOUCH mode a finger drag that starts
    on an INFO notice and leaves it inks once, while a finger tap still
    dismisses and adds no ink.
  - **Acceptance:** Robolectric (MainActivityChromeTest style, editor state
    published, strokes recorded through the model or a fake): with an INFO
    notice shown, a stylus DOWN/MOVE/UP built with `MotionEvent.obtain` and
    `PointerProperties.toolType = TOOL_TYPE_STYLUS`, dispatched to the decor
    view inside the notice's bounds over the page, adds exactly one stroke, and
    `notice.shown == null` after Motion.SHORT; a finger tap on the notice still
    dismisses it and adds no ink. Click Highlighter, Pen, Highlighter, Pen,
    Highlighter, idling 4 s between: the hint shows after the first two
    Highlighter selections and `notice.shown` stays null after the third, also
    after an activity restart; toggling finger drawing shows its hint every
    time. **Effort:** S.
- **U11. TalkBack: page turns are silent and the page view offers no actions.**
  - **Why:** Severity low, confirmed (general and UX merged; lowered from medium
    because the labelled Previous and Next buttons still work). InkPageView
    announces the same hard-coded English text on every page ("PDF page. Write
    with a pen. Pinch to zoom; drag with a finger to pan.", InkPageView.kt:150),
    whatever the ink or zoom. It has no onInitializeAccessibilityNodeInfo or
    performAccessibilityAction, so TalkBack scroll gestures and Switch Access
    cannot turn pages from the page, and swipe-to-turn does not work under
    explore-by-touch. After Next page, TalkBack focus stays on the arrow and
    nothing is spoken, because the counter's new contentDescription ("Page 4 of
    12") is not a live region (MainActivity.kt:284-291, 557-561). The counter
    reads "Page 3 of 12, double-tap to activate" without saying that activating
    it opens Go to page (R-page-counter-label; #41 adds a button role and
    navigation-purpose description).
  - **Where:** ui/InkPageView.kt:148-152, 163-195, 439;
    ui/MainActivity.kt:284-291, 553-561, 657-659.
  - **Approach:** InkPageView: `onInitializeAccessibilityNodeInfo` sets
    `info.isScrollable = true` and adds ACTION_SCROLL_FORWARD and
    ACTION_PAGE_DOWN (or PAGE_RIGHT) when a next page exists,
    ACTION_SCROLL_BACKWARD and ACTION_PAGE_UP (or PAGE_LEFT) when a previous one
    exists; `performAccessibilityAction` maps them to `onTurnPage(+1 or -1)`. In
    show(state) call `ViewCompat.setStateDescription(this,
    getString(R.string.page_count, page + 1, count))` (optionally ", 4 notes").
    Move the description to strings.xml and drop the gesture instructions, which
    are read on every focus; this closes B12/N32's view string (keep B12's
    tool/mode-aware instructions available, for example as a hint, rather than
    on every focus). Optional custom actions for Undo and Fit page. Counter:
    `accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE` (it
    changes only on page turns, and polite regions coalesce rapid turns) and
    `ViewCompat.replaceAccessibilityAction(counter,
    AccessibilityActionCompat.ACTION_CLICK, getString(R.string.go_to_page),
    null)`; reconcile with #41's counter description. Do not announce on the
    first show after a restore. Do not use announceForAccessibility, which is
    deprecated in API 36. F-accessible-text remains the separate PDF-text
    boundary.
  - **Acceptance:** Robolectric InkPageViewAccessibilityTest: after `show(page 3
    of 12)`, `createAccessibilityNodeInfo()` has the forward and backward scroll
    actions and stateDescription "Page 3 of 12";
    `performAccessibilityAction(ACTION_SCROLL_FORWARD, null)` calls
    `onTurnPage(1)`; on the last page the forward action is absent. A
    MainActivity test asserts the counter's live region and its click action
    label "Go to page". Device check with TalkBack; whether stylus writing still
    works with explore-by-touch on is device-only. **Effort:** S.
- **U12. The unsaved-notes prompt has no "Save copy first" option.**
  - **Why:** Severity medium, unverified (UX review). Tapping Open with
    unexported notes (dialog-replace.png) offers only "Keep editing" or "Open
    another". To keep the notes the user must cancel, tap Save copy, go through
    the picker, wait, tap Open and pick the file again. For a PDF that arrived
    from another app, "Keep editing" clears `incoming` (MainActivity.kt:576), so
    the user has to open or share it again. Xodo, Samsung Notes and desktop
    editors offer Save, Don't save and Cancel.
  - **Where:** ui/MainActivity.kt:115-117, 574-580, 667-684;
    res/values/strings.xml:33-36.
  - **Approach:** Add a neutral button "Save copy first" (new string) in
    confirmReplacing. Keep an `afterExport: AfterExport?` field (enum
    OPEN_PICKER, OPEN_INCOMING), saved in onSaveInstanceState next to
    `incoming`. Launch savePdf with `exportName()`; if the savePdf callback gets
    a null Uri (picker cancelled), clear afterExport. In show(), once
    afterExport is set and the state reaches `!busy` with `draft.dirty == false`
    (the export succeeded), run the pending action: `openPdf.launch` for
    OPEN_PICKER, `model.open(incoming)` for OPEN_INCOMING. If the export posts
    an ERROR message or the draft is still dirty, clear afterExport and stay.
    Pitfalls: keep `incoming` until the follow-up open has started; never fire
    when there are no notes; keep the platform's button order; N1's split
    outcomes decide what "export succeeded" means when the draft write fails.
    Design the prompt state together with B-pending-pdf (PromptFor) and B22; V20
    colours the destructive button and keeps this neutral one
    on_surface_variant; F6's quick re-save could make it one tap.
  - **Acceptance:** MainActivityChromeTest: publish a dirty
    `EditorScreens.editing()` and click Open: the dialog has three buttons.
    Click "Save copy first": `shadowOf(activity).nextStartedActivity` is
    ACTION_CREATE_DOCUMENT with EXTRA_TITLE "Quarterly review-annotated.pdf".
    Then publish the same draft with `savedInk = ink` and `busy = false`: the
    next started activity is ACTION_OPEN_DOCUMENT. A cancelled save (null
    result) starts nothing. **Effort:** S.
- **U14. Pulling past the page edge while zoomed should continue onto the next
  page.**
  - **Why:** Severity medium, unverified (UX and features reviews merged).
    Swiping turns pages only at fit zoom: onFling returns early above zoom 1.01
    (InkPageView.kt:137-145). On phones reading usually happens zoomed in,
    because fit makes body text about 6pt. At the bottom of a page the drag
    simply stops (clampPan), and the only way on is the pill's Next arrow. Going
    back always lands at the top of the previous page (alignTopPending), not at
    its bottom, where backward reading would continue. The view already keeps
    zoom and column across turns, so only the gesture is missing; it gives most
    of the feel of continuous scroll without a multi-page layout.
  - **Where:** ui/InkPageView.kt:137-145, 170-173, 209-212, 374-397, 414-422.
  - **Approach:** In followFingers, when `inputMode == PEN`, one finger is down,
    `zoom > FIT_ZOOM_TOLERANCE` and panY sits at its clamp (-maxPan at the
    bottom edge, +maxPan at the top), accumulate the part of the finger delta
    that clamping discarded into `overscroll`; reset it on ACTION_DOWN or a
    direction change. Feedback: offset the page by overscroll x 0.4 (rubber
    band) and fade in a small label at the edge ("Next page", then "Release for
    page 4" at the threshold, with a CLOCK_TICK haptic). The reviewers proposed
    72dp and 96dp thresholds; start at 72dp and tune on a device. On the last
    finger up past the threshold, or a fling against the edge, call
    `onTurnPage(+1 or -1)`; the existing alignTopPending lands at the top for a
    forward turn, and a new `alignBottomPending` sets `panY = -maxPan` for a
    backward one, keeping zoom and column. Skip when `penGesture` (palm safety)
    or `gestureScaled` is set, on the first or last page, and in TOUCH mode.
    Vertical only, so it never fights panning across columns. The UX reviewer's
    alternative feedback, `android.widget.EdgeEffect`, is cheaper but does not
    say what releasing will do. Check against U3's gestures (#31, #32),
    B-multitouch-swipe, V10's asymmetric clamp and D-flip's transition.
  - **Acceptance:** InkPageViewGestureTest at 2.5x zoom, panned to the bottom: a
    further 120dp upward drag calls `onTurnPage(1)` once; a 40dp drag does not;
    a palm during a pen gesture does not. At the top edge a downward pull gives
    -1, and after `show(state(page = previous))` `pageAt` of the view bottom
    maps near the page bottom. The existing fit-zoom swipe tests still pass.
    Device: tune the threshold by feel. **Effort:** M.
- **V-night / D-night-full. Theme audit, separate from page inversion.**
  Main's additional "full dark-theme" seed cites `values-night/colors.xml`,
  adaptive launcher colours and theme-aware shadows. Dark chrome already
  exists (#23); scope remaining theme/contrast gaps in new controls/icons,
  preserving neutral/red identity. Inventory before changing tokens; page
  ColorMatrix inversion stays D-night. Proof: both themes, launcher/shadows,
  large text and contrast screenshots plus device icon review. Depends on
  existing design tokens, generated-icon conventions and T4, not a blanket
  redesign justified by the display-only #49 branch.
  Concrete gaps found by the 2026-10-06 review: V36 (notices),
  V-swatch-contrast's
  V18 (swatch edge), V19-V22 (dialogs, field, disabled thumb), V31 (eraser
  ring), V33 (splash) and V35 (system contrast setting); T4 lists the
  screenshot states that would show them.
- **V36. Dark-theme notices are light cards over the always-white page (awaiting
  #59).** (The 2026-10-06 review's V17.)
  - **Why:** Severity medium, unverified (aesthetics review, editor-error.png).
    values-night flips inverse_surface to #E9EBEF, like a snackbar, but the
    notice sits 72dp above the workspace bottom (applyInsets), which on portrait
    phones puts it over the page bottom (editor-error.png: card y about
    1226-1349 px, page ends at 1327) and entirely over the page once zoomed. The
    page stays #FFFFFF in both themes, so at night the card is #E9EBEF on white,
    1.19:1, with its 6dp shadow as the only edge, and its tone icons (#BF1520,
    #1B7A3A) were darkened to suit a light card. Smaller version of the same
    problem: at night the page pill's hairline (outline_variant #2C3037) is
    1.47:1 against canvas #0C0D10, and dark themes show no elevation shadow.
  - **Where:** res/values-night/colors.xml:23-26; ui/NoticeBar.kt:43;
    ui/MainActivity.kt:641-643; ui/Components.kt:188-192 (raisedPill).
  - **Approach:** values-night: inverse_surface #33373F, inverse_on_surface
    #F2F3F6 (10.8:1 on the card), notice_success #7DDA95 (7.0:1), notice_error
    #FF9298 (5.6:1); the card against the white page becomes 11.9:1. New token
    notice_outline (values #00000000, values-night #4A4F59, 2.4:1 against
    canvas); in NoticeBar's init, `background = ui.rounded(...).apply {
    setStroke(ui.dp(Size.HAIRLINE), ui.color(R.color.notice_outline)) }`. New
    token floating_outline (values #E0E3E9 as today, values-night #3A3E46), used
    in `Components.raisedPill` instead of outline_variant. Rewrite the
    colors.xml comment "Notices use the inverse surface" to explain that night
    keeps them dark because the page behind them is white. Light values stay
    unchanged. U8's action button on main must stay legible on the new card
    (T-notice-ui).
  - **Acceptance:** A UiScreenshotTest case editor-error-dark (night qualifier,
    `editing()` plus a Tone.ERROR message), checked by eye. NoticeBarTest under
    `@GraphicsMode(NATIVE)` and `@Config(qualifiers = "night")`: draw the shown
    notice onto a white bitmap; a card pixel beside the icon has relative
    luminance below 0.1, and the stroke pixel at the card edge differs from
    white. **Effort:** S.
- **V19. Dialog buttons inherit the platform's 88dp minimum width (awaiting
  #59).**
  - **Why:** Severity low, unverified (visual review). DialogButton's parent,
    Widget.Material.Button.Borderless.Colored, sets minWidth 88dp. In
    dialog-page.png the 17dp-wide "Go" is centred in an 88dp button: its text
    ends 47dp from the dialog edge, 57dp after "Cancel". In dialog-replace.png
    "Open another" ends 24dp from the edge with a 26dp gap after "Keep editing".
    The two dialogs' buttons do not end at the same edge, and "Go" looks
    detached from its row.
  - **Where:** res/values/styles.xml:44-53.
  - **Approach:** In the DialogButton style add `android:minWidth` 48dp (keeps
    the touch target) and `android:paddingStart`/`paddingEnd` 12dp (the M3
    dialog text-button padding). minHeight unchanged.
  - **Acceptance:** Robolectric: in the page dialog
    `getButton(BUTTON_POSITIVE).width` is between 48dp and 64dp and its height
    is at least 48dp; the gap from the positive button's text end to the decor's
    right edge differs by at most 8dp between the page dialog and the replace
    dialog. Regenerate dialog-page.png and dialog-replace.png. **Effort:** S.
- **V20. Positive dialog buttons are brand red everywhere, so the destructive
  one gets no distinct signal (awaiting #59).**
  - **Why:** Severity low, unverified (aesthetics review). DialogButton.Positive
    applies accent to every positive button (styles.xml:32, 51-53): "Go" (plain
    navigation) and "Open another" (throws away an unexported draft) look
    equally alarming in dialog-page.png and dialog-replace.png. colors.xml gives
    the primary action graphite and reserves red for attention.
  - **Where:** res/values/styles.xml:32, 51-53;
    res/color/dialog_button_positive.xml; ui/MainActivity.kt:672-684;
    ui/PageDialog.kt:68-73.
  - **Approach:** The default colour in `color/dialog_button_positive.xml`
    becomes `@color/on_surface` (keep sans-serif-medium). Add
    `color/dialog_button_destructive.xml` (disabled: on_surface_disabled;
    default: accent), a style DialogButton.Destructive and a style
    AlertDialogTheme.Destructive (parent AlertDialogTheme) that overrides
    `android:buttonBarPositiveButtonStyle`. confirmReplacing uses
    `AlertDialog.Builder(this, R.style.AlertDialogTheme_Destructive)`; the page
    dialog keeps the default theme. Negative buttons stay on_surface_variant,
    and so does U12's neutral "Save copy first" if it lands.
  - **Acceptance:** Robolectric: the page dialog's
    `getButton(BUTTON_POSITIVE).currentTextColor ==
    getColor(R.color.on_surface)`; with a dirty draft, Open shows the replace
    prompt whose positive colour is `getColor(R.color.accent)`. Regenerate
    dialog-page, dialog-replace and dialog-replace-dark. **Effort:** S.
- **V21. The Go to page field looks like an error while its value is valid
  (awaiting #59).**
  - **Why:** Severity low, unverified (visual and aesthetics reviews merged;
    dialog-page.png). The dialog opens with a valid "3" focused and selected:
    focus draws a 2dp accent (#D3111C) stroke, the selection highlight comes
    from colorAccent (a pink block behind the digit), the caret and "Go" are
    accent too, and the out-of-range caption uses the same accent. So "focused
    and valid" looks like "invalid", against colors.xml:21-22 (brand red only
    for state that needs attention). The row also uses CENTER_VERTICAL, so "of
    12" is centred rather than baseline-aligned, its baseline 2.5dp above the
    digit's.
  - **Where:** ui/PageDialog.kt:30-57, 77-82, 101-107;
    res/values/styles.xml:18-24; res/values/colors.xml:21-22.
  - **Approach:** `fieldBackground` gets three states in this order:
    state_activated (accent 2dp stroke), state_focused (on_surface 2dp stroke:
    graphite by day, #E9EBEF at night), default (fill only). In validate() set
    `field.isActivated = page == null && field.text.isNotEmpty()` (the caption
    is visible). `field.highlightColor =
    ColorUtils.setAlphaComponent(ui.color(R.color.on_surface), 0x33)`.
    `field.textCursorDrawable = ui.rounded(on_surface, 1).apply {
    setSize(ui.dp(2), -1) }` (the setter needs API 29, which is minSdk). Remove
    `gravity = Gravity.CENTER_VERTICAL` from the row so LinearLayout's default
    baseline alignment puts "of N" on the field's baseline (TextView.getBaseline
    already includes the centred digit's gravity offset). The caption stays
    accent; the "Go" colour is V20. G14 rewrites the `of_pages` fragment in the
    same row; B-page-dialog and D-next (#41) also change this dialog.
  - **Acceptance:** PageDialogTest under `@GraphicsMode(NATIVE)`: show the
    dialog and draw its decor; a pixel on the field border (left edge, vertical
    centre) is neutral (|R-G| < 24) while the field holds "3"; after
    `setText("99")` with 12 pages it reads as accent (R-G > 80) and the problem
    text isShown. `total.top + total.baseline == field.top + field.baseline`
    within 1px. Regenerate dialog-page.png: the selected "3" sits on a grey
    block, not a pink one. **Effort:** S.
- **V22. The disabled tool thumb renders darker than its track (awaiting #59).**
  - **Why:** Severity low, unverified (visual review, editor-busy.png). While
    busy the selected Pen thumb renders as (221,222,226) inside a (244,245,249)
    track, darker than its surroundings, so it reads as a pressed hollow rather
    than a disabled raised thumb (enabled: thumb 255,255,255 on track
    235,237,241). `thumbPaint.alpha = 97` makes the thumb translucent, but per
    `Paint.setShadowLayer` a translucent shadow colour keeps its own alpha (#33
    of #1B1F27), so the full blurred shadow shows through. HWUI draws shadow
    layers on hardware canvases since API 28, so devices look the same.
  - **Where:** ui/SegmentedControl.kt:25-28, 63-67, 74-85.
  - **Approach:** When `!isEnabled`, skip the shadow
    (`thumbPaint.clearShadowLayer()`, re-applied in `setEnabled(true)`) and
    paint an opaque precomposited colour,
    `ColorUtils.compositeColors(withAlpha(surface_thumb, Alpha.DISABLED),
    compositeColors(withAlpha(surface_track, Alpha.DISABLED), surface))`,
    instead of using paint alpha. The track stays as it is.
  - **Acceptance:** Robolectric NATIVE unit test: a SegmentedControl with three
    48dp children, `select(0)`, `isEnabled = false`, measured, laid out and
    drawn to a bitmap: the luminance at the thumb centre is at least the
    luminance at the third segment's centre. Regenerate editor-busy.png.
    **Effort:** S.
- **V26. Keyboard focus is almost invisible on every control.**
  - **Why:** Severity medium, unverified (visual review). onKeyShortcut and
    onProvideKeyboardShortcuts advertise hardware-keyboard use (Chromebooks,
    keyboard covers), so people will Tab through the bars. The only focus
    feedback is RippleDrawable's focused background: a 40dp disc at 12% graphite
    on #FAFAFC (about 1.3:1), or 16% light on #16181C. ChoiceDot is focusable
    but draws nothing when focused, and on a selected swatch the faint disc sits
    under the 2dp selection ring, so focused and selected are hard to tell
    apart. In practice this fails WCAG 2.4.7.
  - **Where:** ui/Components.kt:75-83, 85-99, 101-111, 118-148, 154-171,
    173-182, 208-210; ui/ChoiceDot.kt:66-69; res/values/colors.xml:18;
    res/values-night/colors.xml:13.
  - **Approach:** A focus_ring colour token (light #1B1F27, night #E9EBEF) and a
    Components helper that returns a StateListDrawable: state_focused draws a
    2dp-stroke GradientDrawable, the default draws nothing. Use an OVAL inset to
    44dp for 48dp-square controls (it clears ChoiceDot's 34dp selection ring)
    and a pill shape outset 2dp for primaryButton, primaryIconButton and the
    page counter. Combine it with each existing background as
    `LayerDrawable(arrayOf(ripple, ring))` in iconButton, toggleButton, segment,
    choice, primaryButton, primaryIconButton and the counter; keep the ripple's
    mask (LayerDrawable forwards hotspots). state_focused only happens outside
    touch mode, so touch users never see the ring. The page field has its own
    focus state (V21). F8 (rest) and T-keyboard cover the keys themselves.
  - **Acceptance:** Robolectric NATIVE test: leave touch mode
    (`decorView.dispatchKeyEvent` with KEYCODE_TAB down and up),
    `requestFocus()` on the Blue swatch and on Undo, and draw the tool bar to a
    bitmap: the pixel 21dp from each control's centre has
    `ColorUtils.calculateContrast(pixel, surface) >= 3.0`, and the same pixel on
    an unfocused control equals surface. Device check with a Bluetooth keyboard.
    **Effort:** M.
- **V27. The status marker jumps sideways between states and stays tiny at large
  font sizes.**
  - **Why:** Severity low, unverified (visual and aesthetics reviews merged).
    The subtitle text starts at x=142px after the 8dp dot (editor-phone.png), at
    x=152px after the 14dp check (editor-saved.png), and at x=113px with no
    marker for "Working..." (editor-busy.png) and "No notes yet": one export
    (Unexported, Working..., All notes exported) slides the text 14.5dp left and
    then 19.5dp right within a second. The markers also stay 8dp and 14dp at
    font scale 2 next to 26sp text (editor-large-font.png), where the dot is
    about a third of the cap height and reads as a speck.
  - **Where:** ui/MainActivity.kt:604-622 (showStatus);
    ui/DesignTokens.kt:30-31.
  - **Approach:** One marker slot for every state, sized from the text: `slot =
    max(dp(Size.STATUS_ICON), round(status.textSize * 1.1f))`; the check is
    drawn at slot size; the dot is `max(dp(Size.STATUS_DOT), round(textSize *
    0.6f))`, centred in the slot through an InsetDrawable; "Working..." and "No
    notes yet" get a transparent drawable (`ColorDrawable(Color.TRANSPARENT)`)
    with the slot's bounds. `compoundDrawablePadding = max(current value,
    round(textSize * 0.45f))`. The Size constants become minimums; update their
    comments. Create the drawables once (P17); a font-scale change recreates the
    activity. Update `theStatusDotStaysWithItsTextRightToLeft` to measure the
    visible dot (bounds minus the inset) or loosen its tolerance by the inset.
  - **Acceptance:** MainActivityLayoutTest: publish the dirty, exported, busy
    and no-notes states in turn: `status.totalPaddingStart` is identical for all
    four, in LTR and in ar-ldrtl. With `setFontScale(2f)` and a dirty draft the
    visible dot width is at least 0.55 x `status.textSize`; at font scale 1 it
    is still 8dp. Regenerate editor-large-font.png. **Effort:** S.
- **V28. The page pill shows dead controls and an undimmed counter.**
  - **Why:** Severity low, unverified (visual review). (a) A one-page PDF
    (letters, forms) always shows two disabled chevrons and a counter that does
    nothing, so half the pill is dead. (b) While busy the chevrons and Fit drop
    to 38% but the counter stays full on_surface (editor-busy.png: counter
    (27,31,39), chevron (168,170,173)), because the Counter text appearance
    colour has no disabled state. (c) Fit stays enabled at fit zoom, where it
    does nothing. (d) The counter's pressed ripple is a 48dp-tall rectangle with
    8dp corners, while every neighbour ripples as a 40dp circle.
    R-page-counter-width and R-page-counter-label cover the counter's width and
    role.
  - **Where:** ui/MainActivity.kt:276-301 (counter ripple mask at :288),
    552-561; res/values/styles.xml:100-104.
  - **Approach:** In show(): `multiPage = state.pages.size > 1`; previous and
    next are VISIBLE only when multiPage, otherwise GONE (the pill re-wraps);
    `counter.isClickable = multiPage` and `counter.isEnabled = ready`, so it
    greys only while busy. Give the counter a ColorStateList (disabled maps to
    on_surface_disabled, default to on_surface). Add `var onZoomedChanged:
    (Boolean) -> Unit` to InkPageView, fired when `zoom > FIT_ZOOM_TOLERANCE`
    changes (in zoomAround, the reset in show() and at animation end), and set
    `fit.isEnabled = ready && zoomed`. Change the counter's ripple mask to
    `InsetDrawable(ui.rounded(Color.WHITE, Size.BUTTON / 2), 0, dp(4), 0,
    dp(4))` so it presses as a 40dp-tall pill. Keyboard Ctrl+0 (F8 rest) and
    D-focus's long-press Fit (#51) must respect the new enabled state.
  - **Acceptance:** MainActivityChromeTest: publish a one-page state: previous
    and next are not shown, the counter is not clickable, and assertControlsFit
    passes. Publish busy: `counter.currentTextColor == on_surface_disabled`.
    Double-tap the page (InkPageViewGestureTest timing): `fit.isEnabled` becomes
    true, and after a Fit click and the animation it is false. Add an
    editor-single-page screenshot. **Effort:** M.
- **V29. A zoomed page merges with the light-theme bars.**
  - **Why:** Severity low, unverified (visual review). At fit a 12dp canvas
    strip separates the bars from the page (editor-small-phone.png). Once
    zoomed, the white page (#FFFFFF) runs straight into the top bar and tool bar
    (#FAFAFC, about 1.04:1) with no divider or elevation, so the bars lose their
    edges and the page looks unbounded. The dark theme is unaffected (dark bars
    against a white page).
  - **Where:** ui/MainActivity.kt:196-198, 229-230, 585-602;
    res/values/colors.xml:8.
  - **Approach:** Two 1dp outline_variant hairline Views in `column`, one under
    the top bar and one above the tool bar, around `workspace`, shown only for
    Screen.EDITOR in showScreen. Always-on is simpler and costs 2dp; the
    alternative (shown only while pageRect touches a bar, through an InkPageView
    callback) is more code for little gain. D-focus hides the bars and their
    hairlines together.
  - **Acceptance:** UiScreenshotTest editor-zoomed case (double-tap the page
    centre with real event times, see T4): the pixel row just below the top bar
    equals outline_variant, not white or surface; check light and night.
    **Effort:** S.
- **V31. The eraser ring disappears over the ink it is erasing.**
  - **Why:** Severity medium, unverified (aesthetics review). The ring is a
    single 1dp stroke of argb(160, 60, 70, 80) (InkPageView.kt:74). Over
    graphite ink (#19262E) it blends into the same dark tone, so it vanishes
    exactly over the strokes being erased; over white it is a faint grey
    hairline. Erased strokes also disappear the instant they are touched, so
    nothing previews what the gesture is about to remove (D-erase-preview).
  - **Where:** ui/InkPageView.kt:74, 242-245, 293-301.
  - **Approach:** Replace `eraserRing` with three paints, constants rather than
    theme tokens because the page is always white (say so in a comment), drawn
    in this order: a fill of #1B1F27 at alpha 0x14; a halo stroke 3dp wide,
    white at alpha 0xD9; a ring stroke 1.5dp wide, #1B1F27 at alpha 0xCC.
    Convert widths to page units as now (dp x density / scale). Share these
    paints with D-preview's eraser hover ring (#35, #39). The optional second
    step (draw strokes being erased at reduced alpha instead of hiding them) is
    recorded under D-erase-preview.
  - **Acceptance:** InkPageViewTest under `@GraphicsMode(NATIVE)`: start an
    eraser drag (DOWN plus MOVE, no UP) centred on a graphite stroke: pixels
    sampled on the ring radius over the stroke include one with luminance above
    0.7 (the halo) and one below 0.2; a ring pixel over blank paper is below
    0.5. Device check with a stylus eraser end. **Effort:** S.
- **V32. Red and green inks are hard to tell apart for red-green colour-blind
  users.**
  - **Why:** Severity medium, unverified (aesthetics review; computed, not
    user-tested). Pen red #B32F3D and green #207149 are CIELAB dE 88 apart for
    typical vision, but 17 under a full-severity deuteranopia simulation
    (Machado 2009) and 13 under protanopia; below about 20 thin lines are easily
    confused, and red-green colour blindness affects roughly 8% of men.
    Reviewers who use red for "problem" and green for "ok" lose that
    distinction. (Blue, red and green also print as similar greys at L* 38, 41
    and 42; that is inherent to inks dark enough to read and is not addressed.)
  - **Where:** ui/MainActivity.kt:750-751 (COLORS); res/values/strings.xml
    (black, blue, red, green names).
  - **Approach:** Move the palettes into a new ui/InkPalette.kt: an internal
    object holding PEN, PEN_NAMES, HIGHLIGHT and HIGHLIGHT_NAMES that
    MainActivity reads and tests import. Change Red to #C8321E (vermilion, 5.3:1
    on white) and Green to #0E6E5C (teal green, 6.2:1): the red/green pair then
    measures dE 52 under deuteranopia and 30 under protanopia, blue/green stays
    at 58 or more, and graphite and blue are unchanged. Existing strokes keep
    their stored `InkStroke.color`, the saved colorIndex still maps, and export
    geometry does not change. Changing the default inks is a product decision;
    the new red is also closer in hue to the brand accent #D3111C. Recheck
    V-swatch-contrast's dark-theme numbers for the new inks.
  - **Acceptance:** A new JVM test InkPaletteTest implements the sRGB-to-linear
    conversion, the Machado deuteranopia and protanopia matrices and CIELAB
    dE76, and asserts that every pair of non-graphite pen inks is at least dE 25
    apart under both simulations and that every pen ink has WCAG contrast of at
    least 4.5 against #FFFFFF. Regenerate the editor screenshots; verify_pdf.py
    stays green (its fixtures use their own colours). **Effort:** S.
- **V33. The splash screen shows a white launcher disc instead of the brand
  asterisk.**
  - **Why:** Severity low, unverified (aesthetics review; needs a device on
    Android 12+). The theme sets no windowSplashScreen* attributes, so the
    system splash shows the launcher icon (the photoreal artwork on its white
    background layer) centred on windowBackground; in dark theme that is a white
    disc on #16181C, a glare flash at night. The app then cross-fades to the
    64dp geometric red asterisk on the welcome screen, so two different marks
    appear in the first second (the launcher's asterisk red sampled from
    icon.png is about #F80810; the UI accent is #D3111C).
  - **Where:** res/values/styles.xml:6-16; res/mipmap-anydpi/ic_launcher.xml;
    res/drawable/ic_asterisk.xml; media-sources/icon.png.
  - **Approach:** Add drawable/ic_splash.xml: a 288x288dp vector with viewport
    288, path
    `M144,121.33V166.67M124.37,132.67L163.63,155.33M124.37,155.33L163.63,132.67`,
    strokeWidth 8, round caps, strokeColor `@color/accent` (night-aware): the
    welcome mark at its 64dp size. In values/styles.xml add `<style
    name="Platform.AppTheme" parent="Base.AppTheme"/>` and make AppTheme's
    parent Platform.AppTheme; add values-v31/styles.xml redefining
    Platform.AppTheme with `android:windowSplashScreenAnimatedIcon` =
    `@drawable/ic_splash` and `android:windowSplashScreenBackground` =
    `@color/surface`. Night still resolves through Base.AppTheme in
    values-night, so no values-night-v31 is needed. Optional: an AVD that draws
    the three strokes with trimPathEnd 0 to 1 (120 ms each, 60 ms apart) plus
    windowSplashScreenAnimationDuration, linking to D-loader's ink-drawn
    asterisk (#48/#53). The adaptive launcher icon stays generated from
    media-sources/icon.png (AGENTS.md).
  - **Acceptance:** Robolectric `@Config(sdk = [35])`, notnight and night:
    resolving `android.R.attr.windowSplashScreenAnimatedIcon` on MainActivity's
    theme gives `R.drawable.ic_splash`. Device: a cold start on Android 12+ in
    light and dark shows the red asterisk on the surface colour, with no white
    disc. **Effort:** S.
- **V34. Motion uses three different curves, and exits ease like entrances.**
  - **Why:** Severity low, unverified (aesthetics review). DesignTokens says
    Motion holds "durations in ms and the one easing curve"
    (DesignTokens.kt:64-71), but double-tap zoom and Fit use their own
    ZOOM_ANIMATION_MS = 220 with DecelerateInterpolator (InkPageView.kt:17,
    424-432, 551), the welcome/editor cross-fade Fade sets no interpolator
    (MainActivity.kt:587), and notice dismissal (NoticeBar.kt:79) and the swatch
    ring shrinking (ChoiceDot.kt:87-95) use the same decelerating curve as
    entrances, so a fading notice lingers over the page.
  - **Where:** ui/DesignTokens.kt:64-71; ui/InkPageView.kt:17, 424-432, 551;
    ui/NoticeBar.kt:79; ui/MainActivity.kt:587; ui/ChoiceDot.kt:87-95.
  - **Approach:** In `DesignTokens.Motion` keep EASING, add `EXIT =
    PathInterpolator(0.3f, 0f, 1f, 1f)` (standard accelerate), and document
    "enter and move: EASING; leave: EXIT". `InkPageView.animateZoom`: duration
    Motion.MEDIUM, interpolator Motion.EASING; delete ZOOM_ANIMATION_MS and the
    DecelerateInterpolator import. `NoticeBar.dismiss`: Motion.EXIT, still
    Motion.SHORT. `MainActivity.showScreen`:
    `Fade().setDuration(Motion.SHORT).setInterpolator(Motion.EASING)`.
    `ChoiceDot.setSelected`: Motion.EXIT when the target is 0f. Respect reduced
    motion as D-flip and D-loader do.
  - **Acceptance:** NoticeBarTest: after `dismiss()`,
    `notice.animate().interpolator` is Motion.EXIT. The existing NoticeBarTest
    timing tests and the InkPageViewGestureTest double-tap tests pass (B20's
    page-turn change touches the same animator). Grepping app/src/main for
    `Interpolator(` finds constructors only in DesignTokens.kt. **Effort:** S.
- **V35. Colour tokens cannot follow Android 14's contrast setting.**
  - **Why:** Severity medium, unverified (aesthetics review, filed there as
    general). Every token is a plain @color resource read with
    `context.getColor`, so colours can change only through resource qualifiers
    (night). Android 14's system contrast setting (`UiModeManager.getContrast`:
    0.5 medium, 1.0 high) has no resource qualifier, so the app cannot respond
    to it. Today the hairlines are 1.29:1 (outline_variant on raised), the tool
    thumb 1.17:1 on its track, swatch outlines 1.60:1 and the finger-mode disc
    1.13:1 on the bar; the selected tool is carried only by a 2dp shadow and a
    filled glyph.
  - **Where:** ui/Components.kt:68; ui/SegmentedControl.kt:24-28;
    ui/NoticeBar.kt:14-19; ui/PageDialog.kt:47; ui/InkPageView.kt:149;
    ui/MainActivity.kt:196, 208, 413, 438, 449, 472, 595, 612, 616;
    res/values/colors.xml.
  - **Approach:** Step 1: res/values/attrs.xml declares one color attr per token
    (asterCanvas, asterSurface, ... asterNoticeError); AppTheme sets each to its
    @color; styles.xml items use `?attr`; `Components.color` takes an `@AttrRes`
    and resolves it with `theme.resolveAttribute`; switch every call site
    listed. Step 2: ThemeOverlay.Asterinked.HighContrast in values/ and
    values-night/. Light: on_surface_variant #3F4655 (9.1:1), outline #7A8291
    (3.7:1), outline_variant #A9B0BC, surface_track #D5D9E0, accent_container
    #F7CDD0, on_accent_container #8A0A12. Night: on_surface_variant #CDD2DA,
    outline #8C93A0, outline_variant #5E6470, surface_track #30343B,
    surface_thumb #5A5F69. Add asterThumbOutline (transparent by default,
    outline in high contrast), stroked 1dp by SegmentedControl. In
    `MainActivity.onCreate`, before buildLayout: if SDK >= 34 and
    `getSystemService(UiModeManager::class.java).contrast >= 0.5f`, call
    `theme.applyStyle(R.style.ThemeOverlay_Asterinked_HighContrast, true)`;
    register `addContrastChangeListener(mainExecutor) { recreate() }` and remove
    it in onDestroy. Put the decision in a pure `useHighContrast(sdk,
    contrast)`. Dialogs pick up the overlay because their ContextThemeWrapper
    copies the activity theme. Page and ink colours do not change. F31 builds on
    step 1; #59's and #30's new tokens must become attrs too.
  - **Acceptance:** Robolectric: with useHighContrast forced on, the outline
    attr resolves to #7A8291 (#8C93A0 under the night qualifier) and
    SegmentedControl draws a thumb outline; with defaults every token resolves
    to its current value and all UiScreenshotTest PNGs are pixel-identical to
    the previous run. Device: Android 14+ Settings > Display > Contrast > High
    recreates the activity with stronger hairlines. **Effort:** M.

---

## 7. Delightful and quirky ideas

Proposals, not confirmed defects or measured demand. Earlier seeds remain
below with descriptive stable IDs. D-next is already awaiting merge above;
U2 is the same item as D-preview. Basic hover, loader, night, focus and flip
implementations are recorded on awaiting branches above; their acceptance
and remaining slices stay here. Do not start duplicate base implementations.
The 2026-10-06 review's D1 (directional page slide) and D2 (hold to snap)
are merged into D-flip and D-straight, its F21 (lasso plan) into D-lasso;
its new ideas are D6-D9 at the end of this section. Hover now also awaits
#35, and the straight-line slice of D-straight awaits #64.

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

### D-preview / U2 / N35. Hover nib and eraser preview (awaiting #39 and #35, overlapping)
- **Evidence:** Hover-capable pens can report `onHoverEvent`; no device proof yet.
- **Scope:** Lightweight ring at transformed real hover position, current
  colour/width or eraser radius; never commit points. Clear on exit/page/detach.
- **Proof:** Synthetic hover changes no ink/dirty state, transforms place it
  correctly; supported physical pens validate cursor behavior.
- **Depends:** B14 and shared tool/width policy (B19), hardware access.
  Reuse #39's `onHoverEvent` implementation; verify page/detach cleanup and
  eraser-end/button switching against this acceptance. N35's non-hover finger
  affordance remains the separate scoped follow-up in section 6.
- **#35 (2026-10-06 review's implementation of U2):** hover ring in the ink
  colour and width, a highlighter preview disc and an eraser ring. It
  overlaps #39 (same feature) and #58 (also adds `onHoverEvent`); integrate
  one hover handler. V31's ring paints and B32's hover-based palm rule should
  share it.
- **Open follow-up (#35 review, low):** `onGenericMotionEvent` updates the
  hover ring for any button event while hovering. Gating it on a stylus or
  eraser tool type was suggested, with the caveat that some OEMs report odd
  tool types on stylus button events; check real S Pen and USI event streams
  before gating. Test: a mouse button event while hovering leaves the ring
  unchanged, a stylus button event updates it.

### D-straight / D-shape. QuickShape / hold-to-straighten (line slice awaiting #64)
- **Evidence:** Original line-snap seed; highlighter is the narrower first slice.
- **#64 (line slice):** resting the pen 600 ms after at least 24dp of travel
  straightens the stroke from its start; moving then drags the end; works
  for pen and highlighter, not the eraser; loops stay freehand. Rectangles,
  ellipses and arrows (below) remain open. Where the scope below says movement
  resumes freehand, #64 (like the review's D2) drags the line's end instead.
- **Scope:** Stationary highlighter end previews a straight line; lift accepts,
  movement resumes freehand. Set dwell/motion thresholds before implementation.
  Horizontal auto-straightening and pen QuickShape remain later opt-in slices;
  F14's pressure-curve change is separate.
- **Proof:** Export matches preview, cancellation creates no edit, undo is
  one edit, ordinary handwriting never unexpectedly snaps.
- **Depends:** Incremental highlighter path, B19, grouped history; preserve
  `InkIncrementalTest` unless a later pen-geometry change is deliberate.
- **Remaining shapes (2026-10-06 review's D2, unverified, features review):**
  underlines, arrows, boxes and circles are the core of reviewing, and
  hand-drawn ones look sloppy (the red underline in editor-tablet.png wobbles
  across two text lines). Where: ui/InkPageView.kt:441-500 (track, addSamples,
  finishStroke; no hold detection before #64); ink/Ink.kt:49-61 (midpoint
  quadratics round corners). Approach: on a hold (the review proposed within 2dp
  for 450 ms after at least 24dp of travel; #64 uses 600 ms, so reuse #64's
  detector and thresholds), run a pure `ShapeRecognizer.recognize(points)` in
  ink/. Line: maximum deviation from the chord under 4% of its length. Ellipse:
  closed (the end within 15% of the perimeter from the start) and a
  least-squares fit residual under 8%. Rectangle: closed, with four curvature
  corners near 90 degrees. Arrow: a line ending in a V. Anything else returns
  null and nothing happens. The live stroke is replaced at once with a tick
  haptic; with the pen still down a line's end can be dragged; lifting commits.
  Data: a plain InkStroke resampled along the ideal shape at 2pt spacing or
  less, with the stroke's median pressure so its weight matches the user's
  writing. Pitfall: InkGeometry's midpoint quadratics round each corner by about
  half the sample spacing, so insert samples 0.3pt either side of every corner.
  No schema change, and the export contract is untouched, because shapes go
  through `InkGeometry.segments` like any stroke. Undo: record two edits (the
  freehand stroke, then freehand to shape) in #29's or #44's history, so one
  undo gives back the freehand version. Highlighter: line snapping only. Add an
  opt-out in prefs. Recommendation: build on #64's line slice; the recognizer
  adds shapes for the pen only. Acceptance: JVM ShapeRecognizer tests with
  synthetic noisy lines, ellipses, rectangles and a scribble (returns null);
  corner test: `InkGeometry.segments` of a resampled rectangle stays within
  0.5pt of the ideal corner; EditorViewModel: undo after a snap restores the
  freehand stroke; an InkPageView test with timed events (hold, then lift)
  commits the shape. Effort: M.

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
- **Plan (2026-10-06 review's F21, unverified, value medium):** a note in the
  wrong spot, a signature placed too big (F19) or a highlight in the wrong
  colour can only be erased and redone. Layout pitfall: at 320dp the tool bar
  already uses the TRIPLE arrangement (editor-narrow-phone.png), whose writing
  row is history 96dp + tools 144dp + finger 48dp = 288dp of the 312dp
  available; a fourth 48dp segment makes 336dp and clips. Where:
  ui/InkPageView.kt:36, 108-113 (InkTool PEN/ERASER only);
  ui/MainActivity.kt:337-352, 719-723; ui/EditorViewModel.kt:111-120. Approach:
  entry without a fourth segment on narrow widths: the stylus side-button action
  SELECT (F28) and a long-press on the pen segment, or a segment only when the
  measured width allows it. Selection: a pure `InkSelection.select(strokes,
  loop)` in ink/ picks strokes with at least 60% of their points inside the
  polygon (ray casting), so a loop that clips an edge still grabs whole words.
  Interaction: a dashed accent bounding box with one corner handle; drag inside
  to move, use the handle to scale uniformly, tap outside to clear. A small
  floating row offers Delete, Colour (applies the current swatch) and Copy
  (in-memory clipboard; Paste on another page). On drop, replace the selected
  instances in place with transformed copies (points mapped, width scaled by the
  scale factor, kind kept), so z-order holds, recorded as one edit. While
  dragging, hide the originals and draw transformed copies through a canvas
  matrix, marking `inkNodeStale` as the eraser does (InkPageView.kt:453). Export
  unchanged; InkGeometryCache recomputes geometry for the new instances.
  Acceptance: JVM InkSelection polygon tests (fully inside, the 60% threshold,
  an empty loop); the transform helper scales widths; EditorViewModel: a move is
  one edit, and undo restores the identical instances (assertSame); InkPageView
  gesture test: loop around a stroke, then drag 100 px; the model receives
  points moved by the page-unit distance `pageAt()` reports; NATIVE pixel test:
  the moved stroke is drawn only at its new spot; UiScreenshotTest's narrow
  phone still shows every control unclipped. Effort: L.

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
- **Directional slide (2026-10-06 review's D1, unverified, aesthetics review):**
  because zoom and column are kept across turns (alignTopPending),
  similar-looking pages can swap with nothing to show that a turn happened, and
  an uncached preview also exposes the jump from blank to rendered page. Where:
  ui/InkPageView.kt:163-195; ui/MainActivity.kt:657-659. Approach: in
  `InkPageView.show`, when pageKey changes within the same document (`document
  == documentKey`), keep the previous page index and compute `dir = sign(new -
  old)`. If `ValueAnimator.areAnimatorsEnabled()` and `|delta| == 1`:
  `animate().cancel()`; `translationX = dir * 24dp`; `alpha = 0.6f`;
  `animate().translationX(0f).alpha(1f).setDuration(Motion.MEDIUM).setInterpolator(Motion.EASING)`.
  Jumps from the page dialog fade alpha from 0.6 to 1 without the slide. Cancel
  and snap back (translationX 0, alpha 1) on any stroke or erase ACTION_DOWN in
  onTouchEvent and in onDetachedFromWindow. Direction stays physical in RTL (the
  next page enters from the right, matching the swipe; see R-rtl-swipe). The
  fade relies on the view's own opaque background so the temporary alpha layer
  stays opaque and multiply highlights still blend with the page; if P20 removes
  that background, recheck the hardware path or drop the alpha. Touch mapping is
  unaffected, because MotionEvents arrive in view-local coordinates (view
  translation is not the input transform). Its 24dp offset differs from N42's
  8dp; pick one when reconciling with #52. Acceptance: Robolectric
  InkPageViewTest: showing page 3 then page 4 of the same document gives
  `translationX > 0` at once and 0 after `ShadowLooper.idleFor(300 ms)`; page 4
  back to 3 gives `translationX < 0`; switching documents gives no offset, and
  neither does an animator duration scale of 0; device: swipe through 20 pages
  with highlights and watch for jank or wrong highlights during the fade.
  Effort: S.

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
- **Warm page dim before inversion (2026-10-06 review, unverified, aesthetics
  review):** in dark theme the page is pure #FFFFFF on canvas #0C0D10 (19.4:1)
  and covers about 55% of the screen in editor-phone-dark.png, by far the
  brightest thing in an otherwise dark UI. Inversion changes what the ink
  colours mean and needs an ink-adaptation layer; a warm multiply dim is a cheap
  first step that does not. Where: ui/InkPageView.kt:48-52, 201-247;
  res/values-night/colors.xml:2. Approach: a token page_tint: values #FFFFFF,
  values-night #DCD7CE (warm paper, luminance 0.68; the page is then 13.6:1
  against the canvas and graphite ink 10.8:1 on it). InkPageView reads it in
  init into a Paint with `blendMode = BlendMode.MULTIPLY`. In onDraw, inside the
  second save, clip and scale block, after the live pen stroke and before the
  eraser ring: `if (pageTint != Color.WHITE) canvas.drawRect(0f, 0f,
  page.displayWidth, page.displayHeight, tintPaint)`. Multiply darkens page,
  highlights and ink alike, so their relationships hold; it runs after the
  cached ink layer on hardware and after drawStrokes in software; the blank page
  while rendering gets it too; the eraser ring and the hover cursor (D-preview)
  stay above it. PdfEngine is untouched, so exports stay white. Inversion (#49)
  remains the stronger opt-in; decide whether the dim is automatic in dark theme
  or part of #49's toggle. Acceptance: InkPageViewTest under
  `@GraphicsMode(NATIVE)` and `@Config(qualifiers = "night")`: the centre pixel
  of a blank page is within 3 of #DCD7CE per channel, and a graphite stroke
  pixel keeps red below 0x40; a notnight twin asserts #FFFFFF;
  PdfEngineRasterTest and verify_pdf.py stay green, which proves exports are
  unchanged; device: the hardware RenderNode path in dark theme, with highlights
  still under pen ink. Effort: S.

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
- **Concrete first step (from the 2026-10-06 review's V31):** instead of
  skipping strokes in `erasing` inside drawStrokes, draw them at paint alpha
  0x40 outside the cached node so the user sees what will go; finishErase
  removes them, a cancel brings them back, and `inkNodeStale` handling stays
  as it is. Land after V31's visible ring.

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
  draw/hold/fade-to-ghost lifecycle checks. V33's splash icon can reuse the
  same three strokes as an animated vector.

### D6. Vanishing pointer ink for presenting and screen sharing
- **Why:** Unverified (features review). When explaining a PDF on a video call
  or casting a tablet to a projector, people circle things to point at them and
  then undo each mark so the document is not cluttered. A pointer whose ink
  fades after a moment fits a pen-first app.
- **Where:** ui/InkPageView.kt:36 (InkTool PEN/ERASER), 230-246 (uncached draw
  block), 492-500 (finishStroke always calls onStroke).
- **Approach:** Add `InkTool.POINTER`. The live stroke draws in the accent red,
  slightly wider. On lift its segments go to `fading:
  MutableList<FadingInk(segments, liftTime)>` instead of onStroke. onDraw paints
  them in the uncached block with alpha starting to fall after 600 ms and
  reaching 0 at 1200 ms, and calls `postInvalidateOnAnimation` while any remain;
  clear them when the page key changes in show(). Pointer ink never enters
  Draft, history, draft writes or exports, and the status line does not change.
  Entry: "Pointer" in the F25 menu, or the F28 side-button action; a notice says
  "Pointer: marks fade and aren't saved"; picking a tool or colour leaves
  pointer mode. Inject the clock (default `SystemClock.uptimeMillis`) for tests.
  No fourth tool segment: it clips the 320dp tool bar (see D-lasso).
- **Acceptance:** InkPageViewTest: with POINTER a stylus stroke never reaches
  onStroke and the draft stays unchanged. NATIVE pixel test: red pixels right
  after lift and none after advancing the injected clock by 2 s and redrawing.
  Device: smooth fading while casting.
- **Effort:** S

### D7. Swatches fade to the new palette while the tool thumb slides
(The 2026-10-06 review's D3.)
- **Why:** Unverified (aesthetics review). Choosing Highlighter slides the
  segment thumb over 220 ms, but the four swatches and three width dots switch
  from ink colours to tints in a single frame (compare editor-phone.png with
  editor-highlighter.png): the most visible result of the switch is the only
  part with no motion. Width dots also repaint instantly when a new ink is
  picked.
- **Where:** ui/ChoiceDot.kt:34-38, 98-126; ui/MainActivity.kt:461-473.
- **Approach:** ChoiceDot keeps `fill` as the target value and adds a private
  `shownFill` and a `fillAnimator`. In the setter: if `isLaidOut && isShown &&
  ValueAnimator.areAnimatorsEnabled() && value != field`, run
  `ValueAnimator.ofArgb(shownFill, value)` over Motion.SHORT with Motion.EASING
  and invalidate on each update; otherwise set `shownFill = value`. onDraw
  paints the dot with `shownFill` but decides the outline from the target
  (`blendsIn(fill)`), so the hairline does not flicker mid-fade.
  onDetachedFromWindow cancels the animator and snaps `shownFill = fill`,
  because toolbar reflow detaches dots. Expose `shownFill` internally for tests.
  Optional: stagger the swatches by 20 ms each. With F-presets (#61) the target
  colours are each tool's own; V-swatch-contrast (#30, #59) changes the outline
  rule this reads.
- **Acceptance:** MainActivityChromeTest with a published document and the strip
  isShown: select Highlighter and `idleFor(70 ms)`: the first swatch's shownFill
  red channel lies strictly between COLORS[0]'s and HIGHLIGHT_COLORS[0]'s; after
  `idleFor(200 ms)` it equals HIGHLIGHT_COLORS[0]; with an animator duration
  scale of 0 it equals the target at once. The reflow tests from a16fa83 still
  pass.
- **Effort:** S

### D8. Pen and highlighter segments show the colour they will draw
(The 2026-10-06 review's D4.)
- **Why:** Unverified (aesthetics review). The idle pen and idle highlighter
  glyphs are both diagonal nibs in on_surface_variant (editor-phone.png, first
  tool row), so the user cannot tell which tint the highlighter will lay down
  without switching and reading the swatches.
- **Where:** ui/Components.kt:102-111; ui/MainActivity.kt:309-313, 454-466.
- **Approach:** A `Components.inkBar()` factory: a 14x3dp GradientDrawable with
  radius 1.5dp, wrapped in `InsetDrawable(bar, 17dp, 37dp, 17dp, 8dp)` so it
  sits just under the 24dp icon and inside the 40dp thumb. Set it as
  `View.foreground` on the Pen and Highlighter segments (a foreground ignores
  imageTintList). In configurePen colour it with the pen colour and the
  highlight colour (each tool's own once F-presets/U13, #61, lands). Bar alpha
  follows enabled (Alpha.DISABLED while busy). Tints under 1.6:1 against
  surface_thumb or surface_track (yellow; ChoiceDot's blendsIn rule) get a 1dp
  `@color/outline` stroke. The eraser segment gets no bar.
- **Acceptance:** MainActivityChromeTest: pick Blue: the pen segment's bar
  `((foreground as InsetDrawable).drawable as
  GradientDrawable).color?.defaultColor == COLORS[1]` and the highlighter
  segment's equals HIGHLIGHT_COLORS[1] (each tool's own value after U13); with a
  busy state the foreground alpha is 97. Regenerate editor-phone and
  editor-highlighter and check them by eye.
- **Effort:** S

### D9. Asterisk marks: flag pages and export them as PDF bookmarks
(The 2026-10-06 review's D5.)
- **Why:** Unverified (features review). Related to D-next (#41), which
  navigates pages that carry ink. In a long review users want to flag pages to
  come back to ("check this clause", "exam topic"); there are no bookmarks, and
  the export carries nothing navigational. The app's brand is a red asterisk,
  the typographic mark for "see note".
- **Where:** document/DocumentStore.kt:16-23 (Draft, dirty), 77-102 (draft.json
  keys); document/PdfEngine.kt:101-143; ui/MainActivity.kt:276-301 (page pill).
- **Approach:** Data: `Draft.marks: Set<Int>`, written as `"marks": [2, 7]` only
  when non-empty; restore() reads only named keys, so older versions ignore it
  but drop it on their next write (document that; coordinate with G6/D-replay's
  schema version). `Draft.dirty` must also compare marks with savedMarks, or a
  new mark would not count as unexported; export sets `savedMarks = marks`. UI:
  a long-press on the page counter toggles the mark with a short animation of
  the ic_asterisk path drawn stroke by stroke in the page corner; a marked page
  shows a red asterisk beside the number in the pill, and F3's overview shows
  marks too; long-pressing the arrows jumps to the previous or next mark (N33
  proposed the same long-press for note navigation; choose one meaning); the
  counter's description becomes "Page 3 of 12, marked". Export: if the document
  already has an outline, append a top-level PDOutlineItem "Asterinked marks"
  whose children "Page 4" (or the page label, F16) point to a
  PDPageFitDestination; otherwise create a PDDocumentOutline with them. Nothing
  is drawn on the page. keepProtection still runs afterwards (PdfEngine.kt:141).
- **Acceptance:** JVM: a DocumentStore round-trip keeps marks, a draft without
  the key restores with none, and `Draft.dirty` becomes true when a mark is
  added. PdfEngineExportTest: exporting marks {1} from a source with an outline
  keeps the original items and adds "Asterinked marks" whose child resolves to
  page index 1. PDFium: a "marks" case in verify_pdf.py finds that entry in
  `pdf.get_toc()`. Robolectric: a long-press on the counter toggles the mark and
  its description.
- **Effort:** M

---

## 8. Tests, tooling and deferred review follow-ups

G8 (test gaps) is partly done: #11 tests gestures, #12 the error mapping,
#13 adds the `DocumentOperations` fake. T3's model history coverage awaits
#44 (and #29 for undo); remaining original gaps are T5/T6. Branch results do
not establish combined-main or hardware coverage. T9-T14 are CI and tooling
items from the 2026-10-06 review; the refuted-claims list closes this section.

- **T8. Tag releases need the independent PDFium gate.** Confirmed
  `.github/workflows/release.yml:61-79` runs tests/lint, assembles/uploads
  release APK, but omits `scripts/verify_pdf.py`; a tag need not point at
  normal-CI-verified code. Install pinned Python test requirements and verify
  before staging/uploading artifacts; publishing depends on verified build.
  Proof: workflow order plus deliberately failed verifier blocks publication.
  No version/signing changes; AGENTS calls this a hard gate.
  - **Tag ancestry (2026-10-06 review, confirmed, medium):** release.yml's own
    comment says a tag can land on a commit CI never saw, and nothing checks
    ancestry: lkm-release does `git push origin HEAD` and pushes the tag without
    a branch check, so `scripts/release.sh X.Y.Z --push` on a feature branch
    head publishes an unreviewed commit. Even from main, the release does not
    wait for ci.yml's PDFium step. Where: .github/workflows/release.yml:32-34
    (checkout), 61-66 (test and build); .github/workflows/ci.yml:56-68.
    Approach: after "Test and lint" add SHA-pinned actions/setup-python, `pip
    install -r scripts/pdf-test-requirements.txt` and `python3
    scripts/verify_pdf.py`, mirroring ci.yml:56-68. For ancestry set
    `fetch-depth: 0` on the existing checkout, keeping `persist-credentials:
    false`; actions/checkout then already fetches `refs/remotes/origin/*`, so
    skip an extra `git fetch origin main`, which would fail on a private
    repository once credentials are not persisted. Before the version gate run
    `git merge-base --is-ancestor "$GITHUB_SHA" origin/main || { echo '!! tag is
    not on main' >&2; exit 1; }`. FROM-CACHE tests are fine because raster-proof
    is a declared output; with T10 the release build runs them anyway.
    Acceptance: on a fork, a tag on a branch commit fails at the ancestry step;
    a tag on main shows the verify_pdf lines ("highlight: ink aligned; text and
    artwork preserved") before "Build release APK"; actionlint passes. Effort:
    S.
- **T1.** `verify_pdf.py`: assert that plain fixtures' *sources* are
  unencrypted before blaming an export (deferred from #12's review).
- **T2.** `EditorViewModelPagingTest`: pin the prefetch order (previous page
  before next page, so the next page survives a one-preview cache; deferred
  from #13's review), for example with a size-limited fake cache.
- **T3, awaiting #44 and #29 (undo part), overlapping.** Model-level
  add/erase/undo/redo coverage now verifies
  dirty flags, page isolation, replacement, autosave, ordering and identity.
  Keep it when integrating history/eraser; do not add duplicate implementation tests.
  #29's undo tests assume document-wide history (U6); #44's assume its delta
  history. Whichever lands second must keep both sets passing.
- **T4 (rest). Screenshot cache output and focused regression gates.** #23
  renders to `app/build/reports/screens/`, but `app/build.gradle.kts:56-58`
  declares only PDFium fixtures as outputs; fresh cache hits restore no
  screenshots. Declare screen output or a dedicated non-cached visual task.
  Proof: clean cache-hit checkout has inspectable images; stable-chrome golden
  comparisons with tolerance/focused assertions catch intentional regressions
  without brittle whole-screen text rasterization. Add combined large-font
  error/dialog and dark-highlighter states when touching those areas; current
  21 screens separate some dimensions and do not prove real-IME usability.
  - **Six missing theming states (2026-10-06 review, unverified, aesthetics
    review):** the 21 screenshots show no notice in dark theme, no
    finger-drawing state (the accent_container disc), no eraser or eraser ring,
    no highlighter palette at night, no page dialog at night, and no zoomed page
    with chrome floating over white. The dark-notice, eraser-ring and focus-ring
    problems (V36, V31, V26) were found by reasoning, not by looking, and a
    golden comparison would not guard them. Add six cases with the existing
    shoot, publish and click helpers: editor-error-dark (night qualifier),
    editor-touch (click draw_with_finger), editor-eraser (click the eraser, then
    dispatch a stylus DOWN and MOVE without UP to the InkPageView found by type,
    so the ring is drawn), editor-highlighter-dark, dialog-page-dark, and
    editor-zoomed (double-tap the page with real event times as AGENTS.md
    describes, then publish a Tone.INFO message so the notice sits fully over
    white). Acceptance: `./gradlew testDebugUnitTest` writes the six new PNGs to
    app/build/reports/screens/; review them by eye and include them in the
    golden set when T4 lands. T12 makes them visible from CI.
- **T5.** `EditorViewModelTest` busy-waits with `Thread.sleep`; move it to
  #13's queue executor. Proof: explicitly drain worker/main posts, no sleeps
  or polling, preserving the same behavioral checks.
  Implemented in #37 with an invocation-order assertion. Await merge; no new
  polling-removal implementation is needed for this test.
  - **Activity tests cannot inject a fake (2026-10-06 review, confirmed, medium;
    open beyond #37):** `by viewModels()` (MainActivity.kt:71) always uses the
    production constructor (EditorViewModel.kt:52: a real DocumentService and
    `Executors.newSingleThreadExecutor()`). Every activity test therefore
    restores a real draft on a real thread and polls with `Thread.sleep(10)`:
    settle() runs 30-50 rounds per call, MainActivityIntentTest waits up to 5 s
    for a notice and spends 1.5 s proving an absence, and
    `EditorScreens.publish` allows 10 s and then writes the private
    `mutableState` field by reflection (EditorScreens.kt:33-67), so renaming
    that field breaks about 30 UI tests only at runtime. The incoming-PDF flow,
    where the unexported-notes prompt guards against data loss, is tested only
    with a missing provider: Keep editing, Open anyway, a newer intent replacing
    a pending one and rotation during the prompt are untested. Where:
    ui/MainActivity.kt:71; ui/EditorViewModel.kt:52; ui/EditorScreens.kt:33-67;
    ui/MainActivityIntentTest.kt:58-66; ui/MainActivityChromeTest.kt:130-135;
    ui/EditorViewModelTest.kt:28. Approach: use the same `internal open class
    AsterinkedApp : Application()` as B-session-worker's proposal (android:name
    in the manifest; reconcile with #40's process-owned worker), with `open fun
    documents(): DocumentOperations` and `open fun worker(): ExecutorService`;
    the EditorViewModel secondary constructor reads them. Tests use
    `@Config(application = FakeDocumentsApp::class)`, which returns the fake
    from EditorViewModelPagingTest (lifted into a shared test file) and its
    queue executor, fetched through `RuntimeEnvironment.getApplication()`, never
    a static (Robolectric creates the Application per test). A lighter
    alternative with less production surface: MainActivity overrides
    `defaultViewModelProviderFactory` to read an internal test hook. Rewrite
    `EditorScreens.publish` to drive the fake's restore result instead of
    reflection, and replace settle() with the queue's `runAll()` plus
    `shadowOf(mainLooper).idle()`. Add the prompt tests (B-pending-pdf, B22,
    U12). Acceptance: `grep -rn "Thread.sleep\|getDeclaredField" app/src/test`
    is empty; new MainActivityIntentTest cases: a dirty draft plus an incoming
    VIEW shows the prompt; Keep editing keeps the draft and does not call open;
    a second incoming URI during the prompt replaces the pending one; rotation
    during the prompt asks again; the ui test classes run measurably faster
    (compare the Gradle test report durations). Effort: M.
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
  #36's MainActivityChromeTest stylus helper uses fixed timestamps while its
  looper advances. This does not affect current pill-timer assertions; before
  reusing it for velocity/gesture timing, take eventTime from SystemClock and
  preserve downTime for the contact sequence. Deferred minor review follow-up.
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
  F8 (rest) proposes the arrow/Space/Home/End/zoom keys (in
  `InkPageView.onKeyDown`, not the activity), wheel and trackpad input, and a
  MainActivityKeyboardTest built on T5's activity-test seam; at `d48a41d` no
  test sent a key event (branch tests such as #41's are not on main).
- **T-notice-ui. Verify the existing action control.** Current-main U8 now has
  a slot and the build repair; measure action/text at 320dp,
  200% text, RTL and both themes. Verify 48dp target/no overlap, contrast and
  theme-token use, with focused screenshots and real TalkBack focus. This is
  validation of existing controls, not another action-slot implementation.
  Include the dark notice card from V36 (#59) and the landscape placement
  from V25; T13's label checks can cover the action button's label.
- **T9. Nothing verifies that published APKs carry the checked-in signing
  certificate.**
  - **Why:** Severity low, confirmed (a defensive guard: a keystore swap is a
    visible binary diff, and AGENTS.md forbids it). ADR 0001's upgrade contract
    is that every APK is signed by app/debug.keystore, but no workflow checks
    the certificate of the APK it uploads or publishes, and the ADR records no
    digest. If the keystore is replaced (a binary merge conflict resolved to the
    wrong side, a "rotation", a signingConfig edit pointing elsewhere), CI stays
    green and the release goes out signed with another key: every installed
    user's update fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and the only
    way forward is uninstalling, which deletes their drafts.
  - **Where:** .github/workflows/release.yml:65-73;
    .github/workflows/ci.yml:47-54, 70-75; app/build.gradle.kts:17-40;
    docs/decisions/0001-checked-in-debug-keystore.md.
  - **Approach:** Run `apksigner verify --print-certs
    app/build/outputs/apk/release/app-release.apk` once and record the "Signer
    #1 certificate SHA-256 digest" in ADR 0001 and as an `EXPECTED_CERT_SHA256`
    env in both workflows. After assembling, add: `BT=$(ls -d
    "$ANDROID_HOME"/build-tools/* | sort -V | tail -1); "$BT/apksigner" verify
    --print-certs "$APK" | grep -qx "Signer #1 certificate SHA-256 digest:
    $EXPECTED_CERT_SHA256"`, for the release APK before staging in release.yml
    and for both APKs in ci.yml. apksigner prints lowercase hex without colons;
    match the whole line, not a substring. No keystore or signing change.
  - **Acceptance:** The step passes on main. Locally replacing
    app/debug.keystore with a fresh keytool-generated keystore makes it fail.
    The release run log shows the verified digest. **Effort:** S.
- **T10. ci.yml uses moving action tags while release builds restore its Gradle
  cache.**
  - **Why:** Severity medium, confirmed. release.yml pins every action by SHA,
    but ci.yml uses moving tags (actions/checkout@v7,
    gradle/actions/wrapper-validation@v6, setup-java@v6, setup-gradle@v6,
    setup-python@v7, upload-artifact@v7; ci.yml:32, 35, 37, 45, 60, 71) and
    checks out without `persist-credentials: false`. ci.yml runs on every main
    push, and setup-gradle saves ~/.gradle (dependency jars and the local build
    cache with dex and class outputs; gradle.properties sets
    `org.gradle.caching=true`) at job end; tag builds in release.yml restore
    caches saved on the default branch. A moved tag on any of those actions can
    therefore tamper with cached jars or build-cache entries that the next
    release signs and publishes. ci.yml also builds the debug APK that README
    tells users to install.
  - **Where:** .github/workflows/ci.yml:32, 35, 37, 45, 60, 71;
    .github/workflows/release.yml:46; .github/dependabot.yml.
  - **Approach:** Pin every ci.yml action to the SHAs release.yml uses, with `#
    vX.Y.Z` comments (Dependabot's github-actions ecosystem, already configured,
    updates SHA pins and their comments). Add `with: persist-credentials: false`
    to the checkout. In release.yml give setup-gradle `cache-disabled: true`, so
    release builds resolve dependencies fresh and run every task (the 50-minute
    timeout covers the slower build). Update AGENTS.md's FROM-CACHE note to say
    it applies to CI only. This fits the family contract (least privilege,
    wrapper validation) in AGENTS.md.
  - **Acceptance:** `grep -nE 'uses: [^ ]+@v[0-9]' .github/workflows/*.yml`
    returns nothing. The release run log shows Gradle caching disabled and the
    tests executing rather than FROM-CACHE. **Effort:** S.
- **T11. Lint warnings never fail CI and there is no baseline.**
  - **Why:** Severity low, confirmed. lint-results-debug.txt ends with "0
    errors, 7 warnings" (DataExtractionRules at AndroidManifest.xml:3, UseKtx at
    InkPageView.kt:222, 230 and PdfEngine.kt:57, and three
    TrustAllX509TrustManager in bcpkix-jdk15to18-1.72; the Evidence section
    above lists the same classes). app/build.gradle.kts has no `lint { }` block,
    so CI is green with these and any new warning (DefaultLocale, a
    NewApi-adjacent check, a security or accessibility check, ObsoleteSdkInt)
    lands silently. The UI is built in Kotlin, so lint's XML-only checks
    (HardcodedText, ContentDescription) never apply, which makes the remaining
    checks the only automated guard.
  - **Where:** app/build.gradle.kts:5-51.
  - **Approach:** In `android { }` add `lint { warningsAsErrors = true;
    abortOnError = true; baseline = file("lint-baseline.xml") }` and generate
    the baseline with `./gradlew updateLintBaseline`, so today's 7 warnings are
    recorded (G5's #28 removes DataExtractionRules; `checkDependencies` does not
    affect the jar-bytecode TrustAllX509TrustManager warnings, which the
    baseline covers). Decide in the PR whether to `disable += "UseKtx"` instead
    of baselining it. Document in AGENTS.md how to refresh the baseline and that
    only deliberate entries belong in it. Dependabot AGP bumps that add checks
    will then fail CI, on purpose: update the baseline deliberately in the bump
    PR.
  - **Acceptance:** CI passes with the baseline. Adding `if
    (Build.VERSION.SDK_INT >= 21)` to main sources (ObsoleteSdkInt with minSdk
    29, which fires reliably in Kotlin, unlike `"%d".format(1)`) makes
    `./gradlew lintDebug` fail locally. **Effort:** S.
- **T12. CI throws away the test reports, screenshots and PDFium renders.**
  - **Why:** Severity low, confirmed. ci.yml:70-76 uploads only the debug APK.
    When a Robolectric test or the PDFium check fails, the HTML test report, the
    21 screen PNGs from UiScreenshotTest and the PDFium renders are discarded
    with the runner, so reviewers of a UI PR, human or GLM, cannot see what
    changed without a local build, and T4's golden comparison needs these files
    as input. UiScreenshotTest's build/reports/screens is not a declared task
    output, so a FROM-CACHE run does not restore it (T4).
  - **Where:** .github/workflows/ci.yml:70-76; app/build.gradle.kts:53-58;
    ui/UiScreenshotTest.kt:162.
  - **Approach:** A SHA-pinned actions/upload-artifact step with `if: always()`,
    named `asterinked-reports-${{ github.sha }}`, with paths
    app/build/reports/tests/testDebugUnitTest/, app/build/reports/screens/,
    app/build/reports/lint-results-debug.html and
    app/build/test-output/raster-proof/*.png, `if-no-files-found: warn` and
    `retention-days: 14`. Add
    `outputs.dir(layout.buildDirectory.dir("reports/screens"))` next to the
    raster-proof declaration (this is T4's output declaration; do it once) and
    update AGENTS.md's cache note.
  - **Acceptance:** A PR run shows the reports artifact with the screen PNGs and
    the test HTML. A rerun where the tests come FROM-CACHE still has the PNGs.
    **Effort:** S.
- **T13. No automated label checks run over the rendered screens.**
  - **Why:** Severity low (plausible; narrowed:
    `MainActivityLayoutTest.assertControlsFit` already fails any shown clickable
    editor control under 48dp, off-screen or overlapping, across nine
    configurations, so the reviewer's 48dp premise was wrong). Labels, duplicate
    labels and contrast are not checked anywhere, and neither are the welcome
    screen or the dialogs: a new icon button without `describe()`, or two
    controls with the same label (for example "Blue" as both a pen and a
    highlighter swatch after a palette change), would pass CI. UiScreenshotTest
    already brings every screen state to a laid-out hierarchy.
  - **Where:** ui/UiScreenshotTest.kt:131-165 (shoot);
    ui/MainActivityLayoutTest.kt:117-138; ui/Components.kt:216-245.
  - **Approach:** Add a label check to `UiScreenshotTest.shoot`, or extend
    assertControlsFit: every shown, enabled, clickable view has a non-empty
    contentDescription or text, and no two shown clickable views share a label;
    keep a justified allow-list (for example InkPageView). Cover the welcome and
    dialog states too. Optional: the Accessibility Test Framework
    (testImplementation, version only in libs.versions.toml) with
    `AccessibilityCheckPreset.LATEST` and the captured bitmap for contrast
    checks, if it builds a hierarchy under Robolectric.
  - **Acceptance:** The check runs in all screen-state tests and passes.
    Temporarily removing `describe(label)` from `Components.iconButton` makes
    the screenshot tests fail with the offending view named. **Effort:** S.
- **T14. Local test runs fail confusingly on a nearly full disk.**
  - **Why:** From the 2026-10-06 review's own runs (compare the environment
    failures under Evidence above). Robolectric's NATIVE graphics mode extracts
    about 200 MB per test JVM into /tmp (`robolectric-nativeruntime*`
    directories). On a nearly full disk Gradle reports ENOSPC as "Unknown type
    of test metadata" or as random test failures, which look like flaky tests
    rather than a full disk. Killed runs leave the extracted directories behind.
  - **Where:** AGENTS.md ("Toolchain quirks" or "Verification");
    app/build.gradle.kts:56-58 (test task configuration).
  - **Approach:** Add a note to AGENTS.md: before local `testDebugUnitTest`
    runs, check `df -h /tmp` and budget about 200 MB of /tmp per test JVM;
    delete stale `/tmp/robolectric-nativeruntime*` directories left by killed
    runs; treat "Unknown type of test metadata" or scattered unrelated failures
    as a disk-space symptom first. Optionally, when /tmp is small, point the
    test JVMs elsewhere with `systemProperty("java.io.tmpdir", ...)` in the
    existing `tasks.withType<Test>` block; keep that out of CI unless needed. In
    the same AGENTS.md edit, add one line next to "PdfRenderer itself only runs
    on devices": Robolectric's PdfRenderer constructor throws NoSuchMethodError,
    an Error that `DocumentService.during()` does not catch, so
    open/restore/render paths need DocumentOperations fakes.
  - **Acceptance:** AGENTS.md carries the notes, and a reader who hits "Unknown
    type of test metadata" finds the cause there. **Effort:** S.

### Unverified concerns: reproduce before implementation

- **R-share-recreation (scratch N14).** The claim that acknowledging `shared`
  loses an Android chooser on rotation was not reproduced. The chooser owns
  its activity lifecycle; retaining the editor effect risks duplicate launch.
  Verify chooser rotation, cancel/resume and activity recreation on a device,
  plus exactly-once delivery under G7, before implementing recovery behavior.
  Keep B-share-lifetime separate.

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
  V25 computes a concrete loss: in a 680 x 360dp landscape window the notice
  gets at most 64dp, so at font scale 1.15+ or with three lines the text is
  cut mid-line without ellipsis (maxLines has no ellipsize, NoticeBar.kt:31).
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
  - **Evidence and proposed rule (2026-10-06 review's U15, plausible, low; the
    rule is a product decision):** editor-rtl.png confirms the mirrored pill:
    Next sits on the left and points left, while a leftward finger swipe still
    means next (InkPageView.kt:143, `distance < 0` gives +1). For an ordinary
    left-to-right PDF the mirrored pill also suggests that the next page lies to
    the left. Where: ui/InkPageView.kt:137-145; ui/MainActivity.kt:276-301;
    res/drawable/ic_next.xml, ic_previous.xml (autoMirrored). Proposed rule:
    page progression follows the document, not the UI locale (the general
    reviewer's rule; recommended over mirroring the swipe in RTL UIs, because
    almost all PDFs run left to right). Minimal step: `pagePill.layoutDirection
    = View.LAYOUT_DIRECTION_LTR` and `counter.textDirection =
    View.TEXT_DIRECTION_LOCALE` (localized digits keep their own direction); the
    swipe stays as it is. Follow-up: read /ViewerPreferences /Direction
    (`documentCatalog.viewerPreferences?.readingDirection == R2L`) in inspect,
    carry it in OpenDocument and EditorState (cache it with G6), and for R2L
    documents make the pill RTL and invert the fling mapping. Page Up and Page
    Down keep their meaning; F8 (rest)'s arrow keys and D-flip's slide direction
    follow the same rule. Refresh editor-rtl.png. Acceptance:
    MainActivityLayoutTest with the ar-ldrtl qualifier: for a left-to-right
    document the Next button's x is greater than the Previous button's x;
    follow-up InkPageViewGestureTest with direction R2L: a rightward fling at
    fit calls `onTurnPage(1)`; device: in an Arabic UI the swipe and the arrows
    agree. Effort: S.
- **R-import-durability. Imported source may lack a durability sync.**
  `DocumentStore.kt:63` closes without explicit sync. Fault/crash or power-loss
  evidence must show lost source before calling it confirmed data loss.
  Probe ordering with checked commits above; draft-rename failure is already
  confirmed separately, not proof of this concern.
  - **Mechanism and cheap hardening (2026-10-06 review's B43, plausible, low;
    needs a power loss or kernel panic inside the writeback window; cannot be
    observed on the JVM):** import copies through a plain FileOutputStream
    without sync (DocumentStore.kt:63). saveDraft then fsyncs only draft.json
    through AtomicFile (no directory fsync either) and unlinks the previous PDF.
    With delayed allocation on ext4 or f2fs a crash can persist the rename while
    the new PDF's data is not on disk: draft.json points at a zero-length PDF,
    the previous one is gone, and the next launch fails restore (B8). Where:
    document/DocumentStore.kt:56-69. Approach: `file.outputStream().use { out ->
    source.copyTo(out); out.fd.sync() }`, one fsync per import, not per stroke.
    Acceptance: code review; optional on a rooted device: import a large PDF,
    run `echo c > /proc/sysrq-trigger` as soon as the editor shows, reboot, and
    confirm the draft restores. Effort: S. Recommendation: the one-line sync is
    cheap enough to apply as hardening without first proving loss; keep calling
    the loss unconfirmed until the fault test shows it, and keep directory-fsync
    questions with B-atomic-commit.

### Refuted claims (do not re-raise)

Each was checked against source, bytecode, fixtures or green checks; the
entries named hold the details.

- **"A new ACTION_DOWN continues or swallows a stale stroke"** (2026-10-06
  bugs-input review). `ViewGroup.dispatchTouchEvent` calls
  `cancelAndClearTouchTargets` on every ACTION_DOWN and sends ACTION_CANCEL to
  the previous target, which InkPageView handles with `cancelStroke()`
  (:307-311) before the new DOWN arrives; the proposed test bypassed this by
  calling `onTouchEvent` directly. Samsung's legacy S Pen side-button codes
  211-213 would at most leave a stroke open until that synthetic CANCEL discards
  it; file a separate, device-verified item only if they are seen reaching
  onTouchEvent on Android 10+.
- From earlier rounds (see the scorecard and entries above): invented
  golden-test failures; base-first AtomicFile recovery; RC4-export regression
  (the RC4 input fails before export, B-rc4-import); an absent V5 library
  constant (G9(a)); #28's backup blocker; #36's display-name collision; the
  exact-fit 12dp wiggle (N26); the universal org.json NaN/Infinity acceptance
  (B19); a historical schema without `savedInk` (N25, unproved); O(new strokes)
  `InkHistory.record` documentation; defensive note-destination sorting as a
  defect; the unreproduced chooser loss on rotation (R-share-recreation).
- Withdrawn or narrowed by the 2026-10-06 verifier (the rest of each finding
  stands in its entry): NaN and Infinity widths surviving a restore (B19;
  overflowing finite literals still do), and with it a lenient streaming
  JsonReader (P5); a generation counter for draft snapshots (B23: the
  draft-writer barrier suffices); moving prefetches ahead of the draft write
  (B23: widens the durability window); ending the zoom animation at pen-down
  (B20/B-pen-takeover: freeze it); capping undo with a dropLast(1) fallback
  (P-history: turns undoing an erase into deleting a stroke); automatic
  re-export after process death (B37: deferred product decision); signed-PDF
  exports as a bug (F30: the original stays intact); "only force-stop escapes a
  stalled import" (U5: removing the task from Recents kills the process);
  ClipData-only SEND and file:// streams from conforming senders (B41:
  non-conforming or blocked by FileUriExposedException; the guard stays as
  defence); `page_out_of_range`'s "1" and the page field's ASCII digits as
  localization defects (G14); "no guard catches sub-48dp controls" (T13:
  `assertControlsFit` does); "Bouncy Castle and Liberation Sans ship no licence
  at all" (G1: LICENSE.class and name-table notices exist, unreadable to users);
  the "under 3x the file size" allocation target and a byte-identical golden
  JSON (P5); an OutOfMemoryError skipping failWrite as a real hazard (P5:
  harmless on both AtomicFile versions); the extra `git fetch origin main` in
  the release ancestry check (T8: fails without persisted credentials).
- Severities lowered by that verifier: B23 and B25 from high to medium; P12
  (P-live-chunks) from high to medium; B26, B30, B31 (B-quick-scale), B34, B36
  (N1) and B37 from medium to low; P14, P16 and P18 (P-prefetch) from medium to
  low; U11 from medium to low. No valid finding of that review was dropped: each
  is an entry, merged into one, or a refinement.

## 9. Device verification checklist

Robolectric cannot run `PdfRenderer` (its constructor throws
NoSuchMethodError, an Error that `DocumentService.during()` does not catch,
so DocumentService open/restore/render paths need fakes), hardware canvases,
real styluses or launchers. Before a release, check on a device (ideally one
with an active
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
  After #39 or #35 integration, hover ring follows nib/width, switches to
  eraser,
  disappears during writing, finger contact and hover exit/page replacement.
- Open a PDF from Files, Gmail and a browser download; share to Gmail and
  Drive; rotate during the unsaved-notes prompt (#15).
- Themed and adaptive launcher icon; at least 48dp tool targets (`Size.TOUCH`,
  enforced by `assertControlsFit` since #23); page pill placement in both
  orientations: bottom centre over the page in portrait, bottom-end corner in
  landscape, and over the page in any orientation once zoomed (#16, #23).
- Review-pass branches (#56-#64) and items: a palm on the tool bar while
  writing (B21), the Back gesture strip at the page edges with gesture
  navigation (B29), a palm landing before the pen (B32), pen strokes through
  notices (U10), finger-first default on a phone without a pen and a
  Chromebook mouse (U9), hold-to-straighten timing (#64), dark-theme notice
  and dialog colours (#59), and splash/high-contrast behaviour on Android 12+
  and 14+ (V33, V35).
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
| U8 (slot/storage callback, source only) | Optional action slot and storage-error Save copy handler exist; locale, lifecycle, UI and provider checks remain open | Main `d9a9bef`, including build repair |
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
| N2 | Hidden/disabled key consumption; #27 and recorded #46 overlap; T-keyboard remains |
| N21 | B-pen-takeover: margin contact must not falsely latch writing ownership |
| N22 | B-pending-pdf: confirmation must resolve latest incoming URI, plus dismissal processing |
| N23 | Live-stroke handoff at export/share, alongside U5 revision semantics |
| N24 | Persist viewport across rotation with document/page identity |
| N25 | B8/#24: missing savedInk preserves notes as unexported; historical-schema claim unproved |
| N26 | Near-fit motion risk; exact-fit 12dp wiggle claim refuted by clamp math |
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

### 2026-10-06 review: merged and renumbered IDs

That review's IDs that duplicated existing entries live on in those entries;
its renumbered IDs avoid collisions with IDs this file already used. Its
other IDs (B21-B23, B25-B27, B29, B30, B32, B34, B35, B37-B39, B41, B42,
G12, P11, P14, P16, P17, P19-P21, P25, F17-F20, F23, F25-F31, V19-V22,
V24-V29, V31-V35, U9-U12, U14, D6, T9-T14) are unchanged entries above.

| Review ID | Canonical entry/status |
|---|---|
| B24 | B-session-worker (#40) |
| B28 | N24 |
| B31 | B-quick-scale |
| B33, P24 | B-eraser-geometry / P-eraser / N27 (#43) |
| B36 | N1 (#34) |
| B40 | B-pending-pdf / N22 |
| B43 | R-import-durability |
| G10 → G13 | Export temp, scratch and shared-copy cleanup |
| G11 → G14 | Localization readiness |
| P12, P22 | P-live-chunks / P10 |
| P13 | N28 |
| P15 | N29 |
| P18 | P-prefetch |
| P23 | P-history (#44; #29 overlaps) |
| F3 step 1 | D-next (#41); step 2 in F3 (rest) |
| F16 → F32 | Existing annotations and form values |
| F21 | D-lasso |
| F22, F24 | F16 / N36 / N37 |
| V17 → V36 | Dark-theme notices (#59) |
| V18, V30 | V-swatch-contrast (#30; V18 in #59) |
| V23 | V17 |
| U13 | F-presets / N38 (#61) |
| U15 | R-rtl-swipe |
| D1 | D-flip / D-5 / N42 (#52) |
| D2 | D-straight / D-shape (line slice #64) |
| D3 → D7, D4 → D8, D5 → D9 | Renumbered; D-3, D-4 and D-5 are other aliases |
| T8 | T8 (tag ancestry added) |
| U2, U3, U6 (status table) | D-preview (#35), U3 (#31), U6 (#29) |

## 12. This scratch pass: preserved aliases and remaining proposal

Scratch-only labels are namespaced here to avoid colliding with main's N IDs:
N1→N1; N2→N2; N4→B8/N25; N6→P-eraser/N27;
N14→R-share-recreation; N17→P-live-chunks/N29; N20→N26;
N25→U7; N26→V6; D-zen→D-focus. Their ideas and evidence are consolidated
above rather than duplicated as another implementation queue.

### D-restored / scratch D-recent. Acknowledge a recovered document
- **Proposal:** One quiet notice names the draft after a successful cold
  restore, making persistence discoverable before F9's recent-document card.
- **Where/scope:** EditorViewModel's restore completion through G7 effects;
  no message for empty/failed restore, no repeat on rotation, and storage
  recovery errors take priority. Reuse NoticeBar, not another permanent row.
- **Proof:** Successful restore emits once; recreation, empty restore and
  failure emit no duplicate acknowledgement. Lower priority than recovery
  correctness and F9; validate whether it helps rather than adding noise.

`tmp.md` is removed after its facts are consolidated here. Awaiting branches
stay separate from main-done work; upstream history and proposal details remain.
The 2026-10-06 review is consolidated here as well; its IDs map through
section 11 and its implementations are listed under "Review pass".
