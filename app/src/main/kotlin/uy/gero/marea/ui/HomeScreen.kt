package uy.gero.marea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buen día"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
fun HomeScreen(vm: MainViewModel, actions: TrackActions, currentId: String?, coverColors: Boolean, contentPadding: PaddingValues) {
    LaunchedEffect(Unit) { vm.loadHome() }
    val recentTracks = vm.tracks.items.take(12)
    val recentAlbums = vm.albums.items.take(12)
    val playlists = vm.withPinnedFirst(vm.playlists.items).take(12)

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = vm.mixesLoading && vm.mixes.isNotEmpty(),
        onRefresh = { vm.tracks.refresh(); vm.albums.refresh(); vm.playlists.refresh(); vm.loadHome(force = true) },
    ) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            BigHeader(greeting(), "Hecho a partir de lo que te gusta")
        }

        // Accesos rápidos estilo grilla de 2 columnas
        if (playlists.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    playlists.take(6).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { p ->
                                Row(
                                    Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(18.dp)).background(groupColor())
                                        .pressScale().clickable { vm.open(Screen.PlaylistS(p.id, p)) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Cover(p.cover, Modifier.size(60.dp), RoundedCornerShape(18.dp))
                                    Text(p.name, Modifier.padding(horizontal = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item { SectionTitle("Mixes para vos") }
        when {
            vm.mixes.isNotEmpty() -> item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.mixes, key = { it.seed.id }) { mix -> MixCard(mix, coverColors) { vm.player.playTracks(mix.tracks, 0, source = mix.title) } }
                }
            }
            vm.mixesLoading -> item { SkeletonGridRow(columns = 3, itemWidth = 170.dp) }
            else -> item { ErrorBox("No pude armar mixes todavía. Agregá temas a tus me gusta o mirá Ajustes → Diagnóstico.") }
        }

        // Recomendaciones de TIDAL
        if (vm.recLoading && vm.recRows.isEmpty()) item { SkeletonGridRow(columns = 3, itemWidth = 160.dp) }
        vm.recRows.forEach { row ->
            item(key = "rec_" + row.title) { SectionTitle(row.title) }
            item(key = "recrow_" + row.title) {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(row.playlists, key = { "p" + it.id }) { p -> PlaylistCard(p, { vm.open(Screen.PlaylistS(p.id, p)) }, Modifier.width(160.dp)) }
                    items(row.albums, key = { "a" + it.id }) { a -> AlbumCard(a, { vm.open(Screen.AlbumS(a.id, a)) }, Modifier.width(160.dp)) }
                    items(row.tracks, key = { "t" + it.id }) { t ->
                        MediaCard(t.title, t.artistLine, t.cover, { vm.player.playTracks(row.tracks, row.tracks.indexOf(t), source = row.title) }, Modifier.width(160.dp))
                    }
                }
            }
        }

        if (recentTracks.isNotEmpty()) {
            item {
                SectionTitle("Tus últimos me gusta") {
                    IconButton(onClick = { vm.playAllFavorites(true) }) { Icon(Icons.Default.AutoAwesome, "Aleatorio") }
                }
            }
            items(recentTracks.size.coerceAtMost(6), key = { "rt" + recentTracks[it].id }) { i ->
                val t = recentTracks[i]
                Box(Modifier.groupItem(i, recentTracks.size.coerceAtMost(6)).animateItem()) {
                    TrackRow(t, t.id == currentId, actions, onClick = { vm.player.playTracks(vm.tracks.items.toList(), i, source = "Tus canciones") })
                }
            }
        } else if (vm.tracks.error != null) {
            item { ErrorBox("No pude cargar tus me gusta: ${vm.tracks.error}") { vm.tracks.refresh() } }
        }

        if (recentAlbums.isNotEmpty()) {
            item { SectionTitle("Álbumes guardados") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(recentAlbums, key = { it.id }) { a -> AlbumCard(a, { vm.open(Screen.AlbumS(a.id, a)) }, Modifier.width(150.dp)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }
}

@Composable
private fun MixCard(mix: MainViewModel.Mix, coverColors: Boolean, onPlay: () -> Unit) {
    val tint = rememberCoverColor(mix.seed.cover, coverColors, MaterialTheme.colorScheme.primary)
    Column(Modifier.width(170.dp).pressScale().clip(RoundedCornerShape(GroupRadius)).clickable(onClick = onPlay)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(GroupRadius))) {
            // Collage de 4 portadas del mix
            val covers = mix.tracks.mapNotNull { it.cover }.distinct().take(4)
            Column(Modifier.fillMaxSize()) {
                covers.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f)) { row.forEach { Cover(it, Modifier.weight(1f).fillMaxSize(), RoundedCornerShape(0.dp)) } }
                }
                if (covers.isEmpty()) Cover(null, Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, tint.darken(0.4f).copy(alpha = 0.9f)))))
            FilledIconButton(
                onClick = onPlay, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Icon(Icons.Default.PlayArrow, "Reproducir") }
        }
        Spacer(Modifier.height(8.dp))
        Text(mix.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text("${mix.tracks.size} temas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
