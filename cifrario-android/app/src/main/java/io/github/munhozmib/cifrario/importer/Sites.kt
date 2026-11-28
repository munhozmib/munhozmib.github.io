package io.github.munhozmib.cifrario.importer

import java.net.URI
import java.net.URLEncoder

/** Sites suportados. O navegador interno só abre estes domínios (e a busca do Google restrita a eles). */
enum class Site(val label: String, val domain: String, val home: String) {
    CIFRA_CLUB("Cifra Club", "cifraclub.com.br", "https://www.cifraclub.com.br/"),
    CIFRAS("Cifras", "cifras.com.br", "https://www.cifras.com.br/"),
    ULTIMATE_GUITAR("Ultimate Guitar", "ultimate-guitar.com", "https://www.ultimate-guitar.com/"),
    E_CHORDS("E-Chords", "e-chords.com", "https://www.e-chords.com/");

    /** URL de busca a abrir no navegador interno. */
    fun searchUrl(query: String): String {
        val q = enc(query)
        return when (this) {
            CIFRA_CLUB -> "https://www.cifraclub.com.br/?q=$q"
            ULTIMATE_GUITAR -> "https://www.ultimate-guitar.com/search.php?search_type=title&value=$q"
            E_CHORDS -> "https://www.e-chords.com/search-all/$q"
            CIFRAS -> "https://www.google.com/search?q=" + enc("$query site:$domain")
        }
    }

    fun matches(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == domain || h.endsWith(".$domain")
    }

    companion object {
        fun fromUrl(url: String?): Site? {
            val host = runCatching { URI(url ?: return null).host }.getOrNull()
            return entries.firstOrNull { it.matches(host) }
        }

        /** Hosts que o navegador interno pode abrir como página principal. */
        fun isAllowedNavigation(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (uri.scheme != "https" && uri.scheme != "http") return false
            val host = uri.host?.lowercase() ?: return false
            if (entries.any { it.matches(host) }) return true
            // Busca do Google (só a página de resultados / redirecionamento).
            val isGoogle = host == "google.com" || host.endsWith(".google.com") || host.matches(Regex("(www\\.)?google\\.[a-z.]+"))
            return isGoogle && (uri.path.startsWith("/search") || uri.path.startsWith("/url"))
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
