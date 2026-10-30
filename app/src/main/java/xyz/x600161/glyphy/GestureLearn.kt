package xyz.x600161.glyphy

import android.content.Context
import kotlin.math.sqrt

/**
 * Records a custom phone motion and matches it later. The shape of the signed acceleration
 * magnitude over a fixed window is the signature: resampled to POINTS, z-normalised so
 * speed and orientation offsets don't matter, then compared by correlation.
 */
object GestureLearn {
    const val WINDOW_MS = 2500L
    const val POINTS = 40
    const val MATCH_THRESHOLD = 0.8f

    @Volatile var recording = false
        private set

    private var startedAt = 0L
    private val samples = ArrayList<Pair<Long, Float>>()

    fun begin() {
        synchronized(samples) {
            samples.clear()
            startedAt = System.currentTimeMillis()
            recording = true
        }
    }

    fun cancel() {
        synchronized(samples) {
            recording = false
            samples.clear()
        }
    }

    /** Feeds one sample. Saves the template and returns true once the window is complete. */
    fun feed(c: Context, t: Long, signed: Float): Boolean {
        synchronized(samples) {
            if (!recording) return false
            samples.add(t to signed)
            if (t - startedAt < WINDOW_MS) return false
            recording = false
            val tpl = normalise(resample(samples, startedAt, startedAt + WINDOW_MS, POINTS))
            Prefs.setMotionTemplate(c, tpl)
            samples.clear()
            return true
        }
    }

    /** Correlation of the most recent WINDOW_MS against the saved template, or null if none. */
    fun score(c: Context, recent: List<Pair<Long, Float>>, now: Long): Float? {
        val tpl = Prefs.motionTemplate(c) ?: return null
        if (tpl.size != POINTS) return null
        val window = normalise(resample(recent, now - WINDOW_MS, now, POINTS))
        var dot = 0f
        for (i in 0 until POINTS) dot += window[i] * tpl[i]
        return dot / POINTS
    }

    /** Average samples into `n` equal time buckets over [start, end); empty buckets carry forward. */
    fun resample(src: List<Pair<Long, Float>>, start: Long, end: Long, n: Int): FloatArray {
        val out = FloatArray(n)
        val span = (end - start).coerceAtLeast(1L).toFloat()
        val sums = FloatArray(n)
        val counts = IntArray(n)
        for ((t, v) in src) {
            if (t < start || t >= end) continue
            val idx = (((t - start) / span) * n).toInt().coerceIn(0, n - 1)
            sums[idx] += v
            counts[idx]++
        }
        var last = 0f
        for (i in 0 until n) {
            if (counts[i] > 0) last = sums[i] / counts[i]
            out[i] = last
        }
        return out
    }

    /** Zero mean, unit variance. A flat signal becomes all zeros (matches nothing). */
    fun normalise(a: FloatArray): FloatArray {
        val mean = a.average().toFloat()
        var varSum = 0f
        for (v in a) varSum += (v - mean) * (v - mean)
        val std = sqrt(varSum / a.size)
        return if (std < 1e-3f) FloatArray(a.size) else FloatArray(a.size) { (a[it] - mean) / std }
    }
}
