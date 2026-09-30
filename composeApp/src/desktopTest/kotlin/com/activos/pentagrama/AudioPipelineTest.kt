package com.activos.pentagrama

import com.activos.pentagrama.audio.TranscribeMode
import com.activos.pentagrama.audio.TranscribeOptions
import com.activos.pentagrama.audio.Transcriber
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.platform.FileAudioSource
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba de funcionamiento de punta a punta: archivo de audio real -> decodificador -> transcripción -> partitura.
 * melodia_prueba.mp3 contiene Sol4 La4 Si4 Do5 (negras) | Re5 Si4 (blancas) | Sol4 (redonda) a 120 BPM.
 */
class AudioPipelineTest {

    private val expectedFirstMeasure = listOf(67, 69, 71, 72)

    @Test
    fun mp3SeDecodificaYSeTranscribe() = runBlocking {
        val file = File(javaClass.getResource("/melodia_prueba.mp3")!!.toURI())
        var lastProgress = 0f
        val pcm = FileAudioSource(file).decode { lastProgress = it }
        assertEquals(1f, lastProgress)
        assertTrue(pcm.durationSec in 6.0..7.5, "duración=${pcm.durationSec}")

        val r = Transcriber.transcribe(pcm, TranscribeOptions(mode = TranscribeMode.MELODY_AND_CHORDS))
        println("MP3: rate=${pcm.sampleRate} dur=${pcm.durationSec} bpm=${r.bpm} key=${r.score.keyFifths} notas=${r.notes}")
        r.score.measures.forEachIndexed { i, m ->
            println("  compás ${i + 1}: " + m.events.joinToString(" ") { e -> (e.pitches.firstOrNull()?.name() ?: e.kind.name) + ":" + e.value.name })
        }
        assertTrue(r.bpm in 110..130, "bpm=${r.bpm}")
        assertEquals(1, r.score.keyFifths, "armadura (Sol mayor / Mi menor)")
        val first = r.score.measures.first().events.filter { it.kind == EventKind.NOTE }.map { it.pitches.first().midi }
        assertEquals(expectedFirstMeasure, first)
        val second = r.score.measures[1].events.filter { it.kind == EventKind.NOTE }.map { it.pitches.first().midi }
        assertEquals(listOf(74, 71), second)
        assertTrue(r.score.measures.all { it.usedTicks <= r.score.measureTicks }, "ningún compás desbordado")
    }

    @Test
    fun wavSeDecodificaYSeTranscribe() = runBlocking {
        val sr = 22050f
        val melody = listOf(67 to 1.0, 69 to 1.0, 71 to 1.0, 72 to 1.0, 74 to 2.0, 71 to 2.0, 67 to 4.0)
        val beat = 0.5
        val n = ((melody.sumOf { it.second } * beat + 0.6) * sr).toInt()
        val x = DoubleArray(n)
        var t = 0.25
        for ((m, d) in melody) {
            val f = 440.0 * 2.0.pow((m - 69) / 12.0)
            val s0 = (t * sr).toInt(); val s1 = min(n, ((t + d * beat * 0.95) * sr).toInt())
            for (i in s0 until s1) {
                val tt = (i - s0) / sr.toDouble()
                x[i] += min(1.0, tt * 50) * exp(-tt * 1.2) * (0.6 * sin(2 * PI * f * tt) + 0.25 * sin(4 * PI * f * tt))
            }
            t += d * beat
        }
        val bytes = ByteArray(n * 4)
        for (i in 0 until n) {
            val v = (x[i].coerceIn(-1.0, 1.0) * 30000).toInt()
            for (c in 0 until 2) { // estéreo
                bytes[i * 4 + c * 2] = (v and 0xFF).toByte(); bytes[i * 4 + c * 2 + 1] = (v shr 8).toByte()
            }
        }
        val fmt = AudioFormat(sr, 16, 2, true, false)
        val wav = File.createTempFile("melodia", ".wav").apply { deleteOnExit() }
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes), fmt, n.toLong()), AudioFileFormat.Type.WAVE, wav)

        val pcm = FileAudioSource(wav).decode {}
        val r = Transcriber.transcribe(pcm, TranscribeOptions(mode = TranscribeMode.MELODY))
        val first = r.score.measures.first().events.filter { it.kind == EventKind.NOTE }.map { it.pitches.first().midi }
        assertEquals(expectedFirstMeasure, first)
    }

    @Test
    fun archivoQueNoEsAudioFallaConError() {
        val bogus = File.createTempFile("falso", ".wav").apply { writeText("esto no es audio"); deleteOnExit() }
        val result = runCatching { runBlocking { FileAudioSource(bogus).decode {} } }
        assertTrue(result.isFailure, "debe rechazar un archivo que no es audio")
    }
}
