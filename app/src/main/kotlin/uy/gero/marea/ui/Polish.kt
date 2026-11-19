package uy.gero.marea.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Brillo que recorre los esqueletos de carga. */
@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-400f, 1400f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 400f, 400f))
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val b = shimmerBrush()
    Column(modifier.padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(b))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(b))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(b))
    }
}

@Composable
fun SkeletonRow() {
    val b = shimmerBrush()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(b))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(b))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(b))
        }
    }
}

/** Ecualizador animado para marcar el tema que suena. Se congela en pausa. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(18.dp)) {
    val t = rememberInfiniteTransition(label = "eq")
    val speeds = listOf(520, 380, 610)
    val heights = speeds.map { ms ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b$ms")
    }
    Canvas(modifier) {
        val w = size.width / 5f
        heights.forEachIndexed { i, h ->
            val frac = if (playing) h.value else 0.3f + 0.15f * i
            val bh = size.height * frac
            drawRoundRect(color, Offset(w * (i * 2f), size.height - bh), Size(w, bh), CornerRadius(w / 2, w / 2))
        }
    }
}

/** Fondo "vivo": manchas de color de la portada que se mueven despacio (estilo reproductor moderno). */
@Composable
fun AnimatedCoverBackground(color: Color, modifier: Modifier = Modifier, intensity: Float = 1f) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val bg = MaterialTheme.colorScheme.background
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        drawRect(bg)
        val r = size.maxDimension * 0.75f
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.75f * intensity), Color.Transparent), center = Offset(size.width * (0.15f + 0.6f * a), size.height * (0.1f + 0.3f * b)), radius = r),
            radius = r, center = Offset(size.width * (0.15f + 0.6f * a), size.height * (0.1f + 0.3f * b)),
        )
        val c2 = color.darken(0.35f)
        drawCircle(
            Brush.radialGradient(listOf(c2.copy(alpha = 0.6f * intensity), Color.Transparent), center = Offset(size.width * (0.9f - 0.5f * b), size.height * (0.55f + 0.3f * a)), radius = r * 0.9f),
            radius = r * 0.9f, center = Offset(size.width * (0.9f - 0.5f * b), size.height * (0.55f + 0.3f * a)),
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.55f), bg), startY = size.height * 0.45f))
    }
}

@Composable
fun SkeletonGridRow(columns: Int = 2, itemWidth: Dp? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(columns) { SkeletonCard(if (itemWidth != null) Modifier.width(itemWidth) else Modifier.weight(1f)) }
    }
}
