package uy.gero.marea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uy.gero.marea.Album
import uy.gero.marea.Artist
import uy.gero.marea.Playlist
import uy.gero.marea.Track

@Composable
fun BackButton(vm: MainViewModel) {
    FilledTonalIconButton(
        onClick = { vm.back() },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
}

/** Cabecera común para álbum/playlist: portada grande con el color de la portada detrás. */
@Composable
private fun CollectionHeader(
    title: String,
    subtitle: String,
    meta: String?,
    cover: String?,
    coverColors: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRadio: (() -> Unit)?,
    favorite: Boolean?,
    onFavorite: (() -> Unit)?,
    onSubtitle: (() -> Unit)?,
    headerArt: (@Composable () -> Unit)? = null,
) {
    val tint = rememberCoverColor(cover, coverColors, MaterialTheme.colorScheme.primary)
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent)))) {
        WidthAware { wide ->
            val inner: @Composable () -> Unit = {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = if (wide) TextAlign.Start else TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = if (onSubtitle != null) Modifier.clip(RoundedCornerShape(6.dp)).then(Modifier.padding(2.dp)) else Modifier,
                )
                if (meta != null) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPlay, shape = RoundedCornerShape(50)) { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Reproducir") }
                    FilledTonalButton(onClick = onShuffle, shape = RoundedCornerShape(50)) { Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)) }
                    if (onRadio != null) FilledTonalIconButton(onClick = onRadio) { Icon(Icons.Default.Radio, "Radio") }
                    if (favorite != null && onFavorite != null) {
                        IconButton(onClick = onFavorite) {
                            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.statusBarsPad().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
                    if (headerArt != null) Box(Modifier.size(220.dp).clip(RoundedCornerShape(20.dp))) { headerArt() } else Cover(cover, Modifier.size(220.dp), RoundedCornerShape(20.dp))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) { inner() }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().statusBarsPad().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (headerArt != null) Box(Modifier.widthIn(max = 280.dp).fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(22.dp))) { headerArt() }
                    else Cover(cover, Modifier.widthIn(max = 280.dp).fillMaxWidth(0.72f).aspectRatio(1f), RoundedCornerShape(22.dp))
                    Spacer(Modifier.height(18.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { inner() }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(vm: MainViewModel, screen: Screen.AlbumS, actions: TrackActions, currentId: String?, coverColors: Boolean, contentPadding: PaddingValues) {
    var album by remember(screen.id) { mutableStateOf(screen.seed ?: vm.api.cachedAlbum(screen.id)) }
    var tracks by remember(screen.id) { mutableStateOf<List<Track>?>(null) }
    var error by remember(screen.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(screen.id) {
        if (album == null) album = runCatching { vm.api.albums(listOf(screen.id)).firstOrNull() }.getOrNull()
        runCatching { vm.api.albumTracks(screen.id) }.onSuccess { tracks = it }.onFailure { error = it.message }
    }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item {
                val a = album
                val t = tracks.orEmpty()
                CollectionHeader(
                    title = a?.title ?: "…",
                    subtitle = a?.artistLine ?: "",
                    meta = listOfNotNull(a?.type?.lowercase()?.replaceFirstChar { it.uppercase() }, a?.year, t.takeIf { it.isNotEmpty() }?.let { "${it.size} temas · ${totalDuration(it)}" }, if (a?.isHiRes == true) "Hi-Res" else null).joinToString(" · "),
                    cover = a?.coverLarge ?: a?.cover,
                    coverColors = coverColors,
                    onPlay = { vm.player.playContainer("album", screen.id, false, a?.title, t) },
                    onShuffle = { vm.player.playContainer("album", screen.id, true, a?.title, t) },
                    onRadio = t.firstOrNull()?.let { first -> { vm.player.startRadio(first) } },
                    favorite = a?.let { vm.isFavoriteAlbum(it) },
                    onFavorite = a?.let { { vm.toggleFavoriteAlbum(it) } },
                    onSubtitle = a?.artists?.firstOrNull()?.let { { vm.openArtist(it.id) } },
                )
            }
            val t = tracks
            when {
                t == null && error == null -> item { LoadingBox() }
                error != null -> item { ErrorBox(error!!) }
                t != null -> items(t.size, key = { t[it].id + "_" + it }) { i ->
                    val tr = t[i]
                    Box(Modifier.groupItem(i, t.size)) {
                        TrackRow(tr, tr.id == currentId, actions, onClick = { vm.player.playTracks(t, i, source = album?.title) }, showCover = false, leading = (tr.trackNumber ?: (i + 1)).toString())
                    }
                }
            }
        }
        Box(Modifier.statusBarsPad().padding(8.dp)) { if (!collapsed) BackButton(vm) }
        CollapsedTitleBar(album?.title ?: "", collapsed) { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } }
    }
}

@Composable
fun PlaylistScreen(vm: MainViewModel, screen: Screen.PlaylistS, actions: TrackActions, currentId: String?, coverColors: Boolean, contentPadding: PaddingValues) {
    var playlist by remember(screen.id) { mutableStateOf<Playlist?>(screen.seed ?: vm.api.cachedPlaylist(screen.id)) }
    LaunchedEffect(screen.id) {
        if (playlist == null) playlist = runCatching { vm.api.playlists(listOf(screen.id)).firstOrNull() }.getOrNull()
    }
    val loaded = vm.playlistTracks(screen.id)
    TrackCollection(
        vm = vm, actions = actions, currentId = currentId, coverColors = coverColors, contentPadding = contentPadding,
        title = playlist?.name ?: "…",
        subtitle = when (playlist?.type) { "EDITORIAL" -> "Playlist de TIDAL"; "MIX" -> "Mix"; "ARTIST" -> "Playlist de artista"; else -> "Playlist" },
        meta = listOfNotNull(playlist?.numberOfItems?.let { "$it temas" }, playlist?.description?.take(140)).joinToString(" · "),
        cover = playlist?.coverLarge ?: playlist?.cover,
        loaded = loaded,
        onPlay = { shuffle -> vm.playLoaded(loaded, shuffle, playlist?.name ?: "Playlist") },
    )
}

/** Lista de temas con cabecera (la usan las playlists y "Tus me gusta"). Carga todo en segundo plano. */
@Composable
fun TrackCollection(
    vm: MainViewModel, actions: TrackActions, currentId: String?, coverColors: Boolean, contentPadding: PaddingValues,
    title: String, subtitle: String, meta: String?, cover: String?, loaded: LoadedList, onPlay: (shuffle: Boolean) -> Unit,
    headerArt: (@Composable () -> Unit)? = null,
) {
    val tracks = loaded.tracks
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item {
                CollectionHeader(
                    title = title, subtitle = subtitle,
                    meta = listOfNotNull(meta?.takeIf { it.isNotBlank() }, if (loaded.loading) "cargando ${tracks.size}…" else null).joinToString(" · "),
                    cover = cover, coverColors = coverColors,
                    onPlay = { onPlay(false) }, onShuffle = { onPlay(true) },
                    onRadio = tracks.firstOrNull()?.let { first -> { vm.player.startRadio(first) } },
                    favorite = null, onFavorite = null, onSubtitle = null, headerArt = headerArt,
                )
            }
            if (tracks.isEmpty() && loaded.loading) items(6) { SkeletonRow() }
            items(tracks.size, key = { tracks[it].id + "_" + it }) { i ->
                val tr = tracks[i]
                Box(Modifier.groupItem(i, tracks.size)) {
                    TrackRow(tr, tr.id == currentId, actions, onClick = { vm.playLoadedFrom(loaded, i, title) })
                }
            }
            loaded.error?.let { e -> item { ErrorBox(e) } }
        }
        Box(Modifier.statusBarsPad().padding(8.dp)) { if (!collapsed) BackButton(vm) }
        CollapsedTitleBar(title, collapsed) { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } }
    }
}

@Composable
fun ArtistScreen(vm: MainViewModel, screen: Screen.ArtistS, actions: TrackActions, currentId: String?, contentPadding: PaddingValues) {
    var artist by remember(screen.id) { mutableStateOf<Artist?>(screen.seed ?: vm.api.cachedArtist(screen.id)) }
    var top by remember(screen.id) { mutableStateOf<List<Track>?>(null) }
    var albums by remember(screen.id) { mutableStateOf<List<Album>?>(null) }
    var showAllTop by remember(screen.id) { mutableStateOf(false) }
    LaunchedEffect(screen.id) {
        runCatching { vm.api.artist(screen.id) }.getOrNull()?.let { artist = it }
    }
    LaunchedEffect(screen.id) { top = runCatching { vm.api.artistTopTracks(screen.id) }.getOrDefault(emptyList()) }
    LaunchedEffect(screen.id) { albums = runCatching { vm.api.artistAlbums(screen.id) }.getOrDefault(emptyList()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item {
                val a = artist
                Box(Modifier.fillMaxWidth().height(bigHeaderHeight() + 120.dp)) {
                    Cover(a?.pictureLarge ?: a?.picture, Modifier.fillMaxSize(), RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp), Icons.Default.Person)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.95f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                        Text(a?.name ?: "…", style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { top?.let { vm.player.playTracks(it, 0, source = a?.name) } }, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Populares")
                            }
                            FilledTonalButton(onClick = {
                                scope.launchCatching { vm.player.playTracks(vm.api.artistRadio(screen.id), 0, source = "Radio: ${a?.name}") }
                            }, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Radio, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Radio")
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Populares") }
            val t = top
            if (t == null) item { LoadingBox() } else {
                val shown = if (showAllTop) t else t.take(5)
                items(shown.size, key = { "top" + shown[it].id }) { i ->
                    Box(Modifier.groupItem(i, shown.size)) {
                        TrackRow(shown[i], shown[i].id == currentId, actions, onClick = { vm.player.playTracks(t, i, source = artist?.name) })
                    }
                }
                if (t.size > 5) item {
                    androidx.compose.material3.TextButton(onClick = { showAllTop = !showAllTop }, modifier = Modifier.padding(start = 12.dp)) {
                        Text(if (showAllTop) "Ver menos" else "Ver todos (${t.size})")
                    }
                }
            }
            val al = albums
            item { SectionTitle("Discografía") }
            if (al == null) item { LoadingBox() } else if (al.isEmpty()) item { ErrorBox("Sin álbumes") } else {
                val groups = al.groupBy { when (it.type) { "SINGLE" -> "Singles"; "EP" -> "EPs"; else -> "Álbumes" } }
                listOf("Álbumes", "EPs", "Singles").forEach { g ->
                    val list = groups[g] ?: return@forEach
                    item { Text(g, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(list, key = { it.id }) { a -> AlbumCard(a, { vm.open(Screen.AlbumS(a.id, a)) }, Modifier.width(150.dp)) }
                        }
                    }
                }
            }
        }
        Box(Modifier.statusBarsPad().padding(8.dp)) { if (!collapsed) BackButton(vm) }
        CollapsedTitleBar(artist?.name ?: "", collapsed) { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } }
    }
}

fun CoroutineScope.launchCatching(block: suspend () -> Unit) {
    launch { runCatching { block() }.onFailure { android.util.Log.w("Marea", "acción falló", it) } }
}
