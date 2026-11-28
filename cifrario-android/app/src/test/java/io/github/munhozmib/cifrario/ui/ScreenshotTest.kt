package io.github.munhozmib.cifrario.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.munhozmib.cifrario.container
import io.github.munhozmib.cifrario.data.AppSettings
import io.github.munhozmib.cifrario.data.Instrument
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.Song
import io.github.munhozmib.cifrario.data.ThemeMode
import io.github.munhozmib.cifrario.ui.screens.HomeScreen
import io.github.munhozmib.cifrario.ui.screens.PlaylistScreen
import io.github.munhozmib.cifrario.ui.screens.ViewerContent
import io.github.munhozmib.cifrario.ui.theme.CifrarioTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Letra original escrita para os testes (não é de nenhuma música real). */
val SAMPLE = """
[Intro] G  D/F#  Em  C

[Primeira Parte]
G               D/F#
  A estrada é longa, mas eu sigo
Em                    C
  Com o violão e um velho amigo
G                D/F#
  Cada acorde é um caminho
Em         C          D4  D
  Que eu não faço sozinho

[Refrão]
C          G/B        Am7
  Canta comigo essa canção
C           G/B       D
  Que o palco é o coração
C          G/B        Em7
  Toca de novo, mais uma vez
Am7         D7(9)     G
  Até o fim do nosso mês

[Solo]
e|-----3-----2-----0-------|
B|---3---3-----3-----1-----|
G|-0---------2-----0-------|

[Segunda Parte]
G               D/F#
  A noite cai e a luz se acende
Em                    C
  Quem ouve a música entende
""".trimIndent()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = "../docs/screenshots"

    private fun seed(): Long = runBlocking {
        val repo = compose.activity.container.repository
        val s1 = repo.saveSong(Song(title = "A Estrada", artist = "Cifrário (exemplo)", content = SAMPLE, originalKey = "G", sourceSite = "Cifra Club"))
        val s2 = repo.saveSong(Song(title = "Noite de Domingo", artist = "Banda Exemplo", content = "C  G  Am  F\nLetra de exemplo", originalKey = "C", sourceSite = "Cifras", transpose = 2))
        val s3 = repo.saveSong(Song(title = "Mar Aberto", artist = "Artista Teste", content = "Em  C  G  D\nLetra", originalKey = "Em", sourceSite = "Ultimate Guitar", favorite = true))
        val p = repo.createPlaylist("Show de sexta", Instrument.VIOLAO)
        repo.createPlaylist("Missa de domingo", Instrument.TECLADO)
        repo.createPlaylist("Ensaio", Instrument.VIOLAO)
        listOf(s2, s1, s3).forEach { repo.addToPlaylist(p, it) }
        p
    }

    @Test fun home() {
        seed()
        compose.setContent { CifrarioTheme(ThemeMode.LIGHT) { HomeScreen({}, {}, {}, {}, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/01-playlists.png")
        compose.onNodeWithText("Músicas").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/02-musicas.png")
        compose.onNodeWithText("Buscar").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/03-buscar.png")
    }

    @Test fun playlist() {
        val p = seed()
        compose.setContent { CifrarioTheme(ThemeMode.LIGHT) { PlaylistScreen(p, {}, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/04-playlist.png")
    }

    @Test fun viewerGuitar() {
        compose.setContent {
            CifrarioTheme(ThemeMode.LIGHT) {
                Surface {
                    ViewerContent(
                        song = Song(id = 1, title = "A Estrada", artist = "Cifrário (exemplo)", content = SAMPLE, originalKey = "G"),
                        playlist = Playlist(id = 1, name = "Show de sexta"), settings = AppSettings(),
                        neighbors = 2L to 3L, position = 2 to 3,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/05-cifra-violao.png")
    }

    @Test fun viewerKeyboardDarkTransposed() {
        compose.setContent {
            CifrarioTheme(ThemeMode.DARK) {
                Surface {
                    ViewerContent(
                        song = Song(id = 1, title = "A Estrada", artist = "Cifrário (exemplo)", content = SAMPLE, originalKey = "G", transpose = 3),
                        playlist = Playlist(id = 2, name = "Missa de domingo", instrument = Instrument.TECLADO), settings = AppSettings(),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/06-cifra-teclado-escuro.png")
    }
}
