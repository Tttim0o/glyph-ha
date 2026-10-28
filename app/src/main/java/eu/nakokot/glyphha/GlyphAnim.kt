package eu.nakokot.glyphha

import android.content.Context
import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A single step of a custom glyph animation. */
data class AnimStep(
    val target: String,      // one of Glyph.groups keys, "single:<1-16>", or "off"
    val percent: Int,        // 0-100
    val ms: Int,             // hold duration for this step
)

data class GlyphAnimation(val name: String, val steps: List<AnimStep>, val loops: Int = 1)

/** Stores and plays user-authored glyph animations (the "advanced" composer). */
object GlyphAnim {
    private fun file(c: Context) = File(c.filesDir, "animations.json")

    fun load(c: Context): List<GlyphAnimation> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val steps = o.getJSONArray("steps")
            val stepList = (0 until steps.length()).map { j ->
                val s = steps.getJSONObject(j)
                AnimStep(s.getString("target"), s.getInt("percent"), s.getInt("ms"))
            }
            GlyphAnimation(o.getString("name"), stepList, o.optInt("loops", 1))
        }
    }

    fun save(c: Context, anim: GlyphAnimation) {
        val all = load(c).filterNot { it.name == anim.name } + anim
        write(c, all)
    }

    fun delete(c: Context, name: String) {
        write(c, load(c).filterNot { it.name == name })
    }

    private fun write(c: Context, list: List<GlyphAnimation>) {
        val arr = JSONArray()
        list.forEach { a ->
            val o = JSONObject()
            o.put("name", a.name)
            o.put("loops", a.loops)
            val steps = JSONArray()
            a.steps.forEach { s ->
                val so = JSONObject()
                so.put("target", s.target)
                so.put("percent", s.percent)
                so.put("ms", s.ms)
                steps.put(so)
            }
            o.put("steps", steps)
            arr.put(o)
        }
        file(c).writeText(arr.toString())
    }

    private fun stepCmd(s: AnimStep): String {
        val secs = "%d.%03d".format(s.ms / 1000, s.ms % 1000)
        val write = when {
            s.target == "off" -> Glyph.groups.values.joinToString("; ") { "echo 0 > ${Glyph.DIR}/$it" }
            s.target.startsWith("single:") -> {
                val idx = s.target.removePrefix("single:").toIntOrNull() ?: 1
                "echo \"$idx ${Glyph.raw(s.percent)}\" > ${Glyph.DIR}/single_led_br"
            }
            else -> {
                val node = Glyph.groups[s.target] ?: return ""
                "echo ${Glyph.raw(s.percent)} > ${Glyph.DIR}/$node"
            }
        }
        return "$write; sleep $secs; "
    }

    /** Runs the animation as a background shell job and records its PID so it can be stopped. */
    fun play(anim: GlyphAnimation, done: () -> Unit = {}) {
        val body = (1..anim.loops.coerceIn(1, 50)).joinToString("") { anim.steps.joinToString("") { s -> stepCmd(s) } }
        val script = "( echo \$\$ > /data/local/tmp/glyphha_anim.pid; $body ) &\n" +
            "echo started"
        Shell.cmd(script).submit { done() }
    }

    fun stop(done: () -> Unit = {}) {
        Shell.cmd(
            "[ -f /data/local/tmp/glyphha_anim.pid ] && kill \$(cat /data/local/tmp/glyphha_anim.pid) 2>/dev/null; " +
                Glyph.groups.values.joinToString("; ") { "echo 0 > ${Glyph.DIR}/$it" }
        ).submit { done() }
    }

    /** All assignable targets for the composer UI. */
    fun targets(): List<String> = listOf("off") + Glyph.groups.keys + (1..16).map { "single:$it" }
}
