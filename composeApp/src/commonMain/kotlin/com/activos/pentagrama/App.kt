package com.activos.pentagrama

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.activos.pentagrama.editor.EditorState
import com.activos.pentagrama.generated.resources.Res
import com.activos.pentagrama.generated.resources.bravura
import com.activos.pentagrama.model.Score
import com.activos.pentagrama.symbols.SmuflGlyph
import com.activos.pentagrama.ui.EditorScreen
import com.activos.pentagrama.ui.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

val LocalMusicFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalSmufl = staticCompositionLocalOf<List<SmuflGlyph>> { emptyList() }

sealed interface Screen {
    data object Library : Screen
    data class Editor(val state: EditorState) : Screen
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = paperTypography(), shapes = PaperShapes) {
        val music = FontFamily(Font(Res.font.bravura))
        var smufl by remember { mutableStateOf<List<SmuflGlyph>?>(null) }
        LaunchedEffect(Unit) {
            smufl = withContext(Dispatchers.Default) {
                runCatching { SmuflGlyph.parseTsv(Res.readBytes("files/smufl.tsv").decodeToString()) }.getOrDefault(emptyList())
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val glyphs = smufl
            if (glyphs == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            CompositionLocalProvider(LocalMusicFont provides music, LocalSmufl provides glyphs) {
                var screen by remember { mutableStateOf<Screen>(Screen.Library) }
                // Pasar de página: el editor entra desde la derecha y la biblioteca vuelve desde la izquierda.
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState is Screen.Editor
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(380, easing = Paper.Ease)) { dir * it / 6 } + fadeIn(tween(260, 60)))
                            .togetherWith(slideOutHorizontally(tween(320, easing = Paper.Ease)) { -dir * it / 10 } + fadeOut(tween(180)))
                    },
                    contentKey = { it::class },
                ) { sc ->
                    when (sc) {
                        Screen.Library -> LibraryScreen(onOpen = { id: String, score: Score ->
                            screen = Screen.Editor(EditorState(score, id))
                        })
                        is Screen.Editor -> EditorScreen(sc.state, onBack = { screen = Screen.Library })
                    }
                }
            }
        }
    }
}
