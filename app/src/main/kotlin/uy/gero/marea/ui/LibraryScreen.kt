package uy.gero.marea.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import uy.gero.marea.Album
import uy.gero.marea.Artist
import uy.gero.marea.Playlist
import uy.gero.marea.Track

fun gridMin(size: Int) = when (size) { 0 -> 120.dp; 2 -> 200.dp; else -> 156.dp }

@Composable
fun LibraryScreen(vm: MainViewModel, actions: TrackActions, currentId: String?, gridSize: Int, contentPadding: PaddingValues) {
    val section = vm.librarySection
    val paged = vm.paged(section)
    LaunchedEffect(section) { paged.ensureStarted() }
    var filter by rememberSaveable(section) { mutableStateOf("") }
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember(paged) {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore, paged.items.size) { if (shouldLoadMore && filter.isBlank()) paged.loadMore() }

    val isList = section == LibrarySection.TRACKS
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = paged.loading && paged.items.isNotEmpty(),
        onRefresh = { paged.refresh(); if (section == LibrarySection.TRACKS) vm.refreshLiked() },
    ) {
    LazyVerticalGrid(
        columns = if (isList) GridCells.Fixed(1) else GridCells.Adaptive(gridMin(gridSize)),
        state = gridState,
        // Más aire entre playlists: márgenes a los costados y separación entre filas y columnas
        contentPadding = PaddingValues(
            start = if (isList) 0.dp else 14.dp, end = if (isList) 0.dp else 14.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(if (isList) 0.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (isList) 0.dp else 16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                BigHeader("Biblioteca", section.label)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibrarySection.entries.forEach { s ->
                        FilterChip(
                            selected = s == section, onClick = { vm.librarySection = s }, label = { Text(s.label) },
                            shape = RoundedCornerShape(50), border = null,
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    placeholder = { Text("Filtrar ${section.label.lowercase()}…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Default.Clear, "Borrar") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = filledFieldColors(),
                )
                if (isList) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.playAllFavorites(false) }) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reproducir")
                        }
                        FilledTonalButton(onClick = { vm.playAllFavorites(true) }) {
                            Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aleatorio")
                        }
                    }
                }
            }
        }

        val q = filter.trim().lowercase()
        when (section) {
            LibrarySection.PLAYLISTS -> {
                if (q.isEmpty() || "me gusta".contains(q)) item(key = "liked") {
                    Column(Modifier.pressScale().clip(RoundedCornerShape(18.dp)).clickable { vm.open(Screen.LikedS) }.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) { LikedArt() }
                        Spacer(Modifier.height(8.dp))
                        Text("Tus me gusta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Playlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val list = vm.withPinnedFirst(paged.items as List<Playlist>).filter { q.isEmpty() || it.name.lowercase().contains(q) }
                // Mantener apretado una playlist para anclarla arriba de todo
                items(list, key = { "p" + it.id }) { p ->
                    PlaylistCard(
                        p, { vm.open(Screen.PlaylistS(p.id, p)) }, Modifier.animateItem(),
                        onLongClick = { vm.togglePin(p) }, pinned = vm.isPinned(p.id),
                    )
                }
            }
            LibrarySection.ALBUMS -> {
                @Suppress("UNCHECKED_CAST")
                val list = (paged.items as List<Album>).filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artistLine.lowercase().contains(q) }
                items(list, key = { "a" + it.id }) { a -> AlbumCard(a, { vm.open(Screen.AlbumS(a.id, a)) }) }
            }
            LibrarySection.ARTISTS -> {
                @Suppress("UNCHECKED_CAST")
                val list = (paged.items as List<Artist>).filter { q.isEmpty() || it.name.lowercase().contains(q) }
                items(list, key = { "r" + it.id }) { a -> ArtistCard(a, { vm.open(Screen.ArtistS(a.id, a)) }) }
            }
            LibrarySection.TRACKS -> {
                @Suppress("UNCHECKED_CAST")
                val all = paged.items as List<Track>
                val list = all.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artistLine.lowercase().contains(q) || (it.albumTitle?.lowercase()?.contains(q) == true) }
                items(list.size, key = { "t" + list[it].id + "_" + it }) { i ->
                    val t = list[i]
                    Box(Modifier.groupItem(i, list.size)) {
                        TrackRow(t, t.id == currentId, actions, onClick = { vm.player.playTracks(list, i, source = "Tus canciones") })
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            when {
                paged.loading && paged.items.isEmpty() -> Column { repeat(3) { if (isList) SkeletonRow() else SkeletonGridRow() } }
                paged.loading -> LoadingBox()
                paged.error != null -> ErrorBox(paged.error!!) { paged.loadMore() }
                paged.items.isEmpty() && paged.done -> ErrorBox("Acá no hay nada todavía")
                else -> Spacer(Modifier.size(24.dp))
            }
        }
    }
    }
}
