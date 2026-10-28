package eu.nakokot.glyphha

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.IBinder
import android.widget.Toast
import kotlin.math.sqrt

/**
 * Foreground service: watches the accelerometer for two gestures.
 *  - Shake (two sharp jerks in ~1s): toggles the white glyphs as a torch, like Moto's chop-chop.
 *  - Taps on the frame/back (sharp short spikes, no travel): while charging, 2 taps show the
 *    battery percentage on the bottom line; anytime, 3 taps show the notification count and
 *    4 taps show how many arrived in the last 5 minutes.
 *
 * Gesture detection from a bare accelerometer is inherently approximate (no dedicated tap IC
 * like Pixel has) - thresholds are tuned loosely and may need adjusting per taste.
 */
class GestureService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    // Rolling history of recent |magnitude| samples, used to require a calm baseline
    // before a spike counts as a deliberate tap (ambient charger/cable vibration stays
    // low-amplitude and continuous, so it never produces a clean calm->spike->calm shape).
    private val history = ArrayDeque<Pair<Long, Float>>()
    private val historyWindowMs = 400L     // retention, used for the shake-swing check
    private val calmWindowMs = 150L        // only this much immediate past must be calm for a tap

    private val calmThreshold = 2.5f       // max |mag| to call the recent past "calm"
    private val tapThreshold = 24f         // single sharp spike -> tap candidate
    private val shakeThreshold = 20f       // sustained large swing -> shake candidate

    private var lastPeakAt = 0L
    private val shakeWindowMs = 1200L
    private val tapWindowMs = 650L
    private var shakePeaks = 0
    private var firstShakeAt = 0L
    private var tapPeaks = 0
    private var firstTapAt = 0L
    private var lastActionAt = 0L
    private val actionCooldownMs = 1800L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        startForeground(1, buildNotification())
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val magnitude = kotlin.math.abs(sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH)
        val now = System.currentTimeMillis()

        history.addLast(now to magnitude)
        while (history.isNotEmpty() && now - history.first().first > historyWindowMs) history.removeFirst()

        if (now - lastActionAt < actionCooldownMs) return // quiet period right after any gesture fired

        val calmBefore = history.dropLast(1).filter { now - it.first <= calmWindowMs }.all { it.second < calmThreshold }
        val sustainedSwing = history.count { it.second > shakeThreshold } >= 3

        if (now - lastPeakAt < 120) return // ignore samples within the same physical event

        if (sustainedSwing) {
            lastPeakAt = now
            if (now - firstShakeAt > shakeWindowMs) { shakePeaks = 0; firstShakeAt = now }
            shakePeaks++
            toast("shake peak $shakePeaks/2 (mag=${"%.1f".format(magnitude)})")
            if (shakePeaks >= 2) {
                shakePeaks = 0; lastActionAt = now
                toast("shake -> torch toggle")
                Glyph.toggleTorch()
            }
        } else if (calmBefore && magnitude > tapThreshold) {
            lastPeakAt = now
            if (now - firstTapAt > tapWindowMs) { tapPeaks = 0; firstTapAt = now }
            tapPeaks++
            toast("tap $tapPeaks (mag=${"%.1f".format(magnitude)}, charging=${isCharging()})")
            maybeFireTap(tapPeaks, now)
        }
    }

    private fun maybeFireTap(count: Int, now: Long) {
        val charging = isCharging()
        when {
            charging && count == 2 -> { tapPeaks = 0; lastActionAt = now; toast("2x tap -> battery"); showBattery() }
            count == 3 -> { tapPeaks = 0; lastActionAt = now; toast("3x tap -> notif count"); showNotifCount() }
            count == 4 -> { tapPeaks = 0; lastActionAt = now; toast("4x tap -> last 5 min"); showRecentNotifCount() }
        }
    }

    private fun toast(msg: String) {
        if (!Prefs.debugToasts(this)) return
        Toast.makeText(this, "Glyph: $msg", Toast.LENGTH_SHORT).show()
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun batteryPercent(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun showBattery() = Glyph.showBattery(batteryPercent())

    private fun showNotifCount() {
        val n = NotificationCounterService.Stats.countTotal(this)
        Glyph.blinkCount("vline", n.coerceAtMost(8))
    }

    private fun showRecentNotifCount() {
        val n = NotificationCounterService.Stats.countLast(this, 5)
        Glyph.blinkCount("round", n.coerceAtMost(8))
    }

    private fun buildNotification(): Notification {
        val channelId = "glyph_gestures"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            val ch = NotificationChannel(channelId, "Glyph gestures", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("Glyph gestures active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()
    }

    companion object {
        fun isEnabled(c: Context) = Prefs.gesturesEnabled(c)
    }
}
