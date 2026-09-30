package com.activos.pentagrama.ui

import androidx.compose.foundation.clickable
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
import com.activos.pentagrama.platform.rememberTextFileOpener

fun newScoreId() = "s" + currentTimeMillis()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(onOpen: (String, Score) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val stored = remember(refresh) { runCatching { ScoreStorage.list() }.getOrDefault(emptyList()) }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<StoredScore?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(info) { info?.let { snackbar.showSnackbar(it); info = null } }

    val transcribe = rememberTranscription { r ->
        val id = newScoreId()
        ScoreStorage.save(id, ScoreJson.encode(r.score))
        refresh++
        onOpen(id, r.score)
    }
    val openFile = rememberTextFileOpener { _, text ->
        runCatching { ScoreJson.decode(text) }
            .onSuccess { s -> val id = newScoreId(); ScoreStorage.save(id, ScoreJson.encode(s)); refresh++; onOpen(id, s) }
            .onFailure { info = "El archivo no es una partitura válida" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pentagrama") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Crea, edita y transcribe partituras", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(Icons.Filled.Add, "Nueva partitura", "En blanco, cifrado o canción") { showNew = true }
                    ActionCard(Icons.Filled.AutoAwesome, "Desde MP3", "Llena el pentagrama automáticamente") { transcribe() }
                    ActionCard(Icons.Filled.FolderOpen, "Abrir archivo", ".pentagrama / .json") { openFile() }
                    ActionCard(Icons.Filled.LibraryMusic, "Ver ejemplo", "Cifrado estilo \"No lo hay\"") {
                        val s = Templates.demoChart(); val id = newScoreId()
                        ScoreStorage.save(id, ScoreJson.encode(s)); refresh++; onOpen(id, s)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Mis partituras", style = MaterialTheme.typography.titleMedium)
                if (stored.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Aún no tienes partituras guardadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(stored, key = { it.id }) { st ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        val text = ScoreStorage.load(st.id)
                        val score = text?.let { runCatching { ScoreJson.decode(it) }.getOrNull() }
                        if (score != null) onOpen(st.id, score) else info = "No se pudo abrir la partitura"
                    },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(st.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDate(st.modified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toDelete = st }) { Icon(Icons.Filled.Delete, "Eliminar") }
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
    toDelete?.let { st ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar partitura") },
            text = { Text("¿Eliminar \"${st.title}\"? Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { ScoreStorage.delete(st.id); toDelete = null; refresh++ }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 150.dp, max = 220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
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
        title = { Text("Nueva partitura") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Plantilla", style = MaterialTheme.typography.labelLarge)
                Templates.Kind.entries.forEach { k ->
                    Row(Modifier.fillMaxWidth().selectable(kind == k) { kind = k }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(kind == k, { kind = k }); Text(k.es)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Clave", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Clef.TREBLE, Clef.BASS, Clef.TREBLE_8VB, Clef.ALTO, Clef.PERCUSSION).forEach { c ->
                        FilterChip(clef == c, { clef = c }, label = { Text(c.es) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Tonalidad (armadura)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (-6..6).forEach { f ->
                        FilterChip(key == f, { key = f }, label = { Text(keyName(f) + " / " + keyName(f, true)) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Compás", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4 to 4, 3 to 4, 2 to 4, 2 to 2, 6 to 8, 12 to 8).forEach { t ->
                        FilterChip(time == t, { time = t }, label = { Text("${t.first}/${t.second}") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(Templates.create(kind, title.ifBlank { "Sin título" }, clef, key, time.first, time.second))
            }) { Text("Crear") }
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
