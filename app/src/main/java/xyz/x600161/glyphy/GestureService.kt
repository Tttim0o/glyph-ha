package xyz.x600161.glyphy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlin.math.sqrt

/**
 * Foreground service: watches the accelerometer for a few gestures, plus two face-down
 * ambient effects and a raw-hardware-button shortcut.
 *
 *  - Shake (two sharp jerks in ~1s): toggles the white glyphs as a torch.
 *  - Charger plugged in: shows battery % on the bottom line.
 *  - Tap on the frame/back while charging (two sharp isolated spikes): shows battery % on the
 *    bottom line. Does nothing while unplugged.
 *  - Face-down + screen off + unread notifications: the small "front" glyph fades in and back
 *    out once (~5s), re-triggering only when the unread count changes - not a steady light.
 *  - Face-down + screen off + music playing: the bottom line becomes an audio-reactive
 *    equalizer / progress bar (see MusicVisualizer).
 *  - Face-down + screen off + Volume Down pressed twice quickly: blinks out the unread
 *    notification count. Volume Down alone is used (not + Power) because that combo is the
 *    system-wide screenshot shortcut.
 *
 * Gesture detection from a bare accelerometer is inherently approximate - thresholds are
 * tuned loosely and may need adjusting; enable the debug toasts in Settings to see live values.
 */
class GestureService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var buttonWatcher: ButtonWatcher? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Rolling history of recent |magnitude| samples, used to require a calm baseline
    // before a spike counts as a deliberate tap.
    private val history = ArrayDeque<Pair<Long, Float>>()
    private val historyWindowMs = 400L
    private val calmWindowMs = 150L

    private val calmThreshold = 2.5f
    private val tapThreshold = 28f
    private val settleThreshold = 6f        // candidate must decay back below this to count as a real knock
    private val settleWindowMs = 150L       // ...within this long; sustained vibration never settles in time
    private val shakeThreshold = 20f

    private var lastPeakAt = 0L
    private val shakeWindowMs = 1200L
    private val tapWindowMs = 650L
    private var shakePeaks = 0
    private var firstShakeAt = 0L
    private var tapPeaks = 0
    private var firstTapAt = 0L
    private var lastActionAt = 0L
    private val actionCooldownMs = 1800L

    // A tap candidate waits here until it either settles (real knock) or times out (noise).
    private var pendingTapAt = 0L
    private var pendingTapMagnitude = 0f

    @Volatile private var isFaceDown = false
    @Volatile private var screenOn = true
    private var lastGlowedUnreadCount = -1
    private val visualizer = MusicVisualizer()
    private var visualizing = false

    // Signed-magnitude history for custom motion matching (kept WINDOW_MS long).
    private val recent = ArrayDeque<Pair<Long, Float>>()
    private var lastMotionAt = 0L
    private var motionTick = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            updateAmbient()
        }
    }

    // Charger plugged in: show the battery level on the bottom line, like the charging tap does.
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showBattery()
        }
    }

    private val ambientTick = object : Runnable {
        override fun run() {
            updateAmbient()
            mainHandler.postDelayed(this, 200)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        startForeground(1, buildNotification())
        // Accelerometer events stop being delivered when the CPU suspends (screen off, charging),
        // which is exactly when the charging-tap gesture needs them - hold a partial wake lock.
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glyphha:gestures")
            .also { it.setReferenceCounted(false); it.acquire() }
        sensorManager.registerListener(
            this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME
        )
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        })
        registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        mainHandler.post(ambientTick)
        buttonWatcher = ButtonWatcher {
            if (isFaceDown && !screenOn) {
                toast("VolDown x2 -> notif count")
                showActiveCount()
            }
        }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(powerReceiver) } catch (e: Exception) {}
        mainHandler.removeCallbacks(ambientTick)
        buttonWatcher?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        isFaceDown = z < -7f && kotlin.math.abs(x) < 4f && kotlin.math.abs(y) < 4f
        val signed = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        val magnitude = kotlin.math.abs(signed)
        val now = System.currentTimeMillis()

        history.addLast(now to magnitude)
        while (history.isNotEmpty() && now - history.first().first > historyWindowMs) history.removeFirst()
        if (magnitude > 12f) Log.d("Glyphy", "sample mag=${"%.1f".format(magnitude)}")

        // Custom motion: record a template, or match the last window against it.
        recent.addLast(now to signed)
        while (recent.isNotEmpty() && now - recent.first().first > GestureLearn.WINDOW_MS) recent.removeFirst()
        if (GestureLearn.feed(this, now, signed)) {
            toast("motion recorded")
        } else if (!GestureLearn.recording && now - lastMotionAt > 3000 && ++motionTick % 4 == 0) {
            val score = GestureLearn.score(this, recent.toList(), now)
            if (score != null && score > GestureLearn.MATCH_THRESHOLD) {
                lastMotionAt = now
                toast("motion match ${"%.2f".format(score)}")
                Effects.runMotion(this)
            }
        }

        // A pending tap candidate: confirm it only once the signal has settled back down -
        // genuine knocks ring out fast; charger/motor vibration stays elevated and gets dropped.
        if (pendingTapAt != 0L) {
            if (magnitude < settleThreshold) {
                pendingTapAt = 0L
                registerTap(now, pendingTapMagnitude)
            } else if (now - pendingTapAt > settleWindowMs) {
                toast("tap discarded (sustained vibration, mag=${"%.1f".format(magnitude)})")
                pendingTapAt = 0L
            }
            return
        }

        if (now - lastActionAt < actionCooldownMs) return

        val calmBefore = history.dropLast(1).filter { now - it.first <= calmWindowMs }.all { it.second < calmThreshold }
        val sustainedSwing = history.count { it.second > shakeThreshold } >= 3

        if (now - lastPeakAt < 120) return

        if (sustainedSwing) {
            lastPeakAt = now
            if (now - firstShakeAt > shakeWindowMs) { shakePeaks = 0; firstShakeAt = now }
            shakePeaks++
            toast("shake peak $shakePeaks/2 (mag=${"%.1f".format(magnitude)})")
            if (shakePeaks >= 2) {
                shakePeaks = 0; lastActionAt = now
                toast("shake -> torch toggle")
                Effects.toggleForShake(this)
            }
        } else if (magnitude > tapThreshold) {
            toast("peak mag=${"%.1f".format(magnitude)} calm=$calmBefore charging=${isCharging()}")
        }
        if (calmBefore && magnitude > tapThreshold) {
            lastPeakAt = now
            pendingTapAt = now
            pendingTapMagnitude = magnitude
        }
    }

    private fun registerTap(now: Long, magnitude: Float) {
        if (now - firstTapAt > tapWindowMs) { tapPeaks = 0; firstTapAt = now }
        tapPeaks++
        toast("tap $tapPeaks confirmed (mag=${"%.1f".format(magnitude)}, charging=${isCharging()})")
        if (isCharging() && tapPeaks == 2) {
            tapPeaks = 0; lastActionAt = now
            toast("2x tap -> battery"); showBattery()
        }
    }

    /** Runs every 200ms: applies the two face-down ambient effects. Cheap no-ops when inactive. */
    private fun updateAmbient() {
        val active = isFaceDown && !screenOn

        val unread = if (active) NotificationCounterService.Stats.countActive(this) else 0
        if (!active || unread == 0) {
            lastGlowedUnreadCount = -1
        } else if (unread != lastGlowedUnreadCount) {
            lastGlowedUnreadCount = unread
            Glyph.ambientGlow("front") // one fade in/out cycle (~5s), not a continuous light
        }

        val musicOn = active && (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        if (musicOn && !visualizing) {
            visualizing = true
            visualizer.start()
        } else if (!musicOn && visualizing) {
            visualizing = false
            visualizer.stop()
        }
    }

    private fun showActiveCount() {
        val n = NotificationCounterService.Stats.countActive(this)
        Glyph.showCount(n.coerceAtMost(8))
    }

    private fun toast(msg: String) {
        // Always in logcat (tag "Glyphy") so gesture tuning can be read without enabling toasts.
        Log.d("Glyphy", msg)
        if (!Prefs.debugToasts(this)) return
        mainHandler.post { Toast.makeText(this, "Glyph: $msg", Toast.LENGTH_SHORT).show() }
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
