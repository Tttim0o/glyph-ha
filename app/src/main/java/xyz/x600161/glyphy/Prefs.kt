package xyz.x600161.glyphy

import android.content.Context
import java.security.SecureRandom

object Prefs {
    private fun prefs(c: Context) = c.getSharedPreferences("glyph", Context.MODE_PRIVATE)

    fun token(c: Context): String {
        val p = prefs(c)
        return p.getString("token", null) ?: run {
            val chars = "abcdefghijkmnpqrstuvwxyz23456789"
            val r = SecureRandom()
            val t = (1..12).map { chars[r.nextInt(chars.length)] }.joinToString("")
            p.edit().putString("token", t).apply()
            t
        }
    }

    fun gesturesEnabled(c: Context): Boolean = prefs(c).getBoolean("gestures", false)

    fun setGesturesEnabled(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("gestures", v).apply()
    }

    /** Gesture debug toasts. Off by default; turn on in Settings while tuning. */
    fun debugToasts(c: Context): Boolean = prefs(c).getBoolean("debug_toasts", false)

    fun setDebugToasts(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("debug_toasts", v).apply()
    }

    /** Dim battery bar on the bottom line while charging. On by default. */
    fun chargingBar(c: Context): Boolean = prefs(c).getBoolean("charging_bar", true)

    fun setChargingBar(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("charging_bar", v).apply()
    }

    /** What a shake does: "glyph", "flash" or "both". */
    fun shakeEffect(c: Context): String = prefs(c).getString("shake_effect", "glyph") ?: "glyph"

    fun setShakeEffect(c: Context, v: String) {
        prefs(c).edit().putString("shake_effect", v).apply()
    }

    /** What a recorded custom motion does: "glyph", "flash" or "both". */
    fun motionEffect(c: Context): String = prefs(c).getString("motion_effect", "flash") ?: "flash"

    fun setMotionEffect(c: Context, v: String) {
        prefs(c).edit().putString("motion_effect", v).apply()
    }

    /** Normalised template (zero mean, unit variance) of the recorded motion, or null. */
    fun motionTemplate(c: Context): FloatArray? =
        prefs(c).getString("motion_template", null)
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
            ?.takeIf { it.isNotEmpty() }

    fun setMotionTemplate(c: Context, v: FloatArray?) {
        prefs(c).edit().putString("motion_template", v?.joinToString(",")).apply()
    }
}
