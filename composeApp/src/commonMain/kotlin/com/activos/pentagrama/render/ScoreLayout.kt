package com.activos.pentagrama.render

import com.activos.pentagrama.model.Event
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.EndBar
import com.activos.pentagrama.model.Measure
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.StartBar
import com.activos.pentagrama.model.TICKS_PER_QUARTER
import com.activos.pentagrama.model.TimeSymbol
import com.activos.pentagrama.model.keyAlterFor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** All sizes are expressed in "staff spaces" multiplied by [s] (pixels per staff space). */
object Dim {
    const val TITLE_HEIGHT = 10f
    const val ABOVE_STAFF = 9f
    const val STAFF = 4f
    const val BELOW_STAFF = 6f
    const val SYSTEM_HEIGHT = ABOVE_STAFF + STAFF + BELOW_STAFF
    const val CLEF_WIDTH = 3.4f
    const val KEY_ACC_WIDTH = 1.05f
    const val TIME_WIDTH = 2.6f
    const val MIN_MEASURE = 6f
    const val EMPTY_MEASURE = 10f
}

data class EventLayout(val index: Int, val x: Float, val width: Float, val startTick: Int)

data class MeasureLayout(
    val index: Int,
    val x: Float,
    val width: Float,
    val contentX: Float,
    val contentWidth: Float,
    val events: List<EventLayout>,
)

data class SystemLayout(
    val index: Int,
    val top: Float,
    val x: Float,
    val width: Float,
    val headerWidth: Float,
    val showTime: Boolean,
    val measures: List<MeasureLayout>,
) {
    fun staffTop(s: Float) = top + Dim.ABOVE_STAFF * s
    fun staffBottom(s: Float) = staffTop(s) + Dim.STAFF * s
    /** y of a staff step (0 = bottom line, 8 = top line). */
    fun yForStep(step: Int, s: Float) = staffBottom(s) - step * s / 2f
}

data class Hit(
    val measure: Int,
    val insertIndex: Int,
    val eventIndex: Int?,
    val staffStep: Int,
    val tick: Int,
    val xFrac: Float,
)

class ScoreLayout(
    val score: Score,
    val systems: List<SystemLayout>,
    val height: Float,
    val s: Float,
) {
    fun measureLayout(index: Int): Pair<SystemLayout, MeasureLayout>? {
        for (sys in systems) for (m in sys.measures) if (m.index == index) return sys to m
        return null
    }

    fun hitTest(x: Float, y: Float): Hit? {
        val sys = systems.firstOrNull { y >= it.top && y < it.top + Dim.SYSTEM_HEIGHT * s } ?: return null
        if (sys.measures.isEmpty()) return null
        val ml = sys.measures.firstOrNull { x >= it.x && x < it.x + it.width }
            ?: if (x < sys.measures.first().x) sys.measures.first() else sys.measures.last()
        val measure = score.measures[ml.index]
        val step = ((sys.staffBottom(s) - y) / (s / 2f)).roundToInt().coerceIn(-12, 20)
        val xFrac = ((x - ml.x) / ml.width).coerceIn(0f, 1f)

        var eventIndex: Int? = null
        for (e in ml.events) {
            if (x >= e.x - 0.5f * s && x <= e.x + max(e.width - 0.5f * s, 1.4f * s)) { eventIndex = e.index; break }
        }
        val insertIndex = ml.events.count { it.x + 0.6f * s < x }
        val mTicks = score.measureTicks
        val tick = when {
            eventIndex != null -> ml.events[eventIndex].startTick
            measure.events.isEmpty() || insertIndex >= ml.events.size -> {
                val frac = ((x - ml.contentX) / ml.contentWidth).coerceIn(0f, 0.999f)
                val beat = TICKS_PER_QUARTER * 4 / score.timeDen
                ((frac * mTicks / beat).toInt() * beat).coerceAtMost(mTicks - 1).coerceAtLeast(0)
                    .let { if (measure.events.isNotEmpty()) max(it, measure.usedTicks.coerceAtMost(mTicks - 1)) else it }
            }
            else -> ml.events[insertIndex].startTick
        }
        return Hit(ml.index, insertIndex, eventIndex, step, tick, xFrac)
    }

    companion object {
        fun eventWidth(e: Event, keyFifths: Int): Float {
            var w = when (e.value) {
                NoteValue.DOUBLE_WHOLE -> 5f
                NoteValue.WHOLE -> 4.4f
                NoteValue.HALF -> 3.5f
                NoteValue.QUARTER -> 2.8f
                NoteValue.EIGHTH -> 2.4f
                NoteValue.SIXTEENTH -> 2.2f
                NoteValue.THIRTY_SECOND -> 2.1f
                NoteValue.SIXTY_FOURTH -> 2.0f
            }
            if (e.kind == EventKind.NOTE && e.value.flags > 0) w += 0.6f
            w += 0.8f * e.dots
            if (e.kind == EventKind.NOTE && e.pitches.any { it.alter != keyAlterFor(it.step, keyFifths) }) w += 1.4f
            if (e.marks.any { it == "arpeggio" || it.startsWith("grace") }) w += 1.4f
            if (!e.lyric.isNullOrBlank()) w = max(w, e.lyric.length * 0.75f + 0.6f)
            return w
        }

        fun headerWidth(score: Score, showTime: Boolean): Float {
            var w = 0.8f + Dim.CLEF_WIDTH + abs(score.keyFifths) * Dim.KEY_ACC_WIDTH + 0.5f
            if (showTime) w += if (score.timeSymbol == TimeSymbol.NUMERIC) {
                max(score.timeNum.toString().length, score.timeDen.toString().length) * 1.1f + 1.2f
            } else Dim.TIME_WIDTH
            return w
        }

        private fun pads(m: Measure): Pair<Float, Float> {
            val left = 1.2f + if (m.startBar == StartBar.REPEAT_START) 1.4f else if (m.startBar == StartBar.DOUBLE) 0.5f else 0f
            val right = 0.6f + if (m.endBar == EndBar.REPEAT_END) 1.4f else if (m.endBar == EndBar.FINAL) 0.6f else 0f
            return left to right
        }

        fun naturalWidth(m: Measure, score: Score): Float {
            val (l, r) = pads(m)
            val content = if (m.events.isEmpty()) {
                if (m.repeatMeasure) 4f else max(Dim.EMPTY_MEASURE - l - r, m.chords.sumOf { chordWidth(it.text).toDouble() }.toFloat())
            } else m.events.sumOf { eventWidth(it, score.keyFifths).toDouble() }.toFloat()
            val chordsW = m.chords.sumOf { (chordWidth(it.text) + 0.6f).toDouble() }.toFloat()
            return max(Dim.MIN_MEASURE, l + max(content, chordsW) + r)
        }

        fun chordWidth(text: String) = text.length * 1.15f + 0.6f

        fun build(score: Score, widthPx: Float, s: Float, marginSpaces: Float = 2f): ScoreLayout {
            val margin = marginSpaces * s
            val available = widthPx - 2 * margin
            val systems = mutableListOf<SystemLayout>()
            var top = (if (score.title.isNotBlank() || score.composer.isNotBlank()) Dim.TITLE_HEIGHT else 2f) * s
            var i = 0
            val mpl = score.measuresPerLine
            while (i < score.measures.size || (systems.isEmpty() && score.measures.isEmpty())) {
                if (score.measures.isEmpty()) break
                val showTime = systems.isEmpty()
                val header = headerWidth(score, showTime) * s
                val avail = available - header
                val row = mutableListOf<Int>()
                var natural = 0f
                while (i < score.measures.size) {
                    val w = naturalWidth(score.measures[i], score) * s
                    if (row.isNotEmpty() && (natural + w > avail || (mpl > 0 && row.size >= mpl))) break
                    row += i; natural += w; i++
                }
                val naturals = row.map { naturalWidth(score.measures[it], score) * s }
                val isLast = i >= score.measures.size
                val widths: List<Float> = when {
                    mpl > 0 && isLast && row.size < mpl && natural < avail -> {
                        // Keep the grid look: each measure as wide as a full row slot, if it fits.
                        val slot = avail / mpl
                        naturals.map { max(it, slot) }.let { ws -> if (ws.sum() > avail) naturals else ws }
                    }
                    !isLast || mpl > 0 || natural > avail * 0.75f -> {
                        val extra = avail - natural
                        naturals.map { it + extra * (it / natural) }
                    }
                    else -> naturals
                }
                var x = margin + header
                val mls = row.mapIndexed { k, mi ->
                    val m = score.measures[mi]
                    val w = widths[k]
                    val (lp, rp) = pads(m)
                    val contentX = x + lp * s
                    val contentW = w - (lp + rp) * s
                    val ews = m.events.map { eventWidth(it, score.keyFifths) * s }
                    val totalEw = ews.sum()
                    val stretch = if (totalEw > 0f) contentW / totalEw else 1f
                    var ex = contentX
                    var tick = 0
                    val events = m.events.mapIndexed { ei, e ->
                        val el = EventLayout(ei, ex, ews[ei] * stretch, tick)
                        ex += ews[ei] * stretch
                        tick += e.ticks
                        el
                    }
                    MeasureLayout(mi, x, w, contentX, contentW, events).also { x += w }
                }
                systems += SystemLayout(systems.size, top, margin, available, header, showTime, mls)
                top += Dim.SYSTEM_HEIGHT * s
            }
            return ScoreLayout(score, systems, top + 2 * s, s)
        }
    }
}

/**
 * Decides which accidental must be printed for each pitch of each event in a measure,
 * following the usual rule: the key signature applies, and accidentals last until the barline.
 * Returns, for each event, a list parallel to its pitches (null = nothing printed).
 */
fun displayAccidentals(measure: Measure, keyFifths: Int): List<List<Int?>> {
    val state = HashMap<Int, Int>() // diatonic -> current alter
    return measure.events.map { e ->
        if (e.kind != EventKind.NOTE) emptyList() else e.pitches.map { p ->
            val current = state[p.diatonic] ?: keyAlterFor(p.step, keyFifths)
            if (p.alter != current) {
                state[p.diatonic] = p.alter
                p.alter
            } else null
        }
    }
}
