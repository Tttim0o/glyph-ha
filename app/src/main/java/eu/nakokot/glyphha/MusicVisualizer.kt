package eu.nakokot.glyphha

import android.media.audiofx.Visualizer
import com.topjohnwu.superuser.Shell
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Audio-reactive bottom-line bar: captures the global output mix (audio session 0) via
 * Visualizer FFT, splits the spectrum into 8 log-spaced bands and drives the 8 segments
 * like an equalizer / progress bar. Needs RECORD_AUDIO (granted via root in
 * GestureServiceControl). Session 0 capture may be restricted on some builds - if so the
 * bar simply stays dark; see Settings.
 */
class MusicVisualizer {
    private var viz: Visualizer? = null
    private val bands = 8
    private val level = FloatArray(bands)      // smoothed 0..1 per band
    private val frameMs = 60L
    private var lastFrameAt = 0L
    private var running = false

    fun start() {
        if (running) return
        running = true
        try {
            val v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft != null) onFft(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true
            viz = v
        } catch (e: Exception) {
            running = false
        }
    }

    fun stop() {
        running = false
        try { viz?.enabled = false; viz?.release() } catch (e: Exception) {}
        viz = null
        level.fill(0f)
        Glyph.showCount(0)
    }

    private fun onFft(fft: ByteArray) {
        val now = System.currentTimeMillis()
        if (now - lastFrameAt < frameMs) return
        lastFrameAt = now

        // fft layout: [0]=DC, [1]=Nyquist, then (re,im) pairs for bins 1..n/2-1.
        val bins = fft.size / 2
        val mags = FloatArray(bins)
        for (k in 1 until bins) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            mags[k] = sqrt(re * re + im * im)
        }

        // Log-spaced band edges from ~40 Hz up to ~8 kHz (bins 1..bins-1 cover 0..Nyquist).
        val minBin = 1
        val maxBin = bins / 4
        for (b in 0 until bands) {
            val lo = (minBin * Math.pow((maxBin.toDouble() / minBin), b / bands.toDouble())).toInt().coerceAtLeast(1)
            val hi = (minBin * Math.pow((maxBin.toDouble() / minBin), (b + 1) / bands.toDouble())).toInt().coerceAtLeast(lo + 1)
            var sum = 0f
            var count = 0
            for (k in lo until hi.coerceAtMost(bins)) { sum += mags[k]; count++ }
            val avg = if (count > 0) sum / count else 0f
            // dB-ish normalisation into 0..1
            val db = 20f * log10(max(avg, 1f) / 128f)
            val target = ((db + 40f) / 40f).coerceIn(0f, 1f)
            // fast attack, slower release = bar feels like a real level meter
            level[b] = if (target > level[b]) target else level[b] * 0.82f + target * 0.18f
        }
        render()
    }

    private fun render() {
        // Segment order matches the physical bottom line (Glyph.vlineIndexOrder).
        val sb = StringBuilder()
        Glyph.vlineIndexOrder.forEachIndexed { i, idx ->
            val v = level[i]
            val pct = (v * 100).toInt().coerceIn(0, 100)
            sb.append("echo \"$idx ${Glyph.raw(pct)}\" > ${Glyph.DIR}/single_led_br; ")
        }
        Shell.cmd(sb.toString()).submit()
    }
}
