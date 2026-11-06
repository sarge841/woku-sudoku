package com.sarge841.woku

enum class GameStatus { PLAYING, WON, LOST }

enum class LineStyle { SOLID, DOTTED }

data class BoardLine(val from: Int, val to: Int, val style: LineStyle = LineStyle.SOLID)

val ANNOTATION_COLORS = listOf(
    0xFFFF5252, // 1: Red / Coral
    0xFFFF9800, // 2: Orange
    0xFFFFEB3B, // 3: Yellow
    0xFF4CAF50, // 4: Green
    0xFF00BCD4, // 5: Cyan
    0xFF2196F3, // 6: Blue
    0xFF9C27B0, // 7: Purple
    0xFFE91E63  // 8: Pink
)

data class BoardSnapshot(
    val values: List<Int>,
    val notes: List<Set<Int>>,
    val cellColors: Map<Int, Int> = emptyMap(),
    val lines: Set<BoardLine> = emptySet()
)

data class GameState(
    val puzzle: Puzzle,
    val difficulty: Difficulty,
    val values: List<Int>,
    val notes: List<Set<Int>>,
    val cellColors: Map<Int, Int> = emptyMap(),
    val lines: Set<BoardLine> = emptySet(),
    val lives: Int = 3,
    val elapsedSeconds: Int = 0,
    val history: List<BoardSnapshot> = emptyList(),
    val future: List<BoardSnapshot> = emptyList(),
    /** Cell → wrong value revealed by [checkMistakes] (used only when mistakes are hidden until checked). */
    val revealedMistakes: Map<Int, Int> = emptyMap()
) {
    val status: GameStatus get() = when {
        lives == 0 -> GameStatus.LOST
        values == puzzle.solution -> GameStatus.WON
        else -> GameStatus.PLAYING
    }

    fun status(livesEnabled: Boolean): GameStatus = when {
        livesEnabled && lives == 0 -> GameStatus.LOST
        values == puzzle.solution -> GameStatus.WON
        else -> GameStatus.PLAYING
    }

    /**
     * How many of [digit] count toward completion. Normally only correct placements count; when
     * [hideMistakes] is on, every placement counts so the UI does not leak correctness.
     */
    fun digitCount(digit: Int, hideMistakes: Boolean = false): Int =
        if (hideMistakes) values.count { it == digit }
        else (0..80).count { values[it] == digit && puzzle.solution[it] == digit }

    fun isDigitComplete(digit: Int, hideMistakes: Boolean = false): Boolean =
        digit in 1..9 && digitCount(digit, hideMistakes) >= 9

    fun nextIncompleteDigit(fromDigit: Int, hideMistakes: Boolean = false): Int? =
        (1..9).map { (fromDigit - 1 + it) % 9 + 1 }.firstOrNull { !isDigitComplete(it, hideMistakes) }

    val correctCount: Int get() = values.indices.count { values[it] != 0 && values[it] == puzzle.solution[it] }

    val remainingCount: Int get() = 81 - correctCount

    val emptyCount: Int get() = values.count { it == 0 }

    /** Progress shown to the player: correct cells, or filled cells when mistakes are hidden. */
    fun progressCount(hideMistakes: Boolean = false): Int = if (hideMistakes) 81 - emptyCount else correctCount

    /** Cells the fast-complete button would fill. */
    fun fastCompleteCount(hideMistakes: Boolean = false): Int = if (hideMistakes) emptyCount else remainingCount

    val canFastComplete: Boolean get() = status == GameStatus.PLAYING && remainingCount in 1..9

    fun canFastComplete(livesEnabled: Boolean, hideMistakes: Boolean = false): Boolean =
        status(livesEnabled) == GameStatus.PLAYING && fastCompleteCount(hideMistakes) in 1..9

    /** Whether cell [index] should be drawn as a mistake. Hidden mistakes show only after [checkMistakes]. */
    fun showsMistake(index: Int, hideMistakes: Boolean = false): Boolean {
        val v = values[index]
        if (v == 0 || v == puzzle.solution[index]) return false
        return !hideMistakes || revealedMistakes[index] == v
    }

    /**
     * Reveals every wrong placement. Each newly revealed wrong cell/value costs one life when lives
     * are enabled (a mistake already revealed is not charged again). Does not touch undo history.
     */
    fun checkMistakes(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING) return this
        val wrong = values.indices.filter { values[it] != 0 && values[it] != puzzle.solution[it] }
        val newlyFound = wrong.count { revealedMistakes[it] != values[it] }
        val newLives = if (livesEnabled) (lives - newlyFound).coerceAtLeast(0) else lives
        return copy(lives = newLives, revealedMistakes = wrong.associateWith { values[it] })
    }

    private fun changed(
        newValues: List<Int> = values,
        newNotes: List<Set<Int>> = notes,
        newCellColors: Map<Int, Int> = cellColors,
        newLines: Set<BoardLine> = lines,
        newLives: Int = lives
    ): GameState =
        copy(
            values = newValues,
            notes = newNotes,
            cellColors = newCellColors,
            lines = newLines,
            lives = newLives,
            history = (history + BoardSnapshot(values, notes, cellColors, lines)).takeLast(200),
            future = emptyList()
        )

    fun place(index: Int, digit: Int, livesEnabled: Boolean = true, hideMistakes: Boolean = false): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || index !in 0..80 || digit !in 0..9 || puzzle.givens[index] != 0 || values[index] == digit) return this
        val newValues = values.toMutableList().also { it[index] = digit }
        val newNotes = notes.toMutableList().also { list ->
            list[index] = emptySet()
            if (digit != 0) {
                for (peer in Sudoku.peers(index)) {
                    if (digit in list[peer]) list[peer] = list[peer] - digit
                }
            }
        }
        val life = if (livesEnabled && !hideMistakes && digit != 0 && digit != puzzle.solution[index]) (lives - 1).coerceAtLeast(0) else lives
        return changed(newValues = newValues, newNotes = newNotes, newLives = life)
    }

    /** Fills the board with the solution. With [hideMistakes], only empty cells are filled (wrong entries stay). */
    fun fastComplete(livesEnabled: Boolean = true, hideMistakes: Boolean = false): GameState {
        if (!canFastComplete(livesEnabled, hideMistakes)) return this
        val filled = if (hideMistakes) values.indices.map { if (values[it] == 0) puzzle.solution[it] else values[it] } else puzzle.solution
        return changed(newValues = filled, newNotes = List(81) { emptySet() })
    }

    fun toggleNote(
        index: Int,
        digit: Int,
        livesEnabled: Boolean = true,
        recordHistory: Boolean = true
    ): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || index !in 0..80 || digit !in 1..9 || values[index] != 0) return this
        val newNotes = notes.toMutableList()
        newNotes[index] = if (digit in notes[index]) notes[index] - digit else notes[index] + digit
        return if (recordHistory) changed(newNotes = newNotes) else copy(notes = newNotes)
    }

    fun setColor(index: Int, colorIndex: Int, livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || index !in 0..80) return this
        val currentColor = cellColors[index]
        val newColors = if (currentColor == colorIndex || colorIndex == 0) {
            cellColors - index
        } else {
            cellColors + (index to colorIndex)
        }
        if (newColors == cellColors) return this
        return changed(newCellColors = newColors)
    }

    fun toggleLine(from: Int, to: Int, style: LineStyle, livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || from !in 0..80 || to !in 0..80 || from == to) return this
        val a = minOf(from, to)
        val b = maxOf(from, to)
        val existing = lines.firstOrNull { it.from == a && it.to == b }
        val newLines = if (existing == null) {
            lines + BoardLine(a, b, style)
        } else if (existing.style != style) {
            lines - existing + BoardLine(a, b, style)
        } else {
            lines - existing
        }
        if (newLines == lines) return this
        return changed(newLines = newLines)
    }

    fun clearAllCellColors(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || cellColors.isEmpty()) return this
        return changed(newCellColors = emptyMap())
    }

    fun clearAllLines(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || lines.isEmpty()) return this
        return changed(newLines = emptySet())
    }

    fun clearCellAnnotations(index: Int, livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || index !in 0..80) return this
        val newColors = cellColors - index
        val newLines = lines.filterNot { it.from == index || it.to == index }.toSet()
        if (newColors == cellColors && newLines == lines) return this
        return changed(newCellColors = newColors, newLines = newLines)
    }

    fun clearAllAnnotations(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING) return this
        if (cellColors.isEmpty() && lines.isEmpty()) return this
        return changed(newCellColors = emptyMap(), newLines = emptySet())
    }

    fun fastPencil(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING) return this
        val updated = values.indices.map { Sudoku.candidates(values, it) }
        return if (updated == notes) this else changed(newNotes = updated)
    }

    fun undo(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || history.isEmpty()) return this
        val prior = history.last()
        return copy(
            values = prior.values,
            notes = prior.notes,
            cellColors = prior.cellColors,
            lines = prior.lines,
            history = history.dropLast(1),
            future = future + BoardSnapshot(values, notes, cellColors, lines)
        )
    }

    fun redo(livesEnabled: Boolean = true): GameState {
        if (status(livesEnabled) != GameStatus.PLAYING || future.isEmpty()) return this
        val next = future.last()
        return copy(
            values = next.values,
            notes = next.notes,
            cellColors = next.cellColors,
            lines = next.lines,
            future = future.dropLast(1),
            history = history + BoardSnapshot(values, notes, cellColors, lines)
        )
    }

    fun tick(livesEnabled: Boolean = true): GameState =
        if (status(livesEnabled) == GameStatus.PLAYING) copy(elapsedSeconds = elapsedSeconds + 1) else this

    companion object {
        fun new(puzzle: Puzzle, difficulty: Difficulty): GameState =
            GameState(puzzle, difficulty, puzzle.givens, List(81) { emptySet() })
    }
}

enum class SelectionMode { DIGIT, COLOR, LINE }

data class SelectionState(
    val selectedCell: Int? = null,
    val selectedDigit: Int? = null,
    val pencil: Boolean = false,
    val viewOnly: Boolean = false,
    val mode: SelectionMode = SelectionMode.DIGIT,
    val selectedColor: Int = 1,
    val lineStyle: LineStyle = LineStyle.SOLID
) {
    fun onCellTap(
        game: GameState,
        index: Int,
        livesEnabled: Boolean = true,
        autoAdvanceDigit: Boolean = true,
        hideMistakes: Boolean = false
    ): Pair<GameState, SelectionState> {
        if (mode == SelectionMode.COLOR) {
            val nextGame = game.setColor(index, selectedColor, livesEnabled)
            return nextGame to copy(selectedCell = index)
        }
        if (mode == SelectionMode.LINE) {
            val from = selectedCell
            return if (from == null) {
                game to copy(selectedCell = index)
            } else if (from == index) {
                game to copy(selectedCell = null)
            } else {
                val nextGame = game.toggleLine(from, index, lineStyle, livesEnabled)
                nextGame to copy(selectedCell = null)
            }
        }
        val placed = game.values.getOrNull(index) ?: 0
        if (placed != 0) {
            return game to copy(selectedCell = index, selectedDigit = placed)
        }
        val nextSelection = copy(selectedCell = index)
        if (viewOnly) return game to nextSelection
        val digit = selectedDigit
        if (digit == null || game.puzzle.givens.getOrNull(index) != 0) {
            return game to nextSelection
        }
        if (pencil) {
            return game.toggleNote(index, digit, livesEnabled, recordHistory = true) to nextSelection
        }
        val nextGame = game.place(index, digit, livesEnabled, hideMistakes)
        val updatedSelection = if (autoAdvanceDigit && nextGame != game && nextGame.isDigitComplete(digit, hideMistakes)) {
            nextSelection.copy(selectedDigit = nextGame.nextIncompleteDigit(digit, hideMistakes))
        } else {
            nextSelection
        }
        return nextGame to updatedSelection
    }

    fun onPencilDragCell(
        game: GameState,
        index: Int,
        firstCellInGesture: Boolean,
        livesEnabled: Boolean = true
    ): Pair<GameState, SelectionState> {
        if (mode != SelectionMode.DIGIT) return game to this
        if (!pencil || viewOnly) return game to this
        val digit = selectedDigit ?: return game to copy(selectedCell = index)
        if (game.values.getOrNull(index) != 0) return game to this
        val nextGame = game.toggleNote(index, digit, livesEnabled, recordHistory = firstCellInGesture)
        return nextGame to copy(selectedCell = index)
    }

    fun onDigitTap(digit: Int): SelectionState = copy(selectedCell = null, selectedDigit = digit)

    fun onDigitLongPress(digit: Int): SelectionState =
        if (selectedDigit == digit) copy(selectedDigit = null) else this

    fun togglePencil(): SelectionState =
        if (pencil) copy(pencil = false) else copy(pencil = true, viewOnly = false)

    fun toggleViewOnly(): SelectionState =
        if (viewOnly) copy(viewOnly = false) else copy(viewOnly = true, pencil = false)

    fun setMode(newMode: SelectionMode): SelectionState =
        if (mode == newMode) copy(mode = SelectionMode.DIGIT, selectedCell = null) else copy(mode = newMode, selectedCell = null)
}

enum class ColorSlot(val label: String, val defaultArgb: Long, val options: List<Long>) {
    BACKGROUND("Background", 0xFF111418, listOf(0xFF111418, 0xFF000000, 0xFF16131E, 0xFF0F1916, 0xFF181411, 0xFF1B2028)),
    SURFACE("Cell surface", 0xFF1C2228, listOf(0xFF1C2228, 0xFF121212, 0xFF231E2E, 0xFF172621, 0xFF26201B, 0xFF28303C)),
    ACCENT("Accent & 3×3 border", 0xFF75DCC7, listOf(0xFF75DCC7, 0xFF64B5F6, 0xFFB388FF, 0xFFFFB74D, 0xFF81C784, 0xFFF06292)),
    CELL_BORDER("Inner cell lines", 0xFF38424A, listOf(0xFF38424A, 0xFF2C2C2C, 0xFF4A3E5D, 0xFF2E473E, 0xFF4F4339, 0xFF4E5D6C)),
    SELECTED_CELL("Selected cell", 0xFF35584F, listOf(0xFF35584F, 0xFF2B4C6F, 0xFF4A3768, 0xFF5C4728, 0xFF345638, 0xFF5C3244)),
    PEER_HIGHLIGHT("Row/col/box area", 0xFF29333A, listOf(0xFF29333A, 0xFF1E2229, 0xFF312A40, 0xFF21342E, 0xFF362D26, 0xFF343E4D)),
    MATCHING_NUMBER("Matching number cell", 0xFF296A5B, listOf(0xFF296A5B, 0xFF1E5288, 0xFF563D82, 0xFF7A5621, 0xFF2E6B36, 0xFF7A3352)),
    MATCHING_NOTE("Matching note box", 0xFF4A6B82, listOf(0xFF4A6B82, 0xFF3E7B6E, 0xFF6A4E90, 0xFF8C6239, 0xFF4B7A47, 0xFF884A66)),
    COMPLETED_DIGIT_SURFACE("Completed digit button", 0xFF14181D, listOf(0xFF14181D, 0xFF0A0A0A, 0xFF181420, 0xFF111C18, 0xFF1B1613, 0xFF1E242E)),
    COMPLETED_DIGIT_TEXT("Completed digit text", 0xFF4B565E, listOf(0xFF4B565E, 0xFF424242, 0xFF5D5370, 0xFF4A6158, 0xFF665A4F, 0xFF546470)),
    INK("Given numbers & text", 0xFFE7ECEF, listOf(0xFFE7ECEF, 0xFFFFFFFF, 0xFFF3E5F5, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFCFD8DC)),
    MUTED("Notes & secondary text", 0xFF87949D, listOf(0xFF87949D, 0xFF9E9E9E, 0xFFA396B8, 0xFF88A69B, 0xFFA8988A, 0xFF90A4AE)),
    DANGER("Mistakes & hearts", 0xFFFF8189, listOf(0xFFFF8189, 0xFFFF5252, 0xFFFFAB40, 0xFFFF4081, 0xFFE040FB, 0xFFFF6E40)),
    ANNOTATION_LINE("Connecting lines", 0xFF75DCC7, listOf(0xFF75DCC7, 0xFFFF5252, 0xFFFFB74D, 0xFFFFEB3B, 0xFF81C784, 0xFF64B5F6, 0xFFB388FF, 0xFFFFFFFF))
}

data class ThemePalette(
    val colors: Map<ColorSlot, Long> = ColorSlot.entries.associateWith { it.defaultArgb }
) {
    operator fun get(slot: ColorSlot): Long = colors[slot] ?: slot.defaultArgb

    fun withColor(slot: ColorSlot, argb: Long): ThemePalette =
        copy(colors = colors + (slot to (argb or 0xFF000000L)))

    fun encodeColors(): String =
        ColorSlot.entries.joinToString(",") { "${it.name}:${get(it).toString(16)}" }

    companion object {
        fun decodeColors(raw: String): ThemePalette {
            val parsedColors = ColorSlot.entries.associateWith { it.defaultArgb }.toMutableMap()
            if (raw.isNotEmpty()) {
                for (entry in raw.split(",")) {
                    val kv = entry.split(":")
                    if (kv.size == 2) {
                        val slot = ColorSlot.entries.firstOrNull { it.name == kv[0] }
                        val value = kv[1].toLongOrNull(16)
                        if (slot != null && value != null) parsedColors[slot] = value or 0xFF000000L
                    }
                }
            }
            return ThemePalette(parsedColors)
        }

        fun parseHex(input: String): Long? {
            val clean = input.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            return when (clean.length) {
                6 -> clean.toLongOrNull(16)?.let { it or 0xFF000000L }
                8 -> clean.toLongOrNull(16)?.let { it or 0xFF000000L }
                else -> null
            }
        }

        fun formatHex(argb: Long): String = "%06X".format(argb and 0xFFFFFFL)
    }
}

data class SavedTheme(
    val name: String,
    val palette: ThemePalette,
    val builtIn: Boolean = false
) {
    companion object {
        val BUILT_IN: List<SavedTheme> = listOf(
            SavedTheme("Woku Mint", ThemePalette(), builtIn = true),
            SavedTheme(
                "Ocean Blue",
                ThemePalette(
                    mapOf(
                        ColorSlot.BACKGROUND to 0xFF0D1520,
                        ColorSlot.SURFACE to 0xFF152232,
                        ColorSlot.ACCENT to 0xFF64B5F6,
                        ColorSlot.CELL_BORDER to 0xFF2B3E54,
                        ColorSlot.SELECTED_CELL to 0xFF284B70,
                        ColorSlot.PEER_HIGHLIGHT to 0xFF1C2D42,
                        ColorSlot.MATCHING_NUMBER to 0xFF1E5288,
                        ColorSlot.MATCHING_NOTE to 0xFF3E7B6E,
                        ColorSlot.COMPLETED_DIGIT_SURFACE to 0xFF101925,
                        ColorSlot.COMPLETED_DIGIT_TEXT to 0xFF465C75,
                        ColorSlot.INK to 0xFFE6F0FA,
                        ColorSlot.MUTED to 0xFF849BB5,
                        ColorSlot.DANGER to 0xFFFF6E7A,
                        ColorSlot.ANNOTATION_LINE to 0xFF64B5F6
                    )
                ),
                builtIn = true
            ),
            SavedTheme(
                "Amethyst",
                ThemePalette(
                    mapOf(
                        ColorSlot.BACKGROUND to 0xFF14111D,
                        ColorSlot.SURFACE to 0xFF211C30,
                        ColorSlot.ACCENT to 0xFFB388FF,
                        ColorSlot.CELL_BORDER to 0xFF3F3556,
                        ColorSlot.SELECTED_CELL to 0xFF4A3768,
                        ColorSlot.PEER_HIGHLIGHT to 0xFF2E2640,
                        ColorSlot.MATCHING_NUMBER to 0xFF563D82,
                        ColorSlot.MATCHING_NOTE to 0xFF6A4E90,
                        ColorSlot.COMPLETED_DIGIT_SURFACE to 0xFF181423,
                        ColorSlot.COMPLETED_DIGIT_TEXT to 0xFF5D5370,
                        ColorSlot.INK to 0xFFF3E5F5,
                        ColorSlot.MUTED to 0xFF9F92B8,
                        ColorSlot.DANGER to 0xFFFF80AB,
                        ColorSlot.ANNOTATION_LINE to 0xFFB388FF
                    )
                ),
                builtIn = true
            ),
            SavedTheme(
                "Amber Glow",
                ThemePalette(
                    mapOf(
                        ColorSlot.BACKGROUND to 0xFF181411,
                        ColorSlot.SURFACE to 0xFF241E1A,
                        ColorSlot.ACCENT to 0xFFFFB74D,
                        ColorSlot.CELL_BORDER to 0xFF473B32,
                        ColorSlot.SELECTED_CELL to 0xFF5C4728,
                        ColorSlot.PEER_HIGHLIGHT to 0xFF332A23,
                        ColorSlot.MATCHING_NUMBER to 0xFF7A5621,
                        ColorSlot.MATCHING_NOTE to 0xFF8C6239,
                        ColorSlot.COMPLETED_DIGIT_SURFACE to 0xFF1B1613,
                        ColorSlot.COMPLETED_DIGIT_TEXT to 0xFF665A4F,
                        ColorSlot.INK to 0xFFFFF3E0,
                        ColorSlot.MUTED to 0xFFA8988A,
                        ColorSlot.DANGER to 0xFFFF6E40,
                        ColorSlot.ANNOTATION_LINE to 0xFFFFB74D
                    )
                ),
                builtIn = true
            ),
            SavedTheme(
                "OLED Pitch",
                ThemePalette(
                    mapOf(
                        ColorSlot.BACKGROUND to 0xFF000000,
                        ColorSlot.SURFACE to 0xFF0A0D10,
                        ColorSlot.ACCENT to 0xFF81C784,
                        ColorSlot.CELL_BORDER to 0xFF242B30,
                        ColorSlot.SELECTED_CELL to 0xFF28472B,
                        ColorSlot.PEER_HIGHLIGHT to 0xFF151C18,
                        ColorSlot.MATCHING_NUMBER to 0xFF2E6B36,
                        ColorSlot.MATCHING_NOTE to 0xFF4B7A47,
                        ColorSlot.COMPLETED_DIGIT_SURFACE to 0xFF060809,
                        ColorSlot.COMPLETED_DIGIT_TEXT to 0xFF424E45,
                        ColorSlot.INK to 0xFFE8F5E9,
                        ColorSlot.MUTED to 0xFF7E9183,
                        ColorSlot.DANGER to 0xFFFF5252,
                        ColorSlot.ANNOTATION_LINE to 0xFF81C784
                    )
                ),
                builtIn = true
            )
        )
    }
}

data class AppSettings(
    val livesEnabled: Boolean = true,
    val autoAdvanceDigit: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundVolume: Float = 0.6f,
    val palette: ThemePalette = ThemePalette(),
    val customThemes: List<SavedTheme> = emptyList(),
    /** Off by default: wrong digits are hidden (no red, no life loss) until the player taps Check. */
    val hideMistakesUntilChecked: Boolean = false,
    /** On by default: prevents screen from dimming/sleeping while playing. */
    val keepScreenOn: Boolean = true
) {
    val allThemes: List<SavedTheme> get() = SavedTheme.BUILT_IN + customThemes

    fun activeThemeName(): String? = allThemes.firstOrNull { it.palette == palette }?.name

    fun saveCustomTheme(rawName: String): AppSettings {
        val cleanName = rawName.trim().replace("|", "").replace("~", "").replace(";", "").take(24)
        if (cleanName.isEmpty()) return this
        val filtered = customThemes.filterNot { it.name.equals(cleanName, ignoreCase = true) }
        return copy(customThemes = filtered + SavedTheme(cleanName, palette, builtIn = false))
    }

    fun deleteCustomTheme(name: String): AppSettings =
        copy(customThemes = customThemes.filterNot { it.name == name })

    fun encode(): String = listOf(
        "5",
        if (livesEnabled) "1" else "0",
        if (autoAdvanceDigit) "1" else "0",
        if (hapticsEnabled) "1" else "0",
        "%.2f".format(java.util.Locale.US, soundVolume.coerceIn(0f, 1f)),
        palette.encodeColors(),
        customThemes.joinToString("~") { "${it.name};${it.palette.encodeColors()}" },
        if (hideMistakesUntilChecked) "1" else "0",
        if (keepScreenOn) "1" else "0"
    ).joinToString("|")

    companion object {
        fun decode(raw: String): AppSettings = runCatching {
            val parts = raw.split("|")
            val version = parts.firstOrNull() ?: ""
            require(version in listOf("1", "2", "3", "4", "5"))
            val lives = parts[1] == "1"
            val autoAdvance = parts[2] == "1"
            val haptics = parts[3] == "1"
            return when (version) {
                "1" -> {
                    require(parts.size >= 5)
                    AppSettings(lives, autoAdvance, haptics, palette = ThemePalette.decodeColors(parts[4]))
                }
                "2" -> {
                    require(parts.size >= 5)
                    val palette = ThemePalette.decodeColors(parts[4])
                    val custom = if (parts.size >= 6 && parts[5].isNotEmpty()) parseCustomThemes(parts[5]) else emptyList()
                    AppSettings(lives, autoAdvance, haptics, palette = palette, customThemes = custom)
                }
                else -> {
                    require(parts.size >= 6)
                    val volume = parts[4].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.6f
                    val palette = ThemePalette.decodeColors(parts[5])
                    val custom = if (parts.size >= 7 && parts[6].isNotEmpty()) parseCustomThemes(parts[6]) else emptyList()
                    val hideMistakes = version in listOf("4", "5") && parts.size >= 8 && parts[7] == "1"
                    val keepScreen = if (version == "5" && parts.size >= 9) parts[8] == "1" else true
                    AppSettings(lives, autoAdvance, haptics, soundVolume = volume, palette = palette,
                        customThemes = custom, hideMistakesUntilChecked = hideMistakes, keepScreenOn = keepScreen)
                }
            }
        }.getOrElse { AppSettings() }

        private fun parseCustomThemes(raw: String): List<SavedTheme> =
            raw.split("~").mapNotNull { entry ->
                val idx = entry.indexOf(';')
                if (idx <= 0) null
                else SavedTheme(
                    name = entry.substring(0, idx),
                    palette = ThemePalette.decodeColors(entry.substring(idx + 1)),
                    builtIn = false
                )
            }
    }
}

/** Best-time storage keys and one-time resets when a difficulty's meaning changes. */
object BestTimes {
    /** v2: Expert/Extreme/Diabolical moved from clue counts to the technique-rated puzzle bank. */
    const val CURRENT_VERSION = 2
    const val VERSION_KEY = "best_version"

    fun key(difficulty: Difficulty): String = "best_${difficulty.name}"

    /** Keys to delete when upgrading from [storedVersion]; empty when already current. */
    fun keysToReset(storedVersion: Int): List<String> =
        if (storedVersion < 2) listOf(Difficulty.EXPERT, Difficulty.EXTREME, Difficulty.DIABOLICAL).map(::key)
        else emptyList()
}
