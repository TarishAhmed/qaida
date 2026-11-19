package `in`.qaida.app.dsp

import kotlin.math.sqrt

enum class Confidence { OK, LOW }

enum class LowReason { NO_SPEECH, CLIPPING, NOISY }

/**
 * Measurable facts about one recording against its reference. Nothing here is a verdict: the UI may show
 * durations and the aligned waveforms when [confidence] is OK, and nothing numeric when it is LOW.
 * [alignmentDistance] is logged for tuning and never shown.
 */
data class Analysis(
    val confidence: Confidence,
    val lowReason: LowReason?,
    val userSegment: SpeechSegment?,
    val referenceSegment: SpeechSegment?,
    val userDurationMs: Int,
    val referenceDurationMs: Int,
    val durationRatio: Double,
    val alignmentDistance: Double,
    /** Per-10-ms RMS of the user's speech, warped onto the reference timeline by the DTW path. */
    val alignedUserEnvelope: DoubleArray,
    /** Per-10-ms RMS of the reference speech. */
    val referenceEnvelope: DoubleArray,
)

object RecitationAnalyzer {
    /** Below this speech-to-noise ratio the measurement is not trustworthy. */
    const val MIN_SNR_DB = 12.0
    /** Fraction of samples at full scale that counts as clipped. */
    const val CLIP_FRACTION = 0.001
    private const val CLIP_LEVEL = 32_000
    /** Context kept around each speech segment before feature extraction. */
    private const val MARGIN_MS = 30

    private val mfcc = Mfcc()

    fun analyze(user: ShortArray, reference: ShortArray): Analysis {
        val userVad = Vad.detect(user)
        val refVad = Vad.detect(reference)
        val us = userVad.segment
        val rs = refVad.segment
        val reason = when {
            us == null || rs == null -> LowReason.NO_SPEECH
            clipped(user) -> LowReason.CLIPPING
            userVad.snrDb < MIN_SNR_DB -> LowReason.NOISY
            else -> null
        }
        if (reason != null) return low(reason, us, rs)

        val userSpeech = trim(user, us!!)
        val refSpeech = trim(reference, rs!!)
        val a = mfcc.compute(userSpeech)
        val b = mfcc.compute(refSpeech)
        if (a.isEmpty() || b.isEmpty()) return low(LowReason.NO_SPEECH, us, rs)
        val dtw = Dtw.align(a, b)

        val userEnv = envelope(userSpeech)
        val refEnv = envelope(refSpeech)
        val warped = DoubleArray(refEnv.size)
        val counts = IntArray(refEnv.size)
        for ((i, j) in dtw.path) if (i < userEnv.size && j < refEnv.size) { warped[j] += userEnv[i]; counts[j]++ }
        for (j in warped.indices) if (counts[j] > 0) warped[j] /= counts[j]

        return Analysis(
            confidence = Confidence.OK, lowReason = null, userSegment = us, referenceSegment = rs,
            userDurationMs = us.durationMs, referenceDurationMs = rs.durationMs,
            durationRatio = us.durationMs.toDouble() / rs.durationMs,
            alignmentDistance = dtw.distance,
            alignedUserEnvelope = warped, referenceEnvelope = refEnv,
        )
    }

    private fun low(reason: LowReason, us: SpeechSegment?, rs: SpeechSegment?) = Analysis(
        Confidence.LOW, reason, us, rs, 0, 0, 0.0, 0.0, DoubleArray(0), DoubleArray(0),
    )

    fun clipped(pcm: ShortArray): Boolean {
        var n = 0
        for (s in pcm) if (s >= CLIP_LEVEL || s <= -CLIP_LEVEL) n++
        return n > pcm.size * CLIP_FRACTION
    }

    /** The speech segment plus a small margin, clamped to the recording. */
    fun trim(pcm: ShortArray, seg: SpeechSegment): ShortArray {
        val perMs = Framing.SAMPLE_RATE / 1000
        val from = ((seg.onsetMs - MARGIN_MS) * perMs).coerceIn(0, pcm.size)
        val to = ((seg.offsetMs + MARGIN_MS) * perMs).coerceIn(from, pcm.size)
        return pcm.copyOfRange(from, to)
    }

    private fun envelope(pcm: ShortArray): DoubleArray {
        val frames = Framing.frameCount(pcm.size)
        return DoubleArray(frames) { t ->
            var s = 0.0
            val start = t * Framing.HOP
            for (i in start until start + Framing.WINDOW) { val x = pcm[i] / 32768.0; s += x * x }
            sqrt(s / Framing.WINDOW)
        }
    }
}
