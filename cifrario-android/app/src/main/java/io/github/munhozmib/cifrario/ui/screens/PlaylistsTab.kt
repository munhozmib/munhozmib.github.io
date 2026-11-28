package io.github.munhozmib.cifrario.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.PlaylistWithCount
import io.github.munhozmib.cifrario.ui.components.ConfirmDialog
import io.github.munhozmib.cifrario.ui.components.PlaylistFormDialog
import io.github.munhozmib.cifrario.ui.components.icon
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistsTab(vm: HomeViewModel, modifier: Modifier, open: (Long) -> Unit) {
    val data by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    // Cópia local para arrastar com fluidez; grava a ordem ao soltar.
    var items by remember { mutableStateOf(data) }
    LaunchedEffect(data) { items = data }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Box(modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            EmptyState(
                "Nenhuma playlist ainda",
                "Crie uma playlist (ex.: \"Missa de domingo\", \"Show sexta\") e adicione as músicas na ordem em que vai tocar.",
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.playlist.id }) { item ->
                ReorderableItem(reorderState, key = item.playlist.id) { dragging ->
                    PlaylistCard(
                        item, dragging,
                        onClick = { open(item.playlist.id) },
                        onEdit = { editing = item.playlist },
                        onDelete = { deleting = item.playlist },
                        handle = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(onDragStopped = { vm.reorderPlaylists(items.map { it.playlist.id }) }),
                            ) { Icon(Icons.Filled.DragHandle, "Arrastar para reordenar") }
                        },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Nova playlist") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) PlaylistFormDialog("Nova playlist", confirmLabel = "Criar", onDismiss = { creating = false }) { n, i ->
        vm.createPlaylist(n, i); creating = false
    }
    editing?.let { p ->
        PlaylistFormDialog("Editar playlist", p.name, p.instrument, onDismiss = { editing = null }) { n, i ->
            vm.updatePlaylist(p.copy(name = n.trim(), instrument = i)); editing = null
        }
    }
    deleting?.let { p ->
        ConfirmDialog("Excluir playlist?", "\"${p.name}\" será excluída. As músicas continuam salvas na sua biblioteca.", "Excluir",
            onDismiss = { deleting = null }) { vm.deletePlaylist(p.id); deleting = null }
    }
}

@Composable
private fun PlaylistCard(
    item: PlaylistWithCount, dragging: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    handle: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(if (dragging) 8.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { Icon(item.playlist.instrument.icon, null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(item.playlist.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text("${item.playlist.instrument.label} · ${item.songCount} ${if (item.songCount == 1) "música" else "músicas"}")
            },
            trailingContent = {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Renomear / instrumento") }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; onDelete() })
                        }
                    }
                    handle()
                }
            },
        )
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
