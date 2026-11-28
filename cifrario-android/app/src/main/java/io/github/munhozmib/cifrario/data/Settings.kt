package io.github.munhozmib.cifrario.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Do sistema"), LIGHT("Claro"), DARK("Escuro") }

data class AppSettings(
    val fontSize: Float = 16f,
    /** 1..20 */
    val scrollSpeed: Int = 5,
    /** Fração da tela que o botão de rolagem rápida desce (0.25..1.0). */
    val jumpFraction: Float = 0.6f,
    val keepScreenOn: Boolean = true,
    val volumeKeysScroll: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultInstrument: Instrument = Instrument.VIOLAO,
    val showChordDiagrams: Boolean = true,
    /** Abrir a página original salva (true) ou a versão em texto (false). */
    val preferOriginalPage: Boolean = true,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val font = floatPreferencesKey("font")
        val speed = intPreferencesKey("speed")
        val jump = floatPreferencesKey("jump")
        val keepOn = booleanPreferencesKey("keepOn")
        val volume = booleanPreferencesKey("volume")
        val theme = stringPreferencesKey("theme")
        val instrument = stringPreferencesKey("instrument")
        val diagrams = booleanPreferencesKey("diagrams")
        val page = booleanPreferencesKey("page")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            fontSize = this[K.font] ?: d.fontSize,
            scrollSpeed = this[K.speed] ?: d.scrollSpeed,
            jumpFraction = this[K.jump] ?: d.jumpFraction,
            keepScreenOn = this[K.keepOn] ?: d.keepScreenOn,
            volumeKeysScroll = this[K.volume] ?: d.volumeKeysScroll,
            theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            defaultInstrument = this[K.instrument]?.let { runCatching { Instrument.valueOf(it) }.getOrNull() } ?: d.defaultInstrument,
            showChordDiagrams = this[K.diagrams] ?: d.showChordDiagrams,
            preferOriginalPage = this[K.page] ?: d.preferOriginalPage,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(p.toSettings())
            p[K.font] = n.fontSize
            p[K.speed] = n.scrollSpeed
            p[K.jump] = n.jumpFraction
            p[K.keepOn] = n.keepScreenOn
            p[K.volume] = n.volumeKeysScroll
            p[K.theme] = n.theme.name
            p[K.instrument] = n.defaultInstrument.name
            p[K.diagrams] = n.showChordDiagrams
            p[K.page] = n.preferOriginalPage
        }
    }
}

/** Rascunho passado do navegador para o editor quando a leitura automática falha. */
object Drafts {
    @Volatile var pending: Song? = null
}
