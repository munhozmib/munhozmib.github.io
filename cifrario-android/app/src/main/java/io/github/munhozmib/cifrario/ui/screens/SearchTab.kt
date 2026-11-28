package io.github.munhozmib.cifrario.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.munhozmib.cifrario.importer.Site

@Suppress("DEPRECATION")
@Composable
fun SearchTab(vm: HomeViewModel, modifier: Modifier, openBrowser: (String) -> Unit) {
    val state by vm.search.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(state.query) }
    var site by rememberSaveable { mutableStateOf(Site.CIFRA_CLUB) }
    val clipboard = LocalClipboardManager.current

    fun go() {
        if (query.isBlank()) return
        val asUrl = query.trim()
        if (asUrl.startsWith("http") && Site.fromUrl(asUrl) != null) { openBrowser(asUrl); return }
        if (site == Site.CIFRA_CLUB) vm.searchCifraClub(query) else openBrowser(site.searchUrl(query))
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Música ou artista (ou cole um link)") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.text?.let { Regex("https?://\\S+").find(it)?.value }?.let { query = it }
                }) { Icon(Icons.Filled.Link, "Colar link") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Site.entries.forEach { s -> FilterChip(selected = site == s, onClick = { site = s }, label = { Text(s.label) }) }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { go() }, enabled = query.isNotBlank()) { Text("Buscar em ${site.label}") }
            if (site == Site.CIFRA_CLUB && query.isNotBlank()) {
                TextButton(onClick = { openBrowser(site.searchUrl(query)) }) { Text("Abrir no site") }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            state.error?.let { err -> item { Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (site == Site.CIFRA_CLUB && state.searched && state.results.isEmpty() && state.error == null && !state.loading) {
                item { Text("Nenhum resultado.", Modifier.padding(16.dp)) }
            }
            if (site == Site.CIFRA_CLUB) items(state.results, key = { it.url }) { r ->
                ListItem(
                    modifier = Modifier.clickable { openBrowser(r.url) },
                    headlineContent = { Text(r.title) },
                    supportingContent = { Text(r.artist) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                )
                HorizontalDivider()
            }
            if (state.results.isEmpty() || site != Site.CIFRA_CLUB) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Como funciona", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. Busque a música.\n2. Abra a cifra no navegador do app (escolha a versão e o tom que quiser).\n" +
                            "3. Toque em \"Salvar\" — ela fica guardada no aparelho e funciona sem internet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Abrir site", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Site.entries.forEach { s ->
                        OutlinedButton(onClick = { openBrowser(s.home) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Public, null); Text("  ${s.label}")
                        }
                    }
                }
            }
        }
    }
}
