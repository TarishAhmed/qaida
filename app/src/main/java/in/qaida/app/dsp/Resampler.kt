package `in`.qaida.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sample-rate conversion for reference clips: Opus always decodes at 48 kHz, analysis runs at 16 kHz.
 * A windowed-sinc low-pass below the target Nyquist, then linear interpolation at the new rate.
 */
object Resampler {
    private const val TAPS = 63

    fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || input.isEmpty()) return input.copyOf()
        val filtered = if (toRate < fromRate) lowPass(input, 0.45 * toRate / fromRate) else input.map { it.toDouble() }.toDoubleArray()
        val outLength = (input.size.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        return ShortArray(outLength) { i ->
            val pos = i * step
            val k = pos.toInt()
            val frac = pos - k
            val a = filtered[k]
            val b = if (k + 1 < filtered.size) filtered[k + 1] else a
            (a + (b - a) * frac).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** Hamming-windowed sinc low-pass with normalised cutoff [cutoff] (cycles per input sample). */
    private fun lowPass(x: ShortArray, cutoff: Double): DoubleArray {
        val half = TAPS / 2
        val h = DoubleArray(TAPS) { n ->
            val m = n - half
            val sinc = if (m == 0) 2 * cutoff else sin(2 * PI * cutoff * m) / (PI * m)
            sinc * (0.54 - 0.46 * cos(2 * PI * n / (TAPS - 1)))
        }
        val gain = h.sum()
        for (i in h.indices) h[i] /= gain
        return DoubleArray(x.size) { i ->
            var acc = 0.0
            for (n in 0 until TAPS) {
                val j = i + n - half
                if (j in x.indices) acc += h[n] * x[j]
            }
            acc
        }
    }
}
