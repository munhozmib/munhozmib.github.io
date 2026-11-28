package io.github.munhozmib.cifrario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.munhozmib.cifrario.container
import io.github.munhozmib.cifrario.data.Song
import io.github.munhozmib.cifrario.music.ChordSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(songId: Long, done: () -> Unit) {
    val repo = LocalContext.current.container.repository
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Song?>(null) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var capo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    LaunchedEffect(songId) {
        if (songId > 0) repo.getSong(songId)?.let { s ->
            original = s; title = s.title; artist = s.artist; key = s.originalKey.orEmpty()
            capo = if (s.capo > 0) s.capo.toString() else ""; notes = s.notes.orEmpty(); content = s.content
        }
    }

    fun save() = scope.launch {
        val base = original ?: Song(title = "", artist = "", content = "")
        repo.saveSong(
            base.copy(
                title = title.trim().ifBlank { "Sem título" }, artist = artist.trim(), content = content.trimEnd(),
                originalKey = key.trim().ifBlank { null } ?: ChordSheet.guessKey(content),
                capo = capo.toIntOrNull()?.coerceIn(0, 12) ?: 0, notes = notes.trim().ifBlank { null },
            )
        )
        done()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = done) { Icon(Icons.Filled.Close, "Cancelar") } },
                title = { Text(if (songId > 0) "Editar cifra" else "Nova cifra") },
                actions = { IconButton(onClick = { save() }, enabled = content.isNotBlank() || title.isNotBlank()) { Icon(Icons.Filled.Check, "Salvar") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("Música") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(key, { key = it }, label = { Text("Tom") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    capo, { capo = it.filter(Char::isDigit).take(2) }, label = { Text("Capotraste") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Anotações (ex.: \"repetir refrão 2x\")") }, modifier = Modifier.fillMaxWidth())
            Text(
                "Escreva os acordes numa linha e a letra na linha de baixo. O app reconhece os acordes sozinho.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                content, { content = it }, label = { Text("Cifra") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
            )
        }
    }
}
