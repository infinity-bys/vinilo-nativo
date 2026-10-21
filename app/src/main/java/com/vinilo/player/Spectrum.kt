package com.vinilo.player

/**
 * Espectro del audio que esta sonando, para las barras del reproductor.
 * SpectrumTap (dentro del reproductor) analiza el audio y lo deja aqui, con la hora
 * de cada muestra. La pantalla pide lo que corresponde al instante que se escucha.
 * No usa microfono ni ningun permiso.
 */
object Spectrum {
    const val N = 28
    private const val RING = 320

    private val times = LongArray(RING)
    private val data = FloatArray(RING * N)
    private var head = -1
    private var count = 0

    /** Cuanto se adelanta el audio ya analizado respecto a lo que se escucha (se aprende solo). */
    private var leadEst = 400f

    /** Posicion de la cancion (ms) en la que empezo el flujo de audio analizado. */
    @Volatile
    var base = 0L

    @Synchronized
    fun push(streamMs: Long, levels: FloatArray) {
        head = (head + 1) % RING
        times[head] = streamMs
        System.arraycopy(levels, 0, data, head * N, N)
        if (count < RING) count++
    }

    @Synchronized
    fun reset() {
        head = -1
        count = 0
    }

    /** Copia en `out` las barras del instante `mediaMs`. Devuelve false si no hay datos utiles. */
    @Synchronized
    fun fill(mediaMs: Long, out: FloatArray): Boolean {
        if (count == 0) return false
        var target = mediaMs - base
        val lead = (times[head] - target).toFloat()
        if (lead < 0f || lead > leadEst * 2f + 400f) {
            // Desfasado (por ejemplo al reabrir la app con la cancion en pausa): se recalibra solo
            base = mediaMs - (times[head] - leadEst.toLong())
            target = mediaMs - base
        } else {
            leadEst = leadEst * 0.97f + lead * 0.03f
        }
        for (n in 0 until count) {
            val i = ((head - n) % RING + RING) % RING
            if (times[i] <= target) {
                if (target - times[i] > 300L) return false
                System.arraycopy(data, i * N, out, 0, N)
                return true
            }
        }
        return false
    }
}
