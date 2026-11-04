# Woku Sudoku

For coding agents and contributors: start at [AGENTS.md](AGENTS.md) and the [agent documentation](docs/agent/README.md).

A minimal, offline Android Sudoku game built with Kotlin and Jetpack Compose. Dark mode is the default. No account, ads, permissions, or network access are required.

## Play

- Select a number first, then tap an empty square to place it (or toggle a note when Pencil is active). Tapping a number switches the active number for highlighting and clears any selected cell so it does not modify an already-selected square. Long-press the active number to deselect it and turn off number highlighting. When all 9 instances of a number are correctly placed, that number button is darkened/greyed out and the next incomplete number is automatically selected (toggleable in **Settings**).
- Selecting a number highlights all filled cells containing it, and highlights matching penciled notes inside their individual 1/9th sub-cells in an alternative color. Tapping a square with an already-placed number selects that number and highlights its matches and notes. Selecting a square also shades its row, column, and 3×3 box.
- **View only** mode lets you tap cells and numbers to inspect highlights and peers safely without placing numbers or modifying notes.
- Placing a number automatically clears matching penciled notes in the same row, column, and 3×3 box.
- **Pencil** toggles manual notes. In Pencil mode with a number selected, you can tap or drag your finger across cells to immediately flip that number's note along your path (grouped as a single Undo step). **Fast pencil** fills every empty square with currently valid candidates, excluding digits present in its row, column **or box**. It overwrites existing notes; one Undo restores all of them.
- When 9 or fewer cells remain to be solved (`>= 72/81`), a **Fast complete** button appears to finish the board in one step.
- **Erase** clears the selected editable square. Undo/Redo restore both values and complete note sets. Changing a move after Undo clears the Redo branch.
- Progress (`X/81`) is shown next to the timer at the top left. Wrong answers are shown in red and cost one of three lives when the three-life limit is enabled. Undo does not refund a life. A puzzle ends when all lives are lost (if lives are enabled) or every square matches the known solution.
- **Settings** (gear icon at the top right) lets you toggle the three-life limit (`♥` vs `∞`), automatic next-number selection, subtle haptic feedback, and sound volume (for subtle placement/note/completion chimes and the victory fanfare), or open the **Appearance & Themes** submenu to switch between built-in themes, save your own custom themes, or customize every appearance color (including completed digit button/text colors) via swatches, an interactive color wheel, or an exact `#RRGGBB` hex code.
- **New** offers Easy, Medium, Hard, Expert, Extreme, Diabolical, and Diabolical+. Every puzzle has exactly one solution. Easy/Medium/Hard are generated on the device by clue count (42/36/30). Expert and harder are drawn from a bundled, technique-rated bank (Sukaku Explainer rating: Expert 4.0–5.9 for wings and fish, Extreme 6.0–6.9, Diabolical 7.0–8.4 for chains, Diabolical+ 8.5+ for forcing chains). Each bank puzzle is randomly relabeled, reordered, and/or transposed, so boards rarely repeat.
- **Hide mistakes until checked** (Settings, off by default): wrong digits are not shown in red and cost no heart while you play. Progress, number counts, and sounds count placements rather than correct cells. Tap **Check for mistakes** to reveal every wrong digit; each newly revealed one costs a heart. With this setting on, Fast complete fills only the empty cells.
- The timer, current puzzle, notes, lives, move history, and settings are saved locally. **Records** shows the best completion time for each difficulty. (Updating to the rated bank resets the old Expert, Extreme, and Diabolical records once, because those levels changed.) The timer advances while the app is active.

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

## Puzzle credits

Expert through Diabolical+ puzzles come from the [Sudoku Exchange Puzzle Bank](https://github.com/grantm/sudoku-exchange-puzzle-bank) by Grant McLean, which is dedicated to the public domain (Unlicense). The puzzles were generated with QQWing and rated with Sukaku Explainer. `scripts/build_puzzle_bank.py` rebuilds the bundled files reproducibly.
