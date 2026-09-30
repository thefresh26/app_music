package com.activos.pentagrama.model

/** Ticks per quarter note. 96 allows 64th notes, dots and triplets as whole numbers. */
const val TICKS_PER_QUARTER = 96

enum class NoteValue(val ticks: Int, val es: String, val xmlType: String) {
    DOUBLE_WHOLE(TICKS_PER_QUARTER * 8, "Cuadrada", "breve"),
    WHOLE(TICKS_PER_QUARTER * 4, "Redonda", "whole"),
    HALF(TICKS_PER_QUARTER * 2, "Blanca", "half"),
    QUARTER(TICKS_PER_QUARTER, "Negra", "quarter"),
    EIGHTH(TICKS_PER_QUARTER / 2, "Corchea", "eighth"),
    SIXTEENTH(TICKS_PER_QUARTER / 4, "Semicorchea", "16th"),
    THIRTY_SECOND(TICKS_PER_QUARTER / 8, "Fusa", "32nd"),
    SIXTY_FOURTH(TICKS_PER_QUARTER / 16, "Semifusa", "64th");

    val flags: Int
        get() = when (this) {
            EIGHTH -> 1; SIXTEENTH -> 2; THIRTY_SECOND -> 3; SIXTY_FOURTH -> 4; else -> 0
        }
    val hasStem: Boolean get() = this != WHOLE && this != DOUBLE_WHOLE
}

enum class EventKind { NOTE, REST, SLASH }

enum class NoteHead { NORMAL, X, DIAMOND, TRIANGLE, CIRCLE_X }

enum class Clef(val es: String, val bottomLineDiatonic: Int) {
    TREBLE("Clave de Sol", 4 * 7 + 2),        // E4
    BASS("Clave de Fa", 2 * 7 + 4),           // G2
    ALTO("Clave de Do (3ª)", 3 * 7 + 3),      // F3
    TENOR("Clave de Do (4ª)", 3 * 7 + 1),     // D3
    TREBLE_8VB("Clave de Sol 8vb", 3 * 7 + 2), // E3
    PERCUSSION("Clave de percusión", 4 * 7 + 2);
}

enum class TimeSymbol { NUMERIC, COMMON, CUT }

enum class StartBar { NORMAL, REPEAT_START, DOUBLE }
enum class EndBar { SINGLE, DOUBLE, FINAL, REPEAT_END, NONE }

private val STEP_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
val STEP_NAMES = arrayOf("C", "D", "E", "F", "G", "A", "B")
val STEP_NAMES_ES = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")

/** A written pitch: diatonic step (0=C .. 6=B), octave (4 = middle C octave) and alteration in semitones. */
data class Pitch(val step: Int, val octave: Int, val alter: Int = 0) {
    val diatonic: Int get() = octave * 7 + step
    val midi: Int get() = (octave + 1) * 12 + STEP_SEMITONES[step] + alter

    fun name(): String = STEP_NAMES[step] + when (alter) {
        -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> ""
    } + octave

    companion object {
        fun fromDiatonic(d: Int, alter: Int = 0): Pitch {
            val oct = d.floorDiv(7)
            return Pitch(d - oct * 7, oct, alter)
        }

        private val SHARP_SPELL = arrayOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 3 to 0, 3 to 1, 4 to 0, 4 to 1, 5 to 0, 5 to 1, 6 to 0)
        private val FLAT_SPELL = arrayOf(0 to 0, 1 to -1, 1 to 0, 2 to -1, 2 to 0, 3 to 0, 4 to -1, 4 to 0, 5 to -1, 5 to 0, 6 to -1, 6 to 0)

        /** Spell a MIDI number using sharps (keyFifths >= 0) or flats. */
        fun fromMidi(midi: Int, keyFifths: Int = 0): Pitch {
            val pc = midi.mod(12)
            val (step, alter) = if (keyFifths >= 0) SHARP_SPELL[pc] else FLAT_SPELL[pc]
            val octave = midi.floorDiv(12) - 1
            return Pitch(step, octave, alter)
        }
    }
}

/** Alteration that the key signature applies to a given step. */
fun keyAlterFor(step: Int, keyFifths: Int): Int {
    // Order of sharps: F C G D A E B  -> steps 3 0 4 1 5 2 6
    val sharpOrder = intArrayOf(3, 0, 4, 1, 5, 2, 6)
    val flatOrder = intArrayOf(6, 2, 5, 1, 4, 0, 3)
    return if (keyFifths > 0) {
        if (sharpOrder.take(keyFifths).contains(step)) 1 else 0
    } else if (keyFifths < 0) {
        if (flatOrder.take(-keyFifths).contains(step)) -1 else 0
    } else 0
}

data class Event(
    val kind: EventKind,
    val value: NoteValue,
    val pitches: List<Pitch> = emptyList(),
    val dots: Int = 0,
    val triplet: Boolean = false,
    val tieToNext: Boolean = false,
    val head: NoteHead = NoteHead.NORMAL,
    /** Ids of attached symbols (articulations, dynamics, ornaments, fermatas...). */
    val marks: List<String> = emptyList(),
    val lyric: String? = null,
) {
    val ticks: Int
        get() {
            var t = value.ticks
            var add = value.ticks / 2
            repeat(dots) { t += add; add /= 2 }
            if (triplet) t = t * 2 / 3
            return t
        }
}

data class ChordMark(val tick: Int, val text: String)

/** A symbol placed freely inside a measure (any SMuFL glyph). */
data class FreeSymbol(val codepoint: Int, val xFrac: Float, val staffStep: Int)

data class Measure(
    val events: List<Event> = emptyList(),
    val chords: List<ChordMark> = emptyList(),
    val startBar: StartBar = StartBar.NORMAL,
    val endBar: EndBar = EndBar.SINGLE,
    /** Navigation / measure level marks (segno, coda, D.S., fine ...). Symbol ids. */
    val marks: List<String> = emptyList(),
    /** Section label shown highlighted above the measure (Intro, Estrofa, Coro...). */
    val section: String? = null,
    /** Volta text ("1.", "2.") */
    val ending: String? = null,
    /** Free text above the measure (e.g. "x2", "BASS"). */
    val text: String? = null,
    /** Measure-repeat sign (%). */
    val repeatMeasure: Boolean = false,
    val free: List<FreeSymbol> = emptyList(),
) {
    val usedTicks: Int get() = events.sumOf { it.ticks }
}

data class Score(
    val title: String = "Sin título",
    val composer: String = "",
    val subtitle: String = "",
    val clef: Clef = Clef.TREBLE,
    val keyFifths: Int = 0,
    val timeNum: Int = 4,
    val timeDen: Int = 4,
    val timeSymbol: TimeSymbol = TimeSymbol.NUMERIC,
    val tempoBpm: Int = 100,
    /** 0 = automatic, otherwise fixed measures per line (like handwritten charts). */
    val measuresPerLine: Int = 4,
    val measures: List<Measure> = List(8) { Measure() },
) {
    val measureTicks: Int get() = timeNum * TICKS_PER_QUARTER * 4 / timeDen
}

fun keyName(fifths: Int, minor: Boolean = false): String {
    val major = arrayOf("Dob", "Solb", "Reb", "Lab", "Mib", "Sib", "Fa", "Do", "Sol", "Re", "La", "Mi", "Si", "Fa#", "Do#")
    val minorN = arrayOf("Lab m", "Mib m", "Sib m", "Fa m", "Do m", "Sol m", "Re m", "La m", "Mi m", "Si m", "Fa# m", "Do# m", "Sol# m", "Re# m", "La# m")
    val i = (fifths + 7).coerceIn(0, 14)
    return if (minor) minorN[i] else major[i]
}

/** Split [ticks] into a list of (value, dots) that sum exactly to it (greedy). */
fun decomposeTicks(ticks: Int): List<Pair<NoteValue, Int>> {
    val out = mutableListOf<Pair<NoteValue, Int>>()
    var rem = ticks
    val options = NoteValue.entries.filter { it != NoteValue.DOUBLE_WHOLE }
        .flatMap { v -> listOf(v to 1, v to 0) }
        .map { (v, d) -> Triple(v, d, if (d == 1) v.ticks * 3 / 2 else v.ticks) }
        .filter { it.third > 0 && (it.second == 0 || v6ok(it.first)) }
        .sortedByDescending { it.third }
    while (rem > 0) {
        val o = options.firstOrNull { it.third <= rem } ?: break
        out += o.first to o.second
        rem -= o.third
    }
    return out
}

private fun v6ok(v: NoteValue) = v.ticks % 2 == 0

