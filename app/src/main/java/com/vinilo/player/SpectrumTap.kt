package com.vinilo.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Se coloca dentro del reproductor: deja pasar el audio sin tocarlo y, de paso,
 * calcula cuanta energia hay en cada rango de frecuencias (graves a agudos).
 */
@OptIn(UnstableApi::class)
class SpectrumTap : BaseAudioProcessor() {

    private val size = 2048
    private val hop = 1024
    private val win = FloatArray(size)
    private val hann = FloatArray(size) { (0.5 - 0.5 * cos(2.0 * Math.PI * it / (size - 1))).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val mags = FloatArray(size / 2 + 1)
    private var wpos = 0
    private var sinceHop = 0
    private var samples = 0L
    private var peak = 4f
    private var rate = 44100
    private var channels = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        rate = inputAudioFormat.sampleRate
        channels = max(1, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        feed(inputBuffer.duplicate())
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onQueueEndOfStream() {
        restart()
    }

    override fun onFlush() {
        restart()
    }

    override fun onReset() {
        restart()
    }

    private fun restart() {
        win.fill(0f)
        wpos = 0
        sinceHop = 0
        samples = 0L
        Spectrum.reset()
    }

    private fun feed(buf: ByteBuffer) {
        val b = buf.order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = 2 * channels
        while (b.remaining() >= frameBytes) {
            var sum = 0
            for (c in 0 until channels) sum += b.getShort().toInt()
            win[wpos] = sum / (channels * 32768f)
            wpos = (wpos + 1) and (size - 1)
            samples++
            sinceHop++
            if (sinceHop >= hop) {
                sinceHop = 0
                analyze()
            }
        }
    }

    private fun analyze() {
        for (i in 0 until size) {
            re[i] = win[(wpos + i) and (size - 1)] * hann[i]
            im[i] = 0f
        }
        fft()
        for (k in 0..size / 2) mags[k] = sqrt(re[k] * re[k] + im[k] * im[k])

        val n = Spectrum.N
        val binHz = rate.toFloat() / size
        val fLo = 45f
        val fHi = min(14000f, rate / 2f * 0.95f)
        val ratio = fHi / fLo
        val out = FloatArray(n)
        var frameMax = 0f
        for (i in 0 until n) {
            val f0 = fLo * ratio.pow(i.toFloat() / n)
            val f1 = fLo * ratio.pow((i + 1f) / n)
            val fc = fLo * ratio.pow((i + 0.5f) / n)
            val k0 = (f0 / binHz).toInt().coerceIn(1, size / 2 - 2)
            val k1 = max(k0 + 1, (f1 / binHz).toInt()).coerceAtMost(size / 2)
            var m = 0f
            for (k in k0 until k1) m = max(m, mags[k])
            val c = fc / binHz
            val ci = c.toInt().coerceIn(1, size / 2 - 2)
            val fr = (c - ci).coerceIn(0f, 1f)
            val interp = mags[ci] * (1f - fr) + mags[ci + 1] * fr
            // los agudos suenan con menos energia: se compensa para que las barras se vean parejas
            val tilt = (fc / 120f).pow(0.45f)
            val v = max(m, interp) * tilt
            out[i] = v
            if (v > frameMax) frameMax = v
        }
        // se ajusta solo al volumen de la cancion
        peak = max(frameMax, peak * 0.9985f).coerceAtLeast(1.5f)
        if (frameMax < 0.05f) {
            for (i in 0 until n) out[i] = 0f
        } else {
            for (i in 0 until n) out[i] = sqrt((out[i] / peak).coerceIn(0f, 1f))
        }
        Spectrum.push((samples - size / 2) * 1000L / rate, out)
    }

    /** Transformada rapida de Fourier (radix-2) sobre re/im. */
    private fun fft() {
        val n = size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wlr = cos(ang).toFloat()
            val wli = kotlin.math.sin(ang).toFloat()
            val half = len / 2
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] = re[a] + vr
                    im[a] = im[a] + vi
                    val nwr = wr * wlr - wi * wli
                    wi = wr * wli + wi * wlr
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
