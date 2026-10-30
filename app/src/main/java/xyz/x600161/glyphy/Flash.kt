package xyz.x600161.glyphy

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Camera flash LED as a light source. setTorchMode needs no CAMERA permission.
 * Strobe runs on a plain thread so it never blocks the sensor callback.
 */
object Flash {
    @Volatile var on = false
        private set

    @Volatile private var strobing = false

    private fun cameraManager(c: Context) = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun flashId(c: Context): String? {
        val cm = cameraManager(c)
        return cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    fun set(c: Context, v: Boolean) {
        val id = flashId(c) ?: return
        try {
            cameraManager(c).setTorchMode(id, v)
            on = v
        } catch (e: Exception) {
            // Camera busy or torch unavailable - leave state unchanged.
        }
    }

    fun toggle(c: Context) = set(c, !on)

    /** Alternating on/off durations in ms, starting with "on". Runs in the background. */
    fun strobe(c: Context, pattern: LongArray) {
        if (strobing) return
        strobing = true
        Thread {
            try {
                pattern.forEachIndexed { i, ms ->
                    set(c, i % 2 == 0)
                    Thread.sleep(ms)
                }
            } finally {
                set(c, false)
                strobing = false
            }
        }.start()
    }

    /** Named flash patterns, shared by gestures and the UI. */
    val BLINK_TWICE = longArrayOf(120, 120, 120, 500)
    val SOS_LIKE = longArrayOf(200, 150, 200, 150, 200, 600)
}

/** Routes a detected gesture to glyphs, flash, or both. */
object Effects {
    fun toggleForShake(c: Context) {
        when (Prefs.shakeEffect(c)) {
            "flash" -> Flash.toggle(c)
            "both" -> { Flash.toggle(c); Glyph.toggleTorch() }
            else -> Glyph.toggleTorch()
        }
    }

    fun runMotion(c: Context) {
        when (Prefs.motionEffect(c)) {
            "glyph" -> Glyph.toggleTorch()
            "both" -> { Flash.strobe(c, Flash.BLINK_TWICE); Glyph.toggleTorch() }
            else -> Flash.strobe(c, Flash.BLINK_TWICE)
        }
    }
}
