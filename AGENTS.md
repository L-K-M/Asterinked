# AGENTS.md — operating manual

The operational source of truth for agents (and humans) working on Asterinked.
When you learn something durable about how this repo behaves — a quirk, a
footgun, a changed convention — **update this document in the same PR**.

## What this app is

Pen-based PDF annotation for Android 10 and newer (`ch.lkmc.asterinked`).
Open a PDF through the document picker, write with a stylus (pressure changes
line width; fingers pan and pinch), export a copy with the ink baked in as
vector page content. Kotlin, single module `:app`, single activity, MVVM with
`EditorViewModel`; no Compose — the drawing surface is a custom `InkPageView`.
README.md has the architecture sketch and the current-limits list.

## Build, test, lint

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug   # exactly what CI runs
scripts/build.sh                                      # release APK -> dist/
scripts/build.sh --debug                              # debug APK -> dist/
scripts/install.sh                                    # build + install + launch on a device
python3 -m pip install -r scripts/pdf-test-requirements.txt
python3 scripts/verify_pdf.py                         # PDFium re-render of test exports
python3 scripts/generate_launcher_icon.py             # launcher icon from media-sources/icon.png
```

The adaptive icon's foreground webp files are generated; regenerate them from
`media-sources/icon.png` instead of editing them by hand.

- JDK 17. Android SDK via `ANDROID_HOME` or `sdk.dir` in `local.properties`.
- Versions are pinned ONLY in `gradle/libs.versions.toml`. Never add an ad-hoc
  version to a build file; never restate catalog versions in docs.
- `EditorViewModel` takes a `DocumentOperations` and an `ExecutorService`.
  Model tests inject a fake and a queue-backed executor (`EditorDoubles.kt`)
  to decide exactly when worker tasks and main-thread posts run;
  `PdfRenderer` itself only runs on devices. Service tests feed a content
  URI with `ShadowContentResolver.registerInputStream`; its display-name
  query returns no cursor, so the import gets the default name.
- Production `DocumentSession`s share one process-owned FIFO worker. Clearing
  an editor suppresses callbacks and skips queued previews, but drains writes
  and closes its service before the next editor restores. Never shut down that
  worker from an editor or use independent production queues: an old snapshot
  can overwrite new ink and prune its source. Injected workers are session-owned
  and terminate after queued writes and close. `EditorWorkerLifetimeTest` drives
  two production sessions with controlled rendering and real draft storage.

## Toolchain quirks — don't "fix" these

- `compileSdkVersion("android-37.0")` (string form) is deliberately paired
  with `android.suppressUnsupportedCompileSdk=37` in `gradle.properties`.
  The two move together or not at all.
- There is NO `kotlin-android` plugin: AGP 9 provides built-in Kotlin
  support. Only `android-application` is applied.
- Keep `.bat` files normalized (LF) in Git. `.gitattributes` restores CRLF
  on checkout; CRLF blobs produce line-ending-only dirty diffs.
- `app/debug.keystore` is checked in ON PURPOSE and signs BOTH build types.
  Zero-secret CI, reproducible builds, upgrade-compatible sideload APKs —
  see `docs/decisions/0001`. Do not "rotate" it; adding signing secrets is a
  product decision requiring a new ADR.
- `scripts/verify_pdf.py` is a separate hard CI gate, not an optional extra:
  Robolectric cannot run Android's PdfRenderer, so the JVM tests write export
  fixtures to `app/build/test-output/raster-proof/` and the Python script
  re-renders them with PDFium — independent of PDFBox — checking ink
  placement and preserved text/artwork at all four rotations, that an
  owner-restricted encrypted export stays encrypted without regaining print
  permission, that AESV2/AESV3 sources without top-level `/Length` retain AES
  strength and all permission bits, that text stays dark under a
  multiply-blended highlighter marker, and that a highlighter tap still leaves
  a mark. It only works after `testDebugUnitTest` ran in the same checkout.
- PDFBox's `PDEncryption.length` defaults to 40 when `/Length` is absent;
  its loaded `securityHandler.keyLength` also stays at 40. Export strength
  comes from security version / `StdCF` method, not those getters. Standard
  crypt filter `/Length` uses bytes per ISO 32000, but PDFBox writes bits;
  do not use it as the export key length. Missing-Length AES test fixtures
  use equal-size whitespace edits so ciphertext and xref offsets stay intact.
  V=4/R=4 RC4 (`CFM=V2`, `StdCF/Length=16`) without top-level `/Length`
  opens in PDFium but fails PDFBox password validation before export.
- The debug build carries `applicationIdSuffix ".debug"` (and `-debug` on
  versionName) so it can sit next to a release install on the same device.
- `allowBackup=false` disables cloud backup; device transfer varies by OEM.
  `data_extraction_rules.xml` allows only `files/documents` and `pen.xml`
  for devices that transfer anyway. Do not promise universal migration.
- `InkPageView` replays committed ink from cached `RenderNode`s only on
  hardware canvases. Robolectric draws in software, so JVM tests exercise the
  direct-draw fallback, and pixel assertions need `@GraphicsMode(NATIVE)`
  (the default LEGACY mode rasterizes nothing). The RenderNode path needs a
  device check.
- `InkGeometry.segments` is the export contract: `InkIncrementalTest` pins it
  to the v0.1.0 algorithm, and the live `InkStrokeBuilder` must produce the
  same segments for every prefix. Changing stroke geometry changes every
  exported PDF, so do it deliberately and update that reference.
- Eraser contact uses continuous swept capsules against those rendered
  segments, with local pressure widths for pens and a constant
  `InkGeometry.strokeWidth`-sanitized width for highlighters. Raw samples,
  nominal pen widths and sampled probes are not equivalent. Cache and hit
  results retain stroke identity; `InkEraser.reset()` releases the gesture cache.
- `InkPageView` supplies `InkGeometryCache.cachedSegments` to reuse immutable
  renderer geometry on first erase contact. The lookup never computes or
  readmits absent strokes; `update()` drops removed/off-page entries. Eraser
  reset releases gesture metadata and segment references, leaving renderer
  entries intact. Highlighter widths override bounds/contact calculations
  without copying or mutating shared segments; standalone erasers still smooth.
- `AtomicFile.finishWrite` is not a checked commit: Android 29 ignores sync
  errors and logs close errors; Android 35 also logs rename errors. Draft
  writes use a separate `.new`, throwing `FileDescriptor.sync`, checked
  close and same-directory rename before pruning PDFs. Keep AtomicFile's
  `.bak` recovery and legacy JSON compatibility. `DocumentStoreCommitTest`
  injects write, sync, close and rename failures on APIs 29 and 35.

## UI conventions

- Screens are built in code, not XML layouts. Repeated controls come from
  `ui/Components.kt` (icon button, toggle, segment, primary button, choice
  dot) so they look and behave alike; add new ones there.
- Design tokens: colours and type scale live in `res/values/colors.xml`,
  `values-night/colors.xml` and `styles.xml` (they change with the theme);
  spacing, sizes, radii and motion in `ui/DesignTokens.kt`. Theme attributes
  that need a resource (the dialog corner radius) use `values/dimens.xml`.
  Never hard-code a chrome colour: it breaks the dark theme. Ink and
  highlighter colours are document content and stay constants in
  `MainActivity`.
- Every tappable control is at least 48dp square; `MainActivityLayoutTest`
  enforces it, with no overlaps, on phone, landscape, tablet and 200% text.
- The tool bar wraps from its measured, inset-adjusted width. Narrow phones
  need three rows to keep 48dp targets; `screenWidthDp` is not the usable width.
- `InkPageView` updates its shared page rectangle and scale synchronously after
  page, size, pan and zoom changes, including animation frames. Drawing and
  input only consume that transform; no draw may be required before ink or erase.
  An unavailable transform cancels active ink/erase. `ACTION_CANCEL` cleanup
  precedes input eligibility checks.
- `UiScreenshotTest` renders every screen state (light, dark, phone, tablet,
  large text, dialogs, notices) to `app/build/reports/screens/`. Look at the
  PNGs after any UI change; Robolectric cannot show a device, these can.
- Robolectric measures text only under `@GraphicsMode(NATIVE)`; in LEGACY
  mode every character is one pixel wide, so width assertions lie. A view
  must be attached to a window before its `postDelayed` timers and
  `animate()` calls run.
- Tests cannot open a real PDF (no PdfRenderer); `EditorScreens.publish`
  puts the activity into an editor state with a drawn stand-in page.
- UI gesture tests using that stand-in must cancel their strokes, or replace
  the stroke callback, to avoid asynchronously persisting a nonexistent PDF.
- Messages to the user go through `NoticeBar`, not toasts: Android 12+ cuts
  toasts to two lines. A notice can carry one action (`NoticeAction`), which
  keeps it up for ten seconds instead of the tone's usual timeout.

## CI/CD

Three workflows: `ci.yml` (tests + lint + debug and release APKs + the PDFium
proof on every PR and main push), `release.yml` (`v*` tags → verified,
signed, published APK), `zai-code-review.yml` (GLM reviews every PR; respond
per CLAUDE.md). Family contract on every workflow: least-privilege
permissions, explicit concurrency, timeouts, wrapper validation.

CI uses Gradle's build cache, so `testDebugUnitTest` can come back
`FROM-CACHE` without running. The PDFium fixtures are declared outputs of
that task in `app/build.gradle.kts`, which makes a cache hit restore them;
keep that declaration in step with the fixture path.

## Releasing

`scripts/release.sh X.Y.Z --push` (shared lkm-release engine) bumps
versionName, auto-increments versionCode by exactly 1, rewrites the README
version marker, commits, tags `vX.Y.Z`, pushes. **Never hand-edit
versionCode. Never create a `v*` tag by hand.**

<!-- shared-rules:start -->

## Working practices

- Follow explicit task instructions over the default workflow below.
- Writing the code is not finishing the task. A task is finished when
  its changes are merged to main through a PR that passed CI and review,
  or when the user explicitly accepts a different end state.
- Start every task on current code. Fetch first, then cut the task
  branch from origin/main — never from a stale local branch or an old
  checkout. To continue existing work, rebase or merge the latest
  origin/main into it before editing. Never overwrite existing work to
  update.
- Resolve ambiguity before making consequential changes. State low-risk
  assumptions; ask when scope, safety, or expected behavior is unclear.
- Keep changes focused. Do not modify unrelated code, formatting, or comments.
- Prefer surgical edits over whole-file rewrites when the result is equivalent.
- Stage only intended files. Inspect the diff before committing.

## Communication

- Be concise, factual, and direct. Preserve necessary context and uncertainty.
- Avoid praise, motivational filler, emojis, and em dashes in new prose.
- Address the reader directly in user-facing copy.
- Report what was verified and what remains unverified. Never imply that an
  unavailable check passed.

## Code design

- Prefer early returns and shallow nesting. Separate logical blocks with
  blank lines.
- Use descriptive constants or enums for meaningful or repeated values.
  Use existing standard definitions for protocol/specification constants.
  Keep obvious, one-off values inline.
- Use enums for behavioral modes that would otherwise require ambiguous
  boolean arguments.
- Default members to private. Widen visibility only for required consumers,
  and review the change as an API design decision.
- Follow the repository's declared dependency boundaries. UI and controllers
  must use application services rather than directly accessing databases,
  subprocesses, sockets, or other low-level mechanisms.
- Encapsulate low-level mechanics behind domain-oriented interfaces.
- Reuse genuinely shared logic. Avoid speculative abstractions and layers
  that only forward calls.
- Prefer pure functions for business rules and immutable data where practical.
  Isolate side effects; document non-obvious state ownership or synchronization.
- Explain non-obvious intent, constraints, and tradeoffs in comments.
  Do not narrate obvious code. Add examples or diagrams when they clarify it.

## Validation and errors

- Validate untrusted input at entry points. Where practical, represent valid
  states in types and enforce persistent invariants in database schemas.
- Represent absence and failure explicitly.
- Use assertions for internal programming invariants, not external-input
  validation or required runtime error handling.
- Prefer explicit, actionable errors over silent failure or undocumented
  fallback. Document intentional recovery behavior.
- Never report a skipped or failed operation as successful.

## Bug fixes

1. Identify the root cause and define an observable success criterion.
2. Add a regression test and observe the relevant failure before fixing it.
3. Implement the fix and observe the test passing.
4. Check surrounding behavior for regressions and architectural consistency.

If an automated regression test is impractical, document the reproduction
and verification procedure. State any inability to reproduce the failure.

## Verification

- Run relevant tests and lint after changes.
- Choose coverage by affected behavior and risk, not patch size.
- Use integration or end-to-end tests for critical workflows and boundaries;
  test isolated business rules at the lowest effective level.
- Run broader suites for cross-cutting or high-risk changes, and the full
  required release checks before releasing.
- Validate the requested command, options, platform, and configuration.
  Unrelated green CI is not proof that the reported problem is fixed.
- Recheck after the final edit. Distinguish local checks from CI results.
- Gesture tests (`InkPageViewGestureTest`) assert where a stylus tap lands in
  page units rather than reading private zoom state. `ScaleGestureDetector`
  ignores spans under ~27mm (about 170px at the default mdpi test density),
  so synthetic pinches need wider spans; double taps need real event times.

## Commit messages

- Use a capitalized, imperative subject without a final period.
- Target 50 characters; never exceed 72.
- Separate the subject and body with one blank line.
- Wrap body text at 72 characters.
- Explain what changed and why. Leave implementation mechanics to the code.

## Implementation and review

Unless explicitly instructed otherwise:

1. Work on a focused branch cut from the latest origin/main and open a PR
   against main before reporting the task as done.
2. Inspect CI results and completed review feedback for the latest commit.
   A successful reviewer job does not mean the review found no problems.
3. Address important findings or explain why they do not apply. Handle minor
   findings according to the stopping rules below.
4. Evaluate each fix in the surrounding project, add regression coverage,
   and rerun affected checks before pushing.
5. Repeat until a stopping criterion is met.
6. Merge without asking again once the stopping criterion is met, required
   checks pass on the latest commit, and no unresolved blockers or required
   human review requests remain.

### Reviewer context limits

The automated PR reviewer does not see the user's original prompt or
conversation. It may suggest changes that go against or beyond what the
user asked for. Do not implement such suggestions. Note each conflict and
report it to the user at the end of the thread.

### Automated review stopping rules

Judge findings by verified impact, not the reviewer's severity label.
Important findings concern correctness, security, data loss, broken builds,
or materially degraded behavior/performance.

Track completed review rounds and consecutive rounds without important
findings. Reruns of the same revision and integration failures do not count.

- No applicable actionable feedback: finish immediately.
- First minor-only round: optionally fix worthwhile, low-risk findings.
  Do not manufacture another push merely to obtain another review.
- Two consecutive rounds without important findings: stop responding to
  automated nitpicks, even if actionable minor suggestions remain.
  Defer worthwhile leftovers rather than continuing the cycle.
- A confirmed important finding resets the minor-only streak. Address it
  and verify the fix before continuing.

After ten completed rounds, enter stabilization:

- Stop optional cleanup, refactoring, and nitpick fixes.
- One completed review without confirmed important findings is sufficient
  to finish, even if minor suggestions remain.
- Continue only for confirmed important defects. If resolving them stalls,
  report the blockers rather than continuing indefinitely.

These limits end optional automated-feedback work. They do not waive
confirmed blockers, unresolved human review requests, or required checks.

### Reviewer integration failures

After two consecutive reviewer-integration failures, stop and report the
review gap. Do not treat failures as approval. An explicit user instruction
may waive review; report that waiver rather than claiming review passed.

## Ending a task

- A task ends with its changes merged to main — not with code written,
  and not with a PR merely opened. An open PR is work in progress:
  monitor CI on the latest commit, address review findings per the
  stopping rules, and merge once the criteria are met.
- Never finish with uncommitted changes or unpushed commits in the
  worktree. Commit, push, and open or update the PR first.
- If a step is impossible (missing push access, CI failure, reviewer
  outage), report the exact blocker instead. Never present unreviewed or
  unmerged work as finished.
- Before finishing, confirm: the requested behavior is implemented
  without unrelated changes; relevant checks pass on the latest code;
  important review findings are addressed or rejected with reasons;
  deferred suggestions, remaining risks, and validation gaps are
  disclosed.
- The final response states where the work stands: branch, PR, CI
  status, review rounds completed, and whether it is merged.

<!-- shared-rules:end -->
