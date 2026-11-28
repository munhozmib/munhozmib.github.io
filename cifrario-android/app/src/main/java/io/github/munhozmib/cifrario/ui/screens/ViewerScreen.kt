package io.github.munhozmib.cifrario.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.munhozmib.cifrario.container
import io.github.munhozmib.cifrario.data.AppSettings
import io.github.munhozmib.cifrario.data.Instrument
import io.github.munhozmib.cifrario.data.Playlist
import io.github.munhozmib.cifrario.data.Song
import io.github.munhozmib.cifrario.music.ChordSheet
import io.github.munhozmib.cifrario.ui.components.ChordStrip
import io.github.munhozmib.cifrario.ui.components.PlaylistPickerDialog
import io.github.munhozmib.cifrario.ui.components.SheetText
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Velocidade de rolagem: nível 1..20 → dp por segundo. */
fun speedDpPerSecond(level: Int): Float = 2.5f * level.coerceIn(1, 20)

@Composable
fun ViewerScreen(songId: Long, playlistId: Long, back: () -> Unit, edit: (Long) -> Unit, goToSong: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: ViewerViewModel = viewModel(key = "viewer-$songId-$playlistId") { ViewerViewModel(context.container, songId, playlistId) }
    val song by vm.song.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val neighbors by vm.neighbors.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val s = song ?: return
    ViewerContent(
        song = s, playlist = playlist, settings = settings, neighbors = neighbors, position = position,
        back = back, edit = { edit(s.id) }, goToSong = goToSong,
        update = vm::update,
        pickPlaylists = { vm.allPlaylists() to vm.playlistsOfSong() },
        setPlaylists = { vm.setPlaylists(it) },
        createPlaylist = { n, i -> vm.createPlaylist(n, i) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerContent(
    song: Song,
    playlist: Playlist?,
    settings: AppSettings,
    neighbors: Pair<Long?, Long?> = null to null,
    position: Pair<Int, Int>? = null,
    back: () -> Unit = {},
    edit: () -> Unit = {},
    goToSong: (Long) -> Unit = {},
    update: ((Song) -> Song) -> Unit = {},
    pickPlaylists: suspend () -> Pair<List<Playlist>, Set<Long>> = { emptyList<Playlist>() to emptySet() },
    setPlaylists: (Set<Long>) -> Unit = {},
    createPlaylist: (String, Instrument) -> Unit = { _, _ -> },
    startPlaying: Boolean = false,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    var playing by remember(song.id) { mutableStateOf(startPlaying) }
    var controls by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var showDiagrams by remember { mutableStateOf(settings.showChordDiagrams) }
    var picking by remember { mutableStateOf<Pair<List<Playlist>, Set<Long>>?>(null) }
    val focus = remember { FocusRequester() }

    val speed = song.scrollSpeed ?: settings.scrollSpeed
    val fontSize = song.fontSize ?: settings.fontSize
    val instrument = song.instrument ?: playlist?.instrument ?: settings.defaultInstrument
    val chords = remember(song.content, song.transpose) { ChordSheet.distinctChords(song.content, song.transpose) }
    val key = ChordSheet.transposeKey(song.originalKey, song.transpose)

    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Rolagem automática: avança a cada quadro, sem bloquear o toque do usuário.
    LaunchedEffect(playing, speed) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        val pxPerSec = with(density) { speedDpPerSecond(speed).dp.toPx() }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            if (scroll.maxValue in 1..scroll.value) { playing = false; break }
            scroll.dispatchRawDelta(pxPerSec * dt)
        }
    }

    fun jump(direction: Int) {
        val amount = scroll.viewportSize * settings.jumpFraction * direction
        scope.launch { scroll.animateScrollBy(amount) }
    }

    fun setSpeed(v: Int) = update { it.copy(scrollSpeed = v.coerceIn(1, 20)) }
    fun setFont(v: Float) = update { it.copy(fontSize = v.coerceIn(10f, 36f)) }
    fun setTranspose(v: Int) = update { it.copy(transpose = Math.floorMod(v + 6, 12) - 6) }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Pedais Bluetooth costumam enviar Page Down/Up ou setas.
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_PAGE_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_SPACE -> { jump(1); true }
                    AndroidKeyEvent.KEYCODE_PAGE_UP, AndroidKeyEvent.KEYCODE_DPAD_UP -> { jump(-1); true }
                    AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> if (settings.volumeKeysScroll) { jump(1); true } else false
                    AndroidKeyEvent.KEYCODE_VOLUME_UP -> if (settings.volumeKeysScroll) { jump(-1); true } else false
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> neighbors.second?.let { goToSong(it); true } ?: false
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> neighbors.first?.let { goToSong(it); true } ?: false
                    AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { playing = !playing; true }
                    else -> false
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) }
                    .statusBarsPadding()
                    .padding(top = if (controls) 64.dp else 8.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (song.artist.isNotBlank()) Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val info = listOfNotNull(
                        key?.let { "Tom: $it" + if (song.transpose != 0) " (original ${song.originalKey})" else "" },
                        song.capo.takeIf { it > 0 }?.let { "Capotraste na ${it}ª casa" },
                        position?.let { "${it.first}/${it.second} em ${playlist?.name ?: "playlist"}" },
                    )
                    if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    song.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
                if (showDiagrams && chords.isNotEmpty()) {
                    ChordStrip(chords, instrument, Modifier.padding(vertical = 4.dp))
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                Box(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    SheetText(song.content, song.transpose, fontSize)
                }
                // Espaço extra para a última linha poder subir até o topo da tela.
                Spacer(Modifier.height(height * 0.7f))
            }

            // Botões de rolagem rápida (sempre visíveis): grande para descer, pequeno para voltar.
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = if (controls) 132.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = { jump(-1) }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Voltar um trecho")
                }
                FloatingActionButton(
                    onClick = { jump(1) },
                    modifier = Modifier.size(72.dp),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                ) { Icon(Icons.Filled.KeyboardDoubleArrowDown, "Rolagem rápida", Modifier.size(40.dp)) }
            }

            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                    title = { Text(song.title, maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                    actions = {
                        if (playlist != null) {
                            IconButton(onClick = { neighbors.first?.let(goToSong) }, enabled = neighbors.first != null) { Icon(Icons.Filled.SkipPrevious, "Música anterior") }
                            IconButton(onClick = { neighbors.second?.let(goToSong) }, enabled = neighbors.second != null) { Icon(Icons.Filled.SkipNext, "Próxima música") }
                        }
                        IconButton(onClick = { update { it.copy(favorite = !it.favorite) } }) {
                            Icon(if (song.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorita")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(if (showDiagrams) "Ocultar acordes" else "Mostrar acordes") }, onClick = { showDiagrams = !showDiagrams; menu = false })
                                Instrument.entries.forEach { i ->
                                    DropdownMenuItem(
                                        text = { Text("Diagramas: ${i.label}" + if (i == instrument) " ✓" else "") },
                                        onClick = { update { it.copy(instrument = i) }; showDiagrams = true; menu = false },
                                    )
                                }
                                DropdownMenuItem(text = { Text("Playlists…") }, onClick = { menu = false; scope.launch { picking = pickPlaylists() } })
                                DropdownMenuItem(text = { Text("Editar cifra") }, onClick = { menu = false; edit() })
                                DropdownMenuItem(text = { Text("Voltar ao tom original") }, onClick = { menu = false; setTranspose(0) })
                            }
                        }
                    },
                )
            }

            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(onClick = { playing = !playing }, modifier = Modifier.size(48.dp)) {
                                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pausar rolagem" else "Rolagem automática")
                            }
                            Spacer(Modifier.width(8.dp))
                            Stepper("Velocidade", "$speed", { setSpeed(speed - 1) }, { setSpeed(speed + 1) })
                            Spacer(Modifier.weight(1f))
                            Stepper("Tom", key ?: (if (song.transpose > 0) "+${song.transpose}" else "${song.transpose}"), { setTranspose(song.transpose - 1) }, { setTranspose(song.transpose + 1) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { setFont(fontSize - 1) }) { Icon(Icons.Filled.TextDecrease, "Diminuir letra") }
                            Text("${fontSize.toInt()}", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { setFont(fontSize + 1) }) { Icon(Icons.Filled.TextIncrease, "Aumentar letra") }
                            IconButton(onClick = {
                                val longest = song.content.lines().maxOfOrNull { it.length }?.coerceAtLeast(20) ?: 40
                                setFont(((width.value - 40f) / (longest * 0.61f)).coerceIn(10f, 28f))
                            }) { Icon(Icons.Filled.FitScreen, "Ajustar à largura") }
                            Spacer(Modifier.weight(1f))
                            Text("Toque na cifra para esconder os controles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    picking?.let { (all, selected) ->
        PlaylistPickerDialog(
            playlists = all, initiallySelected = selected, onDismiss = { picking = null },
            onCreate = { n, i -> createPlaylist(n, i); scope.launch { kotlinx.coroutines.delay(150); picking = pickPlaylists().first to selected } },
            onConfirm = { setPlaylists(it); picking = null },
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = minus, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Remove, "$label menos") }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        IconButton(onClick = plus, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Add, "$label mais") }
    }
}
