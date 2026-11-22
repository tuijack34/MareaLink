package uy.gero.marea

import android.util.Log
import com.tidal.sdk.auth.CredentialsProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente mínimo para la API pública de TIDAL (openapi.tidal.com/v2, formato JSON:API).
 * Trae IDs de las listas y después "hidrata" en lotes con cache en memoria.
 */
class TidalApi(private val credentials: CredentialsProvider) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile var country: String = "UY"
        private set
    @Volatile private var countryLoaded = false

    private val trackCache = ConcurrentHashMap<String, Track>()
    private val albumCache = ConcurrentHashMap<String, Album>()
    private val artistCache = ConcurrentHashMap<String, Artist>()
    private val playlistCache = ConcurrentHashMap<String, Playlist>()

    // ---------- HTTP ----------

    private suspend fun token(): String =
        credentials.getCredentials().successData?.token
            ?: throw ApiException(401, "La sesión expiró, volvé a iniciar sesión")

    private fun resolve(pathOrUrl: String): String = when {
        pathOrUrl.startsWith("http") -> pathOrUrl
        pathOrUrl.startsWith("/v2/") -> "https://openapi.tidal.com$pathOrUrl"
        pathOrUrl.startsWith("/") -> "$BASE$pathOrUrl"
        else -> "$BASE/$pathOrUrl"
    }

    private suspend fun request(
        method: String,
        pathOrUrl: String,
        params: List<Pair<String, String>> = emptyList(),
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = resolve(pathOrUrl).toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        var attempt = 0
        while (true) {
            val reqBody = body?.toString()?.toRequestBody(JSON_API)
            val builder = Request.Builder().url(url)
                .header("Authorization", "Bearer ${token()}")
                .header("Accept", "application/vnd.api+json")
                .method(method, reqBody)
            if (method != "GET") builder.header("Idempotency-Key", UUID.randomUUID().toString())
            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 429 && attempt < 3) {
                    attempt++
                    delay(800L * attempt)
                    return@use
                }
                if (!resp.isSuccessful) {
                    Log.w(TAG, "$method $url -> ${resp.code}: ${text.take(500)}")
                    DiagLog.add("API", "$method ${url.encodedPath}?${url.encodedQuery?.take(160)} → ${resp.code}: ${text.take(300)}")
                    throw ApiException(resp.code, errorMessage(resp.code, text))
                }
                return@withContext if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        JSONObject()
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONArray("errors")?.optJSONObject(0)?.let {
                it.optString("detail").ifBlank { it.optString("title") }
            }
        }.getOrNull()
        return "Error $code" + if (!detail.isNullOrBlank()) ": $detail" else ""
    }

    private suspend fun get(path: String, params: List<Pair<String, String>> = emptyList()) =
        Doc(request("GET", path, params))

    private suspend fun ensureCountry() {
        if (countryLoaded) return
        runCatching {
            val doc = get("users/me")
            doc.data.firstOrNull()?.attrs()?.optString("country")?.takeIf { it.length == 2 }?.let { country = it }
        }.onFailure { DiagLog.add("API", "users/me falló: ${it.message}") }
        DiagLog.add("API", "País de la cuenta: $country")
        countryLoaded = true
    }

    private fun cc() = listOf("countryCode" to country)

    // ---------- Hidratación en lote ----------

    suspend fun tracks(ids: List<String>): List<Track> {
        ensureCountry()
        val missing = ids.distinct().filter { !trackCache.containsKey(it) }
        for (chunk in missing.chunked(CHUNK)) {
            val doc = get("tracks", chunk.map { "filter[id]" to it } + listOf("include" to "artists", "include" to "albums") + cc())
            doc.included.values.filter { it.optString("type") == "artists" }.forEach { cacheArtistLite(it) }
            val albumIds = doc.data.mapNotNull { it.relIds("albums").firstOrNull() }.distinct()
            runCatching { albums(albumIds) }
            doc.data.forEach { t -> parseTrack(t, doc)?.let { trackCache[it.id] = it } }
        }
        return ids.mapNotNull { trackCache[it] }
    }

    suspend fun albums(ids: List<String>): List<Album> {
        ensureCountry()
        val missing = ids.distinct().filter { !albumCache.containsKey(it) }
        for (chunk in missing.chunked(CHUNK)) {
            val doc = get("albums", chunk.map { "filter[id]" to it } + listOf("include" to "artists", "include" to "coverArt") + cc())
            doc.data.forEach { a -> parseAlbum(a, doc).let { albumCache[it.id] = it } }
        }
        return ids.mapNotNull { albumCache[it] }
    }

    suspend fun artists(ids: List<String>): List<Artist> {
        ensureCountry()
        val missing = ids.distinct().filter { artistCache[it]?.picture == null }
        for (chunk in missing.chunked(CHUNK)) {
            val doc = get("artists", chunk.map { "filter[id]" to it } + listOf("include" to "profileArt") + cc())
            doc.data.forEach { a ->
                val art = doc.artwork(a, "profileArt")
                artistCache[a.optString("id")] = Artist(
                    id = a.optString("id"),
                    name = a.attrs().optString("name"),
                    picture = art.url(320),
                    pictureLarge = art.url(750),
                )
            }
        }
        return ids.mapNotNull { artistCache[it] }
    }

    suspend fun playlists(ids: List<String>): List<Playlist> {
        ensureCountry()
        val missing = ids.distinct().filter { !playlistCache.containsKey(it) }
        for (chunk in missing.chunked(CHUNK)) {
            val doc = get("playlists", chunk.map { "filter[id]" to it } + listOf("include" to "coverArt") + cc())
            doc.data.forEach { p ->
                val at = p.attrs()
                val art = doc.artwork(p, "coverArt")
                playlistCache[p.optString("id")] = Playlist(
                    id = p.optString("id"),
                    name = at.optString("name"),
                    description = at.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    numberOfItems = at.optInt("numberOfItems", -1).takeIf { it >= 0 },
                    cover = art.url(320),
                    coverLarge = art.url(750),
                    type = at.optString("playlistType").takeIf { it.isNotBlank() },
                )
            }
        }
        return ids.mapNotNull { playlistCache[it] }
    }

    fun cachedTrack(id: String) = trackCache[id]
    fun cachedAlbum(id: String) = albumCache[id]
    fun cachedArtist(id: String) = artistCache[id]
    fun cachedPlaylist(id: String) = playlistCache[id]

    // ---------- Colección del usuario ----------

    private suspend fun idsPage(path: String, type: String, params: List<Pair<String, String>>, next: String?): Pair<List<String>, String?> {
        val doc = if (next != null) get(next) else get(path, params)
        return doc.data.filter { it.optString("type") == type }.map { it.optString("id") } to doc.next
    }

    suspend fun favoriteTracks(next: String? = null): Page<Track> {
        val (ids, n) = idsPage("userCollectionTracks/me/relationships/items", "tracks", listOf("sort" to "-addedAt"), next)
        return Page(tracks(ids), n)
    }

    suspend fun favoriteAlbums(next: String? = null): Page<Album> {
        val (ids, n) = idsPage("userCollectionAlbums/me/relationships/items", "albums", listOf("sort" to "-addedAt"), next)
        return Page(albums(ids), n)
    }

    suspend fun favoriteArtists(next: String? = null): Page<Artist> {
        val (ids, n) = idsPage("userCollectionArtists/me/relationships/items", "artists", listOf("sort" to "-addedAt"), next)
        return Page(artists(ids), n)
    }

    suspend fun myPlaylists(next: String? = null): Page<Playlist> {
        val (ids, n) = idsPage(
            "userCollectionPlaylists/me/relationships/items", "playlists",
            listOf("collectionView" to "FLAT", "sort" to "-addedAt"), next,
        )
        return Page(playlists(ids), n)
    }

    suspend fun setTrackFavorite(id: String, favorite: Boolean) =
        mutateCollection("userCollectionTracks", "tracks", id, favorite)

    suspend fun setAlbumFavorite(id: String, favorite: Boolean) =
        mutateCollection("userCollectionAlbums", "albums", id, favorite)

    suspend fun setArtistFavorite(id: String, favorite: Boolean) =
        mutateCollection("userCollectionArtists", "artists", id, favorite)

    private suspend fun mutateCollection(collection: String, type: String, id: String, add: Boolean) {
        val body = JSONObject().put("data", JSONArray().put(JSONObject().put("id", id).put("type", type)))
        request(if (add) "POST" else "DELETE", "$collection/me/relationships/items", cc(), body)
    }

    // ---------- Detalle ----------

    suspend fun albumTracks(albumId: String): List<Track> {
        ensureCountry()
        val ordered = mutableListOf<Pair<String, Int?>>()
        var doc = get("albums/$albumId/relationships/items", cc())
        var pages = 0
        while (true) {
            doc.data.filter { it.optString("type") == "tracks" }.forEach {
                ordered += it.optString("id") to it.optJSONObject("meta")?.optInt("trackNumber")
            }
            val next = doc.next ?: break
            if (++pages > 10) break
            doc = get(next)
        }
        val map = tracks(ordered.map { it.first }).associateBy { it.id }
        return ordered.mapNotNull { (id, n) -> map[id]?.copy(trackNumber = n) }
    }

    /**
     * Carga TODOS los temas de una lista (playlist o tus me gusta) rápido: las páginas de IDs
     * se piden en fila (son livianas) y la info de cada página se completa en paralelo.
     * [onChunk] recibe los temas en orden a medida que están listos.
     */
    suspend fun loadAllTracksFast(path: String, params: List<Pair<String, String>>, onChunk: suspend (List<Track>) -> Unit) = kotlinx.coroutines.coroutineScope {
        ensureCountry()
        val sem = kotlinx.coroutines.sync.Semaphore(4)
        val parts = mutableListOf<kotlinx.coroutines.Deferred<List<Track>>>()
        var emitted = 0
        var doc = get(path, params)
        var guard = 0
        while (true) {
            val ids = doc.data.filter { it.optString("type") == "tracks" }.map { it.optString("id") }
            parts += async { sem.acquire(); try { runCatching { tracks(ids) }.getOrDefault(emptyList()) } finally { sem.release() } }
            while (emitted < parts.size && parts[emitted].isCompleted) onChunk(parts[emitted++].await())
            val next = doc.next ?: break
            if (++guard > 200) break
            doc = get(next)
        }
        while (emitted < parts.size) onChunk(parts[emitted++].await())
    }

    /** Solo los IDs de una colección (sin pedir los datos de cada tema): sirve para saber qué tiene "me gusta". */
    suspend fun collectionIds(path: String, type: String = "tracks", onPage: (List<String>) -> Unit) {
        ensureCountry()
        var doc = get(path, emptyList())
        var guard = 0
        while (true) {
            onPage(doc.data.filter { it.optString("type") == type }.map { it.optString("id") })
            val next = doc.next ?: break
            if (++guard > 500) break
            doc = get(next)
        }
    }

    /** Recomendaciones personales de TIDAL (mixes diarios, descubrimiento, novedades). */
    suspend fun recommendations(): List<RecRow> = kotlinx.coroutines.coroutineScope {
        ensureCountry()
        listOf(
            "Tus mixes diarios" to "userDailyMixes/me/relationships/items",
            "Para descubrir" to "userDiscoveryMixes/me/relationships/items",
            "Novedades para vos" to "userNewReleaseMixes/me/relationships/items",
        ).map { (title, path) ->
            async {
                runCatching {
                    val doc = get(path, emptyList())
                    val pl = doc.data.filter { it.optString("type") == "playlists" }.map { it.optString("id") }
                    val tr = doc.data.filter { it.optString("type") == "tracks" }.map { it.optString("id") }
                    val al = doc.data.filter { it.optString("type") == "albums" }.map { it.optString("id") }
                    DiagLog.add("Inicio", "$title: ${pl.size} mixes, ${tr.size} temas, ${al.size} álbumes")
                    RecRow(title, playlists(pl.take(20)), tracks(tr.take(20)), albums(al.take(20)))
                }.getOrElse { DiagLog.add("Inicio", "$title falló: ${it.message}"); null }
            }
        }.mapNotNull { it.await() }.filter { it.playlists.isNotEmpty() || it.tracks.isNotEmpty() || it.albums.isNotEmpty() }
    }

    suspend fun userId(): String? = credentials.getCredentials().successData?.userId

    /** Playlists creadas por vos (a las que podés agregar temas). */
    suspend fun ownedPlaylists(): List<Playlist> {
        ensureCountry()
        val uid = userId() ?: return emptyList()
        val ids = mutableListOf<String>()
        var doc = get("playlists", listOf("filter[owners.id]" to uid) + cc())
        var pages = 0
        while (true) {
            doc.data.forEach { p -> ids += p.optString("id") }
            val next = doc.next ?: break
            if (++pages > 10) break
            doc = get(next)
        }
        return playlists(ids)
    }

    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        val data = JSONArray()
        trackIds.forEach { data.put(JSONObject().put("id", it).put("type", "tracks")) }
        request("POST", "playlists/$playlistId/relationships/items", cc(), JSONObject().put("data", data).put("meta", JSONObject().put("onDuplicates", "SKIP")))
        playlistCache.remove(playlistId)
    }

    suspend fun createPlaylist(name: String): String? {
        val body = JSONObject().put("data", JSONObject().put("type", "playlists").put("attributes", JSONObject().put("name", name).put("accessType", "UNLISTED")))
        val res = request("POST", "playlists", cc(), body)
        return res.optJSONObject("data")?.optString("id")
    }

    suspend fun playlistTracks(playlistId: String, next: String? = null): Page<Track> {
        ensureCountry()
        val doc = if (next != null) get(next) else get("playlists/$playlistId/relationships/items", cc())
        val ids = doc.data.filter { it.optString("type") == "tracks" }.map { it.optString("id") }
        return Page(tracks(ids), doc.next)
    }

    suspend fun artistTopTracks(artistId: String): List<Track> {
        ensureCountry()
        val doc = get("artists/$artistId/relationships/tracks", listOf("collapseBy" to "FINGERPRINT") + cc())
        return tracks(doc.data.filter { it.optString("type") == "tracks" }.map { it.optString("id") }.take(20))
    }

    suspend fun artistAlbums(artistId: String): List<Album> {
        ensureCountry()
        val ids = mutableListOf<String>()
        var doc = get("artists/$artistId/relationships/albums", cc())
        var pages = 0
        while (true) {
            ids += doc.data.filter { it.optString("type") == "albums" }.map { it.optString("id") }
            val next = doc.next ?: break
            if (++pages > 4) break
            doc = get(next)
        }
        return albums(ids).sortedByDescending { it.releaseDate ?: "" }
    }

    suspend fun artist(artistId: String): Artist? = artists(listOf(artistId)).firstOrNull()

    /** Diagnóstico: le pide a TIDAL el manifiesto del tema y devuelve qué tipo de stream daría. */
    private val formatCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Formato exacto que TIDAL ofrece para el tema (ej. "24-bit · 96 kHz"), sacado del manifiesto MPEG-DASH.
     * Es lo máximo disponible para ese tema; lo que suena depende de la calidad que tengas elegida en TIDAL.
     * Devuelve null si TIDAL no lo dice (por ejemplo si solo da la vista previa).
     */
    suspend fun audioFormat(trackId: String): String? {
        formatCache[trackId]?.let { return it.ifEmpty { null } }
        val r = runCatching {
            val doc = get(
                "trackManifests/$trackId",
                listOf(
                    "adaptive" to "false", "manifestType" to "MPEG_DASH", "uriScheme" to "DATA", "usage" to "PLAYBACK",
                    "formats" to "FLAC", "formats" to "FLAC_HIRES",
                ),
            )
            val at = doc.data.firstOrNull()?.attrs() ?: return@runCatching null
            val formats = at.optJSONArray("formats")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val uri = at.optString("uri")
            val mpd = when {
                uri.startsWith("data:") -> runCatching { String(android.util.Base64.decode(uri.substringAfter(","), android.util.Base64.DEFAULT)) }.getOrDefault("")
                else -> ""
            }
            val isFlac = mpd.contains("codecs=\"flac\"", ignoreCase = true) || mpd.contains("flac", ignoreCase = true)
            val rate = Regex("audioSamplingRate=\"(\\d+)\"").findAll(mpd).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
            val bits = Regex("(?:bitDepth|bitsPerSample)=\"(\\d+)\"", RegexOption.IGNORE_CASE).find(mpd)?.groupValues?.get(1)?.toIntOrNull()
            DiagLog.add("Calidad", "Tema $trackId: presentación=${at.optString("trackPresentation")} formatos=$formats flac=$isFlac rate=$rate bits=$bits")
            if (!isFlac || rate == null) return@runCatching null
            val depth = bits ?: if ("FLAC_HIRES" in formats) 24 else 16
            "$depth-bit · ${khz(rate)}"
        }.onFailure { DiagLog.add("Calidad", "Tema $trackId: ${it.message}") }.getOrNull()
        formatCache[trackId] = r ?: ""
        return r
    }

    private fun khz(hz: Int): String {
        val k = hz / 1000.0
        return if (k == k.toLong().toDouble()) "${k.toLong()} kHz" else "%.1f kHz".format(java.util.Locale.US, k)
    }

    suspend fun probeManifest(trackId: String): String {
        val c = credentials.getCredentials().successData
        val head = "Usuario=${c?.userId} scopes=${c?.grantedScopes?.sorted()} cliente=${c?.clientId}"
        return runCatching {
            val doc = get(
                "trackManifests/$trackId",
                listOf(
                    "adaptive" to "false", "manifestType" to "MPEG_DASH", "uriScheme" to "HTTPS", "usage" to "PLAYBACK",
                    "formats" to "HEAACV1", "formats" to "AACLC", "formats" to "FLAC", "formats" to "FLAC_HIRES",
                ),
            )
            val at = doc.data.firstOrNull()?.attrs()
            "$head\nManifiesto $trackId: presentación=${at?.optString("trackPresentation")} motivo=${at?.optString("previewReason")} formatos=${at?.optJSONArray("formats")}"
        }.getOrElse { "$head\nManifiesto $trackId falló: ${it.message}" }
    }

    /** Letra del tema, tal como la sirve TIDAL. lrc = sincronizada (si existe). */
    suspend fun lyrics(trackId: String): Lyrics? {
        ensureCountry()
        val doc = get("tracks/$trackId/relationships/lyrics", listOf("include" to "lyrics"))
        val id = doc.data.firstOrNull()?.optString("id")
        if (id.isNullOrBlank()) {
            DiagLog.add("Letra", "Tema $trackId: TIDAL no devolvió letra")
            return null
        }
        val res = doc.res("lyrics", id) ?: get("lyrics/$id").data.firstOrNull()
        val at = res?.attrs() ?: return null
        val text = at.optString("text").takeIf { it.isNotBlank() && it != "null" }
        val lrc = at.optString("lrcText").takeIf { it.isNotBlank() && it != "null" }
        DiagLog.add("Letra", "Tema $trackId: estado=${at.optString("technicalStatus")} texto=${text != null} sincronizada=${lrc != null}")
        if (text == null && lrc == null) return null
        return Lyrics(
            plain = text,
            synced = lrc?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() },
            rtl = at.optString("direction") == "RIGHT_TO_LEFT",
        )
    }

    /**
     * Música sugerida a partir de un tema: primero la "radio" del tema (un mix),
     * si no hay, temas sugeridos y por último similares.
     */
    suspend fun suggestions(trackId: String): List<Track> {
        ensureCountry()
        runCatching {
            val radio = get("tracks/$trackId/relationships/radio", emptyList())
            val mix = radio.data.firstOrNull { it.optString("type") == "playlists" }?.optString("id")
            if (mix != null) {
                val tracks = playlistTracks(mix).items
                if (tracks.isNotEmpty()) return tracks.filter { it.id != trackId }
            }
        }.onFailure { DiagLog.add("API", "radio falló: ${it.message}") }
        for (rel in listOf("suggestedTracks", "similarTracks")) {
            runCatching {
                val doc = get("tracks/$trackId/relationships/$rel", cc())
                val ids = doc.data.filter { it.optString("type") == "tracks" }.map { it.optString("id") }
                if (ids.isNotEmpty()) return tracks(ids.take(40)).filter { it.id != trackId }
            }.onFailure { DiagLog.add("API", "$rel falló: ${it.message}") }
        }
        return emptyList()
    }

    suspend fun artistRadio(artistId: String): List<Track> {
        ensureCountry()
        val radio = get("artists/$artistId/relationships/radio", cc())
        val mix = radio.data.firstOrNull { it.optString("type") == "playlists" }?.optString("id")
            ?: return artistTopTracks(artistId).shuffled()
        return playlistTracks(mix).items
    }

    suspend fun search(query: String): SearchResult {
        ensureCountry()
        val doc = get(
            "searchResults",
            listOf("filter[query]" to query, "explicitFilter" to "INCLUDE") +
                listOf("tracks", "albums", "artists", "playlists").map { "include" to it } + cc(),
        )
        val root = doc.data.firstOrNull() ?: return SearchResult(emptyList(), emptyList(), emptyList(), emptyList())
        // Guardamos lo que vino incluido para no pedirlo de nuevo si ya alcanza
        return SearchResult(
            tracks = runCatching { tracks(root.relIds("tracks").take(20)) }.getOrDefault(emptyList()),
            albums = runCatching { albums(root.relIds("albums").take(20)) }.getOrDefault(emptyList()),
            artists = runCatching { artists(root.relIds("artists").take(20)) }.getOrDefault(emptyList()),
            playlists = runCatching { playlists(root.relIds("playlists").take(20)) }.getOrDefault(emptyList()),
        )
    }

    // ---------- Parsing ----------

    private fun cacheArtistLite(a: JSONObject) {
        val id = a.optString("id")
        if (id.isNotBlank() && !artistCache.containsKey(id)) {
            artistCache[id] = Artist(id, a.attrs().optString("name"), null, null)
        }
    }

    private fun artistRefs(res: JSONObject, doc: Doc): List<ArtistRef> =
        res.relIds("artists").mapNotNull { id ->
            val name = doc.res("artists", id)?.attrs()?.optString("name") ?: artistCache[id]?.name
            name?.let { ArtistRef(id, it) }
        }

    private fun parseAlbum(a: JSONObject, doc: Doc): Album {
        val at = a.attrs()
        a.relIds("artists").forEach { id -> doc.res("artists", id)?.let { cacheArtistLite(it) } }
        val art = doc.artwork(a, "coverArt")
        return Album(
            id = a.optString("id"),
            title = at.optString("title"),
            artists = artistRefs(a, doc),
            cover = art.url(320),
            coverLarge = art.url(1080),
            releaseDate = at.optString("releaseDate").takeIf { it.isNotBlank() && it != "null" },
            numberOfItems = at.optInt("numberOfItems", -1).takeIf { it >= 0 },
            tags = at.optJSONArray("mediaTags").strings(),
            type = at.optString("albumType").takeIf { it.isNotBlank() },
        )
    }

    private fun parseTrack(t: JSONObject, doc: Doc): Track? {
        val id = t.optString("id").ifBlank { return null }
        val at = t.attrs()
        val albumId = t.relIds("albums").firstOrNull()
        val album = albumId?.let { albumCache[it] }
        val albumTitle = album?.title ?: albumId?.let { doc.res("albums", it)?.attrs()?.optString("title") }
        return Track(
            id = id,
            title = at.optString("title"),
            version = at.optString("version").takeIf { it.isNotBlank() && it != "null" },
            durationSec = parseIsoDuration(at.optString("duration")),
            explicit = at.optBoolean("explicit"),
            tags = at.optJSONArray("mediaTags").strings(),
            artists = artistRefs(t, doc),
            albumId = albumId,
            albumTitle = albumTitle,
            cover = album?.cover,
            coverLarge = album?.coverLarge,
        )
    }

    companion object {
        private const val TAG = "TidalApi"
        private const val BASE = "https://openapi.tidal.com/v2"
        private const val CHUNK = 20
        private val JSON_API = "application/vnd.api+json".toMediaType()

        fun parseLrc(lrc: String): List<LyricLine> {
            val re = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
            return lrc.lineSequence().flatMap { line ->
                val stamps = re.findAll(line).toList()
                if (stamps.isEmpty()) return@flatMap emptySequence()
                val text = line.substring(stamps.last().range.last + 1).trim()
                stamps.asSequence().map { m ->
                    val (mm, ss, frac) = m.destructured
                    val fracMs = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
                    LyricLine(mm.toLong() * 60_000 + ss.toLong() * 1000 + fracMs, text)
                }
            }.sortedBy { it.timeMs }.toList()
        }

        fun parseIsoDuration(s: String?): Int {
            if (s.isNullOrBlank()) return 0
            val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?""").find(s) ?: return 0
            val (d, h, mi, se) = m.destructured
            return (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 +
                (mi.toIntOrNull() ?: 0) * 60 + (se.toDoubleOrNull()?.toInt() ?: 0)
        }
    }
}

// ---------- Helpers JSON:API ----------

class Artwork(private val files: List<Pair<Int, String>>) {
    /** Devuelve el archivo más chico que sea >= min de ancho (o el más grande disponible). */
    fun url(min: Int): String? {
        if (files.isEmpty()) return null
        val sorted = files.sortedBy { it.first }
        return (sorted.firstOrNull { it.first >= min } ?: sorted.last()).second
    }
}

class Doc(json: JSONObject) {
    val data: List<JSONObject> = when (val d = json.opt("data")) {
        is JSONArray -> (0 until d.length()).mapNotNull { d.optJSONObject(it) }
        is JSONObject -> listOf(d)
        else -> emptyList()
    }
    val included: Map<String, JSONObject> = json.optJSONArray("included")?.let { arr ->
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .associateBy { "${it.optString("type")}:${it.optString("id")}" }
    } ?: emptyMap()
    val next: String? = json.optJSONObject("links")?.optString("next")?.takeIf { it.isNotBlank() && it != "null" }

    fun res(type: String, id: String): JSONObject? = included["$type:$id"]

    fun artwork(owner: JSONObject, rel: String): Artwork {
        val artId = owner.relIds(rel).firstOrNull() ?: return Artwork(emptyList())
        val files = res("artworks", artId)?.attrs()?.optJSONArray("files") ?: return Artwork(emptyList())
        return Artwork((0 until files.length()).mapNotNull { i ->
            val f = files.optJSONObject(i) ?: return@mapNotNull null
            val href = f.optString("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            (f.optJSONObject("meta")?.optInt("width") ?: 0) to href
        })
    }
}

fun JSONObject.attrs(): JSONObject = optJSONObject("attributes") ?: JSONObject()

fun JSONObject.relIds(name: String): List<String> {
    val d = optJSONObject("relationships")?.optJSONObject(name)?.opt("data") ?: return emptyList()
    return when (d) {
        is JSONArray -> (0 until d.length()).mapNotNull { d.optJSONObject(it)?.optString("id") }
        is JSONObject -> listOf(d.optString("id"))
        else -> emptyList()
    }
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }
