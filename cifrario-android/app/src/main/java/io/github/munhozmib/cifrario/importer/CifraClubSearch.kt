package io.github.munhozmib.cifrario.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SearchResult(val title: String, val artist: String, val url: String, val site: Site)

/** Busca pelo mesmo serviço de sugestões que o site do Cifra Club usa. */
class CifraClubSearch(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://solr.sscdn.co/cc/h2/",
) {
    @Serializable private data class Doc(val t: String? = null, val m: String? = null, val a: String? = null, val d: String? = null, val u: String? = null)
    @Serializable private data class Resp(val docs: List<Doc> = emptyList())
    @Serializable private data class Root(val response: Resp = Resp())

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("q", query.trim()).build()
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body.string()
        }
        parse(body)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(body: String): List<SearchResult> {
            val start = body.indexOf('{')
            val end = body.lastIndexOf('}')
            if (start < 0 || end <= start) return emptyList()
            val root = json.decodeFromString(Root.serializer(), body.substring(start, end + 1))
            return root.response.docs
                .filter { it.t == "2" && !it.d.isNullOrBlank() && !it.u.isNullOrBlank() }
                .map { SearchResult(it.m.orEmpty(), it.a.orEmpty(), "https://www.cifraclub.com.br/${it.d}/${it.u}/", Site.CIFRA_CLUB) }
        }
    }
}
