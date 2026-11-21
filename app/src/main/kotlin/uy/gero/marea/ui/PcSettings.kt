package uy.gero.marea.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uy.gero.marea.FoundPc
import uy.gero.marea.PcLink

private const val PC_DOWNLOAD = "https://github.com/tuijack34/MareaLink/releases/latest"

/** Sección "Tu PC" de Ajustes: buscar, emparejar y desconectar Marea PC. */
@Composable
fun PcSettingsCard(vm: MainViewModel) {
    val status by PcLink.status.collectAsState()
    val name by PcLink.pcName.collectAsState()
    val err by PcLink.error.collectAsState()
    val pcState by PcLink.state.collectAsState()
    val uri = LocalUriHandler.current
    var searching by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var codeFor by remember { mutableStateOf<FoundPc?>(null) }

    // Si la PC pide código (primera vez o token vencido), abrimos el diálogo solo
    LaunchedEffect(status) {
        if (status == PcLink.Status.NEEDS_CODE && codeFor == null) {
            val host = PcLink.savedHost ?: return@LaunchedEffect
            codeFor = FoundPc(name ?: host, host)
        }
        if (status == PcLink.Status.CONNECTED && codeFor != null) { codeFor = null; vm.onPc = true; vm.message = "Conectado a ${name ?: "tu PC"}" }
    }

    GroupCard {
        val summary = when (status) {
            PcLink.Status.CONNECTED -> "Conectado a ${name ?: "tu PC"}" + (pcState?.let { if (it.hasSession) " · ${it.title}" else " · nada sonando" } ?: "")
            PcLink.Status.CONNECTING -> "Conectando con ${name ?: "tu PC"}…"
            PcLink.Status.NEEDS_CODE -> "Falta el código que muestra Marea PC"
            PcLink.Status.ERROR -> "No llego a ${name ?: "la PC"}: ${err ?: "sin conexión"}"
            PcLink.Status.OFF -> if (PcLink.isPaired) "Desconectado de ${name ?: "tu PC"}" else "Controlá el TIDAL de tu computadora desde acá"
        }
        SettingRow(
            "Controlar TIDAL de la PC", summary, Icons.Default.Computer,
            onClick = { if (status == PcLink.Status.CONNECTED) vm.onPc = !vm.onPc else pickerOpen = true },
        ) {
            if (status == PcLink.Status.CONNECTED) androidx.compose.material3.Switch(checked = vm.onPc, onCheckedChange = { vm.onPc = it })
        }
        if (status != PcLink.Status.CONNECTED) {
            SettingRow("Buscar mi PC", "Tiene que estar abierto Marea PC, en la misma red wifi", Icons.Default.Search, onClick = { pickerOpen = true })
            if (PcLink.isPaired) SettingRow("Reintentar conexión", null, Icons.Default.Sync, onClick = { PcLink.resume() })
        }
        if (PcLink.isPaired || status == PcLink.Status.CONNECTED) {
            SettingRow("Olvidar esta PC", null, Icons.Default.LinkOff, onClick = { vm.onPc = false; PcLink.disconnect(forget = true) })
        }
        if (status == PcLink.Status.CONNECTED) {
            val cur = vm.player.state.collectAsState().value.current
            SettingRow(
                "Experimental: abrir el tema actual en la PC",
                cur?.let { "Le pide a TIDAL de escritorio que abra \"${it.title}\". Para ver si se puede elegir temas desde acá." } ?: "Poné algo en el teléfono primero",
                Icons.Default.Science,
                onClick = { cur?.let { PcLink.openTrack(it.id); vm.message = "Le pedí a la PC que abra \"${it.title}\"" } },
            )
        }
        SettingRow("Bajar Marea PC (Windows)", "El programa que va en la computadora", null, onClick = { uri.openUri(PC_DOWNLOAD) })
    }

    if (pickerOpen) PcPickerDialog(
        onDismiss = { pickerOpen = false },
        onPick = { pc ->
            pickerOpen = false
            if (pc.host == PcLink.savedHost && PcLink.isPaired) PcLink.pair(pc.host, pc.name, null)
            else codeFor = pc
        },
        searchingChanged = { searching = it },
    )

    codeFor?.let { pc ->
        var code by remember(pc) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { codeFor = null },
            title = { Text("Emparejar con ${pc.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("En la PC, hacé clic derecho en el ícono de Marea PC (al lado del reloj) y mirá el código de 4 números.")
                    OutlinedTextField(
                        value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(4) },
                        singleLine = true, label = { Text("Código") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = err == "Código incorrecto",
                        supportingText = { if (err == "Código incorrecto") Text("Código incorrecto, fijate de nuevo") },
                    )
                    if (status == PcLink.Status.CONNECTING) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  Conectando…")
                    }
                }
            },
            confirmButton = { TextButton(enabled = code.length == 4, onClick = { PcLink.pair(pc.host, pc.name, code) }) { Text("Conectar") } },
            dismissButton = { TextButton(onClick = { codeFor = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PcPickerDialog(onDismiss: () -> Unit, onPick: (FoundPc) -> Unit, searchingChanged: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<FoundPc>?>(null) }
    var manual by remember { mutableStateOf(PcLink.savedHost ?: "") }
    fun search() = scope.launch { found = null; searchingChanged(true); found = PcLink.discover(); searchingChanged(false) }
    LaunchedEffect(Unit) { search() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar mi PC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val f = found
                when {
                    f == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("  Buscando en tu wifi…")
                    }
                    f.isEmpty() -> Text(
                        "No encontré ninguna. Fijate que Marea PC esté abierto, que estén en la misma red y que Windows le haya dado permiso en el firewall (red privada).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> f.forEach { pc ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(pc) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Computer, null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(pc.name, style = MaterialTheme.typography.titleMedium)
                                Text(pc.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("¿No aparece? Poné la IP de la PC:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = manual, onValueChange = { manual = it.trim() }, singleLine = true,
                    placeholder = { Text("192.168.1.20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = manual.count { it == '.' } == 3, onClick = { onPick(FoundPc(manual, manual)) }) { Text("Usar esta IP") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { search() }) { Text("Buscar de nuevo") }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}
