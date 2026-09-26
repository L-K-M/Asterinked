# 0001 — Both build types are signed with a checked-in debug keystore

Asterinked is distributed sideload-only: built locally or by CI, installed by
hand. Both `debug` and `release` build types are signed with
`app/debug.keystore` — a committed, zero-secret debug certificate (the standard
`androiddebugkey` / `android` credential), the same one the sibling app
Wortkatze (L-K-M/Lern-Deutsch--Android, decisions/0002) uses.

## Why

- **Upgrade compatibility across machines.** Android refuses to update an app
  when the signature changes. An auto-generated per-machine debug key would
  force an uninstall — losing drafts — on every switch between a locally built
  APK, a CI artifact and a release APK. A committed key makes every build
  compatible with every other.
- **Zero-secret CI.** Nothing in the release pipeline needs a protected
  secret, so any checkout can build and verify the same artifact.

## Consequences

- Anyone can produce a validly "signed" Asterinked APK — the signature proves
  continuity, not authorship. Acceptable because the app is sideload-only and
  requests no signature-level permissions.
- Moving to a real signing key later (e.g. for Play distribution) would break
  upgrades for every installed user; that switch is a product decision
  requiring a new ADR.
- Do not "rotate" the keystore or gitignore it. It is deliberately committed.
