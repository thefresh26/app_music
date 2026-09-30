package com.activos.pentagrama.symbols

/** SMuFL code points (Bravura font). Verified against the bundled bravura.otf. */
object G {
    fun str(cp: Int): String = buildString { appendCodePointCompat(cp) }

    const val G_CLEF = 0xE050
    const val G_CLEF_8VB = 0xE052
    const val C_CLEF = 0xE05C
    const val F_CLEF = 0xE062
    const val PERC_CLEF = 0xE069

    const val HEAD_DOUBLE_WHOLE = 0xE0A0
    const val HEAD_WHOLE = 0xE0A2
    const val HEAD_HALF = 0xE0A3
    const val HEAD_BLACK = 0xE0A4
    const val HEAD_X = 0xE0A9
    const val HEAD_CIRCLE_X = 0xE0B3
    const val HEAD_TRIANGLE = 0xE0BE
    const val HEAD_DIAMOND = 0xE0DD
    const val SLASH_WHOLE = 0xE102
    const val SLASH_HALF = 0xE103
    const val SLASH = 0xE101

    const val FLAG_8_UP = 0xE240
    const val FLAG_8_DOWN = 0xE241
    // up flags: E240 + 2*(n-1), down flags: E241 + 2*(n-1)

    const val REST_DOUBLE_WHOLE = 0xE4E2
    const val REST_WHOLE = 0xE4E3
    const val REST_HALF = 0xE4E4
    const val REST_QUARTER = 0xE4E5
    const val REST_8 = 0xE4E6
    const val REST_16 = 0xE4E7
    const val REST_32 = 0xE4E8
    const val REST_64 = 0xE4E9

    const val FLAT = 0xE260
    const val NATURAL = 0xE261
    const val SHARP = 0xE262
    const val DOUBLE_SHARP = 0xE263
    const val DOUBLE_FLAT = 0xE264

    const val TIME_0 = 0xE080
    const val TIME_COMMON = 0xE08A
    const val TIME_CUT = 0xE08B
    const val DOT = 0xE1E7

    const val SEGNO = 0xE047
    const val CODA = 0xE048
    const val DAL_SEGNO = 0xE045
    const val DA_CAPO = 0xE046
    const val REPEAT_1_BAR = 0xE500
    const val REPEAT_2_BARS = 0xE501
    const val REPEAT_DOTS = 0xE043
    const val TUPLET_3 = 0xE883
    const val MET_QUARTER = 0xECA5

    fun accidental(alter: Int): Int = when (alter) {
        -2 -> DOUBLE_FLAT; -1 -> FLAT; 0 -> NATURAL; 1 -> SHARP; else -> DOUBLE_SHARP
    }
}

/** Kotlin-common replacement for StringBuilder.appendCodePoint. */
fun StringBuilder.appendCodePointCompat(cp: Int): StringBuilder {
    if (cp < 0x10000) {
        append(cp.toChar())
    } else {
        val v = cp - 0x10000
        append((0xD800 + (v shr 10)).toChar())
        append((0xDC00 + (v and 0x3FF)).toChar())
    }
    return this
}
