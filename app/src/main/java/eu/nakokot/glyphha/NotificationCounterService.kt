package eu.nakokot.glyphha

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Tracks notifications so gestures can show "how many unread" (currently active, undismissed)
 * and "how many arrived in the last N minutes" (a rolling post-time log). Requires the user to
 * grant notification access once (Settings > Apps > Special access > Notification access).
 */
class NotificationCounterService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Stats.setConnected(applicationContext, true)
        instance = this
        Stats.refreshActiveCount(this)
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Stats.record(applicationContext, sbn.postTime)
        Stats.refreshActiveCount(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Stats.refreshActiveCount(this)
    }

    companion object {
        @Volatile var instance: NotificationCounterService? = null
    }

    object Stats {
        private const val PREF = "notif_stats"
        private const val KEY = "timestamps"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_ACTIVE = "active_unread"

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

        /** Recomputes the count of currently active (undismissed), non-ongoing notifications. */
        fun refreshActiveCount(service: NotificationListenerService) {
            val count = try {
                service.activeNotifications
                    .filter { it.packageName != service.packageName }
                    .filter { (it.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) == 0 }
                    .filter { (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 }
                    .size
            } catch (e: Exception) { 0 }
            service.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_ACTIVE, count).apply()
        }

        /** The "unread" figure: currently active, user-clearable notifications. */
        fun countActive(c: Context): Int =
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_ACTIVE, 0)
    }
}
