package `in`.qaida.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.qaida.app.dsp.RecitationAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Times RecitationAnalyzer on the device: 3 s of recording against a 3 s reference. The spec's budget is
 * 150 ms on a Snapdragon 4-series phone; this runs wherever the instrumented suite runs and logs the median.
 */
@RunWith(AndroidJUnit4::class)
class AnalyzerBenchmarkTest {
    /** Harmonic-rich bursts on low noise: enough structure for VAD, MFCC and DTW to do full work. */
    private fun clip(speechFromMs: Int, speechMs: Int, seed: Int): ShortArray {
        val rng = Random(seed)
        return ShortArray(48_000) { i ->
            val ms = i / 16
            var x = rng.nextDouble(-1.0, 1.0) * 0.004
            if (ms in speechFromMs until speechFromMs + speechMs) {
                for (h in 1..12) x += 0.04 / h * sin(2 * PI * 130.0 * h * i / 16_000)
            }
            (x * 32767).toInt().toShort()
        }
    }

    @Test
    fun threeSecondClipAnalysisTime() {
        val user = clip(400, 2200, 1)
        val ref = clip(300, 2000, 2)
        repeat(5) { RecitationAnalyzer.analyze(user, ref) }
        val times = List(15) {
            val t = System.nanoTime()
            RecitationAnalyzer.analyze(user, ref)
            (System.nanoTime() - t) / 1e6
        }.sorted()
        Log.i("QaidaBench", "analyze 3 s vs 3 s: median ${"%.1f".format(times[7])} ms, max ${"%.1f".format(times.last())} ms")
        assertTrue("median ${times[7]} ms", times[7] < 150)
    }
}
