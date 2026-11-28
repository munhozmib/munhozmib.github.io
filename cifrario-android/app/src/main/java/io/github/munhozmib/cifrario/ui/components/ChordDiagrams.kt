package io.github.munhozmib.cifrario.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.munhozmib.cifrario.data.Instrument
import io.github.munhozmib.cifrario.music.Chord
import io.github.munhozmib.cifrario.music.GuitarVoicing
import io.github.munhozmib.cifrario.music.KeyboardVoicing
import io.github.munhozmib.cifrario.ui.theme.LocalSheetColors

@Composable
fun ChordStrip(chords: List<Chord>, instrument: Instrument, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(chords, key = { it.format() }) { chord ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(chord.format(), style = MaterialTheme.typography.labelLarge, color = LocalSheetColors.current.chord, fontWeight = FontWeight.Bold)
                if (instrument == Instrument.VIOLAO) GuitarDiagram(chord) else KeyboardDiagram(chord)
            }
        }
    }
}

@Composable
fun GuitarDiagram(chord: Chord, modifier: Modifier = Modifier) {
    val shape = remember(chord.format()) { GuitarVoicing.find(chord) }
    val ink = MaterialTheme.colorScheme.onSurface
    val dot = LocalSheetColors.current.chord
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(64.dp, 78.dp)) {
        val left = 10.dp.toPx(); val right = size.width - 6.dp.toPx()
        val top = 12.dp.toPx(); val bottom = size.height - 4.dp.toPx()
        val sx = (right - left) / 5f
        val sy = (bottom - top) / 4f
        val base = shape?.baseFret ?: 1
        for (s in 0..5) drawLine(ink, Offset(left + s * sx, top), Offset(left + s * sx, bottom), 1.dp.toPx())
        for (f in 0..4) drawLine(ink, Offset(left, top + f * sy), Offset(right, top + f * sy), if (f == 0 && base == 1) 3.dp.toPx() else 1.dp.toPx())
        if (shape == null) return@Canvas
        if (base > 1) drawText(measurer, "$base", Offset(0f, top + sy * 0.15f), TextStyle(fontSize = 9.sp, color = ink))
        val minF = shape.fretted.minOrNull()
        val barre = minF != null && shape.frets.count { it == minF } >= 2 && shape.frets.indexOfFirst { it == minF }.let { first ->
            (first..5).all { shape.frets[it] < 0 || shape.frets[it] >= minF }
        }
        if (barre && minF != null) {
            val first = shape.frets.indexOfFirst { it == minF }
            val last = shape.frets.indexOfLast { it == minF }
            val y = top + (minF - base + 0.5f) * sy
            drawRoundRect(dot, Offset(left + first * sx - 4.dp.toPx(), y - 4.dp.toPx()), Size((last - first) * sx + 8.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
        shape.frets.forEachIndexed { s, f ->
            val x = left + s * sx
            when {
                f < 0 -> drawText(measurer, "×", Offset(x - 3.dp.toPx(), -2.dp.toPx()), TextStyle(fontSize = 9.sp, color = ink))
                f == 0 -> drawCircle(ink, 3.dp.toPx(), Offset(x, top - 6.dp.toPx()), style = Stroke(1.dp.toPx()))
                else -> drawCircle(dot, 4.dp.toPx(), Offset(x, top + (f - base + 0.5f) * sy))
            }
        }
    }
}

@Composable
fun KeyboardDiagram(chord: Chord, modifier: Modifier = Modifier) {
    val notes = remember(chord.format()) { KeyboardVoicing.notes(chord) }
    val ink = MaterialTheme.colorScheme.onSurface
    val hi = LocalSheetColors.current.chord
    Canvas(modifier.size(112.dp, 56.dp)) {
        // Mostra 2 oitavas (14 teclas brancas) cobrindo as notas do acorde.
        val low = (notes.minOrNull() ?: 48).let { it - Math.floorMod(it, 12) }
        val whiteSteps = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val ww = size.width / 14f
        val set = notes.toSet()
        for (i in 0 until 14) {
            val midi = low + (i / 7) * 12 + whiteSteps[i % 7]
            val x = i * ww
            drawRect(if (midi in set) hi else Color.White, Offset(x, 0f), Size(ww, size.height))
            drawRect(ink, Offset(x, 0f), Size(ww, size.height), style = Stroke(1.dp.toPx()))
        }
        val blackAfter = intArrayOf(0, 1, 3, 4, 5) // após C, D, F, G, A
        for (oct in 0..1) for (b in blackAfter) {
            val midi = low + oct * 12 + whiteSteps[b] + 1
            val x = (oct * 7 + b + 1) * ww - ww * 0.3f
            drawRect(if (midi in set) hi else Color(0xFF222222), Offset(x, 0f), Size(ww * 0.6f, size.height * 0.6f))
            if (midi in set) drawRect(ink, Offset(x, 0f), Size(ww * 0.6f, size.height * 0.6f), style = Stroke(1.dp.toPx()))
        }
    }
}
