package `in`.qaida.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import `in`.qaida.app.dsp.Framing
import `in`.qaida.app.dsp.Wav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Raw 16 kHz mono 16-bit capture. VOICE_RECOGNITION applies less processing than MIC (no aggressive noise
 * suppression or AGC), which keeps durations honest. Recordings stay in app-internal cache and never leave
 * the device; only the most recent [KEEP] are kept.
 */
class Recorder(private val context: Context) {
    private val dir get() = File(context.cacheDir, "recordings").apply { mkdirs() }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Records until [shouldStop] returns true or [maxMs] elapses. Call only with the permission granted. */
    @SuppressLint("MissingPermission")
    suspend fun record(maxMs: Int = MAX_MS, shouldStop: () -> Boolean): ShortArray = withContext(Dispatchers.IO) {
        val rate = Framing.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate / 5 * 2),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
        val out = ShortArray(rate * maxMs / 1000)
        val chunk = ShortArray(rate / 20)
        var n = 0
        record.startRecording()
        try {
            while (n < out.size && !shouldStop()) {
                ensureActive()
                val read = record.read(chunk, 0, minOf(chunk.size, out.size - n))
                if (read < 0) break
                chunk.copyInto(out, n, 0, read)
                n += read
            }
        } finally {
            record.stop()
            record.release()
        }
        out.copyOf(n)
    }

    suspend fun save(pcm: ShortArray): File = withContext(Dispatchers.IO) {
        File(dir, "rec_${System.currentTimeMillis()}.wav").apply { writeBytes(Wav.encode(pcm)) }
    }

    /** Deletes all but the newest [KEEP] recordings. Run at the end of every session. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.startsWith("rec_") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    /** Removes every recording, for Reset progress. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }

    companion object {
        const val MAX_MS = 10_000
        const val KEEP = 20
    }
}
