package io.github.munhozmib.cifrario.importer

import io.github.munhozmib.cifrario.music.ChordSheet
import io.github.munhozmib.cifrario.music.LineKind
import kotlinx.serialization.Serializable

/**
 * O que o navegador interno captura da página aberta, já renderizada no celular.
 * Usamos o texto visível (innerText) em vez do HTML para não depender do layout de cada site.
 */
@Serializable
data class PageSnapshot(
    val url: String,
    val title: String = "",
    val ogTitle: String = "",
    val h1: String = "",
    val h2: String = "",
    val key: String = "",
    /** data-content do Ultimate Guitar, quando existir. */
    val ug: String? = null,
    /** Textos de blocos que podem conter a cifra (pre, code, elementos "cifra"/"chord"...). */
    val candidates: List<String> = emptyList(),
    /** Texto visível da página inteira (reserva). */
    val body: String = "",
)

object SnapshotParser {
    /** JavaScript executado na página; devolve um [PageSnapshot] em JSON. */
    const val SCRIPT = """
(function(){
  function t(e){ return e ? (e.innerText || e.textContent || '') : ''; }
  function meta(p){ var e=document.querySelector(p); return e ? (e.getAttribute('content')||'') : ''; }
  var sel = 'pre, code, [class*="cifra"], [class*="Cifra"], [id*="cifra"], [class*="chord"], [class*="Chord"], [class*="tab-content"], [class*="tablatura"], article';
  var seen = [], c = [];
  document.querySelectorAll(sel).forEach(function(e){
    var x = t(e);
    if (x && x.split('\n').length >= 4 && seen.indexOf(x) < 0) { seen.push(x); c.push(x); }
  });
  c.sort(function(a,b){ return a.length - b.length; });
  if (c.length > 15) c = c.slice(0, 15);
  var ug = document.querySelector('.js-store');
  var keyEl = document.querySelector('#cifra_tom, [id*="tom"], [class*="cifra_tom"], [class*="key"]');
  return JSON.stringify({
    url: location.href, title: document.title, ogTitle: meta('meta[property="og:title"]'),
    h1: t(document.querySelector('h1')).trim().slice(0,200), h2: t(document.querySelector('h2')).trim().slice(0,200),
    key: t(keyEl).trim().slice(0,60), ug: ug ? ug.getAttribute('data-content') : null,
    candidates: c.map(function(x){ return x.slice(0, 30000); }), body: t(document.body).slice(0, 300000)
  });
})()
"""

    fun parse(s: PageSnapshot): ParsedSong {
        val site = Site.fromUrl(s.url)
        val meta = ChordPageParser.splitTitle(s.ogTitle.ifBlank { s.title })
        s.ug?.takeIf { it.isNotBlank() }?.let { raw ->
            ChordPageParser.fromUltimateGuitarJson(raw, s.url, meta)?.let { if (it.content.isNotBlank()) return it }
        }
        // Blocos específicos primeiro; o texto da página inteira só como reserva.
        val content = (bestBlock(s.candidates) ?: bestBlock(listOf(s.body)))
            ?: throw ParseException("Não encontrei acordes nesta página. Abra a página da música (não a lista de resultados).")

        val h1 = ChordPageParser.stripKind(s.h1.lineSequence().firstOrNull().orEmpty().trim())
        val h2 = s.h2.lineSequence().firstOrNull().orEmpty().trim()
        val useHeadings = h1.isNotBlank() && h1.length < 80 && site == Site.CIFRA_CLUB && h2.isNotBlank() && h2.length < 80
        return ParsedSong(
            title = if (useHeadings) h1 else meta.first.ifBlank { h1.ifBlank { "Sem título" } },
            artist = if (useHeadings) h2 else meta.second,
            content = content,
            key = Regex("(?i)(?:tom|key)\\s*:?\\s*([A-G][#b]?m?)\\b").find(s.key)?.groupValues?.get(1),
            site = site,
            url = s.url.substringBefore('#'),
        )
    }

    /**
     * Entre vários textos, escolhe o trecho com mais linhas de acordes e recorta só a cifra
     * (da primeira linha de acordes até a letra que segue a última).
     */
    fun bestBlock(texts: List<String>): String? {
        var best: String? = null
        var bestScore = 0
        for (t in texts) {
            val block = trimToSheet(ChordPageParser.clean(t)) ?: continue
            val lines = ChordSheet.parse(block)
            val chordLines = lines.count { it.kind == LineKind.CHORDS }
            if (chordLines < 2) continue
            // Mais acordes é melhor; com empate, o bloco mais curto (menos "lixo" em volta).
            val score = chordLines * 1000 - block.length / 50
            if (score > bestScore) { bestScore = score; best = block }
        }
        return best
    }

    fun trimToSheet(text: String): String? {
        val lines = text.split('\n')
        val parsed = lines.map { ChordSheet.parseLine(it) }
        val music = parsed.indices.filter { (parsed[it].kind == LineKind.CHORDS && !isWidget(lines[it], parsed[it])) || parsed[it].kind == LineKind.TAB }
        if (music.isEmpty()) return null
        // Agrupa linhas de acordes próximas (até 6 linhas de distância) e fica com o maior grupo:
        // descarta acordes soltos em menus, comentários etc.
        val runs = mutableListOf(mutableListOf(music[0]))
        for (i in music.drop(1)) if (i - runs.last().last() <= 6) runs.last() += i else runs += mutableListOf(i)
        val run = runs.maxBy { it.size }
        val first = run.first()
        val last = run.last()
        // Inclui um título de seção logo antes da primeira linha de acordes, ex.: "[Intro]".
        var start = first
        if (start > 0 && parsed[start - 1].kind == LineKind.SECTION) start--
        // Depois da última linha de acordes, inclui a letra até a próxima linha em branco.
        var end = last
        while (end + 1 < lines.size && lines[end + 1].isNotBlank() && end - last < 4) end++
        return ChordPageParser.clean(lines.subList(start, end + 1).joinToString("\n"))
    }

    private val INFO_LABEL = Regex("(?i)^\\s*(tom|key|capo|capotraste|afina[çc][ãa]o|tuning)\\b")

    /** Linhas de acordes que são da interface do site, não da música: "Tom: G" ou o seletor C C# D ... B. */
    private fun isWidget(line: String, parsed: io.github.munhozmib.cifrario.music.SheetLine): Boolean {
        if (INFO_LABEL.containsMatchIn(line)) return true
        val names = parsed.chordRanges.map { line.substring(it.first, it.last + 1) }
        val plainNotes = names.all { it.matches(Regex("[A-G][#b]?m?")) }
        return names.size >= 7 && plainNotes && names.toSet().size == names.size
    }
}
