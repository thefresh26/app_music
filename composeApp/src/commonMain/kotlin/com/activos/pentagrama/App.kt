package com.activos.pentagrama

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
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

private val Light = lightColorScheme(
    primary = Color(0xFF2F4B8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0E1F4D),
    secondary = Color(0xFF6A5F2E),
    secondaryContainer = Color(0xFFF6E7A6),
    tertiary = Color(0xFF3F7A57),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E8EF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF142B60),
    primaryContainer = Color(0xFF2C3F72),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFD9CB8C),
    secondaryContainer = Color(0xFF514718),
    tertiary = Color(0xFF9FD4B1),
    background = Color(0xFF121317),
    surface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFF2A2C33),
)

sealed interface Screen {
    data object Library : Screen
    data class Editor(val state: EditorState) : Screen
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
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
                when (val sc = screen) {
                    Screen.Library -> LibraryScreen(onOpen = { id: String, score: Score ->
                        screen = Screen.Editor(EditorState(score, id))
                    })
                    is Screen.Editor -> EditorScreen(sc.state, onBack = { screen = Screen.Library })
                }
            }
        }
    }
}
