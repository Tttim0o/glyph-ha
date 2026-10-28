package eu.nakokot.glyphha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast API (usable from Home Assistant Companion `command_broadcast_intent`):
 *   action  eu.nakokot.glyphha.SET        extras: token, group (default "white"), value (0-100)
 *   action  eu.nakokot.glyphha.PULSE      extras: token, group, value (default 100), ms (default 500)
 *   action  eu.nakokot.glyphha.OFF        extras: token
 *   action  eu.nakokot.glyphha.PLAY_ANIM  extras: token, name (a saved animation's name)
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
            "eu.nakokot.glyphha.SET" -> Glyph.set(group, value, finish)
            "eu.nakokot.glyphha.PULSE" -> Glyph.pulse(group, value, ms, finish)
            "eu.nakokot.glyphha.OFF" -> Glyph.off(finish)
            "eu.nakokot.glyphha.PLAY_ANIM" -> {
                val anim = name?.let { n -> GlyphAnim.load(context).find { it.name == n } }
                if (anim != null) GlyphAnim.play(anim) { finish() } else finish()
            }
            else -> finish()
        }
    }
}
