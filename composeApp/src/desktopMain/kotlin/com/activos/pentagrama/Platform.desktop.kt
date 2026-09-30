package com.activos.pentagrama.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.activos.pentagrama.audio.MonoDownsampler
import com.activos.pentagrama.audio.PcmAudio
import com.activos.pentagrama.model.ScoreJson
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

actual object ScoreStorage {
    private val dir: File
        get() = File(System.getProperty("user.home"), "Pentagrama").also { it.mkdirs() }

    actual fun list(): List<StoredScore> = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { f ->
        val title = runCatching { ScoreJson.decode(f.readText()).title }.getOrDefault(f.nameWithoutExtension)
        StoredScore(f.nameWithoutExtension, title, f.lastModified())
    }.sortedByDescending { it.modified }

    private fun file(id: String) = File(dir, "${requireSafeId(id)}.json")

    actual fun load(id: String): String? = file(id).takeIf { it.exists() && it.length() <= MAX_SCORE_FILE_BYTES }?.readText()

    actual fun save(id: String, json: String) {
        // Atomic write: a crash while saving never leaves a half-written score.
        val target = file(id)
        val tmp = File(dir, "${requireSafeId(id)}.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    actual fun delete(id: String) { file(id).delete() }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual val platformName: String = "Desktop"

private class CountingStream(input: InputStream) : FilterInputStream(input) {
    var count = 0L
    override fun read(): Int = super.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    override fun skip(n: Long): Long = super.skip(n).also { count += it }
}

private class FileAudioSource(private val file: File) : AudioSource {
    override val name: String = file.name

    override suspend fun decode(onProgress: (Float) -> Unit): PcmAudio = withContext(Dispatchers.IO) {
        if (file.extension.equals("mp3", ignoreCase = true)) decodeMp3(onProgress) else decodeJavaSound(onProgress)
    }

    private suspend fun decodeMp3(onProgress: (Float) -> Unit): PcmAudio {
        val total = file.length().coerceAtLeast(1)
        val counting = CountingStream(FileInputStream(file))
        val bitstream = Bitstream(BufferedInputStream(counting, 64 * 1024))
        val decoder = Decoder()
        val ds = MonoDownsampler()
        var frames = 0
        try {
            while (true) {
                if (frames % 200 == 0) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    onProgress((counting.count.toFloat() / total).coerceIn(0f, 1f))
                }
                val header = bitstream.readFrame() ?: break
                val out = decoder.decodeFrame(header, bitstream) as SampleBuffer
                ds.configure(out.sampleFrequency)
                ds.pushShorts(out.buffer, out.bufferLength, out.channelCount)
                bitstream.closeFrame()
                frames++
            }
        } finally {
            runCatching { bitstream.close() }
        }
        onProgress(1f)
        return ds.result(if (ds.truncated) "$name (primeros 15 min)" else name)
    }

    private suspend fun decodeJavaSound(onProgress: (Float) -> Unit): PcmAudio {
        val src = AudioSystem.getAudioInputStream(file)
        val base = src.format
        val target = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
        val pcm = AudioSystem.getAudioInputStream(target, src)
        val ds = MonoDownsampler()
        ds.configure(base.sampleRate.toInt())
        val buf = ByteArray(64 * 1024)
        val shorts = ShortArray(buf.size / 2)
        val totalFrames = src.frameLength.takeIf { it > 0 } ?: -1L
        var readFrames = 0L
        pcm.use { stream ->
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val n = stream.read(buf)
                if (n <= 0) break
                val count = n / 2
                for (i in 0 until count) shorts[i] = ((buf[2 * i].toInt() and 0xFF) or (buf[2 * i + 1].toInt() shl 8)).toShort()
                ds.pushShorts(shorts, count, base.channels)
                readFrames += count / base.channels.coerceAtLeast(1)
                if (totalFrames > 0) onProgress((readFrames.toFloat() / totalFrames).coerceIn(0f, 1f))
            }
        }
        onProgress(1f)
        return ds.result(name)
    }
}

private fun chooseFile(title: String, save: Boolean, suggested: String? = null, filter: (String) -> Boolean = { true }): File? {
    val dialog = FileDialog(null as Frame?, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (suggested != null) dialog.file = suggested
    if (!save) dialog.setFilenameFilter { _, n -> filter(n.lowercase()) }
    dialog.isVisible = true
    val f = dialog.file ?: return null
    return File(dialog.directory, f)
}

@Composable
actual fun rememberAudioPicker(onPicked: (AudioSource) -> Unit): () -> Unit {
    val cb by rememberUpdatedState(onPicked)
    return remember<() -> Unit> {
        {
            chooseFile("Selecciona una canción (mp3, wav)", save = false) {
                it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".aiff") || it.endsWith(".aif") || it.endsWith(".au")
            }?.let { cb(FileAudioSource(it)) }
        }
    }
}

@Composable
actual fun rememberFileSaver(onResult: (String) -> Unit): FileSaver {
    val cb by rememberUpdatedState(onResult)
    return remember {
        FileSaver { fileName, _, content ->
            val f = chooseFile("Guardar como", save = true, suggested = fileName) ?: return@FileSaver
            runCatching { f.writeText(content) }
                .onSuccess { cb("Guardado en ${f.absolutePath}") }
                .onFailure { cb("No se pudo guardar: ${it.message}") }
        }
    }
}

@Composable
actual fun rememberTextFileOpener(onOpened: (name: String, text: String) -> Unit): () -> Unit {
    val cb by rememberUpdatedState(onOpened)
    return remember<() -> Unit> {
        {
            chooseFile("Abrir partitura", save = false) { it.endsWith(".pentagrama") || it.endsWith(".json") }
                ?.takeIf { it.length() <= MAX_SCORE_FILE_BYTES }
                ?.let { f -> runCatching { f.readText() }.getOrNull()?.let { cb(f.name, it) } }
        }
    }
}
