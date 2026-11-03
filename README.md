# Woku Sudoku

For coding agents and contributors: start at [AGENTS.md](AGENTS.md) and the [agent documentation](docs/agent/README.md).

A minimal, offline Android Sudoku game built with Kotlin and Jetpack Compose. Dark mode is the default. No account, ads, permissions, or network access are required.

## Play

- Tap an empty square, then a number to enter it. Alternatively, select a number first and tap squares to place it. Tap the selected number again to turn off number selection and highlighting.
- Selecting a number highlights all its filled cells. Selecting a square also shades its row, column, and 3×3 box.
- **Pencil** toggles manual notes. **Fast pencil** fills every empty square with currently valid candidates, excluding digits present in its row, column **or box**. It overwrites existing notes; one Undo restores all of them.
- **Erase** clears the selected editable square. Undo/Redo restore both values and complete note sets. Changing a move after Undo clears the Redo branch.
- Wrong answers are shown in red and cost one of three lives. Undo does not refund a life. A puzzle ends when all lives are lost or every square matches the known solution.
- **New** offers Easy, Medium, Hard, Expert, and Extreme. Each generated puzzle has exactly one solution. Difficulty is based on clue count (42/36/30/26/23); it does not promise a particular solving-technique rating.
- The timer, current puzzle, notes, lives, and move history are saved locally. **Records** shows the best completion time for each difficulty. The timer advances while the app is active.

## Build

Requirements: JDK 17, Android SDK Platform 35, Android SDK Build-Tools 35 (or the version requested by the Android Gradle Plugin), and an internet connection for first-time Gradle dependencies. Set `ANDROID_HOME` to your SDK path, or configure `local.properties` with `sdk.dir=/path/to/sdk`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install

Enable USB debugging on your Android phone, connect it, then run:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK to a device running Android 8.0 (API 26) or later and open it to install (allow installs from that source). For the signed, distributable APK, download `woku-sudoku-vX.Y.Z.apk` from [GitHub Releases](https://github.com/sarge841/woku-sudoku/releases) and check it against `SHA256SUMS.txt`. The debug APK uses a different certificate: uninstall an existing debug build before installing a release build (this clears its local data). Future release builds signed with the same key can upgrade in place.

## CI and releases

`.github/workflows/android.yml` runs tests, Android lint, and a debug APK build on pushes to `main`, PRs, and manual dispatch. The debug APK is downloadable from the workflow run. On a version tag (`vX.Y.Z`) it also builds a signed release APK, verifies that the tag matches `versionName`, and automatically publishes that APK and its SHA-256 checksum as a GitHub Release. The release job only runs after tests, lint, and both builds succeed. A failed tag run does not publish a release. Re-running the workflow replaces its release assets.

For the next release, increment `versionCode` (it must increase for Android upgrades) and set `versionName` in `app/build.gradle.kts`, merge to `main`, then push the matching tag. The repository's Actions secrets `WOKU_KEYSTORE_BASE64`, `WOKU_KEYSTORE_PASSWORD`, `WOKU_KEY_ALIAS`, and `WOKU_KEY_PASSWORD` hold release signing credentials. Keep an **offline backup** of the keystore and password: losing them prevents updates to already-installed release APKs. Do not commit either one. A local signed build uses `WOKU_KEYSTORE_PATH`, `WOKU_KEYSTORE_PASSWORD`, `WOKU_KEY_ALIAS`, and `WOKU_KEY_PASSWORD` as environment variables and runs `./gradlew :app:assembleRelease`.

## Tests

`./gradlew :app:testDebugUnitTest` checks unique puzzle generation, game rules, move history, fast pencil, win/loss, and save restoration. `./gradlew :app:lintDebug` runs Android lint. `./gradlew :app:assembleDebug` compiles and packages the Android app. The project uses Gradle 8.10.2, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, and Compose Material 3.
