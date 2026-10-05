package com.activos.pentagrama

import com.activos.pentagrama.audio.MonoDownsampler
import com.activos.pentagrama.audio.Synth
import com.activos.pentagrama.model.EndBar
import com.activos.pentagrama.model.Event
import com.activos.pentagrama.model.Measure
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.model.StartBar
import com.activos.pentagrama.audio.TranscribeMode
import com.activos.pentagrama.audio.TranscribeOptions
import com.activos.pentagrama.audio.Transcriber
import com.activos.pentagrama.editor.ScoreOps
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.MusicXml
import com.activos.pentagrama.model.Pitch
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.ScoreJson
import com.activos.pentagrama.model.Templates
import com.activos.pentagrama.render.ScoreLayout
import com.activos.pentagrama.symbols.SymbolCatalog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {

    private fun synth(melody: List<Pair<Int, Double>>, bpm: Double, sr: Int = 22050): FloatArray {
        val beat = 60.0 / bpm
        val n = ((melody.sumOf { it.second } * beat + 0.6) * sr).toInt()
        val x = FloatArray(n)
        var t = 0.25
        for ((m, d) in melody) {
            val f = 440.0 * 2.0.pow((m - 69) / 12.0)
            val s0 = (t * sr).toInt(); val s1 = min(n, ((t + d * beat * 0.95) * sr).toInt())
            for (i in s0 until s1) {
                val tt = (i - s0).toDouble() / sr
                val env = min(1.0, tt * 50) * exp(-tt * 1.2)
                x[i] += (env * (0.6 * sin(2 * PI * f * tt) + 0.25 * sin(4 * PI * f * tt))).toFloat()
            }
            t += d * beat
        }
        return x
    }

    @Test
    fun transcribesSimpleMelody() {
        val melody = listOf(67 to 1.0, 69 to 1.0, 71 to 1.0, 72 to 1.0, 74 to 2.0, 71 to 2.0, 67 to 4.0)
        val x = synth(melody, 120.0)
        val ds = MonoDownsampler(); ds.configure(22050); ds.pushFloats(x, x.size, 1)
        val r = Transcriber.transcribe(ds.result("test.mp3"), TranscribeOptions(mode = TranscribeMode.MELODY))
        assertTrue(r.bpm in 110..130, "bpm=${r.bpm}")
        val first = r.score.measures.first().events.filter { it.kind == EventKind.NOTE }.map { it.pitches.first().midi }
        assertEquals(listOf(67, 69, 71, 72), first)
    }

    @Test
    fun jsonRoundTrip() {
        val s = Templates.demoChart()
        assertEquals(s, ScoreJson.decode(ScoreJson.encode(s)))
    }

    @Test
    fun layoutAndHitTest() {
        val s = Templates.demoChart()
        val l = ScoreLayout.build(s, 1000f, 10f)
        assertTrue(l.systems.all { it.measures.size <= 4 })
        val m = l.systems[0].measures[1]
        val hit = l.hitTest(m.events[0].x + 2f, l.systems[0].yForStep(4, 10f))
        assertEquals(1, hit?.measure)
        assertEquals(0, hit?.eventIndex)
    }

    @Test
    fun placeNoteUsesKeySignature() {
        val s = Score(keyFifths = 1) // G major: F is sharp
        val l = ScoreLayout.build(s, 1000f, 10f)
        val sys = l.systems[0]
        val hit = l.hitTest(sys.measures[0].contentX + 5f, sys.yForStep(8, 10f))!! // top line = F5
        val action = SymbolCatalog.curated.first { it.id == "note_QUARTER" }.action
        val r = ScoreOps.apply(s, hit, action, chordMode = false)
        assertEquals(Pitch(3, 5, 1), r.score.measures[0].events[0].pitches[0])
    }

    @Test
    fun musicXmlHasMeasures() {
        val xml = MusicXml.export(Templates.demoChart())
        assertTrue(xml.contains("<score-partwise"))
        assertTrue(xml.contains("<harmony>"))
    }

    @Test
    fun searchIgnoresAccents() {
        assertTrue(SymbolCatalog.curated.any { it.matches("calderon") })
        assertTrue(SymbolCatalog.curated.any { it.matches("clave fa") })
    }

    @Test
    fun rejectsMaliciousFiles() {
        fun rejected(t: String) = runCatching { ScoreJson.decode(t) }.isFailure
        assertTrue(rejected("[".repeat(10_000)), "anidamiento profundo")
        assertTrue(rejected("[1,2]"), "no es objeto")
        assertTrue(rejected("{}basura"), "contenido extra")
        assertTrue(rejected("{\"key\": 1e999}"), "número infinito")
        val s = ScoreJson.decode("{\"title\":\"" + "a".repeat(1000) + "\",\"key\":99,\"timeDen\":7}")
        assertEquals(200, s.title.length)
        assertEquals(7, s.keyFifths)
        assertEquals(4, s.timeDen)
    }

    @Test
    fun rejectsUnsafeStorageIds() {
        assertTrue(runCatching { com.activos.pentagrama.platform.requireSafeId("../../etc/passwd") }.isFailure)
        assertEquals("s123", com.activos.pentagrama.platform.requireSafeId("s123"))
    }

    @Test
    fun notasRepetidasSeSeparan() {
        val x = synth(listOf(67 to 1.0, 67 to 1.0, 67 to 1.0, 67 to 1.0, 69 to 2.0, 69 to 2.0), 120.0)
        val ds = MonoDownsampler(); ds.configure(22050); ds.pushFloats(x, x.size, 1)
        val r = Transcriber.transcribe(ds.result("rep.mp3"), TranscribeOptions(mode = TranscribeMode.MELODY, bpm = 120))
        val first = r.score.measures.first().events.filter { it.kind == EventKind.NOTE }.map { it.pitches.first().midi }
        assertEquals(listOf(67, 67, 67, 67), first)
    }

    @Test
    fun reproduccionSuenaYRespetaRepeticiones() {
        // |: 1 | 2 (casilla 1) :| 3 (casilla 2) | 4  ->  1 2 1 3 4
        val rep = Score(measures = listOf(Measure(startBar = StartBar.REPEAT_START), Measure(ending = "1.", endBar = EndBar.REPEAT_END), Measure(ending = "2."), Measure()))
        assertEquals(listOf(0, 1, 0, 2, 3), Synth.playbackOrder(rep))

        // Una redonda de Do4 a 60 BPM: 4 s de audio a ~261,6 Hz
        val c4 = Score(tempoBpm = 60, measures = listOf(Measure(events = listOf(Event(EventKind.NOTE, NoteValue.WHOLE, listOf(Pitch(0, 4)))))))
        val r = Synth.render(c4)
        assertTrue(r.pcm.size >= 4 * r.sampleRate)
        var zc = 0
        for (i in 1 until r.sampleRate) if ((r.pcm[i - 1] < 0) != (r.pcm[i] < 0)) zc++
        assertTrue(zc / 2 in 255..268, "frecuencia=${zc / 2}")

        // El ejemplo (acordes + barras rítmicas) produce sonido
        assertTrue(Synth.render(Templates.demoChart()).pcm.any { it > 1000 })
        assertEquals(listOf(9, 3), Synth.parseChord("Am7")?.let { listOf(it.first, it.second[1]) })
    }

    @Test
    fun pdfValidoYReproduccionNotaPorNota() {
        val pdf = com.activos.pentagrama.render.PdfWriter.write(listOf(Triple(ByteArray(10), 4, 4), Triple(ByteArray(10), 4, 4))).decodeToString()
        assertTrue(pdf.startsWith("%PDF-1.4") && "/Count 2" in pdf)
        val xref = pdf.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(pdf.substring(xref).startsWith("xref"))

        val cues = Synth.render(Templates.demoChart()).cues
        assertTrue(cues.size > 100 && cues.zipWithNext().all { it.first.sec <= it.second.sec })
        assertEquals(0, cues.first().event)
    }

    @Test
    fun laNotaVaDondeSeToca() {
        // Una negra tocada al 60 % de un compás vacío de 4/4 cae en el tiempo 3, con un silencio de blanca antes.
        val score = Score(measures = listOf(Measure(), Measure()))
        val l = ScoreLayout.build(score, 1000f, 10f)
        val sys = l.systems[0]
        val m = sys.measures[0]
        val negra = SymbolCatalog.curated.first { it.id == "note_QUARTER" }.action
        val r = ScoreOps.apply(score, l.hitTest(m.contentX + 0.6f * m.contentWidth, sys.yForStep(4, 10f))!!, negra, chordMode = false)
        val ev = r.score.measures[0].events
        assertEquals(listOf(EventKind.REST, EventKind.NOTE), ev.map { it.kind })
        assertEquals(NoteValue.HALF, ev[0].value)
    }

    @Test
    fun sePuedenAgregarVariasNotasEnUnCompas() {
        var score = Score(measures = listOf(Measure(), Measure()))
        val negra = SymbolCatalog.curated.first { it.id == "note_QUARTER" }.action
        for (frac in listOf(0.02f, 0.3f, 0.55f, 0.8f)) {
            val l = ScoreLayout.build(score, 1000f, 10f)
            val sys = l.systems[0]
            val m = sys.measures[0]
            val x = maxOf(m.contentX + frac * m.contentWidth, (m.events.lastOrNull()?.x ?: 0f) + 40f)
            score = ScoreOps.apply(score, l.hitTest(x, sys.yForStep(4, 10f))!!, negra, chordMode = false).score
        }
        assertEquals(List(4) { NoteValue.QUARTER }, score.measures[0].events.map { it.value })
    }

    @Test
    fun laBarraRitmicaSeEscucha() {
        val slashes = Measure(events = List(4) { Event(EventKind.SLASH, NoteValue.QUARTER) })
        // Sin acorde: suena un golpe por cada barra. Con acorde: suena el acorde.
        for (m in listOf(slashes, slashes.copy(chords = listOf(com.activos.pentagrama.model.ChordMark(0, "G"))))) {
            val r = Synth.render(Score(tempoBpm = 120, measures = listOf(m)))
            for (beat in 0 until 4) {
                val at = (beat * 0.5 * r.sampleRate).toInt()
                val peak = (at until at + r.sampleRate / 20).maxOf { kotlin.math.abs(r.pcm[it].toInt()) }
                assertTrue(peak > 1_000, "la barra $beat no suena (pico $peak)")
            }
        }
        assertTrue(Synth.preview(emptyList()).any { kotlin.math.abs(it.toInt()) > 1_000 })
        assertEquals(4, Synth.slashNotes(Score(measures = listOf(Measure(chords = listOf(com.activos.pentagrama.model.ChordMark(0, "G"))), slashes)), 1, 0).size)
    }

    /** Acordes sostenidos (tipo órgano) + melodía punteada + ruido: como una canción con acompañamiento. */
    private fun cancionConAcompanamiento(bpm: Int): Pair<List<List<Set<Int>>>, com.activos.pentagrama.audio.PcmAudio> {
        val prog = listOf(listOf(48, 64, 67), listOf(53, 65, 69), listOf(55, 62, 71), listOf(48, 64, 67))
        val mel = listOf(listOf(72, 74, 76, 77), listOf(77, 76, 74, 72), listOf(74, 79, 77, 74), listOf(72, 72, 79, 79))
        val sr = 22050; val q = 60.0 / bpm
        val x = FloatArray(((prog.size * 4 + 1) * q * sr).toInt())
        var rnd = 1L
        for (m in prog.indices) for (k in 0 until 4) {
            val t = (m * 4 + k) * q
            for (midi in prog[m] + mel[m][k]) {
                val isMel = midi == mel[m][k]
                val f = 440.0 * 2.0.pow((midi - 69) / 12.0)
                val s0 = (t * sr).toInt()
                for (i in 0 until (q * sr).toInt()) {
                    val tt = i.toDouble() / sr
                    val env = if (isMel) min(1.0, tt * 300) * exp(-tt * 3) * 0.5 else 0.12
                    var v = 0.0
                    for (h in 1..8) v += sin(2 * PI * f * (t + tt) * h) / h.toDouble().pow(if (isMel) 1.2 else 0.8)
                    x[s0 + i] += (env * v).toFloat()
                }
            }
        }
        for (i in x.indices) { rnd = rnd * 6364136223846793005L + 1; x[i] += ((rnd ushr 40).toInt() / 16777216f - 0.5f) * 0.05f }
        val truth = prog.indices.map { m -> List(16) { u -> (prog[m] + mel[m][u / 4]).toSet() } }
        return truth to com.activos.pentagrama.audio.PcmAudio(x, sr, "cancion.wav")
    }

    @Test
    fun todosLosSonidosDeUnaCancion() {
        for (bpm in listOf(80, 120)) {
            val (truth, audio) = cancionConAcompanamiento(bpm)
            val r = Transcriber.transcribe(audio, TranscribeOptions(mode = TranscribeMode.ALL))
            assertTrue(abs(r.bpm - bpm) <= 2, "bpm=${r.bpm}")
            // Cada semicorchea: qué notas suenan en la partitura vs. en la canción.
            var tp = 0; var fp = 0; var fn = 0
            for ((mi, m) in truth.withIndex()) {
                val got = r.score.measures[mi].events.flatMap { e -> List(e.ticks / 24) { if (e.kind == EventKind.NOTE) e.pitches.map { it.midi }.toSet() else emptySet() } }
                for (u in 0 until 16) { val g = got.getOrElse(u) { emptySet() }; tp += (m[u] intersect g).size; fp += (g - m[u]).size; fn += (m[u] - g).size }
            }
            val precision = tp.toDouble() / (tp + fp); val recall = tp.toDouble() / (tp + fn)
            println("todos los sonidos a $bpm BPM: precisión=$precision cobertura=$recall")
            assertTrue(precision > 0.85 && recall > 0.8, "precisión=$precision cobertura=$recall")
            assertTrue(r.score.measures.all { it.usedTicks == r.score.measureTicks })
        }
    }

    @Test
    fun notaLigadaSuenaUnaSolaVez() {
        // Un Do que dura 2 compases (ligado) no se vuelve a atacar al empezar el compás 2, aunque cambie la melodía encima.
        fun render(tie: Boolean) = Synth.render(Score(tempoBpm = 120, measures = List(2) { mi ->
            Measure(events = List(4) { k -> Event(EventKind.NOTE, NoteValue.QUARTER, listOf(Pitch.fromMidi(48), Pitch.fromMidi(72 + k)), tieToNext = tie && !(mi == 1 && k == 3)) })
        }))
        fun bassEnergyAt(sec: Double, r: com.activos.pentagrama.audio.Rendered): Double {
            val a = (sec * r.sampleRate).toInt()
            return (a until a + 1000).sumOf { kotlin.math.abs(r.pcm[it].toDouble()) }
        }
        // Mismo audio salvo el bajo: si está ligado, a los 2,05 s suena menos (no hay ataque nuevo).
        assertTrue(bassEnergyAt(2.05, render(true)) < bassEnergyAt(2.05, render(false)))
        // Y no se queda colgado: la ligadura solo une la misma nota en notas seguidas.
        assertEquals(render(true).pcm.size, render(false).pcm.size)
    }

    @Test
    fun laRedondaSuenaSusCuatroTiempos() {
        // A 60 BPM una redonda dura 4 s: debe seguir sonando parejo hasta el final y callarse al terminar.
        val r = Synth.render(Score(tempoBpm = 60, measures = listOf(Measure(events = listOf(Event(EventKind.NOTE, NoteValue.WHOLE, listOf(Pitch.fromMidi(60))))))))
        fun level(sec: Double) = (0 until 2205).maxOf { kotlin.math.abs(r.pcm[(sec * r.sampleRate).toInt() + it].toInt()) }
        val inicio = level(0.6); val final = level(3.7)
        println("redonda: nivel a 0,6 s=$inicio, a 3,7 s=$final, a 4,3 s=${level(4.3)}")
        assertTrue(final > inicio * 0.6, "la redonda se apaga antes de tiempo ($inicio → $final)")
        assertTrue(level(4.3) < inicio * 0.05, "la redonda sigue sonando después de sus 4 tiempos")
        // Al escribirla también suena su duración completa, no un toque corto.
        val p = Synth.preview(listOf(60), 4.0)
        assertTrue(p.size >= 4 * Synth.SAMPLE_RATE)
        assertTrue((0 until 2205).maxOf { kotlin.math.abs(p[(3.7 * Synth.SAMPLE_RATE).toInt() + it].toInt()) } > inicio * 0.6)
    }
}
