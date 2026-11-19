package uy.gero.marea.ui

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidal.sdk.auth.model.CredentialsUpdatedMessage
import com.tidal.sdk.auth.model.LoginConfig
import com.tidal.sdk.auth.model.QueryParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uy.gero.marea.Album
import uy.gero.marea.Artist
import uy.gero.marea.BuildConfig
import uy.gero.marea.MareaApp
import uy.gero.marea.Page
import uy.gero.marea.Playlist
import uy.gero.marea.Track

enum class Tab(val label: String) { HOME("Para vos"), LIBRARY("Biblioteca"), SEARCH("Buscar"), SETTINGS("Ajustes") }
enum class LibrarySection(val label: String) { PLAYLISTS("Playlists"), ALBUMS("Álbumes"), ARTISTS("Artistas"), TRACKS("Canciones") }

sealed interface Screen {
    data object Root : Screen
    data class AlbumS(val id: String, val seed: Album? = null) : Screen
    data class PlaylistS(val id: String, val seed: Playlist? = null) : Screen
    data class ArtistS(val id: String, val seed: Artist? = null) : Screen
    data object LikedS : Screen
}

/** Lista que se carga completa en segundo plano (rápido, en paralelo). */
class LoadedList {
    val tracks = mutableStateListOf<Track>()
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
}

/** Lista paginada simple, en estado de Compose. */
class Paged<T>(private val scope: CoroutineScope, private val name: String = "lista", private val loader: suspend (String?) -> Page<T>) {
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var done by mutableStateOf(false); private set
    var started = false; private set
    private var next: String? = null

    fun ensureStarted() { if (!started) refresh() }

    fun refresh() {
        started = true
        items.clear(); next = null; done = false; error = null
        loadMore()
    }

    fun loadMore() {
        if (loading || done) return
        loading = true
        scope.launch {
            runCatching { loader(next) }
                .onSuccess { p ->
                    items.addAll(p.items)
                    uy.gero.marea.DiagLog.add("Lista", "$name: +${p.items.size} (hay más: ${p.next != null})")
                    next = p.next
                    done = p.next == null
                    error = null
                }
                .onFailure { error = it.message ?: "Error"; Log.w("Paged", "falló", it); uy.gero.marea.DiagLog.add("Lista", "$name: ${it::class.simpleName}: ${it.message}") }
            loading = false
        }
    }

    /** Carga todo (hasta un tope) — útil para "Reproducir todo". */
    suspend fun loadAll(maxPages: Int = 50): List<T> {
        var pages = 0
        while (!done && pages++ < maxPages) {
            val p = runCatching { loader(next) }.getOrElse { break }
            items.addAll(p.items); next = p.next; done = p.next == null
        }
        return items.toList()
    }
}

class MainViewModel : ViewModel() {
    private val app = MareaApp.instance
    val api = app.api
    val player get() = app.player
    private val auth = app.tidalAuth

    var loggedIn by mutableStateOf(auth.credentialsProvider.isUserLoggedIn()); private set
    var loginError by mutableStateOf<String?>(null)
    var loggingIn by mutableStateOf(false); private set

    var tab by mutableStateOf(Tab.HOME)
    var librarySection by mutableStateOf(LibrarySection.PLAYLISTS)
    private val stacks = mutableStateMapOf<Tab, List<Screen>>().apply { Tab.entries.forEach { put(it, listOf(Screen.Root)) } }
    val currentScreen: Screen get() = stacks[tab]?.lastOrNull() ?: Screen.Root
    val canGoBack: Boolean get() = (stacks[tab]?.size ?: 1) > 1

    var fullPlayerOpen by mutableStateOf(false)
    var diagnosticsOpen by mutableStateOf(false)
    var labOpen by mutableStateOf(false)
    /** Reproductor lateral minimizado (Fold abierto). */
    var paneMinimized by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    val favoriteTrackIds = mutableStateMapOf<String, Boolean>()
    val favoriteAlbumIds = mutableStateMapOf<String, Boolean>()

    val playlists = Paged(viewModelScope, "Playlists") { api.myPlaylists(it) }
    val albums = Paged(viewModelScope, "Álbumes") { n -> api.favoriteAlbums(n).also { p -> p.items.forEach { favoriteAlbumIds[it.id] = true } } }
    val artists = Paged(viewModelScope, "Artistas") { api.favoriteArtists(it) }
    val tracks = Paged(viewModelScope, "Canciones") { n -> api.favoriteTracks(n).also { p -> p.items.forEach { favoriteTrackIds[it.id] = true } } }

    private val loadedLists = HashMap<String, LoadedList>()

    private fun loadList(key: String, path: String, params: List<Pair<String, String>>): LoadedList =
        loadedLists.getOrPut(key) {
            LoadedList().also { l ->
                viewModelScope.launch {
                    runCatching { api.loadAllTracksFast(path, params) { chunk -> l.tracks.addAll(chunk) } }
                        .onFailure { l.error = it.message }
                    l.loading = false
                    uy.gero.marea.DiagLog.add("Lista", "$key: ${l.tracks.size} temas cargados")
                }
            }
        }

    /**
     * Reproduce una lista que puede estar todavía cargando: arranca con lo que hay y va sumando
     * a la cola (mezclado si es aleatorio) todo lo que siga llegando, hasta el último tema.
     */
    fun playLoaded(l: LoadedList, shuffle: Boolean, source: String, startIndex: Int = 0) {
        val snapshot = l.tracks.toList()
        var consumed = snapshot.size
        val pages: (suspend () -> List<Track>?)? = if (!l.loading) null else suspend {
            while (l.loading && l.tracks.size <= consumed) kotlinx.coroutines.delay(300)
            if (l.tracks.size > consumed) {
                val more = l.tracks.subList(consumed, l.tracks.size).toList()
                consumed = l.tracks.size
                more
            } else null
        }
        val start = if (shuffle) -1 else startIndex
        if (start >= 0) player.playProgressive(snapshot, false, source, startIndex = start, pages = pages)
        else player.playProgressive(snapshot, true, source, pages = pages)
        if (pages != null) message = "Cargando el resto de la lista (${snapshot.size} de ${l.tracks.size}+)…"
    }

    /** Tocar un tema dentro de una lista: suena ese y sigue con el resto (incluido lo que falta cargar). */
    fun playLoadedFrom(l: LoadedList, index: Int, source: String) {
        val st = player.state.value
        if (st.shuffle) {
            // con aleatorio activo: arranca por ese tema y el resto de TODA la lista mezclado
            val snapshot = l.tracks.toList()
            val first = snapshot.getOrNull(index) ?: return
            val rest = snapshot.filterIndexed { i, _ -> i != index }
            var consumed = snapshot.size
            val pages: (suspend () -> List<Track>?)? = if (!l.loading) null else suspend {
                while (l.loading && l.tracks.size <= consumed) kotlinx.coroutines.delay(300)
                if (l.tracks.size > consumed) { val m = l.tracks.subList(consumed, l.tracks.size).toList(); consumed = l.tracks.size; m } else null
            }
            player.playProgressive(listOf(first) + rest.shuffled(), false, source, startIndex = 0, pages = pages, alreadyShuffled = true)
        } else playLoaded(l, false, source, index)
    }

    fun playlistTracks(id: String) = loadList("playlist:$id", "playlists/$id/relationships/items", listOf("countryCode" to api.country))
    fun likedTracks() = loadList("liked", "userCollectionTracks/me/relationships/items", listOf("sort" to "-addedAt"))
    fun refreshLiked() { loadedLists.remove("liked") }
    fun invalidatePlaylist(id: String) { loadedLists.remove("playlist:$id") }

    /** Mixes armados a partir de tus últimos me gusta (radio de cada tema). */
    data class Mix(val title: String, val seed: Track, val tracks: List<Track>)
    val mixes = mutableStateListOf<Mix>()
    var mixesLoading by mutableStateOf(false); private set
    private var mixesLoaded = false

    val recRows = mutableStateListOf<uy.gero.marea.RecRow>()
    var recLoading by mutableStateOf(false); private set

    fun loadHome(force: Boolean = false) {
        if (recRows.isEmpty() || force) viewModelScope.launch {
            recLoading = true
            runCatching { api.recommendations() }.onSuccess { recRows.clear(); recRows.addAll(it) }
            recLoading = false
        }
        playlists.ensureStarted(); albums.ensureStarted(); tracks.ensureStarted()
        if (mixesLoaded && !force) return
        mixesLoaded = true
        viewModelScope.launch {
            mixesLoading = true
            // Esperamos a tener algunos me gusta para sembrar los mixes
            var waited = 0
            while (tracks.items.isEmpty() && tracks.error == null && !tracks.done && waited++ < 40) kotlinx.coroutines.delay(250)
            val seeds = tracks.items.distinctBy { it.artists.firstOrNull()?.id ?: it.id }.take(6)
            mixes.clear()
            for (seed in seeds) {
                val list = runCatching { api.suggestions(seed.id) }.getOrDefault(emptyList())
                if (list.isNotEmpty()) mixes.add(Mix("Mix de ${seed.artists.firstOrNull()?.name ?: seed.title}", seed, listOf(seed) + list))
            }
            uy.gero.marea.DiagLog.add("Inicio", "Mixes generados: ${mixes.size} de ${seeds.size} semillas")
            mixesLoading = false
        }
    }

    fun runProbe() {
        viewModelScope.launch {
            val id = player.state.value.current?.id ?: tracks.items.firstOrNull()?.id ?: "251380837"
            uy.gero.marea.DiagLog.add("Prueba", "Consultando a TIDAL por el tema $id…")
            uy.gero.marea.DiagLog.add("Prueba", api.probeManifest(id))
        }
    }

    init {
        viewModelScope.launch {
            runCatching { auth.credentialsProvider.getCredentials().successData }.getOrNull()?.let { c ->
                uy.gero.marea.DiagLog.add("Sesión", "userId=${c.userId} scopes=${c.grantedScopes.sorted()}")
            }
        }
        viewModelScope.launch {
            auth.credentialsProvider.bus.collect {
                if (it is CredentialsUpdatedMessage) loggedIn = it.credentials?.userId != null
            }
        }
    }

    fun paged(section: LibrarySection): Paged<*> = when (section) {
        LibrarySection.PLAYLISTS -> playlists
        LibrarySection.ALBUMS -> albums
        LibrarySection.ARTISTS -> artists
        LibrarySection.TRACKS -> tracks
    }

    // ---------- Navegación ----------

    fun open(screen: Screen) {
        fullPlayerOpen = false
        if (tab == Tab.SETTINGS) tab = Tab.HOME
        stacks[tab] = (stacks[tab] ?: listOf(Screen.Root)) + screen
    }

    fun back(): Boolean {
        if (fullPlayerOpen) { fullPlayerOpen = false; return true }
        if (labOpen) { labOpen = false; return true }
        if (diagnosticsOpen) { diagnosticsOpen = false; return true }
        val s = stacks[tab] ?: return false
        if (s.size > 1) { stacks[tab] = s.dropLast(1); return true }
        if (tab != Tab.HOME) { tab = Tab.HOME; return true }
        return false
    }

    fun selectTab(t: Tab) {
        if (t == tab) stacks[t] = listOf(Screen.Root) else tab = t
    }

    fun openAlbum(id: String?) { id?.let { open(Screen.AlbumS(it, api.cachedAlbum(it))) } }
    fun openArtist(id: String?) { id?.let { open(Screen.ArtistS(it, api.cachedArtist(it))) } }

    // ---------- Login ----------

    fun loginUri(): Uri = auth.auth.initializeLogin(
        BuildConfig.TIDAL_REDIRECT_URI,
        LoginConfig(customParams = setOf(QueryParameter(key = "appMode", value = "android"))),
    )

    fun finishLogin(uri: Uri) {
        val query = uri.encodedQuery ?: run { loginError = "La respuesta del login vino vacía"; return }
        if (uri.getQueryParameter("error") != null) {
            loginError = "TIDAL rechazó el login: ${uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")}"
            return
        }
        loggingIn = true
        viewModelScope.launch {
            val r = runCatching { auth.auth.finalizeLogin(query) }
            loggingIn = false
            r.onSuccess { res ->
                uy.gero.marea.DiagLog.add("Login", "Resultado: $res")
                if (res.isSuccess) { loggedIn = true; loginError = null }
                else loginError = "No se pudo completar el login (${res})"
            }.onFailure { loginError = "No se pudo completar el login: ${it.message}" }
        }
    }

    fun logout() {
        viewModelScope.launch {
            player.release()
            runCatching { auth.auth.logout() }
            loggedIn = false
            listOf(playlists, albums, artists, tracks).forEach { it.items.clear() }
        }
    }

    // ---------- Favoritos ----------

    fun isFavorite(t: Track) = favoriteTrackIds[t.id] == true

    fun toggleFavorite(t: Track) {
        val now = !isFavorite(t)
        favoriteTrackIds[t.id] = now
        viewModelScope.launch {
            runCatching { api.setTrackFavorite(t.id, now) }
                .onSuccess {
                    message = if (now) "Agregado a tus canciones" else "Quitado de tus canciones"
                    if (now) tracks.items.add(0, t) else tracks.items.removeAll { it.id == t.id }
                    loadedLists["liked"]?.tracks?.let { l -> if (now) l.add(0, t) else l.removeAll { it.id == t.id } }
                }
                .onFailure { favoriteTrackIds[t.id] = !now; message = "No pude cambiar el favorito: ${it.message}" }
        }
    }

    var addToPlaylistFor by mutableStateOf<Track?>(null)
    val ownedPlaylists = mutableStateListOf<Playlist>()
    var ownedLoading by mutableStateOf(false)

    fun openAddToPlaylist(t: Track) {
        addToPlaylistFor = t
        if (ownedPlaylists.isEmpty() && !ownedLoading) {
            ownedLoading = true
            viewModelScope.launch {
                runCatching { api.ownedPlaylists() }.onSuccess { ownedPlaylists.clear(); ownedPlaylists.addAll(it) }
                    .onFailure { message = "No pude traer tus playlists: ${it.message}" }
                ownedLoading = false
            }
        }
    }

    fun addTrackToPlaylist(t: Track, p: Playlist) {
        addToPlaylistFor = null
        viewModelScope.launch {
            runCatching { api.addToPlaylist(p.id, listOf(t.id)) }
                .onSuccess { message = "Agregado a ${p.name}"; invalidatePlaylist(p.id) }
                .onFailure { message = "No pude agregarlo: ${it.message}" }
        }
    }

    fun createPlaylistWith(t: Track, name: String) {
        addToPlaylistFor = null
        viewModelScope.launch {
            runCatching {
                val id = api.createPlaylist(name) ?: error("TIDAL no devolvió la playlist")
                api.addToPlaylist(id, listOf(t.id))
                id
            }.onSuccess { id ->
                message = "Creé \"$name\" con el tema"
                ownedPlaylists.clear(); playlists.refresh()
            }.onFailure { message = "No pude crear la playlist: ${it.message}" }
        }
    }

    fun isFavoriteAlbum(a: Album) = favoriteAlbumIds[a.id] == true

    fun toggleFavoriteAlbum(a: Album) {
        val now = !isFavoriteAlbum(a)
        favoriteAlbumIds[a.id] = now
        viewModelScope.launch {
            runCatching { api.setAlbumFavorite(a.id, now) }
                .onSuccess {
                    message = if (now) "Álbum guardado" else "Álbum quitado"
                    if (now) albums.items.add(0, a) else albums.items.removeAll { it.id == a.id }
                }
                .onFailure { favoriteAlbumIds[a.id] = !now; message = "No pude cambiarlo: ${it.message}" }
        }
    }

    fun playAllFavorites(shuffle: Boolean) {
        // Arranca con lo cargado y sigue trayendo el resto en segundo plano
        val loaded = tracks.items.toList()
        val pages: (suspend () -> List<Track>?)? = if (tracks.done) null else suspend {
            val before = tracks.items.size
            tracks.loadAll(maxPages = 1)
            if (tracks.items.size == before && tracks.done) null else tracks.items.drop(before)
        }
        player.playProgressive(loaded, shuffle, "Tus canciones", pages = pages)
    }

}
