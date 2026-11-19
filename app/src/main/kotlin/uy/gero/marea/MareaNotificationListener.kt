package uy.gero.marea

import android.service.notification.NotificationListenerService

/**
 * No lee ni guarda notificaciones: Android exige un servicio así para que una app
 * pueda ver y controlar las sesiones de medios de otras apps (en este caso, TIDAL).
 */
class MareaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching { MareaApp.instance.player.connect() }
    }
}
