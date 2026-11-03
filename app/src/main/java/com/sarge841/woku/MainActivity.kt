package com.sarge841.woku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Background = Color(0xFF111418)
private val Surface = Color(0xFF1C2228)
private val Ink = Color(0xFFE7ECEF)
private val Muted = Color(0xFF87949D)
private val Accent = Color(0xFF75DCC7)
private val Danger = Color(0xFFFF8189)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(17, 20, 24)
        window.navigationBarColor = android.graphics.Color.rgb(17, 20, 24)
        val prefs = getSharedPreferences("woku", MODE_PRIVATE)
        setContent {
            var game by remember { mutableStateOf(SaveCodec.decode(prefs.getString("game", "") ?: "")
                ?: GameState.new(Sudoku.generate(Difficulty.EASY), Difficulty.EASY)) }
            var selectedCell by remember { mutableStateOf<Int?>(null) }
            var selectedDigit by remember { mutableStateOf<Int?>(null) }
            var pencil by remember { mutableStateOf(false) }
            var showNew by remember { mutableStateOf(false) }
            var showBest by remember { mutableStateOf(false) }
            var pendingNew by remember { mutableStateOf<Difficulty?>(null) }

            fun update(next: GameState) {
                if (game.status != GameStatus.WON && next.status == GameStatus.WON) {
                    val key = "best_${next.difficulty.name}"
                    val old = prefs.getInt(key, Int.MAX_VALUE)
                    if (next.elapsedSeconds < old) prefs.edit().putInt(key, next.elapsedSeconds).apply()
                }
                game = next
                prefs.edit().putString("game", SaveCodec.encode(next)).apply()
            }

            LaunchedEffect(game.status) {
                while (game.status == GameStatus.PLAYING) {
                    delay(1000)
                    update(game.tick())
                }
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background,
                surface = Surface, onSurface = Ink, onBackground = Ink, error = Danger)) {
                SudokuScreen(game, selectedCell, selectedDigit, pencil,
                    best = prefs.getInt("best_${game.difficulty.name}", Int.MAX_VALUE).takeUnless { it == Int.MAX_VALUE },
                    onCell = { index ->
                        selectedCell = index
                        val digit = selectedDigit
                        if (digit != null && game.puzzle.givens[index] == 0) {
                            update(if (pencil) game.toggleNote(index, digit) else game.place(index, digit))
                        }
                    },
                    onDigit = { digit ->
                        if (selectedDigit == digit) selectedDigit = null
                        else {
                            selectedDigit = digit
                            selectedCell?.let { index ->
                                update(if (pencil) game.toggleNote(index, digit) else game.place(index, digit))
                            }
                        }
                    },
                    onPencil = { pencil = !pencil },
                    onFastPencil = { update(game.fastPencil()) },
                    onErase = { selectedCell?.let { update(game.place(it, 0)) } },
                    onUndo = { update(game.undo()) },
                    onRedo = { update(game.redo()) },
                    onNew = { showNew = true }, onBest = { showBest = true })
                if (showNew) AlertDialog(onDismissRequest = { showNew = false },
                    title = { Text("New puzzle") }, text = {
                        Column { Difficulty.entries.forEach { level ->
                            TextButton(onClick = { showNew = false; pendingNew = level },
                                modifier = Modifier.fillMaxWidth()) { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        } }
                    }, confirmButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } })
                pendingNew?.let { level ->
                    AlertDialog(onDismissRequest = { pendingNew = null },
                        title = { Text("Start ${level.name.lowercase()}?") },
                        text = { Text("This replaces your current puzzle. Best times stay saved.") },
                        confirmButton = { TextButton(onClick = {
                            update(GameState.new(Sudoku.generate(level), level))
                            selectedCell = null; selectedDigit = null; pendingNew = null
                        }) { Text("Start") } },
                        dismissButton = { TextButton(onClick = { pendingNew = null }) { Text("Cancel") } })
                }
                if (showBest) AlertDialog(onDismissRequest = { showBest = false },
                    title = { Text("Best times") }, text = {
                        Column { Difficulty.entries.forEach { level ->
                            val seconds = prefs.getInt("best_${level.name}", Int.MAX_VALUE)
                            Text("${level.name.lowercase().replaceFirstChar { it.uppercase() }}  ·  ${if (seconds == Int.MAX_VALUE) "—" else formatTime(seconds)}",
                                color = Ink, modifier = Modifier.padding(vertical = 5.dp))
                        } }
                    }, confirmButton = { TextButton(onClick = { showBest = false }) { Text("Close") } })
            }
        }
    }
}

private fun formatTime(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun SudokuScreen(
    game: GameState, selectedCell: Int?, selectedDigit: Int?, pencil: Boolean, best: Int?,
    onCell: (Int) -> Unit, onDigit: (Int) -> Unit, onPencil: () -> Unit,
    onFastPencil: () -> Unit, onErase: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit,
    onNew: () -> Unit, onBest: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Background).systemBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WOKU", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = 3.sp)
                Text(game.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onBest) { Text("Records", color = Accent) }
            TextButton(onClick = onNew) { Text("New", color = Accent) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TIME  ${formatTime(game.elapsedSeconds)}", color = Ink, fontSize = 14.sp)
            Text("BEST  ${best?.let(::formatTime) ?: "—"}", color = Muted, fontSize = 14.sp)
            Text("♥".repeat(game.lives) + "♡".repeat(3 - game.lives), color = Danger, fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        SudokuGrid(game, selectedCell, selectedDigit, onCell)
        Spacer(Modifier.height(16.dp))
        if (game.status != GameStatus.PLAYING) {
            Text(if (game.status == GameStatus.WON) "Puzzle complete!" else "No lives left — try another puzzle",
                color = if (game.status == GameStatus.WON) Accent else Danger, fontSize = 16.sp)
        } else {
            Text(if (pencil) "PENCIL MODE" else "SELECT A CELL OR NUMBER", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (1..9).forEach { digit ->
                val active = selectedDigit == digit
                val remaining = 9 - game.values.count { it == digit }
                Column(Modifier.weight(1f).background(if (active) Accent else Surface, RoundedCornerShape(8.dp))
                    .clickable { onDigit(digit) }.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$digit", color = if (active) Background else Ink, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                    Text("$remaining", color = if (active) Background else Muted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Undo", game.history.isNotEmpty() && game.status == GameStatus.PLAYING, onUndo, Modifier.weight(1f))
            ActionButton("Redo", game.future.isNotEmpty() && game.status == GameStatus.PLAYING, onRedo, Modifier.weight(1f))
            ActionButton("Erase", selectedCell != null && game.status == GameStatus.PLAYING, onErase, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(if (pencil) "Pencil ON" else "Pencil", game.status == GameStatus.PLAYING, onPencil,
                Modifier.weight(1f), active = pencil)
            ActionButton("Fast pencil", game.status == GameStatus.PLAYING, onFastPencil, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, action: () -> Unit, modifier: Modifier, active: Boolean = false) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = modifier.height(43.dp),
        contentPadding = PaddingValues(horizontal = 3.dp),
        border = BorderStroke(1.dp, if (active) Accent else Color(0xFF354047))) {
        Text(label, fontSize = 12.sp, maxLines = 1, color = if (active) Accent else if (enabled) Ink else Muted)
    }
}

@Composable
private fun SudokuGrid(game: GameState, selectedCell: Int?, selectedDigit: Int?, onCell: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().border(2.dp, Accent)) {
        val size = maxWidth / 9
        Column {
            for (row in 0..8) Row {
                for (col in 0..8) {
                    val index = row * 9 + col
                    val value = game.values[index]
                    val chosen = selectedCell == index
                    val match = selectedDigit != null && value == selectedDigit
                    val peer = selectedCell != null && (row == selectedCell / 9 || col == selectedCell % 9 ||
                        (row / 3 == selectedCell / 27 && col / 3 == selectedCell % 9 / 3))
                    val shade = when { chosen -> Color(0xFF35584F); match -> Color(0xFF296A5B); peer -> Color(0xFF29333A); else -> Surface }
                    Box(Modifier.size(size).background(shade)
                        .border(BorderStroke(if (col == 2 || col == 5) 1.2.dp else 0.3.dp,
                            if (col == 2 || col == 5) Accent else Color(0xFF38424A)))
                        .clickable { onCell(index) }, contentAlignment = Alignment.Center) {
                        if (value != 0) Text("$value", color = when {
                            value != game.puzzle.solution[index] -> Danger
                            game.puzzle.givens[index] != 0 -> Ink
                            else -> Accent
                        }, fontSize = 21.sp, fontWeight = if (game.puzzle.givens[index] != 0) FontWeight.SemiBold else FontWeight.Normal)
                        else Column(Modifier.fillMaxSize().padding(1.dp)) {
                            for (r in 0..2) Row(Modifier.weight(1f)) {
                                for (c in 0..2) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    val digit = r * 3 + c + 1
                                    if (digit in game.notes[index]) Text("$digit", color = Muted, fontSize = 9.sp, lineHeight = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
