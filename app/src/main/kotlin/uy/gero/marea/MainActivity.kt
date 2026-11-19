package uy.gero.marea

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import uy.gero.marea.ui.AppRoot
import uy.gero.marea.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!MareaApp.instance.hasClientId) {
            // APK sin Client ID de fábrica: primero hay que poner el de tu app de TIDAL
            setContent {
                val st by MareaApp.instance.settings.state.collectAsState()
                uy.gero.marea.ui.MareaTheme(st.accent, st.pureBlack) {
                    uy.gero.marea.ui.ClientIdSetupScreen { id -> MareaApp.instance.changeClientIdAndRestart(this, id) }
                }
            }
            return
        }
        handleRedirect(intent)
        setContent { AppRoot(vm, onLogin = ::startLogin) }
    }

    private var askedNotif = false

    override fun onResume() {
        if (!askedNotif && android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) { askedNotif = true; requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2) }
        super.onResume()
        if (!MareaApp.instance.hasClientId) return
        // al volver (por ejemplo, después de dar el permiso o de pasar por TIDAL) nos reconectamos
        MareaApp.instance.player.connect()
        // Puente de los botones de los auriculares (se apaga solo tras 15 min en pausa)
        uy.gero.marea.HeadsetService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (MareaApp.instance.hasClientId) handleRedirect(intent)
    }

    private fun startLogin() {
        vm.loginError = null
        runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, vm.loginUri())
        }.onFailure {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, vm.loginUri())) }
                .onFailure { e -> vm.loginError = "No pude abrir el navegador: ${e.message}" }
        }
    }

    private fun handleRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "tidalfold" && uri.host == "link") {
            vm.finishLogin(uri)
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
