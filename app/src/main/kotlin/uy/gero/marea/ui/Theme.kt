package uy.gero.marea.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

val Accents = listOf(
    "Agua" to Color(0xFF5EEAD4),
    "Violeta" to Color(0xFFA78BFA),
    "Ámbar" to Color(0xFFFBBF24),
    "Rosa" to Color(0xFFF472B6),
    "Cielo" to Color(0xFF60A5FA),
)

@Composable
fun MareaTheme(accentIndex: Int, pureBlack: Boolean, content: @Composable () -> Unit) {
    val accent = Accents.getOrElse(accentIndex) { Accents[0] }.second
    val bg = if (pureBlack) Color.Black else Color(0xFF07090C)
    val surface = if (pureBlack) Color(0xFF0A0A0A) else Color(0xFF0E1217)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF04110F),
        primaryContainer = lerp(surface, accent, 0.22f),
        onPrimaryContainer = Color.White,
        secondary = lerp(accent, Color.White, 0.35f),
        background = bg,
        onBackground = Color(0xFFE9EDF1),
        surface = bg,
        onSurface = Color(0xFFE9EDF1),
        surfaceVariant = surface,
        onSurfaceVariant = Color(0xFF9AA4AF),
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = lerp(surface, Color.White, 0.05f),
        surfaceContainerHighest = lerp(surface, Color.White, 0.08f),
        outline = Color(0xFF2A323B),
        outlineVariant = Color(0xFF1C232A),
    )
    val base = Typography()
    val typography = base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/** Color predominante de una portada, animado. Si está apagado o falla, devuelve [fallback]. */
@Composable
fun rememberCoverColor(url: String?, enabled: Boolean, fallback: Color): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(fallback) }
    LaunchedEffect(url, enabled) {
        if (!enabled || url == null) { target = fallback; return@LaunchedEffect }
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build(),
        )
        val bmp = result.image?.toBitmap() ?: return@LaunchedEffect
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        val sw = p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch
        target = sw?.let { Color(it.rgb) } ?: fallback
    }
    val c by animateColorAsState(target, tween(700), label = "cover")
    return c
}

fun Color.darken(f: Float) = lerp(this, Color.Black, f)
fun Color.argb() = toArgb()
