package uy.gero.marea.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import uy.gero.marea.PlayerUiState

/** Cómo está doblado el Fold ahora. */
data class FoldPosture(
    /** Medio abierto con la bisagra horizontal (apoyado tipo laptop / "modo Flex"). */
    val tabletop: Boolean = false,
    /** Medio abierto con la bisagra vertical (como un libro). */
    val book: Boolean = false,
    /** Dónde empieza y termina la bisagra (en dp, desde arriba o desde la izquierda). */
    val hingeStart: Dp = 0.dp,
    val hingeEnd: Dp = 0.dp,
)

@Composable
fun rememberFoldPosture(): FoldPosture {
    val activity = LocalContext.current as? Activity ?: return FoldPosture()
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by tracker.collectAsState(initial = null)
    val density = LocalDensity.current
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return FoldPosture()
    if (fold.state != FoldingFeature.State.HALF_OPENED) return FoldPosture()
    val b = fold.bounds
    return with(density) {
        if (fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
            FoldPosture(tabletop = true, hingeStart = b.top.toDp(), hingeEnd = b.bottom.toDp())
        } else {
            FoldPosture(book = true, hingeStart = b.left.toDp(), hingeEnd = b.right.toDp())
        }
    }
}

/**
 * Modo "doblado a 90°" (como apoyar una laptop): arriba la portada o la letra,
 * abajo los controles y lo que sigue, cada mitad de un lado de la bisagra.
 */
@Composable
fun TabletopPlayer(st: PlayerUiState, vm: MainViewModel, actions: TrackActions, coverColors: Boolean, posture: FoldPosture) {
    val t = st.current ?: return
    val accent = rememberCoverColor(t.coverLarge ?: t.cover, coverColors, MaterialTheme.colorScheme.primary)
    var top by rememberSaveable { mutableStateOf(0) } // 0 portada, 1 letra, 2 cola
    // Entrada en dos tiempos: la mitad de arriba "baja" desde la bisagra y los controles "suben"
    val upper = remember { Animatable(0f) }
    val lower = remember { Animatable(0f) }
    // y la portada aparece con un pequeño rebote
    val coverPop = remember { Animatable(0.82f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { upper.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 180f)) }
        launch { delay(120); coverPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
        delay(90)
        lower.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 200f))
    }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        AnimatedCoverBackground(accent)
        // brillo sutil sobre la bisagra
        Box(
            Modifier.fillMaxWidth().height(posture.hingeEnd - posture.hingeStart + 40.dp)
                .graphicsLayer { translationY = with(density) { (posture.hingeStart - 20.dp).toPx() }; alpha = 0.55f * lower.value }
                .background(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize()) {
            // ---- Mitad de arriba (la que queda "parada") ----
            Box(
                Modifier.fillMaxWidth().height(posture.hingeStart).statusBarsPad().padding(horizontal = 24.dp, vertical = 12.dp)
                    .graphicsLayer {
                        val p = upper.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * with(density) { 60.dp.toPx() }
                        scaleX = 0.92f + 0.08f * p; scaleY = 0.92f + 0.08f * p
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
            ) {
                AnimatedContent(
                    top,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(180)))
                    },
                    label = "top",
                ) { mode ->
                    when (mode) {
                        1 -> LyricsView(t, st, vm, accent)
                        2 -> QueueView(st, vm, actions)
                        else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            GestureCover(
                                Modifier.fillMaxHeight().aspectRatio(1f).graphicsLayer { scaleX = coverPop.value; scaleY = coverPop.value },
                                onNext = { vm.player.next() }, onPrevious = { vm.player.previous() },
                                onLongPress = { vm.player.state.value.current?.let { vm.sheetTrack = it } }, onDoubleTap = { if (!vm.isFavorite(t)) vm.toggleFavorite(t) },
                            ) { BreathingCover(t, st.isPlaying, Modifier.fillMaxSize()) }
                            Spacer(Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                TrackTitleBlock(t, vm, accent)
                                Spacer(Modifier.height(8.dp))
                                StreamQuality(st)
                            }
                        }
                    }
                }
            }
            // ---- La bisagra ----
            Spacer(Modifier.fillMaxWidth().height(posture.hingeEnd - posture.hingeStart))
            // ---- Mitad de abajo (la que queda apoyada): controles ----
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp, vertical = 12.dp)
                    .graphicsLayer {
                        val p = lower.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * with(density) { 80.dp.toPx() }
                    },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.widthIn(max = 720.dp)) {
                    Column {
                        if (top != 0) {
                            Text(t.fullTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(t.artistLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        SeekBar(st, vm.player, accent)
                        Controls(st, vm.player, accent)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ModeBtn(Icons.Default.Album, "Portada", top == 0, accent) { top = 0 }
                            ModeBtn(Icons.Default.Lyrics, "Letra", top == 1, accent) { top = 1 }
                            ModeBtn(Icons.AutoMirrored.Filled.QueueMusic, "Cola", top == 2, accent) { top = 2 }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(animateColorAsState(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent, label = "mode").value)
            .then(Modifier.padding(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = if (selected) accent else MaterialTheme.colorScheme.onSurface) }
        Text(label, color = if (selected) accent else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
    }
}
