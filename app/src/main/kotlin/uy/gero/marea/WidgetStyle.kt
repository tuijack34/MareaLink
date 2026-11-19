package uy.gero.marea

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Ajustes de cada widget (cada widget del inicio puede verse distinto). */
data class WidgetPrefs(
    /** 0..100: qué tan opaco es el fondo (100 = sólido, 0 = invisible). Igual que el slider de Samsung. */
    val opacity: Int = 20,
    /** true = "Vidrio" (tono del fondo de pantalla con degradé, como las carpetas de One UI); false = sólido oscuro. */
    val glass: Boolean = true,
    /** 0 = fondo de pantalla, 1 = portada del tema, 2.. = tonos fijos */
    val tint: Int = 0,
    /** 0 = automático, 1 = claro, 2 = oscuro (color del texto/íconos) */
    val theme: Int = 0,
    /** Tamaño del texto: 0 chico, 1 normal, 2 grande */
    val textSize: Int = 1,
    val showArtist: Boolean = true,
    val showSkip: Boolean = true,
) {
    fun toJson(): String = JSONObject()
        .put("opacity", opacity).put("glass", glass).put("tint", tint).put("theme", theme)
        .put("textSize", textSize).put("showArtist", showArtist).put("showSkip", showSkip).toString()

    companion object {
        val FIXED_TINTS = listOf(
            "Verde oliva" to 0xFF5E6B3A.toInt(),
            "Gris" to 0xFF5C6166.toInt(),
            "Azul" to 0xFF2F4E7A.toInt(),
            "Violeta" to 0xFF4E3A78.toInt(),
            "Marrón" to 0xFF6A4B36.toInt(),
            "Negro" to 0xFF111111.toInt(),
        )

        fun fromJson(s: String?): WidgetPrefs {
            if (s.isNullOrBlank()) return WidgetPrefs()
            return runCatching {
                val o = JSONObject(s)
                WidgetPrefs(
                    o.optInt("opacity", 20), o.optBoolean("glass", true), o.optInt("tint", 0), o.optInt("theme", 0),
                    o.optInt("textSize", 1), o.optBoolean("showArtist", true), o.optBoolean("showSkip", true),
                )
            }.getOrDefault(WidgetPrefs())
        }
    }
}

object WidgetStore {
    /** Sube cada vez que se guarda algo, para que los widgets abiertos se redibujen. */
    val version = MutableStateFlow(0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun get(ctx: Context, id: Int): WidgetPrefs = WidgetPrefs.fromJson(prefs(ctx).getString("w$id", null))

    fun save(ctx: Context, id: Int, p: WidgetPrefs) {
        prefs(ctx).edit().putString("w$id", p.toJson()).apply()
        version.value++
    }
}

/** Colores y fondo del widget. Se usa igual en el widget real y en la vista previa. */
object WidgetPaint {

    fun wallpaperColor(ctx: Context): Int? = runCatching {
        WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    }.getOrNull()

    fun coverColor(cover: Bitmap?): Int? = cover?.let {
        runCatching {
            val p = Palette.from(it).maximumColorCount(16).generate()
            (p.darkVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb
        }.getOrNull()
    }

    fun baseColor(ctx: Context, prefs: WidgetPrefs, cover: Bitmap?): Int {
        if (!prefs.glass) return 0xFF0E1217.toInt()
        return when (prefs.tint) {
            0 -> wallpaperColor(ctx) ?: 0xFF4A5560.toInt()
            1 -> coverColor(cover) ?: wallpaperColor(ctx) ?: 0xFF4A5560.toInt()
            else -> WidgetPrefs.FIXED_TINTS.getOrNull(prefs.tint - 2)?.second ?: 0xFF4A5560.toInt()
        }
    }

    /** true si el texto tiene que ser oscuro (fondo claro). */
    fun darkText(ctx: Context, prefs: WidgetPrefs, cover: Bitmap?): Boolean = when (prefs.theme) {
        1 -> true
        2 -> false
        else -> {
            // con poca opacidad manda lo que hay atrás: asumimos texto claro (como One UI)
            val c = baseColor(ctx, prefs, cover)
            prefs.opacity >= 60 && ColorUtils.calculateLuminance(c) > 0.55
        }
    }

    /**
     * Fondo estilo vidrio: el tono elegido, un poco más claro arriba y más oscuro abajo,
     * con la opacidad del slider. Se dibuja como imagen porque los widgets no admiten degradés.
     */
    fun background(ctx: Context, prefs: WidgetPrefs, cover: Bitmap?, w: Int = 240, h: Int = 240): Bitmap {
        val base = baseColor(ctx, prefs, cover)
        val a = (prefs.opacity.coerceIn(0, 100) * 255 / 100)
        val top = if (prefs.glass) ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), 0.18f) else base
        val bottom = if (prefs.glass) ColorUtils.blendARGB(base, 0xFF000000.toInt(), 0.22f) else base
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), ColorUtils.setAlphaComponent(top, a), ColorUtils.setAlphaComponent(bottom, a), Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }
}
