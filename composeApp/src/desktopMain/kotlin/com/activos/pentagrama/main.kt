package com.activos.pentagrama

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.activos.pentagrama.generated.resources.Res
import com.activos.pentagrama.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pentagrama",
        icon = painterResource(Res.drawable.app_icon),
        state = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)),
    ) {
        // Por debajo de esto la paleta lateral y la partitura no caben juntas.
        LaunchedEffect(Unit) { window.minimumSize = Dimension(420, 640) }
        App()
    }
}
