package com.activos.pentagrama.platform

import androidx.compose.runtime.Composable
import com.activos.pentagrama.audio.PcmAudio

data class StoredScore(val id: String, val title: String, val modified: Long)

/** Maximum size of a score file the user can open (bytes). */
const val MAX_SCORE_FILE_BYTES = 5L * 1024 * 1024

private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

/** Rejects ids that could escape the storage folder (e.g. "../"). */
fun requireSafeId(id: String): String {
    require(SAFE_ID.matches(id)) { "Identificador de partitura inválido" }
    return id
}

/** Local persistence of scores (JSON text). */
expect object ScoreStorage {
    fun list(): List<StoredScore>
    fun load(id: String): String?
    fun save(id: String, json: String)
    fun delete(id: String)
}

/** An audio file chosen by the user that can be decoded to mono PCM. */
interface AudioSource {
    val name: String
    suspend fun decode(onProgress: (Float) -> Unit): PcmAudio
}

/** Opens the system file picker for audio (mp3, wav, m4a, ogg...). */
@Composable
expect fun rememberAudioPicker(onPicked: (AudioSource) -> Unit): () -> Unit

/** Saves text content to a user-chosen file. */
fun interface FileSaver {
    fun save(fileName: String, mime: String, content: String)
}

@Composable
expect fun rememberFileSaver(onResult: (String) -> Unit): FileSaver

/** Opens a text file (.pentagrama / .json) chosen by the user. */
@Composable
expect fun rememberTextFileOpener(onOpened: (name: String, text: String) -> Unit): () -> Unit

/** Plays mono 16-bit PCM (one sound at a time). */
expect object AudioPlayer {
    fun play(pcm: ShortArray, sampleRate: Int)
    fun stop()
}

expect fun currentTimeMillis(): Long

expect val platformName: String
