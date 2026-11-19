package uy.gero.marea.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** true cuando la pantalla vive dentro de un panel que ya empieza debajo de la barra de estado (Fold abierto). */
val LocalInPanel = staticCompositionLocalOf { false }

/** Deja lugar para la hora, notificaciones y batería, salvo que el panel ya lo haga. */
@Composable
fun Modifier.statusBarsPad(): Modifier = if (LocalInPanel.current) this else this.windowInsetsPadding(WindowInsets.statusBars)
