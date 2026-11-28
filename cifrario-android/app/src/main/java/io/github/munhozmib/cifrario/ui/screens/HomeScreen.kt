package io.github.munhozmib.cifrario.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.munhozmib.cifrario.container

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    openPlaylist: (Long) -> Unit,
    openSong: (Long) -> Unit,
    openBrowser: (String) -> Unit,
    newSong: () -> Unit,
    editSong: (Long) -> Unit,
    openSettings: () -> Unit,
    initialTab: Int = 0,
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel { HomeViewModel(context.container) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val titles = listOf("Playlists", "Músicas", "Buscar")
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cifrário", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, "Configurações") } },
            )
        },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.AutoMirrored.Filled.QueueMusic, Icons.Filled.LibraryMusic, Icons.Filled.Search)
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(t) })
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> PlaylistsTab(vm, m, openPlaylist)
            1 -> LibraryTab(vm, m, openSong, newSong, editSong, goSearch = { tab = 2 })
            else -> SearchTab(vm, m, openBrowser)
        }
    }
}
