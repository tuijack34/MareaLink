package uy.gero.marea.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uy.gero.marea.MareaApp

@Composable
fun AppRoot(vm: MainViewModel, onLogin: () -> Unit) {
    val settings by MareaApp.instance.settings.state.collectAsState()
    MareaTheme(settings.accent, settings.pureBlack) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            if (!vm.loggedIn) LoginScreen(vm, onLogin) else LoggedIn(vm)
        }
    }
}

@Composable
private fun LoggedIn(vm: MainViewModel) {
    val settings by MareaApp.instance.settings.state.collectAsState()
    val st by vm.player.state.collectAsState()
    val actions = TrackActions(
        onPlayNext = { vm.player.playNext(it); vm.message = "Suena a continuación" },
        onAddToQueue = { val pos = vm.player.addToQueue(listOf(it)); vm.message = if (pos == 1) "Suena a continuación" else "Agregado a tu cola (#$pos)" },
        onRadio = { vm.player.startRadio(it) },
        onAlbum = { vm.openAlbum(it.albumId) },
        onArtist = { vm.openArtist(it.artists.firstOrNull()?.id) },
        onToggleFavorite = { vm.toggleFavorite(it) },
        isFavorite = { vm.isFavorite(it) },
        onAddToPlaylist = { vm.openAddToPlaylist(it) },
    )

    BackHandler(enabled = vm.fullPlayerOpen || vm.canGoBack || vm.tab != Tab.HOME || vm.diagnosticsOpen || vm.labOpen) { vm.back() }

    LaunchedEffect(st.error) { st.error?.let { vm.message = it; vm.player.consumeError() } }
    LaunchedEffect(vm.message) { if (vm.message != null) { delay(2800); vm.message = null } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val fullWidth = maxWidth
        val pcStatus by uy.gero.marea.PcLink.status.collectAsState()
        val pc = vm.onPc
        val hasPlayer = st.current != null || pc
        val showPane = wide && settings.showQueuePane && !(vm.paneMinimized && hasPlayer)
        val haze = remember { HazeState() }
        if (wide) {
            // Paneles separados por una línea negra (como Spotify), con esquinas redondeadas,
            // la barra lateral de vidrio esmerilado (estilo iPhone) sobre un fondo de ambiente
            // y todo empezando debajo de la hora/notificaciones/batería.
            // Como Spotify en el Fold: dos paneles (explorador y reproductor) sobre negro,
            // y la navegación como píldora flotante de vidrio abajo del explorador.
            val gap = 8.dp
            val panelShape = RoundedCornerShape(24.dp)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Row(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = gap, end = gap, bottom = gap, top = 2.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(panelShape).background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.fillMaxSize().hazeSource(haze)) {
                            CompositionLocalProvider(LocalInPanel provides true) {
                                Content(vm, actions, st.current?.id, settings.coverColors, settings.gridSize, PaddingValues(bottom = 110.dp))
                            }
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FloatingNav(vm, haze, Modifier.widthIn(max = 440.dp).weight(1f, fill = false))
                            if (!showPane && hasPlayer) {
                                Box(Modifier.weight(1f)) {
                                    // minimizado: tocar o deslizar hacia arriba vuelve a abrir el reproductor
                                    val expand = { if (settings.showQueuePane) vm.paneMinimized = false else vm.fullPlayerOpen = true }
                                    if (pc) PcMiniPlayer(vm, onExpand = expand) else MiniPlayer(st, vm, settings.coverColors, onExpand = expand)
                                }
                            }
                        }
                    }
                    val bookPosture = rememberFoldPosture()
                    // Doblado como libro: el reproductor ocupa exactamente la mitad derecha, del otro lado de la bisagra
                    val paneTarget = if (bookPosture.book) (fullWidth - bookPosture.hingeEnd - gap) else (fullWidth * 0.42f).coerceIn(330.dp, 480.dp)
                    // al doblarlo como libro, el panel se acomoda a la bisagra con una animación suave
                    val paneWidth by androidx.compose.animation.core.animateDpAsState(paneTarget, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 250f), label = "pane")
                    AnimatedVisibility(
                        showPane,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + slideOutVertically { it / 2 } + fadeOut(),
                    ) {
                        Row {
                            Spacer(Modifier.width(gap))
                            CompositionLocalProvider(LocalInPanel provides true) {
                                if (pc) PcPane(vm, Modifier.width(paneWidth).clip(panelShape), onMinimize = { vm.paneMinimized = true })
                                else NowPlayingPane(
                                    st, vm, actions, settings.coverColors, Modifier.width(paneWidth).clip(panelShape),
                                    onMinimize = if (st.current != null) ({ vm.paneMinimized = true }) else null,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    Content(vm, actions, st.current?.id, settings.coverColors, settings.gridSize, PaddingValues(bottom = if (hasPlayer) 190.dp else 120.dp))
                }
                // Vidrio detrás de la hora y las notificaciones: lo que scrollea por debajo se difumina
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).hazeEffect(haze, glassStyle(MaterialTheme.colorScheme.background, 0.55f)))
                // Barra flotante estilo One UI: mini reproductor + navegación en una "píldora" de vidrio
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    AnimatedVisibility(hasPlayer, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        if (pc) PcMiniPlayer(vm, onExpand = { vm.fullPlayerOpen = true }) else MiniPlayer(st, vm, settings.coverColors)
                    }
                    FloatingNav(vm, haze)
                }
            }
        }

        val posture = rememberFoldPosture()
        AnimatedVisibility(
            posture.tabletop && st.current != null && !pc,
            enter = fadeIn(tween(350)) + scaleIn(initialScale = 0.96f, animationSpec = tween(400)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.04f, animationSpec = tween(300)),
        ) {
            TabletopPlayer(st, vm, actions, settings.coverColors, posture)
        }

        AnimatedVisibility(
            vm.fullPlayerOpen && hasPlayer && !(posture.tabletop && !pc),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            if (pc) PcFullPlayer(vm, wide) else FullPlayer(st, vm, actions, settings.coverColors)
        }

        AnimatedVisibility(vm.labOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LabScreen(vm) }
        }

        AnimatedVisibility(vm.diagnosticsOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { DiagnosticsScreen(vm) }
        }

        LinkBanner(vm, st, Modifier.align(Alignment.TopCenter))
        vm.addToPlaylistFor?.let { t -> AddToPlaylistSheet(vm, t) }

        vm.message?.let { msg ->
            Snackbar(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 140.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text(msg) }
        }
    }
}

private fun tabIcon(t: Tab) = when (t) {
    Tab.HOME -> Icons.Default.Home
    Tab.LIBRARY -> Icons.Default.LibraryMusic
    Tab.SEARCH -> Icons.Default.Search
    Tab.SETTINGS -> Icons.Default.Settings
}

@Composable
private fun Content(vm: MainViewModel, actions: TrackActions, currentId: String?, coverColors: Boolean, gridSize: Int, padding: PaddingValues) {
    val screen = vm.currentScreen
    val tab = vm.tab
    AnimatedContent(
        tab to screen,
        transitionSpec = {
            val deeper = targetState.first == initialState.first && targetState.second != Screen.Root && initialState.second == Screen.Root ||
                (targetState.first == initialState.first && targetState.second !is Screen.Root && initialState.second !is Screen.Root)
            if (targetState.first != initialState.first) {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220))) togetherWith fadeOut(tween(150))
            } else if (targetState.second == Screen.Root) {
                (slideInHorizontally(tween(280)) { -it / 5 } + fadeIn(tween(280))) togetherWith (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 5 } + fadeOut(tween(200)))
            }.also { deeper }
        },
        label = "nav",
    ) { (t, s) ->
        Box(Modifier.fillMaxSize()) {
            when (s) {
                is Screen.AlbumS -> AlbumScreen(vm, s, actions, currentId, coverColors, padding)
                is Screen.PlaylistS -> PlaylistScreen(vm, s, actions, currentId, coverColors, padding)
                is Screen.ArtistS -> ArtistScreen(vm, s, actions, currentId, padding)
                Screen.LikedS -> LikedScreen(vm, actions, currentId, coverColors, padding)
                Screen.Root -> when (t) {
                    Tab.HOME -> HomeScreen(vm, actions, currentId, coverColors, padding)
                    Tab.LIBRARY -> LibraryScreen(vm, actions, currentId, gridSize, padding)
                    Tab.SEARCH -> SearchScreen(vm, actions, currentId, padding)
                    Tab.SETTINGS -> SettingsScreen(vm, padding)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(vm: MainViewModel, onLogin: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), MaterialTheme.colorScheme.background))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Marea", style = MaterialTheme.typography.displaySmall)
            Text(
                "Tu música de TIDAL, con una interfaz pensada para el Fold.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (vm.loggingIn) CircularProgressIndicator()
            else Button(onClick = onLogin, shape = RoundedCornerShape(50), modifier = Modifier.height(52.dp)) {
                Text("Iniciar sesión con TIDAL", modifier = Modifier.padding(horizontal = 12.dp))
            }
            vm.loginError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)).padding(12.dp))
            }
        }
    }
}


@Composable
private fun FloatingNav(vm: MainViewModel, haze: HazeState, modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .hazeEffect(haze, glassStyle(MaterialTheme.colorScheme.surfaceContainerHighest, 0.6f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val selected = vm.tab == t
            val bg by androidx.compose.animation.animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent, label = "navbg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "navfg",
            )
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(bg)
                    .clickable { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); vm.selectTab(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tabIcon(t), null, tint = fg)
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
            }
        }
    }
}


/** Aviso arriba cuando falta el permiso o TIDAL no está abierta. */
@Composable
private fun LinkBanner(vm: MainViewModel, st: uy.gero.marea.PlayerUiState, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val show = st.needsAccess || (!st.tidalConnected && st.current != null)
    AnimatedVisibility(show, modifier = modifier, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).fillMaxWidth()
                .clip(RoundedCornerShape(GroupRadius)).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
        ) {
            if (st.needsAccess) {
                Text("Falta un permiso", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Marea Link necesita \"acceso a notificaciones\" para ver y controlar TIDAL. No lee tus notificaciones. " +
                        "Si Android dice \"configuración restringida\", primero andá a Info de la app → ⋮ → Permitir configuración restringida.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { openListenerSettings(ctx) }) { Text("Dar acceso") }
                    androidx.compose.material3.FilledTonalButton(onClick = { openAppInfo(ctx) }) { Text("Info de la app") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIDAL no está abierta", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.player.openTidalApp() }) { Text("Abrir TIDAL") }
                }
            }
        }
    }
}


/** Ítem de la barra lateral: píldora con el ícono y la etiqueta, estilo One UI. */
@Composable
private fun RailItem(vm: MainViewModel, t: Tab) {
    val selected = vm.tab == t
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bg by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent, label = "rbg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), label = "rfg")
    Column(
        Modifier.padding(vertical = 4.dp).width(68.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .clickable { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); vm.selectTab(t) }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tabIcon(t), null, tint = fg)
        Spacer(Modifier.height(4.dp))
        Text(t.label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Vidrio esmerilado: desenfoque + un velo del color dado. */
fun glassStyle(tint: Color, alpha: Float) = HazeStyle(
    backgroundColor = tint,
    tint = HazeTint(tint.copy(alpha = alpha)),
    blurRadius = 28.dp,
    noiseFactor = 0.06f,
)
