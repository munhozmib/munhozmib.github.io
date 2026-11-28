package io.github.munhozmib.cifrario.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Instrument(val label: String) { VIOLAO("Violão"), TECLADO("Teclado") }

@Entity(tableName = "songs", indices = [Index("sourceUrl")])
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    /** Texto puro da cifra — guardado no aparelho, funciona offline. */
    val content: String,
    val sourceSite: String? = null,
    val sourceUrl: String? = null,
    val originalKey: String? = null,
    /** Instrumento preferido desta música (null = usa o da playlist / padrão). */
    val instrument: Instrument? = null,
    val transpose: Int = 0,
    val capo: Int = 0,
    /** Velocidade de rolagem (1..20) salva por música; null = padrão. */
    val scrollSpeed: Int? = null,
    val fontSize: Float? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    /** Cópia da página original (MHTML) salva no aparelho, para ver exatamente como no site. */
    val archivePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val instrument: Instrument = Instrument.VIOLAO,
    /** Ordem da playlist na tela inicial. */
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playlistId"), Index("songId")],
)
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    @ColumnInfo(name = "songCount") val songCount: Int,
)

data class EntryWithSong(
    @Embedded val entry: PlaylistEntry,
    @Embedded(prefix = "s_") val song: SongSummary,
)

/** Versão leve de [Song] para listas (sem o conteúdo inteiro). */
data class SongSummary(
    val id: Long,
    val title: String,
    val artist: String,
    val sourceSite: String?,
    val originalKey: String?,
    val transpose: Int,
    val favorite: Boolean,
)
