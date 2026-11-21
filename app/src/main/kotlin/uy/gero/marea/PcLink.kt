package uy.gero.marea

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Lo que está sonando en la PC (TIDAL de escritorio), según Marea PC. */
data class PcState(
    val app: String,
    val hasSession: Boolean,
    val title: String,
    val artist: String,
    val album: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffle: Boolean?,
    val repeat: String,
    val canNext: Boolean,
    val canPrevious: Boolean,
    val canSeek: Boolean,
    val coverKey: String,
    val receivedAt: Long,
) {
    val isTidal: Boolean get() = app.contains("tidal", ignoreCase = true)

    /** Para reusar la letra y el resto de la interfaz, armamos un "Track" con lo que manda la PC. */
    fun asTrack(): Track = Track(
        id = "pc:" + coverKey.hashCode(),
        title = title,
        version = null,
        durationSec = (durationMs / 1000).toInt(),
        explicit = false,
        tags = emptyList(),
        artists = artist.split(", ", " & ").filter { it.isNotBlank() }.map { ArtistRef("", it) }.ifEmpty { listOf(ArtistRef("", artist)) },
        albumId = null,
        albumTitle = album.ifBlank { null },
        cover = null,
        coverLarge = null,
    )
}

data class FoundPc(val name: String, val host: String)

/**
 * Conexión con Marea PC por la red local: descubre la PC, se empareja con un código
 * y después manda play/pausa/siguiente y recibe qué está sonando.
 */
object PcLink {
    enum class Status { OFF, CONNECTING, CONNECTED, NEEDS_CODE, ERROR }

    const val PORT = 47820
    const val DISCOVERY_PORT = 47821

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status
    private val _state = MutableStateFlow<PcState?>(null)
    val state: StateFlow<PcState?> = _state
    private val _cover = MutableStateFlow<Bitmap?>(null)
    val cover: StateFlow<Bitmap?> = _cover
    /** Posición en segundos, estimada entre mensajes (igual que la del reproductor del teléfono). */
    private val _position = MutableStateFlow(0f)
    val position: StateFlow<Float> = _position
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _pcName = MutableStateFlow<String?>(null)
    val pcName: StateFlow<String?> = _pcName

    private var ws: WebSocket? = null
    private var ticker: Job? = null
    private var retryJob: Job? = null
    private var wantConnected = false
    private var failures = 0

    private val prefs by lazy { MareaApp.instance.getSharedPreferences("marea", Context.MODE_PRIVATE) }
    val savedHost: String? get() = prefs.getString("pcHost", null)
    private val savedToken: String? get() = prefs.getString("pcToken", null)
    val isPaired: Boolean get() = savedHost != null && savedToken != null

    init {
        _pcName.value = prefs.getString("pcName", null)
    }

    // ---------- Descubrimiento ----------

    /** Manda un broadcast por la red y junta las PCs que contestan. */
    suspend fun discover(timeoutMs: Int = 2500): List<FoundPc> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, FoundPc>()
        runCatching {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 400
                val msg = "MAREA_DISCOVER".toByteArray()
                val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
                val end = System.currentTimeMillis() + timeoutMs
                var lastSend = 0L
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    if (System.currentTimeMillis() - lastSend > 700) {
                        targets.forEach { a -> runCatching { sock.send(DatagramPacket(msg, msg.size, a, DISCOVERY_PORT)) } }
                        lastSend = System.currentTimeMillis()
                    }
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        sock.receive(p)
                        val o = JSONObject(String(p.data, 0, p.length))
                        if (o.optString("app") == "MareaPC") {
                            val host = p.address.hostAddress ?: continue
                            found[host] = FoundPc(o.optString("name", host), host)
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                    }
                }
            }
        }.onFailure { DiagLog.add("PC", "Búsqueda falló: ${it.message}") }
        DiagLog.add("PC", "Búsqueda: ${found.size} PC(s) ${found.values.joinToString { "${it.name} (${it.host})" }}")
        found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())

    // ---------- Conexión ----------

    /** Conecta a la PC guardada (si hay). Se llama al abrir la app. */
    fun resume() {
        if (isPaired && _status.value != Status.CONNECTED && _status.value != Status.CONNECTING) {
            wantConnected = true
            open(savedHost!!, savedToken, null)
        }
    }

    /** Primera conexión con una PC: si no hay token, pide el código que muestra Marea PC. */
    fun pair(host: String, name: String?, code: String?) {
        wantConnected = true
        failures = 0
        if (name != null) { _pcName.value = name; prefs.edit().putString("pcName", name).apply() }
        val token = if (host == savedHost) savedToken else null
        open(host, token, code)
    }

    fun disconnect(forget: Boolean) {
        wantConnected = false
        retryJob?.cancel()
        ws?.close(1000, "bye")
        ws = null
        _status.value = Status.OFF
        _state.value = null
        _cover.value = null
        if (forget) {
            prefs.edit().remove("pcHost").remove("pcToken").remove("pcName").apply()
            _pcName.value = null
        }
    }

    private fun open(host: String, token: String?, code: String?) {
        retryJob?.cancel()
        ws?.cancel()
        _status.value = Status.CONNECTING
        _error.value = null
        val device = android.net.Uri.encode(android.os.Build.MODEL ?: "Teléfono")
        val q = buildString {
            append("device=").append(device)
            if (token != null) append("&token=").append(token)
            if (code != null) append("&code=").append(android.net.Uri.encode(code))
        }
        val req = Request.Builder().url("http://$host:$PORT/ws?$q").build()
        DiagLog.add("PC", "Conectando a $host…")
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                failures = 0
                _status.value = Status.CONNECTED
                prefs.edit().putString("pcHost", host).apply()
                DiagLog.add("PC", "Conectado a $host")
                startTicker()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket != ws) return
                val code401 = response?.code == 401
                val body = runCatching { response?.body?.string() }.getOrNull()
                when {
                    code401 && body?.contains("wrong") == true -> {
                        _status.value = Status.NEEDS_CODE
                        _error.value = "Código incorrecto"
                        prefs.edit().remove("pcToken").apply()
                    }
                    code401 -> {
                        _status.value = Status.NEEDS_CODE
                        prefs.edit().remove("pcToken").apply()
                    }
                    else -> {
                        _status.value = Status.ERROR
                        _error.value = t.message ?: "No pude conectar"
                        scheduleRetry(host)
                    }
                }
                _state.value = null
                DiagLog.add("PC", "Falló la conexión: ${response?.code ?: ""} ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket != ws) return
                _status.value = Status.OFF
                _state.value = null
                if (wantConnected) scheduleRetry(host)
            }
        })
    }

    private fun scheduleRetry(host: String) {
        if (!wantConnected) return
        failures++
        if (failures > 6) { DiagLog.add("PC", "Dejé de reintentar"); return }
        retryJob = scope.launch {
            delay(minOf(30_000L, 2_000L * failures))
            if (wantConnected) open(host, savedToken, null)
        }
    }

    private fun handle(text: String) {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (o.optString("type")) {
            "hello" -> {
                val token = o.optString("token").takeIf { it.isNotBlank() && it != "null" }
                if (token != null) prefs.edit().putString("pcToken", token).apply()
                val name = o.optString("name").ifBlank { null }
                if (name != null) { _pcName.value = name; prefs.edit().putString("pcName", name).apply() }
            }
            "state" -> {
                val s = PcState(
                    app = o.optString("app"),
                    hasSession = o.optBoolean("hasSession"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    playing = o.optBoolean("playing"),
                    positionMs = o.optLong("positionMs"),
                    durationMs = o.optLong("durationMs"),
                    shuffle = if (o.isNull("shuffle")) null else o.optBoolean("shuffle"),
                    repeat = o.optString("repeat", "off"),
                    canNext = o.optBoolean("canNext", true),
                    canPrevious = o.optBoolean("canPrevious", true),
                    canSeek = o.optBoolean("canSeek", true),
                    coverKey = o.optString("coverKey"),
                    receivedAt = System.currentTimeMillis(),
                )
                _state.value = s
                _position.value = s.positionMs / 1000f
            }
            "cover" -> {
                val data = o.optString("data").takeIf { it.isNotBlank() && it != "null" }
                _cover.value = data?.let { runCatching { Base64.decode(it, Base64.DEFAULT).let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) } }.getOrNull() }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val s = _state.value
                if (s != null && s.playing) {
                    var p = (s.positionMs + (System.currentTimeMillis() - s.receivedAt)) / 1000f
                    if (s.durationMs > 0) p = p.coerceAtMost(s.durationMs / 1000f)
                    _position.value = p
                }
                delay(250)
            }
        }
    }

    // ---------- Comandos ----------

    fun send(cmd: String, ms: Long? = null, arg: String? = null) {
        val o = JSONObject().put("cmd", cmd)
        if (ms != null) o.put("ms", ms)
        if (arg != null) o.put("arg", arg)
        val ok = ws?.send(o.toString()) ?: false
        if (!ok) DiagLog.add("PC", "No pude mandar \"$cmd\" (sin conexión)")
        // respuesta instantánea en la interfaz, sin esperar a la PC
        _state.value?.let { s ->
            when (cmd) {
                "toggle" -> _state.value = s.copy(playing = !s.playing, positionMs = (_position.value * 1000).toLong(), receivedAt = System.currentTimeMillis())
                "seek" -> if (ms != null) { _state.value = s.copy(positionMs = ms, receivedAt = System.currentTimeMillis()); _position.value = ms / 1000f }
            }
        }
    }

    fun toggle() = send("toggle")
    fun next() = send("next")
    fun previous() = send("previous")
    fun seekTo(sec: Float) = send("seek", ms = (sec * 1000).toLong())
    fun shuffle() = send("shuffle")
    fun repeat() = send("repeat")
    /** Experimental: pedirle a TIDAL de escritorio que abra un tema por su ID. */
    fun openTrack(id: String) = send("open", arg = id)
}
