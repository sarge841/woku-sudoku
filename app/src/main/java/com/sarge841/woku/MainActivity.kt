package com.sarge841.woku

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private fun ThemePalette.color(slot: ColorSlot) = Color(this[slot])

private fun View.tickHaptic(enabled: Boolean, strong: Boolean = false) {
    if (!enabled) return
    val constant = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
    performHapticFeedback(constant)
}

private enum class SoundEffect {
    CORRECT_PLACE,
    DIGIT_COMPLETE,
    NOTE_TOGGLE,
    MISTAKE,
    VICTORY
}

private object SoundEngine {
    private const val SAMPLE_RATE = 44100
    private val executor = Executors.newSingleThreadExecutor()

    private val correctPlacePcm by lazy {
        synthesizeSequence(
            ToneSegment(freqStart = 587.33, freqEnd = 659.25, durationMs = 68, amplitude = 0.28, decay = 9.0)
        )
    }

    private val digitCompletePcm by lazy {
        synthesizeSequence(
            ToneSegment(freqStart = 587.33, durationMs = 65, amplitude = 0.26, decay = 8.0),
            ToneSegment(freqStart = 880.00, durationMs = 115, amplitude = 0.30, decay = 6.5)
        )
    }

    private val noteTogglePcm by lazy {
        synthesizeSequence(
            ToneSegment(freqStart = 460.0, durationMs = 28, amplitude = 0.15, decay = 14.0)
        )
    }

    private val mistakePcm by lazy {
        synthesizeSequence(
            ToneSegment(freqStart = 210.0, freqEnd = 165.0, durationMs = 115, amplitude = 0.24, decay = 6.0)
        )
    }

    private val victoryPcm by lazy {
        synthesizeSequence(
            ToneSegment(freqStart = 523.25, durationMs = 95, amplitude = 0.30, decay = 5.5),
            ToneSegment(freqStart = 659.25, durationMs = 95, amplitude = 0.30, decay = 5.5),
            ToneSegment(freqStart = 783.99, durationMs = 95, amplitude = 0.32, decay = 5.5),
            ToneSegment(freqStart = 1046.50, durationMs = 280, amplitude = 0.35, decay = 3.2)
        )
    }

    private data class ToneSegment(
        val freqStart: Double,
        val freqEnd: Double = freqStart,
        val durationMs: Int,
        val amplitude: Double,
        val decay: Double
    )

    private fun synthesizeSequence(vararg segments: ToneSegment): ShortArray {
        val totalSamples = segments.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
        val output = ShortArray(totalSamples)
        var offset = 0
        for (seg in segments) {
            val count = (SAMPLE_RATE * seg.durationMs) / 1000
            val attackSamples = (SAMPLE_RATE * 0.004).toInt().coerceAtLeast(1)
            var phase = 0.0
            for (i in 0 until count) {
                val progress = i.toDouble() / count.coerceAtLeast(1)
                val freq = seg.freqStart + (seg.freqEnd - seg.freqStart) * progress
                phase += 2.0 * PI * freq / SAMPLE_RATE
                val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
                val envelope = attack * exp(-seg.decay * progress)
                val wave = sin(phase) + 0.18 * sin(phase * 2.0)
                val sample = (wave * seg.amplitude * envelope * Short.MAX_VALUE).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output[offset + i] = sample.toShort()
            }
            offset += count
        }
        return output
    }

    fun play(effect: SoundEffect, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        if (clamped <= 0.01f) return
        executor.execute {
            runCatching {
                val pcm = when (effect) {
                    SoundEffect.CORRECT_PLACE -> correctPlacePcm
                    SoundEffect.DIGIT_COMPLETE -> digitCompletePcm
                    SoundEffect.NOTE_TOGGLE -> noteTogglePcm
                    SoundEffect.MISTAKE -> mistakePcm
                    SoundEffect.VICTORY -> victoryPcm
                }
                val byteCount = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(byteCount)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(clamped)
                track.play()
                val durationMs = (pcm.size * 1000L) / SAMPLE_RATE + 50L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("woku", MODE_PRIVATE)
        val storedBestVersion = prefs.getInt(BestTimes.VERSION_KEY, 1)
        if (storedBestVersion < BestTimes.CURRENT_VERSION) {
            prefs.edit().apply {
                BestTimes.keysToReset(storedBestVersion).forEach(::remove)
                putInt(BestTimes.VERSION_KEY, BestTimes.CURRENT_VERSION)
            }.apply()
        }
        setContent {
            val view = LocalView.current
            val scope = rememberCoroutineScope()
            var settings by remember {
                mutableStateOf(AppSettings.decode(prefs.getString("settings", "") ?: ""))
            }
            var game by remember {
                mutableStateOf(
                    SaveCodec.decode(prefs.getString("game", "") ?: "")
                        ?: GameState.new(Sudoku.generate(Difficulty.EASY), Difficulty.EASY)
                )
            }
            var selection by remember { mutableStateOf(SelectionState()) }
            var showNew by remember { mutableStateOf(false) }
            var showDifficultyGuide by remember { mutableStateOf(false) }
            var showBest by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var showAppearance by remember { mutableStateOf(false) }
            var pendingNew by remember { mutableStateOf<Difficulty?>(null) }
            var generatingDifficulty by remember { mutableStateOf<Difficulty?>(null) }
            var checkMessage by remember { mutableStateOf<String?>(null) }

            val palette = settings.palette
            val bgColor = palette.color(ColorSlot.BACKGROUND)
            val surfaceColor = palette.color(ColorSlot.SURFACE)
            val inkColor = palette.color(ColorSlot.INK)
            val mutedColor = palette.color(ColorSlot.MUTED)
            val accentColor = palette.color(ColorSlot.ACCENT)
            val dangerColor = palette.color(ColorSlot.DANGER)

            SideEffect {
                val bgArgb = palette[ColorSlot.BACKGROUND].toInt()
                window.statusBarColor = bgArgb
                window.navigationBarColor = bgArgb
                if (settings.keepScreenOn) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            fun updateSettings(next: AppSettings) {
                settings = next
                prefs.edit().putString("settings", next.encode()).apply()
            }

            fun update(next: GameState) {
                val wasWon = game.status(settings.livesEnabled) == GameStatus.WON
                val isWon = next.status(settings.livesEnabled) == GameStatus.WON
                if (!wasWon && isWon) {
                    val key = BestTimes.key(next.difficulty)
                    val old = prefs.getInt(key, Int.MAX_VALUE)
                    if (next.elapsedSeconds < old) prefs.edit().putInt(key, next.elapsedSeconds).apply()
                    SoundEngine.play(SoundEffect.VICTORY, settings.soundVolume)
                }
                if (next.values != game.values || next.puzzle != game.puzzle) checkMessage = null
                game = next
                prefs.edit().putString("game", SaveCodec.encode(next)).apply()
            }

            val currentStatus = game.status(settings.livesEnabled)
            LaunchedEffect(currentStatus, settings.livesEnabled) {
                while (game.status(settings.livesEnabled) == GameStatus.PLAYING) {
                    delay(1000)
                    update(game.tick(settings.livesEnabled))
                }
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = accentColor,
                    background = bgColor,
                    surface = surfaceColor,
                    onSurface = inkColor,
                    onBackground = inkColor,
                    error = dangerColor
                )
            ) {
                SudokuScreen(
                    game = game,
                    settings = settings,
                    selectedCell = selection.selectedCell,
                    selectedDigit = selection.selectedDigit,
                    pencil = selection.pencil,
                    viewOnly = selection.viewOnly,
                    best = prefs.getInt(BestTimes.key(game.difficulty), Int.MAX_VALUE).takeUnless { it == Int.MAX_VALUE },
                    selection = selection,
                    onSelectionChange = { selection = it },
                    onCell = { index ->
                        val hide = settings.hideMistakesUntilChecked
                        val prevGame = game
                        val placedDigit = selection.selectedDigit
                        val (nextGame, nextSelection) = selection.onCellTap(
                            game = prevGame,
                            index = index,
                            livesEnabled = settings.livesEnabled,
                            autoAdvanceDigit = settings.autoAdvanceDigit,
                            hideMistakes = hide
                        )
                        // With hidden mistakes, sounds/haptics must not reveal whether a placement was right.
                        val mistake = !hide && (nextGame.lives < prevGame.lives ||
                            (nextGame.values != prevGame.values && nextGame.values[index] != 0 && nextGame.values[index] != nextGame.puzzle.solution[index]))
                        selection = nextSelection
                        if (nextGame != prevGame) {
                            update(nextGame)
                            if (nextGame.status(settings.livesEnabled) != GameStatus.WON) {
                                when {
                                    mistake -> SoundEngine.play(SoundEffect.MISTAKE, settings.soundVolume)
                                    nextGame.values != prevGame.values && placedDigit != null && nextGame.isDigitComplete(placedDigit, hide) ->
                                        SoundEngine.play(SoundEffect.DIGIT_COMPLETE, settings.soundVolume)
                                    nextGame.values != prevGame.values && nextGame.values[index] != 0 &&
                                        (hide || nextGame.values[index] == nextGame.puzzle.solution[index]) ->
                                        SoundEngine.play(SoundEffect.CORRECT_PLACE, settings.soundVolume)
                                    nextGame.notes != prevGame.notes ->
                                        SoundEngine.play(SoundEffect.NOTE_TOGGLE, settings.soundVolume)
                                }
                            }
                        }
                        view.tickHaptic(settings.hapticsEnabled, strong = mistake)
                    },
                    onPencilDragCell = { index, firstInGesture ->
                        val (nextGame, nextSelection) = selection.onPencilDragCell(
                            game = game,
                            index = index,
                            firstCellInGesture = firstInGesture,
                            livesEnabled = settings.livesEnabled
                        )
                        val changed = nextGame != game
                        selection = nextSelection
                        if (changed) {
                            update(nextGame)
                            SoundEngine.play(SoundEffect.NOTE_TOGGLE, settings.soundVolume)
                            view.tickHaptic(settings.hapticsEnabled)
                        }
                        changed
                    },
                    onDigit = { digit ->
                        selection = selection.onDigitTap(digit)
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onDigitLongPress = { digit ->
                        val next = selection.onDigitLongPress(digit)
                        if (next != selection) {
                            selection = next
                            view.tickHaptic(settings.hapticsEnabled, strong = true)
                        }
                    },
                    onPencil = {
                        selection = selection.togglePencil()
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onViewOnly = {
                        selection = selection.toggleViewOnly()
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onFastPencil = {
                        update(game.fastPencil(settings.livesEnabled))
                        SoundEngine.play(SoundEffect.NOTE_TOGGLE, settings.soundVolume)
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onFastComplete = {
                        update(game.fastComplete(settings.livesEnabled, settings.hideMistakesUntilChecked))
                        view.tickHaptic(settings.hapticsEnabled, strong = true)
                    },
                    checkMessage = checkMessage,
                    onCheck = {
                        val checked = game.checkMistakes(settings.livesEnabled)
                        val found = checked.revealedMistakes.size
                        update(checked)
                        checkMessage = when (found) {
                            0 -> "NO MISTAKES SO FAR"
                            1 -> "1 MISTAKE FOUND"
                            else -> "$found MISTAKES FOUND"
                        }
                        if (checked.status(settings.livesEnabled) == GameStatus.PLAYING) {
                            SoundEngine.play(if (found > 0) SoundEffect.MISTAKE else SoundEffect.CORRECT_PLACE, settings.soundVolume)
                        }
                        view.tickHaptic(settings.hapticsEnabled, strong = found > 0)
                    },
                    onErase = {
                        val cell = selection.selectedCell
                        if (cell != null) {
                            if (selection.mode == SelectionMode.COLOR) {
                                update(game.setColor(cell, 0, settings.livesEnabled))
                            } else if (selection.mode == SelectionMode.LINE) {
                                update(game.clearCellAnnotations(cell, settings.livesEnabled))
                            } else {
                                update(game.place(cell, 0, settings.livesEnabled))
                            }
                            view.tickHaptic(settings.hapticsEnabled)
                        }
                    },
                    onClearColors = {
                        update(game.clearAllCellColors(settings.livesEnabled))
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onClearLines = {
                        update(game.clearAllLines(settings.livesEnabled))
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onUndo = {
                        update(game.undo(settings.livesEnabled))
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onRedo = {
                        update(game.redo(settings.livesEnabled))
                        view.tickHaptic(settings.hapticsEnabled)
                    },
                    onNew = { showNew = true },
                    onBest = { showBest = true },
                    onSettings = { showSettings = true }
                )
                if (showNew) AlertDialog(
                    onDismissRequest = { showNew = false },
                    title = {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("New puzzle", color = inkColor)
                            IconButton(
                                onClick = { showDifficultyGuide = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = "Difficulty guide",
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    text = {
                        Column {
                            Difficulty.entries.forEach { level ->
                                TextButton(
                                    onClick = { showNew = false; pendingNew = level },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(level.label, color = inkColor) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showNew = false }) { Text("Cancel", color = accentColor) } }
                )
                if (showDifficultyGuide) DifficultyGuideDialog(
                    palette = palette,
                    onDismiss = { showDifficultyGuide = false }
                )
                pendingNew?.let { level ->
                    AlertDialog(
                        onDismissRequest = { pendingNew = null },
                        title = { Text("Start ${level.label}?") },
                        text = { Text("This replaces your current puzzle. Best times stay saved.") },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingNew = null
                                generatingDifficulty = level
                                scope.launch {
                                    val generated = withContext(Dispatchers.Default) {
                                        Sudoku.generate(level)
                                    }
                                    update(GameState.new(generated, level))
                                    selection = selection.copy(selectedCell = null, selectedDigit = null)
                                    generatingDifficulty = null
                                }
                            }) { Text("Start") }
                        },
                        dismissButton = { TextButton(onClick = { pendingNew = null }) { Text("Cancel") } }
                    )
                }
                generatingDifficulty?.let { level ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Generating puzzle", color = inkColor) },
                        text = {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    "Creating a unique ${level.label} board…",
                                    color = mutedColor,
                                    fontSize = 14.sp
                                )
                            }
                        },
                        confirmButton = {}
                    )
                }
                if (showBest) AlertDialog(
                    onDismissRequest = { showBest = false },
                    title = { Text("Best times") },
                    text = {
                        Column {
                            Difficulty.entries.forEach { level ->
                                val seconds = prefs.getInt(BestTimes.key(level), Int.MAX_VALUE)
                                Text(
                                    "${level.label}  ·  ${if (seconds == Int.MAX_VALUE) "—" else formatTime(seconds)}",
                                    color = inkColor,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showBest = false }) { Text("Close") } }
                )
                if (showSettings) SettingsDialog(
                    settings = settings,
                    onUpdate = { next ->
                        updateSettings(next)
                        view.tickHaptic(next.hapticsEnabled)
                    },
                    onOpenAppearance = {
                        showSettings = false
                        showAppearance = true
                    },
                    onDismiss = { showSettings = false }
                )
                if (showAppearance) AppearanceDialog(
                    settings = settings,
                    onUpdate = { next ->
                        updateSettings(next)
                        view.tickHaptic(next.hapticsEnabled)
                    },
                    onBack = {
                        showAppearance = false
                        showSettings = true
                    },
                    onDismiss = { showAppearance = false }
                )
            }
        }
    }
}

private fun formatTime(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SudokuScreen(
    game: GameState,
    settings: AppSettings,
    selection: SelectionState,
    onSelectionChange: (SelectionState) -> Unit,
    selectedCell: Int?,
    selectedDigit: Int?,
    pencil: Boolean,
    viewOnly: Boolean,
    best: Int?,
    onCell: (Int) -> Unit,
    onPencilDragCell: (Int, Boolean) -> Boolean,
    onDigit: (Int) -> Unit,
    onDigitLongPress: (Int) -> Unit,
    onPencil: () -> Unit,
    onViewOnly: () -> Unit,
    onFastPencil: () -> Unit,
    onFastComplete: () -> Unit,
    checkMessage: String?,
    onCheck: () -> Unit,
    onErase: () -> Unit,
    onClearColors: () -> Unit,
    onClearLines: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onNew: () -> Unit,
    onBest: () -> Unit,
    onSettings: () -> Unit
) {
    val palette = settings.palette
    val bgColor = palette.color(ColorSlot.BACKGROUND)
    val surfaceColor = palette.color(ColorSlot.SURFACE)
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val accentColor = palette.color(ColorSlot.ACCENT)
    val dangerColor = palette.color(ColorSlot.DANGER)
    val status = game.status(settings.livesEnabled)
    val playing = status == GameStatus.PLAYING
    val hideMistakes = settings.hideMistakesUntilChecked

    Column(
        Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WOKU", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = inkColor, letterSpacing = 3.sp)
                Text(game.difficulty.label, color = mutedColor, fontSize = 13.sp)
            }
            TextButton(onClick = onBest, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Records", color = accentColor) }
            TextButton(onClick = onNew, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("New", color = accentColor) }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = accentColor)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${game.progressCount(hideMistakes)}/81", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("TIME  ${formatTime(game.elapsedSeconds)}", color = inkColor, fontSize = 14.sp)
            }
            Text("BEST  ${best?.let(::formatTime) ?: "—"}", color = mutedColor, fontSize = 14.sp)
            if (settings.livesEnabled) {
                Text("♥".repeat(game.lives) + "♡".repeat(3 - game.lives), color = dangerColor, fontSize = 18.sp)
            } else {
                Text("∞", color = mutedColor, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SudokuGrid(
            game = game,
            palette = palette,
            selectedCell = selectedCell,
            selectedDigit = selectedDigit,
            selectionMode = selection.mode,
            pencilDragEnabled = pencil && !viewOnly && selectedDigit != null && playing && selection.mode == SelectionMode.DIGIT,
            hideMistakes = hideMistakes,
            onCell = onCell,
            onPencilDragCell = onPencilDragCell
        )
        Spacer(Modifier.height(14.dp))
        if (!playing) {
            Text(
                if (status == GameStatus.WON) "Puzzle complete!" else "No lives left — try another puzzle",
                color = if (status == GameStatus.WON) accentColor else dangerColor,
                fontSize = 16.sp
            )
        } else {
            Text(
                when {
                    hideMistakes && checkMessage != null -> checkMessage
                    hideMistakes && game.emptyCount == 0 -> "BOARD FULL  ·  TAP CHECK TO FIND MISTAKES"
                    viewOnly -> "VIEW ONLY MODE"
                    selection.mode == SelectionMode.COLOR -> "COLOR MODE  ·  TAP CELLS TO TINT WITH CIRCLE"
                    selection.mode == SelectionMode.LINE ->
                        if (selection.selectedCell == null) "LINE MODE  ·  TAP FIRST CELL TO START LINE"
                        else "LINE MODE  ·  TAP SECOND CELL TO COMPLETE LINE"
                    pencil -> "PENCIL MODE  ·  TAP OR DRAG TO TOGGLE NOTES"
                    else -> "SELECT A NUMBER THEN TAP A CELL"
                },
                color = when {
                    hideMistakes && (checkMessage != null || game.emptyCount == 0) ->
                        if (game.revealedMistakes.isEmpty() && checkMessage != null) accentColor else dangerColor
                    viewOnly || pencil || selection.mode != SelectionMode.DIGIT -> accentColor
                    else -> mutedColor
                },
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        val completedDigitSurface = palette.color(ColorSlot.COMPLETED_DIGIT_SURFACE)
        val completedDigitText = palette.color(ColorSlot.COMPLETED_DIGIT_TEXT)
        when (selection.mode) {
            SelectionMode.DIGIT -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (1..9).forEach { digit ->
                        val active = selectedDigit == digit
                        val completed = game.isDigitComplete(digit, hideMistakes)
                        val remaining = (9 - game.digitCount(digit, hideMistakes)).coerceAtLeast(0)
                        val buttonBg = when {
                            active -> accentColor
                            completed -> completedDigitSurface
                            else -> surfaceColor
                        }
                        val mainTextColor = when {
                            active -> bgColor
                            completed -> completedDigitText
                            else -> inkColor
                        }
                        val subTextColor = when {
                            active -> bgColor
                            completed -> completedDigitText
                            else -> mutedColor
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .background(buttonBg, RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = { onDigit(digit) },
                                    onLongClick = { onDigitLongPress(digit) }
                                )
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$digit", color = mainTextColor, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                            Text("$remaining", color = subTextColor, fontSize = 10.sp)
                        }
                    }
                }
            }
            SelectionMode.COLOR -> {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ANNOTATION_COLORS.forEachIndexed { i, colorValue ->
                        val colorIdx = i + 1
                        val active = selection.selectedColor == colorIdx
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(surfaceColor)
                                .clickable { onSelectionChange(selection.copy(selectedColor = colorIdx)) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorValue))
                                    .border(
                                        if (active) 2.5.dp else 0.dp,
                                        if (active) inkColor else Color.Transparent,
                                        CircleShape
                                    )
                            )
                        }
                    }
                    ActionButton(
                        label = "Clear",
                        enabled = game.cellColors.isNotEmpty() && playing,
                        action = onClearColors,
                        modifier = Modifier.weight(1.8f),
                        palette = palette
                    )
                }
            }
            SelectionMode.LINE -> {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        label = "Solid Line",
                        enabled = true,
                        action = { onSelectionChange(selection.copy(lineStyle = LineStyle.SOLID)) },
                        modifier = Modifier.weight(1.2f),
                        palette = palette,
                        active = selection.lineStyle == LineStyle.SOLID
                    )
                    ActionButton(
                        label = "Dotted Line",
                        enabled = true,
                        action = { onSelectionChange(selection.copy(lineStyle = LineStyle.DOTTED)) },
                        modifier = Modifier.weight(1.2f),
                        palette = palette,
                        active = selection.lineStyle == LineStyle.DOTTED
                    )
                    ActionButton(
                        label = "Clear",
                        enabled = game.lines.isNotEmpty() && playing,
                        action = onClearLines,
                        modifier = Modifier.weight(1.2f),
                        palette = palette
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton("Undo", game.history.isNotEmpty() && playing, onUndo, Modifier.weight(1f), palette)
            ActionButton("Redo", game.future.isNotEmpty() && playing, onRedo, Modifier.weight(1f), palette)
            ActionButton("Erase", !viewOnly && selectedCell != null && playing, onErase, Modifier.weight(1f), palette)
            ActionButton("Color", playing, { onSelectionChange(selection.setMode(SelectionMode.COLOR)) }, Modifier.weight(1f), palette, active = selection.mode == SelectionMode.COLOR)
            ActionButton("Line", playing, { onSelectionChange(selection.setMode(SelectionMode.LINE)) }, Modifier.weight(1f), palette, active = selection.mode == SelectionMode.LINE)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(if (viewOnly) "View ON" else "View only", playing, onViewOnly, Modifier.weight(1f), palette, active = viewOnly)
            ActionButton(if (pencil) "Pencil ON" else "Pencil", playing, onPencil, Modifier.weight(1f), palette, active = pencil)
            ActionButton("Fast pencil", !viewOnly && playing, onFastPencil, Modifier.weight(1f), palette)
        }
        if (hideMistakes) {
            Spacer(Modifier.height(6.dp))
            ActionButton("Check for mistakes", playing, onCheck, Modifier.fillMaxWidth(), palette)
        }
        if (game.canFastComplete(settings.livesEnabled, hideMistakes)) {
            Spacer(Modifier.height(6.dp))
            ActionButton("Fast complete (${game.fastCompleteCount(hideMistakes)} left)", true, onFastComplete, Modifier.fillMaxWidth(), palette, active = true)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    action: () -> Unit,
    modifier: Modifier,
    palette: ThemePalette,
    active: Boolean = false
) {
    val accentColor = palette.color(ColorSlot.ACCENT)
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val borderColor = palette.color(ColorSlot.CELL_BORDER)
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = modifier.height(43.dp),
        contentPadding = PaddingValues(horizontal = 3.dp),
        border = BorderStroke(1.dp, if (active) accentColor else borderColor)
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1, color = if (active) accentColor else if (enabled) inkColor else mutedColor)
    }
}

@Composable
private fun SudokuGrid(
    game: GameState,
    palette: ThemePalette,
    selectedCell: Int?,
    selectedDigit: Int?,
    selectionMode: SelectionMode,
    pencilDragEnabled: Boolean,
    hideMistakes: Boolean,
    onCell: (Int) -> Unit,
    onPencilDragCell: (Int, Boolean) -> Boolean
) {
    val accentColor = palette.color(ColorSlot.ACCENT)
    val cellBorderColor = palette.color(ColorSlot.CELL_BORDER)
    val surfaceColor = palette.color(ColorSlot.SURFACE)
    val selectedCellColor = palette.color(ColorSlot.SELECTED_CELL)
    val peerHighlightColor = palette.color(ColorSlot.PEER_HIGHLIGHT)
    val matchingNumberColor = palette.color(ColorSlot.MATCHING_NUMBER)
    val noteHighlightColor = palette.color(ColorSlot.MATCHING_NOTE)
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val dangerColor = palette.color(ColorSlot.DANGER)
    val lineColor = palette.color(ColorSlot.ANNOTATION_LINE)

    val currentGame by rememberUpdatedState(game)
    val currentDragEnabled by rememberUpdatedState(pencilDragEnabled)
    val currentOnCell by rememberUpdatedState(onCell)
    val currentOnPencilDragCell by rememberUpdatedState(onPencilDragCell)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .border(2.dp, accentColor)
            .pointerInput(selectionMode, pencilDragEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun cellAt(pos: Offset): Int? {
                        if (size.width <= 0 || size.height <= 0) return null
                        if (pos.x < 0f || pos.y < 0f || pos.x >= size.width || pos.y >= size.height) return null
                        val col = (pos.x / (size.width / 9f)).toInt().coerceIn(0, 8)
                        val row = (pos.y / (size.height / 9f)).toInt().coerceIn(0, 8)
                        return row * 9 + col
                    }
                    val startCell = cellAt(down.position)
                    if (!currentDragEnabled) {
                        var upPosition = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            upPosition = change.position
                            if (!change.pressed) break
                        }
                        val endCell = cellAt(upPosition)
                        if (startCell != null && endCell == startCell) {
                            currentOnCell(startCell)
                        }
                    } else {
                        val visited = mutableSetOf<Int>()
                        var recordedFirst = false
                        var draggedOutsideStart = false
                        val startFilled = startCell != null && currentGame.values.getOrNull(startCell) != 0
                        if (startCell != null && !startFilled) {
                            visited.add(startCell)
                            if (currentOnPencilDragCell(startCell, true)) {
                                recordedFirst = true
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val cell = cellAt(change.position)
                            if (cell != null && cell != startCell) {
                                draggedOutsideStart = true
                            }
                            if (cell != null && cell !in visited) {
                                visited.add(cell)
                                if (currentGame.values.getOrNull(cell) == 0) {
                                    if (currentOnPencilDragCell(cell, !recordedFirst)) {
                                        recordedFirst = true
                                    }
                                }
                            }
                            change.consume()
                        }
                        if (startFilled && !draggedOutsideStart) {
                            currentOnCell(startCell)
                        }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val cellWidth = size.width / 9f
                val cellHeight = size.height / 9f
                val thinStroke = 0.6.dp.toPx()
                val thickStroke = 2.dp.toPx()
                for (i in 1..8) {
                    if (i % 3 != 0) {
                        val x = cellWidth * i
                        val y = cellHeight * i
                        drawLine(cellBorderColor, Offset(x, 0f), Offset(x, size.height), thinStroke)
                        drawLine(cellBorderColor, Offset(0f, y), Offset(size.width, y), thinStroke)
                    }
                }
                for (i in listOf(3, 6)) {
                    val x = cellWidth * i
                    val y = cellHeight * i
                    drawLine(accentColor, Offset(x, 0f), Offset(x, size.height), thickStroke)
                    drawLine(accentColor, Offset(0f, y), Offset(size.width, y), thickStroke)
                }
            }
    ) {
        val size = maxWidth / 9

        // Layer 1: Cell backgrounds (selection, peer highlights, and color circle annotations)
        Column {
            for (row in 0..8) Row {
                for (col in 0..8) {
                    val index = row * 9 + col
                    val value = game.values[index]
                    val chosen = selectedCell == index
                    val match = selectedDigit != null && value == selectedDigit
                    val peer = selectedCell != null && (row == selectedCell / 9 || col == selectedCell % 9 ||
                        (row / 3 == selectedCell / 27 && col / 3 == selectedCell % 9 / 3))
                    val shade = when {
                        chosen -> selectedCellColor
                        match -> matchingNumberColor
                        peer -> peerHighlightColor
                        else -> surfaceColor
                    }
                    val cellColorIdx = game.cellColors[index]
                    Box(
                        Modifier
                            .size(size)
                            .background(shade),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellColorIdx != null && cellColorIdx in 1..ANNOTATION_COLORS.size) {
                            Box(
                                Modifier
                                    .size(size * 0.85f)
                                    .clip(CircleShape)
                                    .background(Color(ANNOTATION_COLORS[cellColorIdx - 1]).copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }

        // Layer 2: Connecting lines (in the background behind numbers and notes)
        Canvas(Modifier.matchParentSize()) {
            val cellWidth = this.size.width / 9f
            val cellHeight = this.size.height / 9f
            for (line in currentGame.lines) {
                val fromRow = line.from / 9
                val fromCol = line.from % 9
                val toRow = line.to / 9
                val toCol = line.to % 9
                val startOffset = Offset((fromCol + 0.5f) * cellWidth, (fromRow + 0.5f) * cellHeight)
                val endOffset = Offset((toCol + 0.5f) * cellWidth, (toRow + 0.5f) * cellHeight)
                val strokeWidth = 2.5.dp.toPx()
                val pathEffect = if (line.style == LineStyle.DOTTED) {
                    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                } else null
                drawLine(
                    color = lineColor,
                    start = startOffset,
                    end = endOffset,
                    strokeWidth = strokeWidth,
                    pathEffect = pathEffect
                )
            }
        }

        // Layer 3: Cell content (numbers and notes displayed above connecting lines)
        Column {
            for (row in 0..8) Row {
                for (col in 0..8) {
                    val index = row * 9 + col
                    val value = game.values[index]
                    Box(
                        Modifier.size(size),
                        contentAlignment = Alignment.Center
                    ) {
                        if (value != 0) Text(
                            "$value",
                            color = when {
                                game.showsMistake(index, hideMistakes) -> dangerColor
                                game.puzzle.givens[index] != 0 -> inkColor
                                else -> accentColor
                            },
                            fontSize = 21.sp,
                            fontWeight = if (game.puzzle.givens[index] != 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                        else Column(Modifier.fillMaxSize().padding(1.dp)) {
                            for (r in 0..2) Row(Modifier.weight(1f)) {
                                for (c in 0..2) {
                                    val digit = r * 3 + c + 1
                                    val hasNote = digit in game.notes[index]
                                    val noteHighlighted = hasNote && selectedDigit == digit
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (noteHighlighted) noteHighlightColor else Color.Transparent, RoundedCornerShape(2.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (hasNote) Text(
                                            "$digit",
                                            color = if (noteHighlighted) inkColor else mutedColor,
                                            fontSize = 9.sp,
                                            lineHeight = 9.sp,
                                            fontWeight = if (noteHighlighted) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onOpenAppearance: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = settings.palette
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val accentColor = palette.color(ColorSlot.ACCENT)
    val themeName = settings.activeThemeName() ?: "Custom"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = inkColor) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Gameplay", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                SettingToggleRow(
                    title = "Three-life limit",
                    subtitle = "Mistakes cost a heart (turn off for unlimited lives)",
                    checked = settings.livesEnabled,
                    inkColor = inkColor,
                    mutedColor = mutedColor,
                    onCheckedChange = { onUpdate(settings.copy(livesEnabled = it)) }
                )
                SettingToggleRow(
                    title = "Hide mistakes until checked",
                    subtitle = "No red digits or lost hearts while playing; tap Check to reveal mistakes (each new one costs a heart)",
                    checked = settings.hideMistakesUntilChecked,
                    inkColor = inkColor,
                    mutedColor = mutedColor,
                    onCheckedChange = { onUpdate(settings.copy(hideMistakesUntilChecked = it)) }
                )
                SettingToggleRow(
                    title = "Auto-select next number",
                    subtitle = "Advance to next incomplete digit when all 9 are placed",
                    checked = settings.autoAdvanceDigit,
                    inkColor = inkColor,
                    mutedColor = mutedColor,
                    onCheckedChange = { onUpdate(settings.copy(autoAdvanceDigit = it)) }
                )
                SettingToggleRow(
                    title = "Haptic feedback",
                    subtitle = "Subtle vibration on taps and actions",
                    checked = settings.hapticsEnabled,
                    inkColor = inkColor,
                    mutedColor = mutedColor,
                    onCheckedChange = { onUpdate(settings.copy(hapticsEnabled = it)) }
                )
                SettingToggleRow(
                    title = "Keep screen awake",
                    subtitle = "Prevent screen from dimming or sleeping while playing",
                    checked = settings.keepScreenOn,
                    inkColor = inkColor,
                    mutedColor = mutedColor,
                    onCheckedChange = { onUpdate(settings.copy(keepScreenOn = it)) }
                )
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sound volume", color = inkColor, fontSize = 14.sp)
                            Text("Subtle placement & victory sounds", color = mutedColor, fontSize = 11.sp)
                        }
                        Text(
                            if (settings.soundVolume <= 0.01f) "Off" else "${(settings.soundVolume * 100).roundToInt()}%",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = settings.soundVolume,
                        onValueChange = { onUpdate(settings.copy(soundVolume = it)) },
                        onValueChangeFinished = {
                            SoundEngine.play(SoundEffect.CORRECT_PLACE, settings.soundVolume)
                        }
                    )
                }
                HorizontalDivider(color = palette.color(ColorSlot.CELL_BORDER))
                OutlinedButton(
                    onClick = onOpenAppearance,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Appearance & Themes", color = inkColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Current theme: $themeName", color = mutedColor, fontSize = 11.sp)
                        }
                        Text("Customize ›", color = accentColor, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = accentColor) }
        }
    )
}

@Composable
private fun AppearanceDialog(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = settings.palette
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val accentColor = palette.color(ColorSlot.ACCENT)
    val borderColor = palette.color(ColorSlot.CELL_BORDER)

    var editingSlot by remember { mutableStateOf<ColorSlot?>(null) }
    var showSaveTheme by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Appearance", color = inkColor)
                TextButton(onClick = { showSaveTheme = true }) {
                    Text("+ Save theme", color = accentColor, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Themes", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    settings.allThemes.forEach { theme ->
                        val active = theme.palette == palette
                        Row(
                            Modifier
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) accentColor else borderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    Color(theme.palette[ColorSlot.SURFACE]),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onUpdate(settings.copy(palette = theme.palette)) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(theme.palette[ColorSlot.ACCENT]))
                            )
                            Text(
                                theme.name,
                                color = if (active) accentColor else inkColor,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (!theme.builtIn) {
                                Text(
                                    "×",
                                    color = mutedColor,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .padding(start = 2.dp)
                                        .clickable { onUpdate(settings.deleteCustomTheme(theme.name)) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = borderColor)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Colors", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onUpdate(settings.copy(palette = ThemePalette())) }) {
                        Text("Reset default", color = mutedColor, fontSize = 12.sp)
                    }
                }
                ColorSlot.entries.forEach { slot ->
                    val currentArgb = palette[slot]
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(slot.label, color = inkColor, fontSize = 13.sp)
                            Text(
                                "#${ThemePalette.formatHex(currentArgb)}  ·  Wheel / Hex",
                                color = accentColor,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { editingSlot = slot }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            slot.options.forEach { optionArgb ->
                                val selected = currentArgb == optionArgb
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(optionArgb))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) inkColor else mutedColor.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onUpdate(settings.copy(palette = palette.withColor(slot, optionArgb)))
                                        }
                                )
                            }
                            val isCustom = currentArgb !in slot.options
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (isCustom) Color(currentArgb) else Color.Transparent)
                                    .border(
                                        width = if (isCustom) 2.5.dp else 1.dp,
                                        color = if (isCustom) inkColor else accentColor,
                                        shape = CircleShape
                                    )
                                    .clickable { editingSlot = slot },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = if (isCustom) inkColor else accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back", color = mutedColor) }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = accentColor) }
        }
    )

    if (showSaveTheme) {
        AlertDialog(
            onDismissRequest = { showSaveTheme = false },
            title = { Text("Save custom theme", color = inkColor) },
            text = {
                OutlinedTextField(
                    value = newThemeName,
                    onValueChange = { newThemeName = it },
                    label = { Text("Theme name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { showSaveTheme = false }) { Text("Cancel", color = mutedColor) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newThemeName.isNotBlank()) {
                            onUpdate(settings.saveCustomTheme(newThemeName))
                            newThemeName = ""
                            showSaveTheme = false
                        }
                    }
                ) { Text("Save", color = accentColor) }
            }
        )
    }

    editingSlot?.let { slot ->
        CustomColorPickerDialog(
            slot = slot,
            initialArgb = palette[slot],
            palette = palette,
            onConfirm = { newArgb ->
                onUpdate(settings.copy(palette = palette.withColor(slot, newArgb)))
                editingSlot = null
            },
            onDismiss = { editingSlot = null }
        )
    }
}

@Composable
private fun CustomColorPickerDialog(
    slot: ColorSlot,
    initialArgb: Long,
    palette: ThemePalette,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val accentColor = palette.color(ColorSlot.ACCENT)

    val initialHsv = remember(initialArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialArgb.toInt(), it) }
    }
    var hue by remember(initialArgb) { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember(initialArgb) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialArgb) { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember(initialArgb) { mutableStateOf(ThemePalette.formatHex(initialArgb)) }

    val currentArgb = remember(hue, sat, value) {
        (android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL) or 0xFF000000L
    }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        sat = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        val argb = (android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL) or 0xFF000000L
        hexText = ThemePalette.formatHex(argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.label, color = inkColor) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(180.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun updateAt(pos: Offset) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val radius = minOf(cx, cy)
                                    val dx = pos.x - cx
                                    val dy = pos.y - cy
                                    val dist = (hypot(dx.toDouble(), dy.toDouble()).toFloat() / radius).coerceIn(0f, 1f)
                                    val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
                                    updateFromHsv(angle, dist, value)
                                }
                                updateAt(down.position)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    updateAt(change.position)
                                    change.consume()
                                }
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val sweepColors = listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                        drawCircle(
                            brush = Brush.sweepGradient(sweepColors, center = center),
                            radius = radius,
                            center = center
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = (1f - value).coerceIn(0f, 1f)),
                            radius = radius,
                            center = center
                        )
                        val rad = Math.toRadians(hue.toDouble())
                        val thumbX = center.x + cos(rad).toFloat() * sat * radius
                        val thumbY = center.y + sin(rad).toFloat() * sat * radius
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Text("Brightness", color = mutedColor, fontSize = 12.sp)
                    Slider(
                        value = value,
                        onValueChange = { updateFromHsv(hue, sat, it) }
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(currentArgb))
                            .border(1.dp, inkColor, RoundedCornerShape(8.dp))
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            val clean = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                            hexText = clean
                            if (clean.length == 6) {
                                ThemePalette.parseHex(clean)?.let { parsed ->
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsed.toInt(), hsv)
                                    hue = hsv[0]
                                    sat = hsv[1]
                                    value = hsv[2]
                                }
                            }
                        },
                        prefix = { Text("#", color = mutedColor) },
                        label = { Text("Hex (RRGGBB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = mutedColor) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ThemePalette.parseHex(hexText) ?: currentArgb) }) {
                Text("Apply", color = accentColor)
            }
        }
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    inkColor: Color,
    mutedColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = inkColor, fontSize = 14.sp)
            Text(subtitle, color = mutedColor, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DifficultyGuideDialog(
    palette: ThemePalette,
    onDismiss: () -> Unit
) {
    val inkColor = palette.color(ColorSlot.INK)
    val mutedColor = palette.color(ColorSlot.MUTED)
    val accentColor = palette.color(ColorSlot.ACCENT)
    val surfaceColor = palette.color(ColorSlot.SURFACE)

    val guideItems = listOf(
        Triple(
            "Easy",
            "42 clues",
            "• Naked Singles & Hidden Singles\n• Solvable by direct scanning of rows, columns, and 3×3 boxes without pencil candidate tracking."
        ),
        Triple(
            "Medium",
            "36 clues",
            "• Naked Pairs / Triples, Hidden Pairs\n• Locked Candidates (Pointing & Claiming lines)\n• Candidate notes help spot intersections and eliminations across regions."
        ),
        Triple(
            "Hard",
            "30 clues",
            "• Naked / Hidden Triples & Quads\n• Box-Line Reduction\n• Requires deeper note tracking to eliminate candidates from intersecting rows and boxes."
        ),
        Triple(
            "Expert",
            "SE rating 4.0 – 5.9",
            "• X-Wing, Swordfish, Y-Wing (XY-Wing), XYZ-Wing\n• Simple Coloring, Skyscrapers\n• Eliminates possibilities using geometric candidate alignments and wing patterns across distant cells."
        ),
        Triple(
            "Extreme",
            "SE rating 6.0 – 6.9",
            "• W-Wing, Jellyfish, 2-String Kite\n• Alternating Inference Chains (AIC)\n• Unique Rectangles (deadly pattern avoidance)\n• Advanced conjugate pairs and loop structures."
        ),
        Triple(
            "Diabolical",
            "SE rating 7.0 – 8.4",
            "• Multi-step AICs, Medusa 3D Coloring\n• Forcing Chains, Sue-de-Coq\n• Master-level chains linking candidate dependencies across the entire grid."
        ),
        Triple(
            "Diabolical+",
            "SE rating 8.5 – 10.0",
            "• Dynamic Forcing Chains, Nested Chains\n• Multi-branch Contradiction Nets\n• Elite logic puzzles requiring deep branched hypothesis tracking to uncover contradictions."
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Text("Difficulties & Techniques", color = inkColor)
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                guideItems.forEach { (name, metric, techniques) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(surfaceColor, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(metric, color = mutedColor, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(techniques, color = inkColor, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = accentColor)
            }
        }
    )
}
