package io.github.munhozmib.cifrario.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.munhozmib.cifrario.data.Instrument
import io.github.munhozmib.cifrario.data.Playlist

val Instrument.icon: ImageVector get() = if (this == Instrument.TECLADO) Icons.Filled.Piano else Icons.Filled.MusicNote

@Composable
fun InstrumentChooser(value: Instrument, onChange: (Instrument) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Instrument.entries.forEach { i ->
            FilterChip(
                selected = value == i, onClick = { onChange(i) }, label = { Text(i.label) },
                leadingIcon = { Icon(i.icon, null) },
            )
        }
    }
}

@Composable
fun PlaylistFormDialog(
    title: String,
    initialName: String = "",
    initialInstrument: Instrument = Instrument.VIOLAO,
    confirmLabel: String = "Salvar",
    onDismiss: () -> Unit,
    onConfirm: (String, Instrument) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var instrument by remember { mutableStateOf(initialInstrument) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                InstrumentChooser(instrument) { instrument = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, instrument) }, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Marcar em quais playlists uma música fica. */
@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    initiallySelected: Set<Long>,
    onDismiss: () -> Unit,
    onCreate: (String, Instrument) -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember(initiallySelected) { mutableStateOf(initiallySelected) }
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        PlaylistFormDialog("Nova playlist", confirmLabel = "Criar", onDismiss = { creating = false }) { n, i ->
            onCreate(n, i); creating = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlists") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (p.id in selected) selected - p.id else selected + p.id
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(p.id in selected, null)
                        Icon(p.instrument.icon, null, Modifier.padding(horizontal = 8.dp))
                        Text(p.name)
                    }
                }
                item {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, null); Text(" Nova playlist")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
