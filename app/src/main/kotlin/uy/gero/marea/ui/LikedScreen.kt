package uy.gero.marea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Portada propia de "Tus me gusta": degradé con un corazón. */
@Composable
fun LikedArt(modifier: Modifier = Modifier.fillMaxSize()) {
    val a = MaterialTheme.colorScheme.primary
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF3B2A8F), a.darken(0.1f), Color(0xFFE0457B)))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
}

@Composable
fun LikedScreen(vm: MainViewModel, actions: TrackActions, currentId: String?, coverColors: Boolean, contentPadding: PaddingValues) {
    val loaded = vm.likedTracks()
    TrackCollection(
        vm = vm, actions = actions, currentId = currentId, coverColors = false, contentPadding = contentPadding,
        title = "Tus me gusta", subtitle = "Playlist", meta = if (!loaded.loading) "${loaded.tracks.size} temas · ${totalDuration(loaded.tracks)}" else null,
        cover = null, loaded = loaded,
        onPlay = { shuffle -> vm.playLoaded(loaded, shuffle, "Tus me gusta") },
        headerArt = { LikedArt() },
    )
}
