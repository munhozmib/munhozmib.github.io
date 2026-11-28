package io.github.munhozmib.cifrario.music

enum class LineKind { CHORDS, LYRICS, SECTION, TAB, BLANK }

data class SheetLine(
    val text: String,
    val kind: LineKind,
    /** Posições [start, end) dos acordes em [text]. */
    val chordRanges: List<IntRange> = emptyList(),
    /** Rótulo no início da linha, ex.: "[Intro]" ou "Refrão:". */
    val labelRange: IntRange? = null,
)

/**
 * Interpreta o texto puro de uma cifra (acordes em linhas próprias, acima da letra),
 * identificando acordes para destacar e transpor sem perder o alinhamento.
 */
object ChordSheet {
    private val TOKEN = Regex("\\S+")
    private val LABEL = Regex("^\\s*(\\[[^\\]]{1,40}\\]|[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ0-9 ]{0,24}:)")
    private val SECTION_ONLY = Regex("^\\s*\\[[^\\]]{1,40}\\]\\s*$")
    private val TAB_LINE = Regex("^\\s*[A-Ga-g][#b]?\\s?[|:][-0-9|hpbrxs/\\\\~()^. ]{4,}$")
    private val NOISE = Regex("^(\\|+|:?\\|\\|?:?|[-–—~.*%/]+|\\(?[0-9]+x\\)?|\\(?x[0-9]+\\)?|\\(|\\)|riff|solo|bis)$", RegexOption.IGNORE_CASE)
    private const val LEAD = "([{|"
    private const val TRAIL = ")]}|,;"

    fun parse(text: String, semitones: Int = 0, preferFlats: Boolean? = null): List<SheetLine> {
        val flats = preferFlats ?: (semitones != 0 && prefersFlats(text, semitones))
        return text.replace("\r\n", "\n").replace('\t', ' ').replace(' ', ' ')
            .split('\n').map { parseLine(it, semitones, flats) }
    }

    fun parseLine(line: String, semitones: Int = 0, preferFlats: Boolean = false): SheetLine {
        if (line.isBlank()) return SheetLine(line, LineKind.BLANK)
        if (TAB_LINE.matches(line)) return SheetLine(line, LineKind.TAB)
        if (SECTION_ONLY.matches(line)) return SheetLine(line, LineKind.SECTION, labelRange = line.indices)

        val label = LABEL.find(line)
        val bodyStart = label?.range?.last?.plus(1) ?: 0
        val found = findChords(line, bodyStart) ?: return if (label != null && line.substring(bodyStart).isBlank()) {
            SheetLine(line, LineKind.SECTION, labelRange = label.range)
        } else SheetLine(line, LineKind.LYRICS)

        if (found.isEmpty()) return SheetLine(line, LineKind.LYRICS)
        return rebuild(line, found, semitones, preferFlats, label?.range)
    }

    /** Lista acordes (com posição) se TODOS os tokens após [from] forem acordes ou ruído; senão null. */
    private fun findChords(line: String, from: Int): List<Pair<IntRange, Chord>>? {
        val out = mutableListOf<Pair<IntRange, Chord>>()
        for (m in TOKEN.findAll(line, from)) {
            val tok = m.value
            if (NOISE.matches(tok)) continue
            var s = 0
            var e = tok.length
            // Remove pontuação em volta, mas preserva parênteses que fazem parte do acorde, ex.: "G7(4)".
            fun balanced(a: Int, b: Int) = tok.substring(a, b).count { it == '(' } == tok.substring(a, b).count { it == ')' }
            while (s < e && tok[s] in LEAD && !(tok[s] == '(' && balanced(s, e) && s > 0)) s++
            while (e > s && tok[e - 1] in TRAIL && !(tok[e - 1] == ')' && balanced(s, e))) e--
            val core = tok.substring(s, e)
            if (core.isEmpty() || NOISE.matches(core)) continue
            val chord = Chord.parse(core) ?: return null
            val start = m.range.first + s
            out += (start until start + core.length) to chord
        }
        return out
    }

    private fun rebuild(
        line: String, chords: List<Pair<IntRange, Chord>>, semitones: Int, flats: Boolean, label: IntRange?,
    ): SheetLine {
        if (semitones == 0) return SheetLine(line, LineKind.CHORDS, chords.map { it.first }, label)
        val sb = StringBuilder()
        val ranges = mutableListOf<IntRange>()
        var prevEnd = 0
        for ((range, chord) in chords) {
            val gap = line.substring(prevEnd, range.first)
            if (gap.isBlank()) {
                // Mantém a coluna original do acorde; se o anterior cresceu, garante 1 espaço.
                val spaces = if (sb.isEmpty()) range.first else maxOf(1, range.first - sb.length)
                repeat(spaces) { sb.append(' ') }
            } else sb.append(gap)
            val name = chord.transpose(semitones, flats).format()
            val start = sb.length
            sb.append(name)
            ranges += start until sb.length
            prevEnd = range.last + 1
        }
        sb.append(line.substring(prevEnd))
        return SheetLine(sb.toString(), LineKind.CHORDS, ranges, label)
    }

    /** Texto inteiro transposto (para exportar/copiar). */
    fun transposeText(text: String, semitones: Int): String =
        parse(text, semitones).joinToString("\n") { it.text }

    /** Acordes distintos na ordem em que aparecem (já transpostos). */
    fun distinctChords(text: String, semitones: Int = 0): List<Chord> {
        val seen = LinkedHashMap<String, Chord>()
        val flats = semitones != 0 && prefersFlats(text, semitones)
        for (line in text.split('\n')) {
            val label = LABEL.find(line)
            val body = label?.range?.last?.plus(1) ?: 0
            if (TAB_LINE.matches(line)) continue
            findChords(line, body)?.forEach { (_, c) ->
                val t = c.transpose(semitones, if (semitones == 0) c.rootFlat else flats)
                seen.putIfAbsent(t.format(), t)
            }
        }
        return seen.values.toList()
    }

    /** Primeiro acorde da música = palpite do tom quando o site não informa. */
    fun guessKey(text: String): String? = distinctChords(text).firstOrNull()?.let {
        Notes.name(it.root, it.rootFlat) + if (it.quality().intervals.contains(3) && !it.quality().intervals.contains(4)) "m" else ""
    }

    fun transposeKey(key: String?, semitones: Int): String? {
        if (key.isNullOrBlank()) return null
        val c = Chord.parse(key.trim()) ?: return key
        if (semitones == 0) return key
        val flats = keyPrefersFlats(Math.floorMod(c.root + semitones, 12), c.suffix.startsWith("m") && !c.suffix.startsWith("maj"))
        return c.transpose(semitones, flats).format()
    }

    private fun prefersFlats(text: String, semitones: Int): Boolean {
        val first = distinctChords(text).firstOrNull() ?: return false
        val minor = first.quality().intervals.contains(3) && !first.quality().intervals.contains(4)
        return keyPrefersFlats(Math.floorMod(first.root + semitones, 12), minor)
    }

    /** F, Bb, Eb, Ab, Db, Gb (e relativos menores) se escrevem com bemóis. */
    fun keyPrefersFlats(root: Int, minor: Boolean): Boolean {
        val major = if (minor) Math.floorMod(root + 3, 12) else root
        return major in setOf(5, 10, 3, 8, 1, 6)
    }
}
