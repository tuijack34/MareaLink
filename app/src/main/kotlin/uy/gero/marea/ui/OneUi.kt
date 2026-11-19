package uy.gero.marea.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Lenguaje visual "estilo One UI": título grande que ocupa la parte de arriba
 * (así el contenido queda al alcance del pulgar), contenedores muy redondeados
 * agrupando filas, y una barra chica que aparece al scrollear.
 */

val GroupRadius = 26.dp

@Composable
fun groupColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/** Altura del área de título grande: ~30% de la pantalla en el teléfono cerrado, menos en la pantalla abierta. */
@Composable
fun bigHeaderHeight(): Dp {
    val h = LocalConfiguration.current.screenHeightDp
    val w = LocalConfiguration.current.screenWidthDp
    val frac = if (w >= 700) 0.18f else 0.28f
    return (h * frac).dp.coerceIn(120.dp, 280.dp)
}

@Composable
fun BigHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPad().height(bigHeaderHeight()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom) {
        Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp))
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Barra chica que aparece arriba cuando el título grande ya se fue de pantalla. */
@Composable
fun CollapsedTitleBar(title: String, visible: Boolean, leading: (@Composable () -> Unit)? = null) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background.copy(alpha = 0.92f), Color.Transparent)))
                .statusBarsPad()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** Fondo de una fila dentro de un grupo redondeado (primera/última llevan las esquinas). */
@Composable
fun Modifier.groupItem(index: Int, count: Int, horizontal: Dp = 12.dp): Modifier {
    val top = if (index == 0) GroupRadius else 4.dp
    val bottom = if (index == count - 1) GroupRadius else 4.dp
    return this
        .padding(horizontal = horizontal, vertical = 1.dp)
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(groupColor())
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(GroupRadius)).background(groupColor())) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
fun GroupLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, summary: String?, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) =
    SettingRow(title, summary, icon, onClick = { onChange(!checked) }) { Switch(checked = checked, onCheckedChange = onChange) }

/** Ancho disponible para decidir layouts dentro de un panel. */
@Composable
fun WidthAware(content: @Composable (wide: Boolean) -> Unit) {
    BoxWithConstraints { content(maxWidth >= 560.dp) }
}

/** Achica levemente el elemento mientras se presiona (rebote tipo One UI). No consume el toque. */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "press",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
}


@Composable
fun filledFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
)
