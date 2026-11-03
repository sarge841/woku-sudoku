# Working in Woku Sudoku

Start with [`../../AGENTS.md`](../../AGENTS.md), then use these pages as needed:

- [Architecture and game contracts](architecture.md): source map, board model, edge cases, persistence, and UI state.
- [Development and release workflow](workflow.md): tools, tests, CI, signing, and deployment rules.
- [Project README](../../README.md): user-facing features, installation, and release downloads.

## Scope

This is a **single-module, offline Android application**, not a web app. The source of truth for behavior is the Kotlin code and unit tests, not this document. If documentation and code disagree, inspect the implementation and tests, fix the mismatch, and explain the change. Do not silently change a game rule merely to make a document true.

The default branch is `main`; the package/application ID is `com.sarge841.woku`. Build configuration is in `app/build.gradle.kts`; the GitHub Actions pipeline is `.github/workflows/android.yml`. The most recent release at the time these docs were written is `v1.0.0`; check tags and GitHub before relying on that version for a new release.

## Where to work

| Area | File | Responsibility |
|---|---|---|
| Puzzle engine | `app/src/main/java/com/sarge841/woku/Sudoku.kt` | Generate solved boards, remove clues while checking uniqueness, calculate candidates, count solutions. |
| Game rules | `app/src/main/java/com/sarge841/woku/GameState.kt` | Immutable board values/notes, three lives, status, undo/redo, timer ticks. |
| Save codec | `app/src/main/java/com/sarge841/woku/SaveCodec.kt` | Versioned, Android-independent text representation of game state and history. |
| UI and local storage | `app/src/main/java/com/sarge841/woku/MainActivity.kt` | Compose screen, input/highlighting, dialogs, SharedPreferences save and best times. |
| Tests | `app/src/test/java/com/sarge841/woku/` | JUnit tests for generation, rules, and save round trips. |
| Build and automation | `app/build.gradle.kts`, `.github/workflows/android.yml` | SDK/version/signing and CI/tag-driven releases. |

Make small, focused changes; keep the pure Kotlin engine/rules testable without Android. UI changes should be checked on a device/emulator when available, and lack of that check should be reported honestly.
