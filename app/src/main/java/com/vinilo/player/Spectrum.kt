package com.vinilo.player

import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Espectro del audio real que esta sonando (para las barras del reproductor).
 * Usa el Visualizer de Android, que lee la salida de audio de la propia app.
 * Android exige el permiso de "grabar audio" para usarlo; la app NO graba nada.
 */
object Spectrum {
    const val N = 28

    @Volatile
    var bands: FloatArray = FloatArray(N)
        private set

    @Volatile
    private var lastAt = 0L

    private var viz: Visualizer? = null
    private var peak = 40f

    /** true si estan llegando datos reales (si no, la pantalla usa una animacion de respaldo). */
    fun isLive(): Boolean = SystemClock.uptimeMillis() - lastAt < 400L

    fun start(session: Int) {
        stop()
        if (session == 0) return
        try {
            val v = Visualizer(session)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(min(range[1], 1024))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                }

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) process(fft)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.setEnabled(true)
            viz = v
        } catch (t: Throwable) {
            viz = null
        }
    }

    fun stop() {
        try {
            viz?.setEnabled(false)
            viz?.release()
        } catch (t: Throwable) {
        }
        viz = null
        lastAt = 0L
        bands = FloatArray(N)
    }

    private fun process(fft: ByteArray) {
        val n = fft.size / 2
        if (n < 8) return
        val mags = FloatArray(n)
        for (k in 1 until n) {
            val re = fft[2 * k].toInt()
            val im = fft[2 * k + 1].toInt()
            mags[k] = sqrt((re * re + im * im).toFloat())
        }
        // de ~40 Hz hasta ~13 kHz, repartido en escala logaritmica
        val limit = (n * 0.55).toInt().coerceAtLeast(4)
        val out = FloatArray(N)
        var frameMax = 0f
        for (i in 0 until N) {
            val lo = edge(i, limit)
            val hi = max(lo + 1, edge(i + 1, limit))
            var m = 0f
            for (k in lo until min(hi, n)) m = max(m, mags[k])
            out[i] = m
            if (m > frameMax) frameMax = m
        }
        // se ajusta solo al volumen de la cancion
        peak = max(frameMax, peak * 0.995f).coerceAtLeast(24f)
        for (i in 0 until N) {
            val x = (out[i] / peak).coerceIn(0f, 1f)
            out[i] = sqrt(x)
        }
        bands = out
        lastAt = SystemClock.uptimeMillis()
    }

    private fun edge(i: Int, limit: Int): Int =
        limit.toDouble().pow(i.toDouble() / N).toInt().coerceIn(1, limit)
}
