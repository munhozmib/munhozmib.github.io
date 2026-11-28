package io.github.munhozmib.cifrario.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicingTest {
    private fun shape(c: String) = GuitarVoicing.find(Chord.parse(c)!!).toString()

    @Test fun openChords() {
        assertEquals("x32010", shape("C"))
        assertEquals("x02210", shape("Am"))
        assertEquals("xx0232", shape("D"))
        assertEquals("022100", shape("E"))
        assertEquals("022000", shape("Em"))
        assertEquals("x02220", shape("A"))
    }

    @Test fun everyCommonChordHasAShape() {
        val roots = Notes.SHARP + listOf("Bb", "Eb", "Ab")
        val suffixes = listOf("", "m", "7", "m7", "7M", "4", "7(9)", "m7(b5)", "º", "6", "9", "add9", "sus2")
        for (r in roots) for (s in suffixes) {
            val chord = Chord.parse(r + s)!!
            val sh = GuitarVoicing.find(chord)
            assertNotNull("sem desenho para ${r + s}", sh)
            // A nota mais grave tocada precisa ser a fundamental.
            val tuning = intArrayOf(40, 45, 50, 55, 59, 64)
            val low = sh!!.frets.indexOfFirst { it >= 0 }
            assertEquals("${r + s} = $sh", chord.root, (tuning[low] + sh.frets[low]) % 12)
        }
    }

    @Test fun slashChordUsesBass() {
        val s = GuitarVoicing.find(Chord.parse("G/B")!!)!!
        val low = s.frets.indexOfFirst { it >= 0 }
        assertEquals(11, (intArrayOf(40, 45, 50, 55, 59, 64)[low] + s.frets[low]) % 12)
    }

    @Test fun keyboardNotes() {
        val n = KeyboardVoicing.notes(Chord.parse("C")!!)
        assertEquals(listOf(0, 4, 7), n.map { it % 12 })
        assertTrue(n.zipWithNext().all { (a, b) -> b > a })
        val slash = KeyboardVoicing.notes(Chord.parse("G/B")!!)
        assertEquals(11, slash.first() % 12)
        assertTrue(slash.first() < slash[1])
    }
}
