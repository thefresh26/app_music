package com.activos.pentagrama.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.render.Hit
import com.activos.pentagrama.symbols.MusicSymbol
import com.activos.pentagrama.symbols.SymbolAction
import com.activos.pentagrama.symbols.SymbolCatalog

/** A text value the user must type before an action is applied (chord symbol, section label...). */
data class PendingText(val hit: Hit, val action: SymbolAction, val initial: String)

class EditorState(initial: Score, val id: String) {
    var score by mutableStateOf(initial)
        private set
    var selection by mutableStateOf<Selection?>(null)
    var tool by mutableStateOf(SymbolCatalog.curated.first { it.id == "note_QUARTER" })
    var chordMode by mutableStateOf(false)
    var zoom by mutableStateOf(1f)
    var message by mutableStateOf<String?>(null)
    /** Play each note while writing it (like pressing a piano key). */
    var soundOn by mutableStateOf(true)
    /** Set by the screen: plays the given MIDI notes. */
    var onAudition: ((List<Int>) -> Unit)? = null

    private fun auditionSelected() {
        if (!soundOn) return
        val sel = selection ?: return
        val e = sel.event?.let { score.measures.getOrNull(sel.measure)?.events?.getOrNull(it) } ?: return
        if (e.kind == EventKind.NOTE && e.pitches.isNotEmpty()) onAudition?.invoke(e.pitches.map { it.midi })
    }
    var dirty by mutableStateOf(false)
    var pendingText by mutableStateOf<PendingText?>(null)

    private val undoStack = ArrayDeque<Score>()
    private val redoStack = ArrayDeque<Score>()
    private var historyVersion by mutableIntStateOf(0)

    val canUndo: Boolean get() { historyVersion; return undoStack.isNotEmpty() }
    val canRedo: Boolean get() { historyVersion; return redoStack.isNotEmpty() }

    fun commit(new: Score) {
        if (new == score) return
        undoStack.addLast(score)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        score = new
        dirty = true
        historyVersion++
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(score)
        score = prev
        fixSelection()
        dirty = true
        historyVersion++
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(score)
        score = next
        fixSelection()
        dirty = true
        historyVersion++
    }

    private fun fixSelection() {
        val sel = selection ?: return
        val m = score.measures.getOrNull(sel.measure)
        selection = when {
            m == null -> null
            sel.event != null && sel.event !in m.events.indices -> Selection(sel.measure)
            else -> sel
        }
    }

    private fun needsText(a: SymbolAction) = a is SymbolAction.ChordSymbol || a is SymbolAction.SectionLabel ||
        a is SymbolAction.FreeText || a is SymbolAction.Lyric

    /** User tapped the staff. */
    fun tap(hit: Hit) {
        val action = tool.action
        if (needsText(action)) {
            if (action is SymbolAction.Lyric && hit.eventIndex == null) {
                message = "Toca una nota para escribirle la letra"; return
            }
            val m = score.measures[hit.measure]
            val initial = when (action) {
                SymbolAction.ChordSymbol -> m.chords.firstOrNull { it.tick == hit.tick }?.text ?: ""
                SymbolAction.SectionLabel -> m.section ?: ""
                SymbolAction.FreeText -> m.text ?: ""
                SymbolAction.Lyric -> hit.eventIndex?.let { m.events[it].lyric } ?: ""
                else -> ""
            }
            pendingText = PendingText(hit, action, initial)
            return
        }
        val r = ScoreOps.apply(score, hit, action, chordMode)
        commit(r.score)
        selection = r.selection
        r.message?.let { message = it }
        if (action is SymbolAction.PlaceEvent || action is SymbolAction.Accidental || action is SymbolAction.Select) auditionSelected()
    }

    fun confirmText(text: String) {
        val p = pendingText ?: return
        pendingText = null
        val r = ScoreOps.apply(score, p.hit, p.action, chordMode, text)
        commit(r.score)
        selection = r.selection
    }

    /** A palette symbol was chosen. Some symbols act immediately on the current selection. */
    fun choose(symbol: MusicSymbol) {
        tool = symbol
        val a = symbol.action
        val sel = selection
        when (a) {
            is SymbolAction.SetClef, is SymbolAction.SetKey, is SymbolAction.SetTime -> {
                commit(ScoreOps.apply(score, syntheticHit(sel ?: Selection(0)), a, false).score)
                tool = SymbolCatalog.curated.first { it.id == "note_QUARTER" }
                message = "${symbol.nameEs} aplicado a la partitura"
            }
            is SymbolAction.Accidental, SymbolAction.ToggleDot, SymbolAction.ToggleTie, SymbolAction.ToggleTriplet,
            is SymbolAction.SetHead, is SymbolAction.Attach -> {
                if (sel?.event != null) commit(ScoreOps.apply(score, syntheticHit(sel), a, false).score)
            }
            is SymbolAction.PlaceEvent -> {
                // With a selected event, choosing a duration changes that event's duration.
                if (sel?.event != null && score.measures[sel.measure].events.getOrNull(sel.event)?.kind == a.kind) {
                    commit(ScoreOps.changeValue(score, sel, a))
                }
            }
            else -> {}
        }
    }

    private fun syntheticHit(sel: Selection): Hit {
        val m = score.measures[sel.measure.coerceIn(0, score.measures.lastIndex)]
        val e = sel.event?.let { m.events.getOrNull(it) }
        val step = e?.pitches?.firstOrNull()?.let { it.diatonic - score.clef.bottomLineDiatonic } ?: 4
        val tick = sel.event?.let { idx -> m.events.take(idx).sumOf { it.ticks } } ?: 0
        return Hit(sel.measure, sel.event ?: m.events.size, sel.event, step, tick, 0.5f)
    }

    fun moveSelected(steps: Int) {
        val sel = selection ?: return
        val e = sel.event?.let { score.measures[sel.measure].events.getOrNull(it) } ?: return
        if (e.kind != EventKind.NOTE) return
        commit(ScoreOps.transpose(score, sel, steps))
        auditionSelected()
    }

    fun deleteSelected() {
        val sel = selection ?: return
        if (sel.event != null) {
            commit(ScoreOps.deleteEvent(score, sel))
            val remaining = score.measures[sel.measure].events.size
            selection = if (remaining == 0) Selection(sel.measure) else Selection(sel.measure, (sel.event - 1).coerceAtLeast(0))
        } else {
            commit(ScoreOps.clearMeasure(score, sel.measure))
        }
    }

    fun selectNext(delta: Int) {
        val sel = selection ?: Selection(0, null)
        val flat = score.measures.flatMapIndexed { mi, m -> if (m.events.isEmpty()) listOf(mi to null) else m.events.indices.map { mi to it } }
        val cur = flat.indexOfFirst { it.first == sel.measure && it.second == sel.event }.coerceAtLeast(0)
        val n = flat.getOrNull((cur + delta).coerceIn(0, flat.lastIndex)) ?: return
        selection = Selection(n.first, n.second)
    }

    fun addMeasure() {
        val at = selection?.measure ?: score.measures.lastIndex
        commit(ScoreOps.insertMeasureAfter(score, at))
        selection = Selection(at + 1)
    }

    fun removeMeasure() {
        val at = selection?.measure ?: score.measures.lastIndex
        commit(ScoreOps.deleteMeasure(score, at))
        selection = Selection((at - 1).coerceAtLeast(0))
    }

    fun updateScore(f: (Score) -> Score) = commit(f(score))
}
