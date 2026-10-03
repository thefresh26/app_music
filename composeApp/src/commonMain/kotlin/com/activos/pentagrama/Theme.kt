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

/** "Cuaderno de música": papel cálido, tinta, bermellón de lápiz de corrección y resaltador amarillo. */
object Paper {
    val Highlighter = Color(0xFFF2DE7A)
    /** Salida rápida, llegada suave: la curva de todas las animaciones. */
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

val LightColors = lightColorScheme(
    primary = Color(0xFFC2402F), onPrimary = Color(0xFFFFFCF6),
    primaryContainer = Color(0xFFF5DDD5), onPrimaryContainer = Color(0xFF5A140C),
    secondary = Color(0xFF1E1B16), onSecondary = Color(0xFFFFFCF6),
    secondaryContainer = Color(0xFF1E1B16), onSecondaryContainer = Color(0xFFFFFCF6),
    tertiary = Color(0xFF3E4756), tertiaryContainer = Color(0xFFECE5D6), onTertiaryContainer = Color(0xFF1E1B16),
    background = Color(0xFFF4EEE2), onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFCF6), onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFF4EEE2), onSurfaceVariant = Color(0xFF6A6257),
    surfaceContainerLowest = Color(0xFFFFFEFB), surfaceContainerLow = Color(0xFFFFFCF6),
    surfaceContainer = Color(0xFFFAF5EC), surfaceContainerHigh = Color(0xFFF4EEE2), surfaceContainerHighest = Color(0xFFECE5D6),
    outline = Color(0xFFC9BEA9), outlineVariant = Color(0xFFE3D9C6),
    error = Color(0xFFB3261E), surfaceTint = Color.Transparent,
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFF08A72), onPrimary = Color(0xFF3A0D06),
    primaryContainer = Color(0xFF5C2219), onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = Color(0xFFEEE7D9), onSecondary = Color(0xFF17150F),
    secondaryContainer = Color(0xFFEEE7D9), onSecondaryContainer = Color(0xFF17150F),
    tertiary = Color(0xFFB9C3D3), tertiaryContainer = Color(0xFF2C2922), onTertiaryContainer = Color(0xFFEEE7D9),
    background = Color(0xFF17150F), onBackground = Color(0xFFEEE7D9),
    surface = Color(0xFF221F19), onSurface = Color(0xFFEEE7D9),
    surfaceVariant = Color(0xFF2C2922), onSurfaceVariant = Color(0xFFB5AC9C),
    surfaceContainerLowest = Color(0xFF12100B), surfaceContainerLow = Color(0xFF1C1A14),
    surfaceContainer = Color(0xFF221F19), surfaceContainerHigh = Color(0xFF2C2922), surfaceContainerHighest = Color(0xFF37332B),
    outline = Color(0xFF5A5347), outlineVariant = Color(0xFF3A352D),
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
