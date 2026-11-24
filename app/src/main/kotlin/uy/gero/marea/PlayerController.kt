package uy.gero.marea

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.text.Normalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode { OFF, ALL, ONE }

/** En Marea Link no sabemos el formato real del stream (lo maneja TIDAL); se deja por compatibilidad con la UI. */
data class StreamInfo(
    val quality: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val codec: String?,
    val bitRate: Int?,
    val isPreview: Boolean,
    val previewReason: String? = null,
) {
    val label: String get() = codec ?: ""
    val tier: String get() = quality ?: ""
}

data class PlayerUiState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationSec: Float = 0f,
    val stream: StreamInfo? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
    val sourceLabel: String? = null,
    val loadingSuggestions: Boolean = false,
    val manualCount: Int = 0,
    /** Hay sesión de TIDAL conectada. */
    val tidalConnected: Boolean = false,
    /** Falta el permiso de acceso a notificaciones (sin él no podemos ver ni controlar TIDAL). */
    val needsAccess: Boolean = false,
    /** true = Marea maneja la cola; false = sigue lo que haga TIDAL. */
    val driving: Boolean = false,
) {
    val current: Track? get() = queue.getOrNull(index)
    val upNext: List<Track> get() = if (index < 0) queue else queue.drop(index + 1)
}

/**
 * Marea Link: no reproduce nada. Le pide a la app oficial de TIDAL que reproduzca
 * y la controla a través de su sesión de medios (la misma que usan widgets y relojes).
 */
class PlayerController(private val app: MareaApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _position = MutableStateFlow(0f)
    val position: StateFlow<Float> = _position.asStateFlow()

    private val settings get() = app.settings.value
    private val msm by lazy { app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager }
    private val listenerComponent by lazy { ComponentName(app, MareaNotificationListener::class.java) }

    private var controller: MediaControllerCompat? = null
    private var originalQueue: List<Track> = emptyList()
    private var bulkJob: Job? = null
    private var verifyJob: Job? = null
    private var pollJob: Job? = null

    /** El tema que le pedimos a TIDAL y todavía no confirmamos que esté sonando. */
    private var pendingId: String? = null
    /** Método que ya comprobamos que funciona (en modo automático). */
    private var workingMethod: PlayMethod? = runCatching {
        app.getSharedPreferences("marea", Context.MODE_PRIVATE).getString("workingMethod", null)?.let { PlayMethod.valueOf(it) }
    }.getOrNull()
    private var confirmedAt = 0L
    /** Hasta cuándo ignorar posiciones (justo después de un cambio de tema TIDAL todavía informa la del anterior). */
    private var posValidFrom = 0L
    private var endHandledFor: String? = null
    private var pausedNearEndSince = 0L
    private var reclaimCount = 0
    /** Lo último que sonó (para no volver a sugerir lo mismo y para detectar si algo se repite en bucle). */
    private val recentTitles = ArrayDeque<String>()
    private val recentIds = LinkedHashSet<String>()
    /** Último punto conocido del tema actual, para saber si un cambio de tema fue "natural" (terminó). */
    private var lastPos = 0f
    private var lastDur = 0f
    private var lastMetaKey: String? = null
    private val enrichCache = HashMap<String, Track?>()

    // ---------- Guardar la cola (Android puede cerrar la app en segundo plano) ----------

    private val queueFile by lazy { File(app.filesDir, "cola.json") }
    private var saveJob: Job? = null
    /** Cola guardada de la última vez, para retomarla si TIDAL sigue con el mismo tema. */
    private var restored: PlayerUiState? = null

    private fun trackToJson(t: Track) = org.json.JSONObject().apply {
        put("id", t.id); put("title", t.title); put("version", t.version); put("dur", t.durationSec); put("explicit", t.explicit)
        put("tags", org.json.JSONArray(t.tags))
        put("artists", org.json.JSONArray().apply { t.artists.forEach { a -> put(org.json.JSONObject().put("id", a.id).put("name", a.name)) } })
        put("albumId", t.albumId); put("albumTitle", t.albumTitle); put("cover", t.cover); put("coverLarge", t.coverLarge)
    }

    private fun trackFromJson(o: org.json.JSONObject): Track {
        fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        val arts = o.optJSONArray("artists")
        return Track(
            id = o.optString("id"), title = o.optString("title"), version = str("version"), durationSec = o.optInt("dur"),
            explicit = o.optBoolean("explicit"), tags = o.optJSONArray("tags").strings(),
            artists = (0 until (arts?.length() ?: 0)).mapNotNull { arts?.optJSONObject(it) }.map { ArtistRef(it.optString("id"), it.optString("name")) },
            albumId = str("albumId"), albumTitle = str("albumTitle"), cover = str("cover"), coverLarge = str("coverLarge"),
        )
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            val st = _state.value
            if (!st.driving || st.queue.isEmpty()) return@launch
            val json = org.json.JSONObject()
                .put("index", st.index).put("shuffle", st.shuffle).put("manual", st.manualCount)
                .put("source", st.sourceLabel).put("repeat", st.repeat.name)
                .put("queue", org.json.JSONArray().apply { st.queue.forEach { put(trackToJson(it)) } })
            withContext(Dispatchers.IO) { runCatching { queueFile.writeText(json.toString()) } }
        }
    }

    private fun loadSaved() {
        restored = runCatching {
            val o = org.json.JSONObject(queueFile.readText())
            val arr = o.getJSONArray("queue")
            val q = (0 until arr.length()).map { trackFromJson(arr.getJSONObject(it)) }
            PlayerUiState(
                queue = q, index = o.optInt("index"), shuffle = o.optBoolean("shuffle"), manualCount = o.optInt("manual"),
                sourceLabel = o.optString("source").takeIf { it.isNotBlank() && it != "null" },
                repeat = runCatching { RepeatMode.valueOf(o.optString("repeat")) }.getOrDefault(RepeatMode.OFF), driving = true,
            )
        }.getOrNull()
        restored?.let { DiagLog.add("Link", "Cola guardada: ${it.queue.size} temas (iba por el ${it.index + 1})") }
    }

    init {
        loadSaved()
        // Guardamos la cola cada vez que cambia
        scope.launch {
            var last: Triple<Int, Int, Boolean>? = null
            _state.collect { st ->
                val k = Triple(st.queue.size, st.index, st.shuffle)
                if (k != last) { last = k; scheduleSave() }
            }
        }
    }

    init {
        // Mantener el widget al día cuando cambia el tema o play/pausa
        scope.launch {
            var last: Triple<String?, Boolean, String?>? = null
            _state.collect { st ->
                val k = Triple(st.current?.id, st.isPlaying, st.current?.cover)
                if (k != last) {
                    last = k
                    WidgetCover.update(app, st.current?.let { it.coverLarge ?: it.cover })
                    MareaWidget.refresh(app)
                }
            }
        }
    }

    // ---------- Conexión con TIDAL ----------

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        scope.launch { attach(list) }
    }
    private var listening = false

    /** Llamar al abrir la app y cuando vuelve a primer plano. */
    fun connect() {
        if (!hasAccess()) {
            _state.update { it.copy(needsAccess = true, tidalConnected = false) }
            return
        }
        _state.update { it.copy(needsAccess = false) }
        runCatching {
            if (!listening) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                listening = true
            }
            attach(msm.getActiveSessions(listenerComponent))
        }.onFailure { DiagLog.add("Link", "No pude leer sesiones: ${it.message}") }
        startPolling()
        if (controller == null) wakeTidal()
    }

    // ---------- Despertar a TIDAL en segundo plano ----------

    private var browser: android.support.v4.media.MediaBrowserCompat? = null
    private var wakeTried = 0L

    /**
     * Abre TIDAL "de fondo", sin mostrar su pantalla: nos conectamos a su servicio de medios
     * (el mismo que usa Android Auto). Eso arranca TIDAL y crea su sesión.
     */
    fun wakeTidal() {
        if (controller != null || SystemClock.elapsedRealtime() - wakeTried < 15_000) return
        wakeTried = SystemClock.elapsedRealtime()
        val svc = runCatching {
            app.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService").setPackage(TIDAL_PKG), 0)
                .firstOrNull()?.serviceInfo
        }.getOrNull()
        if (svc == null) { DiagLog.add("Link", "TIDAL no expone servicio de medios (o no está instalada)"); return }
        DiagLog.add("Link", "Despertando TIDAL de fondo (${svc.name})…")
        browser?.disconnect()
        browser = android.support.v4.media.MediaBrowserCompat(
            app, ComponentName(svc.packageName, svc.name),
            object : android.support.v4.media.MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    DiagLog.add("Link", "TIDAL aceptó la conexión de fondo (raíz: ${browser?.root})")
                    val b = browser ?: return
                    if (controller == null) runCatching {
                        val c = MediaControllerCompat(app, b.sessionToken)
                        controller = c
                        c.registerCallback(callback)
                        _state.update { it.copy(tidalConnected = true) }
                        onState(c.playbackState); onMetadata(c.metadata)
                    }
                }
                override fun onConnectionFailed() {
                    DiagLog.add("Link", "TIDAL rechazó la conexión de fondo; igual puede haber arrancado")
                    scope.launch { delay(1500); if (controller == null) runCatching { attach(msm.getActiveSessions(listenerComponent)) } }
                }
                override fun onConnectionSuspended() { DiagLog.add("Link", "Conexión de fondo con TIDAL suspendida") }
            },
            null,
        ).also { runCatching { it.connect() } }
        scope.launch {
            delay(4000)
            if (controller == null) runCatching { attach(msm.getActiveSessions(listenerComponent)) }
            if (controller == null) DiagLog.add("Link", "TIDAL no arrancó de fondo; hay que abrirla a mano")
        }
    }

    private fun attach(list: List<MediaController>?) {
        val tidal = list?.firstOrNull { it.packageName == TIDAL_PKG }
        if (tidal == null) {
            if (controller != null) DiagLog.add("Link", "La sesión de TIDAL desapareció")
            controller?.unregisterCallback(callback)
            controller = null
            _state.update { it.copy(tidalConnected = false) }
            return
        }
        if (controller?.sessionToken?.token == tidal.sessionToken) return
        controller?.unregisterCallback(callback)
        val c = MediaControllerCompat(app, MediaSessionCompat.Token.fromToken(tidal.sessionToken))
        controller = c
        c.registerCallback(callback)
        _state.update { it.copy(tidalConnected = true) }
        DiagLog.add("Link", "Conectado a TIDAL. Acciones: ${actionsLabel(c.playbackState?.actions ?: 0)} · cola=${c.queue?.size ?: "no expone"} · flags=${c.flags}")
        onState(c.playbackState)
        onMetadata(c.metadata)
    }

    private val callback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) { onState(state) }
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) { onMetadata(metadata) }
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) { if (!_state.value.driving) followQueue() }
        override fun onSessionDestroyed() { scope.launch { attach(null) } }
    }

    private var lastLoggedState = -1
    private var selfSeekAt = 0L
    /** Última posición vista MIENTRAS sonaba (no la pisa una lectura en medio de una recarga). */
    private var playingPos = 0f
    private var lastHeadsetSkip = 0L
    private var pauseAt = 0L
    private var pausePos = 0L
    /** Cuándo mandó Marea play/pausa (para no confundir nuestros propios comandos con los auriculares). */
    private var selfControlAt = 0L

    private fun onState(ps: PlaybackStateCompat?) {
        ps ?: return
        if (ps.state != lastLoggedState) {
            lastLoggedState = ps.state
            DiagLog.add("Link", "Estado TIDAL: ${stateName(ps.state)} en ${(ps.position / 1000)}s (+${SystemClock.elapsedRealtime() % 100000}ms)")
        }
        // (Probé detectar el botón "siguiente" de los auriculares por cómo reacciona TIDAL, pero no deja
        // rastro: lo que parecía "siguiente" era el "anterior" reiniciando el tema. Lo saqué para no
        // saltar de tema cuando apretás "anterior".)
        // Algunos reproductores avisan "saltando al siguiente/anterior" cuando apretás los botones
        // de los auriculares: si Marea maneja la cola, lo hacemos nosotros.
        if (_state.value.driving && pendingId == null) {
            if (ps.state == PlaybackStateCompat.STATE_SKIPPING_TO_NEXT) { DiagLog.add("Link", "Botón siguiente (auriculares/TIDAL)"); next(); return }
            if (ps.state == PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS) { DiagLog.add("Link", "Botón anterior (auriculares/TIDAL)"); previous(); return }
        }
        val playing = ps.state == PlaybackStateCompat.STATE_PLAYING
        val buffering = ps.state == PlaybackStateCompat.STATE_BUFFERING || ps.state == PlaybackStateCompat.STATE_CONNECTING
        _state.update { it.copy(isPlaying = playing, isBuffering = buffering) }
        _position.value = currentPosition()
        // TIDAL terminó el tema y se detuvo (sin autoplay): seguimos con la cola de Marea
        val st = _state.value
        val reported = ps.position / 1000f
        if (st.driving && pendingId == null && endHandledFor != lastMetaKey && !playing && !buffering && lastDur > 0 && lastPos >= lastDur - 3f && (reported >= lastDur - 3f || reported <= 1f) &&
            (ps.state == PlaybackStateCompat.STATE_STOPPED || ps.state == PlaybackStateCompat.STATE_PAUSED || ps.state == PlaybackStateCompat.STATE_NONE)
        ) {
            DiagLog.add("Link", "TIDAL terminó el tema (${lastPos.toInt()}/${lastDur.toInt()}s), mando el siguiente de Marea")
            lastPos = 0f
            endHandledFor = lastMetaKey
            advanceNatural()
        }
    }

    private fun onMetadata(md: MediaMetadataCompat?) {
        md ?: return
        val title = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: md.description?.title?.toString() ?: return
        val artist = md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: md.description?.subtitle?.toString().orEmpty()
        val mediaId = md.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) ?: md.description?.mediaId
        val dur = md.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000f
        val key = "$mediaId|$title|$artist"
        if (key == lastMetaKey) {
            if (dur > 0) _state.update { it.copy(durationSec = dur) }
            return
        }
        val prevPos = lastPos
        val prevDur = lastDur
        lastMetaKey = key
        lastPos = 0f
        posValidFrom = SystemClock.elapsedRealtime() + 1200
        playingPos = 0f
        // Si TIDAL no informa la duración, usamos la que conocemos del tema (antes quedaba en 0 y
        // el fin del tema se confundía con "cambiaste de tema en TIDAL").
        lastDur = if (dur > 0) dur else (_state.value.queue.firstOrNull { matches(it, mediaId, title) }?.durationSec?.toFloat() ?: 0f)
        DiagLog.add("Link", "TIDAL suena: \"$title\" — $artist (id=$mediaId, ${dur.toInt()}s)")
        // Detector de bucles: si un tema vuelve a sonar a los pocos temas, dejamos todo el contexto en Diagnóstico
        run {
            val tkey = "$title|$artist".lowercase()
            val ago = recentTitles.indexOf(tkey)
            if (ago in 0..7) {
                val s0 = _state.value
                DiagLog.add(
                    "Bucle",
                    "Se repite \"$title\" (sonó hace ${ago + 1} temas). cola de Marea=${s0.driving} tema=${s0.index + 1}/${s0.queue.size} " +
                        "en tu cola=${s0.manualCount} repetir=${s0.repeat} aleatorio=${s0.shuffle} fuente=${s0.sourceLabel} " +
                        "TIDAL repetir=${controller?.repeatMode} aleatorio=${controller?.shuffleMode} colaTIDAL=${controller?.queue?.size}",
                )
            }
            recentTitles.addFirst(tkey)
            while (recentTitles.size > 30) recentTitles.removeLast()
            mediaId?.let { Regex("""\d{5,}""").find(it)?.value }?.let { id ->
                recentIds.remove(id); recentIds.add(id)
                while (recentIds.size > 400) recentIds.remove(recentIds.first())
            }
        }

        val st = _state.value
        val pending = pendingId
        val pendTrack = pending?.let { p -> st.queue.firstOrNull { it.id == p } }
        var matchesPending = pendTrack != null && matches(pendTrack, mediaId, title, checkVersion = true)
        if (!matchesPending && pendTrack != null && lastTried == PlayMethod.SEARCH && looseMatch(pendTrack, title)) {
            if (searchVariant == 0) {
                searchVariant = 1
                DiagLog.add("Link", "TIDAL puso otra versión de \"${pendTrack.title}\" (id=$mediaId, quería ${pendTrack.id}); busco con el álbum")
                send(pendTrack, PlayMethod.SEARCH)
                return
            }
            DiagLog.add("Link", "TIDAL sigue poniendo otra versión de \"${pendTrack.title}\"; la dejo")
            matchesPending = true
        }
        when {
            matchesPending -> {
                pendingId = null
                verifyJob?.cancel()
                val idx = st.queue.indexOfFirst { it.id == pending }
                _state.update { it.copy(index = idx, durationSec = dur, error = null) }
                if (settings.playMethod == PlayMethod.AUTO && lastTried != null && workingMethod != lastTried) {
                    workingMethod = lastTried
                    app.getSharedPreferences("marea", Context.MODE_PRIVATE).edit().putString("workingMethod", lastTried!!.name).apply()
                }
                confirmedAt = SystemClock.elapsedRealtime()
                DiagLog.add("Link", "Confirmado: sonó lo que pedimos (método ${lastTried?.name})")
                maybeAutoSuggest()
                queueNextInTidal()
            }
            st.driving && st.current != null && matches(st.current!!, mediaId, title) -> {
                _state.update { it.copy(durationSec = dur) }
            }
            st.driving && st.queue.getOrNull(st.index + 1)?.let { matches(it, mediaId, title) } == true -> {
                _state.update { it.copy(index = it.index + 1, durationSec = dur, manualCount = (it.manualCount - 1).coerceAtLeast(0)) }
                maybeAutoSuggest()
                queueNextInTidal()
            }
            st.driving && pending == null && (prevDur <= 0f || prevPos >= prevDur - 8f || prevPos >= prevDur * 0.9f) -> {
                // TIDAL pasó solo a otro tema (su autoplay) justo al terminar el nuestro: lo pisamos con el de Marea.
                DiagLog.add("Link", "TIDAL eligió otro tema al terminar (${prevPos.toInt()}/${prevDur.toInt()}s); pongo el siguiente de Marea")
                advanceNatural()
            }
            pending != null -> Unit // esperando confirmación; lo resuelve verifyJob
            st.driving && st.current != null && SystemClock.elapsedRealtime() - confirmedAt < 8000 && reclaimCount < 3 -> {
                // Pusimos nuestro tema y enseguida TIDAL saltó a otro: es su autoplay del tema anterior
                // que llegó tarde. Volvemos a poner el nuestro en vez de rendirnos.
                reclaimCount++
                DiagLog.add("Link", "TIDAL metió \"$title\" justo después; vuelvo a poner \"${st.current!!.title}\" (intento $reclaimCount)")
                requestPlay(st.current!!)
            }
            else -> {
                // El usuario cambió de tema en TIDAL: dejamos de manejar la cola y seguimos a TIDAL.
                if (st.driving) DiagLog.add("Link", "Cambiaste de tema en TIDAL (${prevPos.toInt()}/${prevDur.toInt()}s): sigo la cola de TIDAL")
                followExternal(md, title, artist, mediaId, dur)
            }
        }
    }

    private fun followExternal(md: MediaMetadataCompat, title: String, artist: String, mediaId: String?, dur: Float) {
        restored?.let { saved ->
            restored = null
            val idx = saved.queue.indexOfFirst { matches(it, mediaId, title) }
            if (idx >= 0) {
                DiagLog.add("Link", "Retomo tu cola: \"$title\" es el tema ${idx + 1} de ${saved.queue.size}")
                originalQueue = saved.queue
                _state.update { saved.copy(index = idx, durationSec = dur, tidalConnected = it.tidalConnected, isPlaying = it.isPlaying, needsAccess = it.needsAccess, manualCount = if (idx == saved.index) saved.manualCount else 0) }
                return
            }
        }
        val id = mediaId?.let { Regex("""\d{5,}""").find(it)?.value } ?: "ext:$title|$artist"
        val cover = coverFrom(md, id)
        val base = Track(
            id = id, title = title, version = null, durationSec = dur.toInt(), explicit = false, tags = emptyList(),
            artists = listOf(ArtistRef("", artist)), albumId = null,
            albumTitle = md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM), cover = cover, coverLarge = cover,
        )
        _state.update { it.copy(queue = listOf(base), index = 0, driving = false, durationSec = dur, sourceLabel = "En TIDAL", manualCount = 0) }
        followQueue()
        enrich(base)
    }

    /** Si TIDAL publica su cola, la mostramos. */
    private fun followQueue() {
        val c = controller ?: return
        val items = c.queue ?: return
        if (items.isEmpty()) return
        val active = c.playbackState?.activeQueueItemId ?: -1
        val idx = items.indexOfFirst { it.queueId == active }
        if (idx < 0) return
        val cur = _state.value.current
        val tracks = items.mapIndexed { i, q ->
            if (i == idx && cur != null) cur else {
                val d = q.description
                val qid = d.mediaId?.let { Regex("""\d{5,}""").find(it)?.value } ?: "q:${q.queueId}"
                val ic = d.iconUri?.toString()
                Track(qid, d.title?.toString().orEmpty(), null, 0, false, emptyList(), listOf(ArtistRef("", d.subtitle?.toString().orEmpty())), null, null, ic, ic)
            }
        }
        _state.update { it.copy(queue = tracks, index = idx) }
    }

    /** Completa con datos de la API (portada, artistas, álbum) si el id es de TIDAL. */
    private fun enrich(t: Track) {
        if (!t.id.all { it.isDigit() }) return
        scope.launch {
            val rich = enrichCache.getOrPut(t.id) { runCatching { app.api.tracks(listOf(t.id)).firstOrNull() }.getOrNull() } ?: return@launch
            _state.update { st ->
                val q = st.queue.map { if (it.id == t.id) rich.copy(cover = rich.cover ?: it.cover, coverLarge = rich.coverLarge ?: it.coverLarge) else it }
                st.copy(queue = q)
            }
        }
    }

    private fun coverFrom(md: MediaMetadataCompat, id: String): String? {
        listOf(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, MediaMetadataCompat.METADATA_KEY_ART_URI, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
            .firstNotNullOfOrNull { md.getString(it)?.takeIf { s -> s.startsWith("http") } }?.let { return it }
        val bmp: Bitmap = md.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: md.description?.iconBitmap ?: return null
        return runCatching {
            val f = File(app.cacheDir, "np_${id.hashCode()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f.absolutePath
        }.getOrNull()
    }

    // ---------- Pedirle a TIDAL que reproduzca ----------

    private var lastTried: PlayMethod? = null
    /** 0 = "título (versión) artista"; 1 = además el álbum (cuando TIDAL puso otra versión del tema). */
    private var searchVariant = 0

    private fun requestPlay(t: Track, method: PlayMethod = settings.playMethod) {
        pendingId = t.id
        searchVariant = 0
        verifyJob?.cancel()
        val chain: List<PlayMethod> = when (method) {
            PlayMethod.AUTO -> listOfNotNull(workingMethod) + listOf(PlayMethod.URI, PlayMethod.SEARCH, PlayMethod.DEEPLINK).filter { it != workingMethod }
            else -> listOf(method)
        }
        verifyJob = scope.launch {
            for ((i, m) in chain.withIndex()) {
                if (!send(t, m)) continue
                lastTried = m
                // esperamos a que la metadata de TIDAL confirme el tema
                val timeout = if (m == PlayMethod.DEEPLINK) 9000L else 4500L
                val start = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime() - start < timeout) {
                    if (pendingId != t.id) return@launch // confirmado (o se pidió otro)
                    delay(150)
                }
                DiagLog.add("Link", "Método ${m.name}: TIDAL no puso \"${t.title}\" en ${timeout / 1000}s")
                if (m == PlayMethod.DEEPLINK && controller != null) {
                    // Abrió la pantalla del tema pero no lo reprodujo: intentamos darle play
                    controller?.transportControls?.play()
                }
                if (i == chain.lastIndex) {
                    setError("TIDAL no reprodujo el tema. Probá otro método en Ajustes → Laboratorio.")
                    pendingId = null
                }
            }
        }
    }

    /** Devuelve false si el método no se puede usar ahora (ej: no hay sesión). */
    private fun send(t: Track, m: PlayMethod): Boolean {
        val tc = controller?.transportControls
        return when (m) {
            PlayMethod.URI -> {
                if (tc == null) return false
                DiagLog.add("Link", "→ playFromUri ${trackUrl(t.id)}")
                tc.playFromUri(Uri.parse(trackUrl(t.id)), Bundle())
                true
            }
            PlayMethod.SEARCH -> {
                if (tc == null) return false
                // con la versión (ej. "Live at NPR Music's Tiny Desk"): sin ella TIDAL ponía la original
                val artist = t.artists.firstOrNull()?.name.orEmpty()
                val q = (if (searchVariant == 0) "${t.fullTitle} $artist" else "${t.fullTitle} $artist ${t.albumTitle.orEmpty()}").trim()
                DiagLog.add("Link", "→ playFromSearch \"$q\"")
                tc.playFromSearch(q, Bundle().apply {
                    putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                    putString(MediaStore.EXTRA_MEDIA_TITLE, t.fullTitle)
                    t.artists.firstOrNull()?.name?.let { putString(MediaStore.EXTRA_MEDIA_ARTIST, it) }
                    t.albumTitle?.let { putString(MediaStore.EXTRA_MEDIA_ALBUM, it) }
                })
                true
            }
            PlayMethod.DEEPLINK -> openInTidal(trackUrl(t.id))
            PlayMethod.AUTO -> false
        }
    }

    fun openInTidal(url: String): Boolean {
        DiagLog.add("Link", "→ abrir en TIDAL $url")
        return runCatching {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(TIDAL_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse {
            setError("No pude abrir TIDAL. ¿Está instalada?")
            false
        }
    }

    fun openTidalApp() {
        runCatching { app.packageManager.getLaunchIntentForPackage(TIDAL_PKG)?.let { app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private fun trackUrl(id: String) = "https://tidal.com/browse/track/$id"

    /** Para el laboratorio: prueba un método puntual con un tema. */
    fun labTest(t: Track, m: PlayMethod) {
        _state.update { it.copy(queue = listOf(t), index = 0, driving = true, sourceLabel = "Laboratorio", manualCount = 0) }
        requestPlay(t, m)
    }

    fun labInfo(): String {
        val c = controller ?: return if (hasAccess()) "No hay sesión de TIDAL activa. Abrí TIDAL y poné algo un segundo." else "Falta el acceso a notificaciones."
        val ps = c.playbackState
        return buildString {
            appendLine("Paquete: ${TIDAL_PKG}")
            appendLine("Estado: ${ps?.state} · posición ${ps?.position}")
            appendLine("Acciones: ${actionsLabel(ps?.actions ?: 0)}")
            appendLine("Cola expuesta: ${c.queue?.size ?: "no"} · comandos de cola: ${(c.flags and MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS.toLong()) != 0L}")
            append("Metadata: ${c.metadata?.description?.title} / id=${c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)}")
        }
    }

    // ---------- API pública (la misma que usaba Marea) ----------

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, source: String? = null) {
        if (tracks.isEmpty()) return
        reclaimCount = 0
        if (source != _state.value.sourceLabel) bulkJob?.cancel()
        originalQueue = tracks
        val (queue, idx) = if (shuffle) {
            val first = tracks[startIndex.coerceIn(tracks.indices)]
            (listOf(first) + tracks.filterIndexed { i, _ -> i != startIndex }.shuffled()) to 0
        } else tracks to startIndex.coerceIn(tracks.indices)
        _state.update { it.copy(queue = queue, index = idx, shuffle = shuffle, sourceLabel = source, error = null, manualCount = 0, driving = settings.mareaQueue) }
        if (!checkReady()) return
        HeadsetService.start(app)
        requestPlay(queue[idx])
    }

    /**
     * Reproducir un álbum o playlist entero: si TIDAL acepta links, se lo pasamos directo
     * (arranca al instante y TIDAL maneja la cola completa). Si no, usamos la cola de Marea.
     */
    fun playContainer(kind: String, id: String, shuffle: Boolean, source: String?, fallback: List<Track>) {
        val c = controller
        val uriOk = c != null && (workingMethod == PlayMethod.URI || settings.playMethod == PlayMethod.URI)
        if (!uriOk || !settings.containerToTidal) {
            if (shuffle) playShuffled(fallback, source) else playTracks(fallback, 0, source = source)
            return
        }
        val url = "https://tidal.com/browse/$kind/$id"
        DiagLog.add("Link", "→ playFromUri $url (shuffle=$shuffle)")
        val before = lastMetaKey
        c!!.transportControls.setShuffleMode(if (shuffle) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
        c.transportControls.playFromUri(Uri.parse(url), Bundle())
        pendingId = null
        _state.update { it.copy(driving = false, sourceLabel = source, shuffle = shuffle, manualCount = 0, queue = fallback.take(1).ifEmpty { it.queue }, index = 0) }
        verifyJob?.cancel()
        verifyJob = scope.launch {
            delay(4500)
            if (lastMetaKey == before) {
                DiagLog.add("Link", "TIDAL no arrancó la $kind por link; uso la cola de Marea")
                if (shuffle) playShuffled(fallback, source) else playTracks(fallback, 0, source = source)
            }
        }
    }

    /** La cola ya viene mezclada desde afuera: solo marcamos el modo aleatorio. */
    fun markShuffled() { _state.update { it.copy(shuffle = true) } }

    fun playShuffled(tracks: List<Track>, source: String? = null) {
        if (tracks.isEmpty()) return
        playTracks(tracks, tracks.indices.random(), shuffle = true, source = source)
    }

    fun playProgressive(initial: List<Track>, shuffle: Boolean, source: String?, startIndex: Int = 0, pages: (suspend () -> List<Track>?)?, alreadyShuffled: Boolean = false) {
        bulkJob?.cancel()
        scope.launch {
            var first = initial
            if (first.isEmpty() && pages != null) first = pages() ?: emptyList()
            if (first.isEmpty()) { setError("La lista está vacía"); return@launch }
            if (shuffle) playShuffled(first, source) else playTracks(first, startIndex, source = source)
            if (alreadyShuffled) markShuffled()
            if (pages == null) return@launch
            bulkJob = scope.launch {
                var guard = 0
                while (guard++ < 2000) {
                    val more = runCatching { pages() }.getOrNull() ?: break
                    if (more.isEmpty()) continue
                    if (_state.value.sourceLabel != source) break
                    originalQueue = originalQueue + more
                    _state.update {
                        val head = it.queue.take(it.index + 1 + it.manualCount)
                        val rest = it.queue.drop(it.index + 1 + it.manualCount) + more
                        it.copy(queue = head + if (it.shuffle) rest.shuffled() else rest)
                    }
                }
            }
        }
    }

    fun togglePlay() {
        val c = controller
        val st = _state.value
        if (c == null) {
            st.current?.let { if (checkReady()) requestPlay(it) } ?: openTidalApp()
            return
        }
        selfControlAt = SystemClock.elapsedRealtime()
        if (st.isPlaying) c.transportControls.pause() else c.transportControls.play()
    }

    fun pause() { controller?.transportControls?.pause() }

    fun togglePlayIfPaused() { if (!_state.value.isPlaying) togglePlay() }

    fun next() {
        val st = _state.value
        if (!st.driving) { controller?.transportControls?.skipToNext(); return }
        when {
            st.index + 1 < st.queue.size -> jumpTo(st.index + 1)
            st.repeat == RepeatMode.ALL && st.queue.isNotEmpty() -> jumpTo(0)
            else -> addSuggestions()
        }
    }

    fun previous() {
        val st = _state.value
        if (!st.driving) { controller?.transportControls?.skipToPrevious(); return }
        if (_position.value > 3f || st.index <= 0) seekTo(0f) else jumpTo(st.index - 1)
    }

    fun jumpTo(index: Int) {
        reclaimCount = 0
        val st = _state.value
        if (index !in st.queue.indices) return
        if (!st.driving) {
            // cola de TIDAL: si expone ids de cola, saltamos ahí
            val items = controller?.queue
            if (items != null && index in items.indices) { controller?.transportControls?.skipToQueueItem(items[index].queueId); return }
        }
        _state.update {
            val skipped = index - it.index
            it.copy(index = index, driving = true, manualCount = if (skipped > 0) (it.manualCount - skipped).coerceAtLeast(0) else 0)
        }
        requestPlay(_state.value.queue[index])
    }

    fun seekTo(sec: Float) {
        selfSeekAt = SystemClock.elapsedRealtime()
        controller?.transportControls?.seekTo((sec * 1000).toLong())
        _position.value = sec
        lastPos = sec
    }

    fun playNext(track: Track) {
        takeOverQueue()
        _state.update { st ->
            if (st.current == null) return@update st.copy(queue = listOf(track), index = 0)
            st.copy(queue = st.queue.toMutableList().apply { add(st.index + 1, track) }, manualCount = st.manualCount + 1)
        }
        if (controller == null && _state.value.queue.size == 1) requestPlay(track)
    }

    fun addToQueue(tracks: List<Track>): Int {
        takeOverQueue()
        var pos = 1
        _state.update { st ->
            if (st.current == null) return@update st.copy(queue = tracks, index = 0)
            val at = st.index + 1 + st.manualCount
            pos = st.manualCount + 1
            st.copy(queue = st.queue.toMutableList().apply { addAll(at.coerceAtMost(size), tracks) }, manualCount = st.manualCount + tracks.size)
        }
        if (_state.value.queue.size == tracks.size) requestPlay(tracks.first())
        return pos
    }

    /** Si veníamos siguiendo la cola de TIDAL, a partir de ahora la maneja Marea. */
    private fun takeOverQueue() {
        val st = _state.value
        if (st.driving) return
        val cur = st.current
        _state.update { it.copy(queue = listOfNotNull(cur), index = if (cur != null) 0 else -1, driving = true, sourceLabel = "Tu cola", manualCount = 0) }
    }

    private fun appendToEnd(tracks: List<Track>) {
        takeOverQueue()
        _state.update { st -> if (st.current == null) st.copy(queue = tracks, index = 0) else st.copy(queue = st.queue + tracks) }
    }

    fun removeFromQueue(index: Int) {
        val st = _state.value
        if (index == st.index || index !in st.queue.indices) return
        takeOverQueue()
        _state.update {
            val q = it.queue.toMutableList().apply { removeAt(index) }
            val inManual = index > it.index && index <= it.index + it.manualCount
            it.copy(queue = q, index = if (index < it.index) it.index - 1 else it.index, manualCount = if (inManual) it.manualCount - 1 else it.manualCount)
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        val st = _state.value
        if (from !in st.queue.indices || to !in st.queue.indices || from == to) return
        val cur = st.current
        val q = st.queue.toMutableList().apply { add(to, removeAt(from)) }
        _state.update { it.copy(queue = q, index = q.indexOf(cur)) }
    }

    /**
     * Mueve un tema dentro de "A continuación" (índices absolutos de la cola, siempre después del actual).
     * Mantiene bien el bloque "En tu cola": si lo soltás dentro de ese bloque pasa a ser parte de él.
     */
    fun moveUpNext(from: Int, to: Int, forceManual: Boolean = false) {
        val st = _state.value
        if (from == to || from <= st.index || to <= st.index || from !in st.queue.indices || to !in st.queue.indices) return
        takeOverQueue()
        _state.update {
            val q = it.queue.toMutableList().apply { add(to, removeAt(from)) }
            val fromManual = from <= it.index + it.manualCount
            val toManual = forceManual || to <= it.index + it.manualCount
            val manual = it.manualCount + (if (toManual) 1 else 0) - (if (fromManual) 1 else 0)
            it.copy(queue = q, manualCount = manual.coerceIn(0, q.size - it.index - 1))
        }
    }

    /** "Reproducir a continuación" sobre un tema que ya está en la cola: lo sube al primer lugar sin duplicarlo. */
    fun moveToNext(from: Int) = moveUpNext(from, _state.value.index + 1, forceManual = true)

    fun clearUpNext() { _state.update { it.copy(queue = it.queue.take(it.index + 1), manualCount = 0) } }

    fun toggleShuffle() {
        val st = _state.value
        if (!st.driving) {
            val c = controller ?: return
            val on = c.shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_ALL
            c.transportControls.setShuffleMode(if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
            _state.update { it.copy(shuffle = on) }
            return
        }
        val cur = st.current ?: return
        if (!st.shuffle) {
            originalQueue = st.queue
            val manual = st.queue.drop(st.index + 1).take(st.manualCount)
            val rest = (st.queue.take(st.index) + st.queue.drop(st.index + 1 + st.manualCount)).shuffled()
            _state.update { it.copy(queue = listOf(cur) + manual + rest, index = 0, shuffle = true) }
        } else {
            val manual = st.queue.drop(st.index + 1).take(st.manualCount)
            val base = originalQueue.ifEmpty { st.queue }
            val idx = base.indexOfFirst { it.id == cur.id }.takeIf { it >= 0 } ?: 0
            val restBase = base.drop(idx + 1).filter { b -> manual.none { it.id == b.id } }
            _state.update { it.copy(queue = base.take(idx + 1) + manual + restBase, index = idx, shuffle = false) }
        }
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }
        _state.update { it.copy(repeat = next) }
        controller?.transportControls?.setRepeatMode(
            when (next) {
                RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                RepeatMode.ALL -> if (_state.value.driving) PlaybackStateCompat.REPEAT_MODE_NONE else PlaybackStateCompat.REPEAT_MODE_ALL
                RepeatMode.OFF -> PlaybackStateCompat.REPEAT_MODE_NONE
            },
        )
    }

    fun addSuggestions(seed: Track? = null, replaceUpNext: Boolean = false) {
        val base = seed ?: _state.value.current ?: return
        if (_state.value.loadingSuggestions || !base.id.all { it.isDigit() }) return
        _state.update { it.copy(loadingSuggestions = true) }
        scope.launch {
            val result = runCatching { app.api.suggestions(base.id) }
            _state.update { it.copy(loadingSuggestions = false) }
            result.onSuccess { list ->
                // ni lo que está en la cola ni lo que ya sonó hace poco (evita quedar girando entre 4 o 5 temas parecidos)
                val existing = _state.value.queue.map { it.id }.toSet() + recentIds
                val fresh = list.filter { it.id !in existing }
                DiagLog.add("Link", "Sugerencias de \"${base.title}\": ${list.size}, nuevas ${fresh.size}")
                var add = fresh
                if (add.isEmpty()) {
                    // todo lo sugerido ya sonó: probamos con la radio del artista para salir del círculo
                    val artistId = base.artists.firstOrNull { it.id.isNotBlank() }?.id
                    if (artistId != null) add = runCatching { app.api.artistRadio(artistId) }.getOrDefault(emptyList()).filter { it.id !in existing }
                    DiagLog.add("Link", "Sugerencias repetidas; radio del artista: ${add.size} nuevas")
                }
                if (add.isEmpty()) { setError("No encontré sugerencias nuevas para este tema"); return@onSuccess }
                if (replaceUpNext) clearUpNext()
                appendToEnd(add)
            }.onFailure { setError("No pude traer sugerencias: ${it.message}") }
        }
    }

    /** Sin cola de Marea (estaba sonando algo puesto desde TIDAL): seguimos con temas parecidos. */
    private fun nextFromSuggestions() {
        val cur = _state.value.current ?: return
        if (!cur.id.all { it.isDigit() }) return
        DiagLog.add("Link", "No hay cola de Marea: sigo con temas parecidos a \"${cur.title}\"")
        scope.launch {
            val list = runCatching { app.api.suggestions(cur.id) }.getOrDefault(emptyList())
            if (list.isEmpty()) { setError("No encontré qué poner después"); return@launch }
            playTracks(list, 0, source = "Parecidos a ${cur.title}")
        }
    }

    fun startRadio(track: Track) {
        scope.launch {
            _state.update { it.copy(loadingSuggestions = true) }
            val list = runCatching { app.api.suggestions(track.id) }.getOrDefault(emptyList())
            _state.update { it.copy(loadingSuggestions = false) }
            playTracks(listOf(track) + list, 0, source = "Radio: ${track.title}")
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun release() {
        controller?.unregisterCallback(callback)
        controller = null
        _state.value = PlayerUiState(needsAccess = !hasAccess())
    }

    // ---------- Interno ----------

    private fun checkReady(): Boolean {
        if (!hasAccess()) {
            _state.update { it.copy(needsAccess = true) }
            setError("Primero dale a Marea Link acceso a notificaciones (te lo pide arriba)")
            return false
        }
        return true
    }

    /** El tema actual terminó en forma natural: siguiente de la cola de Marea. */
    private fun advanceNatural() {
        reclaimCount = 0
        val st = _state.value
        val nextIdx = when {
            st.repeat == RepeatMode.ONE -> st.index
            st.index + 1 < st.queue.size -> st.index + 1
            st.repeat == RepeatMode.ALL && st.queue.isNotEmpty() -> 0
            else -> -1
        }
        if (nextIdx < 0) { addSuggestions(); return }
        _state.update { it.copy(index = nextIdx, manualCount = if (nextIdx > it.index) (it.manualCount - 1).coerceAtLeast(0) else it.manualCount) }
        requestPlay(_state.value.queue[nextIdx])
    }

    /**
     * Si TIDAL acepta comandos de cola, le dejamos anotado el siguiente tema de Marea.
     * Así pasa al siguiente sin corte y sin que su autoplay meta sugerencias en el medio.
     */
    private fun queueNextInTidal() {
        val c = controller ?: return
        val st = _state.value
        if (!st.driving || st.repeat == RepeatMode.ONE) return
        if ((c.flags and MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS.toLong()) == 0L) return
        val next = st.queue.getOrNull(st.index + 1) ?: return
        if (!next.id.all { it.isDigit() }) return
        runCatching {
            val desc = android.support.v4.media.MediaDescriptionCompat.Builder()
                .setMediaId(next.id).setMediaUri(Uri.parse(trackUrl(next.id))).setTitle(next.title).setSubtitle(next.artistLine).build()
            val pos = (c.queue?.indexOfFirst { it.queueId == c.playbackState?.activeQueueItemId } ?: -1) + 1
            if (pos > 0) c.addQueueItem(desc, pos) else c.addQueueItem(desc)
            DiagLog.add("Link", "Anoté en la cola de TIDAL el siguiente: ${next.title}")
        }.onFailure { DiagLog.add("Link", "TIDAL no aceptó anotar en su cola: ${it.message}") }
    }

    private fun maybeAutoSuggest() {
        val st = _state.value
        if (bulkJob?.isActive != true && settings.autoSuggestions && st.driving && st.repeat == RepeatMode.OFF && st.queue.size - st.index <= 1) addSuggestions()
    }

    private fun currentPosition(): Float {
        val ps = controller?.playbackState ?: return _position.value
        var pos = ps.position.toFloat()
        if (ps.state == PlaybackStateCompat.STATE_PLAYING && ps.lastPositionUpdateTime > 0) {
            pos += (SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed
        }
        return (pos / 1000f).coerceAtLeast(0f)
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                if (controller != null) {
                    val p = currentPosition()
                    _position.value = p
                    val now = SystemClock.elapsedRealtime()
                    if (pendingId == null && now >= posValidFrom) {
                        lastPos = p
                        if (_state.value.isPlaying && !_state.value.isBuffering && p > 1f) playingPos = p
                        if (lastDur > 0 && p < lastDur - 5f) endHandledFor = null // (repetir uno: el mismo tema puede volver a terminar)
                    }
                    // Respaldo: TIDAL quedó en pausa al final del tema y no avisó nada útil → seguimos nosotros
                    val st0 = _state.value
                    if (st0.driving && pendingId == null && !st0.isPlaying && !st0.isBuffering && lastDur > 0 && lastPos >= lastDur - 3f && endHandledFor != lastMetaKey) {
                        if (pausedNearEndSince == 0L) pausedNearEndSince = now
                        else if (now - pausedNearEndSince > 1200) {
                            pausedNearEndSince = 0L
                            endHandledFor = lastMetaKey
                            DiagLog.add("Link", "TIDAL quedó en pausa al final (${lastPos.toInt()}/${lastDur.toInt()}s); mando el siguiente")
                            lastPos = 0f
                            advanceNatural()
                        }
                    } else pausedNearEndSince = 0L
                    val st = _state.value
                    // Si TIDAL repite el mismo tema al terminar (sin cambiar metadata) también avanzamos
                    if (st.driving && pendingId == null && lastDur > 0 && st.isPlaying && p >= lastDur - 0.3f) lastPos = p
                }
                delay(if (_state.value.isPlaying) 250 else 1000)
            }
        }
    }

    /** Se pone en true la primera vez que el id de TIDAL coincide con el nuestro: desde ahí confiamos en los ids. */
    private var idsReliable = false

    private fun matches(t: Track, mediaId: String?, title: String, checkVersion: Boolean = false): Boolean {
        if (mediaId != null && t.id.all { it.isDigit() } && mediaId.contains(t.id)) { idsReliable = true; return true }
        // si los ids de TIDAL son confiables y es otro, es otro tema (u otra versión: en vivo, remix…)
        if (idsReliable && mediaId != null && t.id.all { it.isDigit() } && Regex("""\d{5,}""").containsMatchIn(mediaId)) return false
        // la versión que pedimos (ej. "Live at NPR Music's Tiny Desk") tiene que aparecer en el título que suena
        if (checkVersion) {
            val v = t.version?.let { norm(it) }.orEmpty()
            if (v.isNotEmpty() && !norm(title).contains(v)) return false
        }
        return looseMatch(t, title)
    }

    /** Mismo título (ignora la versión): para reconocer "puso otra versión del tema que pedimos". */
    private fun looseMatch(t: Track, title: String): Boolean {
        val a = norm(t.title)
        val b = norm(title)
        return a.isNotEmpty() && (a == b || b.startsWith(a) || a.startsWith(b))
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}"""), "").replace(Regex("""[^a-z0-9]"""), "")

    private fun setError(msg: String) = _state.update { it.copy(error = msg) }

    private fun stateName(st: Int) = when (st) {
        PlaybackStateCompat.STATE_NONE -> "NINGUNO"
        PlaybackStateCompat.STATE_STOPPED -> "DETENIDO"
        PlaybackStateCompat.STATE_PAUSED -> "PAUSA"
        PlaybackStateCompat.STATE_PLAYING -> "SONANDO"
        PlaybackStateCompat.STATE_BUFFERING -> "CARGANDO"
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> "SALTANDO_SIGUIENTE"
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> "SALTANDO_ANTERIOR"
        PlaybackStateCompat.STATE_ERROR -> "ERROR"
        else -> "estado $st"
    }

    private fun actionsLabel(a: Long): String = listOf(
        PlaybackStateCompat.ACTION_PLAY_FROM_URI to "playFromUri",
        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH to "playFromSearch",
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID to "playFromMediaId",
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM to "saltarEnCola",
        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE to "shuffle",
        PlaybackStateCompat.ACTION_SET_REPEAT_MODE to "repeat",
        PlaybackStateCompat.ACTION_SEEK_TO to "seek",
    ).filter { (a and it.first) != 0L }.joinToString(", ") { it.second }.ifEmpty { "ninguna declarada" }

    companion object {
        const val TIDAL_PKG = "com.aspiro.tidal"
    }
}
