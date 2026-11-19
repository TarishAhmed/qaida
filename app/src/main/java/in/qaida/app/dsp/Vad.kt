package `in`.qaida.app.dsp

import kotlin.math.log10

data class SpeechSegment(val onsetMs: Int, val offsetMs: Int) {
    val durationMs get() = offsetMs - onsetMs
}

data class VadResult(
    /** The span from the first to the last speech frame, or null if no clear speech was found. */
    val segment: SpeechSegment?,
    val noiseFloorDb: Double,
    /** Mean energy of the speech frames; equal to the noise floor when there is no speech. */
    val speechDb: Double,
) {
    val snrDb get() = speechDb - noiseFloorDb
}

/**
 * Energy + zero-crossing voice activity detection on 25 ms / 10 ms frames.
 *
 * The noise floor is estimated from the first 200 ms (the learner taps record, then speaks), bounded by the
 * quietest tenth of all frames in case speech starts early. A frame is speech when its energy clears the
 * floor by [ENERGY_MARGIN_DB], or by [FRICATIVE_MARGIN_DB] with a high zero-crossing rate: the hiss of
 * sin, shin and fa is quiet but crosses zero constantly. Gaps shorter than [BRIDGE_MS] are bridged
 * (stop closures inside a syllable) and runs shorter than [MIN_SPEECH_MS] are dropped (clicks).
 */
object Vad {
    const val NOISE_WINDOW_MS = 200
    const val ENERGY_MARGIN_DB = 10.0
    const val FRICATIVE_MARGIN_DB = 4.0
    const val FRICATIVE_ZCR = 0.25
    /** Below this a frame is silence whatever the floor: guards against a perfectly quiet recording. */
    const val ABSOLUTE_FLOOR_DB = -65.0
    const val BRIDGE_MS = 120
    const val MIN_SPEECH_MS = 60

    fun detect(pcm: ShortArray): VadResult {
        val frames = Framing.frameCount(pcm.size)
        if (frames == 0) return VadResult(null, ABSOLUTE_FLOOR_DB, ABSOLUTE_FLOOR_DB)

        val energy = DoubleArray(frames)
        val zcr = DoubleArray(frames)
        for (t in 0 until frames) {
            val start = t * Framing.HOP
            var sum = 0.0
            var crossings = 0
            for (i in start until start + Framing.WINDOW) {
                val x = pcm[i] / 32768.0
                sum += x * x
                if (i > start && (pcm[i] >= 0) != (pcm[i - 1] >= 0)) crossings++
            }
            energy[t] = 10 * log10(sum / Framing.WINDOW + 1e-12)
            zcr[t] = crossings.toDouble() / Framing.WINDOW
        }

        val leading = (NOISE_WINDOW_MS / Framing.FRAME_MS).coerceAtMost(frames)
        val leadingMean = energy.take(leading).average()
        val quietest = energy.sorted()[frames / 10]
        val floor = maxOf(minOf(leadingMean, quietest + 3.0), ABSOLUTE_FLOOR_DB - ENERGY_MARGIN_DB)

        val speech = BooleanArray(frames) { t ->
            energy[t] > floor + ENERGY_MARGIN_DB ||
                (energy[t] > floor + FRICATIVE_MARGIN_DB && zcr[t] > FRICATIVE_ZCR && energy[t] > ABSOLUTE_FLOOR_DB)
        }
        bridgeGaps(speech, BRIDGE_MS / Framing.FRAME_MS)
        dropShortRuns(speech, MIN_SPEECH_MS / Framing.FRAME_MS)

        val first = speech.indexOfFirst { it }
        if (first < 0) return VadResult(null, floor, floor)
        val last = speech.indexOfLast { it }
        val speechDb = (first..last).filter { speech[it] }.map { energy[it] }.average()
        // A 25 ms frame that only overlaps the edge of speech still counts as speech, so frame spans would
        // overstate the segment. Each frame stands for its centre +- half a hop instead.
        val centre = Framing.WINDOW * 1000 / Framing.SAMPLE_RATE / 2.0
        val halfHop = Framing.FRAME_MS / 2.0
        val segment = SpeechSegment(
            (Framing.frameToMs(first) + centre - halfHop).toInt(),
            (Framing.frameToMs(last) + centre + halfHop).toInt(),
        )
        return VadResult(segment, floor, speechDb)
    }

    private fun bridgeGaps(speech: BooleanArray, maxGap: Int) {
        var lastSpeech = -1
        for (t in speech.indices) {
            if (!speech[t]) continue
            if (lastSpeech >= 0 && t - lastSpeech - 1 in 1..maxGap) for (g in lastSpeech + 1 until t) speech[g] = true
            lastSpeech = t
        }
    }

    private fun dropShortRuns(speech: BooleanArray, minRun: Int) {
        var t = 0
        while (t < speech.size) {
            if (!speech[t]) { t++; continue }
            val start = t
            while (t < speech.size && speech[t]) t++
            if (t - start < minRun) for (i in start until t) speech[i] = false
        }
    }
}
