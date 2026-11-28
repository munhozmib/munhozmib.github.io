package io.github.munhozmib.cifrario.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.SongSummary
import io.github.munhozmib.cifrario.music.ChordSheet
import io.github.munhozmib.cifrario.ui.components.ConfirmDialog
import io.github.munhozmib.cifrario.ui.components.PlaylistPickerDialog
import kotlinx.coroutines.launch

@Composable
fun LibraryTab(
    vm: HomeViewModel, modifier: Modifier, openSong: (Long) -> Unit, newSong: () -> Unit, editSong: (Long) -> Unit,
    goSearch: () -> Unit,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val query by vm.libraryQuery.collectAsStateWithLifecycle()
    val sort by vm.librarySort.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<Pair<SongSummary, Pair<List<Playlist>, Set<Long>>>?>(null) }
    var deleting by remember { mutableStateOf<SongSummary?>(null) }

    Box(modifier.fillMaxSize()) {
        Column {
            OutlinedTextField(
                value = query, onValueChange = { vm.libraryQuery.value = it },
                placeholder = { Text("Procurar nas minhas músicas") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("title" to "Título", "artist" to "Artista", "recent" to "Recentes").forEach { (k, l) ->
                    FilterChip(selected = sort == k, onClick = { vm.librarySort.value = k }, label = { Text(l) })
                }
            }
            if (songs.isEmpty()) {
                EmptyState(
                    if (query.isBlank()) "Sua biblioteca está vazia" else "Nada encontrado",
                    if (query.isBlank()) "Busque cifras no Cifra Club, Cifras, Ultimate Guitar ou E-Chords e salve para usar offline."
                    else "Nenhuma música salva com \"$query\".",
                ) { if (query.isBlank()) Button(onClick = goSearch) { Text("Buscar cifras") } }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(songs, key = { it.id }) { s ->
                    SongRow(
                        s, onClick = { openSong(s.id) },
                        onPlaylists = {
                            scope.launch { picking = s to (vm.allPlaylists() to vm.playlistsOf(s.id)) }
                        },
                        onEdit = { editSong(s.id) },
                        onDelete = { deleting = s },
                    )
                    HorizontalDivider()
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = newSong, icon = { Icon(Icons.Filled.Edit, null) }, text = { Text("Escrever cifra") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    picking?.let { (song, data) ->
        PlaylistPickerDialog(
            playlists = data.first, initiallySelected = data.second,
            onDismiss = { picking = null },
            onCreate = { n, i -> scope.launch { vm.createPlaylist(n, i).join(); picking = song to (vm.allPlaylists() to data.second) } },
            onConfirm = { vm.setSongPlaylists(song.id, it); picking = null },
        )
    }
    deleting?.let { s ->
        ConfirmDialog("Excluir música?", "\"${s.title}\" será removida do aparelho e de todas as playlists.", "Excluir",
            onDismiss = { deleting = null }) { vm.deleteSong(s.id); deleting = null }
    }
}

@Composable
fun SongRow(
    s: SongSummary, onClick: () -> Unit, onPlaylists: (() -> Unit)? = null, onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null, trailingExtra: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = leading,
        headlineContent = { Text(s.title, maxLines = 1) },
        supportingContent = {
            val key = ChordSheet.transposeKey(s.originalKey, s.transpose)
            Text(listOfNotNull(s.artist.ifBlank { null }, key?.let { "Tom: $it" }, s.sourceSite).joinToString(" · "), maxLines = 1)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.favorite) Icon(Icons.Filled.Star, "Favorita", tint = MaterialTheme.colorScheme.tertiary)
                if (onPlaylists != null || onEdit != null || onDelete != null) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                    DropdownMenu(menu, { menu = false }) {
                        onPlaylists?.let { DropdownMenuItem(text = { Text("Playlists…") }, onClick = { menu = false; it() }) }
                        onEdit?.let { DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; it() }) }
                        onDelete?.let { DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; it() }) }
                    }
                }
                trailingExtra?.invoke()
            }
        },
    )
}
