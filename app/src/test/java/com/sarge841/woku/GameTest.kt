package com.sarge841.woku

import org.junit.Assert.*
import org.junit.Test

class GameTest {
    private val solution = listOf(
        5,3,4,6,7,8,9,1,2, 6,7,2,1,9,5,3,4,8, 1,9,8,3,4,2,5,6,7,
        8,5,9,7,6,1,4,2,3, 4,2,6,8,5,3,7,9,1, 7,1,3,9,2,4,8,5,6,
        9,6,1,5,3,7,2,8,4, 2,8,7,4,1,9,6,3,5, 3,4,5,2,8,6,1,7,9
    )
    private fun game() = GameState.new(Puzzle(List(81) { if (it == 0) 5 else 0 }, solution), Difficulty.EASY)

    @Test fun wrongEntryCostsLifeAndUndoRestoresWholeNoteSetWithoutRefundingLife() {
        val original = game().toggleNote(1, 3).toggleNote(1, 4)
        val wrong = original.place(1, 9)
        assertEquals(2, wrong.lives)
        assertEquals(9, wrong.values[1])
        assertTrue(wrong.notes[1].isEmpty())
        val undone = wrong.undo()
        assertEquals(setOf(3, 4), undone.notes[1])
        assertEquals(0, undone.values[1])
        assertEquals(2, undone.lives)
        val redone = undone.redo()
        assertEquals(9, redone.values[1])
        assertEquals(2, redone.lives)
    }

    @Test fun fastPencilUsesRowColumnAndBoxAndIsOneUndoStep() {
        val start = game()
        val filled = start.fastPencil()
        assertEquals(Sudoku.candidates(start.values, 1), filled.notes[1])
        assertFalse(5 in filled.notes[1])
        assertEquals(start.notes, filled.undo().notes)
        assertEquals(filled.notes, filled.undo().redo().notes)
    }

    @Test fun finishedBoardWinsAndNoMoreMovesAreAllowed() {
        val start = GameState.new(Puzzle(solution.mapIndexed { i, n -> if (i == 80) 0 else n }, solution), Difficulty.EASY)
        val won = start.place(80, 9)
        assertEquals(GameStatus.WON, won.status)
        assertEquals(won, won.place(80, 1))
    }

    @Test fun thirdMistakeEndsGame() {
        val lost = game().place(1, 9).place(1, 8).place(1, 7)
        assertEquals(0, lost.lives)
        assertEquals(GameStatus.LOST, lost.status)
        assertEquals(lost, lost.undo())
    }
}
