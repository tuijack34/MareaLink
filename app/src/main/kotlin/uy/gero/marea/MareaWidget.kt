package uy.gero.marea

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Portada actual ya cargada como imagen (los widgets no pueden bajar imágenes solos). */
object WidgetCover {
    val flow = MutableStateFlow<Pair<String?, Bitmap?>>(null to null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun update(ctx: Context, url: String?) {
        if (flow.value.first == url) return
        flow.value = url to null
        if (url == null) return
        scope.launch {
            val bmp = runCatching {
                SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(url).size(500).allowHardware(false).build()).image?.toBitmap()
            }.getOrNull()
            if (flow.value.first == url) flow.value = url to bmp
        }
    }
}

/**
 * Widget de "lo que suena", estilo One UI: esquinas grandes, vidrio con transparencia ajustable
 * y diseño que se adapta al tamaño (de 2x1 hasta 4x4).
 */
class MareaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MareaApp
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        app.player.state.value.current?.let { WidgetCover.update(context, it.coverLarge ?: it.cover) }
        provideContent {
            val st by app.player.state.collectAsState()
            val coverPair by WidgetCover.flow.collectAsState()
            val ver by WidgetStore.version.collectAsState()
            val prefs = remember(ver) { WidgetStore.get(context, widgetId) }
            Content(st, coverPair.second, prefs)
        }
    }

    @Composable
    private fun Content(st: PlayerUiState, cover: Bitmap?, prefs: WidgetPrefs) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val t = st.current
        val bg = remember(prefs, cover) { WidgetPaint.background(ctx, prefs, cover) }
        val dark = remember(prefs, cover) { WidgetPaint.darkText(ctx, prefs, cover) }
        val fg = if (dark) Color(0xFF111111) else Color.White
        val dim = fg.copy(alpha = 0.72f)
        val scale = when (prefs.textSize) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }
        val open = actionStartActivity<MainActivity>()
        val radius = 26.dp

        Box(GlanceModifier.fillMaxSize().cornerRadius(radius)) {
            Image(ImageProvider(bg), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (t == null) {
                Column(GlanceModifier.fillMaxSize().clickable(open).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Marea Link", style = TextStyle(color = ColorProvider(fg), fontSize = (16 * scale).sp, fontWeight = FontWeight.Bold))
                    Text("Tocá para elegir música", style = TextStyle(color = ColorProvider(dim), fontSize = (12 * scale).sp))
                }
                return@Box
            }
            val w = size.width
            val h = size.height
            when {
                // 2x2 o menos: portada a pantalla completa, título y play encima
                w < 200.dp && h >= 100.dp -> Box(GlanceModifier.fillMaxSize()) {
                    CoverImg(cover, GlanceModifier.fillMaxSize().cornerRadius(radius).clickable(open))
                    Column(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(GlanceModifier.defaultWeight()) {
                                Label(t.title, Color.White, 13 * scale, bold = true)
                                if (prefs.showArtist) Label(t.artistLine, Color.White.copy(alpha = 0.8f), 11 * scale)
                            }
                            PlayBtn(st.isPlaying, 40.dp, dark = false)
                        }
                    }
                }
                // Bajo y ancho (4x1, 3x1): todo en una fila
                h < 110.dp -> Row(GlanceModifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImg(cover, GlanceModifier.size(h - 20.dp).cornerRadius(16.dp).clickable(open))
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight().clickable(open)) {
                        Label(t.title, fg, 15 * scale, bold = true)
                        if (prefs.showArtist) Label(t.artistLine, dim, 12 * scale)
                    }
                    Controls(st.isPlaying, prefs.showSkip, dark, small = true)
                }
                // Alto (4x3, 4x4): portada grande arriba, info y controles abajo
                h >= 260.dp && w >= 200.dp -> Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                    val coverSize = minOf(w - 28.dp, h - 130.dp * scale)
                    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CoverImg(cover, GlanceModifier.size(coverSize).cornerRadius(20.dp).clickable(open))
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    Label(t.title, fg, 18 * scale, bold = true)
                    if (prefs.showArtist) Label(t.artistLine, dim, 13 * scale)
                    Spacer(GlanceModifier.defaultWeight())
                    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Controls(st.isPlaying, prefs.showSkip, dark, small = false)
                    }
                }
                // 4x2 (el típico): portada a la izquierda ocupando el alto, info y controles a la derecha
                else -> Row(GlanceModifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImg(cover, GlanceModifier.size(minOf(h - 24.dp, w * 0.45f)).cornerRadius(20.dp).clickable(open))
                    Spacer(GlanceModifier.width(14.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Column(GlanceModifier.clickable(open)) {
                            Label(t.title, fg, 17 * scale, bold = true, lines = 2)
                            if (prefs.showArtist) Label(t.artistLine, dim, 13 * scale)
                        }
                        Spacer(GlanceModifier.height(10.dp))
                        Controls(st.isPlaying, prefs.showSkip, dark, small = false)
                    }
                }
            }
        }
    }

    @Composable
    private fun Label(text: String, color: Color, sizeSp: Float, bold: Boolean = false, lines: Int = 1) {
        Text(
            text, maxLines = lines,
            style = TextStyle(color = ColorProvider(color), fontSize = TextUnit(sizeSp, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        )
    }

    @Composable
    private fun CoverImg(cover: Bitmap?, modifier: GlanceModifier) {
        if (cover != null) Image(ImageProvider(cover), null, modifier, contentScale = ContentScale.Crop)
        else Box(modifier.background(ColorProvider(Color(0x33FFFFFF)))) {}
    }

    @Composable
    private fun Controls(playing: Boolean, showSkip: Boolean, dark: Boolean, small: Boolean) {
        val s = if (small) 36.dp else 44.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showSkip) { IconBtn(R.drawable.ic_w_prev, actionRunCallback<PrevAction>(), s, dark); Spacer(GlanceModifier.width(6.dp)) }
            PlayBtn(playing, if (small) 42.dp else 52.dp, dark)
            if (showSkip) { Spacer(GlanceModifier.width(6.dp)); IconBtn(R.drawable.ic_w_next, actionRunCallback<NextAction>(), s, dark) }
        }
    }

    @Composable
    private fun PlayBtn(playing: Boolean, size: Dp, dark: Boolean) {
        // círculo sólido con el ícono "en negativo": se lee sobre cualquier fondo
        val circle = if (dark) Color(0xFF111111) else Color.White
        val icon = if (dark) Color.White else Color.Black
        Box(
            GlanceModifier.size(size).cornerRadius(size / 2).background(ColorProvider(circle)).clickable(actionRunCallback<ToggleAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
                if (playing) "Pausa" else "Reproducir",
                GlanceModifier.size(size * 0.5f),
                colorFilter = ColorFilter.tint(ColorProvider(icon)),
            )
        }
    }

    @Composable
    private fun IconBtn(res: Int, action: Action, size: Dp, dark: Boolean) {
        Box(GlanceModifier.size(size).cornerRadius(size / 2).clickable(action), contentAlignment = Alignment.Center) {
            Image(ImageProvider(res), null, GlanceModifier.size(size * 0.62f), colorFilter = ColorFilter.tint(ColorProvider(if (dark) Color(0xFF111111) else Color.White)))
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refresh(context: Context) {
            scope.launch {
                runCatching {
                    if (GlanceAppWidgetManager(context).getGlanceIds(MareaWidget::class.java).isNotEmpty()) MareaWidget().updateAll(context)
                }
            }
        }

        suspend fun widgetIds(context: Context): List<Int> = runCatching {
            val m = GlanceAppWidgetManager(context)
            m.getGlanceIds(MareaWidget::class.java).map { m.getAppWidgetId(it) }
        }.getOrDefault(emptyList())
    }
}

class MareaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MareaWidget()
}

private fun player(context: Context) = (context.applicationContext as MareaApp).player.also { it.connect() }

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { player(context).togglePlay() }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { player(context).next() }
    }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { player(context).previous() }
    }
}
