package io.github.munhozmib.cifrario.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.munhozmib.cifrario.music.ChordSheet
import io.github.munhozmib.cifrario.music.LineKind
import io.github.munhozmib.cifrario.ui.theme.LocalSheetColors
import io.github.munhozmib.cifrario.ui.theme.SheetColors

fun buildSheet(content: String, semitones: Int, colors: SheetColors): AnnotatedString {
    val lines = ChordSheet.parse(content, semitones)
    return buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            val start = length
            append(line.text)
            when (line.kind) {
                LineKind.CHORDS -> line.chordRanges.forEach {
                    addStyle(SpanStyle(color = colors.chord, fontWeight = FontWeight.Bold), start + it.first, start + it.last + 1)
                }
                LineKind.TAB -> addStyle(SpanStyle(color = colors.tab), start, length)
                else -> {}
            }
            line.labelRange?.let {
                addStyle(SpanStyle(color = colors.section, fontWeight = FontWeight.Bold), start + it.first, start + it.last + 1)
            }
            if (i < lines.lastIndex) append('\n')
        }
    }
}

/** Texto da cifra em fonte monoespaçada, com acordes destacados. Não quebra linhas (preserva o alinhamento). */
@Composable
fun SheetText(content: String, semitones: Int, fontSize: Float, modifier: Modifier = Modifier) {
    val colors = LocalSheetColors.current
    val text = remember(content, semitones, colors) { buildSheet(content, semitones, colors) }
    Text(
        text = text,
        modifier = modifier,
        softWrap = false,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.35f).sp,
            color = LocalContentColor.current,
        ),
    )
}
