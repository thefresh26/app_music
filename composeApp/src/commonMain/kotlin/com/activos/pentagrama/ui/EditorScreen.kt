package com.activos.pentagrama.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.activos.pentagrama.LocalMusicFont
import com.activos.pentagrama.editor.EditorState
import com.activos.pentagrama.editor.ScoreOps
import com.activos.pentagrama.editor.Selection
import com.activos.pentagrama.model.Clef
import com.activos.pentagrama.model.MusicXml
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.ScoreJson
import com.activos.pentagrama.model.keyName
import com.activos.pentagrama.audio.Synth
import com.activos.pentagrama.platform.AudioPlayer
import com.activos.pentagrama.platform.ScoreStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.activos.pentagrama.platform.rememberFileSaver
import com.activos.pentagrama.render.ScoreCanvas
import com.activos.pentagrama.render.ScoreColors
import com.activos.pentagrama.render.ScoreLayout
import com.activos.pentagrama.render.ScorePdf
import com.activos.pentagrama.symbols.SymbolAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(state: EditorState, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var showProps by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); state.message = null }
    }

    fun save() {
        ScoreStorage.save(state.id, ScoreJson.encode(state.score))
        state.dirty = false
        state.message = "Partitura guardada"
    }

    val saver = rememberFileSaver { state.message = it }

    // ---- Reproducción: sintetiza la partitura y resalta el compás que suena.
    val scope = rememberCoroutineScope()
    var playJob by remember { mutableStateOf<Job?>(null) }
    fun stopPlayback() { playJob?.cancel(); playJob = null; AudioPlayer.stop() }
    fun play() {
        stopPlayback()
        val from = state.selection?.measure ?: 0
        playJob = scope.launch {
            try {
                val r = withContext(Dispatchers.Default) { Synth.render(state.score, from) }
                AudioPlayer.play(r.pcm, r.sampleRate)
                // Follow what the speaker is actually playing (device position), note by note.
                val total = r.pcm.size.toDouble() / r.sampleRate
                var i = -1
                while (true) {
                    val pos = AudioPlayer.positionSeconds() ?: break
                    while (i + 1 < r.cues.size && r.cues[i + 1].sec <= pos) i++
                    r.cues.getOrNull(i)?.let { c -> if (state.selection != Selection(c.measure, c.event)) state.selection = Selection(c.measure, c.event) }
                    if (pos >= total - 0.05) break
                    delay(30)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                state.message = "No se pudo reproducir: ${e.message}"
            } finally {
                // Only the job that is still current may stop the sound (a newer Play may already be running).
                if (playJob === coroutineContext[Job]) { AudioPlayer.stop(); playJob = null }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { AudioPlayer.stop() } }

    // ---- PDF para imprimir
    val pdfMeasurer = rememberTextMeasurer()
    val musicFont = LocalMusicFont.current
    val density = LocalDensity.current
    fun exportPdf() = scope.launch {
        state.message = "Preparando PDF…"
        runCatching { withContext(Dispatchers.Default) { ScorePdf.export(state.score, pdfMeasurer, musicFont, density) } }
            .onSuccess { saver.save(fileNameOf(state.score, "pdf"), "application/pdf", it) }
            .onFailure { state.message = "No se pudo crear el PDF: ${it.message}" }
    }
    val transcribe = rememberTranscription { r ->
        state.updateScore { r.score }
        state.message = "Transcripción lista: ${r.notes} notas, ${r.chords} acordes, ♩=${r.bpm}, ${r.key}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.padding(end = 4.dp)) {
                        Text(state.score.title + if (state.dirty) " •" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${keyName(state.score.keyFifths)} · ${state.score.timeNum}/${state.score.timeDen} · ${state.score.measures.size} compases",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.dirty) confirmExit = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { if (playJob != null) stopPlayback() else play() }) {
                        if (playJob != null) Icon(Icons.Filled.Stop, "Detener") else Icon(Icons.Filled.PlayArrow, "Reproducir")
                    }
                    IconButton(onClick = state::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer") }
                    IconButton(onClick = state::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer") }
                    IconButton(onClick = { save() }) { Icon(Icons.Filled.Save, "Guardar") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Propiedades (título, tempo, compases por línea)") }, { menu = false; showProps = true })
                            DropdownMenuItem({ Text("Llenar desde MP3 / audio…") }, { menu = false; transcribe() })
                            DropdownMenuItem({ Text("Rellenar compases vacíos con barras rítmicas") }, {
                                menu = false; state.updateScore { ScoreOps.fillSlashes(it) }
                            })
                            HorizontalDivider()
                            DropdownMenuItem({ Text("Exportar PDF (para imprimir)") }, { menu = false; exportPdf() })
                            DropdownMenuItem({ Text("Exportar MusicXML (MuseScore, Finale…)") }, {
                                menu = false
                                saver.save(fileNameOf(state.score, "musicxml"), "application/vnd.recordare.musicxml+xml", MusicXml.export(state.score).encodeToByteArray())
                            })
                            DropdownMenuItem({ Text("Exportar archivo .pentagrama") }, {
                                menu = false
                                saver.save(fileNameOf(state.score, "pentagrama"), "application/json", ScoreJson.encode(state.score).encodeToByteArray())
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val wide = maxWidth > 840.dp
            val paletteHeight = (maxHeight * 0.38f).coerceAtLeast(220.dp)
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        EditToolbar(state)
                        ScoreView(state, Modifier.weight(1f).fillMaxWidth(), follow = playJob != null)
                    }
                    VerticalDivider()
                    SymbolPalette(state.tool.id, state::choose, Modifier.width(360.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ScoreView(state, Modifier.weight(1f).fillMaxWidth(), follow = playJob != null)
                    HorizontalDivider()
                    EditToolbar(state)
                    SymbolPalette(state.tool.id, state::choose, Modifier.fillMaxWidth().height(paletteHeight))
                }
            }
        }
    }

    state.pendingText?.let { p -> TextEntryDialog(p.action, p.initial, onDismiss = { state.pendingText = null }, onConfirm = state::confirmText) }
    if (showProps) PropertiesDialog(state.score, onDismiss = { showProps = false }) { s -> showProps = false; state.updateScore { s } }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Cambios sin guardar") },
        text = { Text("¿Quieres guardar antes de salir?") },
        confirmButton = { TextButton(onClick = { save(); confirmExit = false; onBack() }) { Text("Guardar y salir") } },
        dismissButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Salir sin guardar") } },
    )
}

private fun fileNameOf(score: Score, ext: String) =
    score.title.ifBlank { "partitura" }.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]"), "").trim().replace(' ', '_') + "." + ext

@Composable
private fun ScoreView(state: EditorState, modifier: Modifier, follow: Boolean = false) {
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val colors = ScoreColors(
        ink = Color(0xFF111111), staff = Color(0xFF333333),
        selection = cs.primary.copy(alpha = 0.18f), measureSelection = cs.primary.copy(alpha = 0.07f),
        overfull = Color(0x22E53935), section = Color(0xFFE9F76B), sectionText = Color(0xFF1B1B1B),
        chord = Color(0xFF111111), paper = Color(0xFFFFFFFF), muted = Color(0xFF666666),
    )
    BoxWithConstraints(modifier.background(Color(0xFFE9EAEE))) {
        val widthPx = with(density) { maxWidth.toPx() }
        val baseSpace = with(density) { (if (maxWidth > 700.dp) 8.5f else 6.5f).dp.toPx() }
        val s = baseSpace * state.zoom
        val layout = remember(state.score, widthPx, s) { ScoreLayout.build(state.score, widthPx, s) }
        val heightDp = with(density) { layout.height.toDp() }
        val scroll = rememberScrollState()
        val viewportPx = with(density) { maxHeight.toPx() }
        // While playing, keep the line that is sounding on screen.
        LaunchedEffect(state.selection, follow) {
            if (!follow) return@LaunchedEffect
            val sys = state.selection?.let { layout.measureLayout(it.measure)?.first } ?: return@LaunchedEffect
            val sysH = com.activos.pentagrama.render.Dim.SYSTEM_HEIGHT * layout.s
            if (sys.top < scroll.value || sys.top + sysH > scroll.value + viewportPx) {
                scroll.animateScrollTo((sys.top - viewportPx * 0.15f).toInt().coerceAtLeast(0))
            }
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            ScoreCanvas(
                score = state.score, layout = layout, musicFont = LocalMusicFont.current, colors = colors,
                selection = state.selection, onTap = state::tap,
                modifier = Modifier.fillMaxWidth().height(heightDp).testTag("score"),
            )
        }
    }
}

@Composable
private fun EditToolbar(state: EditorState) {
    val sel = state.selection
    val hasEvent = sel?.event != null
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AssistChip(onClick = {}, label = {
                Text("Herramienta: " + state.tool.nameEs, maxLines = 1, fontWeight = FontWeight.SemiBold)
            })
            FilterChip(state.chordMode, { state.chordMode = !state.chordMode }, label = { Text("Modo acorde") })
            IconButton(onClick = { state.moveSelected(1) }, enabled = hasEvent) { Icon(Icons.Filled.ArrowUpward, "Subir nota") }
            IconButton(onClick = { state.moveSelected(-1) }, enabled = hasEvent) { Icon(Icons.Filled.ArrowDownward, "Bajar nota") }
            IconButton(onClick = { state.deleteSelected() }, enabled = sel != null) { Icon(Icons.Filled.Delete, "Borrar selección") }
            TextButton(onClick = { state.selectNext(-1) }) { Text("◀") }
            TextButton(onClick = { state.selectNext(1) }) { Text("▶") }
            TextButton(onClick = { state.addMeasure() }) { Icon(Icons.Filled.Add, null); Text("Compás") }
            TextButton(onClick = { state.removeMeasure() }) { Icon(Icons.Filled.Remove, null); Text("Compás") }
            IconButton(onClick = { state.zoom = (state.zoom - 0.15f).coerceAtLeast(0.5f) }) { Icon(Icons.Filled.ZoomOut, "Alejar") }
            IconButton(onClick = { state.zoom = (state.zoom + 0.15f).coerceAtMost(2.5f) }) { Icon(Icons.Filled.ZoomIn, "Acercar") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextEntryDialog(action: SymbolAction, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val (title, hint, suggestions) = when (action) {
        SymbolAction.ChordSymbol -> Triple(
            "Acorde / cifrado", "Ej: Am, F#m7b5, Bb/D, G7",
            listOf("C", "D", "E", "F", "G", "A", "B", "Bb", "Eb", "F#", "C#", "Ab"),
        )
        SymbolAction.SectionLabel -> Triple(
            "Etiqueta de sección", "Ej: Coro",
            listOf("Intro", "Estrofa", "Pre-coro", "Coro", "Puente", "Interludio", "Solo", "Final", "Outro", "A", "B", "C"),
        )
        SymbolAction.Lyric -> Triple("Letra", "Sílaba o palabra", emptyList())
        else -> Triple("Texto", "Ej: x2, BASS, BAT, rit.", listOf("x2", "x3", "x4", "BASS", "BAT", "Tutti", "Solo", "rit."))
    }
    val qualities = listOf("m", "7", "m7", "maj7", "sus4", "dim", "m7b5", "add9", "/")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it }, placeholder = { Text(hint) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { sgg -> AssistChip(onClick = { text = sgg }, label = { Text(sgg) }) }
                    }
                }
                if (action == SymbolAction.ChordSymbol) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        qualities.forEach { q -> AssistChip(onClick = { text += q }, label = { Text("+$q") }) }
                    }
                }
                Text("Déjalo vacío para quitarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesDialog(score: Score, onDismiss: () -> Unit, onSave: (Score) -> Unit) {
    var title by remember { mutableStateOf(score.title) }
    var subtitle by remember { mutableStateOf(score.subtitle) }
    var composer by remember { mutableStateOf(score.composer) }
    var tempo by remember { mutableStateOf(score.tempoBpm.toString()) }
    var mpl by remember { mutableStateOf(score.measuresPerLine) }
    var clef by remember { mutableStateOf(score.clef) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propiedades") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtítulo / indicación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(composer, { composer = it }, label = { Text("Autor / arreglista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    tempo, { tempo = it.filter(Char::isDigit).take(3) }, label = { Text("Tempo (♩ por minuto)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Compases por línea", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Auto", 2 to "2", 3 to "3", 4 to "4", 5 to "5", 6 to "6").forEach { (v, l) ->
                        FilterChip(mpl == v, { mpl = v }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Clave", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Clef.entries.forEach { c -> FilterChip(clef == c, { clef = c }, label = { Text(c.es) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(score.copy(title = title, subtitle = subtitle, composer = composer, tempoBpm = tempo.toIntOrNull() ?: score.tempoBpm, measuresPerLine = mpl, clef = clef))
            }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Suppress("unused")
private fun Selection?.describe(): String = this?.let { "Compás ${it.measure + 1}" } ?: ""
