# Changelog

All notable changes to Asterinked are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- The APK carries the licence texts and NOTICE files of every bundled
  component (AndroidX, Kotlin, PDFBox-Android, Bouncy Castle, its fonts and
  data files, the Material Symbols icons), shown under Open-source licences
  on the start screen. CI and the release workflow check them in each APK.

### Changed
- Release assets are named `asterinked-vX.Y.Z-android-universal.apk` (plus
  `.sha256`), following the family naming rule `<base>-<vX.Y.Z>-<os>-<arch>`;
  `scripts/build.sh` stages the same name (debug builds:
  `asterinked-debug-vX.Y.Z-android-universal.apk`).

## [0.1.0] - 2026-09-26

### Added
- Pen-based PDF annotation for Android 10+: stylus writing with
  pressure-sensitive stroke width, finger pan/pinch navigation, touch-ink
  mode, undo/redo, page navigation, and "save copy" export.
- Vector ink export via PDFBox that preserves original text and artwork;
  repeated exports do not accumulate duplicate ink.
- Private on-device draft storage restoring the open document and completed
  strokes after reopening.
- JVM test suite plus `scripts/verify_pdf.py`, a PDFium render check covering
  ink placement and text/artwork preservation at all four page rotations.
- Repo scaffolding: CI/release/review workflows, build/install/release
  scripts, and the agent operating manual.
