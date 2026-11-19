package uy.gero.marea

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uy.gero.marea.ui.MareaTheme

/** Pantalla para personalizar cada widget (se abre al ponerlo y con "Configuración" al mantenerlo apretado). */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        lifecycleScope.launch {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                // abierto desde la app: tomamos el primer widget que haya
                widgetId = MareaWidget.widgetIds(this@WidgetConfigActivity).firstOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
            }
            val id = widgetId
            setContent {
                val s by MareaApp.instance.settings.state.collectAsState()
                MareaTheme(s.accent, s.pureBlack) {
                    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
                            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Todavía no pusiste el widget. Mantené apretado el inicio → Widgets → Marea Link.")
                            }
                        } else {
                            ConfigScreen(WidgetStore.get(this@WidgetConfigActivity, id)) { p ->
                                WidgetStore.save(this@WidgetConfigActivity, id, p)
                                MareaWidget.refresh(this@WidgetConfigActivity)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(initial: WidgetPrefs, onSave: (WidgetPrefs) -> Unit) {
    var p by remember { mutableStateOf(initial) }
    val ctx = LocalContext.current
    val st by MareaApp.instance.player.state.collectAsState()
    val cover by WidgetCover.flow.collectAsState()
    val wallpaper = remember {
        runCatching { WallpaperManager.getInstance(ctx).drawable?.toBitmap(600, 600)?.asImageBitmap() }.getOrNull()
    }
    val wallColors = remember { WidgetPaint.wallpaperColor(ctx)?.let { Color(it) } ?: Color(0xFF3A4A3A) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Vista previa sobre el fondo de pantalla (o sus colores, si Android no nos deja leerlo)
        Box(
            Modifier.fillMaxWidth().height(260.dp).padding(12.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(wallColors, wallColors.copy(alpha = 0.6f), Color.Black))),
            contentAlignment = Alignment.Center,
        ) {
            if (wallpaper != null) Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Preview(p, st.current, cover.second)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Label("Estilo")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(p.glass, { p = p.copy(glass = true) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Vidrio") }
                SegmentedButton(!p.glass, { p = p.copy(glass = false) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Sólido") }
            }
            Label("Transparencia del fondo: ${100 - p.opacity}%")
            // Igual que el slider de Samsung: a la derecha = más transparente
            Slider((100 - p.opacity).toFloat(), { p = p.copy(opacity = 100 - it.toInt()) }, valueRange = 0f..100f)
            Text(
                "Como en One UI: 0% de transparencia = fondo sólido, 100% = invisible. Para que se parezca a las carpetas de Samsung, dejala entre 80 y 100%.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.glass) {
                Label("Tono del vidrio")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val opts = listOf("Fondo de pantalla", "Portada") + WidgetPrefs.FIXED_TINTS.map { it.first }
                    opts.forEachIndexed { i, label ->
                        FilterChip(p.tint == i, { p = p.copy(tint = i) }, { Text(label) }, shape = RoundedCornerShape(50))
                    }
                }
            }
            Label("Color del texto")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Automático", "Oscuro", "Claro").forEachIndexed { i, l ->
                    SegmentedButton(p.theme == i, { p = p.copy(theme = i) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                }
            }
            Label("Tamaño del texto")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Chico", "Normal", "Grande").forEachIndexed { i, l ->
                    SegmentedButton(p.textSize == i, { p = p.copy(textSize = i) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                }
            }
            SwitchLine("Mostrar artista", p.showArtist) { p = p.copy(showArtist = it) }
            SwitchLine("Botones de anterior y siguiente", p.showSkip) { p = p.copy(showSkip = it) }
            Text(
                "El tamaño se cambia en el inicio: mantené apretado el widget y arrastrá los bordes. El diseño se acomoda solo (de 2x1 a 4x4).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        Button(onClick = { onSave(p) }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(50)) { Text("Guardar") }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))

@Composable
private fun SwitchLine(t: String, v: Boolean, on: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { on(!v) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, Modifier.weight(1f)); Switch(v, on)
    }
}

/** Réplica del widget 4x2 para la vista previa. */
@Composable
private fun Preview(p: WidgetPrefs, t: Track?, cover: android.graphics.Bitmap?) {
    val ctx = LocalContext.current
    val bg = remember(p, cover) { WidgetPaint.background(ctx, p, cover).asImageBitmap() }
    val dark = remember(p, cover) { WidgetPaint.darkText(ctx, p, cover) }
    val fg = if (dark) Color(0xFF111111) else Color.White
    val scale = when (p.textSize) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }
    Box(Modifier.width(330.dp).height(150.dp).clip(RoundedCornerShape(26.dp))) {
        Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF))) {
                cover?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t?.title ?: "Nombre del tema", color = fg, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.showArtist) Text(t?.artistLine ?: "Artista", color = fg.copy(alpha = 0.72f), fontSize = (13 * scale).sp, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.showSkip) Text("⏮", color = fg, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (dark) Color(0xFF111111) else Color.White), contentAlignment = Alignment.Center) {
                        Text("▶", color = if (dark) Color.White else Color.Black, fontSize = 18.sp)
                    }
                    if (p.showSkip) Text("⏭", color = fg, fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}
