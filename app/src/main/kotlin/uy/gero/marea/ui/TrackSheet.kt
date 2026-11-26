package uy.gero.marea.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Bedtime
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uy.gero.marea.Track

/** Menú de un tema (botón ⋮ del reproductor o mantener apretada la tapa). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(vm: MainViewModel, t: Track) {
    val ctx = LocalContext.current
    val isCurrent = vm.player.state.value.current?.id == t.id
    val close = { vm.sheetTrack = null }
    val sleep by uy.gero.marea.SleepTimer.state.collectAsState()
    var sleepOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(t.cover, Modifier.size(56.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.fullTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t.artistLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            // Temporizador para dormir
            val sl = sleep
            if (sl != null) {
                val left by androidx.compose.runtime.produceState(remaining(sl), sl) { while (true) { value = remaining(sl); kotlinx.coroutines.delay(1000) } }
                SheetItem(Icons.Default.Bedtime, "Temporizador: $left", "Tocá para cancelarlo") { uy.gero.marea.SleepTimer.cancel(); vm.message = "Temporizador cancelado" }
            } else {
                SheetItem(Icons.Default.Bedtime, "Temporizador para dormir", if (sleepOpen) null else "Pausa la música después de un rato") { sleepOpen = !sleepOpen }
                androidx.compose.animation.AnimatedVisibility(sleepOpen) {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth().padding(start = 62.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                            androidx.compose.material3.AssistChip(
                                onClick = { uy.gero.marea.SleepTimer.start(m); close(); vm.message = "Se pausa en ${if (m < 60) "$m min" else if (m == 60) "1 hora" else "1 h 30"}" },
                                label = { Text(if (m < 60) "$m min" else if (m == 60) "1 h" else "1 h 30") },
                            )
                        }
                        androidx.compose.material3.AssistChip(
                            onClick = { uy.gero.marea.SleepTimer.startEndOfTrack(); close(); vm.message = "Se pausa al terminar este tema" },
                            label = { Text("Al terminar el tema") },
                        )
                    }
                }
            }
            if (t.albumId != null) SheetItem(Icons.Default.Album, "Ir al álbum", t.albumTitle) { close(); vm.fullPlayerOpen = false; vm.openAlbum(t.albumId) }
            t.artists.filter { it.id.isNotBlank() }.forEach { a ->
                SheetItem(Icons.Default.Person, "Ir a ${a.name}", null) { close(); vm.fullPlayerOpen = false; vm.openArtist(a.id) }
            }
            // un tema de la PC que no encontramos en el catálogo solo tiene el temporizador
            if (!t.id.all { it.isDigit() }) return@Column
            val fav = vm.isFavorite(t)
            SheetItem(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (fav) "Quitar de me gusta" else "Me gusta", null) { close(); vm.toggleFavorite(t) }
            SheetItem(Icons.Default.LibraryAdd, "Agregar a una playlist…", null) { close(); vm.openAddToPlaylist(t) }
            if (!isCurrent) {
                SheetItem(Icons.AutoMirrored.Filled.QueueMusic, "Reproducir a continuación", null) { close(); vm.trackActions.onPlayNext(t) }
                SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Agregar a la cola", null) { close(); vm.trackActions.onAddToQueue(t) }
            }
            SheetItem(Icons.Default.Radio, "Radio de este tema", null) { close(); vm.player.startRadio(t) }
            SheetItem(Icons.Default.Share, "Compartir", "Link de TIDAL") {
                close()
                val link = "https://tidal.com/track/${t.id}"
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${t.fullTitle} — ${t.artistLine}\n$link"), null))
            }
        }
    }
}

private fun remaining(s: uy.gero.marea.SleepTimer.State): String {
    val end = s.endsAtMs ?: return "al terminar el tema"
    val sec = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
}

@Composable
private fun SheetItem(icon: ImageVector, text: String, sub: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
