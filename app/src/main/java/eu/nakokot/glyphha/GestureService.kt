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
    private var lastMagnitude = 0f
    private var lastPeakAt = 0L
    private val shakeWindowMs = 1200L
    private val tapWindowMs = 700L
    private var shakePeaks = 0
    private var firstShakeAt = 0L
    private var tapPeaks = 0
    private var firstTapAt = 0L
    private var lastActionAt = 0L

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
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (now - lastActionAt < 900) { lastMagnitude = magnitude; return } // debounce after firing

        val isSharp = kotlin.math.abs(magnitude - lastMagnitude) > 14f
        val isJerk = kotlin.math.abs(magnitude) > 16f

        if (isJerk && now - lastPeakAt > 150) {
            lastPeakAt = now
            // Shake: sustained large jerks -> count toward shake gesture.
            if (now - firstShakeAt > shakeWindowMs) { shakePeaks = 0; firstShakeAt = now }
            shakePeaks++
            if (shakePeaks >= 2) {
                shakePeaks = 0; lastActionAt = now
                Glyph.toggleTorch()
            }
        } else if (isSharp && !isJerk && now - lastPeakAt > 120) {
            lastPeakAt = now
            // Tap: short sharp spike without a sustained jerk -> count toward tap gesture.
            if (now - firstTapAt > tapWindowMs) { tapPeaks = 0; firstTapAt = now }
            tapPeaks++
            maybeFireTap(tapPeaks, now)
        }
        lastMagnitude = magnitude
    }

    private fun maybeFireTap(count: Int, now: Long) {
        val charging = isCharging()
        when {
            charging && count == 2 -> { tapPeaks = 0; lastActionAt = now; showBattery() }
            count == 3 -> { tapPeaks = 0; lastActionAt = now; showNotifCount() }
            count == 4 -> { tapPeaks = 0; lastActionAt = now; showRecentNotifCount() }
        }
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
