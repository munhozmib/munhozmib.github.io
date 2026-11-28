package io.github.munhozmib.cifrario.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChordTest {
    @Test fun parsesCommonChords() {
        listOf("C", "Am", "Am7", "G/B", "F#m7(b5)", "Bb7M", "D4", "E7(9)", "C#º", "Asus4", "A5", "Cadd9", "Dm6", "G7(13)", "Ebmaj7", "Bbm/Ab", "C°", "E7/9", "A7(4/9)")
            .forEach { assertNotNull("deveria reconhecer $it", Chord.parse(it)) }
    }

    @Test fun rejectsWords() {
        listOf("Eu", "As", "Amor", "Bom", "Deus", "Era", "Agora", "Gente", "Dó", "Cá", "Ela", "Bem", "Fé", "e|---", "-----")
            .forEach { assertNull("não deveria reconhecer $it", Chord.parse(it)) }
    }

    @Test fun transposes() {
        assertEquals("D", Chord.parse("C")!!.transpose(2).format())
        assertEquals("C#m7", Chord.parse("Bm7")!!.transpose(2).format())
        assertEquals("Dbm7", Chord.parse("Bm7")!!.transpose(2, preferFlats = true).format())
        assertEquals("A/C#", Chord.parse("G/B")!!.transpose(2).format())
        assertEquals("Bb7M", Chord.parse("A7M")!!.transpose(1, preferFlats = true).format())
        assertEquals("B", Chord.parse("C")!!.transpose(-1).format())
        assertEquals("G", Chord.parse("G")!!.transpose(12).format())
    }

    @Test fun pitchClasses() {
        assertEquals(setOf(0, 4, 7), Chord.parse("C")!!.pitchClasses().toSet())
        assertEquals(setOf(9, 0, 4), Chord.parse("Am")!!.pitchClasses().toSet())
        assertEquals(setOf(7, 11, 2, 5), Chord.parse("G7")!!.pitchClasses().toSet())
        assertEquals(setOf(0, 4, 7, 11), Chord.parse("C7M")!!.pitchClasses().toSet())
        assertEquals(setOf(2, 7, 9), Chord.parse("Dsus4")!!.pitchClasses().toSet())
        assertEquals(setOf(2, 7, 9), Chord.parse("D4")!!.pitchClasses().toSet())
        assertEquals(setOf(11, 2, 5, 9), Chord.parse("Bm7(b5)")!!.pitchClasses().toSet())
        assertEquals(setOf(4, 8, 11, 2, 6), Chord.parse("E7(9)")!!.pitchClasses().toSet())
        assertEquals(setOf(0, 4, 7, 2), Chord.parse("Cadd9")!!.pitchClasses().toSet())
        assertEquals(setOf(9, 4), Chord.parse("A5")!!.pitchClasses().toSet())
        // Baixo diferente entra nas notas.
        assertEquals(setOf(11, 7, 2), Chord.parse("G/B")!!.pitchClasses().toSet())
    }
}
