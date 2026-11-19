package uy.gero.marea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uy.gero.marea.BuildConfig

private const val DEV_URL = "https://developer.tidal.com/dashboard"

/** Primera pantalla cuando el APK no trae Client ID: explica cómo conseguirlo y lo pide. */
@Composable
fun ClientIdSetupScreen(onSave: (String) -> Unit) {
    var id by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("Bienvenido a Marea", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Antes de empezar necesitás un Client ID propio de TIDAL. Es gratis y lleva unos 5 minutos. Se pide una sola vez.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Step(1, "Entrá al panel de desarrolladores de TIDAL con tu cuenta y creá una app (el nombre no importa).") {
                    TextButton(onClick = { uri.openUri(DEV_URL) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Abrir developer.tidal.com")
                    }
                }
                Step(2, "En \"Redirect URIs\" agregá exactamente esto:") { Copyable(BuildConfig.TIDAL_REDIRECT_URI) }
                Step(3, "En los permisos (scopes) activá todos estos:") { Copyable(BuildConfig.TIDAL_SCOPES) }
                Step(4, "Copiá el Client ID de tu app y pegalo acá. El Client Secret no hace falta: no lo pongas en ningún lado.") {
                    OutlinedTextField(
                        value = id, onValueChange = { id = it.trim() }, singleLine = true,
                        placeholder = { Text("Client ID") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    )
                }
                Button(
                    onClick = { onSave(id) }, enabled = id.length >= 8,
                    shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Guardar y seguir") }
                Text(
                    "Lo podés cambiar después en Ajustes → Client ID de TIDAL.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text("$n", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        content()
    }
}

@Composable
private fun Copyable(value: String) {
    val clip = LocalClipboardManager.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { clip.setText(AnnotatedString(value)) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ContentCopy, "Copiar", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Para cambiar el Client ID desde Ajustes. Al guardar se cierra la sesión y la app se reinicia. */
@Composable
fun ClientIdDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var id by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Client ID de TIDAL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "El de tu app en developer.tidal.com. Si lo cambiás, se cierra la sesión y Marea se reinicia.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = id, onValueChange = { id = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = id.length >= 8 && id != current, onClick = { onDismiss(); onSave(id) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
