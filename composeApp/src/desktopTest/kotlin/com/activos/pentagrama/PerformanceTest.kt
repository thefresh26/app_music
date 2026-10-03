package com.activos.pentagrama

import com.activos.pentagrama.audio.MonoDownsampler
import com.activos.pentagrama.audio.TranscribeMode
import com.activos.pentagrama.audio.TranscribeOptions
import com.activos.pentagrama.audio.Transcriber
import com.activos.pentagrama.model.ChordMark
import com.activos.pentagrama.model.Event
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.Measure
import com.activos.pentagrama.model.MusicXml
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.model.Pitch
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.ScoreJson
import com.activos.pentagrama.render.ScoreLayout
import com.activos.pentagrama.symbols.SmuflGlyph
import com.activos.pentagrama.symbols.SymbolCatalog
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pruebas de rendimiento. Cada una mide el tiempo real de una operación con datos grandes y falla si supera
 * un límite holgado (pensado para un PC lento / un servidor de CI). Los resultados se imprimen, se guardan en
 * build/test-screenshots/rendimiento.md (GitHub Actions lo muestra en el resumen del job).
 */
class PerformanceTest {

    companion object {
        private val rows = mutableListOf<Triple<String, String, String>>()

        fun report(name: String, value: String, limit: String) {
            rows += Triple(name, value, limit)
            println("RENDIMIENTO | $name | $value | límite $limit")
            val md = buildString {
                appendLine("## Pruebas de rendimiento")
                appendLine()
                appendLine("| Prueba | Resultado | Límite |")
                appendLine("|---|---|---|")
                rows.forEach { (n, v, l) -> appendLine("| $n | $v | $l |") }
            }
            File("build/test-screenshots").apply { mkdirs() }.resolve("rendimiento.md").writeText(md)
        }
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val t0 = System.nanoTime()
        val r = block()
        return r to (System.nanoTime() - t0) / 1_000_000
    }

    /** Canción sintética: melodía de 8 notas repetida + acordes, a 44,1 kHz estéreo. */
    private fun song(seconds: Int): com.activos.pentagrama.audio.PcmAudio {
        val sr = 44_100
        val ds = MonoDownsampler(); ds.configure(sr)
        val block = FloatArray(sr * 2) // 1 s estéreo
        val melody = intArrayOf(67, 69, 71, 72, 74, 72, 71, 69)
        val beat = 0.5
        for (sec in 0 until seconds) {
            for (i in 0 until sr) {
                val t = sec + i.toDouble() / sr
                val k = (t / beat).toInt()
                val tt = t - k * beat
                val f = 440.0 * 2.0.pow((melody[k % melody.size] - 69) / 12.0)
                val chord = if ((k / 8) % 2 == 0) 55 else 60
                var v = min(1.0, tt * 50) * exp(-tt * 2) * 0.6 * sin(2 * PI * f * tt)
                for (iv in intArrayOf(0, 4, 7)) v += 0.06 * sin(2 * PI * 440.0 * 2.0.pow((chord + iv - 69) / 12.0) * t)
                block[2 * i] = v.toFloat(); block[2 * i + 1] = v.toFloat()
            }
            ds.pushFloats(block, block.size, 2)
        }
        return ds.result("rendimiento.mp3")
    }

    @Test
    fun transcripcionDeUnaCancionDe3Minutos() {
        val audio = song(180)
        Transcriber.transcribe(song(10)) // calentamiento de la JVM
        val (r, ms) = timed { Transcriber.transcribe(audio, TranscribeOptions(mode = TranscribeMode.MELODY_AND_CHORDS)) }
        val factor = 180_000.0 / ms
        report("Transcribir canción de 3 min (melodía + acordes)", "$ms ms (${"%.0f".format(factor)}× tiempo real)", "< 30 000 ms")
        assertTrue(ms < 30_000, "tardó $ms ms")
        println("compases=${r.score.measures.size} bpm=${r.bpm} tonalidad=${r.key}")
        assertTrue(r.score.measures.isNotEmpty())
    }

    @Test
    fun todosLosSonidosDeUnaCancionDe3Minutos() {
        val audio = song(180)
        Transcriber.transcribe(song(10), TranscribeOptions(mode = TranscribeMode.ALL)) // calentamiento
        val (r, ms) = timed { Transcriber.transcribe(audio, TranscribeOptions(mode = TranscribeMode.ALL)) }
        report("Pentagrama completo (todos los sonidos) de canción de 3 min", "$ms ms (${"%.0f".format(180_000.0 / ms)}× tiempo real, ${r.notes} notas)", "< 30 000 ms")
        assertTrue(ms < 30_000, "tardó $ms ms")
        assertTrue(r.notes > 100)
    }

    @Test
    fun memoriaDelAudioDecodificado() {
        val audio = song(60)
        val mb = audio.samples.size * 4 / 1_048_576.0
        report("Memoria de 1 min de audio decodificado", "${"%.1f".format(mb)} MB (máx. 15 min ≈ ${"%.0f".format(mb * 15)} MB)", "< 6 MB/min")
        assertTrue(mb < 6.0, "usa $mb MB por minuto")
    }

    private fun bigScore(measures: Int): Score {
        val notes = listOf(60, 62, 64, 65, 67, 69, 71, 72)
        return Score(
            title = "Partitura grande", keyFifths = 2,
            measures = List(measures) { i ->
                Measure(
                    events = List(8) { k -> Event(EventKind.NOTE, NoteValue.EIGHTH, listOf(Pitch.fromMidi(notes[(i + k) % 8], 2))) },
                    chords = listOf(ChordMark(0, "D"), ChordMark(192, "A7")),
                    section = if (i % 16 == 0) "Sección ${i / 16 + 1}" else null,
                )
            },
        )
    }

    @Test
    fun layoutDePartituraDe500Compases() {
        val score = bigScore(500)
        repeat(3) { ScoreLayout.build(score, 1080f, 17f) }
        val (layout, ms) = timed { ScoreLayout.build(score, 1080f, 17f) }
        report("Acomodar partitura de 500 compases / 4 000 notas", "$ms ms (${layout.systems.size} líneas)", "< 300 ms")
        assertTrue(ms < 300, "tardó $ms ms")
        val (_, hitMs) = timed { repeat(1_000) { layout.hitTest(500f, layout.systems[it % layout.systems.size].top + 100f) } }
        report("1 000 toques sobre el pentagrama (hit test)", "$hitMs ms", "< 200 ms")
        assertTrue(hitMs < 200)
    }

    @Test
    fun guardarYAbrirPartituraGrande() {
        val score = bigScore(1_000)
        repeat(2) { ScoreJson.decode(ScoreJson.encode(score)) }
        val (json, encMs) = timed { ScoreJson.encode(score) }
        val (_, decMs) = timed { ScoreJson.decode(json) }
        report("Guardar partitura de 1 000 compases (JSON)", "$encMs ms, ${json.length / 1024} KB", "< 1 000 ms")
        report("Abrir partitura de 1 000 compases (JSON)", "$decMs ms", "< 1 500 ms")
        assertTrue(encMs < 1_000 && decMs < 1_500)
        val (xml, xmlMs) = timed { MusicXml.export(score) }
        report("Exportar MusicXML de 1 000 compases", "$xmlMs ms, ${xml.length / 1024} KB", "< 1 500 ms")
        assertTrue(xmlMs < 1_500)
    }

    @Test
    fun buscadorDeFiguras() {
        val tsv = File("src/commonMain/composeResources/files/smufl.tsv").readText()
        val (glyphs, loadMs) = timed { SmuflGlyph.parseTsv(tsv) }
        report("Cargar catálogo de ${glyphs.size} figuras SMuFL", "$loadMs ms", "< 500 ms")
        assertTrue(loadMs < 500)
        val queries = listOf("negra", "calderon", "clave fa", "sostenido", "coda", "trino", "pedal", "xyz")
        repeat(3) { queries.forEach { q -> SymbolCatalog.curated.filter { it.matches(q) }; glyphs.filter { it.matches(q) } } }
        val (_, searchMs) = timed {
            queries.forEach { q -> SymbolCatalog.curated.filter { it.matches(q) }; glyphs.filter { it.matches(q) } }
        }
        val per = searchMs.toDouble() / queries.size
        report("Búsqueda de figuras (por consulta, ${glyphs.size + SymbolCatalog.curated.size} figuras)", "${"%.1f".format(per)} ms", "< 50 ms")
        assertTrue(per < 50)
    }

    @Test
    fun sonidoAlEscribirYAlDarPlay() {
        // Lo que se oye al tocar una nota, un acorde o una barra rítmica: debe estar listo casi al instante.
        val casos = listOf("nota" to listOf(67), "acorde" to listOf(43, 55, 59, 62), "barra rítmica sin acorde" to emptyList())
        repeat(20) { casos.forEach { (_, m) -> com.activos.pentagrama.audio.Synth.preview(m) } }
        for ((nombre, midis) in casos) {
            val (_, ms) = timed { repeat(100) { com.activos.pentagrama.audio.Synth.preview(midis) } }
            val per = ms / 100.0
            report("Preparar el sonido de una $nombre al escribirla", "${"%.2f".format(per)} ms", "< 20 ms")
            assertTrue(per < 20, "$nombre tardó $per ms")
        }

        // ▶ Play: antes de sonar se sintetiza toda la partitura. Canción de ~5 min con melodía, acordes y barras rítmicas.
        val base = bigScore(80)
        val score = base.copy(tempoBpm = 120, measures = base.measures + base.measures.take(70).map { m ->
            m.copy(events = List(4) { Event(EventKind.SLASH, NoteValue.QUARTER) })
        })
        repeat(2) { com.activos.pentagrama.audio.Synth.render(score) }
        val (r, ms) = timed { com.activos.pentagrama.audio.Synth.render(score) }
        val secs = r.pcm.size / r.sampleRate
        report("Sintetizar ${score.measures.size} compases (${secs / 60} min ${secs % 60} s) antes de ▶", "$ms ms", "< 2 000 ms")
        assertTrue(ms < 2_000, "tardó $ms ms")
        val (_, uno) = timed { com.activos.pentagrama.audio.Synth.render(score, fromMeasure = score.measures.size - 1) }
        report("▶ desde el último compás", "$uno ms", "< 100 ms")
        assertTrue(uno < 100)
    }
}
