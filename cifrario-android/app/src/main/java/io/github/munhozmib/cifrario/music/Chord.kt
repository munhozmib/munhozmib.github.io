package io.github.munhozmib.cifrario.music

/**
 * Teoria musical mínima para cifras: reconhecer um acorde, transpor e listar as notas.
 */
object Notes {
    val SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val FLAT = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** Índice 0..11 de uma nota ("C", "C#", "Db"...) ou null. */
    fun index(name: String): Int? {
        if (name.isEmpty()) return null
        val base = when (name[0].uppercaseChar()) {
            'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11
            else -> return null
        }
        var i = base
        for (c in name.drop(1)) {
            when (c) {
                '#', '♯' -> i++
                'b', '♭' -> i--
                else -> return null
            }
        }
        return Math.floorMod(i, 12)
    }

    fun name(index: Int, preferFlats: Boolean): String =
        (if (preferFlats) FLAT else SHARP)[Math.floorMod(index, 12)]
}

data class Chord(
    val root: Int,
    /** Tudo entre a fundamental e o baixo, ex.: "m7", "7(9)", "sus4". */
    val suffix: String,
    val bass: Int? = null,
    val rootFlat: Boolean = false,
) {
    fun transpose(semitones: Int, preferFlats: Boolean = rootFlat): Chord =
        copy(
            root = Math.floorMod(root + semitones, 12),
            bass = bass?.let { Math.floorMod(it + semitones, 12) },
            rootFlat = preferFlats,
        )

    fun format(): String = buildString {
        append(Notes.name(root, rootFlat))
        append(suffix)
        if (bass != null) append('/').append(Notes.name(bass, rootFlat))
    }

    /** Classes de nota (0..11) que formam o acorde. A fundamental vem primeiro. */
    fun pitchClasses(): List<Int> {
        val q = ChordQuality.parse(suffix)
        val result = LinkedHashSet<Int>()
        bass?.let { result.add(it) }
        q.intervals.forEach { result.add(Math.floorMod(root + it, 12)) }
        return result.toList()
    }

    /** Intervalos do acorde (sem baixo) — útil para saber o que é obrigatório. */
    fun quality(): ChordQuality = ChordQuality.parse(suffix)

    companion object {
        // Fundamental, sufixo, baixo opcional.
        private val REGEX = Regex(
            "^([A-G])([#b♯♭]?)" +
                "((?:maj|min|dim|aug|sus|add|m|M|º|°|ø|\\+|-|[0-9]|\\(|\\)|#|b|/(?=[0-9])|,|\\.)*)" +
                "(?:/([A-G][#b♯♭]?))?$"
        )

        fun parse(token: String): Chord? {
            val t = token.trim()
            if (t.isEmpty() || t.length > 16) return null
            val m = REGEX.matchEntire(t) ?: return null
            val root = Notes.index(m.groupValues[1] + m.groupValues[2]) ?: return null
            val suffix = m.groupValues[3]
            if (!ChordQuality.isPlausible(suffix)) return null
            val bass = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { Notes.index(it) ?: return null }
            val flat = m.groupValues[2] == "b" || m.groupValues[2] == "♭"
            return Chord(root, suffix, bass, flat)
        }
    }
}

/** Qualidade do acorde como intervalos em semitons a partir da fundamental. */
data class ChordQuality(val intervals: List<Int>, val optional: Set<Int>) {
    companion object {
        fun isPlausible(suffix: String): Boolean {
            if (suffix.count { it == '(' } != suffix.count { it == ')' }) return false
            // Rejeita sequências que não fazem sentido como acorde (ex.: "-----" de tablatura).
            if (suffix.contains("--") || suffix.contains("++")) return false
            return true
        }

        fun parse(rawSuffix: String): ChordQuality {
            var s = rawSuffix.replace("(", " ").replace(")", " ").replace(",", " ").replace(".", " ")
                .replace("º", "dim").replace("°", "dim").replace("♯", "#").replace("♭", "b")
            val notes = sortedSetOf(0)
            var third: Int? = 4
            var fifth: Int? = 7
            var seventh: Int? = null

            fun take(prefix: String): Boolean {
                val t = s.trimStart()
                return if (t.startsWith(prefix)) { s = t.removePrefix(prefix); true } else false
            }

            when {
                take("maj7") || take("7M") || take("M7") -> seventh = 11
                take("min") -> third = 3
                take("dim") -> { third = 3; fifth = 6; if (take("7")) seventh = 9 }
                take("ø") -> { third = 3; fifth = 6; seventh = 10 }
                take("aug") || take("+") -> fifth = 8
                take("m") -> third = 3
            }
            if (third == 3 && (take("7M") || take("maj7") || take("M7"))) seventh = 11

            val extras = mutableListOf<Int>()
            // Tokens restantes: 7, 9, 11, 13, sus2, sus4, add9, b5, #5, 5, 6, b9...
            val tokens = Regex("(sus[24]?|add|maj|M|[#b+-]?[0-9]+)").findAll(s).map { it.value }.toList()
            var add = false
            for (tk in tokens) {
                when {
                    tk == "add" -> add = true
                    tk.startsWith("sus") -> third = if (tk == "sus2") 2 else 5
                    tk == "maj" || tk == "M" -> seventh = 11
                    else -> {
                        val acc = when (tk[0]) { '#', '+' -> 1; 'b', '-' -> -1; else -> 0 }
                        val n = tk.trimStart('#', 'b', '+', '-').toIntOrNull() ?: continue
                        when (n) {
                            5 -> if (acc != 0) fifth = 7 + acc else if (tokens.size == 1 && rawSuffix == "5") third = null
                            6 -> extras += 9
                            7 -> if (seventh == null) seventh = if (third == 3 && fifth == 6 && rawSuffix.contains("dim")) 9 else 10
                            9, 2 -> { extras += 2 + acc; if (!add && acc == 0 && n == 9 && seventh == null && !rawSuffix.contains("(")) seventh = 10 }
                            // Na notação brasileira "D4" e "A7(4)" significam suspenso (sem a terça).
                            4 -> if (add) extras += 5 + acc else third = 5
                            11 -> { extras += 5 + acc; if (!add && acc == 0 && seventh == null && !rawSuffix.contains("(")) seventh = 10 }
                            13 -> { extras += 9 + acc; if (!add && seventh == null && !rawSuffix.contains("(")) seventh = 10 }
                        }
                        add = false
                    }
                }
            }
            third?.let { notes += it }
            fifth?.let { notes += it }
            seventh?.let { notes += it }
            notes += extras.map { Math.floorMod(it, 12) }
            val optional = if (fifth == 7) setOf(7) else emptySet()
            return ChordQuality(notes.toList(), optional)
        }
    }
}
