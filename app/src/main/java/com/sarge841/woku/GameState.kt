package com.sarge841.woku

enum class GameStatus { PLAYING, WON, LOST }

data class BoardSnapshot(val values: List<Int>, val notes: List<Set<Int>>)

data class GameState(
    val puzzle: Puzzle,
    val difficulty: Difficulty,
    val values: List<Int>,
    val notes: List<Set<Int>>,
    val lives: Int = 3,
    val elapsedSeconds: Int = 0,
    val history: List<BoardSnapshot> = emptyList(),
    val future: List<BoardSnapshot> = emptyList()
) {
    val status: GameStatus get() = when {
        lives == 0 -> GameStatus.LOST
        values == puzzle.solution -> GameStatus.WON
        else -> GameStatus.PLAYING
    }

    private fun changed(newValues: List<Int>, newNotes: List<Set<Int>>, newLives: Int = lives): GameState =
        copy(values = newValues, notes = newNotes, lives = newLives,
            history = (history + BoardSnapshot(values, notes)).takeLast(200), future = emptyList())

    fun place(index: Int, digit: Int): GameState {
        if (status != GameStatus.PLAYING || index !in 0..80 || digit !in 0..9 || puzzle.givens[index] != 0 || values[index] == digit) return this
        val newValues = values.toMutableList().also { it[index] = digit }
        val newNotes = notes.toMutableList().also { it[index] = emptySet() }
        val life = if (digit != 0 && digit != puzzle.solution[index]) lives - 1 else lives
        return changed(newValues, newNotes, life)
    }

    fun toggleNote(index: Int, digit: Int): GameState {
        if (status != GameStatus.PLAYING || index !in 0..80 || digit !in 1..9 || values[index] != 0) return this
        val newNotes = notes.toMutableList()
        newNotes[index] = if (digit in notes[index]) notes[index] - digit else notes[index] + digit
        return changed(values, newNotes)
    }

    fun fastPencil(): GameState {
        if (status != GameStatus.PLAYING) return this
        val updated = values.indices.map { Sudoku.candidates(values, it) }
        return if (updated == notes) this else changed(values, updated)
    }

    fun undo(): GameState {
        if (status != GameStatus.PLAYING || history.isEmpty()) return this
        val prior = history.last()
        return copy(values = prior.values, notes = prior.notes, history = history.dropLast(1),
            future = future + BoardSnapshot(values, notes))
    }

    fun redo(): GameState {
        if (status != GameStatus.PLAYING || future.isEmpty()) return this
        val next = future.last()
        return copy(values = next.values, notes = next.notes, future = future.dropLast(1),
            history = history + BoardSnapshot(values, notes))
    }

    fun tick(): GameState = if (status == GameStatus.PLAYING) copy(elapsedSeconds = elapsedSeconds + 1) else this

    companion object {
        fun new(puzzle: Puzzle, difficulty: Difficulty): GameState =
            GameState(puzzle, difficulty, puzzle.givens, List(81) { emptySet() })
    }
}
