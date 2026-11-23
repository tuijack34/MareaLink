package uy.gero.marea

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Registro en memoria de lo que pasa por dentro, para poder verlo desde Ajustes → Diagnóstico. */
object DiagLog {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun add(tag: String, msg: String) {
        Log.i("Marea/$tag", msg)
        val line = "${fmt.format(Date())} [$tag] $msg"
        _lines.update { (it + line).takeLast(800) }
    }

    fun clear() { _lines.value = emptyList() }

    fun dump(): String = _lines.value.joinToString("\n")
}
