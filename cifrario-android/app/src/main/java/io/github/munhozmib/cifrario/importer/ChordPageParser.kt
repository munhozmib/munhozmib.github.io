package io.github.munhozmib.cifrario.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedSong(
    val title: String,
    val artist: String,
    val content: String,
    val key: String?,
    val site: Site?,
    val url: String,
)

class ParseException(message: String) : Exception(message)

/**
 * Converte o HTML de uma página de cifra no texto puro (acordes + letra) para guardar offline.
 * Cada site tem um caminho preferencial; se a estrutura mudar, cai no extrator genérico (maior <pre>).
 */
object ChordPageParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String, url: String): ParsedSong {
        val doc = Jsoup.parse(html, url)
        val site = Site.fromUrl(url)
        val result = when (site) {
            Site.ULTIMATE_GUITAR -> parseUltimateGuitar(doc, url) ?: parseGeneric(doc, url, site)
            Site.CIFRA_CLUB -> parseCifraClub(doc, url) ?: parseGeneric(doc, url, site)
            else -> parseGeneric(doc, url, site)
        }
        if (result.content.lines().count { it.isNotBlank() } < 2) {
            throw ParseException("Não encontrei uma cifra nesta página. Abra a página da música (não a lista de resultados) e tente de novo.")
        }
        return result
    }

    // ---------------- Cifra Club ----------------

    private fun parseCifraClub(doc: Document, url: String): ParsedSong? {
        val pre = doc.selectFirst(".cifra_cnt pre, .cifra-cnt pre, #cifra_cnt pre") ?: return null
        val title = doc.selectFirst("h1.t1, h1.g-title, .cifra_header h1")?.text()?.trim()
        val artist = doc.selectFirst("h2.t3 a, h2.t3, .cifra_header h2 a")?.text()?.trim()
        val key = doc.selectFirst("#cifra_tom a, #cifra_tom b, .cifra_tom a")?.text()?.trim()
        val (t, a) = titleArtistFromMeta(doc)
        return ParsedSong(
            title = title?.takeIf { it.isNotBlank() } ?: t,
            artist = artist?.takeIf { it.isNotBlank() } ?: a,
            content = clean(preText(pre)),
            key = key?.takeIf { it.isNotBlank() },
            site = Site.CIFRA_CLUB,
            url = canonical(doc, url),
        )
    }

    // ---------------- Ultimate Guitar ----------------

    private fun parseUltimateGuitar(doc: Document, url: String): ParsedSong? {
        val raw = doc.selectFirst("div.js-store")?.attr("data-content")?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val data = root.path("store", "page", "data") ?: return null
        val content = data.path("tab_view", "wiki_tab", "content")?.str() ?: return null
        val tab = data.path("tab")
        return ParsedSong(
            title = tab?.path("song_name")?.str() ?: titleArtistFromMeta(doc).first,
            artist = tab?.path("artist_name")?.str() ?: titleArtistFromMeta(doc).second,
            content = clean(ugMarkupToText(content)),
            key = tab?.path("tonality_name")?.str()?.takeIf { it.isNotBlank() },
            site = Site.ULTIMATE_GUITAR,
            url = url,
        )
    }

    fun ugMarkupToText(s: String): String =
        s.replace("[tab]", "").replace("[/tab]", "").replace("[ch]", "").replace("[/ch]", "")

    private fun JsonElement.path(vararg keys: String): JsonElement? {
        var cur: JsonElement = this
        for (k in keys) cur = (cur as? JsonObject)?.get(k) ?: return null
        return cur
    }

    private fun JsonElement.str(): String? = runCatching { jsonPrimitive.content }.getOrNull()

    // ---------------- Genérico (Cifras, E-Chords, fallback) ----------------

    private fun parseGeneric(doc: Document, url: String, site: Site?): ParsedSong {
        val candidates = doc.select("pre")
        val pre = candidates.maxByOrNull { it.wholeText().length }
            ?: throw ParseException("Não encontrei uma cifra nesta página.")
        val (t, a) = titleArtistFromMeta(doc)
        val h1 = doc.selectFirst("h1")?.text()?.trim()?.let(::stripKind)
        return ParsedSong(
            title = h1?.takeIf { it.isNotBlank() && it.length < 80 } ?: t,
            artist = a,
            content = clean(preText(pre)),
            key = findKey(doc),
            site = site,
            url = canonical(doc, url),
        )
    }

    private fun findKey(doc: Document): String? {
        val text = doc.select("#cifra_tom, .tom, [class*=tom], [id*=key], .key").firstOrNull()?.text() ?: return null
        return Regex("(?i)(?:tom|key)\\s*:?\\s*([A-G][#b]?m?)").find(text)?.groupValues?.get(1)
    }

    // ---------------- Utilidades ----------------

    private fun preText(pre: Element): String {
        val el = pre.clone()
        el.select("script, style, ins, iframe, button, .ad, [class*=publicidade], [class*=banner]").remove()
        el.select("br").forEach { it.after("\n"); it.remove() }
        return el.wholeText()
    }

    fun clean(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ').replace("\t", "    ")
            .split('\n').map { it.trimEnd() }
        val out = mutableListOf<String>()
        var blanks = 0
        for (l in lines) {
            if (l.isBlank()) { blanks++; if (blanks <= 1 && out.isNotEmpty()) out += "" } else { blanks = 0; out += l }
        }
        while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.lastIndex)
        // Remove indentação comum a todas as linhas.
        val indent = out.filter { it.isNotBlank() }.minOfOrNull { l -> l.takeWhile { it == ' ' }.length } ?: 0
        return out.joinToString("\n") { if (it.length >= indent) it.substring(indent) else it }
    }

    private val SITE_WORDS = Regex("(?i)cifra ?club|cifras(\\.com\\.br)?|ultimate[- ]guitar(\\.com)?|e-chords(\\.com)?|ug$")

    /** Título e artista a partir de og:title / <title>, ex.: "Tempo Perdido - Legião Urbana - Cifra Club". */
    fun titleArtistFromMeta(doc: Document): Pair<String, String> {
        val raw = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: doc.title()
        return splitTitle(raw)
    }

    fun splitTitle(raw: String): Pair<String, String> {
        val by = Regex("(?i)^(.+?)\\s+(?:chords|tabs?|cifra|acordes|ukulele)\\s+(?:by|de|por)\\s+(.+?)(?:\\s*[@|\\-–].*)?$").find(raw.trim())
        if (by != null) return stripKind(by.groupValues[1]) to by.groupValues[2].trim()
        val parts = raw.split(Regex("\\s+[-–|]\\s+")).map { it.trim() }.filter { it.isNotBlank() && !SITE_WORDS.containsMatchIn(it) }
        return when {
            parts.size >= 2 -> stripKind(parts[0]) to stripKind(parts[1])
            parts.size == 1 -> stripKind(parts[0]) to ""
            else -> "Sem título" to ""
        }
    }

    private fun stripKind(s: String) =
        s.replace(Regex("(?i)\\s*[(\\[]?\\b(cifra|cifras|chords|tab|tabs|acordes|simplificada|para teclado|ver\\.? ?\\d*)\\b[)\\]]?\\s*$"), "").trim()

    private fun canonical(doc: Document, url: String): String =
        doc.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.isNotBlank() } ?: url
}
