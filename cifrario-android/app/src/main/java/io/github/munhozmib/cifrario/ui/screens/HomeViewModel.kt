package io.github.munhozmib.cifrario.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.munhozmib.cifrario.AppContainer
import io.github.munhozmib.cifrario.data.Instrument
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.PlaylistWithCount
import io.github.munhozmib.cifrario.data.SongSummary
import io.github.munhozmib.cifrario.importer.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository

    val playlists: StateFlow<List<PlaylistWithCount>> =
        repo.playlistsWithCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryQuery = MutableStateFlow("")
    val librarySort = MutableStateFlow("title")
    val songs: StateFlow<List<SongSummary>> = combine(libraryQuery, librarySort) { q, s -> q to s }
        .flatMapLatest { (q, s) -> repo.songSummaries(q, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val search = MutableStateFlow(SearchState())

    fun createPlaylist(name: String, instrument: Instrument) = viewModelScope.launch { repo.createPlaylist(name, instrument) }
    fun updatePlaylist(p: Playlist) = viewModelScope.launch { repo.updatePlaylist(p) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repo.deletePlaylist(id) }
    fun reorderPlaylists(ids: List<Long>) = viewModelScope.launch { repo.reorderPlaylists(ids) }
    fun deleteSong(id: Long) = viewModelScope.launch { repo.deleteSong(id) }

    suspend fun allPlaylists() = repo.allPlaylists()
    suspend fun playlistsOf(songId: Long) = repo.playlistsOf(songId).toSet()
    fun setSongPlaylists(songId: Long, ids: Set<Long>) = viewModelScope.launch { repo.setSongPlaylists(songId, ids) }

    fun searchCifraClub(query: String) {
        search.update { it.copy(query = query, loading = true, error = null) }
        viewModelScope.launch {
            try {
                val r = c.cifraClubSearch.search(query)
                search.update { it.copy(loading = false, results = r, searched = true) }
            } catch (e: Exception) {
                search.update { it.copy(loading = false, error = "Sem conexão com o Cifra Club. Verifique a internet.", searched = true) }
            }
        }
    }
}
