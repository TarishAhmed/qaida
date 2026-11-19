package `in`.qaida.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import `in`.qaida.app.R

/**
 * The app's only sound effect: a soft tick on a correct answer. An incorrect answer is silent, by design.
 * Unlike letter audio this respects the ringer: no tick when the phone is on silent or vibrate.
 */
class UiSounds(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val tick = pool.load(context, R.raw.tick, 1)

    fun correct() {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        pool.play(tick, 1f, 1f, 1, 0, 1f)
    }
}
