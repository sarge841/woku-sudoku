# Development, CI, and release workflow

## Local build and verification

Use JDK 17, the checked-in Gradle wrapper (8.10.2), Android SDK Platform 35 and Build-Tools 35. Point `ANDROID_HOME` at the SDK or use an ignored `local.properties` with `sdk.dir=...`. From the repository root:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; install with `adb install -r ...` after `adb devices` shows a device. Test on a real device/emulator when changing the screen or input; unit tests cover rules but not touch behavior or layout. Report whether device testing actually happened.

On an ARM64 Linux host, Gradle/Java can run natively while Android's AAPT2 binary requires x86-64 emulation and amd64 runtime libraries; see local host tooling before assuming `assembleDebug` is broken by source code. Do not put machine-specific SDK or emulation paths in the repo.

## Code change checklist

1. Read the affected implementation and nearest JUnit tests; confirm current behavior before editing.
2. For a rule change or bug fix, write a focused failing test first, then implement and run the test suite. Keep Android-independent code in the puzzle/game/codec classes where practical.
3. Run unit tests, lint, and debug assembly; inspect failures instead of bypassing them. For screen changes, check a device if available.
4. Update README and these agent docs if external behavior or contributor steps changed; check `git diff --check` and exclude generated files/secrets from the commit.
5. Push only when requested/appropriate; read back remote CI status before claiming it passed. Do not report a published release from a local build or a queued workflow.

## GitHub Actions

`.github/workflows/android.yml` runs on `main` pushes, PRs into `main`, manual dispatch, and version tags. The build job installs JDK/SDK, runs tests + lint + debug assembly, and uploads the debug APK as a run artifact. On `v*` tags it restores a signing key from Actions secrets, checks the tag against `versionName`, builds a signed release APK, and uploads it as a run artifact. The dependent release job publishes **that** APK and `SHA256SUMS.txt` to GitHub Releases. It will not publish if the build job fails. A rerun replaces release assets. The Android setup action needs an explicit SDK `packages` list; its former default `tools` package is obsolete.

## Releasing safely

- The current 1.0.0 release has `versionCode = 2` and `versionName = "1.0.0"` in `app/build.gradle.kts`. For a **future** release, increment `versionCode` and set `versionName`, merge to `main`, wait for green CI, then push an annotated matching tag such as `v1.1.0`. Do not move or reuse an existing published tag.
- Release signing is configured from `WOKU_KEYSTORE_PATH`, `WOKU_KEYSTORE_PASSWORD`, `WOKU_KEY_ALIAS`, and `WOKU_KEY_PASSWORD` environment variables. CI reconstructs the keystore from repository secrets `WOKU_KEYSTORE_BASE64` plus the same password/alias secrets. Never print, expose, or commit these values or the keystore. The backup resides outside the repo at `/home/ubuntu/.local/share/woku-signing/` on this server; protect it and arrange an offline backup with the owner. Do not regenerate the key for updates: Android will reject APKs signed with a different certificate.
- After tag CI completes, verify the GitHub Release exists, has both the signed APK and checksum asset, `sha256sum -c SHA256SUMS.txt` passes on downloaded assets, and `apksigner verify` reports the intended certificate. Confirm packaged `versionName` and `versionCode` with `aapt dump badging`.
- Debug builds use a different signing certificate. Installing a release over a debug build requires uninstalling the debug build, which clears local saved progress. Release-to-release updates use the stable signing key.

User-facing build/install and release steps live in [`../../README.md`](../../README.md).
