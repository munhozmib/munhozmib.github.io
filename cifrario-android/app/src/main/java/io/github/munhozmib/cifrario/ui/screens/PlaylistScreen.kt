package io.github.munhozmib.cifrario.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.munhozmib.cifrario.AppContainer
import io.github.munhozmib.cifrario.container
import io.github.munhozmib.cifrario.data.EntryWithSong
import io.github.munhozmib.cifrario.ui.components.icon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class PlaylistViewModel(c: AppContainer, val playlistId: Long) : ViewModel() {
    private val repo = c.repository
    val playlist = repo.playlist(playlistId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val entries = repo.entries(playlistId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val library = repo.songSummaries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderEntries(ids) }
    fun remove(entryId: Long) = viewModelScope.launch { repo.removeEntry(entryId) }
    fun add(songIds: List<Long>) = viewModelScope.launch { songIds.forEach { repo.addToPlaylist(playlistId, it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(playlistId: Long, back: () -> Unit, openSong: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: PlaylistViewModel = viewModel { PlaylistViewModel(context.container, playlistId) }
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf(entries) }
    LaunchedEffect(entries) { items = entries }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        playlist?.let { Icon(it.instrument.icon, null, Modifier.padding(end = 8.dp)) }
                        Text(playlist?.name.orEmpty(), maxLines = 1)
                    }
                },
                actions = {
                    IconButton(onClick = { items.firstOrNull()?.let { openSong(it.song.id) } }, enabled = items.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, "Começar do início")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                text = { Text("Adicionar músicas") },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) EmptyState("Playlist vazia", "Adicione músicas da sua biblioteca. Depois arraste pela alça ≡ para colocar na ordem do repertório.")
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(items, key = { _, it -> it.entry.entryId }) { index, e ->
                    ReorderableItem(reorderState, key = e.entry.entryId) { dragging ->
                        Surface(tonalElevation = if (dragging) 6.dp else 0.dp, shadowElevation = if (dragging) 6.dp else 0.dp) {
                            SongRow(
                                e.song, onClick = { openSong(e.song.id) },
                                leading = {
                                    Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                },
                                trailingExtra = {
                                    IconButton(onClick = { vm.remove(e.entry.entryId) }) { Icon(Icons.Filled.RemoveCircleOutline, "Tirar da playlist") }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(onDragStopped = { vm.reorder(items.map { it.entry.entryId }) }),
                                    ) { Icon(Icons.Filled.DragHandle, "Arrastar para reordenar") }
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) AddSongsDialog(vm, entries, onDismiss = { adding = false })
}

@Composable
private fun AddSongsDialog(vm: PlaylistViewModel, current: List<EntryWithSong>, onDismiss: () -> Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(listOf<Long>()) }
    var filter by remember { mutableStateOf("") }
    val inPlaylist = current.map { it.song.id }.toSet()
    val shown = library.filter { filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar músicas") },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(filter, { filter = it }, placeholder = { Text("Filtrar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (library.isEmpty()) Text("Nenhuma música salva ainda. Use a aba Buscar.", Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (s.id in selected) selected - s.id else selected + s.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(s.id in selected, null)
                            androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp)) {
                                Text(s.title, maxLines = 1)
                                Text(
                                    s.artist + if (s.id in inPlaylist) " · já está na playlist" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        // A ordem de seleção vira a ordem na playlist.
        confirmButton = { TextButton(onClick = { vm.add(selected); onDismiss() }, enabled = selected.isNotEmpty()) { Text("Adicionar (${selected.size})") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
