package `in`.qaida.app

import android.content.Context
import android.content.res.Resources
import `in`.qaida.app.data.backup.BackupRepository
import `in`.qaida.app.ui.common.withLocale
import android.util.Log
import `in`.qaida.app.audio.PlaybackController
import `in`.qaida.app.audio.Recorder
import `in`.qaida.app.audio.ReferenceDecoder
import `in`.qaida.app.audio.UiSounds
import `in`.qaida.app.data.content.AssetContentSource
import `in`.qaida.app.data.content.ContentRepository
import `in`.qaida.app.data.db.QaidaDatabase
import `in`.qaida.app.data.progress.ProgressRepository
import `in`.qaida.app.data.settings.SettingsRepository
import `in`.qaida.app.domain.SessionConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Manual dependency graph. One instance, owned by [QaidaApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: () -> Long = System::currentTimeMillis
    val random: Random = Random.Default

    val database = QaidaDatabase.create(context)
    val contentSource = AssetContentSource(context.assets)
    val contentRepository = ContentRepository(contentSource, database.contentDao())
    val progressRepository = ProgressRepository(database.progressDao())
    val playback = PlaybackController(context)
    val uiSounds = UiSounds(context)
    val recorder = Recorder(context)
    val referenceDecoder = ReferenceDecoder(context)

    val settingsRepository = SettingsRepository(context)
    val backupRepository = BackupRepository(database.progressDao(), settingsRepository)
    private val appContext = context.applicationContext

    /** Resources in the learner's chosen UI language, for text produced outside an activity. */
    suspend fun localizedResources(): Resources = appContext.withLocale(settingsRepository.current().uiLanguage.tag).resources

    suspend fun sessionConfig() = SessionConfig(durationMs = settingsRepository.current().sessionSeconds * 1000L)

    /** Completes once the bundled content is in Room; anything reading content awaits it. */
    val contentReady = CompletableDeferred<Unit>()

    fun start() {
        appScope.launch(Dispatchers.IO) {
            val imported = contentRepository.importIfChanged()
            Log.i(TAG, if (imported == null) "content up to date" else "imported $imported letters")
            contentReady.complete(Unit)
        }
    }

    private companion object {
        const val TAG = "Qaida"
    }
}
