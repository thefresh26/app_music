package com.activos.pentagrama.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.activos.pentagrama.audio.TranscribeMode
import com.activos.pentagrama.audio.TranscribeOptions
import com.activos.pentagrama.audio.Transcriber
import com.activos.pentagrama.audio.TranscriptionResult
import com.activos.pentagrama.platform.AudioSource
import com.activos.pentagrama.platform.rememberAudioPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picks an audio file, asks for options, decodes and transcribes it off the main thread.
 * Returns a function that starts the flow.
 */
@Composable
fun rememberTranscription(onDone: (TranscriptionResult) -> Unit): () -> Unit {
    var source by remember { mutableStateOf<AudioSource?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    val picker = rememberAudioPicker { source = it }

    val src = source
    if (src != null && !running) {
        TranscribeOptionsDialog(
            fileName = src.name,
            onDismiss = { source = null },
            onStart = { opts ->
                running = true; progress = 0f; status = "Decodificando audio…"
                job = scope.launch {
                    try {
                        val pcm = withContext(Dispatchers.Default) {
                            val ctx = coroutineContext
                            src.decode { p -> ctx.ensureActive(); progress = 0.35f * p }
                        }
                        if (pcm.samples.isEmpty()) throw IllegalStateException("No se pudo leer audio del archivo")
                        val result = withContext(Dispatchers.Default) {
                            val ctx = coroutineContext
                            Transcriber.transcribe(pcm, opts) { p, msg ->
                                ctx.ensureActive(); progress = 0.35f + 0.65f * p; status = msg
                            }
                        }
                        done(result)
                    } catch (e: CancellationException) {
                        // cancelled by the user
                    } catch (e: Throwable) {
                        errorMsg = e.message ?: e.toString()
                    } finally {
                        running = false
                        source = null
                    }
                }
            },
        )
    }
    if (running) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Transcribiendo…") },
            text = {
                Column {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { job?.cancel() }) { Text("Cancelar") } },
        )
    }
    errorMsg?.let {
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("No se pudo transcribir") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("Aceptar") } },
        )
    }
    return picker
}

@Composable
private fun TranscribeOptionsDialog(fileName: String, onDismiss: () -> Unit, onStart: (TranscribeOptions) -> Unit) {
    var mode by remember { mutableStateOf(TranscribeMode.MELODY_AND_CHORDS) }
    var bpmText by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(4 to 4) }
    var maxSecText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Llenar el pentagrama desde audio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(fileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("¿Qué quieres obtener?", style = MaterialTheme.typography.labelLarge)
                TranscribeMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = mode == m, onClick = { mode = m }).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(m.es)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Compás", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4 to 4, 3 to 4, 2 to 4, 6 to 8).forEach { t ->
                        FilterChip(selected = time == t, onClick = { time = t }, label = { Text("${t.first}/${t.second}") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bpmText, onValueChange = { bpmText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Tempo (BPM) — vacío = detectar") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxSecText, onValueChange = { maxSecText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Analizar solo los primeros N segundos (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Consejo: funciona mejor con una melodía clara (voz o instrumento solista). " +
                        "Los acordes se estiman de la mezcla completa. Revisa y corrige el resultado en el editor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onStart(
                    TranscribeOptions(
                        mode = mode, bpm = bpmText.toIntOrNull()?.coerceIn(30, 260) ?: 0,
                        timeNum = time.first, timeDen = time.second, maxSeconds = maxSecText.toIntOrNull() ?: 0,
                    )
                )
            }) { Text("Transcribir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
