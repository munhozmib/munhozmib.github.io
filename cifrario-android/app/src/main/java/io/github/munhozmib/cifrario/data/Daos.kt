package io.github.munhozmib.cifrario.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query(
        "SELECT id, title, artist, sourceSite, originalKey, transpose, favorite FROM songs " +
            "WHERE (:q = '' OR title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%') " +
            "ORDER BY CASE WHEN :sort = 'artist' THEN artist END COLLATE NOCASE, " +
            "CASE WHEN :sort = 'recent' THEN -createdAt END, title COLLATE NOCASE"
    )
    fun observeSummaries(q: String, sort: String): Flow<List<SongSummary>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observe(id: Long): Flow<Song?>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: Long): Song?

    @Query("SELECT * FROM songs")
    suspend fun all(): List<Song>

    @Query("SELECT * FROM songs WHERE sourceUrl = :url LIMIT 1")
    suspend fun findByUrl(url: String): Song?

    @Insert
    suspend fun insert(song: Song): Long

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount " +
            "FROM playlists p ORDER BY p.position, p.id"
    )
    fun observeWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists ORDER BY position, id")
    suspend fun all(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): Playlist?

    @Insert
    suspend fun insert(p: Playlist): Long

    @Update
    suspend fun update(p: Playlist)

    @Update
    suspend fun updateAll(p: List<Playlist>)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlists")
    suspend fun nextPosition(): Int

    // ---- Entradas (músicas da playlist) ----

    @Query(
        "SELECT e.*, s.id AS s_id, s.title AS s_title, s.artist AS s_artist, s.sourceSite AS s_sourceSite, " +
            "s.originalKey AS s_originalKey, s.transpose AS s_transpose, s.favorite AS s_favorite " +
            "FROM playlist_entries e JOIN songs s ON s.id = e.songId " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position, e.entryId"
    )
    fun observeEntries(playlistId: Long): Flow<List<EntryWithSong>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position, entryId")
    suspend fun entries(playlistId: Long): List<PlaylistEntry>

    @Query("SELECT * FROM playlist_entries")
    suspend fun allEntries(): List<PlaylistEntry>

    @Query("SELECT playlistId FROM playlist_entries WHERE songId = :songId")
    suspend fun playlistsOf(songId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun nextEntryPosition(playlistId: Long): Int

    @Insert
    suspend fun insertEntry(e: PlaylistEntry): Long

    @Query("DELETE FROM playlist_entries WHERE entryId = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("UPDATE playlist_entries SET position = :position WHERE entryId = :entryId")
    suspend fun setEntryPosition(entryId: Long, position: Int)

    @Transaction
    suspend fun reorderEntries(entryIdsInOrder: List<Long>) {
        entryIdsInOrder.forEachIndexed { i, id -> setEntryPosition(id, i) }
    }

    @Query("UPDATE playlists SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Transaction
    suspend fun reorderPlaylists(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { i, id -> setPosition(id, i) }
    }
}
