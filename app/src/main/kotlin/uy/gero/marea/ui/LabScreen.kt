package uy.gero.marea.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uy.gero.marea.DiagLog
import uy.gero.marea.PlayMethod

/** Pantalla para descubrir qué forma de pedirle temas a TIDAL funciona en este teléfono. */
@Composable
fun LabScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val lines by DiagLog.lines.collectAsState()
    val st by vm.player.state.collectAsState()
    var info by remember { mutableStateOf("") }
    LaunchedEffect(st.tidalConnected, st.needsAccess) {
        while (true) { info = vm.player.labInfo(); delay(1500) }
    }
    val test = vm.tracks.items.firstOrNull { it.id != st.current?.id } ?: vm.tracks.items.firstOrNull()
    LaunchedEffect(Unit) { vm.tracks.ensureStarted() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { vm.labOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
            Text("Laboratorio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                GroupLabel("1 · Permiso y conexión")
                GroupCard {
                    Text(info, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (st.needsAccess) Button(onClick = { openListenerSettings(ctx) }) { Text("Dar acceso") }
                        FilledTonalButton(onClick = { vm.player.openTidalApp() }) { Text("Abrir TIDAL") }
                        FilledTonalButton(onClick = { vm.player.connect() }) { Text("Reconectar") }
                    }
                }
            }
            item {
                GroupLabel("2 · Probar métodos")
                GroupCard {
                    Text(
                        if (test == null) "Cargando un tema de tus me gusta para probar…" else "Tema de prueba: ${test.title} — ${test.artistLine}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
                    )
                    Text(
                        "Con TIDAL abierta (aunque sea en pausa), probá cada botón y fijate si TIDAL pone ese tema. Después elegí el que anduvo en Ajustes → Cómo pedir los temas.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PlayMethod.MEDIA_ID, PlayMethod.URI, PlayMethod.SEARCH, PlayMethod.DEEPLINK).forEach { m ->
                            FilledTonalButton(onClick = { test?.let { vm.player.labTest(it, m) } }, enabled = test != null) { Text(m.label) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Registro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        val text = "Marea Link\n$info\n" + DiagLog.dump()
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Marea Link", text))
                        vm.message = "Copiado"
                    }) { Text("Copiar todo") }
                }
            }
            items(lines.filter { "[Link]" in it }.takeLast(60)) { l ->
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
            }
        }
    }
}
