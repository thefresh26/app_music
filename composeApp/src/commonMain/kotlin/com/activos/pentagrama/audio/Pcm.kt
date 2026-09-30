package com.activos.pentagrama.audio

/** Mono PCM audio in the range [-1, 1]. */
class PcmAudio(val samples: FloatArray, val sampleRate: Int, val name: String) {
    val durationSec: Double get() = samples.size.toDouble() / sampleRate
}

/**
 * Streams interleaved 16-bit PCM from any decoder, mixes it down to mono and
 * decimates it by an integer factor (box filter) so long songs stay small in memory.
 * Decoders (Android MediaCodec, desktop JLayer) push blocks as they decode.
 */
class MonoDownsampler(private val targetMinRate: Int = 16_000, private val maxSeconds: Int = MAX_SECONDS) {
    companion object {
        /** Longest audio kept in memory (protects low-memory phones from huge files). */
        const val MAX_SECONDS = 15 * 60
    }

    /** True when the input was longer than [maxSeconds] and got truncated. */
    var truncated = false
        private set

    private var srcRate = 0
    private var factor = 1
    private var acc = 0f
    private var accCount = 0
    private var out = FloatArray(1 shl 16)
    private var size = 0

    val outputRate: Int get() = if (srcRate == 0) targetMinRate else srcRate / factor

    fun configure(sampleRate: Int) {
        if (srcRate == sampleRate) return
        srcRate = sampleRate
        factor = (sampleRate / targetMinRate).coerceAtLeast(1)
    }

    fun pushShorts(data: ShortArray, length: Int, channels: Int) {
        val ch = channels.coerceAtLeast(1)
        var i = 0
        while (i + ch <= length) {
            var sum = 0f
            for (c in 0 until ch) sum += data[i + c] / 32768f
            push(sum / ch)
            i += ch
        }
    }

    fun pushFloats(data: FloatArray, length: Int, channels: Int) {
        val ch = channels.coerceAtLeast(1)
        var i = 0
        while (i + ch <= length) {
            var sum = 0f
            for (c in 0 until ch) sum += data[i + c]
            push(sum / ch)
            i += ch
        }
    }

    private fun push(v: Float) {
        if (size >= maxSeconds * outputRate) { truncated = true; return }
        acc += v; accCount++
        if (accCount >= factor) {
            if (size == out.size) out = out.copyOf(out.size * 2)
            out[size++] = acc / accCount
            acc = 0f; accCount = 0
        }
    }

    fun result(name: String): PcmAudio = PcmAudio(out.copyOf(size), outputRate, name)
}
