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
}
