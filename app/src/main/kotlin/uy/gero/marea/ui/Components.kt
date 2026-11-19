package uy.gero.marea.ui

import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import uy.gero.marea.Album
import uy.gero.marea.Artist
import uy.gero.marea.Playlist
import uy.gero.marea.Track

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp), placeholder: ImageVector = Icons.Default.MusicNote) {
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
        if (url != null) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(ctx).data(url).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun QualityBadge(text: String, highlight: Boolean = false) {
    val c = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = c,
        modifier = Modifier
            .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun TrackBadges(t: Track) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (t.explicit) QualityBadge("E")
        if (t.isHiRes) QualityBadge("HI-RES", highlight = true)
        if (t.isAtmos) QualityBadge("ATMOS")
    }
}

data class TrackActions(
    val onPlayNext: (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onRadio: (Track) -> Unit,
    val onAlbum: (Track) -> Unit,
    val onArtist: (Track) -> Unit,
    val onToggleFavorite: (Track) -> Unit,
    val isFavorite: (Track) -> Boolean,
    val onAddToPlaylist: (Track) -> Unit = {},
)

@Composable
fun TrackRow(
    t: Track,
    isCurrent: Boolean,
    actions: TrackActions,
    onClick: () -> Unit,
    showCover: Boolean = true,
    leading: String? = null,
    swipe: Boolean = true,
) {
    if (swipe) {
        SwipeableTrack(onPlayNext = { actions.onPlayNext(t) }, onAddToQueue = { actions.onAddToQueue(listOf(t).first()) }) {
            TrackRow(t, isCurrent, actions, onClick, showCover, leading, swipe = false)
        }
        return
    }
    var menu by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) EqualizerBars(nowPlaying(), accent, Modifier.size(16.dp))
                else Text(leading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showCover) {
            Box(contentAlignment = Alignment.Center) {
                Cover(t.cover, Modifier.size(48.dp), RoundedCornerShape(12.dp))
                androidx.compose.animation.AnimatedVisibility(isCurrent, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        EqualizerBars(nowPlaying(), accent, Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.fullTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackBadges(t)
                if (t.explicit || t.isHiRes || t.isAtmos) Spacer(Modifier.width(6.dp))
                Text(
                    t.artistLine, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(fmt(t.durationSec.toFloat()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
            TrackMenu(t, menu, { menu = false }, actions)
        }
    }
}

@Composable
fun TrackMenu(t: Track, expanded: Boolean, onDismiss: () -> Unit, a: TrackActions) {
    DropdownMenu(expanded, onDismiss) {
        val fav = a.isFavorite(t)
        DropdownMenuItem(
            text = { Text(if (fav) "Quitar de mis canciones" else "Agregar a mis canciones") },
            leadingIcon = { Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
            onClick = { onDismiss(); a.onToggleFavorite(t) },
        )
        DropdownMenuItem(text = { Text("Agregar a una playlist…") }, leadingIcon = { Icon(Icons.Default.LibraryAdd, null) }, onClick = { onDismiss(); a.onAddToPlaylist(t) })
        DropdownMenuItem(text = { Text("Reproducir a continuación") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }, onClick = { onDismiss(); a.onPlayNext(t) })
        DropdownMenuItem(text = { Text("Agregar a la cola") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }, onClick = { onDismiss(); a.onAddToQueue(t) })
        DropdownMenuItem(text = { Text("Radio de este tema") }, leadingIcon = { Icon(Icons.Default.Radio, null) }, onClick = { onDismiss(); a.onRadio(t) })
        if (t.albumId != null) DropdownMenuItem(text = { Text("Ir al álbum") }, leadingIcon = { Icon(Icons.Default.Album, null) }, onClick = { onDismiss(); a.onAlbum(t) })
        if (t.artists.isNotEmpty()) DropdownMenuItem(text = { Text("Ir al artista") }, leadingIcon = { Icon(Icons.Default.Person, null) }, onClick = { onDismiss(); a.onArtist(t) })
    }
}

@Composable
fun MediaCard(title: String, subtitle: String?, url: String?, onClick: () -> Unit, modifier: Modifier = Modifier, circle: Boolean = false, badge: String? = null, onLongClick: (() -> Unit)? = null, pinned: Boolean = false) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        modifier.pressScale().clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick?.let { lc -> { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); lc() } })
            .padding(6.dp),
    ) {
        Box {
            Cover(
                url, Modifier.fillMaxWidth().aspectRatio(1f),
                if (circle) CircleShape else RoundedCornerShape(16.dp),
                if (circle) Icons.Default.Person else Icons.Default.Album,
            )
            if (pinned) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(26.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PushPin, "Anclada", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                }
            }
            if (badge != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AlbumCard(a: Album, onClick: () -> Unit, modifier: Modifier = Modifier) =
    MediaCard(a.title, listOfNotNull(a.artistLine.takeIf { it.isNotBlank() }, a.year).joinToString(" · "), a.cover, onClick, modifier, badge = if (a.isHiRes) "HI-RES" else null)

@Composable
fun PlaylistCard(p: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, pinned: Boolean = false) =
    MediaCard(p.name, p.numberOfItems?.let { "$it temas" }, p.cover, onClick, modifier, onLongClick = onLongClick, pinned = pinned)

@Composable
fun ArtistCard(a: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) =
    MediaCard(a.name, null, a.picture, onClick, modifier, circle = true)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun ErrorBox(msg: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

fun fmt(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun totalDuration(tracks: List<Track>): String {
    val s = tracks.sumOf { it.durationSec }
    val h = s / 3600; val m = (s % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min"
}


/** true si hay algo sonando ahora mismo (para animar indicadores). */
@Composable
fun nowPlaying(): Boolean {
    val st by uy.gero.marea.MareaApp.instance.player.state.collectAsState()
    return st.isPlaying
}
