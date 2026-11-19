package uy.gero.marea

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Puente para los botones de los auriculares (y reloj, auto, pantalla de bloqueo).
 *
 * Android le manda esos botones a la app que está reproduciendo audio más recientemente.
 * Como el audio lo pone TIDAL, los botones le llegaban a TIDAL, que con un solo tema en su
 * cola ignoraba "siguiente". Este servicio reproduce SILENCIO (ceros, sin pedir el foco de
 * audio, así TIDAL sigue sonando) y tiene su propia sesión de medios: así Android le manda
 * los botones a Marea, y Marea los traduce a su cola.
 */
class HeadsetService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat
    private var track: AudioTrack? = null
    @Volatile private var writing = false
    private var writer: Thread? = null
    private val pc get() = MareaApp.instance.player
    private var idleSince = 0L

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Controles de Marea", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        session = MediaSessionCompat(this, "MareaLink").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { log("play"); pc.togglePlayIfPaused() }
                override fun onPause() { log("pausa"); pc.pause() }
                override fun onSkipToNext() { log("siguiente"); pc.next() }
                override fun onSkipToPrevious() { log("anterior"); pc.previous() }
                override fun onSeekTo(pos: Long) { pc.seekTo(pos / 1000f) }
                override fun onStop() { pc.pause() }
            })
            setSessionActivity(PendingIntent.getActivity(this@HeadsetService, 0, Intent(this@HeadsetService, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            isActive = true
        }
        startSilence()
        // Reflejamos en nuestra sesión lo que hace TIDAL
        scope.launch {
            pc.state.map { Triple(it.current?.id, it.isPlaying, it.durationSec) }.distinctUntilChanged().collect { (_, playing, _) ->
                update()
                if (playing) {
                    idleSince = 0L
                    // TIDAL acaba de arrancar (o cambiar de) tema y pasó a ser "la última app que sonó":
                    // reiniciamos el silencio un instante después para volver a quedar primeros.
                    delay(700)
                    bumpSilence()
                } else if (idleSince == 0L) idleSince = SystemClock.elapsedRealtime()
            }
        }
        // Si pasa mucho tiempo en pausa, nos vamos (ahorro de batería)
        scope.launch {
            while (true) {
                delay(60_000)
                if (idleSince != 0L && SystemClock.elapsedRealtime() - idleSince > 15 * 60_000) { stopSelf(); break }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> pc.togglePlay()
            ACTION_NEXT -> pc.next()
            ACTION_PREV -> pc.previous()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        update()
        return START_NOT_STICKY
    }

    private fun log(what: String) = DiagLog.add("Botones", "Llegó \"$what\" (auriculares/reloj/bloqueo)")

    // ---------- Silencio ----------

    private fun startSilence() {
        if (track != null) return
        val rate = 16000
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(min * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        track = t
        t.play()
        writing = true
        writer = Thread({
            val zeros = ByteArray(min)
            while (writing) {
                val tr = track ?: break
                if (tr.write(zeros, 0, zeros.size) < 0) SystemClock.sleep(50)
            }
        }, "marea-silencio").apply { isDaemon = true; start() }
        DiagLog.add("Botones", "Puente de auriculares activo")
    }

    /** Pausar y volver a arrancar el silencio para que Android nos vuelva a tomar como "lo último que sonó". */
    private fun bumpSilence() {
        val t = track ?: return
        runCatching { t.pause(); t.flush(); t.play() }
    }

    private fun stopSilence() {
        writing = false
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    // ---------- Sesión y notificación ----------

    private fun update() {
        val st = pc.state.value
        val t = st.current
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t?.fullTitle ?: "Marea Link")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, t?.artistLine ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, t?.albumTitle ?: "")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (st.durationSec * 1000).toLong())
                .apply { WidgetCover.flow.value.second?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO,
                )
                // Siempre "sonando": así Android nos sigue mandando los botones aunque TIDAL esté en pausa
                .setState(if (st.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, (pc.position.value * 1000).toLong(), if (st.isPlaying) 1f else 0f)
                .build(),
        )
        val n = notification(st)
        ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun notification(st: PlayerUiState): Notification {
        val t = st.current
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_w_play)
            .setContentTitle(t?.fullTitle ?: "Marea Link")
            .setContentText(t?.artistLine ?: "Controles de los auriculares activos")
            .setLargeIcon(WidgetCover.flow.value.second)
            .setOnlyAlertOnce(true).setShowWhen(false).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_w_prev, "Anterior", action(ACTION_PREV, 1))
            .addAction(if (st.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (st.isPlaying) "Pausa" else "Reproducir", action(ACTION_TOGGLE, 2))
            .addAction(R.drawable.ic_w_next, "Siguiente", action(ACTION_NEXT, 3))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun action(a: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, HeadsetService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        stopSilence()
        session.release()
        scope.cancel()
        DiagLog.add("Botones", "Puente de auriculares apagado")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "marea_controles"
        private const val NOTIF_ID = 77
        private const val ACTION_TOGGLE = "uy.gero.marealink.TOGGLE"
        private const val ACTION_NEXT = "uy.gero.marealink.NEXT"
        private const val ACTION_PREV = "uy.gero.marealink.PREV"
        private const val ACTION_STOP = "uy.gero.marealink.STOP"

        fun start(ctx: Context) {
            if (!MareaApp.instance.settings.value.headsetBridge) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, HeadsetService::class.java)) }
                .onFailure { DiagLog.add("Botones", "No pude iniciar el puente: ${it.message}") }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, HeadsetService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
