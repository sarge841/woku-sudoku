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

    @Test fun selectingDigitDeselectsCellAndDoesNotPlaceUntilCellIsTapped() {
        val start = game()
        val (_, cellSelected) = SelectionState().onCellTap(start, 1)
        assertEquals(1, cellSelected.selectedCell)
        assertNull(cellSelected.selectedDigit)

        val digitSelected = cellSelected.onDigitTap(3)
        assertEquals(3, digitSelected.selectedDigit)
        assertNull(digitSelected.selectedCell)
        assertEquals(0, start.values[1])

        val (placedGame, afterPlaceSelection) = digitSelected.onCellTap(start, 1)
        assertEquals(3, placedGame.values[1])
        assertEquals(1, afterPlaceSelection.selectedCell)
        assertEquals(3, afterPlaceSelection.selectedDigit)

        val switchedDigit = afterPlaceSelection.onDigitTap(4)
        assertEquals(4, switchedDigit.selectedDigit)
        assertNull(switchedDigit.selectedCell)
    }

    @Test fun placingDigitClearsMatchingNotesInSameRowColumnAndBoxAndUndoRestoresThem() {
        val start = game()
            .toggleNote(2, 3)  // same row (row 0, col 2) and same box
            .toggleNote(5, 3)  // same row (row 0, col 5)
            .toggleNote(5, 4)  // different digit in same row
            .toggleNote(10, 3) // same col (row 1, col 1) and same box
            .toggleNote(19, 3) // same box (row 2, col 1)
            .toggleNote(28, 3) // same col (row 3, col 1)
            .toggleNote(40, 3) // unrelated cell (row 4, col 4)
        val placed = start.place(1, 3)
        assertFalse(3 in placed.notes[2])
        assertFalse(3 in placed.notes[5])
        assertTrue(4 in placed.notes[5])
        assertFalse(3 in placed.notes[10])
        assertFalse(3 in placed.notes[19])
        assertFalse(3 in placed.notes[28])
        assertTrue(3 in placed.notes[40])

        val undone = placed.undo()
        assertEquals(start.notes, undone.notes)
    }

    @Test fun viewOnlyModeAllowsSelectingCellsAndDigitsWithoutPlacingOrTogglingNotes() {
        val start = game()
        val viewOnly = SelectionState().toggleViewOnly().onDigitTap(3)
        assertTrue(viewOnly.viewOnly)
        assertFalse(viewOnly.pencil)

        val (afterEmptyTap, emptySelection) = viewOnly.onCellTap(start, 1)
        assertEquals(start, afterEmptyTap)
        assertEquals(1, emptySelection.selectedCell)
        assertEquals(3, emptySelection.selectedDigit)

        val (afterGivenTap, givenSelection) = emptySelection.onCellTap(start, 0)
        assertEquals(start, afterGivenTap)
        assertEquals(0, givenSelection.selectedCell)
        assertEquals(5, givenSelection.selectedDigit)

        val switchedToPencil = givenSelection.togglePencil()
        assertTrue(switchedToPencil.pencil)
        assertFalse(switchedToPencil.viewOnly)
    }

    @Test fun progressCountAndFastCompleteWhenAtMostNineRemaining() {
        val tenLeftGivens = solution.mapIndexed { i, n -> if (i < 10) 0 else n }
        val tenLeft = GameState.new(Puzzle(tenLeftGivens, solution), Difficulty.EASY)
        assertEquals(71, tenLeft.correctCount)
        assertEquals(10, tenLeft.remainingCount)
        assertFalse(tenLeft.canFastComplete)
        assertEquals(tenLeft, tenLeft.fastComplete())

        val nineLeft = tenLeft.place(0, solution[0])
        assertEquals(72, nineLeft.correctCount)
        assertEquals(9, nineLeft.remainingCount)
        assertTrue(nineLeft.canFastComplete)

        val completed = nineLeft.fastComplete()
        assertEquals(GameStatus.WON, completed.status)
        assertEquals(81, completed.correctCount)
        assertEquals(0, completed.remainingCount)
        assertTrue(completed.notes.all { it.isEmpty() })
    }

    @Test fun tappingFilledCellSelectsThatDigitWithoutOverwriting() {
        val start = game().place(1, 3)
        val initialSelection = SelectionState(selectedDigit = 4)

        val (afterGivenTap, givenSelection) = initialSelection.onCellTap(start, 0)
        assertEquals(start, afterGivenTap)
        assertEquals(0, givenSelection.selectedCell)
        assertEquals(5, givenSelection.selectedDigit)

        val (afterPlacedTap, placedSelection) = givenSelection.onCellTap(start, 1)
        assertEquals(start, afterPlacedTap)
        assertEquals(1, placedSelection.selectedCell)
        assertEquals(3, placedSelection.selectedDigit)
    }

    @Test fun tappingSelectedDigitKeepsItSelectedWhileLongPressDeselectsIt() {
        val selected = SelectionState().onDigitTap(7)
        assertEquals(7, selected.selectedDigit)

        val tappedAgain = selected.onDigitTap(7)
        assertEquals(7, tappedAgain.selectedDigit)

        val longPressedOther = tappedAgain.onDigitLongPress(2)
        assertEquals(7, longPressedOther.selectedDigit)

        val longPressedSame = tappedAgain.onDigitLongPress(7)
        assertNull(longPressedSame.selectedDigit)
    }

    @Test fun autoAdvancesToNextIncompleteDigitWhenEnabled() {
        val lastFourIndex = solution.indexOfLast { it == 4 }
        val lastSixIndex = solution.indexOfLast { it == 6 }
        val givens = solution.mapIndexed { i, n ->
            if (i == lastFourIndex || i == lastSixIndex) 0 else n
        }
        val state = GameState.new(Puzzle(givens, solution), Difficulty.EASY)
        val selection = SelectionState(selectedDigit = 4)

        val (_, staySelection) = selection.onCellTap(state, lastFourIndex, autoAdvanceDigit = false)
        assertEquals(4, staySelection.selectedDigit)

        val (afterFour, nextSelection) = selection.onCellTap(state, lastFourIndex, autoAdvanceDigit = true)
        assertTrue(afterFour.isDigitComplete(4))
        assertTrue(afterFour.isDigitComplete(5))
        assertFalse(afterFour.isDigitComplete(6))
        assertEquals(6, nextSelection.selectedDigit)

        val (afterSix, finalSelection) = nextSelection.onCellTap(afterFour, lastSixIndex, autoAdvanceDigit = true)
        assertEquals(GameStatus.WON, afterSix.status)
        assertNull(finalSelection.selectedDigit)
    }

    @Test fun disablingLivesPreventsLifeLossAndAllowsPlayAtZeroLives() {
        val noLifeLoss = game().place(1, 9, livesEnabled = false).place(1, 8, livesEnabled = false)
        assertEquals(3, noLifeLoss.lives)
        assertEquals(GameStatus.PLAYING, noLifeLoss.status(livesEnabled = false))

        val lostWithLives = game().place(1, 9).place(1, 8).place(1, 7)
        assertEquals(GameStatus.LOST, lostWithLives.status(livesEnabled = true))
        assertEquals(GameStatus.PLAYING, lostWithLives.status(livesEnabled = false))

        val recoveredMove = lostWithLives.place(1, 3, livesEnabled = false)
        assertEquals(3, recoveredMove.values[1])
    }

    @Test fun appSettingsRoundTripPreservesTogglesSoundVolumeCustomPaletteAndSavedThemes() {
        val customPalette = ThemePalette()
            .withColor(ColorSlot.ACCENT, 0xFF64B5F6)
            .withColor(ColorSlot.BACKGROUND, 0xFF000000)
            .withColor(ColorSlot.COMPLETED_DIGIT_SURFACE, 0xFF101925)
            .withColor(ColorSlot.COMPLETED_DIGIT_TEXT, 0xFF465C75)
        val settings = AppSettings(
            livesEnabled = false,
            autoAdvanceDigit = false,
            hapticsEnabled = false,
            soundVolume = 0.35f,
            palette = customPalette
        ).saveCustomTheme("Midnight Blue")

        val restored = AppSettings.decode(settings.encode())
        assertEquals(settings, restored)
        assertEquals(0.35f, restored.soundVolume, 0.001f)
        assertEquals("Midnight Blue", restored.activeThemeName())
        assertTrue(restored.customThemes.any { it.name == "Midnight Blue" && it.palette == customPalette })

        // Verify v2 backwards compatibility
        val v2Encoded = "2|0|1|0|${customPalette.encodeColors()}|Midnight Blue;${customPalette.encodeColors()}"
        val v2Restored = AppSettings.decode(v2Encoded)
        assertFalse(v2Restored.livesEnabled)
        assertTrue(v2Restored.autoAdvanceDigit)
        assertFalse(v2Restored.hapticsEnabled)
        assertEquals(0.6f, v2Restored.soundVolume, 0.001f)
        assertEquals(customPalette, v2Restored.palette)

        val afterDelete = restored.deleteCustomTheme("Midnight Blue")
        assertTrue(afterDelete.customThemes.isEmpty())
        assertNull(afterDelete.activeThemeName())
        assertEquals(AppSettings(), AppSettings.decode("invalid"))
    }

    @Test fun hexColorParsingAndFormattingRoundTrips() {
        val parsed = ThemePalette.parseHex("#4A6B82")
        assertEquals(0xFF4A6B82L, parsed)
        assertEquals("4A6B82", ThemePalette.formatHex(parsed!!))
        assertNull(ThemePalette.parseHex("ZZZZZZ"))
    }

    @Test fun pencilDragFlipsNotesAlongPathAndGroupsIntoOneUndoStep() {
        val start = game().toggleNote(1, 3) // cell 1 already has note 3; cell 2 and 3 do not
        val selection = SelectionState(selectedDigit = 3, pencil = true)

        val (step1, sel1) = selection.onPencilDragCell(start, index = 1, firstCellInGesture = true)
        assertFalse(3 in step1.notes[1])
        assertEquals(1, sel1.selectedCell)

        val (step2, sel2) = sel1.onPencilDragCell(step1, index = 2, firstCellInGesture = false)
        assertTrue(3 in step2.notes[2])
        assertEquals(2, sel2.selectedCell)

        val (step3, sel3) = sel2.onPencilDragCell(step2, index = 3, firstCellInGesture = false)
        assertTrue(3 in step3.notes[3])
        assertEquals(3, sel3.selectedCell)

        // One undo restores the entire drag gesture back to `start`
        val undone = step3.undo()
        assertEquals(start.notes, undone.notes)
    }

    @Test fun hiddenMistakesCostNoLifeAndDoNotLeakThroughDisplayOrProgress() {
        val wrong = game().place(1, 9, hideMistakes = true)
        assertEquals(3, wrong.lives)
        assertFalse(wrong.showsMistake(1, hideMistakes = true))
        assertTrue(wrong.showsMistake(1, hideMistakes = false))
        assertEquals(2, wrong.progressCount(hideMistakes = true))
        assertEquals(1, wrong.progressCount(hideMistakes = false))
        assertEquals(1, wrong.digitCount(9, hideMistakes = true))
        assertEquals(0, wrong.digitCount(9, hideMistakes = false))
    }

    @Test fun checkRevealsMistakesChargesEachNewOneOnceAndEditsHideThem() {
        val start = game().place(1, 9, hideMistakes = true).place(2, 8, hideMistakes = true).place(3, 6, hideMistakes = true)
        val checked = start.checkMistakes()
        assertEquals(1, checked.lives)
        assertEquals(mapOf(1 to 9, 2 to 8), checked.revealedMistakes)
        assertTrue(checked.showsMistake(1, hideMistakes = true))
        assertFalse(checked.showsMistake(3, hideMistakes = true))
        assertEquals(start.history, checked.history)
        // Re-checking the same mistakes is free.
        assertEquals(1, checked.checkMistakes().lives)
        // Changing a revealed cell hides its mark; a new wrong value there costs a life on the next check.
        val edited = checked.place(1, 7, hideMistakes = true)
        assertFalse(edited.showsMistake(1, hideMistakes = true))
        val rechecked = edited.checkMistakes()
        assertEquals(0, rechecked.lives)
        assertEquals(GameStatus.LOST, rechecked.status)
        // Without lives, checks only reveal.
        assertEquals(3, start.checkMistakes(livesEnabled = false).lives)
    }

    @Test fun hiddenFastCompleteFillsOnlyEmptyCellsAndKeepsWrongEntries() {
        val nearly = solution.mapIndexed { i, n -> if (i in 70..80) 0 else n }
        val start = GameState.new(Puzzle(nearly, solution), Difficulty.EASY)
            .place(70, if (solution[70] == 1) 2 else 1, hideMistakes = true).place(71, solution[71], hideMistakes = true)
        assertEquals(9, start.fastCompleteCount(hideMistakes = true))
        assertTrue(start.canFastComplete(livesEnabled = true, hideMistakes = true))
        val done = start.fastComplete(hideMistakes = true)
        assertEquals(0, done.emptyCount)
        assertEquals(start.values[70], done.values[70])
        assertEquals(GameStatus.PLAYING, done.status)
        // Normal mode counts the wrong entry as remaining (10), so fast complete is not offered.
        assertEquals(10, start.fastCompleteCount())
        assertFalse(start.canFastComplete(livesEnabled = true))
    }

    @Test fun hiddenModeAutoAdvanceCountsPlacementsNotCorrectness() {
        val oneLeft = solution.mapIndexed { i, n -> if (n == 9 && i != 80) n else 0 }
        val board = GameState.new(Puzzle(oneLeft, solution), Difficulty.EASY)
        val wrongCell = board.values.indices.first { board.values[it] == 0 && solution[it] != 9 && it != 80 }
        val (next, selection) = SelectionState(selectedDigit = 9).onCellTap(board, wrongCell, hideMistakes = true)
        assertEquals(3, next.lives)
        assertTrue(next.isDigitComplete(9, hideMistakes = true))
        assertEquals(1, selection.selectedDigit)
    }

    @Test fun bestTimeResetClearsOnlyRedefinedTiersOnce() {
        assertEquals(listOf("best_EXPERT", "best_EXTREME", "best_DIABOLICAL"), BestTimes.keysToReset(1))
        assertTrue(BestTimes.keysToReset(BestTimes.CURRENT_VERSION).isEmpty())
        assertEquals("best_DIABOLICAL_PLUS", BestTimes.key(Difficulty.DIABOLICAL_PLUS))
    }

    @Test fun cellColoringAndLinesCanBeToggledAndUndone() {
        val start = game()
        val colored = start.setColor(10, 3)
        assertEquals(mapOf(10 to 3), colored.cellColors)
        val toggledSameColor = colored.setColor(10, 3)
        assertEquals(emptyMap<Int, Int>(), toggledSameColor.cellColors)

        val withLine = start.toggleLine(10, 20, LineStyle.SOLID)
        assertEquals(setOf(BoardLine(10, 20, LineStyle.SOLID)), withLine.lines)
        val styleSwapped = withLine.toggleLine(20, 10, LineStyle.DOTTED)
        assertEquals(setOf(BoardLine(10, 20, LineStyle.DOTTED)), styleSwapped.lines)
        val toggledOff = styleSwapped.toggleLine(10, 20, LineStyle.DOTTED)
        assertEquals(emptySet<BoardLine>(), toggledOff.lines)

        // Undo / redo on coloring & lines
        val step1 = start.setColor(5, 1)
        val step2 = step1.toggleLine(5, 6, LineStyle.SOLID)
        assertEquals(step1.cellColors, step2.undo().cellColors)
        assertEquals(step1.lines, step2.undo().lines)
        assertEquals(step2.cellColors, step2.undo().redo().cellColors)
        assertEquals(step2.lines, step2.undo().redo().lines)

        // Clear cell annotations
        val cleared = step2.clearCellAnnotations(5)
        assertEquals(emptyMap<Int, Int>(), cleared.cellColors)
        assertEquals(emptySet<BoardLine>(), cleared.lines)

        // Clear all annotations
        val multiple = start.setColor(1, 2).setColor(2, 3).toggleLine(1, 2, LineStyle.SOLID)
        val clearedAll = multiple.clearAllAnnotations()
        assertEquals(emptyMap<Int, Int>(), clearedAll.cellColors)
        assertEquals(emptySet<BoardLine>(), clearedAll.lines)
        assertEquals(multiple.cellColors, clearedAll.undo().cellColors)
        assertEquals(multiple.lines, clearedAll.undo().lines)

        // Clear all cell colors only
        val clearedColorsOnly = multiple.clearAllCellColors()
        assertEquals(emptyMap<Int, Int>(), clearedColorsOnly.cellColors)
        assertEquals(multiple.lines, clearedColorsOnly.lines)
        assertEquals(multiple.cellColors, clearedColorsOnly.undo().cellColors)

        // Clear all lines only
        val clearedLinesOnly = multiple.clearAllLines()
        assertEquals(multiple.cellColors, clearedLinesOnly.cellColors)
        assertEquals(emptySet<BoardLine>(), clearedLinesOnly.lines)
        assertEquals(multiple.lines, clearedLinesOnly.undo().lines)
    }

    @Test fun lineModePairSelectionConnectsAndResetsSelectionForNextLine() {
        val start = game()
        val lineMode = SelectionState(mode = SelectionMode.LINE)

        // Tap first cell (start of line)
        val (g1, s1) = lineMode.onCellTap(start, 10)
        assertEquals(emptySet<BoardLine>(), g1.lines)
        assertEquals(10, s1.selectedCell)

        // Tapping the same cell cancels/deselects start cell
        val (gCancel, sCancel) = s1.onCellTap(g1, 10)
        assertEquals(emptySet<BoardLine>(), gCancel.lines)
        assertNull(sCancel.selectedCell)

        // Tap start cell again, then tap end cell
        val (_, sStart) = lineMode.onCellTap(start, 10)
        assertEquals(10, sStart.selectedCell)
        val (gLine, sEnd) = sStart.onCellTap(start, 20)
        assertEquals(setOf(BoardLine(10, 20, LineStyle.SOLID)), gLine.lines)
        // Selection is reset to null so the next tap starts a new line instead of chaining
        assertNull(sEnd.selectedCell)

        // Next tap starts an independent line at cell 30
        val (g3, s3) = sEnd.onCellTap(gLine, 30)
        assertEquals(gLine.lines, g3.lines)
        assertEquals(30, s3.selectedCell)
        val (g4, s4) = s3.onCellTap(g3, 40)
        assertEquals(setOf(BoardLine(10, 20, LineStyle.SOLID), BoardLine(30, 40, LineStyle.SOLID)), g4.lines)
        assertNull(s4.selectedCell)
    }

    @Test fun settingsKeepHideMistakesOffByDefaultAndRoundTripIt() {
        assertFalse(AppSettings().hideMistakesUntilChecked)
        assertTrue(AppSettings().keepScreenOn)
        val on = AppSettings(hideMistakesUntilChecked = true, keepScreenOn = false)
        assertEquals(on, AppSettings.decode(on.encode()))
        val v3 = AppSettings().encode().replaceFirst("5|", "3|").substringBeforeLast("|").substringBeforeLast("|")
        assertFalse(AppSettings.decode(v3).hideMistakesUntilChecked)
        assertTrue(AppSettings.decode(v3).keepScreenOn)
        assertEquals(AppSettings(), AppSettings.decode(v3))
        val v4 = AppSettings().encode().replaceFirst("5|", "4|").substringBeforeLast("|")
        assertTrue(AppSettings.decode(v4).keepScreenOn)
    }
}
