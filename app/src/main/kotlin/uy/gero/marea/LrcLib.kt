package uy.gero.marea

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Letras desde LRCLIB (lrclib.net), una base abierta y comunitaria de letras sincronizadas.
 * Se usa cuando TIDAL no le da letras a la app.
 */
object LrcLib {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private const val UA = "MareaLink/0.2 (cliente personal)"
    private val cache = HashMap<String, Lyrics?>()

    suspend fun find(t: Track): Lyrics? {
        val key = "${t.title}|${t.artistLine}"
        if (cache.containsKey(key)) return cache[key]
        val artist = t.artists.firstOrNull()?.name.orEmpty()
        val result = runCatching { exact(t.title, artist, t.albumTitle, t.durationSec) }.getOrNull()
            ?: runCatching { search(t.title, artist, t.durationSec) }.getOrNull()
        DiagLog.add("Letra", "LRCLIB \"${t.title}\": ${if (result == null) "sin resultado" else if (result.synced != null) "sincronizada" else "texto"}")
        cache[key] = result
        return result
    }

    private suspend fun exact(title: String, artist: String, album: String?, dur: Int): Lyrics? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (!album.isNullOrBlank()) addQueryParameter("album_name", album) }
            .apply { if (dur > 0) addQueryParameter("duration", dur.toString()) }
            .build().toString()
        val body = fetch(url) ?: return null
        return parse(JSONObject(body))
    }

    private suspend fun search(title: String, artist: String, dur: Int): Lyrics? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build().toString()
        val body = fetch(url) ?: return null
        val arr = JSONArray(body)
        val items = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        // preferimos sincronizadas y con duración parecida
        val best = items.sortedWith(
            compareBy<JSONObject>({ it.optString("syncedLyrics").isBlank() }, { kotlin.math.abs(it.optDouble("duration", 0.0) - dur) }),
        ).firstOrNull { dur <= 0 || kotlin.math.abs(it.optDouble("duration", 0.0) - dur) < 8 } ?: return null
        return parse(best)
    }

    private fun parse(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental")) return null
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced == null && plain == null) return null
        return Lyrics(plain = plain, synced = synced?.let { TidalApi.parseLrc(it) }?.takeIf { it.isNotEmpty() }, rtl = false, source = "LRCLIB")
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.code == 404) null else if (!r.isSuccessful) throw IllegalStateException("LRCLIB ${r.code}") else r.body?.string()
        }
    }
}
