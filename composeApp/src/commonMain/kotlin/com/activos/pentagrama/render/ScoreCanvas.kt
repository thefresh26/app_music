package com.activos.pentagrama.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.activos.pentagrama.editor.Selection
import com.activos.pentagrama.model.Clef
import com.activos.pentagrama.model.EndBar
import com.activos.pentagrama.model.Event
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.NoteHead
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.model.StartBar
import com.activos.pentagrama.model.TICKS_PER_QUARTER
import com.activos.pentagrama.model.TimeSymbol
import com.activos.pentagrama.symbols.G
import com.activos.pentagrama.symbols.GlyphMetrics
import com.activos.pentagrama.symbols.MarkPlacement
import com.activos.pentagrama.symbols.Marks
import kotlin.math.max
import kotlin.math.min

data class ScoreColors(
    val ink: Color,
    val staff: Color,
    val selection: Color,
    val measureSelection: Color,
    val overfull: Color,
    val section: Color,
    val sectionText: Color,
    val chord: Color,
    val paper: Color,
    val muted: Color,
    /** Faint lines that show the free beats ("casillas") where a tap places a note. Transparent = off (print). */
    val guide: Color = Color.Transparent,
)

@Composable
fun ScoreCanvas(
    score: Score,
    layout: ScoreLayout,
    musicFont: FontFamily,
    colors: ScoreColors,
    selection: Selection?,
    onTap: (Hit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 1024)
    val currentLayout by rememberUpdatedState(layout)
    val currentOnTap by rememberUpdatedState(onTap)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos -> currentLayout.hitTest(pos.x, pos.y)?.let { currentOnTap(it) } }
        }
    ) {
        drawRect(colors.paper)
        ScorePainter(this, measurer, musicFont, colors, layout, selection).drawAll()
    }
}

/** Draws a laid-out score. SMuFL glyphs are drawn with their baseline on the staff position they refer to. */
internal class ScorePainter(
    val ds: DrawScope,
    val measurer: TextMeasurer,
    val musicFont: FontFamily,
    val c: ScoreColors,
    val layout: ScoreLayout,
    val selection: Selection?,
) {
    val s = layout.s
    val score = layout.score
    private val density: Density = ds
    private val em: TextUnit = with(density) { (4f * s).toSp() }
    private val musicStyle = TextStyle(fontFamily = musicFont, fontSize = em)

    fun px(spaces: Float) = spaces * s

    // ---------------------------------------------------------------- primitives

    fun glyph(cp: Int, x: Float, baseline: Float, color: Color = c.ink, scale: Float = 1f) {
        val style = if (scale == 1f) musicStyle else musicStyle.copy(fontSize = em * scale)
        val r = measurer.measure(G.str(cp), style)
        ds.drawText(r, color, Offset(x, baseline - r.firstBaseline))
    }

    fun glyphWidth(cp: Int, scale: Float = 1f): Float = GlyphMetrics[cp].let { max(it.advance, it.width) } * s * scale

    fun text(
        str: String, x: Float, baseline: Float, sizeSpaces: Float, color: Color = c.ink,
        bold: Boolean = false, italic: Boolean = false, serif: Boolean = true, alignCenter: Boolean = false,
        alignRight: Boolean = false,
    ): Float {
        val style = TextStyle(
            fontSize = with(density) { (sizeSpaces * s).toSp() },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
        )
        val r = measurer.measure(str, style)
        val w = r.size.width.toFloat()
        val dx = when { alignCenter -> -w / 2; alignRight -> -w; else -> 0f }
        ds.drawText(r, color, Offset(x + dx, baseline - r.firstBaseline))
        return w
    }

    fun textWidth(str: String, sizeSpaces: Float, bold: Boolean = false): Float {
        val style = TextStyle(
            fontSize = with(density) { (sizeSpaces * s).toSp() },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Serif,
        )
        return measurer.measure(str, style).size.width.toFloat()
    }

    fun vline(x: Float, y1: Float, y2: Float, w: Float = 0.16f, color: Color = c.ink) =
        ds.drawLine(color, Offset(x, y1), Offset(x, y2), strokeWidth = px(w))

    fun hline(x1: Float, x2: Float, y: Float, w: Float = 0.13f, color: Color = c.ink) =
        ds.drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = px(w))

    // ---------------------------------------------------------------- score

    fun drawAll() {
        drawTitle()
        for (sys in layout.systems) drawSystem(sys)
    }

    private fun drawTitle() {
        val cx = ds.size.width / 2
        if (score.title.isNotBlank()) text(score.title, cx, px(4.2f), 2.6f, bold = true, alignCenter = true)
        if (score.subtitle.isNotBlank()) text(score.subtitle, cx, px(6.6f), 1.3f, c.muted, italic = true, alignCenter = true)
        if (score.composer.isNotBlank()) text(score.composer, ds.size.width - px(2f), px(8.6f), 1.4f, alignRight = true)
    }

    private fun drawSystem(sys: SystemLayout) {
        val top = sys.staffTop(s)
        val bottom = sys.staffBottom(s)
        // selection / overfull backgrounds
        for (ml in sys.measures) {
            val m = score.measures[ml.index]
            if (m.usedTicks > score.measureTicks) {
                ds.drawRect(c.overfull, Offset(ml.x, top - px(1f)), Size(ml.width, px(6f)))
            }
            if (selection != null && selection.measure == ml.index) {
                if (selection.event == null) {
                    ds.drawRoundRect(c.measureSelection, Offset(ml.x, sys.top + px(1f)), Size(ml.width, px(Dim.SYSTEM_HEIGHT - 2f)), CornerRadius(px(0.6f)))
                } else ml.events.getOrNull(selection.event)?.let { el ->
                    ds.drawRoundRect(c.selection, Offset(el.x - px(0.3f), top - px(3f)), Size(max(el.width, px(1.8f)), px(10f)), CornerRadius(px(0.5f)))
                }
            }
        }
        // staff lines
        val x0 = sys.x
        val x1 = sys.measures.lastOrNull()?.let { it.x + it.width } ?: (sys.x + sys.width)
        for (i in 0 until 5) hline(x0, x1, top + px(i.toFloat()), color = c.staff)
        vline(x0, top, bottom, 0.16f)
        drawHeader(sys)
        if (sys.index > 0 && sys.measures.isNotEmpty()) {
            text("${sys.measures.first().index + 1}", x0, top - px(1.1f), 1.1f, c.muted, italic = true)
        }
        for (ml in sys.measures) drawMeasure(sys, ml)
    }

    private fun clefGlyphAndStep(clef: Clef): Pair<Int, Int> = when (clef) {
        Clef.TREBLE -> G.G_CLEF to 2
        Clef.TREBLE_8VB -> G.G_CLEF_8VB to 2
        Clef.BASS -> G.F_CLEF to 6
        Clef.ALTO -> G.C_CLEF to 4
        Clef.TENOR -> G.C_CLEF to 6
        Clef.PERCUSSION -> G.PERC_CLEF to 4
    }

    fun keySteps(fifths: Int, clef: Clef): List<Int> {
        val trebleSharps = listOf(8, 5, 9, 6, 3, 7, 4)
        val trebleFlats = listOf(4, 7, 3, 6, 2, 5, 1)
        val shift = when (clef) {
            Clef.TREBLE, Clef.TREBLE_8VB, Clef.PERCUSSION -> 0
            Clef.BASS -> -2
            Clef.ALTO -> -1
            Clef.TENOR -> if (fifths > 0) -6 else 1
        }
        return if (fifths > 0) trebleSharps.take(fifths).map { it + shift }
        else trebleFlats.take(-fifths).map { it + shift }
    }

    private fun drawHeader(sys: SystemLayout) {
        var x = sys.x + px(0.8f)
        val (clefCp, clefStep) = clefGlyphAndStep(score.clef)
        glyph(clefCp, x, sys.yForStep(clefStep, s))
        x += px(Dim.CLEF_WIDTH)
        if (score.clef != Clef.PERCUSSION) {
            val acc = if (score.keyFifths > 0) G.SHARP else G.FLAT
            for (st in keySteps(score.keyFifths, score.clef)) {
                glyph(acc, x, sys.yForStep(st, s)); x += px(Dim.KEY_ACC_WIDTH)
            }
        }
        x += px(0.5f)
        if (sys.showTime) {
            when (score.timeSymbol) {
                TimeSymbol.COMMON -> glyph(G.TIME_COMMON, x, sys.yForStep(4, s))
                TimeSymbol.CUT -> glyph(G.TIME_CUT, x, sys.yForStep(4, s))
                TimeSymbol.NUMERIC -> {
                    val num = score.timeNum.toString(); val den = score.timeDen.toString()
                    val wN = num.length * 1.1f; val wD = den.length * 1.1f
                    val wMax = max(wN, wD)
                    num.forEachIndexed { i, ch -> glyph(G.TIME_0 + (ch - '0'), x + px((wMax - wN) / 2 + i * 1.1f), sys.yForStep(6, s)) }
                    den.forEachIndexed { i, ch -> glyph(G.TIME_0 + (ch - '0'), x + px((wMax - wD) / 2 + i * 1.1f), sys.yForStep(2, s)) }
                }
            }
        }
    }

    private fun drawMeasure(sys: SystemLayout, ml: MeasureLayout) {
        val m = score.measures[ml.index]
        val top = sys.staffTop(s)
        val bottom = sys.staffBottom(s)
        val end = ml.x + ml.width

        // ---- start barline
        when (m.startBar) {
            StartBar.REPEAT_START -> {
                val bx = ml.x + px(0.25f)
                vline(bx, top, bottom, 0.5f)
                vline(bx + px(0.6f), top, bottom, 0.16f)
                glyph(G.REPEAT_DOTS, bx + px(0.85f), sys.yForStep(4, s))
            }
            StartBar.DOUBLE -> { vline(ml.x + px(0.1f), top, bottom); vline(ml.x + px(0.45f), top, bottom) }
            StartBar.NORMAL -> {}
        }
        // ---- end barline
        when (m.endBar) {
            EndBar.SINGLE -> vline(end, top, bottom)
            EndBar.DOUBLE -> { vline(end - px(0.45f), top, bottom); vline(end, top, bottom) }
            EndBar.FINAL -> { vline(end - px(0.75f), top, bottom); vline(end - px(0.25f), top, bottom, 0.5f) }
            EndBar.REPEAT_END -> {
                glyph(G.REPEAT_DOTS, end - px(1.45f), sys.yForStep(4, s))
                vline(end - px(0.8f), top, bottom); vline(end - px(0.25f), top, bottom, 0.5f)
            }
            EndBar.NONE -> {}
        }

        // ---- labels above the staff
        m.section?.let { label ->
            val w = textWidth(label, 1.5f, bold = true)
            ds.drawRoundRect(c.section, Offset(ml.x + px(0.2f), sys.top + px(0.4f)), Size(w + px(1.2f), px(2.2f)), CornerRadius(px(0.4f)))
            text(label, ml.x + px(0.8f), sys.top + px(2.05f), 1.5f, c.sectionText, bold = true, serif = false)
        }
        m.text?.let { t ->
            val sx = ml.x + px(0.4f) + if (m.section != null) textWidth(m.section, 1.5f, true) + px(2.4f) else 0f
            text(t, sx, sys.top + px(2.1f), 1.4f, italic = true, bold = true)
        }
        m.ending?.let { t ->
            val y = sys.top + px(3.3f)
            hline(ml.x + px(0.3f), end - px(0.4f), y, 0.12f)
            vline(ml.x + px(0.3f), y, y + px(1.6f), 0.12f)
            text(t, ml.x + px(0.7f), y + px(1.5f), 1.2f, bold = true)
        }
        var markX = ml.x + px(0.5f)
        var textMarkRight = end - px(0.4f)
        for (id in m.marks) {
            val def = Marks[id] ?: continue
            if (def.codepoint != null) {
                val base = if (def.placement == MarkPlacement.BELOW) sys.staffBottom(s) + px(3.5f) else sys.top + px(6.0f)
                glyph(def.codepoint, markX, base)
                markX += glyphWidth(def.codepoint) + px(0.6f)
            } else if (def.text != null) {
                val w = text(def.text, textMarkRight, sys.top + px(5.6f), 1.35f, italic = true, bold = true, alignRight = true)
                textMarkRight -= w + px(1f)
            }
        }

        // ---- chords
        val chordBase = sys.top + px(7.8f)
        for (ch in m.chords) {
            val x = chordX(ml, m.events, ch.tick)
            text(ch.text, x, chordBase, 1.9f, c.chord, bold = true)
        }

        // ---- free beats ("casillas"): where a tap in the empty part of the measure puts the note
        if (c.guide.alpha > 0f && !m.repeatMeasure && m.usedTicks < score.measureTicks) {
            val beat = TICKS_PER_QUARTER * 4 / score.timeDen
            val lastEnd = ml.events.lastOrNull()?.let { it.x + px(1.8f) } ?: ml.contentX
            var t = (m.usedTicks + beat - 1) / beat * beat
            while (t < score.measureTicks) {
                val x = ml.contentX + t.toFloat() / score.measureTicks * ml.contentWidth + px(0.6f)
                if (x > lastEnd) ds.drawLine(c.guide, Offset(x, top), Offset(x, bottom), strokeWidth = px(0.9f))
                t += beat
            }
        }

        // ---- content
        if (m.repeatMeasure) {
            val w = glyphWidth(G.REPEAT_1_BAR)
            glyph(G.REPEAT_1_BAR, ml.contentX + ml.contentWidth / 2 - w / 2, sys.yForStep(4, s))
        } else {
            val acc = displayAccidentals(m, score.keyFifths)
            m.events.forEachIndexed { i, e ->
                val el = ml.events[i]
                val nextX = ml.events.getOrNull(i + 1)?.x ?: (end + px(0.8f))
                drawEvent(sys, el, e, acc.getOrNull(i) ?: emptyList(), nextX)
            }
        }

        // ---- free symbols
        for (f in m.free) {
            val w = glyphWidth(f.codepoint)
            glyph(f.codepoint, ml.x + f.xFrac * ml.width - w / 2, sys.yForStep(f.staffStep, s))
        }
    }

    private fun chordX(ml: MeasureLayout, events: List<Event>, tick: Int): Float {
        ml.events.firstOrNull { it.startTick >= tick }?.let { if (it.startTick == tick) return it.x }
        val frac = tick.toFloat() / score.measureTicks
        return ml.contentX + frac * ml.contentWidth
    }

    private fun headGlyph(e: Event): Int = when (e.head) {
        NoteHead.X -> G.HEAD_X
        NoteHead.CIRCLE_X -> G.HEAD_CIRCLE_X
        NoteHead.TRIANGLE -> G.HEAD_TRIANGLE
        NoteHead.DIAMOND -> G.HEAD_DIAMOND
        NoteHead.NORMAL -> when (e.value) {
            NoteValue.DOUBLE_WHOLE -> G.HEAD_DOUBLE_WHOLE
            NoteValue.WHOLE -> G.HEAD_WHOLE
            NoteValue.HALF -> G.HEAD_HALF
            else -> G.HEAD_BLACK
        }
    }

    private fun drawEvent(sys: SystemLayout, el: EventLayout, e: Event, accidentals: List<Int?>, nextX: Float) {
        var headX = el.x
        if (accidentals.any { it != null }) headX += px(1.4f)
        if (e.marks.any { it == "arpeggio" || it.startsWith("grace") }) headX += px(1.4f)
        val stepOf = { d: Int -> d - score.clef.bottomLineDiatonic }

        var topY: Float
        var bottomY: Float
        var headW: Float
        var stemUp = true
        var stemEndY = 0f
        var stemX = 0f

        when (e.kind) {
            EventKind.REST -> {
                val (cp, st) = when (e.value) {
                    NoteValue.DOUBLE_WHOLE -> G.REST_DOUBLE_WHOLE to 4
                    NoteValue.WHOLE -> G.REST_WHOLE to 6
                    NoteValue.HALF -> G.REST_HALF to 4
                    NoteValue.QUARTER -> G.REST_QUARTER to 4
                    NoteValue.EIGHTH -> G.REST_8 to 4
                    NoteValue.SIXTEENTH -> G.REST_16 to 4
                    NoteValue.THIRTY_SECOND -> G.REST_32 to 4
                    NoteValue.SIXTY_FOURTH -> G.REST_64 to 4
                }
                val x = headX
                glyph(cp, x, sys.yForStep(st, s))
                headW = glyphWidth(cp)
                repeat(e.dots) { d -> glyph(G.DOT, x + headW + px(0.3f + d * 0.5f), sys.yForStep(5, s)) }
                topY = sys.staffTop(s); bottomY = sys.staffBottom(s)
            }
            EventKind.SLASH -> {
                val cp = when (e.value) { NoteValue.WHOLE, NoteValue.DOUBLE_WHOLE -> G.SLASH_WHOLE; NoteValue.HALF -> G.SLASH_HALF; else -> G.SLASH }
                val y = sys.yForStep(4, s)
                glyph(cp, headX, y)
                headW = glyphWidth(cp)
                val box = GlyphMetrics[cp]
                if (e.value != NoteValue.QUARTER && e.value.hasStem) {
                    stemX = headX + box.neX * s - px(0.08f)
                    stemEndY = y - px(box.neY) - px(2.8f)
                    vline(stemX, y - px(box.neY * 0.6f), stemEndY, 0.12f)
                    if (e.value.flags > 0) glyph(G.FLAG_8_UP + 2 * (e.value.flags - 1), stemX - px(0.06f), stemEndY)
                }
                repeat(e.dots) { d -> glyph(G.DOT, headX + headW + px(0.3f + d * 0.5f), sys.yForStep(5, s)) }
                topY = y - px(2f); bottomY = y + px(2f)
            }
            EventKind.NOTE -> {
                val steps = e.pitches.map { stepOf(it.diatonic) }.sorted()
                if (steps.isEmpty()) return
                val cp = headGlyph(e)
                headW = GlyphMetrics[cp].width * s
                val minStep = steps.first(); val maxStep = steps.last()
                stemUp = (minStep + maxStep) / 2f < 4f
                // ledger lines
                val ledgerX1 = headX - px(0.4f); val ledgerX2 = headX + headW + px(0.4f)
                var l = -2
                while (l >= minStep) { hline(ledgerX1, ledgerX2, sys.yForStep(l, s), 0.16f); l -= 2 }
                l = 10
                while (l <= maxStep) { hline(ledgerX1, ledgerX2, sys.yForStep(l, s), 0.16f); l += 2 }
                // heads, second intervals displaced to the other side of the stem
                var prev = Int.MIN_VALUE
                var displaced = false
                for (st in steps) {
                    displaced = st - prev == 1 && !displaced
                    val hx = if (displaced) (if (stemUp) headX + headW - px(0.12f) else headX - headW + px(0.12f)) else headX
                    glyph(cp, hx, sys.yForStep(st, s))
                    prev = st
                }
                // accidentals (parallel to pitches, which are sorted by diatonic in the model)
                val order = e.pitches.indices.sortedBy { e.pitches[it].diatonic }
                var accOffset = 0
                for (k in order.reversed()) {
                    val a = accidentals.getOrNull(k) ?: continue
                    val st = stepOf(e.pitches[k].diatonic)
                    glyph(G.accidental(a), el.x + px(0.1f) - px(0.9f * accOffset), sys.yForStep(st, s))
                    accOffset = (accOffset + 1) % 2
                }
                // dots
                repeat(e.dots) { d ->
                    for (st in steps) {
                        val dotStep = if (st % 2 == 0) st + 1 else st
                        glyph(G.DOT, headX + headW + px(0.35f + d * 0.5f), sys.yForStep(dotStep, s))
                    }
                }
                // stem & flags
                if (e.value.hasStem) {
                    val stemLen = 3.5f
                    if (stemUp) {
                        stemX = headX + headW - px(0.07f)
                        val startY = sys.yForStep(minStep, s) - px(0.2f)
                        stemEndY = min(sys.yForStep(maxStep, s) - px(stemLen), sys.yForStep(4, s))
                        vline(stemX, startY, stemEndY, 0.12f)
                        if (e.value.flags > 0) glyph(G.FLAG_8_UP + 2 * (e.value.flags - 1), stemX - px(0.06f), stemEndY)
                    } else {
                        stemX = headX + px(0.07f)
                        val startY = sys.yForStep(maxStep, s) + px(0.2f)
                        stemEndY = max(sys.yForStep(minStep, s) + px(stemLen), sys.yForStep(4, s))
                        vline(stemX, startY, stemEndY, 0.12f)
                        if (e.value.flags > 0) glyph(G.FLAG_8_DOWN + 2 * (e.value.flags - 1), stemX - px(0.06f), stemEndY)
                    }
                }
                topY = min(sys.yForStep(maxStep, s) - px(0.5f), if (e.value.hasStem && stemUp) stemEndY else sys.staffTop(s))
                bottomY = max(sys.yForStep(minStep, s) + px(0.5f), if (e.value.hasStem && !stemUp) stemEndY else sys.staffBottom(s))

                // tie
                if (e.tieToNext) {
                    val y = if (stemUp) sys.yForStep(minStep, s) + px(0.8f) else sys.yForStep(maxStep, s) - px(0.8f)
                    val xa = headX + headW + px(0.2f)
                    val xb = nextX - px(0.2f)
                    val bend = if (stemUp) px(1.1f) else -px(1.1f)
                    val path = Path().apply {
                        moveTo(xa, y)
                        cubicTo(xa + (xb - xa) * 0.25f, y + bend, xa + (xb - xa) * 0.75f, y + bend, xb, y)
                        cubicTo(xa + (xb - xa) * 0.75f, y + bend * 0.7f, xa + (xb - xa) * 0.25f, y + bend * 0.7f, xa, y)
                        close()
                    }
                    ds.drawPath(path, c.ink)
                    ds.drawPath(path, c.ink, style = Stroke(px(0.05f)))
                }
            }
        }

        // triplet
        if (e.triplet) {
            val y = if (e.kind == EventKind.NOTE && !stemUp) bottomY + px(1.6f) else topY - px(0.6f)
            glyph(G.TUPLET_3, headX, y, scale = 0.8f)
            if (stemUp || e.kind != EventKind.NOTE) topY -= px(1.6f) else bottomY += px(1.6f)
        }

        // attached marks
        var above = min(topY - px(0.8f), sys.staffTop(s) - px(0.8f))
        var below = max(bottomY + px(2.2f), sys.staffBottom(s) + px(2.6f))
        for (id in e.marks) {
            val def = Marks[id] ?: continue
            val cp = def.codepoint ?: continue
            val w = glyphWidth(cp)
            when (def.placement) {
                MarkPlacement.ABOVE -> {
                    glyph(cp, headX + headW / 2 - w / 2, above)
                    above -= GlyphMetrics[cp].height * s + px(0.6f)
                }
                MarkPlacement.BELOW -> {
                    glyph(cp, headX, below)
                    below += GlyphMetrics[cp].height * s + px(0.6f)
                }
                MarkPlacement.LEFT -> {
                    val mid = (topY + bottomY) / 2
                    glyph(cp, headX - px(1.4f), mid + px(0.5f), scale = if (id.startsWith("grace")) 0.7f else 1f)
                }
            }
        }

        // lyric
        e.lyric?.let {
            text(it, headX + headW / 2, sys.staffBottom(s) + px(5.3f), 1.4f, alignCenter = true, serif = false)
        }
    }
}
