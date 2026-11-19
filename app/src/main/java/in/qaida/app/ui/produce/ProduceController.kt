package `in`.qaida.app.ui.produce

import android.util.Log
import `in`.qaida.app.AppContainer
import `in`.qaida.app.domain.AudioClip
import `in`.qaida.app.dsp.Analysis
import `in`.qaida.app.dsp.Confidence
import `in`.qaida.app.dsp.RecitationAnalyzer
import `in`.qaida.app.dsp.Wav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ProducePhase { IDLE, RECORDING, ANALYSING, DONE }

data class ProduceState(
    val phase: ProducePhase = ProducePhase.IDLE,
    /** Present once a recording has been analysed against a reference. */
    val analysis: Analysis? = null,
    val hasRecording: Boolean = false,
    /** Which clip the A/B button plays next. */
    val nextIsUser: Boolean = true,
)

/**
 * Record, analyse against the reference, and A/B both, for one PRODUCE prompt. Shared by session cards and
 * the letter detail screen. Measurements are facts for the learner; nothing here judges the recitation.
 */
class ProduceController(
    private val c: AppContainer,
    private val scope: CoroutineScope,
    private val reference: AudioClip?,
) {
    private val _state = MutableStateFlow(ProduceState())
    val state: StateFlow<ProduceState> = _state.asStateFlow()

    @Volatile private var stopRequested = false
    private var job: Job? = null
    private val userClip = "rec_current_${System.identityHashCode(this)}.wav"
    private val refClip = "ref_trimmed_${System.identityHashCode(this)}.wav"

    fun hasPermission() = c.recorder.hasPermission()

    fun playReference() {
        reference?.let { c.playback.play(it.file) }
    }

    fun startRecording() {
        if (_state.value.phase == ProducePhase.RECORDING || !c.recorder.hasPermission()) return
        stopRequested = false
        c.playback.stop()
        _state.value = ProduceState(phase = ProducePhase.RECORDING)
        job = scope.launch {
            val pcm = try {
                c.recorder.record { stopRequested }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "recording failed", e)
                _state.value = ProduceState()
                return@launch
            }
            c.recorder.save(pcm)
            _state.update { it.copy(phase = ProducePhase.ANALYSING, hasRecording = true) }
            val analysis = reference?.let { ref ->
                val refPcm = c.referenceDecoder.decode(ref.file)
                withContext(Dispatchers.Default) { RecitationAnalyzer.analyze(pcm, refPcm) }
                    .also { a -> prepareAB(pcm, refPcm, a) }
            }
            if (analysis == null) c.playback.putClip(userClip, Wav.encode(pcm))
            _state.update { it.copy(phase = ProducePhase.DONE, analysis = analysis, nextIsUser = true) }
        }
    }

    fun stopRecording() {
        stopRequested = true
    }

    /** Both clips cut to their own speech boundaries, so the A/B comparison starts and ends alike. */
    private fun prepareAB(user: ShortArray, ref: ShortArray, a: Analysis) {
        val u = a.userSegment?.let { RecitationAnalyzer.trim(user, it) } ?: user
        val r = a.referenceSegment?.let { RecitationAnalyzer.trim(ref, it) } ?: ref
        c.playback.putClip(userClip, Wav.encode(if (a.confidence == Confidence.OK) u else user))
        c.playback.putClip(refClip, Wav.encode(if (a.confidence == Confidence.OK) r else ref))
    }

    /** A/B: a single tap plays the learner's clip, the next the reference, and so on. */
    fun playAB() {
        val s = _state.value
        if (!s.hasRecording) return
        val playUser = s.nextIsUser || reference == null
        if (playUser) c.playback.play(userClip)
        else if (s.analysis != null) c.playback.play(refClip) else playReference()
        _state.update { it.copy(nextIsUser = !playUser) }
    }

    fun reset() {
        job?.cancel()
        _state.value = ProduceState()
    }

    private companion object {
        const val TAG = "Qaida"
    }
}
