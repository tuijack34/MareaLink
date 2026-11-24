package uy.gero.marea.ui

import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uy.gero.marea.Lyrics
import uy.gero.marea.PlayerController
import uy.gero.marea.PlayerUiState
import uy.gero.marea.RepeatMode
import uy.gero.marea.Track

enum class PlayerTab(val label: String) { NOW("Ahora"), LYRICS("Letra"), QUEUE("Cola") }

// ---------- Piezas ----------

@Composable
fun StreamQuality(st: PlayerUiState, center: Boolean = false) {
    val s = st.stream
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s == null) {
            st.current?.let { t ->
                when {
                    t.isHiRes -> QualityBadge("HI-RES", highlight = true)
                    t.tags.any { it.contains("LOSSLESS") } -> QualityBadge("LOSSLESS", highlight = true)
                }
                if (t.isAtmos) { Spacer(Modifier.width(6.dp)); QualityBadge("ATMOS") }
                Spacer(Modifier.width(8.dp))
                // formato exacto si TIDAL lo informa; si no, lo que garantiza cada nivel de TIDAL
                val exact by androidx.compose.runtime.produceState<String?>(null, t.id) { value = uy.gero.marea.MareaApp.instance.api.audioFormat(t.id) }
                val lossless = t.tags.any { it.contains("LOSSLESS") }
                val spec = exact ?: when {
                    t.isHiRes -> "24-bit · hasta 192 kHz"
                    lossless -> "16-bit · 44.1 kHz"
                    else -> null
                }
                Text(
                    if (spec != null) "$spec · máx. disponible" else "la reproduce TIDAL",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            return@Row
        }
        if (s.isPreview) { QualityBadge("PREVIEW" + (s.previewReason?.let { " · $it" } ?: ""), highlight = false); Spacer(Modifier.width(6.dp)) }
        if (s.tier.isNotBlank()) { QualityBadge(s.tier, highlight = s.tier == "HI-RES" || s.tier == "LOSSLESS"); Spacer(Modifier.width(8.dp)) }
        Text(s.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SeekBar(st: PlayerUiState, player: PlayerController, accent: Color) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = (st.durationSec.takeIf { it > 0 } ?: st.current?.durationSec?.toFloat() ?: 0f).coerceAtLeast(1f)
    val live by player.position.collectAsState()
    val pos = dragging ?: live.coerceIn(0f, dur)
    Column {
        Slider(
            value = pos, valueRange = 0f..dur,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { player.seekTo(it) }; dragging = null },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.18f)),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(fmt(pos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("-" + fmt(dur - pos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Controls(st: PlayerUiState, player: PlayerController, accent: Color, big: Boolean = true) {
    val main = if (big) 76.dp else 60.dp
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun tick() = haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { player.toggleShuffle() }) {
            Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (st.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { tick(); player.previous() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(36.dp)) }
        FilledIconButton(
            onClick = { tick(); player.togglePlay() }, modifier = Modifier.size(main).pressScale(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            if (st.isBuffering) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.dp)
            else AnimatedContent(
                st.isPlaying,
                transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                label = "pp",
            ) { playing -> Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausa", Modifier.size(if (big) 40.dp else 32.dp)) }
        }
        IconButton(onClick = { tick(); player.next() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, "Siguiente", Modifier.size(36.dp)) }
        IconButton(onClick = { player.cycleRepeat() }) {
            Icon(
                if (st.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repetir",
                tint = if (st.repeat != RepeatMode.OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TrackTitleBlock(t: Track, vm: MainViewModel, accent: Color, center: Boolean = false) {
    val mismatch by vm.player.mismatch.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
            // TIDAL está sonando otra cosa que lo pedido: lo decimos claro en vez de mostrar un título que no es
            mismatch?.let { real ->
                Row(
                    Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFB300)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(15.dp), tint = Color(0xFFFFC94D))
                    Spacer(Modifier.width(6.dp))
                    Text("En TIDAL suena: $real", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD98A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(t.fullTitle, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (center) TextAlign.Center else TextAlign.Start)
            Text(
                t.artistLine, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openArtist(t.artists.firstOrNull()?.id) },
            )
            if (t.albumTitle != null) Text(
                t.albumTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.openAlbum(t.albumId) },
            )
        }
        IconButton(onClick = { vm.openAddToPlaylist(t) }) {
            Icon(Icons.Default.LibraryAdd, "Agregar a una playlist", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { vm.toggleFavorite(t) }) {
            val fav = vm.isFavorite(t)
            // con tapas oscuras el color de acento queda casi negro y el corazón "desaparece": lo aclaramos
            val heart = if (accent.luminance() < 0.35f) androidx.compose.ui.graphics.lerp(accent, Color.White, 0.55f) else accent
            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (fav) "Quitar de me gusta" else "Me gusta", tint = if (fav) heart else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { vm.sheetTrack = t }) { Icon(Icons.Default.MoreVert, "Más opciones") }
    }
}

@Composable
fun SuggestButton(st: PlayerUiState, player: PlayerController, modifier: Modifier = Modifier) {
    TextButton(onClick = { player.addSuggestions() }, enabled = !st.loadingSuggestions, modifier = modifier) {
        if (st.loadingSuggestions) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Sugeridas")
    }
}

// ---------- Letra ----------

@Composable
fun LyricsView(
    track: Track?, st: PlayerUiState, vm: MainViewModel, accent: Color, modifier: Modifier = Modifier,
    // para la PC: otra fuente de posición y otra forma de saltar
    position: kotlinx.coroutines.flow.StateFlow<Float> = vm.player.position,
    onSeek: (Float) -> Unit = { vm.player.seekTo(it) },
) {
    var lyrics by remember(track?.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track?.id) { mutableStateOf(true) }
    var failed by remember(track?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(track?.id) {
        if (track == null) return@LaunchedEffect
        loading = true
        runCatching { if (track.id.all { it.isDigit() }) vm.api.lyrics(track.id) else null }
            .recoverCatching { null }
            .mapCatching { it ?: if (uy.gero.marea.MareaApp.instance.settings.value.lrclib) uy.gero.marea.LrcLib.find(track) else null }
            .onSuccess { lyrics = it }
            .onFailure { failed = it.message; uy.gero.marea.DiagLog.add("Letra", "Falló: ${it.message}") }
        loading = false
    }
    Box(modifier.fillMaxSize()) {
        val l = lyrics
        when {
            track == null -> {}
            loading -> LoadingBox()
            l == null -> ErrorBox(if (failed != null) "No pude cargar la letra ($failed)" else "No encontré la letra de este tema")
            l.synced != null -> Box(Modifier.fillMaxSize()) {
                SyncedLyrics(l, position, onSeek, accent)
                Text("Letra: ${l.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
            }
            else -> Text(
                l.plain.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 34.sp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                textAlign = if (l.rtl) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun SyncedLyrics(l: Lyrics, position: kotlinx.coroutines.flow.StateFlow<Float>, onSeek: (Float) -> Unit, accent: Color) {
    val lines = l.synced.orEmpty()
    val live by position.collectAsState()
    val posMs = (live * 1000).toLong() + 250
    val active = lines.indexOfLast { it.timeMs <= posMs }.coerceAtLeast(0)
    val state = rememberLazyListState()
    // La línea activa queda a un tercio desde arriba (antes quedaba abajo y se cortaba)
    LaunchedEffect(active) { state.animateScrollToItem(active) }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val topPad = maxHeight * 0.28f
    LazyColumn(state = state, contentPadding = PaddingValues(top = topPad, bottom = maxHeight * 0.6f), modifier = Modifier.fillMaxSize()) {
        items(lines.size) { i ->
            val isActive = i == active
            val alpha by animateFloatAsState(if (isActive) 1f else if (i < active) 0.35f else 0.5f, label = "ly")
            val dist = kotlin.math.abs(i - active).coerceAtMost(4)
            val blurDp by animateFloatAsState(if (isActive) 0f else dist * 0.9f, tween(400), label = "blur")
            val scale by animateFloatAsState(if (isActive) 1f else 0.94f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "lys")
            Text(
                lines[i].text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold, lineHeight = 34.sp),
                color = (if (isActive) Color.White else Color.White).copy(alpha = alpha),
                textAlign = if (l.rtl) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                    .then(if (android.os.Build.VERSION.SDK_INT >= 31 && blurDp > 0.1f) Modifier.blur(blurDp.dp) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSeek(lines[i].timeMs / 1000f) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
    }
}

// ---------- Cola ----------

@Composable
fun QueueView(st: PlayerUiState, vm: MainViewModel, actions: TrackActions, modifier: Modifier = Modifier) {
    val player = vm.player
    val upNext = st.upNext
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // claves estables (id + número de aparición): así un tema arrastrado no pierde el dedo al cambiar de lugar
    val keys = remember(upNext) {
        val seen = HashMap<String, Int>()
        upNext.map { t -> val n = seen.merge(t.id, 1, Int::plus)!!; "${t.id}#$n" }
    }
    val latestKeys by androidx.compose.runtime.rememberUpdatedState(keys)
    val latestIndex by androidx.compose.runtime.rememberUpdatedState(st.index)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowH by remember { mutableFloatStateOf(1f) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
                Text("A continuación", style = MaterialTheme.typography.titleMedium)
                st.sourceLabel?.let { Text("Desde: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Row {
                    SuggestButton(st, player)
                    if (upNext.isNotEmpty()) TextButton(onClick = { player.clearUpNext() }) { Text("Vaciar") }
                }
            }
        }
        if (upNext.isEmpty()) item { ErrorBox("La cola está vacía. Tocá \"Sugeridas\" para seguir escuchando.") }
        val manual = st.manualCount.coerceAtMost(upNext.size)

        @Composable
        fun row(pos: Int, groupI: Int, count: Int) {
            val realIndex = st.index + 1 + pos
            val t = upNext[pos]
            val key = keys[pos]
            val dragging = dragKey == key
            Row(
                Modifier
                    .then(if (dragging) Modifier.zIndex(2f).graphicsLayer { translationY = dragOffset; shadowElevation = 18f; scaleX = 1.02f; scaleY = 1.02f } else Modifier)
                    .onSizeChanged { if (it.height > 0) rowH = it.height.toFloat() }
                    .groupItem(groupI, count)
                    // derecha = subir a "a continuación" (ya no salta ni borra lo del medio) · izquierda = quitar
                    .swipeHorizontal(
                        onSwipeLeft = { player.removeFromQueue(realIndex) },
                        onSwipeRight = { player.moveToNext(realIndex); vm.message = "Suena a continuación" },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { TrackRow(t, false, actions, onClick = { player.jumpTo(realIndex) }, swipe = false) }
                IconButton(onClick = { player.removeFromQueue(realIndex) }) { Icon(Icons.Default.Close, "Quitar") }
                // manija para reordenar: arrastrá hacia arriba o abajo
                Icon(
                    Icons.Default.DragHandle, "Mover",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp).size(28.dp).pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { dragKey = key; dragOffset = 0f; haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                            onDragEnd = { dragKey = null; dragOffset = 0f },
                            onDragCancel = { dragKey = null; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val h = rowH.coerceAtLeast(1f)
                                var cur = latestKeys.indexOf(key)
                                while (cur >= 0 && dragOffset > h * 0.55f && cur < latestKeys.size - 1) {
                                    val base = latestIndex + 1
                                    player.moveUpNext(base + cur, base + cur + 1)
                                    dragOffset -= h; cur++
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                                while (cur > 0 && dragOffset < -h * 0.55f) {
                                    val base = latestIndex + 1
                                    player.moveUpNext(base + cur, base + cur - 1)
                                    dragOffset += h; cur--
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                            },
                        )
                    },
                )
            }
        }
        if (manual > 0) {
            item { GroupLabel("En tu cola") }
            items(manual, key = { keys[it] }) { i -> Box(if (dragKey == keys[i]) Modifier.zIndex(2f) else Modifier.animateItem()) { row(i, i, manual) } }
        }
        val restCount = upNext.size - manual
        if (restCount > 0) {
            item { GroupLabel(if (st.sourceLabel != null) "Después, de: ${st.sourceLabel}" else "Después") }
            items(restCount, key = { keys[manual + it] }) { i -> Box(if (dragKey == keys[manual + i]) Modifier.zIndex(2f) else Modifier.animateItem()) { row(manual + i, i, restCount) } }
        }
    }
}

// ---------- Mini reproductor (teléfono cerrado) ----------

@Composable
fun MiniPlayer(st: PlayerUiState, vm: MainViewModel, coverColors: Boolean, onExpand: () -> Unit = { vm.fullPlayerOpen = true }) {
    val t = st.current ?: return
    val tint = rememberCoverColor(t.cover, coverColors, MaterialTheme.colorScheme.primary)
    val dur = (st.durationSec.takeIf { it > 0 } ?: t.durationSec.toFloat()).coerceAtLeast(1f)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .swipeHorizontal(onSwipeLeft = { vm.player.next() }, onSwipeRight = { vm.player.previous() })
            .swipeUp { onExpand() }
            .pressScale()
            .clip(RoundedCornerShape(GroupRadius))
            .background(Brush.horizontalGradient(listOf(tint.darken(0.55f), MaterialTheme.colorScheme.surfaceContainerHighest)))
            .clickable { onExpand() },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmoothCover(t.cover, Modifier.size(46.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.fullTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(t.artistLine, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.player.togglePlay() }) { Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausa") }
            IconButton(onClick = { vm.player.next() }) { Icon(Icons.Default.SkipNext, "Siguiente") }
        }
        LinearProgressIndicator(
            progress = { (vm.player.position.value / dur).coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp).height(2.dp),
            color = tint, trackColor = Color.Transparent, drawStopIndicator = {},
        )
    }
}

// ---------- Panel lateral (pantalla abierta) ----------

@Composable
fun NowPlayingPane(st: PlayerUiState, vm: MainViewModel, actions: TrackActions, coverColors: Boolean, modifier: Modifier = Modifier, onMinimize: (() -> Unit)? = null) {
    val t = st.current
    val accent = rememberCoverColor(t?.coverLarge ?: t?.cover, coverColors, MaterialTheme.colorScheme.primary)
    var tab by rememberSaveable { mutableStateOf(PlayerTab.QUEUE) }
    Box(
        modifier.fillMaxHeight()
            .clipToBounds() // el fondo animado dibuja círculos grandes: sin esto se "derraman" sobre la izquierda
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (t != null) AnimatedCoverBackground(accent, intensity = 0.7f)

        if (t == null) {
            // Estado vacío: el panel queda siempre, con accesos rápidos
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(150.dp).clip(RoundedCornerShape(28.dp))) { LikedArt() }
                Spacer(Modifier.height(20.dp))
                Text("Nada sonando", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (st.tidalConnected) "Elegí algo de tu biblioteca o empezá con tus me gusta" else "Abrí TIDAL o elegí algo de tu biblioteca",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Button(
                    onClick = { val l = vm.likedTracks(); if (l.tracks.isNotEmpty()) vm.player.playShuffled(l.tracks.toList(), "Tus me gusta") else vm.open(Screen.LikedS) },
                    shape = RoundedCornerShape(50),
                ) { Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Tus me gusta en aleatorio") }
                if (!st.tidalConnected) TextButton(onClick = { vm.player.openTidalApp() }) { Text("Abrir TIDAL") }
            }
            return@Box
        }
        val scrollAll = uy.gero.marea.MareaApp.instance.settings.state.collectAsState().value.paneScrollAll
        val scrollAllNow by androidx.compose.runtime.rememberUpdatedState(scrollAll)
        // Cuánto de la parte de arriba (tapa, título, controles) está escondido, en px
        val collapse = remember { androidx.compose.animation.core.Animatable(0f) }
        val headerH = remember { intArrayOf(0) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LaunchedEffect(scrollAll) { if (!scrollAll) collapse.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f)) }
        val conn = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                // Subir el dedo: primero se esconde la parte de arriba, después scrollea la lista
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (!scrollAllNow || available.y >= 0f) return androidx.compose.ui.geometry.Offset.Zero
                    val old = collapse.value
                    val new = (old - available.y).coerceAtMost(headerH[0].toFloat())
                    scope.launch { collapse.snapTo(new) }
                    return androidx.compose.ui.geometry.Offset(0f, -(new - old))
                }
                // Bajar el dedo: la lista vuelve arriba de todo y recién ahí reaparece la tapa
                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (!scrollAllNow || available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
                    val old = collapse.value
                    val new = (old - available.y).coerceAtLeast(0f)
                    scope.launch { collapse.snapTo(new) }
                    return androidx.compose.ui.geometry.Offset(0f, old - new)
                }
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPad().then(if (onMinimize != null) Modifier.dragDownToDismiss(onMinimize) else Modifier)) {
            androidx.compose.ui.layout.Layout(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().nestedScroll(conn),
                content = {
                    // ----- Parte de arriba (se puede esconder si está desfijado) -----
                    Column(Modifier.fillMaxWidth()) {
                        // Cabecera como Spotify: minimizar, de dónde viene lo que suena, fijar y pantalla completa
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (onMinimize != null) IconButton(onClick = onMinimize) { Icon(Icons.Default.KeyboardArrowDown, "Minimizar", Modifier.size(30.dp)) }
                            else Spacer(Modifier.size(48.dp))
                            Spacer(Modifier.size(48.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("REPRODUCIENDO DESDE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(st.sourceLabel ?: "TIDAL", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DeviceButton(vm)
                            IconButton(onClick = { uy.gero.marea.MareaApp.instance.settings.update { it.copy(paneScrollAll = !it.paneScrollAll) } }) {
                                Crossfade(scrollAll, label = "pin") { all ->
                                    Icon(
                                        if (all) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                                        if (all) "Fijar la parte de arriba" else "Desfijar (scrollear todo)",
                                        tint = if (all) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            IconButton(onClick = { vm.fullPlayerOpen = true }) { Icon(Icons.Default.OpenInFull, "Pantalla completa") }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
                            GestureCover(
                                Modifier.size(140.dp), onNext = { vm.player.next() }, onPrevious = { vm.player.previous() },
                                onLongPress = { vm.player.state.value.current?.let { vm.sheetTrack = it } }, onDoubleTap = { if (!vm.isFavorite(t)) vm.toggleFavorite(t) }, onTap = { vm.fullPlayerOpen = true },
                            ) { BreathingCover(t, st.isPlaying, Modifier.fillMaxSize()) }
                        }
                        AnimatedContent(
                            t, contentKey = { it.id },
                            transitionSpec = { (slideInVertically { it / 3 } + fadeIn(tween(300))) togetherWith (slideOutVertically { -it / 3 } + fadeOut(tween(200))) },
                            label = "title",
                        ) { tr -> Box(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) { TrackTitleBlock(tr, vm, accent) } }
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            StreamQuality(st)
                            SeekBar(st, vm.player, accent)
                            Controls(st, vm.player, accent, big = false)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // ----- Cola / Letra (las pestañas quedan siempre a mano) -----
                    Column(Modifier.fillMaxWidth()) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            listOf(PlayerTab.QUEUE, PlayerTab.LYRICS).forEachIndexed { i, pt ->
                                SegmentedButton(selected = tab == pt, onClick = { tab = pt }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(pt.label) }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                PlayerTab.LYRICS -> LyricsView(t, st, vm, accent)
                                else -> QueueView(st, vm, actions)
                            }
                        }
                    }
                },
            ) { ms, c ->
                val top = ms[0].measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                headerH[0] = top.height
                // Desfijado: la lista ocupa todo el alto, así al esconder la tapa no se re-mide en cada frame
                val bodyH = if (scrollAll) c.maxHeight else (c.maxHeight - top.height).coerceAtLeast(0)
                val body = ms[1].measure(androidx.compose.ui.unit.Constraints.fixed(c.maxWidth, bodyH))
                layout(c.maxWidth, c.maxHeight) {
                    val off = collapse.value.toInt().coerceIn(0, top.height)
                    top.place(0, -off)
                    body.place(0, top.height - off)
                }
            }
        }
    }
}

// ---------- Reproductor a pantalla completa ----------

@Composable
fun FullPlayer(st: PlayerUiState, vm: MainViewModel, actions: TrackActions, coverColors: Boolean) {
    val t = st.current ?: run { vm.fullPlayerOpen = false; return }
    val accent = rememberCoverColor(t.coverLarge ?: t.cover, coverColors, MaterialTheme.colorScheme.primary)
    var tab by rememberSaveable { mutableStateOf(PlayerTab.NOW) }
    Box(Modifier.fillMaxSize().clickable(enabled = false) {}) {
        AnimatedCoverBackground(accent)
        WidthAware { wide ->
            Column(Modifier.fillMaxSize().dragDownToDismiss { vm.fullPlayerOpen = false }.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.fullPlayerOpen = false }) { Icon(Icons.Default.KeyboardArrowDown, "Cerrar", Modifier.size(32.dp)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REPRODUCIENDO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        st.sourceLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (wide) {
                        val stacked = uy.gero.marea.MareaApp.instance.settings.state.collectAsState().value.playerStacked
                        IconButton(onClick = { uy.gero.marea.MareaApp.instance.settings.update { it.copy(playerStacked = !it.playerStacked) } }) {
                            Icon(if (stacked) Icons.Default.VerticalSplit else Icons.Default.ViewAgenda, if (stacked) "Letra al costado" else "Letra abajo")
                        }
                    }
                    DeviceButton(vm)
                    SuggestButton(st, vm.player)
                }
                val stackedMode = uy.gero.marea.MareaApp.instance.settings.state.collectAsState().value.playerStacked
                if (wide && stackedMode) {
                    // Reproductor ocupando toda la pantalla; scrolleando hacia abajo aparecen la letra y la cola
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                        val h = maxHeight
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                                Column(Modifier.widthIn(max = 620.dp).fillMaxHeight().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                                    GestureCover(
                                        Modifier.fillMaxWidth().aspectRatio(1f).weight(1f, fill = false),
                                        onNext = { vm.player.next() }, onPrevious = { vm.player.previous() },
                                        onLongPress = { vm.player.state.value.current?.let { vm.sheetTrack = it } }, onDoubleTap = { if (!vm.isFavorite(t)) vm.toggleFavorite(t) },
                                    ) { BreathingCover(t, st.isPlaying, Modifier.fillMaxSize()) }
                                    Spacer(Modifier.height(20.dp))
                                    TrackTitleBlock(t, vm, accent)
                                    Spacer(Modifier.height(6.dp))
                                    StreamQuality(st)
                                    SeekBar(st, vm.player, accent)
                                    Controls(st, vm.player, accent)
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Letra y cola", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().height(h * 0.85f).padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(GroupRadius)).background(Color.Black.copy(alpha = 0.28f)),
                            ) {
                                Column {
                                    Text("Letra", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, top = 18.dp))
                                    LyricsView(t, st, vm, accent)
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().height(h * 0.85f).padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(GroupRadius)).background(Color.Black.copy(alpha = 0.28f)),
                            ) { QueueView(st, vm, actions) }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                } else if (wide) {
                    // Pantalla abierta: portada + controles a la izquierda, letra/cola a la derecha.
                    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            GestureCover(
                                Modifier.fillMaxWidth().widthIn(max = 520.dp).aspectRatio(1f).weight(1f, fill = false),
                                onNext = { vm.player.next() }, onPrevious = { vm.player.previous() },
                                onLongPress = { vm.player.state.value.current?.let { vm.sheetTrack = it } }, onDoubleTap = { if (!vm.isFavorite(t)) vm.toggleFavorite(t) },
                            ) { BreathingCover(t, st.isPlaying, Modifier.fillMaxSize()) }
                            Spacer(Modifier.height(20.dp))
                            TrackTitleBlock(t, vm, accent)
                            Spacer(Modifier.height(6.dp))
                            StreamQuality(st)
                            SeekBar(st, vm.player, accent)
                            Controls(st, vm.player, accent)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(GroupRadius)).background(Color.Black.copy(alpha = 0.25f))) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                                listOf(PlayerTab.LYRICS, PlayerTab.QUEUE).forEachIndexed { i, pt ->
                                    SegmentedButton(selected = (if (tab == PlayerTab.NOW) PlayerTab.LYRICS else tab) == pt, onClick = { tab = pt }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(pt.label) }
                                }
                            }
                            if (tab == PlayerTab.QUEUE) QueueView(st, vm, actions) else LyricsView(t, st, vm, accent)
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(tab, label = "tab") { cur ->
                            when (cur) {
                                PlayerTab.NOW -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
                                    GestureCover(
                                        Modifier.fillMaxWidth().aspectRatio(1f), onNext = { vm.player.next() }, onPrevious = { vm.player.previous() },
                                        onLongPress = { vm.player.state.value.current?.let { vm.sheetTrack = it } }, onDoubleTap = { if (!vm.isFavorite(t)) vm.toggleFavorite(t) },
                                    ) { BreathingCover(t, st.isPlaying, Modifier.fillMaxSize()) }
                                }
                                PlayerTab.LYRICS -> LyricsView(t, st, vm, accent)
                                PlayerTab.QUEUE -> QueueView(st, vm, actions)
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Spacer(Modifier.height(12.dp))
                        TrackTitleBlock(t, vm, accent)
                        Spacer(Modifier.height(6.dp))
                        StreamQuality(st)
                        SeekBar(st, vm.player, accent)
                        Controls(st, vm.player, accent)
                    }
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TabIcon(Icons.Default.Lyrics, "Letra", tab == PlayerTab.LYRICS, accent) { tab = if (tab == PlayerTab.LYRICS) PlayerTab.NOW else PlayerTab.LYRICS }
                        TabIcon(Icons.AutoMirrored.Filled.QueueMusic, "Cola", tab == PlayerTab.QUEUE, accent) { tab = if (tab == PlayerTab.QUEUE) PlayerTab.NOW else PlayerTab.QUEUE }
                    }
                }
            }
        }
    }
}

/** Portada que se achica suave al pausar y hace crossfade al cambiar de tema. */
@Composable
fun BreathingCover(t: Track, playing: Boolean, modifier: Modifier) {
    val scale by animateFloatAsState(if (playing) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 220f), label = "cover")
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (playing) 40f else 8f; shape = RoundedCornerShape(28.dp); clip = true }) {
        SmoothCover(t.coverLarge ?: t.cover, Modifier.fillMaxSize(), RoundedCornerShape(28.dp))
    }
}

@Composable
private fun TabIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
    }
}
