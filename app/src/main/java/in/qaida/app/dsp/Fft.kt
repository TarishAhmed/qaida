package `in`.qaida.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iterative radix-2 Cooley-Tukey FFT of size [n] (a power of two). Twiddles and the bit-reversal
 * permutation are computed once; [transform] allocates nothing.
 */
class Fft(val n: Int) {
    init {
        require(n >= 2 && n and (n - 1) == 0) { "FFT size must be a power of two, got $n" }
    }

    private val cosTable = DoubleArray(n / 2) { cos(-2 * PI * it / n) }
    private val sinTable = DoubleArray(n / 2) { sin(-2 * PI * it / n) }
    private val reversed = IntArray(n).also { rev ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** In-place forward transform of the complex sequence ([re], [im]). */
    fun transform(re: DoubleArray, im: DoubleArray) {
        require(re.size == n && im.size == n)
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val l = j + half
                    val tr = wr * re[l] - wi * im[l]
                    val ti = wr * im[l] + wi * re[l]
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    private val scratchRe = DoubleArray(n)
    private val scratchIm = DoubleArray(n)

    /**
     * Power spectrum |X(k)|^2 for k in 0..n/2 of a real [frame] (zero-padded to n) into [out].
     * Not thread-safe: reuses internal scratch buffers.
     */
    fun powerSpectrum(frame: DoubleArray, out: DoubleArray) {
        require(frame.size <= n && out.size == n / 2 + 1)
        frame.copyInto(scratchRe)
        scratchRe.fill(0.0, frame.size, n)
        scratchIm.fill(0.0)
        transform(scratchRe, scratchIm)
        for (k in out.indices) out[k] = scratchRe[k] * scratchRe[k] + scratchIm[k] * scratchIm[k]
    }
}
