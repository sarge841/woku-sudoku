# Architecture and behavior contracts

## Board and puzzle

- A board is a `List<Int>` of **81 cells**; index `row * 9 + column`, zero-based. `0` is empty; `1..9` are digits. `Puzzle.givens` contains fixed clues; `Puzzle.solution` contains the full answer. Do not mutate a given.
- `Sudoku.generate(difficulty, seed)` creates a completed valid board, removes clues, and retains each removal only while `countSolutions(board) == 1`. Clue targets are Easy 42, Medium 36, Hard 30, Expert 26, Extreme 23. These levels are clue-count based, **not** rated by solving technique. Preserve uniqueness when changing generation.
- `Sudoku.candidates(values, index)` excludes digits in the same row, column, **and 3×3 box**; a filled cell has no candidates. `fastPencil()` replaces notes on all empty cells with these candidates, and records the entire grid as **one** undo step. It can overwrite manual notes; Undo must restore them.

## GameState invariants

- `GameState` is immutable. Board changes record a `BoardSnapshot(values, notes)` in `history`, clear `future`, and cap history at 200 snapshots. Undo/redo restore both the value and **whole note sets**; an edit after Undo invalidates Redo.
- A wrong nonzero placement (compared to `puzzle.solution[index]`) deducts one life and is shown in red. Clearing with `0` does not. A life spent is **not refunded by Undo**. Three mistakes result in `LOST`; an exact match to the solution results in `WON`. Moves are blocked after either terminal state.
- Notes can be toggled only in empty cells while playing. Placing a value clears that cell's notes; Undo must bring those notes back. `tick()` advances elapsed seconds only while playing.

## UI and storage

- `MainActivity` holds transient selected cell/digit and pencil-mode state in Compose; tapping a selected digit again deselects it and removes number highlighting. A selected number highlights all filled cells containing it. Selecting a cell shades its row, column, and box. Number entry works by choosing either the cell or digit first.
- SharedPreferences name `woku`: `game` holds `SaveCodec.encode(game)`; `best_<DIFFICULTY_NAME>` holds integer completion seconds, updated only for a newly won game if faster. The timer advances during active composition; it does not account for time away from the app.
- `SaveCodec` currently uses format version `1`. It stores puzzle, difficulty, lives, elapsed time, current board, and undo/redo stacks. Preserve old-save compatibility or explicitly implement migration when modifying the format. `decode` returns `null` on invalid saves, leading to a new Easy puzzle; do not casually drop user progress.
- Dark colors and the single-screen Compose layout are in `MainActivity.kt`. There is no network permission, backend, account, or external database. Keep these properties unless the task asks otherwise.

## Tests to extend

`SudokuTest.kt` exercises clue count and uniqueness; `GameTest.kt` exercises life cost, note restoration, fast pencil, win/loss; `SaveCodecTest.kt` checks history/time/lives round trips and corrupt saves. Add focused regression tests for new rules and save changes; avoid using only a successful APK build as evidence that the behavior works.
