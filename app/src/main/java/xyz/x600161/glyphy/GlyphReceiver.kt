package xyz.x600161.glyphy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.topjohnwu.superuser.Shell

/**
 * Broadcast API for Tasker ("Send Intent", target: Broadcast Receiver) and Home Assistant
 * Companion (`command_broadcast_intent`). Every action needs the `token` extra from Settings.
 * Extras may be strings or ints; HA always sends strings.
 *
 *   xyz.x600161.glyphy.SET         group (default "white"), value 0-100
 *   xyz.x600161.glyphy.PULSE       group, value (default 100), ms (default 500)
 *   xyz.x600161.glyphy.OFF         all glyphs off
 *   xyz.x600161.glyphy.PLAY_ANIM   name (a saved animation's name)
 *   xyz.x600161.glyphy.TORCH       state on|off|toggle (default toggle) - white glyphs as a torch
 *   xyz.x600161.glyphy.FLASH       state on|off|toggle (default toggle) - camera flash
 *   xyz.x600161.glyphy.STROBE      pattern sos|blink (default blink) - camera flash
 *   xyz.x600161.glyphy.BAR         count 0-8, value 0-100 (default 100) - bottom line, stays on
 *   xyz.x600161.glyphy.BATTERY     battery level on the bottom line for a moment
 *   xyz.x600161.glyphy.COUNT       unread-notification count on the bottom line for a moment
 *   xyz.x600161.glyphy.GESTURES    state on|off - the gesture service
 *   xyz.x600161.glyphy.CONFIG      key shake_effect|motion_effect (value glyph|flash|both),
 *                                  key charging_bar|debug_toasts (value on|off)
 */
class GlyphReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (str(intent, "token") != Prefs.token(context)) return
        val group = str(intent, "group") ?: "white"
        val value = int(intent, "value", 100)
        val ms = int(intent, "ms", 500)
        val state = str(intent, "state") ?: "toggle"
        val pending = goAsync()
        val finish = { pending.finish() }
        when (intent.action?.removePrefix("xyz.x600161.glyphy.")) {
            "SET" -> Glyph.set(group, value, finish)
            "PULSE" -> Glyph.pulse(group, value, ms, finish)
            "OFF" -> Glyph.off(finish)
            "PLAY_ANIM" -> {
                val anim = str(intent, "name")?.let { n -> GlyphAnim.load(context).find { it.name == n } }
                if (anim != null) GlyphAnim.play(anim) { finish() } else finish()
            }
            "TORCH" -> when (state) {
                "on" -> Glyph.set("white", 100, finish)
                "off" -> Glyph.set("white", 0, finish)
                else -> Glyph.toggleTorch(finish)
            }
            "FLASH" -> {
                when (state) {
                    "on" -> Flash.set(context, true)
                    "off" -> Flash.set(context, false)
                    else -> Flash.toggle(context)
                }
                finish()
            }
            "STROBE" -> {
                Flash.strobe(context, if (str(intent, "pattern") == "sos") Flash.SOS_LIKE else Flash.BLINK_TWICE)
                finish()
            }
            "BAR" -> Glyph.showBar(int(intent, "count", 0), value, finish)
            "BATTERY" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                Glyph.showBattery(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), done = finish)
            }
            "COUNT" -> Glyph.showCount(
                NotificationCounterService.Stats.countActive(context).coerceAtMost(8), done = finish
            )
            "GESTURES" -> {
                if (state == "off") {
                    GestureServiceControl.stop(context)
                    finish()
                } else {
                    // A background receiver may not start a foreground service itself; root's am may.
                    Prefs.setGesturesEnabled(context, true)
                    Shell.cmd(
                        "pm grant ${context.packageName} android.permission.RECORD_AUDIO",
                        "am start-foreground-service -n ${context.packageName}/.GestureService"
                    ).submit { finish() }
                }
            }
            "CONFIG" -> {
                val v = str(intent, "value")
                when (str(intent, "key")) {
                    "shake_effect" -> v?.takeIf { it in EFFECTS }?.let { Prefs.setShakeEffect(context, it) }
                    "motion_effect" -> v?.takeIf { it in EFFECTS }?.let { Prefs.setMotionEffect(context, it) }
                    "debug_toasts" -> Prefs.setDebugToasts(context, v == "on")
                    "charging_bar" -> {
                        Prefs.setChargingBar(context, v == "on")
                        context.sendBroadcast(
                            Intent(GestureService.ACTION_REDRAW_CHARGING_BAR).setPackage(context.packageName)
                        )
                    }
                }
                finish()
            }
            else -> finish()
        }
    }

    private fun str(i: Intent, key: String): String? =
        i.getStringExtra(key) ?: i.extras?.get(key)?.toString()

    private fun int(i: Intent, key: String, default: Int): Int = str(i, key)?.toIntOrNull() ?: default

    private companion object {
        val EFFECTS = setOf("glyph", "flash", "both")
    }
}
