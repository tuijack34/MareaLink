package uy.gero.marea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uy.gero.marea.BuildConfig
import uy.gero.marea.MareaApp

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val store = MareaApp.instance.settings
    val s by store.state.collectAsState()
    var picker by remember { mutableStateOf<String?>(null) }
    var needsRestart by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { BigHeader("Ajustes", "Marea ${BuildConfig.VERSION_NAME}") }

        item { GroupLabel("Reproducción en TIDAL") }
        item {
            val st by vm.player.state.collectAsState()
            GroupCard {
                SettingRow(
                    "Acceso a notificaciones",
                    if (st.needsAccess) "Falta: sin esto Marea Link no ve ni controla TIDAL" else "Concedido",
                    Icons.Default.Notifications, onClick = { openListenerSettings(ctx) },
                )
                SettingRow(
                    "Conexión con TIDAL",
                    if (st.tidalConnected) "Conectado a la app de TIDAL" else "TIDAL no está abierta (tocá para abrirla)",
                    Icons.Default.Link, onClick = { vm.player.openTidalApp() },
                )
                SettingRow("Cómo pedir los temas", s.playMethod.label + " — " + s.playMethod.detail, Icons.Default.PlayCircle, onClick = { picker = "method" })
                SwitchRow("La cola la maneja Marea", "Marea manda el siguiente tema cuando termina el actual. Apagado: sigue la cola/autoplay de TIDAL", s.mareaQueue, Icons.AutoMirrored.Filled.QueueMusic) {
                    store.update { st2 -> st2.copy(mareaQueue = it) }
                }
                SwitchRow("Sugeridas automáticas", "Cuando se termina la cola, sigue con música parecida", s.autoSuggestions, Icons.Default.AutoAwesome) {
                    store.update { st2 -> st2.copy(autoSuggestions = it) }
                }
                SwitchRow("Álbumes y playlists directo a TIDAL", "Arranca al instante y TIDAL maneja la cola completa (si TIDAL acepta links)", s.containerToTidal, Icons.Default.Bolt) {
                    store.update { st2 -> st2.copy(containerToTidal = it) }
                }
                SwitchRow("Letras de LRCLIB", "Cuando TIDAL no da la letra, la busca en LRCLIB (base abierta de letras sincronizadas)", s.lrclib, Icons.Default.Lyrics) {
                    store.update { st2 -> st2.copy(lrclib = it) }
                }
                SwitchRow(
                    "Botones de los auriculares",
                    "Siguiente/anterior de auriculares, reloj o pantalla de bloqueo siguen tu cola de Marea. Reproduce silencio de fondo para recibirlos (agrega un control de música más en las notificaciones)",
                    s.headsetBridge, Icons.Default.Headphones,
                ) { on ->
                    store.update { st2 -> st2.copy(headsetBridge = on) }
                    if (on) uy.gero.marea.HeadsetService.start(ctx) else uy.gero.marea.HeadsetService.stop(ctx)
                }
                SettingRow("Laboratorio", "Probá qué método funciona con tu TIDAL", Icons.Default.Science, onClick = { vm.labOpen = true })
            }
        }

        item { GroupLabel("Apariencia") }
        item {
            GroupCard {
                SettingRow("Color de acento", Accents.getOrNull(s.accent)?.first, Icons.Default.Palette)
                Row(Modifier.fillMaxWidth().padding(start = 70.dp, end = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accents.forEachIndexed { i, (_, c) ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c)
                                .then(if (i == s.accent) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                .clickable { store.update { it.copy(accent = i) } },
                        )
                    }
                }
                SwitchRow("Colores de la portada", "El reproductor toma el color del disco que suena", s.coverColors, Icons.Default.GraphicEq) {
                    store.update { st -> st.copy(coverColors = it) }
                }
                SwitchRow("Negro puro", "Fondo totalmente negro (ahorra batería en AMOLED)", s.pureBlack, Icons.Default.Contrast) {
                    store.update { st -> st.copy(pureBlack = it) }
                }
                SwitchRow("Panel de reproducción lateral", "Con el Fold abierto, deja el reproductor y la cola fijos a la derecha", s.showQueuePane, Icons.Default.ViewSidebar) {
                    store.update { st -> st.copy(showQueuePane = it) }
                }
                SettingRow("Personalizar widget", "Transparencia, vidrio, tono, texto y botones", Icons.Default.Widgets, onClick = {
                    ctx.startActivity(android.content.Intent(ctx, uy.gero.marea.WidgetConfigActivity::class.java))
                })
                SettingRow("Tamaño de la grilla", listOf("Chica", "Normal", "Grande")[s.gridSize.coerceIn(0, 2)], Icons.Default.GridView, onClick = { picker = "grid" })
            }
        }

        item { GroupLabel("Cuenta") }
        item {
            GroupCard {
                SettingRow("Diagnóstico", "Errores y eventos internos, para reportar problemas", Icons.Default.BugReport, onClick = { vm.diagnosticsOpen = true })
                SettingRow("Cerrar sesión", "Desconecta tu cuenta de TIDAL de Marea", Icons.AutoMirrored.Filled.Logout, onClick = { vm.logout() })
            }
        }
        item {
            Text(
                "Marea Link no reproduce audio: le pide a la app oficial de TIDAL que reproduzca y la controla. La calidad y las descargas son las de tu app de TIDAL.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (needsRestart) {
        AlertDialog(
            onDismissRequest = { needsRestart = false },
            confirmButton = { TextButton(onClick = { needsRestart = false }) { Text("Entendido") } },
            title = { Text("Reiniciá la app") },
            text = { Text("Este cambio se aplica la próxima vez que abras Marea (cerrala desde recientes).") },
        )
    }

    when (picker) {
        "method" -> ChoiceDialog("Cómo pedir los temas", uy.gero.marea.PlayMethod.entries, s.playMethod, { it.label }, { m -> store.update { it.copy(playMethod = m) } }, { picker = null })
        "grid" -> ChoiceDialog("Tamaño de la grilla", listOf(0, 1, 2), s.gridSize, { listOf("Chica", "Normal", "Grande")[it] }, { g -> store.update { it.copy(gridSize = g) } }, { picker = null })
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onPick(o); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = o == selected, onClick = { onPick(o); onDismiss() })
                        Text(label(o))
                    }
                }
            }
        },
    )
}


fun openListenerSettings(ctx: android.content.Context) {
    val comp = android.content.ComponentName(ctx, uy.gero.marea.MareaNotificationListener::class.java)
    val detail = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, comp.flattenToString())
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(detail) }.onFailure {
        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openAppInfo(ctx: android.content.Context) {
    ctx.startActivity(
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
