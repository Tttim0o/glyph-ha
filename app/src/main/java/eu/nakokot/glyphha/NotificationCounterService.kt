package eu.nakokot.glyphha

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Tracks notification post times so gestures can report "how many notifications"
 * and "how many in the last 5 minutes". Requires the user to grant notification
 * access once (Settings > Apps > Special access > Notification access).
 */
class NotificationCounterService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Stats.record(applicationContext, sbn.postTime)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Stats.setConnected(applicationContext, true)
    }

    object Stats {
        private const val PREF = "notif_stats"
        private const val KEY = "timestamps"
        private const val KEY_CONNECTED = "connected"

        fun setConnected(c: Context, v: Boolean) {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONNECTED, v).apply()
        }

        fun isConnected(c: Context): Boolean =
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_CONNECTED, false)

        fun record(c: Context, whenMs: Long) {
            val p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val cutoff = System.currentTimeMillis() - 24 * 3600_000L
            val kept = (p.getStringSet(KEY, emptySet()) ?: emptySet())
                .mapNotNull { it.toLongOrNull() }
                .filter { it > cutoff }
                .toMutableList()
            kept.add(whenMs)
            p.edit().putStringSet(KEY, kept.map { it.toString() }.toSet()).apply()
        }

        /** Count of notifications posted in the last [minutes] minutes. */
        fun countLast(c: Context, minutes: Int): Int {
            val p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val cutoff = System.currentTimeMillis() - minutes * 60_000L
            return (p.getStringSet(KEY, emptySet()) ?: emptySet())
                .mapNotNull { it.toLongOrNull() }
                .count { it > cutoff }
        }

        /** Total tracked in the last 24h, used as the "how many notifications" figure. */
        fun countTotal(c: Context): Int =
            (c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()).size
    }
}
