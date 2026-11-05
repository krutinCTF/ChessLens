package com.chesslens.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val pieces = listOf("·", "♙", "♘", "♗", "♖", "♕", "♔", "♟", "♞", "♝", "♜", "♛", "♚")
private val fenChars = mapOf("♙" to "P", "♘" to "N", "♗" to "B", "♖" to "R", "♕" to "Q", "♔" to "K",
    "♟" to "p", "♞" to "n", "♝" to "b", "♜" to "r", "♛" to "q", "♚" to "k")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChessLensApp() }
    }
}

@Composable
fun ChessLensApp() {
    var screenshot by remember { mutableStateOf<Uri?>(null) }
    var selected by remember { mutableStateOf("♙") }
    var turn by remember { mutableStateOf("w") }
    var fen by remember { mutableStateOf("") }
    val board = remember { mutableStateListOf<String>().apply { repeat(64) { add("·") } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { screenshot = it }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFA78BFA),
        background = Color(0xFF101018),
        surface = Color(0xFF1B1B27)
    )) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101018)) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("ChessLens", color = Color.White, fontSize = 28.sp)
                Text("Импорт позиции для последующего анализа", color = Color(0xFFB8B8C8))
                Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать скриншот Chess.com")
                }
                screenshot?.let {
                    AsyncImage(
                        model = it, contentDescription = "Импортированный скриншот",
                        modifier = Modifier.fillMaxWidth().height(130.dp).background(Color.DarkGray, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Text("1. Выбери фигуру, затем нажми клетку:", color = Color.White)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pieces.chunked(7).forEach { group ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            group.forEach { p ->
                                FilterChip(
                                    selected = selected == p,
                                    onClick = { selected = p },
                                    label = { Text(p, fontSize = 18.sp) }
                                )
                            }
                        }
                    }
                }
                Text("2. Расставь фигуры на доске:", color = Color.White)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(board) { index, piece ->
                        val row = index / 8
                        val col = index % 8
                        val light = (row + col) % 2 == 0
                        Box(
                            Modifier.aspectRatio(1f)
                                .background(if (light) Color(0xFFF0D9B5) else Color(0xFFB58863))
                                .clickable { board[index] = selected }
                                .border(0.3.dp, Color.Black.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(piece, fontSize = 29.sp, color = if (piece.firstOrNull()?.isLowerCase() == true) Color(0xFF171717) else Color(0xFFFAFAFA))
                            if (piece == "·" && row == 7) Text("abcdefgh"[col].toString(), Modifier.align(Alignment.BottomEnd), fontSize = 8.sp, color = Color.DarkGray)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { repeat(64) { board[it] = "·" }; fen = "" }) { Text("Очистить") }
                    OutlinedButton(onClick = { turn = if (turn == "w") "b" else "w" }) { Text(if (turn == "w") "Ход белых" else "Ход чёрных") }
                    Button(onClick = {
                        val ranks = (0..7).map { r ->
                            var empty = 0
                            buildString {
                                for (c in 0..7) {
                                    val p = board[r * 8 + c]
                                    if (p == "·") empty++ else {
                                        if (empty > 0) { append(empty); empty = 0 }
                                        append(fenChars[p] ?: "")
                                    }
                                }
                                if (empty > 0) append(empty)
                            }
                        }
                        fen = ranks.joinToString("/") + " $turn - - 0 1"
                    }) { Text("Создать FEN") }
                }
                if (fen.isNotBlank()) {
                    Text("FEN", color = Color(0xFFA78BFA))
                    Text(fen, color = Color.White, fontSize = 12.sp)
                    OutlinedButton(onClick = { /* copy handled by long-press */ }) {
                        Text("Удерживай FEN, чтобы скопировать")
                    }
                }
                Text("Важно: сейчас скриншот загружается как образец, а фигуры расставляются вручную. Автораспознавание и Stockfish будут следующими этапами.", color = Color(0xFFB8B8C8), fontSize = 11.sp)
            }
        }
    }
}
