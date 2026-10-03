package com.activos.pentagrama.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.activos.pentagrama.Eyebrow
import com.activos.pentagrama.italicNote
import com.activos.pentagrama.pressScale
import com.activos.pentagrama.riseIn
import com.activos.pentagrama.symbols.G
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.activos.pentagrama.model.Clef
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.ScoreJson
import com.activos.pentagrama.model.Templates
import com.activos.pentagrama.model.keyName
import com.activos.pentagrama.platform.ScoreStorage
import com.activos.pentagrama.platform.StoredScore
import com.activos.pentagrama.platform.currentTimeMillis
import com.activos.pentagrama.platform.rememberFileOpener

fun newScoreId() = "s" + currentTimeMillis()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(onOpen: (String, Score) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val stored = remember(refresh) { runCatching { ScoreStorage.list() }.getOrDefault(emptyList()) }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<StoredScore?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(info) { info?.let { snackbar.showSnackbar(it); info = null } }

    val transcribe = rememberTranscription { r ->
        val id = newScoreId()
        ScoreStorage.save(id, ScoreJson.encode(r.score))
        refresh++
        onOpen(id, r.score)
    }
    val openFile = rememberFileOpener(
        onScore = { _, text ->
            runCatching { ScoreJson.decode(text) }
                .onSuccess { s -> val id = newScoreId(); ScoreStorage.save(id, ScoreJson.encode(s)); refresh++; onOpen(id, s) }
                .onFailure { info = "El archivo no es una partitura válida" }
        },
        onAudio = { transcribe(it) }, // a song: build the whole score from it
    )

    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            val wide = maxWidth > 720.dp
            val gutter = if (wide) 40.dp else 20.dp
            LazyColumn(
                Modifier.widthIn(max = 980.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = if (wide) 40.dp else 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().riseIn(0), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("CUADERNO DE MÚSICA", style = Eyebrow, color = cs.onSurfaceVariant)
                            Text("Pentagrama", style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp))
                            Text("Crea, edita y transcribe partituras.", style = italicNote(), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        RoundIcon(Icons.AutoMirrored.Filled.HelpOutline, "¿Cómo se usa?") { showHelp = true }
                        Spacer(Modifier.width(6.dp))
                        RoundIcon(Icons.Filled.Info, "Privacidad") { showPrivacy = true }
                    }
                    Spacer(Modifier.height(if (wide) 28.dp else 20.dp))
                    val newScore = @Composable { m: Modifier -> HeroCard(m.riseIn(1)) { showNew = true } }
                    val mp3 = @Composable { m: Modifier -> ActionCard(Icons.Filled.GraphicEq, "Desde canción", "MP3 → pentagrama completo", m.riseIn(2)) { transcribe(null) } }
                    val open = @Composable { m: Modifier -> ActionCard(Icons.Filled.FolderOpen, "Abrir archivo", "Canción o partitura", m.riseIn(3)) { openFile() } }
                    val demo = @Composable { m: Modifier ->
                        ActionCard(Icons.Filled.LibraryMusic, "Ver ejemplo", "Cifrado \"No lo hay\"", m.riseIn(4)) {
                            val s = Templates.demoChart(); val id = newScoreId()
                            ScoreStorage.save(id, ScoreJson.encode(s)); refresh++; onOpen(id, s)
                        }
                    }
                    if (wide) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            newScore(Modifier.weight(1.6f).fillMaxHeight()); mp3(Modifier.weight(1f).fillMaxHeight())
                            open(Modifier.weight(1f).fillMaxHeight()); demo(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        newScore(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            mp3(Modifier.weight(1f).fillMaxHeight()); open(Modifier.weight(1f).fillMaxHeight()); demo(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth().riseIn(5), verticalAlignment = Alignment.Bottom) {
                        Text("Mis partituras", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (stored.isNotEmpty()) Text(
                            if (stored.size == 1) "1 guardada" else "${stored.size} guardadas",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (stored.isEmpty()) EmptyStaff(Modifier.riseIn(6)) { showHelp = true }
                }
                itemsIndexed(stored, key = { _, it -> it.id }) { i, st ->
                    ScoreRow(st, Modifier.animateItem().riseIn(6 + i), onDelete = { toDelete = st }) {
                        val text = ScoreStorage.load(st.id)
                        val score = text?.let { runCatching { ScoreJson.decode(it) }.getOrNull() }
                        if (score != null) onOpen(st.id, score) else info = "No se pudo abrir la partitura"
                    }
                }
            }
        }
    }

    if (showNew) NewScoreDialog(onDismiss = { showNew = false }) { s ->
        showNew = false
        val id = newScoreId()
        ScoreStorage.save(id, ScoreJson.encode(s))
        refresh++
        onOpen(id, s)
    }
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        icon = { Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Privacidad") },
        text = {
            Text(
                "Pentagrama funciona sin internet. Tus partituras se guardan solo en este dispositivo y el audio " +
                    "que transcribes se procesa en memoria: nunca se envía ni se guarda. No hay cuentas, publicidad, " +
                    "cookies ni analítica.\n\nTratamiento conforme a la Ley 1581 de 2012 (Colombia). " +
                    "Contacto: soporte.tecnico@activosporcolombia.com"
            )
        },
        confirmButton = { Button(onClick = { showPrivacy = false }) { Text("Entendido") } },
    )
    if (showHelp) HelpDialog { showHelp = false }
    toDelete?.let { st ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar partitura") },
            text = { Text("¿Eliminar \"${st.title}\"? Esta acción no se puede deshacer.") },
            icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            confirmButton = {
                Button(
                    onClick = { ScoreStorage.delete(st.id); toDelete = null; refresh++ },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, shape = CircleShape, color = cs.surface, border = BorderStroke(1.dp, cs.outlineVariant),
        interactionSource = src, modifier = Modifier.size(44.dp).pressScale(src),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp)) } }
}

@Composable
private fun HeroCard(modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.large, color = cs.primary, contentColor = cs.onPrimary,
        shadowElevation = 6.dp, interactionSource = src, modifier = modifier.pressScale(src),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(cs.onPrimary.copy(alpha = 0.16f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, Modifier.size(28.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text("Nueva partitura", style = MaterialTheme.typography.titleLarge)
                Text("En blanco, cifrado o canción", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary.copy(alpha = 0.85f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium, color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant), interactionSource = src, modifier = modifier.pressScale(src),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = cs.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScoreRow(st: StoredScore, modifier: Modifier, onDelete: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.small, color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant), interactionSource = src,
        modifier = modifier.fillMaxWidth().pressScale(src),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(cs.primaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                GlyphView(G.str(G.G_CLEF), 34.dp, cs.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(st.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(st.modified), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Eliminar", tint = cs.onSurfaceVariant) }
        }
    }
}

/** Estado vacío: un pentagrama sin notas, como una hoja nueva del cuaderno. */
@Composable
private fun EmptyStaff(modifier: Modifier, onHelp: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onHelp, shape = MaterialTheme.shapes.medium, color = Color.Transparent,
        border = BorderStroke(1.5.dp, cs.outline), modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                repeat(5) { i ->
                    val y = i * size.height / 4
                    drawLine(cs.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Aún no tienes partituras guardadas.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "¿Primera vez? Toca aquí y te explicamos en 7 pasos cómo escribir, escuchar e imprimir tu música.",
                style = MaterialTheme.typography.bodyMedium, color = cs.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewScoreDialog(onDismiss: () -> Unit, onCreate: (Score) -> Unit) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Templates.Kind.CHART) }
    var clef by remember { mutableStateOf(Clef.TREBLE) }
    var key by remember { mutableIntStateOf(0) }
    var time by remember { mutableStateOf(4 to 4) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva partitura", style = MaterialTheme.typography.headlineMedium) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small, textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                )
                Spacer(Modifier.height(14.dp))
                SectionLabel("Plantilla")
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Templates.Kind.entries.forEach { k -> TemplateTile(k.es, kind == k, Modifier.weight(1f).fillMaxHeight()) { kind = k } }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Clave")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Clef.TREBLE, Clef.BASS, Clef.TREBLE_8VB, Clef.ALTO, Clef.PERCUSSION).forEach { c ->
                        FilterChip(clef == c, { clef = c }, label = { Text(c.es) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Tonalidad (armadura)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (-6..6).forEach { f ->
                        FilterChip(key == f, { key = f }, label = { Text(keyName(f) + " / " + keyName(f, true)) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Compás")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4 to 4, 3 to 4, 2 to 4, 2 to 2, 6 to 8, 12 to 8).forEach { t ->
                        FilterChip(time == t, { time = t }, label = { Text("${t.first}/${t.second}") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onCreate(Templates.create(kind, title.ifBlank { "Sin título" }, clef, key, time.first, time.second))
            }, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** yyyy-mm-dd hh:mm (UTC-5, Colombia) without extra date libraries. */
fun formatDate(millis: Long): String {
    if (millis <= 0) return ""
    val local = millis - 5 * 3600_000L
    val days = local.floorDiv(86_400_000L)
    val secOfDay = (local - days * 86_400_000L) / 1000
    // Civil-from-days (Howard Hinnant)
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    if (m <= 2) y += 1
    val hh = secOfDay / 3600; val mm = (secOfDay % 3600) / 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return "$y-${two(m)}-${two(d)} ${two(hh)}:${two(mm)}"
}

@Composable
internal fun SectionLabel(text: String) =
    Text(text, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))

@Composable
internal fun TemplateTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) cs.primary else cs.outlineVariant, tween(220))
    val bg by animateColorAsState(if (selected) cs.primaryContainer else cs.surfaceContainerHigh, tween(220))
    val src = remember { MutableInteractionSource() }
    Surface(
        selected = selected, onClick = onClick, shape = MaterialTheme.shapes.small, color = bg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border), interactionSource = src, modifier = modifier.pressScale(src),
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
        }
    }
}
