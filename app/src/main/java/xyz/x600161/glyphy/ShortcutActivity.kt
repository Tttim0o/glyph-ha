package xyz.x600161.glyphy

import android.app.Activity
import android.os.Bundle

/** Runs the action of a launcher (long-press) shortcut and closes immediately. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val done = { finish() }
        when (intent.action) {
            "xyz.x600161.glyphy.PULSE" -> Glyph.pulse("white", 100, 500, done)
            "xyz.x600161.glyphy.SET" -> Glyph.set("white", 100, done)
            "xyz.x600161.glyphy.OFF" -> Glyph.off(done)
            else -> finish()
        }
    }
}
