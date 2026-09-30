package com.activos.pentagrama.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Radix-2 FFT with precomputed tables. */
class Fft(val n: Int) {
    init { require(n > 0 && (n and (n - 1)) == 0) { "El tamaño de la FFT debe ser potencia de 2" } }

    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { -sin(2 * PI * it / n).toFloat() }
    private val rev = IntArray(n).also { r ->
        val bits = BitUtil.trailingZeros(n)
        for (i in 0 until n) {
            var x = i; var y = 0
            repeat(bits) { y = (y shl 1) or (x and 1); x = x shr 1 }
            r[i] = y
        }
    }
    val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() } // Hann

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]; val wi = sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr; im[j + half] = im[j] - xi
                    re[j] += xr; im[j] += xi
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }

    /** Magnitude spectrum (n/2 bins) of a windowed frame starting at [offset]. */
    fun magnitudes(signal: FloatArray, offset: Int, re: FloatArray, im: FloatArray, out: FloatArray) {
        for (i in 0 until n) {
            val idx = offset + i
            re[i] = if (idx in signal.indices) signal[idx] * window[i] else 0f
            im[i] = 0f
        }
        transform(re, im)
        for (i in 0 until n / 2) out[i] = sqrt(re[i] * re[i] + im[i] * im[i])
    }
}

private object BitUtil {
    fun trailingZeros(v: Int): Int {
        var c = 0; var x = v
        while (x and 1 == 0 && c < 32) { x = x shr 1; c++ }
        return c
    }
}
