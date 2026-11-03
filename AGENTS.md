# Agent instructions — Woku Sudoku

Read [docs/agent/README.md](docs/agent/README.md) before changing this repository. Its [architecture](docs/agent/architecture.md) and [workflow](docs/agent/workflow.md) pages contain the current code map, behavior contracts, verification steps, and release safety rules.

- Keep the app native Kotlin + Jetpack Compose, offline, and dark by default. Preserve the user's three-life, notes, highlight, undo/redo, timer, and per-difficulty best-time behavior unless the task explicitly changes it.
- For behavior changes, add or update focused unit tests in `app/src/test/java/com/sarge841/woku/` and run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` before claiming completion. A successful APK build does not prove device UX; say when no device test was performed.
- Do not commit keystores, passwords, `local.properties`, generated APKs, or build caches. Do not publish a tag/release as part of routine code changes; follow the version and signing checks in the workflow guide.
- Update the relevant documentation when you change game rules, persistence, build requirements, or CI/release behavior. Keep this file as the short entry point, not a second copy of the README.
