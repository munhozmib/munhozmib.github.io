package io.github.munhozmib.cifrario.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Repository(private val db: AppDatabase) {
    private val songs = db.songs()
    private val playlists = db.playlists()

    fun songSummaries(query: String = "", sort: String = "title"): Flow<List<SongSummary>> =
        songs.observeSummaries(query.trim(), sort)

    fun song(id: Long): Flow<Song?> = songs.observe(id)
    suspend fun getSong(id: Long) = songs.get(id)
    suspend fun findByUrl(url: String) = songs.findByUrl(url)

    suspend fun saveSong(song: Song): Long =
        if (song.id == 0L) songs.insert(song) else { songs.update(song.copy(updatedAt = System.currentTimeMillis())); song.id }

    suspend fun deleteSong(id: Long) = songs.deleteById(id)

    fun playlistsWithCount() = playlists.observeWithCount()
    fun playlist(id: Long) = playlists.observe(id)
    fun entries(playlistId: Long) = playlists.observeEntries(playlistId)
    suspend fun allPlaylists() = playlists.all()
    suspend fun playlistsOf(songId: Long) = playlists.playlistsOf(songId)

    suspend fun createPlaylist(name: String, instrument: Instrument): Long =
        playlists.insert(Playlist(name = name.trim(), instrument = instrument, position = playlists.nextPosition()))

    suspend fun updatePlaylist(p: Playlist) = playlists.update(p)
    suspend fun deletePlaylist(id: Long) = playlists.delete(id)
    suspend fun reorderPlaylists(ids: List<Long>) = playlists.reorderPlaylists(ids)

    suspend fun addToPlaylist(playlistId: Long, songId: Long) {
        playlists.insertEntry(PlaylistEntry(playlistId = playlistId, songId = songId, position = playlists.nextEntryPosition(playlistId)))
    }

    suspend fun removeEntry(entryId: Long) = playlists.deleteEntry(entryId)
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = playlists.removeSong(playlistId, songId)
    suspend fun reorderEntries(entryIds: List<Long>) = playlists.reorderEntries(entryIds)

    /** Lista de IDs de músicas na ordem da playlist (para "próxima / anterior" no modo palco). */
    suspend fun playlistSongIds(playlistId: Long): List<Long> = playlists.entries(playlistId).map { it.songId }

    /** Define em quais playlists a música está (adiciona/remove para bater com [ids]). */
    suspend fun setSongPlaylists(songId: Long, ids: Set<Long>) = db.withTransaction {
        val current = playlists.playlistsOf(songId).toSet()
        (ids - current).forEach { addToPlaylist(it, songId) }
        (current - ids).forEach { playlists.removeSong(it, songId) }
    }

    // ---------------- Backup ----------------

    suspend fun exportJson(): String {
        val backup = Backup(
            version = 1,
            songs = songs.all().map { BackupSong.from(it) },
            playlists = playlists.all().map { p ->
                BackupPlaylist(p.id, p.name, p.instrument.name, p.position, playlists.entries(p.id).map { it.songId })
            },
        )
        return json.encodeToString(Backup.serializer(), backup)
    }

    /** Importa um backup somando ao que já existe (não apaga nada). Retorna (músicas, playlists). */
    suspend fun importJson(text: String): Pair<Int, Int> = db.withTransaction {
        val backup = json.decodeFromString(Backup.serializer(), text)
        val idMap = HashMap<Long, Long>()
        var newSongs = 0
        for (s in backup.songs) {
            val existing = s.sourceUrl?.let { songs.findByUrl(it) }?.takeIf { it.content == s.content }
            idMap[s.id] = existing?.id ?: songs.insert(s.toSong()).also { newSongs++ }
        }
        var pos = playlists.nextPosition()
        for (p in backup.playlists) {
            val instrument = runCatching { Instrument.valueOf(p.instrument) }.getOrDefault(Instrument.VIOLAO)
            val pid = playlists.insert(Playlist(name = p.name, instrument = instrument, position = pos++))
            p.songIds.forEachIndexed { i, sid ->
                idMap[sid]?.let { playlists.insertEntry(PlaylistEntry(playlistId = pid, songId = it, position = i)) }
            }
        }
        newSongs to backup.playlists.size
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    }
}

@Serializable
data class Backup(val version: Int, val app: String = "Cifrário", val songs: List<BackupSong>, val playlists: List<BackupPlaylist>)

@Serializable
data class BackupPlaylist(val id: Long, val name: String, val instrument: String, val position: Int, val songIds: List<Long>)

@Serializable
data class BackupSong(
    val id: Long, val title: String, val artist: String, val content: String,
    val sourceSite: String? = null, val sourceUrl: String? = null, val originalKey: String? = null,
    val instrument: String? = null, val transpose: Int = 0, val capo: Int = 0, val scrollSpeed: Int? = null,
    val fontSize: Float? = null, val notes: String? = null, val favorite: Boolean = false, val createdAt: Long = 0,
) {
    fun toSong() = Song(
        title = title, artist = artist, content = content, sourceSite = sourceSite, sourceUrl = sourceUrl,
        originalKey = originalKey, instrument = instrument?.let { runCatching { Instrument.valueOf(it) }.getOrNull() },
        transpose = transpose, capo = capo, scrollSpeed = scrollSpeed, fontSize = fontSize, notes = notes,
        favorite = favorite, createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
    )

    companion object {
        fun from(s: Song) = BackupSong(
            s.id, s.title, s.artist, s.content, s.sourceSite, s.sourceUrl, s.originalKey, s.instrument?.name,
            s.transpose, s.capo, s.scrollSpeed, s.fontSize, s.notes, s.favorite, s.createdAt,
        )
    }
}
