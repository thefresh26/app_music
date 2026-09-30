package com.activos.pentagrama.platform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.activos.pentagrama.audio.MonoDownsampler
import com.activos.pentagrama.audio.PcmAudio
import com.activos.pentagrama.model.ScoreJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

object AndroidPlatform {
    lateinit var appContext: Context
    fun init(context: Context) { appContext = context.applicationContext }
}

actual object ScoreStorage {
    private val dir: File
        get() = File(AndroidPlatform.appContext.filesDir, "scores").also { it.mkdirs() }

    actual fun list(): List<StoredScore> = dir.listFiles { f -> f.extension == "json" }.orEmpty().map { f ->
        val title = runCatching { ScoreJson.decode(f.readText()).title }.getOrDefault(f.nameWithoutExtension)
        StoredScore(f.nameWithoutExtension, title, f.lastModified())
    }.sortedByDescending { it.modified }

    actual fun load(id: String): String? = File(dir, "$id.json").takeIf { it.exists() }?.readText()
    actual fun save(id: String, json: String) { File(dir, "$id.json").writeText(json) }
    actual fun delete(id: String) { File(dir, "$id.json").delete() }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual val platformName: String = "Android"

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) return c.getString(i)
        }
    }
    return uri.lastPathSegment ?: "audio"
}

private class UriAudioSource(private val context: Context, private val uri: Uri) : AudioSource {
    override val name: String = displayName(context, uri)

    override suspend fun decode(onProgress: (Float) -> Unit): PcmAudio = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("El archivo no contiene una pista de audio")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val ds = MonoDownsampler()
        ds.configure(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var pcmFloat = false
        var shorts = ShortArray(0)
        var floats = FloatArray(0)
        try {
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        val ob = codec.getOutputBuffer(outIndex)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        if (pcmFloat) {
                            val fb = ob.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            val n = fb.remaining()
                            if (floats.size < n) floats = FloatArray(n)
                            fb.get(floats, 0, n)
                            ds.pushFloats(floats, n, channels)
                        } else {
                            val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val n = sb.remaining()
                            if (shorts.size < n) shorts = ShortArray(n)
                            sb.get(shorts, 0, n)
                            ds.pushShorts(shorts, n, channels)
                        }
                        if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        ds.configure(of.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                        if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmFloat = of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        onProgress(1f)
        ds.result(name)
    }
}

@Composable
actual fun rememberAudioPicker(onPicked: (AudioSource) -> Unit): () -> Unit {
    val context = LocalContext.current
    val cb by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) cb(UriAudioSource(context, uri))
    }
    return { launcher.launch(arrayOf("audio/*")) }
}

@Composable
actual fun rememberFileSaver(onResult: (String) -> Unit): FileSaver {
    val context = LocalContext.current
    val cb by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val content = pending
        pending = null
        if (uri != null && content != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } }
                .onSuccess { cb("Archivo guardado") }
                .onFailure { cb("No se pudo guardar: ${it.message}") }
        }
    }
    return remember(launcher) {
        FileSaver { fileName, _, content ->
            pending = content
            launcher.launch(fileName)
        }
    }
}

@Composable
actual fun rememberTextFileOpener(onOpened: (name: String, text: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val cb by rememberUpdatedState(onOpened)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (text != null) cb(displayName(context, uri), text)
        }
    }
    return { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
}
