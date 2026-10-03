package com.activos.pentagrama.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.platform.encodeJpeg

/** Prints a score to A4 pages (raster at 200 dpi, JPEG inside a PDF). */
object ScorePdf {
    const val PAGE_W = 1654 // A4 @ 200 dpi
    const val PAGE_H = 2339

    val printColors = ScoreColors(
        ink = Color(0xFF1F2937), staff = Color(0xFF374151), selection = Color.Transparent, measureSelection = Color.Transparent,
        overfull = Color.Transparent, section = Color(0xFFF1C40F), sectionText = Color.Black, chord = Color.Black,
        paper = Color.White, muted = Color(0xFF555555),
    )

    /** Page boundaries (start/end y in layout coordinates), breaking only between systems. */
    fun pageRanges(layout: ScoreLayout, contentH: Float): List<Pair<Float, Float>> {
        val pages = mutableListOf<Pair<Float, Float>>()
        var start = 0f
        for (sys in layout.systems) {
            val bottom = sys.top + Dim.SYSTEM_HEIGHT * layout.s
            if (bottom - start > contentH && sys.top > start) { pages += start to sys.top; start = sys.top }
        }
        pages += start to maxOf(layout.height, start + 1f)
        return pages
    }

    fun renderPages(
        score: Score, measurer: TextMeasurer, musicFont: FontFamily, density: Density,
        serif: FontFamily = FontFamily.Serif, sans: FontFamily = FontFamily.SansSerif,
    ): List<ImageBitmap> {
        val colors = printColors.copy(serif = serif, sans = sans)
        val margin = PAGE_W * 0.06f
        val s = PAGE_W / 210f * 1.75f // staff space = 1.75 mm
        val layout = ScoreLayout.build(score, PAGE_W.toFloat(), s, marginSpaces = margin / s)
        val contentH = PAGE_H - 2 * margin
        val ranges = pageRanges(layout, contentH)
        return ranges.mapIndexed { i, (start, end) ->
            val img = ImageBitmap(PAGE_W, PAGE_H)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(PAGE_W.toFloat(), PAGE_H.toFloat())) {
                drawRect(Color.White)
                clipRect(top = margin, bottom = margin + (end - start)) {
                    translate(0f, margin - start) {
                        ScorePainter(this, measurer, musicFont, colors, layout, null).drawAll()
                    }
                }
                val footer = measurer.measure(
                    "${score.title} — ${i + 1} / ${ranges.size}",
                    TextStyle(fontSize = with(density) { (s * 1.2f).toSp() }, color = Color(0xFF555555), fontFamily = serif, fontStyle = FontStyle.Italic),
                )
                drawText(footer, topLeft = Offset((PAGE_W - footer.size.width) / 2f, PAGE_H - margin * 0.6f))
            }
            img
        }
    }

    fun export(
        score: Score, measurer: TextMeasurer, musicFont: FontFamily, density: Density,
        serif: FontFamily = FontFamily.Serif, sans: FontFamily = FontFamily.SansSerif,
    ): ByteArray =
        PdfWriter.write(renderPages(score, measurer, musicFont, density, serif, sans).map { Triple(encodeJpeg(it, 88), it.width, it.height) })
}
