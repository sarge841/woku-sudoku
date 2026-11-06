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

    @Test fun roundTripKeepsCellColorsAndLinesAndOlderVersionSavesStillLoad() {
        val puzzle = Sudoku.generate(Difficulty.MEDIUM, 21L)
        val game = GameState.new(puzzle, Difficulty.MEDIUM)
            .setColor(5, 2)
            .setColor(14, 4)
            .toggleLine(5, 14, LineStyle.SOLID)
            .toggleLine(14, 23, LineStyle.DOTTED)

        val encoded = SaveCodec.encode(game)
        val decoded = SaveCodec.decode(encoded)
        assertEquals(game, decoded)
        assertEquals(mapOf(5 to 2, 14 to 4), decoded?.cellColors)
        assertEquals(setOf(BoardLine(5, 14, LineStyle.SOLID), BoardLine(14, 23, LineStyle.DOTTED)), decoded?.lines)

        // Version 2 save decoding test
        val v2 = SaveCodec.encode(game).replaceFirst("3|", "2|").split("|").take(10).joinToString("|")
        val decodedV2 = SaveCodec.decode(v2)
        assertNotNull(decodedV2)
        assertEquals(emptyMap<Int, Int>(), decodedV2?.cellColors)
        assertEquals(emptySet<BoardLine>(), decodedV2?.lines)
    }

    @Test fun roundTripKeepsRevealedMistakesAndVersionOneSavesStillLoad() {
        val puzzle = Sudoku.generate(Difficulty.MEDIUM, 21L)
        val i = puzzle.givens.indexOf(0)
        val wrong = (1..9).first { it != puzzle.solution[i] }
        val game = GameState.new(puzzle, Difficulty.MEDIUM).place(i, wrong, hideMistakes = true).checkMistakes()
        assertEquals(mapOf(i to wrong), game.revealedMistakes)
        assertEquals(game, SaveCodec.decode(SaveCodec.encode(game)))

        val v1 = SaveCodec.encode(game).replaceFirst("3|", "1|").split("|").take(9).joinToString("|")
        assertEquals(game.copy(revealedMistakes = emptyMap(), cellColors = emptyMap(), lines = emptySet()), SaveCodec.decode(v1))
    }

    @Test fun diabolicalPlusSaveRoundTrips() {
        val game = GameState.new(Sudoku.generate(Difficulty.DIABOLICAL_PLUS, 5L), Difficulty.DIABOLICAL_PLUS)
        assertEquals(game, SaveCodec.decode(SaveCodec.encode(game)))
    }
}
