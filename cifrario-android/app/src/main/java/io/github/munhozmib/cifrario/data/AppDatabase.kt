package io.github.munhozmib.cifrario.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun fromInstrument(i: Instrument?): String? = i?.name
    @TypeConverter fun toInstrument(s: String?): Instrument? = s?.let { runCatching { Instrument.valueOf(it) }.getOrNull() }
}

@Database(entities = [Song::class, Playlist::class, PlaylistEntry::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun playlists(): PlaylistDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cifrario.db").addMigrations(MIGRATION_1_2).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN archivePath TEXT")
            }
        }

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
