package eu.nakokot.glyphha

import android.app.Activity
import android.os.Bundle

/** Runs the action of a launcher (long-press) shortcut and closes immediately. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val done = { finish() }
        when (intent.action) {
            "eu.nakokot.glyphha.PULSE" -> Glyph.pulse("white", 100, 500, done)
            "eu.nakokot.glyphha.SET" -> Glyph.set("white", 100, done)
            "eu.nakokot.glyphha.OFF" -> Glyph.off(done)
            else -> finish()
        }
    }
}
