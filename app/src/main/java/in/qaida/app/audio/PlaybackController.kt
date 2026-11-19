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

    private val player: ExoPlayer by lazy {
        val dataSources = DataSource.Factory {
            ByteArrayDataSource { uri -> clips[uri.lastPathSegment] ?: throw IOException("clip not preloaded: $uri") }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .build()
    }

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
        player.seekTo(0)
        player.play()
    }

    fun stop() {
        if (current != null) player.pause()
    }

    private companion object {
        const val SCHEME = "qaida-clip"
    }
}
