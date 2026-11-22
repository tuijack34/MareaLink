package uy.gero.marea.ui

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uy.gero.marea.PcLink

/** Color dominante de la tapa que manda la PC (para el fondo, como con los temas del teléfono). */
@Composable
private fun rememberPcTint(fallback: Color): Color {
    val cat by PcLink.catalog.collectAsState()
    cat?.let { return rememberCoverColor(it.coverLarge ?: it.cover, true, fallback) }
    val bmp by PcLink.cover.collectAsState()
    val target = remember(bmp) {
        bmp?.let { b ->
            runCatching {
                val p = androidx.palette.graphics.Palette.from(b).generate()
                Color(p.getVibrantColor(p.getDominantColor(0xFF6650A4.toInt())))
            }.getOrNull()
        } ?: fallback
    }
    val c by animateColorAsState(target, label = "pcTint")
    return c
}

@Composable
private fun PcCover(modifier: Modifier) {
    val bmp by PcLink.cover.collectAsState()
    val cat by PcLink.catalog.collectAsState()
    cat?.let { t -> SmoothCover(t.coverLarge ?: t.cover, modifier, RoundedCornerShape(28.dp)); return }
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Crossfade(bmp, label = "pcCover") { b ->
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun pcFmt(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PcSeek(accent: Color) {
    val st by PcLink.state.collectAsState()
    val pos by PcLink.position.collectAsState()
    val dur = ((st?.durationMs ?: 0L) / 1000f).coerceAtLeast(1f)
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column {
        Slider(
            value = (dragging ?: pos).coerceIn(0f, dur),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { PcLink.seekTo(it) }; dragging = null },
            valueRange = 0f..dur,
            enabled = st?.canSeek != false,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(pcFmt(dragging ?: pos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("-" + pcFmt(dur - (dragging ?: pos)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PcControls(accent: Color, big: Boolean) {
    val st by PcLink.state.collectAsState()
    val s = st
    val main = if (big) 76.dp else 64.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { PcLink.shuffle() }, enabled = s?.shuffle != null) {
            Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (s?.shuffle == true) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { PcLink.previous() }, enabled = s?.canPrevious != false) { Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(36.dp)) }
        Box(
            Modifier.size(main).clip(CircleShape).background(Color.White).clickable { PcLink.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(s?.playing == true, label = "pcPlay") { playing ->
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausa", Modifier.size(main * 0.5f), tint = Color.Black)
            }
        }
        IconButton(onClick = { PcLink.next() }, enabled = s?.canNext != false) { Icon(Icons.Default.SkipNext, "Siguiente", Modifier.size(36.dp)) }
        IconButton(onClick = { PcLink.repeat() }) {
            Icon(
                if (s?.repeat == "one") Icons.Default.RepeatOne else Icons.Default.Repeat, "Repetir",
                tint = if (s?.repeat == "off" || s == null) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            )
        }
    }
}

@Composable
private fun PcTitle(vm: MainViewModel, center: Boolean = false) {
    val st by PcLink.state.collectAsState()
    val cat by PcLink.catalog.collectAsState()
    val s = st
    Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            if (s?.hasSession == false || s == null) "No hay nada sonando" else s.title,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (s != null && s.hasSession) {
            Text(s.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.album.isNotBlank()) Text(s.album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("Abrí TIDAL en la PC y poné algo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // con el tema del catálogo se puede dar me gusta y abrir el menú (álbum, artista, playlist…)
    cat?.let { t ->
        IconButton(onClick = { vm.toggleFavorite(t) }) {
            val fav = vm.isFavorite(t)
            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Me gusta", tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { vm.sheetTrack = t }) { Icon(Icons.Default.MoreVert, "Más opciones") }
    }
    }
}

/** Letra del tema que suena en la PC, sincronizada con la posición que manda Marea PC. */
@Composable
private fun PcLyrics(vm: MainViewModel, accent: Color, modifier: Modifier = Modifier) {
    val st by PcLink.state.collectAsState()
    val cat by PcLink.catalog.collectAsState()
    val track = cat ?: remember(st?.coverKey, st?.durationMs != null && st!!.durationMs > 0) { st?.takeIf { it.hasSession && it.title.isNotBlank() }?.asTrack() }
    LyricsView(track, vm.player.state.value, vm, accent, modifier, position = PcLink.position, onSeek = { PcLink.seekTo(it) })
}

/** Botón para elegir dónde suena: este teléfono o la PC. */
@Composable
fun DeviceButton(vm: MainViewModel) {
    val status by PcLink.status.collectAsState()
    val name by PcLink.pcName.collectAsState()
    var open by remember { mutableStateOf(false) }
    if (!PcLink.isPaired && status == PcLink.Status.OFF) return
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                if (vm.onPc) Icons.Default.Computer else Icons.Default.Devices, "Dónde suena",
                tint = if (vm.onPc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text("Controlar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            DropdownMenuItem(
                text = { Text("Este teléfono") },
                leadingIcon = { Icon(Icons.Default.Smartphone, null, tint = if (!vm.onPc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                onClick = { vm.onPc = false; open = false },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(name ?: "Mi PC")
                        Text(
                            when (status) {
                                PcLink.Status.CONNECTED -> "TIDAL de escritorio"
                                PcLink.Status.CONNECTING -> "Conectando…"
                                PcLink.Status.NEEDS_CODE -> "Falta emparejar (Ajustes)"
                                else -> "Sin conexión, tocá para reintentar"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.Computer, null, tint = if (vm.onPc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    open = false
                    if (status == PcLink.Status.CONNECTED) vm.onPc = true
                    else if (status == PcLink.Status.NEEDS_CODE) { vm.onPc = false; vm.tab = Tab.SETTINGS; vm.fullPlayerOpen = false }
                    else { PcLink.resume(); vm.onPc = true }
                },
            )
        }
    }
}

@Composable
private fun PcHeader(vm: MainViewModel, onMinimize: (() -> Unit)?, onExpand: (() -> Unit)?) {
    val name by PcLink.pcName.collectAsState()
    val status by PcLink.status.collectAsState()
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onMinimize != null) IconButton(onClick = onMinimize) { Icon(Icons.Default.KeyboardArrowDown, "Minimizar", Modifier.size(30.dp)) }
        else Spacer(Modifier.size(48.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SONANDO EN LA PC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (status == PcLink.Status.CONNECTED) (name ?: "Mi PC") else "Reconectando con ${name ?: "la PC"}…",
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        DeviceButton(vm)
        if (onExpand != null) IconButton(onClick = onExpand) { Icon(Icons.Default.OpenInFull, "Pantalla completa") }
    }
}

/** Panel lateral (Fold abierto) cuando se controla la PC. */
@Composable
fun PcPane(vm: MainViewModel, modifier: Modifier = Modifier, onMinimize: (() -> Unit)?) {
    val accent = rememberPcTint(MaterialTheme.colorScheme.primary)
    Box(modifier.fillMaxHeight().background(Brush.verticalGradient(listOf(accent.darken(0.45f), MaterialTheme.colorScheme.background)))) {
        Column(Modifier.fillMaxSize()) {
            PcHeader(vm, onMinimize, onExpand = { vm.fullPlayerOpen = true })
            Row(Modifier.padding(start = 20.dp, top = 12.dp)) { PcCover(Modifier.size(140.dp)) }
            Box(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) { PcTitle(vm) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                PcSeek(accent)
                PcControls(accent, big = false)
            }
            Text("Letra", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            PcLyrics(vm, accent, Modifier.weight(1f))
        }
    }
}

/** Reproductor a pantalla completa cuando se controla la PC. */
@Composable
fun PcFullPlayer(vm: MainViewModel, wide: Boolean) {
    val accent = rememberPcTint(MaterialTheme.colorScheme.primary)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.darken(0.4f), MaterialTheme.colorScheme.background))).dragDownToDismiss { vm.fullPlayerOpen = false }) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                PcHeader(vm, onMinimize = { vm.fullPlayerOpen = false }, onExpand = null)
                if (wide) {
                    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            PcCover(Modifier.fillMaxWidth().widthIn(max = 520.dp).aspectRatio(1f))
                            Spacer(Modifier.height(20.dp))
                            PcTitle(vm)
                            PcSeek(accent)
                            PcControls(accent, big = true)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.25f))) {
                            PcLyrics(vm, accent)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        PcCover(Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 12.dp))
                        Spacer(Modifier.height(18.dp))
                        PcTitle(vm)
                        PcSeek(accent)
                        PcControls(accent, big = true)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.08f))
                        PcLyrics(vm, accent, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Mini reproductor de la PC (barra de abajo). */
@Composable
fun PcMiniPlayer(vm: MainViewModel, onExpand: () -> Unit) {
    val st by PcLink.state.collectAsState()
    val pos by PcLink.position.collectAsState()
    val bmp by PcLink.cover.collectAsState()
    val accent = rememberPcTint(MaterialTheme.colorScheme.primary)
    val s = st
    val dur = ((s?.durationMs ?: 0L) / 1000f).coerceAtLeast(1f)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .swipeHorizontal(onSwipeLeft = { PcLink.next() }, onSwipeRight = { PcLink.previous() })
            .swipeUp { onExpand() }
            .pressScale()
            .clip(RoundedCornerShape(GroupRadius))
            .background(Brush.horizontalGradient(listOf(accent.darken(0.55f), MaterialTheme.colorScheme.surfaceContainerHighest)))
            .clickable { onExpand() },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                val b = bmp
                val cat by PcLink.catalog.collectAsState()
                val c = cat
                if (c != null) SmoothCover(c.cover, Modifier.fillMaxSize(), RoundedCornerShape(14.dp))
                else if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Computer, null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (s?.hasSession == true) s.title else "PC: no hay nada sonando", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Computer, null, Modifier.size(13.dp), tint = accent)
                    Spacer(Modifier.width(4.dp))
                    Text(s?.artist.orEmpty().ifBlank { "En la PC" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DeviceButton(vm)
            IconButton(onClick = { PcLink.toggle() }) { Icon(if (s?.playing == true) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausa") }
            IconButton(onClick = { PcLink.next() }) { Icon(Icons.Default.SkipNext, "Siguiente") }
        }
        LinearProgressIndicator(
            progress = { (pos / dur).coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp).height(2.dp),
            color = accent, trackColor = Color.Transparent, drawStopIndicator = {},
        )
    }
}
