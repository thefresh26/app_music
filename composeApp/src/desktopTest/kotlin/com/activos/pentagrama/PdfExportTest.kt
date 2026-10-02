package com.activos.pentagrama

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.activos.pentagrama.model.Templates
import com.activos.pentagrama.render.ScorePdf
import com.activos.pentagrama.symbols.SmuflGlyph
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exporta el ejemplo a PDF con la fuente real y guarda el PDF y la página 1 como PNG para revisarlos. */
class PdfExportTest {
    @Test
    fun exportaElEjemploAPdf() {
        SmuflGlyph.parseTsv(File("src/commonMain/composeResources/files/smufl.tsv").readText()) // métricas de los glifos
        val bravura = FontFamily(Font("bravura", File("src/commonMain/composeResources/font/bravura.otf").readBytes()))
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val score = Templates.demoChart()
        val out = File("build/test-screenshots").apply { mkdirs() }

        val pages = ScorePdf.renderPages(score, measurer, bravura, density)
        ImageIO.write(pages.first().toAwtImage(), "png", File(out, "pdf_pagina_1.png"))
        val pdf = ScorePdf.export(score, measurer, bravura, density)
        File(out, "partitura_ejemplo.pdf").writeBytes(pdf)

        println("PDF: ${pages.size} página(s), ${pdf.size / 1024} KB")
        assertTrue(pdf.decodeToString(0, 8) == "%PDF-1.4")
        assertTrue(pages.isNotEmpty() && pdf.size > 20_000)
    }
}
