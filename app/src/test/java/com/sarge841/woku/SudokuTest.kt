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

    @Test fun diabolicalGeneratesDistinctValid17CluePuzzlesAcrossSeeds() {
        val puzzles = (1L..12L).map { Sudoku.generate(Difficulty.DIABOLICAL, seed = it) }
        for (p in puzzles) {
            assertEquals(17, p.givens.count { it != 0 })
            assertEquals(1, Sudoku.countSolutions(p.givens))
            assertEquals(1, Sudoku.countSolutions(p.solution))
            assertTrue(p.givens.indices.all { p.givens[it] == 0 || p.givens[it] == p.solution[it] })
        }
        assertTrue(puzzles.map { it.givens }.distinct().size > 1)
    }
}
