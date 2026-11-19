package `in`.qaida.app.dsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class FftTest {
    @Test
    fun `matches a naive DFT on random input`() {
        val n = 64
        val rng = Random(1)
        val x = DoubleArray(n) { rng.nextDouble(-1.0, 1.0) }
        val re = x.copyOf(); val im = DoubleArray(n)
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            var sr = 0.0; var si = 0.0
            for (t in 0 until n) { sr += x[t] * cos(-2 * PI * k * t / n); si += x[t] * sin(-2 * PI * k * t / n) }
            assertEquals(sr, re[k], 1e-9); assertEquals(si, im[k], 1e-9)
        }
    }

    @Test
    fun `a sine at bin k peaks at bin k`() {
        val n = 512
        val k = 37
        val frame = DoubleArray(n) { sin(2 * PI * k * it / n) }
        val power = DoubleArray(n / 2 + 1)
        Fft(n).powerSpectrum(frame, power)
        assertEquals(k, power.indices.maxBy { power[it] })
    }

    @Test
    fun `Parseval holds`() {
        val n = 512
        val rng = Random(2)
        val x = DoubleArray(n) { rng.nextDouble(-1.0, 1.0) }
        val re = x.copyOf(); val im = DoubleArray(n)
        Fft(n).transform(re, im)
        val time = x.sumOf { it * it }
        val freq = (0 until n).sumOf { re[it] * re[it] + im[it] * im[it] } / n
        assertEquals(time, freq, 1e-8)
    }

    @Test
    fun `rejects sizes that are not powers of two`() {
        assertThrows<IllegalArgumentException> { Fft(400) }
    }
}

class FramingTest {
    @Test
    fun `frame geometry is 25 ms every 10 ms`() {
        assertEquals(400, Framing.WINDOW); assertEquals(160, Framing.HOP)
        assertEquals(0, Framing.frameCount(399))
        assertEquals(1, Framing.frameCount(400))
        assertEquals(98, Framing.frameCount(16_000)) // 1 + (16000 - 400) / 160
    }

    @Test
    fun `Hamming window and pre-emphasis`() {
        assertEquals(0.08, Framing.hamming.first(), 1e-9)
        assertEquals(0.08, Framing.hamming.last(), 1e-9)
        assertEquals(1.0, Framing.hamming.max(), 1e-4)
        val y = Framing.preEmphasis(shortArrayOf(16384, 16384))
        assertEquals(0.5, y[0], 1e-9)
        assertEquals(0.5 - 0.97 * 0.5, y[1], 1e-9)
    }
}

class MfccTest {
    private val mfcc = Mfcc()

    @Test
    fun `mel scale round trips`() {
        for (hz in listOf(300.0, 1000.0, 4000.0, 8000.0)) assertEquals(hz, Mfcc.melToHz(Mfcc.hzToMel(hz)), 1e-6)
    }

    @Test
    fun `26 filters between 300 and 8000 Hz with rising centres`() {
        assertEquals(26, mfcc.filterbank.size)
        val centres = mfcc.filterbank.map { w -> w.indices.maxBy { w[it] } }
        assertTrue(centres.zipWithNext().all { (a, b) -> b >= a })
        val hzPerBin = 16_000.0 / 512
        assertTrue(centres.first() * hzPerBin > 300 && centres.last() * hzPerBin < 8000)
    }

    @Test
    fun `13 coefficients plus deltas per frame`() {
        val pcm = Synthetic.toPcm(Synthetic.vowel(500))
        val f = mfcc.compute(pcm)
        assertEquals(Framing.frameCount(pcm.size), f.size)
        assertTrue(f.all { it.size == 26 })
    }

    @Test
    fun `deltas of a constant sequence are zero`() {
        val c = Array(10) { doubleArrayOf(1.0, 2.0, 3.0) }
        assertTrue(Mfcc.deltas(c).all { row -> row.all { abs(it) < 1e-12 } })
    }

    @Test
    fun `different vowels give different features, the same vowel the same`() {
        val a = mfcc.staticCoefficients(Synthetic.toPcm(Synthetic.vowel(300, formants = listOf(700.0, 1200.0, 2600.0))))
        val a2 = mfcc.staticCoefficients(Synthetic.toPcm(Synthetic.vowel(300, formants = listOf(700.0, 1200.0, 2600.0))))
        val i = mfcc.staticCoefficients(Synthetic.toPcm(Synthetic.vowel(300, formants = listOf(300.0, 2300.0, 3000.0))))
        assertArrayEquals(a[10], a2[10], 1e-12)
        val d = sqrt((1 until 13).sumOf { (a[10][it] - i[10][it]).let { x -> x * x } })
        assertTrue(d > 1.0, "distance $d")
    }
}

class VadTest {
    /** Ten labelled clips: (description, total ms, onset ms, offset ms, pcm). */
    private val clips: List<Triple<String, IntRange, ShortArray>> = run {
        val r = Random(3)
        listOf(
            Triple("short vowel", 300..600, Synthetic.mix(1500, listOf(300 to Synthetic.vowel(300)))),
            Triple("long vowel", 400..1900, Synthetic.mix(2600, listOf(400 to Synthetic.vowel(1500)))),
            Triple("held vowel, low pitch", 600..1700, Synthetic.mix(2400, listOf(600 to Synthetic.vowel(1100, f0 = 95.0)))),
            Triple("high pitch vowel", 350..1050, Synthetic.mix(1800, listOf(350 to Synthetic.vowel(700, f0 = 220.0, formants = listOf(400.0, 2000.0, 2800.0))))),
            Triple("fricative alone", 500..900, Synthetic.mix(1600, listOf(500 to Synthetic.fricative(400)))),
            Triple("fricative then vowel", 400..1100, Synthetic.mix(1800, listOf(400 to Synthetic.fricative(200), 600 to Synthetic.vowel(500)))),
            Triple("vowel, stop gap, vowel", 300..1200, Synthetic.mix(1800, listOf(300 to Synthetic.vowel(400), 780 to Synthetic.vowel(420)))),
            Triple("quiet vowel", 450..1250, Synthetic.mix(1900, listOf(450 to Synthetic.vowel(800, amp = 0.06)))),
            Triple("noisy room", 500..1300, Synthetic.mix(2000, listOf(500 to Synthetic.vowel(800)), noiseAmp = 0.02, rng = r)),
            Triple("late start, long", 1000..2800, Synthetic.mix(3200, listOf(1000 to Synthetic.vowel(1800, f0 = 140.0)))),
        )
    }

    @Test
    fun `VAD durations are within 10 percent on ten labelled clips`() {
        for ((name, truth, pcm) in clips) {
            val seg = Vad.detect(pcm).segment
            assertNotNull(seg, name)
            val trueMs = truth.last - truth.first
            val err = abs(seg!!.durationMs - trueMs).toDouble() / trueMs
            println("vad $name: truth ${truth.first}..${truth.last} got ${seg.onsetMs}..${seg.offsetMs} err ${"%.1f".format(err * 100)}%")
            assertTrue(err <= 0.10, "$name: duration ${seg.durationMs} ms vs $trueMs ms")
            assertTrue(abs(seg.onsetMs - truth.first) <= 40, "$name: onset ${seg.onsetMs} vs ${truth.first}")
        }
    }

    @Test
    fun `VAD durations are within 10 percent on hand-labelled real clips, when present`() {
        val dir = javaClass.classLoader!!.getResource("vad")?.let { java.io.File(it.toURI()) }
        val labels = dir?.resolve("labels.json")?.takeIf { it.exists() }
        org.junit.jupiter.api.Assumptions.assumeTrue(labels != null, "no hand-labelled clips in test resources/vad")
        val json = org.json.JSONObject(labels!!.readText())
        for (name in json.keys()) {
            val label = json.getJSONObject(name)
            val (pcm, rate) = Wav.decode(dir.resolve(name).readBytes())
            val seg = Vad.detect(Resampler.resample(pcm, rate, Framing.SAMPLE_RATE)).segment
            assertNotNull(seg, name)
            val trueMs = label.getInt("offsetMs") - label.getInt("onsetMs")
            val err = abs(seg!!.durationMs - trueMs).toDouble() / trueMs
            println("vad real $name: truth $trueMs ms got ${seg.durationMs} ms err ${"%.1f".format(err * 100)}%")
            assertTrue(err <= 0.10, "$name: ${seg.durationMs} ms vs $trueMs ms")
        }
    }

    @Test
    fun `silence and steady noise contain no speech`() {
        assertNull(Vad.detect(ShortArray(16_000)).segment)
        assertNull(Vad.detect(Synthetic.mix(2000, emptyList(), noiseAmp = 0.02)).segment)
    }

    @Test
    fun `a click is not speech`() {
        val pcm = Synthetic.mix(1500, listOf(700 to Synthetic.vowel(30)))
        assertNull(Vad.detect(pcm).segment)
    }
}

class DtwTest {
    private val mfcc = Mfcc()

    @Test
    fun `identical sequences align on the diagonal with zero distance`() {
        val f = mfcc.compute(Synthetic.toPcm(Synthetic.vowel(400)))
        val r = Dtw.align(f, f)
        assertEquals(0.0, r.distance, 1e-9)
        assertTrue(r.path.all { (i, j) -> i == j })
    }

    @Test
    fun `a time-stretched copy aligns end to end, monotonically, and close`() {
        val v = Synthetic.vowel(400, formants = listOf(500.0, 1500.0, 2500.0)) + Synthetic.vowel(300, formants = listOf(300.0, 2200.0, 2900.0))
        val a = mfcc.compute(Synthetic.toPcm(v))
        val b = mfcc.compute(Synthetic.toPcm(Synthetic.stretch(v, 1.08)))
        val r = Dtw.align(a, b)
        assertEquals(0 to 0, r.path.first())
        assertEquals(a.size - 1 to b.size - 1, r.path.last())
        assertTrue(r.path.zipWithNext().all { (p, q) -> q.first >= p.first && q.second >= p.second })
        val other = Dtw.align(a, mfcc.compute(Synthetic.toPcm(Synthetic.fricative(700))))
        assertTrue(r.distance < other.distance, "stretched ${r.distance} vs different ${other.distance}")
        assertTrue(r.distance in 0.0..1.0 && other.distance in 0.0..1.0)
    }

    @Test
    fun `path stays inside the Sakoe-Chiba band`() {
        val a = Array(100) { doubleArrayOf(1.0, it.toDouble()) }
        val b = Array(100) { doubleArrayOf(1.0, (99 - it).toDouble()) }
        val r = Dtw.align(a, b)
        assertTrue(r.path.all { (i, j) -> abs(i - j) <= 10 })
    }
}

class ResamplerWavTest {
    @Test
    fun `48 kHz to 16 kHz keeps a 1 kHz tone and drops a 12 kHz one`() {
        fun rms(x: ShortArray) = sqrt(x.drop(100).dropLast(100).sumOf { it.toDouble() * it } / (x.size - 200))
        val keep = Resampler.resample(Synthetic.toPcm(Synthetic.sine(1000.0, 500, rate = 48_000)), 48_000, 16_000)
        val drop = Resampler.resample(Synthetic.toPcm(Synthetic.sine(12_000.0, 500, rate = 48_000)), 48_000, 16_000)
        assertEquals(8000, keep.size)
        assertEquals(0.5 * 32767 / sqrt(2.0), rms(keep), 0.05 * 32767)
        assertTrue(rms(drop) < 0.02 * 32767, "aliased ${rms(drop)}")
    }

    @Test
    fun `WAV round trips`() {
        val pcm = ShortArray(1000) { (it * 31 % 2000 - 1000).toShort() }
        val (back, rate) = Wav.decode(Wav.encode(pcm, 16_000))
        assertEquals(16_000, rate)
        assertArrayEquals(pcm, back)
    }
}

class RecitationAnalyzerTest {
    private val syllable = Synthetic.fricative(120) + Synthetic.vowel(500)

    @Test
    fun `durations come from VAD and the ratio follows a held sound`() {
        val ref = Synthetic.mix(2000, listOf(400 to syllable))
        val user = Synthetic.mix(2600, listOf(500 to Synthetic.stretch(syllable, 1.5)))
        val a = RecitationAnalyzer.analyze(user, ref)
        assertEquals(Confidence.OK, a.confidence)
        assertEquals(1.5, a.durationRatio, 0.15)
        assertTrue(a.alignmentDistance in 0.0..1.0)
        assertEquals(a.referenceEnvelope.size, a.alignedUserEnvelope.size)
    }

    @Test
    fun `silence, clipping and noise are low confidence`() {
        val ref = Synthetic.mix(2000, listOf(400 to syllable))
        assertEquals(LowReason.NO_SPEECH, RecitationAnalyzer.analyze(ShortArray(32_000), ref).lowReason)

        val loud = Synthetic.mix(2000, listOf(400 to syllable.map { it * 6 }.toDoubleArray()))
        assertEquals(LowReason.CLIPPING, RecitationAnalyzer.analyze(loud, ref).lowReason)

        val noisy = Synthetic.mix(2000, listOf(400 to Synthetic.vowel(600, amp = 0.05)), noiseAmp = 0.03)
        val r = RecitationAnalyzer.analyze(noisy, ref)
        assertEquals(Confidence.LOW, r.confidence)
        assertEquals(0, r.userDurationMs) // no numbers from a low-confidence analysis
    }

    @Test
    fun `a three second clip is analysed quickly`() {
        val ref = Synthetic.mix(3000, listOf(600 to Synthetic.stretch(syllable, 3.0)))
        val user = Synthetic.mix(3000, listOf(500 to Synthetic.stretch(syllable, 3.3)))
        repeat(5) { RecitationAnalyzer.analyze(user, ref) } // warm up the JIT
        val times = List(10) { val t = System.nanoTime(); RecitationAnalyzer.analyze(user, ref); (System.nanoTime() - t) / 1e6 }.sorted()
        println("analyze 3 s + 3 s on the JVM: median ${"%.1f".format(times[5])} ms")
        assertTrue(times[5] < 150, "median ${times[5]} ms")
    }
}
