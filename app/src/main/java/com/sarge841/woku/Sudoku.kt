package com.sarge841.woku

import kotlin.random.Random

/**
 * Easy/Medium/Hard are generated on device by clue count ([clues]). Expert and above are drawn
 * from a bundled, technique-rated bank ([bank], Sukaku Explainer rating in [ratingRange]); see
 * [PuzzleBank].
 */
enum class Difficulty(
    val label: String,
    val clues: Int? = null,
    val bank: String? = null,
    val ratingRange: ClosedFloatingPointRange<Double>? = null
) {
    EASY("Easy", clues = 42),
    MEDIUM("Medium", clues = 36),
    HARD("Hard", clues = 30),
    EXPERT("Expert", bank = "expert", ratingRange = 4.0..5.9),
    EXTREME("Extreme", bank = "extreme", ratingRange = 6.0..6.9),
    DIABOLICAL("Diabolical", bank = "diabolical", ratingRange = 7.0..8.4),
    DIABOLICAL_PLUS("Diabolical+", bank = "diabolical_plus", ratingRange = 8.5..10.0)
}

data class Puzzle(val givens: List<Int>, val solution: List<Int>)

data class BankEntry(val givens: String, val rating: Double)

/**
 * Bundled puzzles from the public-domain Sudoku Exchange Puzzle Bank, rated with Sukaku Explainer.
 * Files live in `src/main/resources/com/sarge841/woku/puzzles/` (one `<81 digits> <rating>` per
 * line) and are rebuilt by `scripts/build_puzzle_bank.py`. Plain Java resources keep this
 * Android-independent so unit tests can read the same data the app ships.
 */
object PuzzleBank {
    private val cache = mutableMapOf<String, List<BankEntry>>()

    fun entries(difficulty: Difficulty): List<BankEntry> {
        val name = difficulty.bank ?: return emptyList()
        return synchronized(cache) {
            cache.getOrPut(name) {
                val stream = PuzzleBank::class.java.getResourceAsStream("/com/sarge841/woku/puzzles/$name.txt")
                    ?: error("Missing puzzle bank $name")
                stream.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.map { line ->
                        val parts = line.trim().split(" ")
                        BankEntry(parts[0], parts[1].toDouble())
                    }.toList()
                }
            }
        }
    }
}

object Sudoku {

    fun peers(index: Int): Set<Int> {
        val row = index / 9
        val col = index % 9
        val boxRow = row / 3 * 3
        val boxCol = col / 3 * 3
        val rowIndices = (0..8).map { row * 9 + it }
        val colIndices = (0..8).map { it * 9 + col }
        val boxIndices = (0..2).flatMap { r -> (0..2).map { c -> (boxRow + r) * 9 + boxCol + c } }
        return (rowIndices + colIndices + boxIndices - index).toSet()
    }

    fun candidates(values: List<Int>, index: Int): Set<Int> {
        if (values[index] != 0) return emptySet()
        val used = peers(index).map { values[it] }
        return (1..9).filterNot { it in used }.toSet()
    }

    fun solve(values: List<Int>): List<Int>? {
        val board = values.toMutableList()
        if (board.size != 81 || board.any { it !in 0..9 }) return null
        fun search(): Boolean {
            var cell = -1
            var options: Set<Int> = emptySet()
            for (i in board.indices) if (board[i] == 0) {
                val choices = candidates(board, i)
                if (choices.isEmpty()) return false
                if (cell == -1 || choices.size < options.size) {
                    cell = i
                    options = choices
                    if (choices.size == 1) break
                }
            }
            if (cell == -1) return true
            for (digit in options) {
                board[cell] = digit
                if (search()) return true
                board[cell] = 0
            }
            return false
        }
        return if (search()) board else null
    }

    fun countSolutions(values: List<Int>, limit: Int = 2): Int {
        val board = values.toMutableList()
        if (board.size != 81 || board.any { it !in 0..9 }) return 0
        for (i in board.indices) {
            if (board[i] != 0) {
                val digit = board[i]
                board[i] = 0
                val valid = digit in candidates(board, i)
                board[i] = digit
                if (!valid) return 0
            }
        }
        fun search(): Int {
            var cell = -1
            var options: Set<Int> = emptySet()
            for (i in board.indices) if (board[i] == 0) {
                val choices = candidates(board, i)
                if (choices.isEmpty()) return 0
                if (cell == -1 || choices.size < options.size) {
                    cell = i
                    options = choices
                    if (choices.size == 1) break
                }
            }
            if (cell == -1) return 1
            var total = 0
            for (digit in options) {
                board[cell] = digit
                total += search()
                board[cell] = 0
                if (total >= limit) break
            }
            return total
        }
        return search()
    }

    fun generate(difficulty: Difficulty, seed: Long = Random.nextLong()): Puzzle {
        val random = Random(seed)
        if (difficulty.bank != null) {
            val entry = PuzzleBank.entries(difficulty).random(random)
            val givens = transform(entry.givens.map { it.digitToInt() }, random)
            val solution = solve(givens) ?: error("Invalid ${difficulty.label} bank puzzle")
            return Puzzle(givens, solution)
        }
        val clues = difficulty.clues ?: error("${difficulty.label} has no clue target")
        repeat(500) {
            val digits = (1..9).shuffled(random)
            val rows = (0..2).shuffled(random).flatMap { band -> (0..2).shuffled(random).map { band * 3 + it } }
            val cols = (0..2).shuffled(random).flatMap { stack -> (0..2).shuffled(random).map { stack * 3 + it } }
            val solution = rows.flatMap { row -> cols.map { col -> digits[(row * 3 + row / 3 + col) % 9] } }
            val board = solution.toMutableList()
            for (i in (0..80).shuffled(random)) {
                if (board.count { it != 0 } == clues) break
                val old = board[i]
                board[i] = 0
                if (countSolutions(board) != 1) board[i] = old
            }
            if (board.count { it != 0 } == clues) return Puzzle(board, solution)
        }
        error("Could not generate a unique ${difficulty.label} puzzle")
    }

    /**
     * Applies a random rating-preserving symmetry: digit relabeling, band/stack and in-band
     * row/column shuffles, and an optional transpose. Uniqueness and solving techniques are
     * unchanged, so each bank puzzle yields many distinct-looking boards.
     */
    fun transform(board: List<Int>, random: Random): List<Int> {
        val digitMap = (1..9).shuffled(random)
        val rows = (0..2).shuffled(random).flatMap { band -> (0..2).shuffled(random).map { band * 3 + it } }
        val cols = (0..2).shuffled(random).flatMap { stack -> (0..2).shuffled(random).map { stack * 3 + it } }
        val transpose = random.nextBoolean()
        return List(81) { idx ->
            val r = idx / 9
            val c = idx % 9
            val srcRow = if (transpose) cols[c] else rows[r]
            val srcCol = if (transpose) rows[r] else cols[c]
            val v = board[srcRow * 9 + srcCol]
            if (v == 0) 0 else digitMap[v - 1]
        }
    }
}
