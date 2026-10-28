package eu.nakokot.glyphha

import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Watches the Volume Down key at the raw input level (via root `getevent`), so a double-press
 * can be detected even with the screen off, without colliding with any Android key-combo
 * (Volume Down + Power is reserved system-wide for the screenshot shortcut, so that combo is
 * deliberately NOT used here).
 */
class ButtonWatcher(private val onDoublePress: () -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var process: Process? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            while (running) {
                try { watchLoop() } catch (e: Exception) { /* retry below */ }
                if (running) Thread.sleep(2000)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        process?.destroy()
        thread?.interrupt()
    }

    private fun resolveDevice(): String? {
        val out = Shell.cmd("getevent -pl 2>/dev/null").exec().out.joinToString("\n")
        var current: String? = null
        out.lineSequence().forEach { line ->
            val add = Regex("""add device.*: (/dev/input/event\d+)""").find(line)
            if (add != null) current = add.groupValues[1]
            if (current != null && line.contains("KEY_VOLUMEDOWN")) return current
        }
        return null
    }

    private fun watchLoop() {
        val device = resolveDevice() ?: return
        val proc = ProcessBuilder("su", "-c", "getevent -lt $device").redirectErrorStream(true).start()
        process = proc
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var lastPressAt = 0L
        var presses = 0
        reader.useLines { lines ->
            for (line in lines) {
                if (!running) break
                if (!line.contains("KEY_VOLUMEDOWN") || !line.contains("DOWN")) continue
                val now = System.currentTimeMillis()
                if (now - lastPressAt > 700) presses = 0
                presses++
                lastPressAt = now
                if (presses >= 2) {
                    presses = 0
                    onDoublePress()
                }
            }
        }
    }
}
