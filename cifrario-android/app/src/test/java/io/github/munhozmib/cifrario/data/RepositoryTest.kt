package io.github.munhozmib.cifrario.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = Repository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun song(t: String) = repo.saveSong(Song(title = t, artist = "Artista $t", content = "C G\nletra $t", sourceUrl = "https://x/$t"))

    @Test fun playlistOrderingAndReorder() = runTest {
        val a = song("A"); val b = song("B"); val c = song("C")
        val p = repo.createPlaylist("Domingo", Instrument.TECLADO)
        repo.addToPlaylist(p, a); repo.addToPlaylist(p, b); repo.addToPlaylist(p, c)
        assertEquals(listOf(a, b, c), repo.playlistSongIds(p))

        val entries = repo.entries(p).first()
        repo.reorderEntries(listOf(entries[2].entry.entryId, entries[0].entry.entryId, entries[1].entry.entryId))
        assertEquals(listOf(c, a, b), repo.playlistSongIds(p))
        assertEquals(3, repo.playlistsWithCount().first().single().songCount)

        // Excluir a música tira ela da playlist.
        repo.deleteSong(a)
        assertEquals(listOf(c, b), repo.playlistSongIds(p))
    }

    @Test fun playlistsReorderAndMembership() = runTest {
        val p1 = repo.createPlaylist("Um", Instrument.VIOLAO)
        val p2 = repo.createPlaylist("Dois", Instrument.VIOLAO)
        repo.reorderPlaylists(listOf(p2, p1))
        assertEquals(listOf("Dois", "Um"), repo.allPlaylists().map { it.name })

        val s = song("X")
        repo.setSongPlaylists(s, setOf(p1, p2))
        assertEquals(setOf(p1, p2), repo.playlistsOf(s).toSet())
        repo.setSongPlaylists(s, setOf(p2))
        assertEquals(listOf(p2), repo.playlistsOf(s))
    }

    @Test fun searchAndSort() = runTest {
        song("Banana"); song("Abacate")
        assertEquals(listOf("Abacate", "Banana"), repo.songSummaries().first().map { it.title })
        assertEquals(listOf("Banana"), repo.songSummaries("nan").first().map { it.title })
    }

    @Test fun backupRoundTrip() = runTest {
        val a = song("A"); val b = song("B")
        repo.saveSong(repo.getSong(a)!!.copy(transpose = 3, scrollSpeed = 7))
        val p = repo.createPlaylist("Show", Instrument.VIOLAO)
        repo.addToPlaylist(p, b); repo.addToPlaylist(p, a)
        val json = repo.exportJson()

        val other = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val repo2 = Repository(other)
        assertEquals(2 to 1, repo2.importJson(json))
        val pl = repo2.allPlaylists().single()
        assertEquals("Show", pl.name)
        val ids = repo2.playlistSongIds(pl.id)
        assertEquals(listOf("B", "A"), ids.map { repo2.getSong(it)!!.title })
        assertEquals(3, repo2.getSong(ids[1])!!.transpose)
        assertEquals(7, repo2.getSong(ids[1])!!.scrollSpeed)

        // Importar de novo não duplica músicas iguais.
        assertEquals(0 to 1, repo2.importJson(json))
        other.close()
    }
}
