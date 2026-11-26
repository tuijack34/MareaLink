package uy.gero.marea

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Temporizador para dormir: pausa la música después de X minutos o al terminar el tema.
 * En los últimos 30 s baja el volumen de a poco (solo en el teléfono) y al final lo deja como estaba.
 */
object SleepTimer {
    data class State(val endsAtMs: Long?, val endOfTrack: Boolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state
    private var job: Job? = null

    /** Qué hacer al terminar: lo define el ViewModel (pausar el teléfono o la PC). */
    var pauser: () -> Unit = {}
    /** true si en este momento se está controlando la PC (no tocamos el volumen del teléfono). */
    var onPc: () -> Boolean = { false }

    private const val FADE_MS = 30_000L

    fun start(minutes: Int) {
        cancel()
        val ends = System.currentTimeMillis() + minutes * 60_000L
        _state.value = State(ends, endOfTrack = false)
        DiagLog.add("Dormir", "Pausa en $minutes min")
        job = scope.launch {
            val untilFade = ends - FADE_MS - System.currentTimeMillis()
            if (untilFade > 0) delay(untilFade)
            fadeOutAndPause()
        }
    }

    /** Pausa cuando termina el tema que está sonando. */
    fun startEndOfTrack() {
        cancel()
        _state.value = State(null, endOfTrack = true)
        DiagLog.add("Dormir", "Pausa al terminar el tema")
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value != null) DiagLog.add("Dormir", "Cancelado")
        _state.value = null
    }

    /** Lo llama el reproductor cuando un tema termina solo. Devuelve true si había que dormir (y ya pausó). */
    fun consumeEndOfTrack(): Boolean {
        if (_state.value?.endOfTrack != true) return false
        DiagLog.add("Dormir", "Terminó el tema: pauso")
        _state.value = null
        pauser()
        return true
    }

    private suspend fun fadeOutAndPause() {
        val am = MareaApp.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val fade = !onPc()
        val original = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (fade && original > 0) {
            val steps = original
            val stepMs = FADE_MS / steps.coerceAtLeast(1)
            for (v in original - 1 downTo 0) {
                delay(stepMs)
                runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
            }
        } else {
            delay(FADE_MS)
        }
        DiagLog.add("Dormir", "Tiempo cumplido: pauso")
        pauser()
        if (fade) {
            // dejamos que la pausa se asiente antes de devolver el volumen
            delay(1500)
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0) }
        }
        _state.value = null
        job = null
    }
}
