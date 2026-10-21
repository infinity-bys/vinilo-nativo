package com.vinilo.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.vinilo.player.data.Store
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/** Lo que el ecualizador debe hacer ahora (la pantalla lo escribe, el procesador lo lee). */
object EqState {
    const val BANDS = 10

    /** Frecuencias centrales de las 10 bandas, en Hz. */
    val FREQS = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    /** Ganancia de cada banda en dB (-12 a 12). Se reemplaza entera, nunca se modifica a medias. */
    @Volatile
    var target: FloatArray = FloatArray(BANDS)
        private set

    /** Lee los ajustes guardados. Si el ecualizador esta apagado, todo queda en cero. */
    fun load(store: Store) {
        val eq = store.eq
        val on = store.eqOn
        target = FloatArray(BANDS) { i -> if (on) eq.getOrElse(i) { 0 }.toFloat() else 0f }
    }
}

/**
 * Ecualizador grafico de 10 bandas hecho por software: un filtro "peaking" por banda.
 * No depende del telefono, asi que se ve y suena igual en todos.
 * Los cambios de ganancia se hacen poco a poco (sin chasquidos) y, para no distorsionar,
 * se baja un poco el volumen general cuando se refuerza y se suaviza lo que pase de 0 dB.
 */
@OptIn(UnstableApi::class)
class EqProcessor : BaseAudioProcessor() {

    private val n = EqState.BANDS
    private val cur = FloatArray(n)

    // coeficientes de cada banda (ya normalizados)
    private val cb0 = DoubleArray(n) { 1.0 }
    private val cb1 = DoubleArray(n)
    private val cb2 = DoubleArray(n)
    private val ca1 = DoubleArray(n)
    private val ca2 = DoubleArray(n)

    // memoria de cada filtro por canal
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)

    private var rate = 44100
    private var channels = 2
    private var isFloat = false
    private var bypass = true
    private var pre = 1.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        isFloat = enc == C.ENCODING_PCM_FLOAT
        rate = inputAudioFormat.sampleRate
        channels = max(1, inputAudioFormat.channelCount)
        z1 = DoubleArray(channels * n)
        z2 = DoubleArray(channels * n)
        // una cancion nueva empieza ya con el ecualizador puesto
        val t = EqState.target
        for (b in 0 until n) {
            cur[b] = t[b]
            setBand(b, cur[b])
        }
        refresh()
        return inputAudioFormat
    }

    override fun onFlush() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    override fun onReset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    /** Filtro "peaking" (receta clasica de Robert Bristow-Johnson). */
    private fun setBand(b: Int, gainDb: Float) {
        val f0 = EqState.FREQS[b]
        if (f0 >= rate * 0.45f || abs(gainDb) < 0.01f) {
            cb0[b] = 1.0
            cb1[b] = 0.0
            cb2[b] = 0.0
            ca1[b] = 0.0
            ca2[b] = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / rate
        val alpha = sin(w0) / (2.0 * Q)
        val c = cos(w0)
        val a0 = 1.0 + alpha / a
        cb0[b] = (1.0 + alpha * a) / a0
        cb1[b] = (-2.0 * c) / a0
        cb2[b] = (1.0 - alpha * a) / a0
        ca1[b] = (-2.0 * c) / a0
        ca2[b] = (1.0 - alpha / a) / a0
    }

    /** Recalcula si todo esta plano (se salta el filtrado) y cuanto bajar el volumen. */
    private fun refresh() {
        var any = false
        var maxPos = 0f
        for (b in 0 until n) {
            if (cur[b] != 0f) any = true
            if (cur[b] > maxPos) maxPos = cur[b]
        }
        if (!any && !bypass) {
            z1.fill(0.0)
            z2.fill(0.0)
        }
        bypass = !any
        pre = 10.0.pow(-(maxPos * 0.5) / 20.0)
    }

    /** Acerca poco a poco las ganancias a lo pedido (sin saltos bruscos). */
    private fun stepGains() {
        val t = EqState.target
        var changed = false
        for (b in 0 until n) {
            val d = t[b] - cur[b]
            if (d != 0f) {
                cur[b] = if (abs(d) <= STEP) t[b] else cur[b] + (if (d > 0f) STEP else -STEP)
                setBand(b, cur[b])
                changed = true
            }
        }
        if (changed) refresh()
    }

    private fun soft(y: Double): Double {
        val a = abs(y)
        if (a <= 0.92) return y
        val s = 0.92 + 0.08 * tanh((a - 0.92) / 0.08)
        return if (y < 0.0) -s else s
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val ib = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = (if (isFloat) 4 else 2) * channels
        val frames = remaining / frameBytes
        var done = 0
        while (done < frames) {
            val chunk = min(CHUNK, frames - done)
            stepGains()
            if (bypass) {
                val lim = ib.limit()
                ib.limit(ib.position() + chunk * frameBytes)
                out.put(ib)
                ib.limit(lim)
            } else {
                for (f in 0 until chunk) {
                    for (ch in 0 until channels) {
                        var x = if (isFloat) ib.getFloat().toDouble() else ib.getShort() / 32768.0
                        val base = ch * n
                        for (b in 0 until n) {
                            val i = base + b
                            val y = cb0[b] * x + z1[i]
                            z1[i] = cb1[b] * x - ca1[b] * y + z2[i]
                            z2[i] = cb2[b] * x - ca2[b] * y
                            x = y
                        }
                        x = soft(x * pre)
                        if (isFloat) {
                            out.putFloat(x.toFloat())
                        } else {
                            out.putShort((x * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
                        }
                    }
                }
            }
            done += chunk
        }
        // si sobro media trama, pasa igual
        while (ib.hasRemaining()) out.put(ib.get())
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private companion object {
        const val Q = 1.41
        const val STEP = 0.5f
        const val CHUNK = 256
    }
}
