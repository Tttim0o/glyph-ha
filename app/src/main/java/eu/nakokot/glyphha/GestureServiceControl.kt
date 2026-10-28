package eu.nakokot.glyphha

import android.content.Context
import android.content.Intent

object GestureServiceControl {
    fun start(c: Context) {
        Prefs.setGesturesEnabled(c, true)
        c.startForegroundService(Intent(c, GestureService::class.java))
    }

    fun stop(c: Context) {
        Prefs.setGesturesEnabled(c, false)
        c.stopService(Intent(c, GestureService::class.java))
    }
}
