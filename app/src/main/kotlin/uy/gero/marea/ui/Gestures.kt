package uy.gero.marea.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Deslizar horizontal: el contenido sigue al dedo y, si pasa el umbral,
 * dispara la acción y vuelve con un resorte.
 */
fun Modifier.swipeHorizontal(onSwipeLeft: (() -> Unit)?, onSwipeRight: (() -> Unit)?, thresholdDp: Int = 72): Modifier = composed {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { thresholdDp.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    // Los callbacks cambian en cada recomposición; si los usáramos como clave, el gesto se cortaría a la mitad.
    val left by androidx.compose.runtime.rememberUpdatedState(onSwipeLeft)
    val right by androidx.compose.runtime.rememberUpdatedState(onSwipeRight)
    val hasLeft = onSwipeLeft != null
    val hasRight = onSwipeRight != null
    this
        .pointerInput(hasLeft, hasRight) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val v = offset.value
                    when {
                        v > threshold -> right?.invoke()
                        v < -threshold -> left?.invoke()
                    }
                    armed = false
                    scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, drag ->
                val allowed = (drag > 0 && hasRight) || (drag < 0 && hasLeft) || offset.value != 0f
                if (!allowed) return@detectHorizontalDragGestures
                change.consume()
                // resistencia: cuanto más lejos, más cuesta
                val damp = 1f / (1f + abs(offset.value) / (threshold * 2.5f))
                scope.launch { offset.snapTo(offset.value + drag * damp) }
                val nowArmed = abs(offset.value) > threshold
                if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                armed = nowArmed
            }
        }
        .graphicsLayer { translationX = offset.value }
}

/** Arrastrar hacia abajo para cerrar (como una hoja). */
fun Modifier.dragDownToDismiss(onDismiss: () -> Unit): Modifier = composed {
    val dismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 140.dp.toPx() }
    this
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (offset.value > threshold) dismiss()
                    scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, drag ->
                if (offset.value + drag < 0f) return@detectVerticalDragGestures
                change.consume()
                scope.launch { offset.snapTo((offset.value + drag).coerceAtLeast(0f)) }
            }
        }
        .graphicsLayer {
            translationY = offset.value
            val f = (offset.value / (threshold * 3)).coerceIn(0f, 0.12f)
            scaleX = 1f - f; scaleY = 1f - f
        }
}

/**
 * Portada con gestos: deslizar para cambiar de tema, doble toque para "me gusta"
 * (con un corazón que aparece), toque simple opcional.
 */
@Composable
fun GestureCover(
    modifier: Modifier,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDoubleTap: () -> Unit,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var hearts by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val dbl by androidx.compose.runtime.rememberUpdatedState(onDoubleTap)
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val long by androidx.compose.runtime.rememberUpdatedState(onLongPress)
    Box(
        modifier
            .swipeHorizontal(onSwipeLeft = onNext, onSwipeRight = onPrevious, thresholdDp = 60)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); dbl(); hearts++ },
                    onTap = { tap?.invoke() },
                    onLongPress = { long?.let { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (hearts > 0) HeartPop(hearts)
    }
}

@Composable
private fun HeartPop(key: Int) {
    val anim = remember(key) { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700))
    }
    val v = anim.value
    val scale = if (v < 0.3f) v / 0.3f * 1.2f else 1.2f - (v - 0.3f) * 0.3f
    val alpha = if (v < 0.7f) 1f else 1f - (v - 0.7f) / 0.3f
    Icon(
        Icons.Default.Favorite, null, tint = Color.White,
        modifier = Modifier.size(96.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
    )
}

/** Fila de tema que se puede deslizar: derecha = a continuación, izquierda = a la cola. */
@Composable
fun SwipeableTrack(onPlayNext: () -> Unit, onAddToQueue: () -> Unit, content: @Composable () -> Unit) {
    var dragging by remember { mutableStateOf(0) }
    Box {
        Row(
            Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                Text("  A continuación", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A la cola  ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).swipeHorizontal(onSwipeLeft = onAddToQueue, onSwipeRight = onPlayNext)) {
            content()
        }
    }
}

/** Deslizar hacia arriba sobre el mini reproductor para abrir el reproductor completo. */
fun Modifier.swipeUp(onSwipeUp: () -> Unit): Modifier = composed {
    val up by androidx.compose.runtime.rememberUpdatedState(onSwipeUp)
    var total by remember { mutableStateOf(0f) }
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total < -threshold) up() },
        ) { change, drag -> change.consume(); total += drag }
    }
}
