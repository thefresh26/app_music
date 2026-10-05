package com.activos.pentagrama

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Abre la app con el tamaño de pantalla de un celular (412 × 892 dp, como un Android típico)
 * y guarda capturas en build/test-screenshots/movil_*.png para revisar cómo se ve en móvil.
 */
@OptIn(ExperimentalTestApi::class)
class MobileScreenshotsTest {
    private val outDir = File("build/test-screenshots").apply { mkdirs() }

    private fun ComposeUiTest.snap(name: String) {
        waitForIdle()
        val roots = onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        for (i in 0 until count) {
            val suffix = if (count > 1) "_$i" else ""
            ImageIO.write(roots[i].captureToImage().toAwtImage(), "png", File(outDir, "movil_$name$suffix.png"))
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun pantallasEnTamanoCelular() = runDesktopComposeUiTest(width = 412, height = 892) {
        setContent { App() }
        waitForText("Mis partituras")
        snap("1_inicio")

        onNodeWithText("Ver ejemplo").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("score").fetchSemanticsNodes().isNotEmpty() }
        waitForText("compases")
        snap("2_editor")

        onNode(hasSetTextAction()).performTextInput("clave")
        waitForText("Clave de Fa")
        snap("3_buscador")
    }

    @Test
    fun dialogoNuevaPartituraEnCelular() = runDesktopComposeUiTest(width = 412, height = 892) {
        setContent { App() }
        waitForText("Mis partituras")
        onNodeWithText("Nueva partitura").performClick()
        waitForText("Plantilla")
        snap("4_nueva_partitura")
    }

    /** Como el celular del usuario: modo oscuro y letra grande del sistema (×1.3). */
    @Test
    fun oscuroConLetraGrande() = runDesktopComposeUiTest(width = 412, height = 892) {
        setContent { App(dark = true, fontScale = 1.3f) }
        waitForText("Mis partituras")
        snap("5_oscuro_letra_grande_inicio")

        onNodeWithText("Nueva partitura").performClick()
        waitForText("Plantilla")
        snap("6_oscuro_letra_grande_nueva")
        onNodeWithText("Cancelar").performClick()

        onNodeWithContentDescription("¿Cómo se usa?").performClick()
        waitForText("Elige una figura")
        snap("7_oscuro_letra_grande_ayuda")
        onNodeWithText("Entendido").performClick()

        onNodeWithText("Ver ejemplo").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("score").fetchSemanticsNodes().isNotEmpty() }
        waitForText("compases")
        snap("8_oscuro_letra_grande_editor")
    }
}
