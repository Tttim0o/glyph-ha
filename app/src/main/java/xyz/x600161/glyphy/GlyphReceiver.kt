package xyz.x600161.glyphy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast API (usable from Home Assistant Companion `command_broadcast_intent`):
 *   action  xyz.x600161.glyphy.SET        extras: token, group (default "white"), value (0-100)
 *   action  xyz.x600161.glyphy.PULSE      extras: token, group, value (default 100), ms (default 500)
 *   action  xyz.x600161.glyphy.OFF        extras: token
 *   action  xyz.x600161.glyphy.PLAY_ANIM  extras: token, name (a saved animation's name)
 */
class GlyphReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("token") != Prefs.token(context)) return
        val group = intent.getStringExtra("group") ?: "white"
        val value = intent.getStringExtra("value")?.toIntOrNull() ?: intent.getIntExtra("value", 100)
        val ms = intent.getStringExtra("ms")?.toIntOrNull() ?: intent.getIntExtra("ms", 500)
        val name = intent.getStringExtra("name")
        val pending = goAsync()
        val finish = { pending.finish() }
        when (intent.action) {
            "xyz.x600161.glyphy.SET" -> Glyph.set(group, value, finish)
            "xyz.x600161.glyphy.PULSE" -> Glyph.pulse(group, value, ms, finish)
            "xyz.x600161.glyphy.OFF" -> Glyph.off(finish)
            "xyz.x600161.glyphy.PLAY_ANIM" -> {
                val anim = name?.let { n -> GlyphAnim.load(context).find { it.name == n } }
                if (anim != null) GlyphAnim.play(anim) { finish() } else finish()
            }
            else -> finish()
        }
    }
}
