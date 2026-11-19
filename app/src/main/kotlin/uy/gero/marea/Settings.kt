package uy.gero.marea

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cómo le pedimos a la app oficial de TIDAL que reproduzca un tema. */
enum class PlayMethod(val label: String, val detail: String) {
    AUTO("Automático", "Prueba los métodos en orden hasta que uno funcione"),
    URI("Por link (sin salir)", "Le pasa el link del tema a TIDAL por la sesión de medios"),
    SEARCH("Por búsqueda (sin salir)", "Le pide a TIDAL que busque y reproduzca \"tema + artista\""),
    DEEPLINK("Abriendo TIDAL", "Abre el tema en la app de TIDAL (cambia de pantalla)"),
}

data class AppSettings(
    val playMethod: PlayMethod = PlayMethod.AUTO,
    /** Si Marea maneja la cola (manda el siguiente tema cuando termina el actual) o la deja a TIDAL. */
    val mareaQueue: Boolean = true,
    /** Álbumes y playlists enteros se los pasamos a TIDAL (instantáneo, cola de TIDAL). */
    val containerToTidal: Boolean = true,
    val lrclib: Boolean = true,
    /** Opacidad del fondo de los widgets (0-100), como en One UI. */
    val widgetOpacity: Int = 55,
    val pureBlack: Boolean = false,
    val coverColors: Boolean = true,
    val accent: Int = 0,
    val showQueuePane: Boolean = true,
    val gridSize: Int = 1,
    val autoSuggestions: Boolean = true,
    /** Reproductor a pantalla completa con el Fold abierto: false = letra al costado, true = letra abajo. */
    val playerStacked: Boolean = false,
    /** Botones de auriculares/reloj controlan la cola de Marea (reproduce silencio para recibirlos). */
    val headsetBridge: Boolean = true,
    /** Panel del reproductor (Fold abierto): false = arriba fijo y solo scrollea la cola; true = scrollea todo. */
    val paneScrollAll: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("marea", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    private fun load() = AppSettings(
        playMethod = runCatching { PlayMethod.valueOf(prefs.getString("playMethod", null) ?: "AUTO") }.getOrDefault(PlayMethod.AUTO),
        mareaQueue = prefs.getBoolean("mareaQueue", true),
        containerToTidal = prefs.getBoolean("containerToTidal", true),
        lrclib = prefs.getBoolean("lrclib", true),
        widgetOpacity = prefs.getInt("widgetOpacity", 55),
        pureBlack = prefs.getBoolean("pureBlack", false),
        coverColors = prefs.getBoolean("coverColors", true),
        accent = prefs.getInt("accent", 1),
        showQueuePane = prefs.getBoolean("showQueuePane", true),
        gridSize = prefs.getInt("gridSize", 1),
        autoSuggestions = prefs.getBoolean("autoSuggestions", true),
        playerStacked = prefs.getBoolean("playerStacked", false),
        headsetBridge = prefs.getBoolean("headsetBridge", true),
        paneScrollAll = prefs.getBoolean("paneScrollAll", false),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        prefs.edit()
            .putString("playMethod", s.playMethod.name)
            .putBoolean("mareaQueue", s.mareaQueue)
            .putBoolean("containerToTidal", s.containerToTidal)
            .putBoolean("lrclib", s.lrclib)
            .putInt("widgetOpacity", s.widgetOpacity)
            .putBoolean("pureBlack", s.pureBlack)
            .putBoolean("coverColors", s.coverColors)
            .putInt("accent", s.accent)
            .putBoolean("showQueuePane", s.showQueuePane)
            .putInt("gridSize", s.gridSize)
            .putBoolean("autoSuggestions", s.autoSuggestions)
            .putBoolean("playerStacked", s.playerStacked)
            .putBoolean("headsetBridge", s.headsetBridge)
            .putBoolean("paneScrollAll", s.paneScrollAll)
            .apply()
        _state.value = s
    }
}
