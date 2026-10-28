package eu.nakokot.glyphha

import com.topjohnwu.superuser.Shell

object Glyph {
    private const val DIR = "/sys/class/leds/aw210xx_led"
    const val MAX = 4095

    val groups = linkedMapOf(
        "white" to "all_white_leds_br",
        "dot" to "dot_led_br",
        "round" to "round_leds_br",
        "vline" to "vline_leds_br",
        "front" to "front_cam_led_br",
        "rear" to "rear_cam_led_br",
        "horse" to "horse_race_leds_br",
    )

    private fun raw(percent: Int) = (percent.coerceIn(0, 100) * MAX / 100)

    fun set(group: String, percent: Int, done: () -> Unit = {}) {
        val node = groups[group] ?: return done()
        Shell.cmd("echo ${raw(percent)} > $DIR/$node").submit { done() }
    }

    fun off(done: () -> Unit = {}) {
        Shell.cmd(groups.values.joinToString("; ") { "echo 0 > $DIR/$it" }).submit { done() }
    }

    fun pulse(group: String, percent: Int, ms: Int, done: () -> Unit = {}) {
        val node = groups[group] ?: return done()
        val secs = "%d.%03d".format(ms / 1000, ms % 1000)
        Shell.cmd("echo ${raw(percent)} > $DIR/$node; sleep $secs; echo 0 > $DIR/$node").submit { done() }
    }
}
