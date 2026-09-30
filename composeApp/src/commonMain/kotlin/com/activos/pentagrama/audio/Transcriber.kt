package com.activos.pentagrama.audio

import com.activos.pentagrama.model.ChordMark
import com.activos.pentagrama.model.Clef
import com.activos.pentagrama.model.EndBar
import com.activos.pentagrama.model.Event
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.Measure
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.model.Pitch
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.TICKS_PER_QUARTER
import com.activos.pentagrama.model.keyName
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class TranscribeMode(val es: String) {
    MELODY_AND_CHORDS("Melodía + cifrado"),
    MELODY("Solo melodía"),
    CHORDS("Solo cifrado (acordes con barras rítmicas)"),
}

data class TranscribeOptions(
    val mode: TranscribeMode = TranscribeMode.MELODY_AND_CHORDS,
    /** 0 = detect automatically. */
    val bpm: Int = 0,
    val timeNum: Int = 4,
    val timeDen: Int = 4,
    /** 0 = whole file. */
    val maxSeconds: Int = 0,
    val minNoteMs: Int = 70,
)

data class TranscriptionResult(
    val score: Score,
    val bpm: Int,
    val key: String,
    val notes: Int,
    val chords: Int,
)

/** A detected note in seconds / MIDI. */
data class RawNote(val start: Double, val end: Double, val midi: Int)

/**
 * On-device transcription: monophonic pitch tracking (YIN) + tempo (spectral flux autocorrelation)
 * + key (Krumhansl-Schmuckler) + chords (chroma templates) + rhythmic quantization to a 16th grid.
 * Works best with a clear lead melody (voice, solo instrument). Chords are estimated from the full mix.
 */
object Transcriber {
    private const val SR = 16_000
    private const val YIN_SR = 8_000
    private const val YIN_HOP = 160          // 20 ms at 8 kHz
    private const val YIN_W = 256
    private const val FLUX_N = 1024
    private const val FLUX_HOP = 320         // 20 ms at 16 kHz (same frame rate as YIN)
    private const val CHROMA_N = 4096
    private const val CHROMA_HOP = 1024
    private const val FRAME_SEC = 0.02

    fun transcribe(
        audio: PcmAudio,
        opts: TranscribeOptions = TranscribeOptions(),
        progress: (Float, String) -> Unit = { _, _ -> },
    ): TranscriptionResult {
        progress(0.02f, "Preparando audio…")
        var x = resample(audio.samples, audio.sampleRate, SR)
        if (opts.maxSeconds > 0) x = x.copyOf(min(x.size, opts.maxSeconds * SR))
        normalize(x)
        require(x.size > SR) { "El audio es demasiado corto" }

        progress(0.08f, "Analizando ritmo…")
        val flux = spectralFlux(x)
        val detectedBpm = estimateTempo(flux)
        val bpm = if (opts.bpm > 0) opts.bpm.toDouble() else detectedBpm
        val beatFrames = 60.0 / bpm / FRAME_SEC
        val phase = beatPhase(flux, beatFrames)

        progress(0.2f, "Calculando armonía…")
        val chroma = chromagram(x)

        var notes = emptyList<RawNote>()
        if (opts.mode != TranscribeMode.CHORDS) {
            progress(0.35f, "Detectando la melodía (tono)…")
            val y = resample(x, SR, YIN_SR)
            val (pitch, rms) = yinTrack(y) { p -> progress(0.35f + 0.4f * p, "Detectando la melodía (tono)…") }
            notes = segmentNotes(pitch, rms, flux, opts.minNoteMs)
        }

        progress(0.8f, "Detectando tonalidad…")
        val (keyFifths, minor) = detectKey(notes, chroma)

        progress(0.85f, "Escribiendo la partitura…")
        val beatSec = 60.0 / bpm
        var t0 = phase * FRAME_SEC
        if (notes.size >= 4) {
            // Fine-tune the grid phase so detected note onsets fall on 16th positions (circular mean).
            val grid = beatSec / 4
            var sx = 0.0; var sy = 0.0
            for (n in notes) {
                val a = 2 * PI * ((n.start - t0) / grid)
                val w = min(1.0, n.end - n.start)
                sx += cos(a) * w; sy += sin(a) * w
            }
            t0 += atan2(sy, sx) / (2 * PI) * grid
            // Choose which 16th of the beat is the "on-beat": the one where most notes start.
            val counts = DoubleArray(4)
            for (n in notes) {
                val q = ((n.start - t0) / grid).roundToInt()
                counts[q.mod(4)] += min(1.0, n.end - n.start)
            }
            val r = counts.indices.maxByOrNull { counts[it] } ?: 0
            t0 += r * grid
        }
        val firstStart = notes.firstOrNull()?.start ?: t0
        while (t0 > firstStart + 0.05) t0 -= beatSec
        while (t0 + beatSec < firstStart - 0.05) t0 += beatSec

        val score = buildScore(audio.name, notes, chroma, x.size.toDouble() / SR, t0, bpm, keyFifths, minor, opts)
        progress(1f, "Listo")
        return TranscriptionResult(
            score, bpm.roundToInt(), keyName(keyFifths, minor), notes.size,
            score.measures.sumOf { it.chords.size },
        )
    }

    // ---------------------------------------------------------------- signal helpers

    fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return src.copyOf()
        val ratio = from.toDouble() / to
        // Low-pass by moving average when downsampling to reduce aliasing.
        val filtered = if (ratio > 1.0) {
            val k = ratio.roundToInt().coerceAtLeast(1)
            if (k <= 1) src else {
                val out = FloatArray(src.size)
                var acc = 0f
                for (i in src.indices) {
                    acc += src[i]
                    if (i >= k) acc -= src[i - k]
                    out[i] = acc / min(i + 1, k)
                }
                out
            }
        } else src
        val n = (src.size / ratio).toInt()
        return FloatArray(n) { i ->
            val pos = i * ratio
            val a = pos.toInt()
            val f = (pos - a).toFloat()
            val s0 = filtered[min(a, filtered.size - 1)]
            val s1 = filtered[min(a + 1, filtered.size - 1)]
            s0 + (s1 - s0) * f
        }
    }

    private fun normalize(x: FloatArray) {
        var peak = 0f
        for (v in x) peak = max(peak, abs(v))
        if (peak > 1e-6f) for (i in x.indices) x[i] /= peak
    }

    private fun spectralFlux(x: FloatArray): FloatArray {
        val fft = Fft(FLUX_N)
        val frames = (x.size - FLUX_N) / FLUX_HOP + 1
        val re = FloatArray(FLUX_N); val im = FloatArray(FLUX_N)
        var prev = FloatArray(FLUX_N / 2); var cur = FloatArray(FLUX_N / 2)
        val out = FloatArray(max(frames, 1))
        for (f in 0 until frames) {
            fft.magnitudes(x, f * FLUX_HOP, re, im, cur)
            var s = 0f
            for (k in 1 until FLUX_N / 2) {
                val v = ln(1f + 100f * cur[k])
                val d = v - prev[k]
                if (d > 0) s += d
                prev[k] = v
            }
            out[f] = if (f == 0) 0f else s
        }
        // Remove slow trend (moving average over ~0.5 s) and rectify.
        val w = 25
        val res = FloatArray(out.size)
        var acc = 0f
        for (i in out.indices) {
            acc += out[i]
            if (i >= w) acc -= out[i - w]
            val mean = acc / min(i + 1, w)
            res[i] = max(0f, out[i] - mean)
        }
        return res
    }

    /** Returns BPM between 60 and 180 using autocorrelation of the onset envelope with a tempo prior. */
    fun estimateTempo(onset: FloatArray): Double {
        val minLag = (60.0 / 190 / FRAME_SEC).toInt()
        val maxLag = (60.0 / 55 / FRAME_SEC).toInt() + 1
        if (onset.size < maxLag * 4) return 100.0
        val ac = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag + 1) {
            var s = 0.0
            for (i in 0 until onset.size - lag) s += onset[i] * onset[i + lag]
            ac[lag] = s / (onset.size - lag)
        }
        var best = minLag; var bestScore = -1.0
        for (lag in minLag..maxLag) {
            val bpm = 60.0 / (lag * FRAME_SEC)
            val prior = exp(-0.5 * (log2(bpm / 110.0) / 0.8).pow(2))
            // Reward lags whose double also correlates (metrical consistency).
            val harm = if (lag * 2 <= maxLag + 1) 0.5 * ac[lag * 2] else 0.0
            val score = (ac[lag] + harm) * prior
            if (score > bestScore) { bestScore = score; best = lag }
        }
        // Parabolic refinement.
        val a = ac[best - 1]; val b = ac[best]; val c = ac[best + 1]
        val denom = a - 2 * b + c
        val offset = if (abs(denom) > 1e-12) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
        var bpm = 60.0 / ((best + offset) * FRAME_SEC)
        while (bpm < 65) bpm *= 2
        while (bpm > 175) bpm /= 2
        return bpm
    }

    private fun beatPhase(onset: FloatArray, period: Double): Double {
        var bestPhase = 0.0; var best = -1.0
        val steps = max(1, period.toInt())
        for (p in 0 until steps) {
            var s = 0.0
            var t = p.toDouble()
            while (t < onset.size) { s += onset[t.roundToInt().coerceAtMost(onset.size - 1)]; t += period }
            if (s > best) { best = s; bestPhase = p.toDouble() }
        }
        return bestPhase
    }

    private fun chromagram(x: FloatArray): Array<FloatArray> {
        val fft = Fft(CHROMA_N)
        val frames = max(1, (x.size - CHROMA_N) / CHROMA_HOP + 1)
        val re = FloatArray(CHROMA_N); val im = FloatArray(CHROMA_N); val mag = FloatArray(CHROMA_N / 2)
        val binPc = IntArray(CHROMA_N / 2) { k ->
            val f = k.toDouble() * SR / CHROMA_N
            if (f < 55 || f > 2000) -1 else (((12 * log2(f / 440.0) + 69).roundToInt()) % 12 + 12) % 12
        }
        return Array(frames) { fr ->
            fft.magnitudes(x, fr * CHROMA_HOP, re, im, mag)
            val c = FloatArray(12)
            for (k in mag.indices) { val pc = binPc[k]; if (pc >= 0) c[pc] += sqrt(mag[k]) }
            c
        }
    }

    // ---------------------------------------------------------------- pitch tracking

    /** YIN pitch tracker. Returns (midi per frame or NaN, rms per frame), frames every 20 ms. */
    fun yinTrack(y: FloatArray, progress: (Float) -> Unit = {}): Pair<FloatArray, FloatArray> {
        val tauMin = YIN_SR / 1100
        val tauMax = YIN_SR / 62
        val frames = max(0, (y.size - YIN_W - tauMax) / YIN_HOP)
        val midi = FloatArray(frames) { Float.NaN }
        val rms = FloatArray(frames)
        for (f in 0 until frames) {
            val o = f * YIN_HOP
            var e = 0f
            for (j in 0 until YIN_W) e += y[o + j] * y[o + j]
            rms[f] = sqrt(e / YIN_W)
        }
        var maxRms = 0f
        for (v in rms) maxRms = max(maxRms, v)
        val gate = maxRms * 0.04f
        val d = FloatArray(tauMax + 2)
        for (f in 0 until frames) {
            if (f % 500 == 0) progress(f.toFloat() / frames)
            if (rms[f] < gate) continue
            val o = f * YIN_HOP
            d[0] = 1f
            var running = 0f
            for (tau in 1..tauMax + 1) {
                var s = 0f
                for (j in 0 until YIN_W) { val diff = y[o + j] - y[o + j + tau]; s += diff * diff }
                running += s
                d[tau] = if (running > 0f) s * tau / running else 1f
            }
            var tau = -1
            var t = tauMin
            while (t <= tauMax) {
                if (d[t] < 0.15f) {
                    while (t + 1 <= tauMax && d[t + 1] < d[t]) t++
                    tau = t; break
                }
                t++
            }
            if (tau < 0) {
                var bi = tauMin
                for (k in tauMin..tauMax) if (d[k] < d[bi]) bi = k
                if (d[bi] < 0.3f) tau = bi
            }
            if (tau < 0) continue
            val a = d[tau - 1]; val b = d[tau]; val c = d[tau + 1]
            val den = a - 2 * b + c
            val shift = if (abs(den) > 1e-9f) (0.5f * (a - c) / den).coerceIn(-1f, 1f) else 0f
            val freq = YIN_SR / (tau + shift)
            val m = (69 + 12 * log2(freq / 440.0)).toFloat()
            if (m in 36f..96f) midi[f] = m
        }
        // Median filter (5 frames) on voiced frames to remove octave glitches.
        val out = midi.copyOf()
        val win = FloatArray(5)
        for (f in midi.indices) {
            if (midi[f].isNaN()) continue
            var n = 0
            for (k in f - 2..f + 2) if (k in midi.indices && !midi[k].isNaN()) win[n++] = midi[k]
            win.sort(0, n)
            out[f] = win[n / 2]
        }
        progress(1f)
        return out to rms
    }

    fun segmentNotes(midi: FloatArray, rms: FloatArray, onset: FloatArray, minNoteMs: Int): List<RawNote> {
        val notes = mutableListOf<RawNote>()
        val minFrames = max(2, (minNoteMs / 1000.0 / FRAME_SEC).roundToInt())
        var onsetMax = 0f
        for (v in onset) onsetMax = max(onsetMax, v)
        val onsetThr = onsetMax * 0.3f

        var start = -1
        val vals = mutableListOf<Float>()
        var unvoiced = 0
        var deviate = 0

        fun close(endFrame: Int) {
            if (start >= 0 && endFrame - start >= minFrames && vals.isNotEmpty()) {
                val sorted = vals.sorted()
                val m = sorted[sorted.size / 2].roundToInt()
                notes += RawNote(start * FRAME_SEC, endFrame * FRAME_SEC, m)
            }
            start = -1; vals.clear(); deviate = 0
        }

        for (f in midi.indices) {
            val m = midi[f]
            if (m.isNaN()) {
                unvoiced++
                if (unvoiced >= 2) close(f - unvoiced + 1)
                continue
            }
            unvoiced = 0
            if (start < 0) { start = f; vals += m; continue }
            val ref = vals.takeLast(6).sorted().let { it[it.size / 2] }
            val isOnset = f in onset.indices && onset[f] > onsetThr &&
                (f == 0 || onset[f] >= onset[f - 1]) && (f + 1 >= onset.size || onset[f] >= onset[f + 1])
            val diff = abs(m - ref)
            if (abs(diff - 12f) < 0.75f || abs(diff - 24f) < 0.75f) {
                // Octave error of the pitch tracker inside a sustained note: ignore the frame.
                deviate = 0
                continue
            }
            if (diff > 0.75f) {
                deviate++
                if (deviate >= 3) {
                    val newStart = f - 2
                    val keep = vals.takeLast(2)
                    repeat(2) { if (vals.isNotEmpty()) vals.removeAt(vals.lastIndex) }
                    close(newStart)
                    start = newStart; vals += keep; vals += m
                }
                else vals += m
            } else {
                deviate = 0
                // Repeated note: only split when the energy really rises (a new attack), not on codec/vibrato noise.
                if (reattackAt(rms, f) && f - start >= minFrames) { close(f); start = f }
                vals += m
            }
        }
        close(midi.size)
        return notes
    }

    /** True when the energy around frame [f] rises clearly above the level just before it (a new attack). */
    private fun reattackAt(rms: FloatArray, f: Int): Boolean {
        if (f !in rms.indices || f < 1) return false
        if (rms[f] <= rms[f - 1]) return false
        var before = Float.MAX_VALUE
        for (k in f - 4..f - 1) if (k in rms.indices) before = min(before, rms[k])
        return rms[f] >= 1.5f * before
    }

    // ---------------------------------------------------------------- key & chords

    private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
    private val MAJOR_FIFTHS = intArrayOf(0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5)

    /** Returns (fifths of the key signature, isMinor). */
    fun detectKey(notes: List<RawNote>, chroma: Array<FloatArray>): Pair<Int, Boolean> {
        val h = DoubleArray(12)
        var chromaTotal = 0.0
        for (c in chroma) for (k in 0 until 12) { h[k] += c[k].toDouble(); chromaTotal += c[k] }
        if (chromaTotal > 0) for (k in 0 until 12) h[k] /= chromaTotal
        var noteTotal = 0.0
        val hn = DoubleArray(12)
        for (n in notes) { val w = n.end - n.start; hn[((n.midi % 12) + 12) % 12] += w; noteTotal += w }
        if (noteTotal > 0) for (k in 0 until 12) h[k] += hn[k] / noteTotal
        var best = -2.0; var bestTonic = 0; var bestMinor = false
        for (tonic in 0 until 12) for (minor in listOf(false, true)) {
            val prof = if (minor) MINOR_PROFILE else MAJOR_PROFILE
            val r = correlation(h) { k -> prof[((k - tonic) % 12 + 12) % 12] }
            if (r > best) { best = r; bestTonic = tonic; bestMinor = minor }
        }
        val majorTonic = if (bestMinor) (bestTonic + 3) % 12 else bestTonic
        return MAJOR_FIFTHS[majorTonic] to bestMinor
    }

    private fun correlation(a: DoubleArray, b: (Int) -> Double): Double {
        val ma = a.average()
        val bv = DoubleArray(12) { b(it) }
        val mb = bv.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (k in 0 until 12) {
            num += (a[k] - ma) * (bv[k] - mb); da += (a[k] - ma).pow(2); db += (bv[k] - mb).pow(2)
        }
        return if (da == 0.0 || db == 0.0) 0.0 else num / sqrt(da * db)
    }

    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    private data class ChordGuess(val root: Int, val quality: Int, val score: Double) // 0 maj, 1 min, 2 dom7

    private val TEMPLATES = listOf(intArrayOf(0, 4, 7), intArrayOf(0, 3, 7), intArrayOf(0, 4, 7, 10))

    private fun guessChord(c: DoubleArray, keyFifths: Int): ChordGuess? {
        val norm = sqrt(c.sumOf { it * it })
        if (norm <= 1e-9) return null
        val keyTonic = MAJOR_FIFTHS.indexOf(keyFifths).coerceAtLeast(0)
        val diatonicMaj = setOf(0, 5, 7).map { (it + keyTonic) % 12 }
        val diatonicMin = setOf(2, 4, 9).map { (it + keyTonic) % 12 }
        var best: ChordGuess? = null
        for (root in 0 until 12) for ((q, t) in TEMPLATES.withIndex()) {
            var dot = 0.0
            for (iv in t) dot += c[(root + iv) % 12]
            var score = dot / (norm * sqrt(t.size.toDouble()))
            if (q == 2) score *= 0.96
            if ((q == 0 || q == 2) && root in diatonicMaj) score *= 1.06
            if (q == 1 && root in diatonicMin) score *= 1.06
            if (best == null || score > best.score) best = ChordGuess(root, q, score)
        }
        return best
    }

    private fun chordName(g: ChordGuess, keyFifths: Int): String {
        val names = if (keyFifths < 0) FLAT_NAMES else SHARP_NAMES
        return names[g.root] + when (g.quality) { 1 -> "m"; 2 -> "7"; else -> "" }
    }

    private fun chromaSum(chroma: Array<FloatArray>, fromSec: Double, toSec: Double): Pair<DoubleArray, Double> {
        val a = (fromSec * SR / CHROMA_HOP).toInt().coerceAtLeast(0)
        val b = (toSec * SR / CHROMA_HOP).toInt().coerceAtMost(chroma.size)
        val out = DoubleArray(12)
        var energy = 0.0
        for (i in a until b) for (k in 0 until 12) { out[k] += chroma[i][k]; energy += chroma[i][k] }
        return out to (if (b > a) energy / (b - a) else 0.0)
    }

    // ---------------------------------------------------------------- score building

    private data class Seg(val start: Int, val len: Int, val midi: Int?) // grid = 16th notes

    fun unitsToValue(u: Int): Pair<NoteValue, Int> = when (u) {
        16 -> NoteValue.WHOLE to 0; 12 -> NoteValue.HALF to 1; 8 -> NoteValue.HALF to 0
        6 -> NoteValue.QUARTER to 1; 4 -> NoteValue.QUARTER to 0; 3 -> NoteValue.EIGHTH to 1
        2 -> NoteValue.EIGHTH to 0; else -> NoteValue.SIXTEENTH to 0
    }

    /** Split a duration at a position (both in 16ths, relative to the measure) into notatable pieces. */
    fun splitUnits(pos: Int, len: Int): List<Int> {
        val out = mutableListOf<Int>()
        var p = pos; var rem = len
        while (rem > 0) {
            val u = listOf(16, 12, 8, 6, 4, 3, 2, 1).first { u ->
                u <= rem && when (u) {
                    16 -> p % 16 == 0
                    12 -> p % 4 == 0
                    8 -> p % 4 == 0
                    6 -> p % 2 == 0
                    4 -> p % 2 == 0
                    3 -> true
                    2 -> true
                    else -> true
                }
            }
            out += u; p += u; rem -= u
        }
        return out
    }

    private fun buildScore(
        fileName: String, notes: List<RawNote>, chroma: Array<FloatArray>, durationSec: Double,
        t0: Double, bpm: Double, keyFifths: Int, minor: Boolean, opts: TranscribeOptions,
    ): Score {
        val grid = 60.0 / bpm / 4.0 // 16th note in seconds (BPM counted in quarter notes)
        val measureUnits = opts.timeNum * 16 / opts.timeDen
        val ticksPerUnit = TICKS_PER_QUARTER / 4

        // Quantize notes to the grid.
        val q = mutableListOf<Seg>()
        for (n in notes.sortedBy { it.start }) {
            val s = ((n.start - t0) / grid).roundToInt().coerceAtLeast(0)
            var e = ((n.end - t0) / grid).roundToInt()
            if (e <= s) e = s + 1
            if (q.isNotEmpty()) {
                val last = q.last()
                if (s < last.start + last.len) {
                    val newLen = s - last.start
                    if (newLen <= 0) { if (e - s > last.len) q[q.lastIndex] = Seg(s, e - s, n.midi); continue }
                    q[q.lastIndex] = last.copy(len = newLen)
                } else if (s - (last.start + last.len) == 1) {
                    // Absorb tiny 16th gaps into the previous note (legato singing).
                    q[q.lastIndex] = last.copy(len = s - last.start)
                }
            }
            q += Seg(s, e - s, n.midi)
        }

        val totalUnits = max(
            ((durationSec - t0) / grid).toInt(),
            q.lastOrNull()?.let { it.start + it.len } ?: 0,
        )
        val measureCount = max(1, (totalUnits + measureUnits - 1) / measureUnits)

        val median = notes.map { it.midi }.sorted().let { if (it.isEmpty()) 67 else it[it.size / 2] }
        val clef = if (opts.mode != TranscribeMode.CHORDS && median < 55) Clef.BASS else Clef.TREBLE

        val measures = MutableList(measureCount) { Measure() }

        // ---- melody
        if (opts.mode != TranscribeMode.CHORDS) {
            for (mi in 0 until measureCount) {
                val mStart = mi * measureUnits
                val mEnd = mStart + measureUnits
                val events = mutableListOf<Event>()
                var cursor = mStart
                fun addPieces(start: Int, len: Int, midi: Int?, tieOut: Boolean) {
                    val pieces = splitUnits(start - mStart, len)
                    pieces.forEachIndexed { k, u ->
                        val (v, dots) = unitsToValue(u)
                        events += if (midi == null) Event(EventKind.REST, v, dots = dots)
                        else Event(EventKind.NOTE, v, listOf(Pitch.fromMidi(midi, keyFifths)), dots = dots,
                            tieToNext = k < pieces.lastIndex || tieOut)
                    }
                }
                for (seg in q) {
                    val segEnd = seg.start + seg.len
                    if (segEnd <= mStart || seg.start >= mEnd) continue
                    val a = max(seg.start, mStart)
                    val b = min(segEnd, mEnd)
                    if (a > cursor) addPieces(cursor, a - cursor, null, false)
                    addPieces(a, b - a, seg.midi, segEnd > mEnd)
                    cursor = b
                }
                if (cursor < mEnd) {
                    if (cursor == mStart && measureUnits == 16) events += Event(EventKind.REST, NoteValue.WHOLE)
                    else addPieces(cursor, mEnd - cursor, null, false)
                }
                measures[mi] = measures[mi].copy(events = events)
            }
        }

        // ---- chords
        if (opts.mode != TranscribeMode.MELODY) {
            val slotsPerMeasure = if (measureUnits % 8 == 0 && measureUnits >= 16) 2 else 1
            val slotUnits = measureUnits / slotsPerMeasure
            var energyMax = 0.0
            val slotData = (0 until measureCount * slotsPerMeasure).map { si ->
                val from = t0 + si * slotUnits * grid
                val (c, e) = chromaSum(chroma, from, from + slotUnits * grid)
                energyMax = max(energyMax, e)
                c to e
            }
            var prevName: String? = null
            for (mi in 0 until measureCount) {
                val names = (0 until slotsPerMeasure).map { k ->
                    val (c, e) = slotData[mi * slotsPerMeasure + k]
                    if (e < energyMax * 0.08) null else guessChord(c, keyFifths)?.let { chordName(it, keyFifths) }
                }
                val chords = mutableListOf<ChordMark>()
                val first = names[0]
                val second = names.getOrNull(1)
                if (opts.mode == TranscribeMode.CHORDS) {
                    if (first != null) chords += ChordMark(0, first)
                    if (second != null && second != first) chords += ChordMark(slotUnits * ticksPerUnit, second)
                } else {
                    if (first != null && first != prevName) chords += ChordMark(0, first)
                    if (second != null && second != (first ?: prevName)) chords += ChordMark(slotUnits * ticksPerUnit, second)
                }
                prevName = second ?: first ?: prevName
                measures[mi] = measures[mi].copy(chords = chords)
            }
            if (opts.mode == TranscribeMode.CHORDS) {
                val beatValue = when (opts.timeDen) { 2 -> NoteValue.HALF; 8 -> NoteValue.EIGHTH; else -> NoteValue.QUARTER }
                for (mi in 0 until measureCount) {
                    val m = measures[mi]
                    val prev = measures.getOrNull(mi - 1)
                    val same = prev != null && m.chords.size == 1 && prev.chords.size <= 1 &&
                        m.chords.firstOrNull()?.text == (prev.chords.firstOrNull()?.text ?: lastChordText(measures, mi - 1))
                    measures[mi] = if (same) m.copy(repeatMeasure = true, chords = emptyList())
                    else m.copy(events = List(opts.timeNum) { Event(EventKind.SLASH, beatValue) })
                }
            }
        }

        measures[measures.lastIndex] = measures.last().copy(endBar = EndBar.FINAL)
        val title = fileName.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Transcripción" }
        return Score(
            title = title,
            composer = "",
            subtitle = "Transcripción automática · ♩ = ${bpm.roundToInt()} · ${keyName(keyFifths, minor)}",
            clef = clef,
            keyFifths = keyFifths,
            timeNum = opts.timeNum,
            timeDen = opts.timeDen,
            tempoBpm = bpm.roundToInt(),
            measuresPerLine = 4,
            measures = measures,
        )
    }

    private fun lastChordText(measures: List<Measure>, upTo: Int): String? {
        for (i in upTo downTo 0) measures[i].chords.lastOrNull()?.let { return it.text }
        return null
    }
}
