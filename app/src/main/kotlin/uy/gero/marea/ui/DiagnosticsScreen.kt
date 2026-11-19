package uy.gero.marea.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uy.gero.marea.BuildConfig
import uy.gero.marea.DiagLog

@Composable
fun DiagnosticsScreen(vm: MainViewModel) {
    val lines by DiagLog.lines.collectAsState()
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.size - 1) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { vm.diagnosticsOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
            Text("Diagnóstico", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(
            "Copiá esto y pasáselo a Claude si algo no anda.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val text = "Marea ${BuildConfig.VERSION_NAME}\n" + DiagLog.dump()
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Marea", text))
                vm.message = "Copiado"
            }) { Text("Copiar todo") }
            FilledTonalButton(onClick = { vm.runProbe() }) { Text("Probar stream") }
            FilledTonalButton(onClick = { DiagLog.clear() }) { Text("Limpiar") }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (lines.isEmpty()) item { Text("Todavía no hay nada registrado.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(lines) { l ->
                Text(l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}
