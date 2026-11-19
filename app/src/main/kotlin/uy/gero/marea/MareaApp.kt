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

    private val idPrefs by lazy { getSharedPreferences("marea", MODE_PRIVATE) }

    /** El Client ID que puso el usuario en la app; si no, el que vino al compilar (puede estar vacío). */
    val clientId: String
        get() = idPrefs.getString("tidalClientId", null)?.trim()?.takeIf { it.isNotEmpty() } ?: BuildConfig.TIDAL_CLIENT_ID
    val hasClientId: Boolean get() = clientId.isNotBlank()

    /** Guarda un Client ID nuevo y reinicia la app (la sesión de TIDAL depende del ID, así que hay que empezar de cero). */
    fun changeClientIdAndRestart(activity: android.app.Activity, id: String) {
        val changed = id.trim() != clientId
        idPrefs.edit().putString("tidalClientId", id.trim()).commit()
        if (changed) runCatching { kotlinx.coroutines.runBlocking { tidalAuthOrNull?.auth?.logout() } }
        val i = packageManager.getLaunchIntentForPackage(packageName)!!
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    private var tidalAuthOrNull: TidalAuth? = null

    val tidalAuth by lazy {
        TidalAuth.getInstance(
            AuthConfig(
                clientId = clientId,
                credentialsKey = "uy.gero.marealink.auth",
                scopes = BuildConfig.TIDAL_SCOPES.split(" ").filter { it.isNotBlank() }.toSet(),
                enableCertificatePinning = false,
            ),
            this,
        ).also { tidalAuthOrNull = it }
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
