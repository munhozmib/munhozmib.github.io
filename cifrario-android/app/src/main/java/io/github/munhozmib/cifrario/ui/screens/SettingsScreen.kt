package io.github.munhozmib.cifrario.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.munhozmib.cifrario.BuildConfig
import io.github.munhozmib.cifrario.container
import io.github.munhozmib.cifrario.data.AppSettings
import io.github.munhozmib.cifrario.data.ThemeMode
import io.github.munhozmib.cifrario.ui.components.InstrumentChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

const val PRIVACY_URL = "https://munhozmib.github.io/cifrario/privacidade.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    fun set(t: (AppSettings) -> AppSettings) = scope.launch { c.settings.update(t) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = c.repository.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup salvo" else "Falha ao salvar backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                val (songs, lists) = c.repository.importJson(text)
                "Importado: $songs músicas novas, $lists playlists"
            }.getOrElse { "Arquivo de backup inválido" }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                title = { Text("Configurações") },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Leitura")
            LabeledSlider("Tamanho da letra padrão: ${settings.fontSize.roundToInt()}", settings.fontSize, 10f..32f, 21) { v -> set { it.copy(fontSize = v.roundToInt().toFloat()) } }
            LabeledSlider("Velocidade padrão da rolagem: ${settings.scrollSpeed}", settings.scrollSpeed.toFloat(), 1f..20f, 18) { v -> set { it.copy(scrollSpeed = v.roundToInt()) } }
            LabeledSlider("Rolagem rápida desce ${(settings.jumpFraction * 100).roundToInt()}% da tela", settings.jumpFraction, 0.25f..1f, 14) { v ->
                set { it.copy(jumpFraction = (v * 20).roundToInt() / 20f) }
            }
            Toggle("Manter a tela ligada na cifra", settings.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
            Toggle("Botões de volume rolam a cifra", settings.volumeKeysScroll) { v -> set { it.copy(volumeKeysScroll = v) } }
            Toggle("Mostrar diagramas dos acordes", settings.showChordDiagrams) { v -> set { it.copy(showChordDiagrams = v) } }
            Text("Pedal Bluetooth: Page Down/↓/Espaço desce, Page Up/↑ sobe, → e ← trocam de música, Enter liga/desliga a rolagem.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Section("Instrumento padrão")
            InstrumentChooser(settings.defaultInstrument) { v -> set { it.copy(defaultInstrument = v) } }

            Section("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m -> FilterChip(settings.theme == m, { set { it.copy(theme = m) } }, label = { Text(m.label) }) }
            }

            Section("Backup")
            Text("Suas cifras ficam só neste aparelho. Faça um backup de vez em quando (por exemplo, no Google Drive).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("cifrario-backup-$date.json")
                }) { Text("Exportar") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Importar") }
            }

            Section("Sobre")
            Text("Cifrário ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text("As cifras pertencem aos seus autores e aos sites de origem. O Cifrário guarda uma cópia apenas para seu uso pessoal.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }) { Text("Política de privacidade") }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onChange)
    }
}
