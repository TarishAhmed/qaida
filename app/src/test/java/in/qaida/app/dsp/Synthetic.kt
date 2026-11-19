package `in`.qaida.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Speech-like test signals with exactly known boundaries, at 16 kHz in [-1, 1].
 * Vowels are a glottal pulse train through formant resonators; fricatives are high-passed noise.
 */
object Synthetic {
    const val RATE = 16_000
    fun samples(ms: Int) = ms * RATE / 1000

    fun sine(freq: Double, ms: Int, amp: Double = 0.5, rate: Int = RATE) =
        DoubleArray(ms * rate / 1000) { amp * sin(2 * PI * freq * it / rate) }

    /** A vowel: pulses at [f0] Hz filtered by resonators at [formants], with 15 ms fade in and out. */
    fun vowel(ms: Int, f0: Double = 120.0, formants: List<Double> = listOf(700.0, 1200.0, 2600.0), amp: Double = 0.4): DoubleArray {
        val n = samples(ms)
        var x = DoubleArray(n)
        val period = RATE / f0
        var next = 0.0
        for (i in 0 until n) if (i >= next) { x[i] = 1.0; next += period }
        for (f in formants) x = resonate(x, f, bandwidth = 90.0)
        return shape(normalise(x, amp))
    }

    /** A fricative hiss: white noise through a first difference (energy above ~3 kHz). */
    fun fricative(ms: Int, amp: Double = 0.15, rng: Random = Random(7)): DoubleArray {
        val n = samples(ms)
        val w = DoubleArray(n + 1) { rng.nextDouble(-1.0, 1.0) }
        return shape(normalise(DoubleArray(n) { w[it + 1] - w[it] }, amp))
    }

    /** Places [parts] (start ms to signal) on [totalMs] of white noise at [noiseAmp] RMS-ish level. */
    fun mix(totalMs: Int, parts: List<Pair<Int, DoubleArray>>, noiseAmp: Double = 0.003, rng: Random = Random(11)): ShortArray {
        val out = DoubleArray(samples(totalMs)) { rng.nextDouble(-1.0, 1.0) * noiseAmp * 1.7 }
        for ((startMs, sig) in parts) {
            val s = samples(startMs)
            for (i in sig.indices) if (s + i < out.size) out[s + i] += sig[i]
        }
        return toPcm(out)
    }

    fun toPcm(x: DoubleArray) = ShortArray(x.size) { (x[it] * 32767).roundToInt().coerceIn(-32768, 32767).toShort() }

    /** Stretches [x] in time by [factor] with linear interpolation (changes duration, not pitch-accurately). */
    fun stretch(x: DoubleArray, factor: Double) = DoubleArray((x.size * factor).toInt()) { i ->
        val p = i / factor
        val k = p.toInt().coerceAtMost(x.size - 1)
        val f = p - k
        val b = if (k + 1 < x.size) x[k + 1] else x[k]
        x[k] + (b - x[k]) * f
    }

    private fun resonate(x: DoubleArray, freq: Double, bandwidth: Double): DoubleArray {
        val r = exp(-PI * bandwidth / RATE)
        val a1 = 2 * r * cos(2 * PI * freq / RATE)
        val a2 = -r * r
        val y = DoubleArray(x.size)
        for (i in x.indices) y[i] = x[i] + a1 * (if (i > 0) y[i - 1] else 0.0) + a2 * (if (i > 1) y[i - 2] else 0.0)
        return y
    }

    private fun normalise(x: DoubleArray, peak: Double): DoubleArray {
        val m = x.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: return x
        return DoubleArray(x.size) { x[it] / m * peak }
    }

    private fun shape(x: DoubleArray): DoubleArray {
        val ramp = samples(15)
        return DoubleArray(x.size) { i ->
            val g = minOf(1.0, i.toDouble() / ramp, (x.size - 1 - i).toDouble() / ramp)
            x[i] * g.coerceAtLeast(0.0)
        }
    }
}
