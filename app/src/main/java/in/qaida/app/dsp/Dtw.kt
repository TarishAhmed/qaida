package `in`.qaida.app.dsp

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Dynamic time warping of two feature sequences with a Sakoe-Chiba band.
 *
 * The band follows the diagonal from (0, 0) to (n-1, m-1), so sequences of different lengths still align
 * end to end, with a radius of 10% of the longer sequence. Local cost is cosine distance (0 identical,
 * 2 opposite); the result is the total cost along the best path divided by its length, halved into 0..1.
 */
object Dtw {
    const val BAND_FRACTION = 0.1

    data class Result(
        /** Mean cosine distance along the path, scaled to 0..1. */
        val distance: Double,
        /** Aligned (a index, b index) pairs from start to end, monotone in both. */
        val path: List<Pair<Int, Int>>,
    )

    fun align(a: Array<DoubleArray>, b: Array<DoubleArray>, bandFraction: Double = BAND_FRACTION): Result {
        val n = a.size
        val m = b.size
        require(n > 0 && m > 0) { "cannot align empty sequences" }
        val radius = maxOf(1, ceil(bandFraction * maxOf(n, m)).toInt())
        val normA = DoubleArray(n) { norm(a[it]) }
        val normB = DoubleArray(m) { norm(b[it]) }

        val inf = Double.POSITIVE_INFINITY
        val cost = Array(n) { DoubleArray(m) { inf } }
        // Back-pointer per cell: 0 diagonal, 1 from (i-1, j), 2 from (i, j-1).
        val from = Array(n) { ByteArray(m) }
        for (i in 0 until n) {
            val centre = if (n == 1) 0.0 else i.toDouble() * (m - 1) / (n - 1)
            val lo = maxOf(0, (centre - radius).toInt())
            val hi = minOf(m - 1, ceil(centre + radius).toInt())
            for (j in lo..hi) {
                val d = cosine(a[i], b[j], normA[i], normB[j])
                if (i == 0 && j == 0) { cost[0][0] = d; continue }
                val diag = if (i > 0 && j > 0) cost[i - 1][j - 1] else inf
                val up = if (i > 0) cost[i - 1][j] else inf
                val left = if (j > 0) cost[i][j - 1] else inf
                when {
                    diag <= up && diag <= left -> { cost[i][j] = d + diag; from[i][j] = 0 }
                    up <= left -> { cost[i][j] = d + up; from[i][j] = 1 }
                    else -> { cost[i][j] = d + left; from[i][j] = 2 }
                }
            }
        }

        val path = ArrayList<Pair<Int, Int>>(n + m)
        var i = n - 1
        var j = m - 1
        while (true) {
            path += i to j
            if (i == 0 && j == 0) break
            when (from[i][j].toInt()) {
                0 -> { i--; j-- }
                1 -> i--
                else -> j--
            }
        }
        path.reverse()
        return Result(cost[n - 1][m - 1] / path.size / 2.0, path)
    }

    private fun norm(v: DoubleArray): Double {
        var s = 0.0
        for (x in v) s += x * x
        return sqrt(s)
    }

    private fun cosine(x: DoubleArray, y: DoubleArray, nx: Double, ny: Double): Double {
        if (nx == 0.0 || ny == 0.0) return if (nx == ny) 0.0 else 1.0
        var dot = 0.0
        for (k in x.indices) dot += x[k] * y[k]
        return (1.0 - dot / (nx * ny)).coerceIn(0.0, 2.0)
    }
}
