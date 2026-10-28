package eu.nakokot.glyphha

import android.content.Context
import android.content.Intent
import com.topjohnwu.superuser.Shell

object GestureServiceControl {
    fun start(c: Context) {
        Prefs.setGesturesEnabled(c, true)
        // Visualizer needs RECORD_AUDIO; grant it through root so no runtime dialog appears.
        Shell.cmd("pm grant ${c.packageName} android.permission.RECORD_AUDIO").submit()
        c.startForegroundService(Intent(c, GestureService::class.java))
    }

    fun stop(c: Context) {
        Prefs.setGesturesEnabled(c, false)
        c.stopService(Intent(c, GestureService::class.java))
    }
}
