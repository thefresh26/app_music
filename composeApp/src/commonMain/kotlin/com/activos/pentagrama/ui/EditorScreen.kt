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
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.symbols.G
import com.activos.pentagrama.symbols.SymbolAction
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.activos.pentagrama.riseIn
import com.activos.pentagrama.serifFamily
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import com.activos.pentagrama.Paper
import com.activos.pentagrama.pressScale

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
    val serifFont = serifFamily()
    val sansFont = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.SansSerif
    fun exportPdf() = scope.launch {
        state.message = "Preparando PDF…"
        runCatching { withContext(Dispatchers.Default) { ScorePdf.export(state.score, pdfMeasurer, musicFont, density, serifFont, sansFont) } }
            .onSuccess { saver.save(fileNameOf(state.score, "pdf"), "application/pdf", it) }
            .onFailure { state.message = "No se pudo crear el PDF: ${it.message}" }
    }
    state.onAudition = { midis -> if (playJob == null) runCatching { AudioPlayer.play(Synth.preview(midis), Synth.SAMPLE_RATE) } }
    var showHelp by remember { mutableStateOf(false) }
    val transcribe = rememberTranscription { r ->
        state.updateScore { r.score }
        state.message = "Transcripción lista: ${r.notes} notas, ${r.chords} acordes, ♩=${r.bpm}, ${r.key}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.padding(end = 4.dp)) {
                        Text(state.score.title + if (state.dirty) " •" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${keyName(state.score.keyFifths)} · ${state.score.timeNum}/${state.score.timeDen} · ${state.score.measures.size} compases",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.dirty) confirmExit = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = state::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer") }
                    IconButton(onClick = state::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer") }
                    IconButton(onClick = { save() }) { Icon(Icons.Filled.Save, "Guardar") }
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Ayuda") }
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
                    PlayButton(playing = playJob != null) { if (playJob != null) stopPlayback() else play() }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val wide = maxWidth > 840.dp
            val paletteHeight = (maxHeight * 0.38f).coerceAtLeast(220.dp)
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        EditToolbar(state)
                        HintBar(state)
                        ScoreView(state, Modifier.weight(1f).fillMaxWidth(), follow = playJob != null)
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 22.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.width(380.dp).fillMaxHeight(),
                    ) { SymbolPalette(state.tool.id, state::choose, Modifier.fillMaxSize().padding(top = 8.dp)) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ScoreView(state, Modifier.weight(1f).fillMaxWidth(), follow = playJob != null)
                    EditToolbar(state)
                    HintBar(state)
                    Surface(
                        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().height(paletteHeight).padding(top = 6.dp),
                    ) {
                        Column {
                            Box(Modifier.padding(top = 8.dp).size(36.dp, 4.dp).background(MaterialTheme.colorScheme.outline, CircleShape).align(Alignment.CenterHorizontally))
                            SymbolPalette(state.tool.id, state::choose, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    state.pendingText?.let { p -> TextEntryDialog(p.action, p.initial, onDismiss = { state.pendingText = null }, onConfirm = state::confirmText) }
    if (showHelp) HelpDialog { showHelp = false }
    if (showProps) PropertiesDialog(state.score, onDismiss = { showProps = false }) { s -> showProps = false; state.updateScore { s } }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Cambios sin guardar") },
        text = { Text("¿Quieres guardar antes de salir?") },
        confirmButton = { Button(onClick = { save(); confirmExit = false; onBack() }) { Text("Guardar y salir") } },
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
        ink = Color(0xFF1E1B16), staff = Color(0xFF3A352D),
        selection = cs.primary.copy(alpha = 0.18f), measureSelection = cs.primary.copy(alpha = 0.07f),
        overfull = Color(0x26C2402F), section = Paper.Highlighter, sectionText = Color(0xFF1E1B16),
        chord = Color(0xFF1E1B16), paper = Color(0xFFFFFEFB), muted = Color(0xFF6A6257),
        guide = cs.primary.copy(alpha = 0.22f), accent = cs.primary,
        serif = serifFamily(), sans = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.SansSerif,
    )
    BoxWithConstraints(modifier.background(cs.background).padding(horizontal = 10.dp)) {
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
        Box(Modifier.fillMaxSize().shadow(10.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFFEFB)).verticalScroll(scroll)) {
            ScoreCanvas(
                score = state.score, layout = layout, musicFont = LocalMusicFont.current, colors = colors,
                selection = state.selection, onTap = state::tap,
                modifier = Modifier.fillMaxWidth().height(heightDp).testTag("score"),
                ghostGlyph = (state.tool.action as? SymbolAction.PlaceEvent)?.takeIf { it.kind == EventKind.NOTE }?.let {
                    when (it.value) {
                        NoteValue.DOUBLE_WHOLE -> G.HEAD_DOUBLE_WHOLE
                        NoteValue.WHOLE -> G.HEAD_WHOLE
                        NoteValue.HALF -> G.HEAD_HALF
                        else -> G.HEAD_BLACK
                    }
                },
            )
        }
    }
}

@Composable
private fun EditToolbar(state: EditorState) {
    val sel = state.selection
    val hasEvent = sel?.event != null
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolPill(state.tool.nameEs)
            FilterChip(state.chordMode, { state.chordMode = !state.chordMode }, label = { Text("Modo acorde") }, shape = CircleShape)
            FilterChip(
                state.soundOn, { state.soundOn = !state.soundOn }, shape = CircleShape,
                label = { Text(if (state.soundOn) "Sonido: sí" else "Sonido: no") },
                leadingIcon = { Icon(if (state.soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.width(4.dp))
            ToolGroup {
                GroupIcon(Icons.Filled.ArrowUpward, "Subir nota", hasEvent) { state.moveSelected(1) }
                GroupIcon(Icons.Filled.ArrowDownward, "Bajar nota", hasEvent) { state.moveSelected(-1) }
                GroupIcon(Icons.Outlined.Delete, "Borrar selección", sel != null, tint = MaterialTheme.colorScheme.primary) { state.deleteSelected() }
            }
            ToolGroup {
                GroupIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nota anterior") { state.selectNext(-1) }
                GroupIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nota siguiente") { state.selectNext(1) }
            }
            ToolGroup {
                TextButton(onClick = { state.addMeasure() }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text("Compás") }
                TextButton(onClick = { state.removeMeasure() }) { Icon(Icons.Filled.Remove, null, Modifier.size(18.dp)); Text("Compás") }
            }
            ToolGroup {
                GroupIcon(Icons.Filled.ZoomOut, "Alejar") { state.zoom = (state.zoom - 0.15f).coerceAtLeast(0.5f) }
                Text("${(state.zoom * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                GroupIcon(Icons.Filled.ZoomIn, "Acercar") { state.zoom = (state.zoom + 0.15f).coerceAtMost(2.5f) }
            }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { sgg -> AssistChip(onClick = { text = sgg }, label = { Text(sgg, style = if (action == SymbolAction.ChordSymbol) MaterialTheme.typography.titleMedium.copy(fontFamily = serifFamily(), fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge) }, shape = CircleShape) }
                    }
                }
                if (action == SymbolAction.ChordSymbol) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        qualities.forEach { q -> AssistChip(onClick = { text += q }, label = { Text("+$q") }, shape = CircleShape, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Déjalo vacío para quitarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text("Aceptar") } },
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
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtítulo / indicación") }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                OutlinedTextField(composer, { composer = it }, label = { Text("Autor / arreglista") }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                OutlinedTextField(
                    tempo, { tempo = it.filter(Char::isDigit).take(3) }, label = { Text("Tempo (♩ por minuto)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                SectionLabel("Compases por línea")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Auto", 2 to "2", 3 to "3", 4 to "4", 5 to "5", 6 to "6").forEach { (v, l) ->
                        FilterChip(mpl == v, { mpl = v }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Clave")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Clef.entries.forEach { c -> FilterChip(clef == c, { clef = c }, label = { Text(c.es) }) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(score.copy(title = title, subtitle = subtitle, composer = composer, tempoBpm = tempo.toIntOrNull() ?: score.tempoBpm, measuresPerLine = mpl, clef = clef))
            }) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Suppress("unused")
private fun Selection?.describe(): String = this?.let { "Compás ${it.measure + 1}" } ?: ""

/** One line that always tells the user what the current tool does and what to do next. */
fun hintFor(state: EditorState): String {
    val n = state.tool.nameEs
    return when (val a = state.tool.action) {
        is SymbolAction.PlaceEvent -> when (a.kind) {
            EventKind.NOTE -> "Toca el pentagrama donde quieras la $n. La línea o espacio decide la nota (Do, Re, Mi…); las rayas de color son los tiempos libres." +
                if (state.chordMode) " Modo acorde: toca encima de una nota para sumarle otra." else ""
            EventKind.REST -> "Toca un tiempo del compás para poner el $n."
            EventKind.SLASH -> "Toca un tiempo del compás para poner la barra rítmica."
        }
        is SymbolAction.Accidental -> "Toca una nota para ponerle $n (o selecciónala y luego elige la alteración)."
        SymbolAction.ToggleDot, SymbolAction.ToggleTie, SymbolAction.ToggleTriplet, is SymbolAction.SetHead, is SymbolAction.Attach ->
            "Toca una nota para aplicarle: $n."
        is SymbolAction.SetClef, is SymbolAction.SetKey, is SymbolAction.SetTime -> "$n se aplica a toda la partitura."
        is SymbolAction.SetStartBar, is SymbolAction.SetEndBar, is SymbolAction.MeasureMark, is SymbolAction.Ending, SymbolAction.MeasureRepeat ->
            "Toca el compás donde quieres poner: $n."
        SymbolAction.ChordSymbol -> "Toca encima del pentagrama, en el tiempo donde cambia el acorde, y escribe el cifrado (Am, G7, F#m…)."
        SymbolAction.SectionLabel -> "Toca un compás para ponerle nombre de sección (Intro, Estrofa, Coro…)."
        SymbolAction.FreeText -> "Toca un compás para escribirle un texto (x2, BASS, rit.…)."
        SymbolAction.Lyric -> "Toca una nota para escribirle la letra."
        is SymbolAction.Free -> "Toca el lugar del pentagrama donde quieres este símbolo."
        SymbolAction.Eraser -> "Toca una nota, acorde o símbolo para borrarlo. ↶ deshace si te equivocas."
        SymbolAction.Select -> "Toca una nota para seleccionarla y oírla. Luego usa ↑ ↓ para cambiar la altura o 🗑 para borrarla."
    }
}

@Composable
private fun HintBar(state: EditorState) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).animateContentSize(tween(240, easing = Paper.Ease)), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, null, Modifier.padding(end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            AnimatedContent(hintFor(state), transitionSpec = { fadeIn(tween(220, 60)) togetherWith fadeOut(tween(120)) }) { h ->
                Text(h, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 3)
            }
        }
    }
}

private val HELP_STEPS = listOf(
    "Elige una figura" to "En la paleta (abajo en el celular, a la derecha en el PC) toca la figura que quieres: negra, blanca, silencio, sostenido… Si no la ves, búscala por nombre: \"calderón\", \"clave de fa\".",
    "Toca el pentagrama" to "La nota cae en el tiempo (casilla) que tocaste; las rayas de color marcan los tiempos libres. La línea o espacio decide qué nota es. La vas a oír y verás su nombre (Sol4, Fa♯5…).",
    "Corrige sin miedo" to "Con \"Seleccionar\" toca una nota: ↑ ↓ cambian su altura y 🗑 la borra. ↶ deshace y ↷ rehace cualquier cambio.",
    "Acordes y secciones" to "En la categoría \"Cifrado y texto\": acordes (Am, G7), secciones (Intro, Coro) y textos (x2). Repeticiones, casillas 1/2 y Coda están en \"Barras\" y \"Navegación\".",
    "Escucha lo que escribiste" to "▶ toca la partitura y va marcando la nota que suena. Si seleccionas un compás primero, empieza desde ahí.",
    "Desde una canción" to "⋮ → \"Llenar desde MP3\": la app saca la melodía, el ritmo y los acordes. Revísalo y corrígelo aquí.",
    "Guarda e imprime" to "💾 guarda en el dispositivo. ⋮ → \"Exportar PDF\" para imprimir o compartir; también MusicXML para MuseScore.",
)

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cómo se usa?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HELP_STEPS.forEachIndexed { i, (t, d) ->
                    Row(Modifier.padding(bottom = 14.dp).riseIn(i)) {
                        Box(
                            Modifier.size(30.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleMedium.copy(fontFamily = serifFamily(), fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(t, style = MaterialTheme.typography.titleSmall)
                            Text(d, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Entendido") } },
    )
}


/** Herramienta activa: píldora de tinta; el nombre cambia con un pequeño deslizamiento vertical. */
@Composable
private fun ToolPill(name: String) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.secondary, contentColor = cs.onSecondary, shape = CircleShape, modifier = Modifier.padding(end = 4.dp)) {
        AnimatedContent(
            name,
            transitionSpec = {
                (slideInVertically(tween(260, easing = Paper.Ease)) { it / 2 } + fadeIn(tween(200)))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(120)))
                    .using(SizeTransform(clip = false))
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) { n -> Text(n, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
    }
}

/** Botón de reproducir: círculo bermellón que respira suavemente mientras suena. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition()
    val ring by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(700, easing = Paper.Ease), RepeatMode.Reverse))
    val src = remember { MutableInteractionSource() }
    Box(contentAlignment = Alignment.Center) {
        if (playing) Box(Modifier.size(44.dp).graphicsLayer { scaleX = ring; scaleY = ring }.background(cs.primary.copy(alpha = 0.22f), CircleShape))
        Surface(
            onClick = onClick, shape = CircleShape, color = cs.primary, contentColor = cs.onPrimary, shadowElevation = 4.dp,
            interactionSource = src, modifier = Modifier.size(44.dp).pressScale(src),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Crossfade(playing, animationSpec = tween(180)) { p ->
                    if (p) Icon(Icons.Filled.Stop, "Detener") else Icon(Icons.Filled.PlayArrow, "Reproducir")
                }
            }
        }
    }
}

/** Grupo de botones en una cápsula con borde, como en la maqueta. */
@Composable
private fun ToolGroup(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = CircleShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Row(Modifier.height(44.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, content = content) }
}

@Composable
private fun GroupIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = LocalContentColor.current, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}
