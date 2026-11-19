package uy.gero.marea

import android.app.Application
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MareaApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settings by lazy { SettingsStore(this) }

    val tidalAuth by lazy {
        TidalAuth.getInstance(
            AuthConfig(
                clientId = BuildConfig.TIDAL_CLIENT_ID,
                credentialsKey = "uy.gero.marealink.auth",
                scopes = BuildConfig.TIDAL_SCOPES.split(" ").filter { it.isNotBlank() }.toSet(),
                enableCertificatePinning = false,
            ),
            this,
        )
    }

    val api by lazy { TidalApi(tidalAuth.credentialsProvider) }

    /** Se crea recién cuando hay sesión iniciada. */
    val player by lazy { PlayerController(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MareaApp
            private set
    }
}
