package com.sarge841.woku

import org.junit.Assert.*
import org.junit.Test

class SaveCodecTest {
    @Test fun roundTripRestoresBoardNotesTimerLivesAndHistory() {
        val puzzle = Sudoku.generate(Difficulty.MEDIUM, 13L)
        val i = puzzle.givens.indexOf(0)
        val game = GameState.new(puzzle, Difficulty.MEDIUM).toggleNote(i, 2).place(i, 9).undo().copy(elapsedSeconds = 72)
        val restored = SaveCodec.decode(SaveCodec.encode(game))
        assertEquals(game, restored)
        assertEquals(game.redo(), restored?.redo())
    }

    @Test fun corruptedSaveIsRejected() {
        assertNull(SaveCodec.decode("broken"))
    }
}
