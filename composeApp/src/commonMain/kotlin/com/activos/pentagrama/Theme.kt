package com.activos.pentagrama

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.activos.pentagrama.generated.resources.Res
import com.activos.pentagrama.generated.resources.dmsans_medium
import com.activos.pentagrama.generated.resources.dmsans_regular
import com.activos.pentagrama.generated.resources.dmsans_semibold
import com.activos.pentagrama.generated.resources.fraunces_bold
import com.activos.pentagrama.generated.resources.fraunces_italic
import com.activos.pentagrama.generated.resources.fraunces_semibold
import org.jetbrains.compose.resources.Font

/** Paleta: #F5F3FF lavanda (fondo) · #1F2937 pizarra (texto) · #9F1239 carmesí (acción) · #4C0519 vino (selección) · #F1C40F oro (resaltador). */
object Paper {
    val Highlighter = Color(0xFFF1C40F)
    /** Salida rápida, llegada suave: la curva de todas las animaciones. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

val LightColors = lightColorScheme(
    primary = Color(0xFF9F1239), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE7EE), onPrimaryContainer = Color(0xFF4C0519),
    secondary = Color(0xFF4C0519), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4C0519), onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFF1C40F), tertiaryContainer = Color(0xFFECE9FA), onTertiaryContainer = Color(0xFF1F2937),
    background = Color(0xFFF5F3FF), onBackground = Color(0xFF1F2937),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF5F3FF), onSurfaceVariant = Color(0xFF5B6472),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF9F8FF), surfaceContainerHigh = Color(0xFFF5F3FF), surfaceContainerHighest = Color(0xFFECE9FA),
    outline = Color(0xFFC7C2E0), outlineVariant = Color(0xFFE4E0F5),
    error = Color(0xFFB3261E), surfaceTint = Color.Transparent,
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFF1C40F), onPrimary = Color(0xFF1F2937),
    primaryContainer = Color(0xFF4C0519), onPrimaryContainer = Color(0xFFFCE7EE),
    secondary = Color(0xFFF5F3FF), onSecondary = Color(0xFF1F2937),
    secondaryContainer = Color(0xFFF5F3FF), onSecondaryContainer = Color(0xFF1F2937),
    tertiary = Color(0xFFF1C40F), tertiaryContainer = Color(0xFF2F3B50), onTertiaryContainer = Color(0xFFF5F3FF),
    background = Color(0xFF1F2937), onBackground = Color(0xFFF5F3FF),
    surface = Color(0xFF273346), onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Color(0xFF2F3B50), onSurfaceVariant = Color(0xFFB8BDCB),
    surfaceContainerLowest = Color(0xFF18202C), surfaceContainerLow = Color(0xFF232E3E),
    surfaceContainer = Color(0xFF273346), surfaceContainerHigh = Color(0xFF2F3B50), surfaceContainerHighest = Color(0xFF3A475D),
    outline = Color(0xFF556075), outlineVariant = Color(0xFF374151),
    surfaceTint = Color.Transparent,
)

val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun serifFamily() = FontFamily(
    Font(Res.font.fraunces_semibold, FontWeight.SemiBold),
    Font(Res.font.fraunces_bold, FontWeight.Bold),
    Font(Res.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)

@Composable
fun paperTypography(): Typography {
    val serif = serifFamily()
    val sans = FontFamily(
        Font(Res.font.dmsans_regular, FontWeight.Normal),
        Font(Res.font.dmsans_medium, FontWeight.Medium),
        Font(Res.font.dmsans_semibold, FontWeight.SemiBold),
    )
    val base = Typography()
    fun TextStyle.sans(w: FontWeight = fontWeight ?: FontWeight.Normal) = copy(fontFamily = sans, fontWeight = w)
    fun TextStyle.serif(size: Int, w: FontWeight = FontWeight.SemiBold) =
        copy(fontFamily = serif, fontWeight = w, fontSize = size.sp, letterSpacing = (-0.015).em)
    return Typography(
        displayLarge = base.displayLarge.serif(52), displayMedium = base.displayMedium.serif(44), displaySmall = base.displaySmall.serif(36),
        headlineLarge = base.headlineLarge.serif(34), headlineMedium = base.headlineMedium.serif(28), headlineSmall = base.headlineSmall.serif(24),
        titleLarge = base.titleLarge.serif(22), titleMedium = base.titleMedium.sans(FontWeight.SemiBold), titleSmall = base.titleSmall.sans(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.sans(), bodyMedium = base.bodyMedium.sans(), bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(FontWeight.SemiBold), labelMedium = base.labelMedium.sans(FontWeight.Medium), labelSmall = base.labelSmall.sans(FontWeight.Medium),
    )
}

/** Subtítulo en itálica, como anotación a lápiz. */
@Composable
fun italicNote() = TextStyle(fontFamily = serifFamily(), fontStyle = FontStyle.Italic, fontSize = 17.sp)

/** Rótulo pequeño en versalitas espaciadas ("PLANTILLA", "CLAVE"). */
val Eyebrow = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em)

/** Entrada escalonada: el elemento sube 14dp y aparece; `index` retrasa 45 ms por posición. */
fun androidx.compose.ui.Modifier.riseIn(index: Int = 0): androidx.compose.ui.Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index.coerceAtMost(10))
        p.animateTo(1f, tween(420, easing = Paper.Ease))
    }
    val d = LocalDensity.current
    graphicsLayer { alpha = p.value; translationY = (1f - p.value) * with(d) { 14.dp.toPx() } }
}

/** Se hunde un poco al presionarlo, como una tecla. */
fun androidx.compose.ui.Modifier.pressScale(source: MutableInteractionSource): androidx.compose.ui.Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f))
    graphicsLayer { scaleX = sc; scaleY = sc }
}
