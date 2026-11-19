package `in`.qaida.app.dsp

import kotlin.math.PI
import kotlin.math.cos

/** 25 ms Hamming windows every 10 ms at 16 kHz, after 0.97 pre-emphasis. */
object Framing {
    const val SAMPLE_RATE = 16_000
    const val WINDOW = 400 // 25 ms
    const val HOP = 160 // 10 ms
    const val PRE_EMPHASIS = 0.97
    const val FRAME_MS = 10

    val hamming = DoubleArray(WINDOW) { 0.54 - 0.46 * cos(2 * PI * it / (WINDOW - 1)) }

    /** PCM to [-1, 1] with y[n] = x[n] - 0.97 x[n-1]. */
    fun preEmphasis(pcm: ShortArray): DoubleArray {
        val out = DoubleArray(pcm.size)
        var prev = 0.0
        for (i in pcm.indices) {
            val x = pcm[i] / 32768.0
            out[i] = x - PRE_EMPHASIS * prev
            prev = x
        }
        return out
    }

    fun frameCount(samples: Int) = if (samples < WINDOW) 0 else 1 + (samples - WINDOW) / HOP

    /** Copies frame [index] of [signal] into [out], Hamming-windowed. */
    fun windowed(signal: DoubleArray, index: Int, out: DoubleArray) {
        val start = index * HOP
        for (i in 0 until WINDOW) out[i] = signal[start + i] * hamming[i]
    }

    fun frameToMs(frame: Int) = frame * FRAME_MS
}
