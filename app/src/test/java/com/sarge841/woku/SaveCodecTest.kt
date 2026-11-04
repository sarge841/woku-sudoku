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

    @Test fun roundTripKeepsRevealedMistakesAndVersionOneSavesStillLoad() {
        val puzzle = Sudoku.generate(Difficulty.MEDIUM, 21L)
        val i = puzzle.givens.indexOf(0)
        val wrong = (1..9).first { it != puzzle.solution[i] }
        val game = GameState.new(puzzle, Difficulty.MEDIUM).place(i, wrong, hideMistakes = true).checkMistakes()
        assertEquals(mapOf(i to wrong), game.revealedMistakes)
        assertEquals(game, SaveCodec.decode(SaveCodec.encode(game)))

        val v1 = SaveCodec.encode(game).replaceFirst("2|", "1|").substringBeforeLast("|")
        assertEquals(game.copy(revealedMistakes = emptyMap()), SaveCodec.decode(v1))
    }

    @Test fun diabolicalPlusSaveRoundTrips() {
        val game = GameState.new(Sudoku.generate(Difficulty.DIABOLICAL_PLUS, 5L), Difficulty.DIABOLICAL_PLUS)
        assertEquals(game, SaveCodec.decode(SaveCodec.encode(game)))
    }
}
