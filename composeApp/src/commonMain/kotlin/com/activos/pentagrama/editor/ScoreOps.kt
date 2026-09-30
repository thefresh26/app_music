package com.activos.pentagrama.editor

import com.activos.pentagrama.model.ChordMark
import com.activos.pentagrama.model.Event
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.FreeSymbol
import com.activos.pentagrama.model.Measure
import com.activos.pentagrama.model.Pitch
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.keyAlterFor
import com.activos.pentagrama.render.Hit
import com.activos.pentagrama.symbols.SymbolAction
import kotlin.math.abs

data class Selection(val measure: Int, val event: Int? = null)

data class OpResult(val score: Score, val selection: Selection?, val message: String? = null)

/** Pure editing operations over an immutable [Score]. */
object ScoreOps {

    fun pitchForStep(score: Score, step: Int): Pitch {
        val d = score.clef.bottomLineDiatonic + step
        val p = Pitch.fromDiatonic(d)
        return p.copy(alter = keyAlterFor(p.step, score.keyFifths))
    }

    private fun Score.updateMeasure(i: Int, f: (Measure) -> Measure): Score =
        copy(measures = measures.mapIndexed { k, m -> if (k == i) f(m) else m })

    private fun Measure.updateEvent(i: Int, f: (Event) -> Event): Measure =
        copy(events = events.mapIndexed { k, e -> if (k == i) f(e) else e })

    /** Apply a palette action at a tapped position. [text] is used by text actions. */
    fun apply(score: Score, hit: Hit, action: SymbolAction, chordMode: Boolean, text: String? = null): OpResult {
        val mi = hit.measure
        return when (action) {
            is SymbolAction.PlaceEvent -> placeEvent(score, hit, action, chordMode)
            is SymbolAction.Accidental -> onEvent(score, hit.measure, hit.eventIndex) { e ->
                if (e.kind != EventKind.NOTE || e.pitches.isEmpty()) e else {
                    val idx = nearestPitch(score, e, hit.staffStep)
                    e.copy(pitches = e.pitches.mapIndexed { k, p -> if (k == idx) p.copy(alter = action.alter) else p })
                }
            }
            SymbolAction.ToggleDot -> onEvent(score, mi, hit.eventIndex) { it.copy(dots = if (it.dots == 0) 1 else 0) }
            SymbolAction.ToggleTie -> onEvent(score, mi, hit.eventIndex) { it.copy(tieToNext = !it.tieToNext) }
            SymbolAction.ToggleTriplet -> onEvent(score, mi, hit.eventIndex) { it.copy(triplet = !it.triplet) }
            is SymbolAction.SetHead -> onEvent(score, mi, hit.eventIndex) { it.copy(head = action.head) }
            is SymbolAction.Attach -> onEvent(score, mi, hit.eventIndex) { e ->
                e.copy(marks = if (action.markId in e.marks) e.marks - action.markId else e.marks + action.markId)
            }
            is SymbolAction.SetClef -> OpResult(score.copy(clef = action.clef), Selection(mi))
            is SymbolAction.SetKey -> OpResult(score.copy(keyFifths = action.fifths), Selection(mi))
            is SymbolAction.SetTime -> OpResult(score.copy(timeNum = action.num, timeDen = action.den, timeSymbol = action.symbol), Selection(mi))
            is SymbolAction.SetStartBar -> OpResult(score.updateMeasure(mi) { it.copy(startBar = action.bar) }, Selection(mi))
            is SymbolAction.SetEndBar -> OpResult(score.updateMeasure(mi) { it.copy(endBar = action.bar) }, Selection(mi))
            is SymbolAction.MeasureMark -> OpResult(score.updateMeasure(mi) { m ->
                m.copy(marks = if (action.markId in m.marks) m.marks - action.markId else m.marks + action.markId)
            }, Selection(mi))
            is SymbolAction.Ending -> OpResult(score.updateMeasure(mi) { it.copy(ending = action.text.ifBlank { null }) }, Selection(mi))
            SymbolAction.MeasureRepeat -> OpResult(score.updateMeasure(mi) {
                if (it.repeatMeasure) it.copy(repeatMeasure = false) else it.copy(repeatMeasure = true, events = emptyList())
            }, Selection(mi))
            SymbolAction.ChordSymbol -> OpResult(setChord(score, mi, hit.tick, text ?: ""), Selection(mi))
            SymbolAction.SectionLabel -> OpResult(score.updateMeasure(mi) { it.copy(section = text?.ifBlank { null }) }, Selection(mi))
            SymbolAction.FreeText -> OpResult(score.updateMeasure(mi) { it.copy(text = text?.ifBlank { null }) }, Selection(mi))
            SymbolAction.Lyric -> onEvent(score, mi, hit.eventIndex) { it.copy(lyric = text?.ifBlank { null }) }
            is SymbolAction.Free -> OpResult(score.updateMeasure(mi) {
                it.copy(free = it.free + FreeSymbol(action.codepoint, hit.xFrac, hit.staffStep))
            }, Selection(mi))
            SymbolAction.Eraser -> erase(score, hit)
            SymbolAction.Select -> OpResult(score, Selection(mi, hit.eventIndex))
        }
    }

    private fun nearestPitch(score: Score, e: Event, step: Int): Int {
        val target = score.clef.bottomLineDiatonic + step
        return e.pitches.indices.minByOrNull { abs(e.pitches[it].diatonic - target) } ?: 0
    }

    private fun onEvent(score: Score, mi: Int, ei: Int?, f: (Event) -> Event): OpResult {
        if (ei == null) return OpResult(score, Selection(mi), "Toca directamente sobre una nota")
        val m = score.measures[mi]
        if (ei !in m.events.indices) return OpResult(score, Selection(mi))
        return OpResult(score.updateMeasure(mi) { it.updateEvent(ei, f) }, Selection(mi, ei))
    }

    private fun placeEvent(score: Score, hit: Hit, a: SymbolAction.PlaceEvent, chordMode: Boolean): OpResult {
        val mi = hit.measure
        val m = score.measures[mi]
        val ei = hit.eventIndex
        val existing = ei?.let { m.events.getOrNull(it) }
        if (ei != null && a.kind == EventKind.NOTE && existing != null && existing.kind == EventKind.NOTE && chordMode) {
            val p = pitchForStep(score, hit.staffStep)
            if (existing.pitches.none { it.diatonic == p.diatonic }) {
                val ns = score.updateMeasure(mi) { mm ->
                    mm.updateEvent(ei) { it.copy(pitches = (it.pitches + p).sortedBy { q -> q.diatonic }) }
                }
                return OpResult(ns, Selection(mi, ei))
            }
            return OpResult(score, Selection(mi, ei))
        }
        val ev = Event(
            kind = a.kind,
            value = a.value,
            pitches = if (a.kind == EventKind.NOTE) listOf(pitchForStep(score, hit.staffStep)) else emptyList(),
            dots = a.dots,
            triplet = a.triplet,
            head = a.head,
        )
        // Replace a tapped rest/slash of the same kind? Keep it simple: tapped event -> replace; otherwise insert.
        val (newMeasure, idx) = if (ei != null && existing != null && !chordMode) {
            m.copy(events = m.events.toMutableList().also { it[ei] = ev }, repeatMeasure = false) to ei
        } else {
            val at = hit.insertIndex.coerceIn(0, m.events.size)
            m.copy(events = m.events.toMutableList().also { it.add(at, ev) }, repeatMeasure = false) to at
        }
        var ns = score.updateMeasure(mi) { newMeasure }
        var msg: String? = null
        if (newMeasure.usedTicks > score.measureTicks) msg = "El compás ${mi + 1} tiene más tiempos de los que permite ${score.timeNum}/${score.timeDen}"
        if (mi == ns.measures.lastIndex && newMeasure.usedTicks >= score.measureTicks) {
            ns = ns.copy(measures = ns.measures + Measure())
        }
        return OpResult(ns, Selection(mi, idx), msg)
    }

    private fun setChord(score: Score, mi: Int, tick: Int, text: String): Score = score.updateMeasure(mi) { m ->
        val others = m.chords.filter { it.tick != tick }
        m.copy(chords = if (text.isBlank()) others else (others + ChordMark(tick, text.trim())).sortedBy { it.tick })
    }

    private fun erase(score: Score, hit: Hit): OpResult {
        val mi = hit.measure
        val m = score.measures[mi]
        hit.eventIndex?.let { ei ->
            return OpResult(score.updateMeasure(mi) { it.copy(events = it.events.filterIndexed { k, _ -> k != ei }) }, Selection(mi))
        }
        val free = m.free.withIndex().minByOrNull { abs(it.value.xFrac - hit.xFrac) + abs(it.value.staffStep - hit.staffStep) * 0.02f }
        if (free != null && abs(free.value.xFrac - hit.xFrac) < 0.12f) {
            return OpResult(score.updateMeasure(mi) { it.copy(free = it.free.filterIndexed { k, _ -> k != free.index }) }, Selection(mi))
        }
        if (hit.staffStep > 9) { // above the staff: chords, marks, labels
            val chord = m.chords.minByOrNull { abs(it.tick - hit.tick) }
            if (chord != null) return OpResult(score.updateMeasure(mi) { it.copy(chords = it.chords - chord) }, Selection(mi))
            if (m.marks.isNotEmpty()) return OpResult(score.updateMeasure(mi) { it.copy(marks = it.marks.dropLast(1)) }, Selection(mi))
            if (m.section != null || m.text != null || m.ending != null)
                return OpResult(score.updateMeasure(mi) { it.copy(section = null, text = null, ending = null) }, Selection(mi))
        }
        if (m.repeatMeasure) return OpResult(score.updateMeasure(mi) { it.copy(repeatMeasure = false) }, Selection(mi))
        return OpResult(score, Selection(mi))
    }

    // ---------- Selection based operations ----------

    fun transpose(score: Score, sel: Selection, steps: Int): Score {
        val ei = sel.event ?: return score
        return score.updateMeasure(sel.measure) { m ->
            m.updateEvent(ei) { e ->
                e.copy(pitches = e.pitches.map { p ->
                    val np = Pitch.fromDiatonic(p.diatonic + steps)
                    np.copy(alter = keyAlterFor(np.step, score.keyFifths))
                })
            }
        }
    }

    fun deleteEvent(score: Score, sel: Selection): Score {
        val ei = sel.event ?: return score
        return score.updateMeasure(sel.measure) { it.copy(events = it.events.filterIndexed { k, _ -> k != ei }) }
    }

    fun changeValue(score: Score, sel: Selection, a: SymbolAction.PlaceEvent): Score {
        val ei = sel.event ?: return score
        return score.updateMeasure(sel.measure) { m -> m.updateEvent(ei) { it.copy(value = a.value, dots = a.dots, triplet = a.triplet) } }
    }

    fun insertMeasureAfter(score: Score, index: Int): Score {
        val l = score.measures.toMutableList()
        l.add((index + 1).coerceIn(0, l.size), Measure())
        return score.copy(measures = l)
    }

    fun deleteMeasure(score: Score, index: Int): Score {
        if (score.measures.size <= 1) return score.copy(measures = listOf(Measure()))
        return score.copy(measures = score.measures.filterIndexed { k, _ -> k != index })
    }

    fun clearMeasure(score: Score, index: Int): Score = score.updateMeasure(index) { Measure(startBar = it.startBar, endBar = it.endBar) }

    /** Fill every empty measure with rhythm slashes (useful for chord charts). */
    fun fillSlashes(score: Score): Score {
        val beats = score.timeNum
        val value = when (score.timeDen) {
            2 -> com.activos.pentagrama.model.NoteValue.HALF
            8 -> com.activos.pentagrama.model.NoteValue.EIGHTH
            else -> com.activos.pentagrama.model.NoteValue.QUARTER
        }
        return score.copy(measures = score.measures.map { m ->
            if (m.events.isEmpty() && !m.repeatMeasure) m.copy(events = List(beats) { Event(EventKind.SLASH, value) }) else m
        })
    }
}
