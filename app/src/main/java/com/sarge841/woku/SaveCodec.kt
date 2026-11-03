package com.sarge841.woku

/** Compact, versioned, offline save format. No Android dependencies, so saves can be unit tested. */
object SaveCodec {
    private fun digits(values: List<Int>) = values.joinToString("")
    private fun board(values: String): List<Int> {
        require(values.length == 81 && values.all { it in '0'..'9' })
        return values.map { it.digitToInt() }
    }
    private fun notes(values: List<Set<Int>>) = values.joinToString(",") { it.sorted().joinToString("") }
    private fun parseNotes(values: String): List<Set<Int>> {
        val cells = values.split(",", limit = 82)
        require(cells.size == 81 && cells.all { cell -> cell.all { it in '1'..'9' } })
        return cells.map { cell -> cell.map { it.digitToInt() }.toSet() }
    }
    private fun snapshot(s: BoardSnapshot) = digits(s.values) + ":" + notes(s.notes)
    private fun parseSnapshot(s: String): BoardSnapshot {
        val parts = s.split(":", limit = 2)
        require(parts.size == 2)
        return BoardSnapshot(board(parts[0]), parseNotes(parts[1]))
    }
    private fun stack(items: List<BoardSnapshot>) = items.joinToString("~") { snapshot(it) }
    private fun parseStack(s: String) = if (s.isEmpty()) emptyList() else s.split("~").map(::parseSnapshot)

    fun encode(game: GameState): String = listOf(
        "1", game.difficulty.name, game.lives.toString(), game.elapsedSeconds.toString(),
        digits(game.puzzle.givens), digits(game.puzzle.solution),
        snapshot(BoardSnapshot(game.values, game.notes)), stack(game.history), stack(game.future)
    ).joinToString("|")

    fun decode(save: String): GameState? = runCatching {
        val p = save.split("|", limit = 10)
        require(p.size == 9 && p[0] == "1")
        val difficulty = Difficulty.valueOf(p[1])
        val lives = p[2].toInt().also { require(it in 0..3) }
        val seconds = p[3].toInt().also { require(it >= 0) }
        val givens = board(p[4]); val solution = board(p[5])
        require(solution.all { it in 1..9 } && Sudoku.countSolutions(solution) == 1)
        require(givens.indices.all { givens[it] == 0 || givens[it] == solution[it] })
        val current = parseSnapshot(p[6])
        require(givens.indices.all { givens[it] == 0 || current.values[it] == givens[it] })
        GameState(Puzzle(givens, solution), difficulty, current.values, current.notes,
            lives, seconds, parseStack(p[7]), parseStack(p[8]))
    }.getOrNull()
}
