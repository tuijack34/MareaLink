package uy.gero.marea.ui

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
            if (t.albumId != null) SheetItem(Icons.Default.Album, "Ir al álbum", t.albumTitle) { close(); vm.fullPlayerOpen = false; vm.openAlbum(t.albumId) }
            t.artists.filter { it.id.isNotBlank() }.forEach { a ->
                SheetItem(Icons.Default.Person, "Ir a ${a.name}", null) { close(); vm.fullPlayerOpen = false; vm.openArtist(a.id) }
            }
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
