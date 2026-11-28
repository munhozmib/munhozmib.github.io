package io.github.munhozmib.cifrario.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import io.github.munhozmib.cifrario.container
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.Song
import io.github.munhozmib.cifrario.importer.ChordPageParser
import io.github.munhozmib.cifrario.importer.ParseException
import io.github.munhozmib.cifrario.importer.ParsedSong
import io.github.munhozmib.cifrario.importer.Site
import io.github.munhozmib.cifrario.music.ChordSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/** Pede o HTML renderizado da página (após o JavaScript do site rodar). */
private const val GET_HTML = "(function(){return document.documentElement.outerHTML;})()"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(startUrl: String, back: () -> Unit, openSong: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ParsedSong?>(null) }
    var existing by remember { mutableStateOf<Song?>(null) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    fun extract() {
        val wv = webView ?: return
        extracting = true
        wv.evaluateJavascript(GET_HTML) { raw ->
            scope.launch {
                try {
                    val html = Json.parseToJsonElement(raw).jsonPrimitive.content
                    val parsed = withContext(Dispatchers.Default) { ChordPageParser.parse(html, wv.url ?: currentUrl) }
                    existing = repo.findByUrl(parsed.url)
                    playlists = repo.allPlaylists()
                    preview = parsed
                } catch (e: ParseException) {
                    snackbar.showSnackbar(e.message ?: "Não foi possível ler a cifra.")
                } catch (e: Exception) {
                    snackbar.showSnackbar("Não foi possível ler a cifra desta página.")
                } finally {
                    extracting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.Filled.Close, "Fechar") } },
                    title = {
                        Column {
                            Text(title.ifBlank { Site.fromUrl(currentUrl)?.label ?: "Navegador" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(currentUrl, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = {
                        if (canGoBack) IconButton(onClick = { webView?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar página") }
                        IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, "Recarregar") }
                    },
                )
                if (progress in 1..99 || extracting) LinearProgressIndicator(progress = { if (extracting) 1f else progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!extracting) extract() },
                icon = { Icon(Icons.Filled.DownloadForOffline, null) },
                text = { Text(if (extracting) "Lendo…" else "Salvar cifra") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            override fun onReceivedTitle(view: WebView?, t: String?) { title = t.orEmpty() }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val url = request.url.toString()
                                if (Site.isAllowedNavigation(url)) return false
                                Toast.makeText(ctx, "Link fora dos sites de cifras — bloqueado", Toast.LENGTH_SHORT).show()
                                return true
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                currentUrl = url ?: currentUrl; canGoBack = view.canGoBack()
                            }
                            // Se o processo do WebView cair (pouca memória), não derruba o app: volta para a tela anterior.
                            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                webView = null
                                (view.parent as? android.view.ViewGroup)?.removeView(view)
                                view.destroy()
                                back()
                                return true
                            }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                currentUrl = url ?: currentUrl; canGoBack = view.canGoBack()
                            }
                        }
                        loadUrl(startUrl)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }

    preview?.let { p ->
        SavePreviewDialog(
            parsed = p, existing = existing, playlists = playlists,
            onDismiss = { preview = null },
            onSave = { title, artist, key, selected, replace ->
                preview = null
                scope.launch {
                    val base = if (replace && existing != null) existing!! else Song(title = "", artist = "", content = "")
                    val id = repo.saveSong(
                        base.copy(
                            title = title.trim().ifBlank { "Sem título" }, artist = artist.trim(), content = p.content,
                            originalKey = key.trim().ifBlank { null } ?: ChordSheet.guessKey(p.content),
                            sourceSite = p.site?.label, sourceUrl = p.url,
                            transpose = if (replace) base.transpose else 0,
                        )
                    )
                    selected.forEach { pid -> if (pid !in repo.playlistsOf(id)) repo.addToPlaylist(pid, id) }
                    val r = snackbar.showSnackbar("\"${title.trim()}\" salva offline", actionLabel = "Abrir", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) openSong(id)
                }
            },
        )
    }
}

@Composable
private fun SavePreviewDialog(
    parsed: ParsedSong, existing: Song?, playlists: List<Playlist>, onDismiss: () -> Unit,
    onSave: (title: String, artist: String, key: String, playlists: Set<Long>, replace: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(parsed.title) }
    var artist by remember { mutableStateOf(parsed.artist) }
    var key by remember { mutableStateOf(parsed.key ?: ChordSheet.guessKey(parsed.content).orEmpty()) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Você já tem esta cifra" else "Salvar cifra") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Música") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text("Tom") }, singleLine = true)
                Text(
                    parsed.content.lines().take(10).joinToString("\n"),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 10,
                    modifier = Modifier.heightIn(max = 160.dp),
                )
                if (playlists.isNotEmpty()) {
                    Text("Adicionar às playlists", style = MaterialTheme.typography.titleSmall)
                    playlists.forEach { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (pl.id in selected) selected - pl.id else selected + pl.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Checkbox(pl.id in selected, null); Text(pl.name) }
                    }
                }
            }
        },
        confirmButton = {
            if (existing != null) TextButton(onClick = { onSave(title, artist, key, selected, true) }) { Text("Atualizar") }
            else TextButton(onClick = { onSave(title, artist, key, selected, false) }) { Text("Salvar") }
        },
        dismissButton = {
            if (existing != null) TextButton(onClick = { onSave(title, artist, key, selected, false) }) { Text("Salvar cópia") }
            else TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
