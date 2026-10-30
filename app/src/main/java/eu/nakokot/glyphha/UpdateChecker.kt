package eu.nakokot.glyphha

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manual, user-initiated update check against GitHub Releases. Nothing is installed silently:
 * the app only reports the newer version and opens the APK download page, and Android's
 * own installer asks for confirmation.
 *
 * Release tags must be "v<versionCode>" (for example v3), so the comparison is an integer.
 */
object UpdateChecker {
    const val REPO = "Tttim0o/glyph-ha"

    data class Result(val latestCode: Long, val currentCode: Long, val apkUrl: String?, val pageUrl: String?) {
        val updateAvailable get() = latestCode > currentCode && apkUrl != null
    }

    fun currentVersionCode(c: Context): Long {
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        return info.longVersionCode
    }

    /** Blocking network call - run it off the main thread. Throws on failure. */
    fun check(c: Context): Result {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name").removePrefix("v")
            val latest = tag.toLongOrNull() ?: error("unexpected tag $tag")
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
                }
            }
            return Result(latest, currentVersionCode(c), apk, json.optString("html_url", null))
        } finally {
            conn.disconnect()
        }
    }
}
