package io.github.munhozmib.cifrario.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSheetTest {
    private val song = """
        [Intro] C  G  Am  F

        [Primeira Parte]
        C              G
          Era uma vez um lugar
        Am         F
          E eu cantava ali
        e|-----0-----3---|
        B|---1---1-------|
        Refrão: Dm7  G7(4)  C
    """.trimIndent()

    @Test fun classifiesLines() {
        val lines = ChordSheet.parse(song)
        assertEquals(LineKind.CHORDS, lines[0].kind)       // [Intro] C G Am F
        assertEquals(4, lines[0].chordRanges.size)
        assertEquals(LineKind.BLANK, lines[1].kind)
        assertEquals(LineKind.SECTION, lines[2].kind)
        assertEquals(LineKind.CHORDS, lines[3].kind)
        assertEquals(LineKind.LYRICS, lines[4].kind)
        assertEquals(LineKind.CHORDS, lines[5].kind)
        assertEquals(LineKind.LYRICS, lines[6].kind)       // "E eu cantava ali" não é acorde
        assertEquals(LineKind.TAB, lines[7].kind)
        assertEquals(LineKind.TAB, lines[8].kind)
        assertEquals(LineKind.CHORDS, lines[9].kind)
        assertEquals(3, lines[9].chordRanges.size)
    }

    @Test fun transposeKeepsColumns() {
        val line = "C              G"
        val t = ChordSheet.parseLine(line, 1, preferFlats = false)
        assertEquals("C#             G#", t.text)
        assertEquals("C#", t.text.substring(t.chordRanges[0].first, t.chordRanges[0].last + 1))
        // Acorde cresce e ainda sobra pelo menos um espaço entre eles.
        val tight = ChordSheet.parseLine("C G A", 1)
        assertEquals("C# G# A#", tight.text)
        // Linha com rótulo mantém o rótulo.
        assertEquals("[Intro] D  A  Bm  G", ChordSheet.parseLine("[Intro] C  G  Am  F", 2).text)
    }

    @Test fun choosesFlatsForFlatKeys() {
        // C -> F (5 semitons): Fá maior usa bemóis (Bb, não A#). Colunas preservadas.
        assertEquals("F  Bb C", ChordSheet.transposeText("C  F  G", 5))
        assertEquals("(F  Bb)", ChordSheet.transposeText("(C  F)", 5))
    }

    @Test fun distinctChordsInOrder() {
        val chords = ChordSheet.distinctChords(song).map { it.format() }
        assertEquals(listOf("C", "G", "Am", "F", "Dm7", "G7(4)"), chords)
        assertTrue(ChordSheet.distinctChords(song, 2).map { it.format() }.contains("Em7"))
    }

    @Test fun keys() {
        assertEquals("C", ChordSheet.guessKey(song))
        assertEquals("Bb", ChordSheet.transposeKey("C", -2))
        assertEquals("F#m", ChordSheet.transposeKey("Em", 2))
        assertEquals("Ebm", ChordSheet.transposeKey("Cm", 3))
    }
}
