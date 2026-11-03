package com.sarge841.woku

import kotlin.random.Random

enum class Difficulty(val clues: Int) {
    EASY(42), MEDIUM(36), HARD(30), EXPERT(26), EXTREME(23)
}

data class Puzzle(val givens: List<Int>, val solution: List<Int>)

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
        repeat(500) {
            val digits = (1..9).shuffled(random)
            val rows = (0..2).shuffled(random).flatMap { band -> (0..2).shuffled(random).map { band * 3 + it } }
            val cols = (0..2).shuffled(random).flatMap { stack -> (0..2).shuffled(random).map { stack * 3 + it } }
            val solution = rows.flatMap { row -> cols.map { col -> digits[(row * 3 + row / 3 + col) % 9] } }
            val board = solution.toMutableList()
            for (i in (0..80).shuffled(random)) {
                if (board.count { it != 0 } == difficulty.clues) break
                val old = board[i]
                board[i] = 0
                if (countSolutions(board) != 1) board[i] = old
            }
            if (board.count { it != 0 } == difficulty.clues) return Puzzle(board, solution)
        }
        error("Could not generate a unique ${difficulty.name.lowercase()} puzzle")
    }
}
