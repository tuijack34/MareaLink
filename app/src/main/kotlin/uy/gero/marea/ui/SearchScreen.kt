package uy.gero.marea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uy.gero.marea.SearchResult

@Composable
fun SearchScreen(vm: MainViewModel, actions: TrackActions, currentId: String?, contentPadding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { result = null; error = null; return@LaunchedEffect }
        delay(450)
        loading = true
        runCatching { vm.api.search(q) }
            .onSuccess { result = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            BigHeader("Buscar", "Todo el catálogo de TIDAL")
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Artistas, temas, álbumes, playlists") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Borrar") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = RoundedCornerShape(28.dp),
                colors = filledFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        if (loading) item { LoadingBox() }
        error?.let { e -> item { ErrorBox(e) } }
        val r = result
        if (r != null) {
            if (r.artists.isNotEmpty()) {
                item { SectionTitle("Artistas") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(r.artists, key = { it.id }) { a -> ArtistCard(a, { vm.open(Screen.ArtistS(a.id, a)) }, Modifier.width(130.dp)) }
                    }
                }
            }
            if (r.tracks.isNotEmpty()) {
                item { SectionTitle("Temas") }
                items(r.tracks.size, key = { "t" + r.tracks[it].id }) { i ->
                    val t = r.tracks[i]
                    androidx.compose.foundation.layout.Box(Modifier.groupItem(i, r.tracks.size)) { TrackRow(t, t.id == currentId, actions, onClick = { vm.player.playTracks(listOf(t), 0, source = "Búsqueda"); if (vm.player.state.value.upNext.isEmpty()) vm.player.addSuggestions(t) }) }
                }
            }
            if (r.albums.isNotEmpty()) {
                item { SectionTitle("Álbumes") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(r.albums, key = { it.id }) { a -> AlbumCard(a, { vm.open(Screen.AlbumS(a.id, a)) }, Modifier.width(150.dp)) }
                    }
                }
            }
            if (r.playlists.isNotEmpty()) {
                item { SectionTitle("Playlists") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(r.playlists, key = { it.id }) { p -> PlaylistCard(p, { vm.open(Screen.PlaylistS(p.id, p)) }, Modifier.width(150.dp)) }
                    }
                }
            }
            if (r.tracks.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty() && r.playlists.isEmpty() && !loading) {
                item { ErrorBox("Sin resultados para \"$query\"") }
            }
        }
    }
}
