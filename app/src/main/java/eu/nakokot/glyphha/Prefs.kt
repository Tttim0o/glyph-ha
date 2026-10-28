package eu.nakokot.glyphha

import android.content.Context
import java.security.SecureRandom

object Prefs {
    fun token(c: Context): String {
        val p = c.getSharedPreferences("glyph", Context.MODE_PRIVATE)
        return p.getString("token", null) ?: run {
            val chars = "abcdefghijkmnpqrstuvwxyz23456789"
            val r = SecureRandom()
            val t = (1..12).map { chars[r.nextInt(chars.length)] }.joinToString("")
            p.edit().putString("token", t).apply()
            t
        }
    }

    fun gesturesEnabled(c: Context): Boolean =
        c.getSharedPreferences("glyph", Context.MODE_PRIVATE).getBoolean("gestures", false)

    fun setGesturesEnabled(c: Context, v: Boolean) {
        c.getSharedPreferences("glyph", Context.MODE_PRIVATE).edit().putBoolean("gestures", v).apply()
    }

    /** While tuning: show a toast for every detected peak/action so it can be judged live. */
    fun debugToasts(c: Context): Boolean =
        c.getSharedPreferences("glyph", Context.MODE_PRIVATE).getBoolean("debug_toasts", true)

    fun setDebugToasts(c: Context, v: Boolean) {
        c.getSharedPreferences("glyph", Context.MODE_PRIVATE).edit().putBoolean("debug_toasts", v).apply()
    }
}
