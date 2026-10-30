package xyz.x600161.glyphy

import com.topjohnwu.superuser.Shell

object Glyph {
    const val DIR = "/sys/class/leds/aw210xx_led"
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

    /** Physical LED index order of the bottom/vertical line, left to right (from the kernel driver). */
    val vlineIndexOrder = listOf(13, 11, 9, 12, 10, 14, 15, 8)

    fun raw(percent: Int) = (percent.coerceIn(0, 100) * MAX / 100)

    fun set(group: String, percent: Int, done: () -> Unit = {}) {
        val node = groups[group] ?: return done()
        Shell.cmd("echo ${raw(percent)} > $DIR/$node").submit { done() }
    }

    fun setIndex(index: Int, percent: Int, done: () -> Unit = {}) {
        Shell.cmd("echo \"$index ${raw(percent)}\" > $DIR/single_led_br").submit { done() }
    }

    fun off(done: () -> Unit = {}) {
        Shell.cmd(groups.values.joinToString("; ") { "echo 0 > $DIR/$it" }).submit { done() }
    }

    fun pulse(group: String, percent: Int, ms: Int, done: () -> Unit = {}) {
        val node = groups[group] ?: return done()
        val secs = "%d.%03d".format(ms / 1000, ms % 1000)
        Shell.cmd("echo ${raw(percent)} > $DIR/$node; sleep $secs; echo 0 > $DIR/$node").submit { done() }
    }

    /** Toggle the main white glyphs fully on/off (shake-to-torch). */
    fun toggleTorch(done: () -> Unit = {}) {
        Shell.cmd(
            "cur=\$(cat $DIR/all_white_leds_br); " +
                "if [ \"\$cur\" -gt 0 ]; then echo 0 > $DIR/all_white_leds_br; " +
                "else echo $MAX > $DIR/all_white_leds_br; fi"
        ).submit { done() }
    }

    /** Light `count` of the 8 bottom-line segments (in physical order), the rest off. */
    fun showBar(count: Int, percent: Int = 100, done: () -> Unit = {}) {
        val n = count.coerceIn(0, vlineIndexOrder.size)
        val sb = StringBuilder()
        vlineIndexOrder.forEachIndexed { i, idx ->
            val v = if (i < n) raw(percent) else 0
            sb.append("echo \"$idx $v\" > $DIR/single_led_br; ")
        }
        Shell.cmd(sb.toString()).submit { done() }
    }

    /** Light `count` segments for `holdMs`, then turn off - used to show a count like notifications. */
    fun showCount(count: Int, holdMs: Int = 2500, percent: Int = 100, done: () -> Unit = {}) {
        val n = count.coerceIn(0, vlineIndexOrder.size)
        val sb = StringBuilder()
        vlineIndexOrder.forEachIndexed { i, idx ->
            val v = if (i < n) raw(percent) else 0
            sb.append("echo \"$idx $v\" > $DIR/single_led_br; ")
        }
        val secs = "%d.%03d".format(holdMs / 1000, holdMs % 1000)
        sb.append("sleep $secs; ")
        vlineIndexOrder.forEach { idx -> sb.append("echo \"$idx 0\" > $DIR/single_led_br; ") }
        Shell.cmd(sb.toString()).submit { done() }
    }

    /** How many of the 8 bottom-line segments represent `percent` battery (at least 1 above 0 %). */
    fun batterySegments(percent: Int): Int =
        ((percent.coerceIn(0, 100) / 100f) * vlineIndexOrder.size).let {
            if (it < 1f && percent > 0) 1 else kotlin.math.ceil(it).toInt()
        }

    /** Show battery percentage on the bottom line for `holdMs`, then turn off. */
    fun showBattery(percent: Int, holdMs: Int = 2500, done: () -> Unit = {}) {
        val lit = batterySegments(percent)
        val sb = StringBuilder()
        vlineIndexOrder.forEachIndexed { i, idx ->
            val v = if (i < lit) raw(100) else 0
            sb.append("echo \"$idx $v\" > $DIR/single_led_br; ")
        }
        val secs = "%d.%03d".format(holdMs / 1000, holdMs % 1000)
        sb.append("sleep $secs; ")
        vlineIndexOrder.forEach { idx -> sb.append("echo \"$idx 0\" > $DIR/single_led_br; ") }
        Shell.cmd(sb.toString()).submit { done() }
    }

    /** Fade a group in, hold, then fade it out over roughly `totalMs` - a one-shot ambient pulse. */
    fun ambientGlow(group: String, totalMs: Int = 5000, peakPercent: Int = 18, done: () -> Unit = {}) {
        val node = groups[group] ?: return done()
        val stepMs = 80
        val rampMs = (totalMs * 0.35).toInt()
        val holdMs = totalMs - rampMs * 2
        val steps = (rampMs / stepMs).coerceAtLeast(1)
        val sb = StringBuilder()
        val stepSecs = "%d.%03d".format(stepMs / 1000, stepMs % 1000)
        for (i in 1..steps) {
            val v = raw(peakPercent * i / steps)
            sb.append("echo $v > $DIR/$node; sleep $stepSecs; ")
        }
        val holdSecs = "%d.%03d".format(holdMs.coerceAtLeast(0) / 1000, holdMs.coerceAtLeast(0) % 1000)
        sb.append("sleep $holdSecs; ")
        for (i in (steps - 1) downTo 0) {
            val v = raw(peakPercent * i / steps)
            sb.append("echo $v > $DIR/$node; sleep $stepSecs; ")
        }
        sb.append("echo 0 > $DIR/$node; ")
        Shell.cmd(sb.toString()).submit { done() }
    }

    /** Blink a group `times`, used to signal a count (e.g. notifications). */
    fun blinkCount(group: String, times: Int, done: () -> Unit = {}) {
        val node = groups[group] ?: return done()
        val n = times.coerceIn(0, 12)
        val sb = StringBuilder()
        repeat(n) {
            sb.append("echo $MAX > $DIR/$node; sleep 0.22; echo 0 > $DIR/$node; sleep 0.16; ")
        }
        Shell.cmd(sb.toString()).submit { done() }
    }
}
