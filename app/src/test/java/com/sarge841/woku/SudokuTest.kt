package com.sarge841.woku

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SudokuTest {
    private fun assertValid(p: Puzzle) {
        assertEquals(1, Sudoku.countSolutions(p.givens))
        assertTrue(p.solution.all { it in 1..9 })
        assertEquals(1, Sudoku.countSolutions(p.solution))
        assertTrue(p.givens.indices.all { p.givens[it] == 0 || p.givens[it] == p.solution[it] })
    }

    @Test fun generatedPuzzleHasOneSolutionForEveryDifficulty() {
        for (difficulty in Difficulty.entries) {
            val puzzle = Sudoku.generate(difficulty, seed = 42L)
            assertValid(puzzle)
            difficulty.clues?.let { assertEquals(it, puzzle.givens.count { v -> v != 0 }) }
        }
    }

    @Test fun eachDifficultyIsEitherClueGeneratedOrBankedWithARatingBand() {
        for (d in Difficulty.entries) {
            assertTrue(d.name, (d.clues != null) xor (d.bank != null))
            assertEquals(d.name, d.bank != null, d.ratingRange != null)
        }
        assertEquals(
            listOf(Difficulty.EXPERT, Difficulty.EXTREME, Difficulty.DIABOLICAL, Difficulty.DIABOLICAL_PLUS),
            Difficulty.entries.filter { it.bank != null }
        )
        assertEquals("Diabolical+", Difficulty.DIABOLICAL_PLUS.label)
    }

    @Test fun bankedTiersHaveWellFormedPuzzlesInsideTheirRatingBand() {
        for (d in Difficulty.entries.filter { it.bank != null }) {
            val entries = PuzzleBank.entries(d)
            assertTrue("${d.name} bank too small", entries.size >= 1000)
            val range = d.ratingRange!!
            for (e in entries) {
                assertEquals(81, e.givens.length)
                assertTrue(e.givens.all { it in '0'..'9' })
                assertTrue("${d.name} rating ${e.rating}", e.rating in range)
            }
            assertEquals(entries.size, entries.map { it.givens }.distinct().size)
        }
        // Tiers must not overlap so best times stay comparable.
        val banked = Difficulty.entries.filter { it.bank != null }.map { PuzzleBank.entries(it).map { e -> e.givens }.toSet() }
        for (i in banked.indices) for (j in i + 1 until banked.size) assertTrue((banked[i] intersect banked[j]).isEmpty())
        assertTrue(PuzzleBank.entries(Difficulty.DIABOLICAL_PLUS).any { it.rating >= 9.0 })
    }

    @Test fun sampledBankPuzzlesHaveUniqueSolutions() {
        val random = Random(7)
        for (d in Difficulty.entries.filter { it.bank != null }) {
            val entries = PuzzleBank.entries(d)
            repeat(15) {
                val e = entries[random.nextInt(entries.size)]
                assertEquals("${d.name} ${e.givens}", 1, Sudoku.countSolutions(e.givens.map { it.digitToInt() }))
            }
        }
    }

    @Test fun transformPreservesClueCountAndUniqueness() {
        val source = PuzzleBank.entries(Difficulty.DIABOLICAL_PLUS).last().givens.map { it.digitToInt() }
        val random = Random(3)
        val boards = (1..6).map { Sudoku.transform(source, random) }
        for (b in boards) {
            assertEquals(source.count { it != 0 }, b.count { it != 0 })
            assertEquals(1, Sudoku.countSolutions(b))
        }
        assertTrue(boards.distinct().size > 1)
    }

    @Test fun diabolicalGeneratesDistinctValidPuzzlesAcrossSeeds() {
        val puzzles = (1L..8L).map { Sudoku.generate(Difficulty.DIABOLICAL, seed = it) }
        puzzles.forEach(::assertValid)
        assertTrue(puzzles.map { it.givens }.distinct().size > 1)
        assertEquals(puzzles, (1L..8L).map { Sudoku.generate(Difficulty.DIABOLICAL, seed = it) })
    }
}
