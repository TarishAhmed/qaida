package `in`.qaida.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MFCC features: 26 triangular mel filters over 300-8000 Hz on a 512-point FFT, log energies, DCT-II to
 * 13 cepstral coefficients, plus their deltas (regression over +-2 frames). One row per 10 ms frame.
 */
class Mfcc(
    private val sampleRate: Int = Framing.SAMPLE_RATE,
    private val nFft: Int = 512,
    val filters: Int = 26,
    private val fMin: Double = 300.0,
    private val fMax: Double = 8000.0,
    val coefficients: Int = 13,
) {
    private val fft = Fft(nFft)
    private val bins = nFft / 2 + 1

    /** filterbank[m][k]: weight of FFT bin k in mel filter m. */
    val filterbank: Array<DoubleArray> = run {
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val edges = DoubleArray(filters + 2) { melToHz(melMin + (melMax - melMin) * it / (filters + 1)) }
        val binOf = DoubleArray(edges.size) { edges[it] * nFft / sampleRate }
        Array(filters) { m ->
            DoubleArray(bins) { k ->
                val (lo, mid, hi) = Triple(binOf[m], binOf[m + 1], binOf[m + 2])
                when {
                    k < lo || k > hi -> 0.0
                    k <= mid -> (k - lo) / (mid - lo)
                    else -> (hi - k) / (hi - mid)
                }
            }
        }
    }

    // DCT-II basis with orthonormal scaling.
    private val dct = Array(coefficients) { i ->
        val scale = if (i == 0) sqrt(1.0 / filters) else sqrt(2.0 / filters)
        DoubleArray(filters) { m -> scale * cos(PI * i * (m + 0.5) / filters) }
    }

    /** Static coefficients followed by deltas: rows of 2 x [coefficients]. */
    fun compute(pcm: ShortArray): Array<DoubleArray> {
        val static = staticCoefficients(pcm)
        val deltas = deltas(static)
        return Array(static.size) { t -> static[t] + deltas[t] }
    }

    fun staticCoefficients(pcm: ShortArray): Array<DoubleArray> {
        val signal = Framing.preEmphasis(pcm)
        val frames = Framing.frameCount(signal.size)
        val frame = DoubleArray(Framing.WINDOW)
        val power = DoubleArray(bins)
        val logMel = DoubleArray(filters)
        return Array(frames) { t ->
            Framing.windowed(signal, t, frame)
            fft.powerSpectrum(frame, power)
            for (m in 0 until filters) {
                var e = 0.0
                val w = filterbank[m]
                for (k in 0 until bins) e += w[k] * power[k]
                logMel[m] = ln(e + 1e-10)
            }
            DoubleArray(coefficients) { i ->
                var c = 0.0
                for (m in 0 until filters) c += dct[i][m] * logMel[m]
                c
            }
        }
    }

    companion object {
        private const val DELTA_N = 2

        fun hzToMel(hz: Double) = 2595.0 * log10(1 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1)

        /** d_t = sum_n n (c_{t+n} - c_{t-n}) / (2 sum_n n^2), edges clamped. */
        fun deltas(c: Array<DoubleArray>): Array<DoubleArray> {
            if (c.isEmpty()) return c
            val denom = 2.0 * (1..DELTA_N).sumOf { it * it }
            val last = c.size - 1
            return Array(c.size) { t ->
                DoubleArray(c[0].size) { i ->
                    var num = 0.0
                    for (n in 1..DELTA_N) num += n * (c[minOf(t + n, last)][i] - c[maxOf(t - n, 0)][i])
                    num / denom
                }
            }
        }
    }
}
