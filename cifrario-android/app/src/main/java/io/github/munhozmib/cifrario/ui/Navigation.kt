package io.github.munhozmib.cifrario.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.munhozmib.cifrario.importer.Site
import io.github.munhozmib.cifrario.ui.screens.BrowserScreen
import io.github.munhozmib.cifrario.ui.screens.EditorScreen
import io.github.munhozmib.cifrario.ui.screens.HomeScreen
import io.github.munhozmib.cifrario.ui.screens.PlaylistScreen
import io.github.munhozmib.cifrario.ui.screens.SettingsScreen
import io.github.munhozmib.cifrario.ui.screens.ViewerScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class PlaylistRoute(val id: Long)
@Serializable data class ViewerRoute(val songId: Long, val playlistId: Long = -1)
@Serializable data class BrowserRoute(val url: String, val playlistId: Long = -1)
@Serializable data class EditorRoute(val songId: Long = 0)
@Serializable object SettingsRoute

@Composable
fun CifrarioNavHost(sharedUrl: String? = null) {
    val nav = rememberNavController()
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null && Site.fromUrl(sharedUrl) != null) nav.navigate(BrowserRoute(sharedUrl))
    }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                openPlaylist = { nav.navigate(PlaylistRoute(it)) },
                openSong = { nav.navigate(ViewerRoute(it)) },
                openBrowser = { nav.navigate(BrowserRoute(it)) },
                newSong = { nav.navigate(EditorRoute()) },
                editSong = { nav.navigate(EditorRoute(it)) },
                openSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<PlaylistRoute> {
            val r = it.toRoute<PlaylistRoute>()
            PlaylistScreen(
                playlistId = r.id,
                back = { nav.popBackStack() },
                openSong = { songId -> nav.navigate(ViewerRoute(songId, r.id)) },
            )
        }
        composable<ViewerRoute> {
            val r = it.toRoute<ViewerRoute>()
            ViewerScreen(
                songId = r.songId,
                playlistId = r.playlistId,
                back = { nav.popBackStack() },
                edit = { id -> nav.navigate(EditorRoute(id)) },
                goToSong = { id ->
                    nav.navigate(ViewerRoute(id, r.playlistId)) { popUpTo<ViewerRoute> { inclusive = true } }
                },
            )
        }
        composable<BrowserRoute> {
            val r = it.toRoute<BrowserRoute>()
            BrowserScreen(
                startUrl = r.url,
                back = { nav.popBackStack() },
                openSong = { id -> nav.navigate(ViewerRoute(id)) },
            )
        }
        composable<EditorRoute> {
            val r = it.toRoute<EditorRoute>()
            EditorScreen(songId = r.songId, done = { nav.popBackStack() })
        }
        composable<SettingsRoute> { SettingsScreen(back = { nav.popBackStack() }) }
    }
}
