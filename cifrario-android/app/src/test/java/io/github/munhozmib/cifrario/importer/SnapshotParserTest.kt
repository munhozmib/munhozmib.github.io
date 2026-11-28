package io.github.munhozmib.cifrario.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotParserTest {
    private val sheet = """
        [Intro] G  D  Em  C

        [Primeira Parte]
        G              D
          Linha de teste um
        Em             C
          Linha de teste dois
        G       D/F#     Em
          Linha de teste três
    """.trimIndent()

    /** Texto visível de uma página inteira: menus, seletor de tom, cifra, comentários. */
    private val body = listOf(
        """
        Cifra Club
        Entrar  Buscar
        Minha Canção
        Banda Teste
        Tom: G
        C  C#  D  Eb  E  F  F#  G  Ab  A  Bb  B
        Auto rolagem   Texto   Simplificar
        Afinação padrão
        """.trimIndent(),
        sheet,
        """

        Composição de Fulano
        Comentários
        A música é muito boa
        Mais acessadas
        1. Outra música
        2. Mais uma
        Rodapé · Termos · Privacidade
        """.trimIndent(),
    ).joinToString("\n")

    @Test fun picksSheetFromWholePage() {
        val p = SnapshotParser.parse(PageSnapshot(url = "https://www.cifraclub.com.br/banda-teste/minha-cancao/#instrument=guitar", title = "Minha Canção - Banda Teste - Cifra Club", h1 = "Minha Canção", h2 = "Banda Teste", key = "Tom: G", body = body))
        assertEquals(sheet, p.content)
        assertEquals("Minha Canção", p.title)
        assertEquals("Banda Teste", p.artist)
        assertEquals("G", p.key)
        assertEquals("https://www.cifraclub.com.br/banda-teste/minha-cancao/", p.url)
        assertEquals(Site.CIFRA_CLUB, p.site)
    }

    @Test fun prefersCandidateOverBody() {
        val withExtra = "    " + sheet.replace("\n", "\n    ") // indentação do <pre>
        val p = SnapshotParser.parse(PageSnapshot(url = "https://www.e-chords.com/chords/x/y", ogTitle = "Song Chords by Band", candidates = listOf("menu\nmenu\nmenu\nmenu", withExtra), body = body))
        assertEquals(sheet, p.content)
        assertEquals("Song" to "Band", p.title to p.artist)
    }

    @Test fun ultimateGuitarJsonWins() {
        val ug = """{"store":{"page":{"data":{"tab":{"song_name":"S","artist_name":"A"},"tab_view":{"wiki_tab":{"content":"[ch]C[/ch] [ch]G[/ch]\nletra"}}}}}}"""
        val p = SnapshotParser.parse(PageSnapshot(url = "https://tabs.ultimate-guitar.com/tab/a/s-chords-1", ug = ug, body = "nada"))
        assertEquals("C G\nletra", p.content)
        assertEquals("S", p.title)
    }

    @Test(expected = ParseException::class)
    fun searchResultsPageFails() {
        SnapshotParser.parse(PageSnapshot(url = "https://www.cifraclub.com.br/?q=x", body = "Resultados\nMúsica 1\nMúsica 2\nMúsica 3"))
    }

    @Test fun ignoresLoneChordLines() {
        assertNull(SnapshotParser.bestBlock(listOf("Tom\nC  C#  D  Eb\ntexto\ntexto")))
        val block = SnapshotParser.trimToSheet(body)!!
        assertFalse(block.contains("Comentários"))
        assertFalse(block.contains("Eb  E  F"))
        assertTrue(block.startsWith("[Intro]"))
    }
}
