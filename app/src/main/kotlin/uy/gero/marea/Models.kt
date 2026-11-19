package uy.gero.marea

data class ArtistRef(val id: String, val name: String)

data class Track(
    val id: String,
    val title: String,
    val version: String?,
    val durationSec: Int,
    val explicit: Boolean,
    val tags: List<String>,
    val artists: List<ArtistRef>,
    val albumId: String?,
    val albumTitle: String?,
    val cover: String?,
    val coverLarge: String?,
    val trackNumber: Int? = null,
) {
    val fullTitle: String get() = if (version.isNullOrBlank()) title else "$title ($version)"
    val artistLine: String get() = artists.joinToString(", ") { it.name }
    val isHiRes: Boolean get() = tags.any { it.contains("HIRES", true) || it.contains("HI_RES", true) }
    val isAtmos: Boolean get() = tags.any { it.contains("ATMOS", true) }
}

data class Album(
    val id: String,
    val title: String,
    val artists: List<ArtistRef>,
    val cover: String?,
    val coverLarge: String?,
    val releaseDate: String?,
    val numberOfItems: Int?,
    val tags: List<String>,
    val type: String?,
) {
    val artistLine: String get() = artists.joinToString(", ") { it.name }
    val year: String? get() = releaseDate?.take(4)
    val isHiRes: Boolean get() = tags.any { it.contains("HIRES", true) || it.contains("HI_RES", true) }
}

data class Artist(val id: String, val name: String, val picture: String?, val pictureLarge: String?)

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val numberOfItems: Int?,
    val cover: String?,
    val coverLarge: String?,
    val type: String?,
)

data class Page<T>(val items: List<T>, val next: String?)

data class SearchResult(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
)

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val plain: String?, val synced: List<LyricLine>?, val rtl: Boolean, val source: String = "TIDAL")

class ApiException(val code: Int, message: String) : Exception(message)

data class RecRow(val title: String, val playlists: List<Playlist>, val tracks: List<Track>, val albums: List<Album>)
