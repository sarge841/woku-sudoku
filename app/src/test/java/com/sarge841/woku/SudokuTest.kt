package com.sarge841.woku

import org.junit.Assert.*
import org.junit.Test

class SudokuTest {
    @Test fun generatedPuzzleHasOneSolutionAndCorrectClueCount() {
        for (difficulty in Difficulty.entries) {
            val puzzle = Sudoku.generate(difficulty, seed = 42L)
            assertEquals(difficulty.clues, puzzle.givens.count { it != 0 })
            assertEquals(1, Sudoku.countSolutions(puzzle.givens))
            assertTrue(puzzle.solution.all { it in 1..9 })
            assertTrue(puzzle.givens.indices.all { puzzle.givens[it] == 0 || puzzle.givens[it] == puzzle.solution[it] })
        }
    }
}
