package com.activos.pentagrama.audio

import com.activos.pentagrama.model.EndBar
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.Measure
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.StartBar
import com.activos.pentagrama.model.TICKS_PER_QUARTER
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Something to highlight while playing: measure, event index (null = whole measure) and start time in seconds. */
data class Cue(val measure: Int, val event: Int?, val sec: Double)

/** Rendered audio plus cues to follow the playback on screen, note by note. */
class Rendered(val pcm: ShortArray, val sampleRate: Int, val measureStarts: List<Pair<Int, Double>>, val cues: List<Cue>)

/**
 * Turns a score into sound: melody notes, chord symbols (pad, or strummed on rhythm slashes) and a bass root.
 * ponytail: simple additive "piano" synth, no SoundFont; repeats and 1st/2nd endings are honored, D.S./Coda jumps are not.
 */
object Synth {
    const val SAMPLE_RATE = 22_050

    /** Measure indices in playback order, expanding |: :| repeats once and skipping "1." endings on the second pass. */
    fun playbackOrder(score: Score, from: Int = 0): List<Int> {
        val ms = score.measures
        val order = mutableListOf<Int>()
        var repeatStart = 0
        val repeatedEnds = mutableSetOf<Int>()
        var secondPass = false
        var i = 0
        while (i < ms.size && order.size < 20_000) {
            val m = ms[i]
            if (m.startBar == StartBar.REPEAT_START && !secondPass) repeatStart = i
            if (!(secondPass && m.ending?.startsWith("1") == true)) order += i
            if (m.endBar == EndBar.REPEAT_END && i !in repeatedEnds) {
                repeatedEnds += i; secondPass = true; i = repeatStart; continue
            }
            if (m.endBar == EndBar.REPEAT_END) secondPass = false
            i++
        }
        val start = order.indexOfFirst { it >= from }.coerceAtLeast(0)
        return order.drop(start)
    }

    /** Root + intervals (semitones) of a chord symbol like "F#m7", "Bb", "B-", "Dsus4", "C/E" (slash bass ignored). */
    fun parseChord(text: String): Pair<Int, IntArray>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val base = intArrayOf(9, 11, 0, 2, 4, 5, 7)["ABCDEFG".indexOf(t[0].uppercaseChar()).takeIf { it >= 0 } ?: return null]
        var i = 1
        var root = base
        while (i < t.length && t[i] in "#b♯♭") { root += if (t[i] == '#' || t[i] == '♯') 1 else -1; i++ }
        val q = t.substring(i).substringBefore('/').lowercase()
        val third = if (q.startsWith("m") && !q.startsWith("maj") || q.startsWith("-") || q.startsWith("dim") || q.startsWith("o") || q.startsWith("ø")) 3 else 4
        val fifth = when { "b5" in q || q.startsWith("dim") || q.startsWith("o") || q.startsWith("ø") -> 6; "+" in q || "aug" in q -> 8; else -> 7 }
        val iv = mutableListOf(0, if ("sus4" in q) 5 else if ("sus2" in q) 2 else third, fifth)
        when {
            "maj7" in q || "7m" in q || "δ" in q -> iv += 11
            q.startsWith("dim7") || q.startsWith("o7") -> iv += 9
            "7" in q || "9" in q -> iv += 10
            "6" in q -> iv += 9
        }
        return root.mod(12) to iv.toIntArray()
    }

    private fun freq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Adds one decaying additive tone into [buf]. Oscillator and envelope run as recurrences (no sin/exp per sample). */
    private fun tone(buf: FloatArray, startSec: Double, durSec: Double, midi: Int, gain: Double) {
        val sr = SAMPLE_RATE
        val s0 = (startSec * sr).toInt()
        val len = ((durSec + 0.08) * sr).toInt()
        val f = freq(midi)
        val w = 2 * PI * f / sr
        val cw = cos(w); val sw = sin(w)
        val decayStep = exp(-(1.2 + f / 400.0) / sr)
        val releaseStep = exp(-1.0 / (0.02 * sr))
        val release = (durSec * sr).toInt()
        var s = 0.0; var c = 1.0 // sin(wk), cos(wk)
        var env = 1.0
        val attack = sr / 200 // 5 ms
        val end = min(len, buf.size - s0)
        for (k in 0 until end) {
            if (s0 + k >= 0) {
                val s2 = 2 * s * c; val c2 = 1 - 2 * s * s
                val x = s + 0.45 * s2 + 0.2 * s * (3 - 4 * s * s) + 0.08 * 2 * s2 * c2
                val a = if (k < attack) k.toDouble() / attack else 1.0
                buf[s0 + k] += (gain * a * env * x).toFloat()
            }
            val ns = s * cw + c * sw; c = c * cw - s * sw; s = ns
            env *= if (k > release) decayStep * releaseStep else decayStep
        }
    }

    /** Short percussive click (filtered noise), used for rhythm slashes without a chord. */
    private fun click(buf: FloatArray, startSec: Double, gain: Double) {
        val s0 = (startSec * SAMPLE_RATE).toInt()
        var seed = 12345; var lp = 0.0; var env = 1.0
        val step = exp(-1.0 / (0.012 * SAMPLE_RATE))
        for (k in 0 until SAMPLE_RATE / 20) {
            val idx = s0 + k
            if (idx >= buf.size) break
            seed = seed * 1103515245 + 12345
            val n = ((seed ushr 16) and 0x7fff) / 16384.0 - 1.0
            lp += 0.35 * (n - lp)
            if (idx >= 0) buf[idx] += (gain * env * (n - lp)).toFloat()
            env *= step
        }
    }

    /** Notes to hear for a rhythm slash at [tick] of measure [mi]: the chord in force there, or empty (= click). */
    fun slashNotes(score: Score, mi: Int, tick: Int): List<Int> {
        val text = score.measures.getOrNull(mi)?.chords?.filter { it.tick <= tick }?.maxByOrNull { it.tick }?.text
            ?: (mi - 1 downTo 0).firstNotNullOfOrNull { i -> score.measures[i].chords.maxByOrNull { it.tick }?.text }
            ?: return emptyList()
        val (bass, notes) = chordNotes(text) ?: return emptyList()
        return listOf(bass) + notes
    }

    private fun chordNotes(text: String): Pair<Int, List<Int>>? {
        val (root, iv) = parseChord(text) ?: return null
        val r = 48 + root // C3..B3
        return (r - 12) to iv.map { r + it }
    }

    /** Short sound of one note or chord, to hear what was just written. */
    fun preview(midis: List<Int>, seconds: Double = 0.6): ShortArray {
        val buf = FloatArray(((seconds + 0.15) * SAMPLE_RATE).toInt())
        if (midis.isEmpty()) click(buf, 0.0, 0.6)
        midis.forEach { tone(buf, 0.0, seconds, it, if (midis.size > 2) 0.12 else 0.25) }
        return ShortArray(buf.size) { (tanh(buf[it] * 1.5) * 30_000).toInt().toShort() }
    }

    fun render(score: Score, fromMeasure: Int = 0): Rendered {
        val sr = SAMPLE_RATE
        val secPerTick = 60.0 / score.tempoBpm.coerceIn(20, 400) / TICKS_PER_QUARTER
        val measureSec = score.measureTicks * secPerTick
        val order = playbackOrder(score, fromMeasure)
        val buf = FloatArray(((order.size * measureSec + 1.0) * sr).toInt().coerceAtMost(sr * 60 * 30))
        val starts = mutableListOf<Pair<Int, Double>>()
        val cues = mutableListOf<Cue>()

        var t0 = 0.0
        var lastContent: Measure? = null
        var currentChord: String? = null
        val tied = mutableSetOf<Int>() // midi notes still sounding from a tie
        for (mi in order) {
            if (t0 * sr >= buf.size) break
            starts += mi to t0
            val written = score.measures[mi]
            val m = if (written.repeatMeasure) (lastContent ?: written) else written.also { lastContent = it }
            if (written.events.isEmpty()) cues += Cue(mi, null, t0)

            // Chord symbols: strum on slashes, otherwise sustained pad until the next chord.
            val chords = m.chords.sortedBy { it.tick }
            fun chordAt(tick: Int): String? = chords.lastOrNull { it.tick <= tick }?.text ?: currentChord
            val hasSlashes = m.events.any { it.kind == EventKind.SLASH }
            if (!hasSlashes) {
                val spans = (if (chords.isEmpty() || chords.first().tick > 0) listOfNotNull(currentChord?.let { 0 to it }) else emptyList()) +
                    chords.map { it.tick to it.text }
                spans.forEachIndexed { k, (tick, text) ->
                    val end = spans.getOrNull(k + 1)?.first ?: score.measureTicks
                    val (bass, notes) = chordNotes(text) ?: return@forEachIndexed
                    val st = t0 + tick * secPerTick; val du = (end - tick) * secPerTick
                    tone(buf, st, du, bass, 0.10)
                    notes.forEach { tone(buf, st, du, it, 0.05) }
                }
            }

            var tick = 0
            for ((ei, e) in m.events.withIndex()) {
                val st = t0 + tick * secPerTick
                if (!written.repeatMeasure) cues += Cue(mi, ei, st)
                val du = e.ticks * secPerTick
                when (e.kind) {
                    EventKind.NOTE -> e.pitches.forEach { p ->
                        if (p.midi in tied) { if (!e.tieToNext) tied -= p.midi; return@forEach }
                        var total = du
                        if (e.tieToNext) { tied += p.midi; total += du } // ponytail: a tie extends one more value, enough for most ties
                        tone(buf, st, total, p.midi, 0.22)
                    }
                    EventKind.SLASH -> {
                        val notes = chordAt(tick)?.let { chordNotes(it) }
                        if (notes == null) click(buf, st, 0.5) // no chord yet: the rhythm is still heard
                        else {
                            tone(buf, st, du * 0.9, notes.first, 0.10)
                            notes.second.forEachIndexed { k, n -> tone(buf, st + k * 0.012, du * 0.9, n, 0.07) }
                        }
                    }
                    EventKind.REST -> {}
                }
                tick += e.ticks
            }
            chords.lastOrNull()?.let { currentChord = it.text }
            t0 += measureSec
        }

        val end = min(buf.size, ((t0 + 0.5) * sr).toInt())
        val pcm = ShortArray(end) { (tanh(buf[it] * 1.5) * 30_000).toInt().toShort() }
        return Rendered(pcm, sr, starts, cues)
    }
}
