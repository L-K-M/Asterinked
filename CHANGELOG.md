# Changelog

All notable changes to Asterinked are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
