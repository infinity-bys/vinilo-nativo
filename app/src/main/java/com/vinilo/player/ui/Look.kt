package com.vinilo.player.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Color dominante de la carátula (misma idea que la versión web). */
private fun dominant(src: Bitmap): IntArray? {
    val s = Bitmap.createScaledBitmap(src, 32, 32, true)
    val px = IntArray(32 * 32)
    s.getPixels(px, 0, 32, 0, 0, 32, 32)
    val m = HashMap<Int, DoubleArray>()
    for (p in px) {
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val lum = (r + g + b) / 3.0
        if (lum < 25 || lum > 235) continue
        val sat = if (mx > 0) (mx - mn).toDouble() / mx else 0.0
        val w = 0.3 + sat
        val k = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
        val o = m.getOrPut(k) { DoubleArray(4) }
        o[0] += w
        o[1] += r * w
        o[2] += g * w
        o[3] += b * w
    }
    var best: DoubleArray? = null
    for (o in m.values) {
        if (best == null || o[0] > best[0]) best = o
    }
    val bst = best ?: return null
    return intArrayOf(
        (bst[1] / bst[0]).roundToInt(),
        (bst[2] / bst[0]).roundToInt(),
        (bst[3] / bst[0]).roundToInt()
    )
}

/**
 * Colores del reproductor según la carátula.
 * El fondo siempre se tiñe; los botones solo si el ajuste está activo.
 */
fun computeLook(bmp: Bitmap?, accentOn: Boolean): Look {
    if (bmp == null) return Look()
    val c = dominant(bmp) ?: return Look()

    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(c[0], c[1], c[2], hsl)
    val h = hsl[0]
    val s = hsl[1]

    fun hs(dh: Float, sat: Float, lig: Float): Color {
        val hh = (((h + dh) % 360f) + 360f) % 360f
        return Color(ColorUtils.HSLToColor(floatArrayOf(hh, sat, lig)))
    }

    fun mix(t: Double): Color {
        val base = intArrayOf(26, 20, 36)
        val v0 = DoubleArray(3) { c[it] * t + base[it] * (1 - t) }
        val l = v0[0] * 0.3 + v0[1] * 0.59 + v0[2] * 0.11
        val v = if (l > 85) DoubleArray(3) { v0[it] * 85 / l } else v0
        return Color(
            v[0].roundToInt().coerceIn(0, 255),
            v[1].roundToInt().coerceIn(0, 255),
            v[2].roundToInt().coerceIn(0, 255)
        )
    }

    val top = mix(0.6)
    val bottom = mix(0.22)
    return if (accentOn && s >= 0.14f) {
        Look(
            acc = hs(15f, 0.82f, 0.66f),
            acc2 = hs(-30f, 0.78f, 0.58f),
            soft = hs(5f, 0.70f, 0.84f),
            top = top,
            bottom = bottom
        )
    } else {
        Look(top = top, bottom = bottom)
    }
}
