package io.github.munhozmib.cifrario.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HTMLs sintéticos que imitam a estrutura de cada site (sem letras reais). */
class ChordPageParserTest {
    @Test fun cifraClub() {
        val html = """
            <html><head><title>Minha Canção - Banda Teste - Cifra Club</title>
            <link rel="canonical" href="https://www.cifraclub.com.br/banda-teste/minha-cancao/"></head>
            <body><div class="cifra_header"><h1 class="t1">Minha Canção</h1><h2 class="t3"><a href="/banda-teste/">Banda Teste</a></h2></div>
            <span id="cifra_tom">tom: <a class="js-modal-trigger">G</a></span>
            <div class="cifra_cnt g-fix cifra-mono"><pre>[Intro] <b>G</b>  <b>D</b>  <b>Em</b>

<b>G</b>              <b>D</b>
  Linha de teste um
<span class="tablatura"><span class="cnt">E|---3---|</span></span>
<b>Em</b>       <b>C</b>
  Linha de teste dois
<script>ads()</script></pre></div></body></html>
        """.trimIndent()
        val p = ChordPageParser.parse(html, "https://www.cifraclub.com.br/banda-teste/minha-cancao/#tabs=false")
        assertEquals("Minha Canção", p.title)
        assertEquals("Banda Teste", p.artist)
        assertEquals("G", p.key)
        assertEquals(Site.CIFRA_CLUB, p.site)
        assertEquals("https://www.cifraclub.com.br/banda-teste/minha-cancao/", p.url)
        assertTrue(p.content.startsWith("[Intro] G  D  Em"))
        assertTrue(p.content.contains("G              D\n  Linha de teste um"))
        assertTrue(p.content.contains("E|---3---|"))
        assertFalse(p.content.contains("ads()"))
    }

    @Test fun ultimateGuitar() {
        val wiki = "[Intro]\r\n[ch]Em[/ch]   [ch]G[/ch]\r\n\r\n[Verse 1]\r\n[tab][ch]Em[/ch]       [ch]G[/ch]\r\nTest line one[/tab]"
        val json = """{"store":{"page":{"data":{"tab":{"song_name":"Test Song","artist_name":"Test Band","tonality_name":"Em"},"tab_view":{"wiki_tab":{"content":${kotlinx.serialization.json.JsonPrimitive(wiki)}}}}}}}"""
        val escaped = json.replace("&", "&amp;").replace("\"", "&quot;")
        val html = """<html><body><div class="js-store" data-content="$escaped"></div></body></html>"""
        val p = ChordPageParser.parse(html, "https://tabs.ultimate-guitar.com/tab/test-band/test-song-chords-1")
        assertEquals("Test Song", p.title)
        assertEquals("Test Band", p.artist)
        assertEquals("Em", p.key)
        assertEquals("[Intro]\nEm   G\n\n[Verse 1]\nEm       G\nTest line one", p.content)
    }

    @Test fun genericLargestPre() {
        val html = """
            <html><head><meta property="og:title" content="Outra Música - Cantora X - Cifras"></head>
            <body><pre>curto</pre><pre id="core">
    A          E
    Primeira linha
    F#m        D
    Segunda linha
            </pre></body></html>
        """.trimIndent()
        val p = ChordPageParser.parse(html, "https://www.cifras.com.br/cifra/cantora-x/outra-musica")
        assertEquals("Outra Música", p.title)
        assertEquals("Cantora X", p.artist)
        assertEquals(Site.CIFRAS, p.site)
        assertEquals("A          E\nPrimeira linha\nF#m        D\nSegunda linha", p.content)
    }

    @Test(expected = ParseException::class)
    fun pageWithoutChordsFails() {
        ChordPageParser.parse("<html><body><p>Resultados da busca</p></body></html>", "https://www.e-chords.com/search-all/x")
    }

    @Test fun titleSplitting() {
        assertEquals("Wonderwall" to "Oasis", ChordPageParser.splitTitle("Wonderwall Chords by Oasis"))
        assertEquals("Tempo" to "Banda", ChordPageParser.splitTitle("Tempo - Banda - Cifra Club"))
        assertEquals("Song" to "Artist", ChordPageParser.splitTitle("Song (Chords) - Artist - E-Chords"))
    }

    @Test fun cleanText() {
        assertEquals("a\n\nb", ChordPageParser.clean("\n\n  a  \n\n\n\n  b\n\n"))
    }

    @Test fun sites() {
        assertEquals(Site.CIFRA_CLUB, Site.fromUrl("https://m.cifraclub.com.br/x/y/"))
        assertEquals(Site.ULTIMATE_GUITAR, Site.fromUrl("https://tabs.ultimate-guitar.com/tab/x"))
        assertTrue(Site.isAllowedNavigation("https://www.e-chords.com/chords/x"))
        assertTrue(Site.isAllowedNavigation("https://www.google.com/search?q=x"))
        assertFalse(Site.isAllowedNavigation("https://www.google.com/maps"))
        assertFalse(Site.isAllowedNavigation("https://evil.com/cifraclub.com.br"))
        assertFalse(Site.isAllowedNavigation("https://cifraclub.com.br.evil.com/"))
        assertFalse(Site.isAllowedNavigation("intent://x"))
    }

    @Test fun cifraClubSearchParsing() {
        val body = """({"response":{"numFound":2,"docs":[
            {"t":"2","m":"Minha Canção","a":"Banda Teste","d":"banda-teste","u":"minha-cancao"},
            {"t":"1","m":"Banda Teste","a":"Banda Teste","d":"banda-teste"}]}})"""
        val r = CifraClubSearch.parse(body)
        assertEquals(1, r.size)
        assertEquals("https://www.cifraclub.com.br/banda-teste/minha-cancao/", r[0].url)
        assertEquals("Banda Teste", r[0].artist)
    }
}
