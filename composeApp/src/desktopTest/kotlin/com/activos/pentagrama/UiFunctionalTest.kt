package com.activos.pentagrama

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.runComposeUiTest
import com.activos.pentagrama.model.Templates
import com.activos.pentagrama.render.ScoreLayout
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pruebas de funcionamiento de la interfaz: abren la app real, navegan y usan el editor como lo haría una persona.
 * Guardan capturas en composeApp/build/test-screenshots (se publican como artefacto en GitHub Actions).
 */
@OptIn(ExperimentalTestApi::class)
class UiFunctionalTest {
    private val outDir = File("build/test-screenshots").apply { mkdirs() }

    /** Captura cada ventana/diálogo abierto (hay una raíz por diálogo). */
    private fun ComposeUiTest.snap(name: String) {
        waitForIdle()
        val roots = onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        for (i in 0 until count) {
            val suffix = if (count > 1) "_$i" else ""
            ImageIO.write(roots[i].captureToImage().toAwtImage(), "png", File(outDir, "$name$suffix.png"))
        }
    }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = true) =
        waitUntil(timeoutMillis = 30_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun abrirEjemploBuscarFiguraYColocarNota() = runComposeUiTest {
        setContent { App() }

        // 1. Pantalla de inicio
        waitForText("Mis partituras")
        onNodeWithText("Nueva partitura").assertExistsCompat()
        onNodeWithText("Desde MP3").assertExistsCompat()
        snap("01_inicio")

        // 2. Abrir el ejemplo de cifrado
        onNodeWithText("Ver ejemplo").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("score").fetchSemanticsNodes().isNotEmpty() }
        waitForText("compases")
        snap("02_editor_ejemplo")

        // 3. Buscar una figura sin tilde
        onNode(hasSetTextAction()).performTextInput("calderon")
        waitForText("Calderón largo")
        onNodeWithText("Calderón").assertExistsCompat()
        snap("03_busqueda_calderon")
        onNode(hasSetTextAction()).performTextClearance()

        // 4. Tocar el pentagrama con la herramienta "Negra" (la predeterminada) sobre el primer tiempo
        val node = onNodeWithTag("score").fetchSemanticsNode()
        val widthPx = node.size.width.toFloat()
        val d = density.density
        val s = (if (widthPx / d > 700f) 8.5f else 6.5f) * d
        val layout = ScoreLayout.build(Templates.demoChart(), widthPx, s)
        val sys = layout.systems.first()
        val first = sys.measures.first().events.first()
        onNodeWithTag("score").performTouchInput { click(Offset(first.x + s, sys.yForStep(2, s))) }
        waitForText("•") // el título muestra • cuando hay cambios sin guardar
        snap("04_nota_colocada")

        // 5. Guardar y volver al inicio: la partitura aparece en la lista
        onNodeWithContentDescriptionCompat("Guardar").performClick()
        waitForText("Partitura guardada")
        onNodeWithContentDescriptionCompat("Volver").performClick()
        waitForText("Mis partituras")
        waitForText("No lo hay")
        snap("05_biblioteca_con_partitura")
    }

    @Test
    fun crearPartituraNueva() = runComposeUiTest {
        setContent { App() }
        waitForText("Mis partituras")
        onNodeWithText("Nueva partitura").performClick()
        waitForText("Plantilla")
        onNode(hasSetTextAction()).performTextInput("Rey de Reyes")
        snap("06_dialogo_nueva")
        onNodeWithText("Crear").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("score").fetchSemanticsNodes().isNotEmpty() }
        waitForText("Rey de Reyes")
        snap("07_partitura_nueva")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.onNodeWithContentDescriptionCompat(label: String) =
    onNode(androidx.compose.ui.test.hasContentDescription(label))
