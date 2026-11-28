package io.github.munhozmib.cifrario.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromInstrument(i: Instrument?): String? = i?.name
    @TypeConverter fun toInstrument(s: String?): Instrument? = s?.let { runCatching { Instrument.valueOf(it) }.getOrNull() }
}

@Database(entities = [Song::class, Playlist::class, PlaylistEntry::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun playlists(): PlaylistDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cifrario.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
