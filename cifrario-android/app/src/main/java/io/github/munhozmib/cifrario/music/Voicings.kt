package io.github.munhozmib.cifrario.music

/** Desenho de acorde no violão: [frets] do Mi grave ao Mi agudo, -1 = corda abafada, 0 = solta. */
data class GuitarShape(val frets: List<Int>) {
    val fretted get() = frets.filter { it > 0 }
    /** Casa inicial do diagrama (1 quando cabe na primeira posição). */
    val baseFret: Int get() = fretted.minOrNull()?.let { if ((fretted.maxOrNull() ?: 0) <= 4) 1 else it } ?: 1

    override fun toString() = frets.joinToString("") { if (it < 0) "x" else if (it > 9) "($it)" else "$it" }
}

object GuitarVoicing {
    /** Afinação padrão (MIDI): E2 A2 D3 G3 B3 E4. */
    private val TUNING = intArrayOf(40, 45, 50, 55, 59, 64)
    private val cache = HashMap<String, GuitarShape?>()

    fun find(chord: Chord): GuitarShape? = synchronized(cache) {
        cache.getOrPut(chord.format()) { search(chord) }
    }

    private fun search(chord: Chord): GuitarShape? {
        val quality = chord.quality()
        val pcs = chord.pitchClasses().toSet()
        val required = quality.intervals.filter { it !in quality.optional }.map { Math.floorMod(chord.root + it, 12) }.toSet() +
            setOfNotNull(chord.bass)
        val bassPc = chord.bass ?: chord.root
        var best: GuitarShape? = null
        var bestScore = Double.MAX_VALUE

        for (base in 0..9) {
            val window = if (base == 0) 1..4 else base..(base + 3)
            val options = Array(6) { s ->
                val list = mutableListOf(-1)
                if (base <= 3 && (TUNING[s] % 12) in pcs) list += 0
                for (f in window) if ((TUNING[s] + f) % 12 in pcs) list += f
                list
            }
            val current = IntArray(6)
            fun rec(s: Int) {
                if (s == 6) {
                    val score = evaluate(current, required, bassPc, base) ?: return
                    if (score < bestScore) { bestScore = score; best = GuitarShape(current.toList()) }
                    return
                }
                for (f in options[s]) { current[s] = f; rec(s + 1) }
            }
            rec(0)
        }
        return best
    }

    private fun evaluate(fr: IntArray, required: Set<Int>, bassPc: Int, base: Int): Double? {
        val sounding = (0..5).filter { fr[it] >= 0 }
        if (sounding.size < 4 && !(sounding.size == 3 && required.size <= 3)) return null
        val lowest = sounding.first()
        if ((TUNING[lowest] + fr[lowest]) % 12 != bassPc) return null
        val notes = sounding.map { (TUNING[it] + fr[it]) % 12 }.toSet()
        if (!notes.containsAll(required)) return null

        val fretted = sounding.filter { fr[it] > 0 }
        val minF = fretted.minOfOrNull { fr[it] } ?: 0
        val maxF = fretted.maxOfOrNull { fr[it] } ?: 0
        if (maxF - minF > 3) return null
        val internalMutes = (lowest..5).count { fr[it] < 0 }
        if (internalMutes > 1) return null

        // Pestana: dedo 1 cobre todas as cordas a partir da primeira na casa mínima.
        val firstAtMin = fretted.firstOrNull { fr[it] == minF }
        val canBarre = firstAtMin != null &&
            (firstAtMin..5).all { fr[it] < 0 || fr[it] >= minF } &&
            fretted.count { fr[it] == minF } >= 2
        val fingers = if (canBarre) 1 + fretted.count { fr[it] > minF } else fretted.size
        if (fingers > 4) return null

        val opens = sounding.count { fr[it] == 0 }
        return base * 1.6 + (6 - sounding.size) * 1.2 + internalMutes * 4.0 + fingers * 0.4 +
            (if (canBarre && fretted.count { fr[it] > minF } > 0) 0.8 else 0.0) - opens * 0.3 +
            (maxF - minF) * 0.3
    }
}

object KeyboardVoicing {
    /** Notas (MIDI) a partir do Dó central, na posição fundamental, com baixo uma oitava abaixo. */
    fun notes(chord: Chord): List<Int> {
        val pcs = chord.quality().intervals.map { Math.floorMod(chord.root + it, 12) }
        var prev = 48 + chord.root // C3 + raiz
        if (prev >= 55) prev -= 12
        val out = mutableListOf(prev)
        for (pc in pcs.drop(1)) {
            var n = prev + 1
            while (n % 12 != pc) n++
            out += n
            prev = n
        }
        chord.bass?.let { b ->
            if (b != chord.root) {
                var n = out[0] - 1
                while (Math.floorMod(n, 12) != b) n--
                out.add(0, n)
            }
        }
        return out
    }
}
