package io.github.munhozmib.cifrario.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.munhozmib.cifrario.AppContainer
import io.github.munhozmib.cifrario.data.AppSettings
import io.github.munhozmib.cifrario.data.Instrument
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewerViewModel(private val c: AppContainer, val songId: Long, val playlistId: Long) : ViewModel() {
    private val repo = c.repository
    val song: StateFlow<Song?> = repo.song(songId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val playlist: StateFlow<Playlist?> =
        (if (playlistId > 0) repo.playlist(playlistId) else flowOf(null)).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Vizinhos na playlist (anterior, próxima). */
    val neighbors = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val position = MutableStateFlow<Pair<Int, Int>?>(null)

    init {
        if (playlistId > 0) viewModelScope.launch {
            val ids = repo.playlistSongIds(playlistId)
            val i = ids.indexOf(songId)
            if (i >= 0) {
                neighbors.value = ids.getOrNull(i - 1) to ids.getOrNull(i + 1)
                position.value = (i + 1) to ids.size
            }
        }
    }

    fun update(transform: (Song) -> Song) {
        val s = song.value ?: return
        viewModelScope.launch { repo.saveSong(transform(s)) }
    }

    fun setInstrument(i: Instrument) = update { it.copy(instrument = i) }
    suspend fun allPlaylists() = repo.allPlaylists()
    suspend fun playlistsOfSong() = repo.playlistsOf(songId).toSet()
    fun setPlaylists(ids: Set<Long>) = viewModelScope.launch { repo.setSongPlaylists(songId, ids) }
    fun createPlaylist(name: String, instrument: Instrument) = viewModelScope.launch { repo.createPlaylist(name, instrument) }
}
