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
    private fun colors(map: Map<Int, Int>) = map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
    private fun parseColors(s: String): Map<Int, Int> = if (s.isEmpty()) emptyMap() else s.split(",").associate { entry ->
        val (cell, color) = entry.split(":").map { it.toInt() }
        require(cell in 0..80 && color in 1..9)
        cell to color
    }
    private fun parseNoteColors(s: String): Map<Int, Int> = if (s.isEmpty()) emptyMap() else s.split(",").associate { entry ->
        val (key, color) = entry.split(":").map { it.toInt() }
        require(noteColorCell(key) in 0..80 && noteColorDigit(key) in 1..9 && color in 1..9)
        key to color
    }
    private fun lines(lines: Set<BoardLine>) = lines.sortedWith(
        compareBy({ it.fromCell }, { it.fromDigit ?: 0 }, { it.toCell }, { it.toDigit ?: 0 })
    ).joinToString(",") {
        val fromStr = if (it.fromDigit != null) "${it.fromCell}.${it.fromDigit}" else "${it.fromCell}"
        val toStr = if (it.toDigit != null) "${it.toCell}.${it.toDigit}" else "${it.toCell}"
        val styleStr = if (it.style == LineStyle.DOTTED) "D" else "S"
        "$fromStr-$toStr:$styleStr"
    }
    private fun parseLines(s: String): Set<BoardLine> = if (s.isEmpty()) emptySet() else s.split(",").map { entry ->
        val parts = entry.split(":")
        val endPoints = parts[0].split("-")
        val fromParts = endPoints[0].split(".")
        val toParts = endPoints[1].split(".")
        val fromCell = fromParts[0].toInt()
        val fromDigit = fromParts.getOrNull(1)?.toInt()
        val toCell = toParts[0].toInt()
        val toDigit = toParts.getOrNull(1)?.toInt()
        val style = if (parts.getOrNull(1) == "D") LineStyle.DOTTED else LineStyle.SOLID
        BoardLine.create(fromCell, fromDigit, toCell, toDigit, style)
    }.toSet()
    private fun snapshot(s: BoardSnapshot) =
        digits(s.values) + ":" + notes(s.notes) + ";" + colors(s.cellColors) + ";" + lines(s.lines) + ";" + colors(s.noteColors)
    private fun parseSnapshot(s: String): BoardSnapshot {
        val scParts = s.split(";")
        val parts = scParts[0].split(":", limit = 2)
        require(parts.size == 2)
        val board = board(parts[0])
        val notes = parseNotes(parts[1])
        val cellColors = if (scParts.size > 1) parseColors(scParts[1]) else emptyMap()
        val lines = if (scParts.size > 2) parseLines(scParts[2]) else emptySet()
        val noteColors = if (scParts.size > 3) parseNoteColors(scParts[3]) else emptyMap()
        return BoardSnapshot(board, notes, cellColors, noteColors, lines)
    }
    private fun stack(items: List<BoardSnapshot>) = items.joinToString("~") { snapshot(it) }
    private fun parseStack(s: String) = if (s.isEmpty()) emptyList() else s.split("~").map(::parseSnapshot)
    private fun revealed(map: Map<Int, Int>) = map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
    private fun parseRevealed(s: String): Map<Int, Int> = if (s.isEmpty()) emptyMap() else s.split(",").associate { entry ->
        val (cell, digit) = entry.split(":").map { it.toInt() }
        require(cell in 0..80 && digit in 1..9)
        cell to digit
    }

    /** Version 4 appends note colors and candidate line endpoints; versions 1, 2, and 3 saves still decode. */
    fun encode(game: GameState): String = listOf(
        "4", game.difficulty.name, game.lives.toString(), game.elapsedSeconds.toString(),
        digits(game.puzzle.givens), digits(game.puzzle.solution),
        snapshot(BoardSnapshot(game.values, game.notes, game.cellColors, game.noteColors, game.lines)),
        stack(game.history), stack(game.future),
        revealed(game.revealedMistakes),
        colors(game.cellColors),
        lines(game.lines),
        colors(game.noteColors)
    ).joinToString("|")

    fun decode(save: String): GameState? = runCatching {
        val p = save.split("|", limit = 14)
        require(
            (p.size == 9 && p[0] == "1") ||
            (p.size == 10 && p[0] == "2") ||
            (p.size == 12 && p[0] == "3") ||
            (p.size == 13 && p[0] == "4")
        )
        val difficulty = Difficulty.valueOf(p[1])
        val lives = p[2].toInt().also { require(it in 0..3) }
        val seconds = p[3].toInt().also { require(it >= 0) }
        val givens = board(p[4]); val solution = board(p[5])
        require(solution.all { it in 1..9 } && Sudoku.countSolutions(solution) == 1)
        require(givens.indices.all { givens[it] == 0 || givens[it] == solution[it] })
        val current = parseSnapshot(p[6])
        require(givens.indices.all { givens[it] == 0 || current.values[it] == givens[it] })
        val cellColors = if (p.size >= 11 && (p[0] == "3" || p[0] == "4")) parseColors(p[10]) else if (p[0] == "3" || p[0] == "4") current.cellColors else emptyMap()
        val lines = if (p.size >= 12 && (p[0] == "3" || p[0] == "4")) parseLines(p[11]) else if (p[0] == "3" || p[0] == "4") current.lines else emptySet()
        val noteColors = if (p.size >= 13 && p[0] == "4") parseNoteColors(p[12]) else if (p[0] == "4") current.noteColors else emptyMap()
        GameState(
            Puzzle(givens, solution), difficulty, current.values, current.notes,
            cellColors = cellColors,
            noteColors = noteColors,
            lines = lines,
            lives = lives,
            elapsedSeconds = seconds,
            history = parseStack(p[7]),
            future = parseStack(p[8]),
            revealedMistakes = if (p.size >= 10) parseRevealed(p[9]) else emptyMap()
        )
    }.getOrNull()
}
