package `in`.qaida.app.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import `in`.qaida.app.data.content.ContentPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's one ExoPlayer. A session's clips are read into memory up front so that a replay is a seek,
 * not a reload: a stall between a prompt and its repeat breaks the discrimination task.
 *
 * Letter audio uses the media stream, so it plays even with the ringer silenced: the learner tapped play.
 */
@OptIn(UnstableApi::class)
class PlaybackController(private val context: Context) {
    private val clips = ConcurrentHashMap<String, ByteArray>()
    private var current: String? = null

    private val mediaSources by lazy {
        DefaultMediaSourceFactory(
            DataSource.Factory {
                ByteArrayDataSource { uri -> clips[uri.lastPathSegment] ?: throw IOException("clip not preloaded: $uri") }
            },
        )
    }

    private val playerLazy = lazy {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSources)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .build()
    }
    private val player: ExoPlayer by playerLazy

    /** Loads exactly [files] into memory, dropping clips from earlier sessions. */
    suspend fun preload(files: Collection<String>) {
        val wanted = files.toSet()
        clips.keys.retainAll(wanted)
        withContext(Dispatchers.IO) {
            for (f in wanted) if (!clips.containsKey(f)) {
                clips[f] = context.assets.open(ContentPaths.audio(f)).use { it.readBytes() }
            }
        }
    }

    /** Adds an in-memory clip (a recording, or a trimmed reference) under [name] for playback. */
    fun putClip(name: String, bytes: ByteArray) {
        clips[name] = bytes
        if (current == name) current = null
    }

    /** Prepares [file] without playing it, so the first play of a card starts immediately. */
    fun cue(file: String) {
        if (current == file) return
        current = file
        player.setMediaItem(MediaItem.fromUri(Uri.parse("$SCHEME:///$file")))
        player.prepare()
    }

    fun play(file: String, volume: Float = 1f) {
        cue(file)
        player.volume = volume
        player.seekTo(0, 0)
        player.play()
    }

    /** Plays [files] one after another with [gapMs] of silence between, so two clips are heard as two. */
    fun playSequence(files: List<String>, gapMs: Long = SEQUENCE_GAP_MS, volume: Float = 1f) {
        if (files.size == 1) return play(files.single(), volume)
        current = null
        val sources = files.flatMapIndexed { i, f ->
            val clip = mediaSources.createMediaSource(MediaItem.fromUri(Uri.parse("$SCHEME:///$f")))
            if (i == 0) listOf(clip) else listOf(SilenceMediaSource(gapMs * 1000), clip)
        }
        player.setMediaSources(sources)
        player.prepare()
        player.volume = volume
        player.seekTo(0, 0)
        player.play()
    }

    fun stop() {
        if (playerLazy.isInitialized()) player.pause()
    }

    companion object {
        private const val SCHEME = "qaida-clip"
        const val SEQUENCE_GAP_MS = 400L
    }
}
