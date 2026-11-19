package `in`.qaida.app

import android.content.Context
import android.util.Log
import `in`.qaida.app.audio.PlaybackController
import `in`.qaida.app.audio.UiSounds
import `in`.qaida.app.data.content.AssetContentSource
import `in`.qaida.app.data.content.ContentRepository
import `in`.qaida.app.data.db.QaidaDatabase
import `in`.qaida.app.data.progress.ProgressRepository
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
    val contentRepository = ContentRepository(AssetContentSource(context.assets), database.contentDao())
    val progressRepository = ProgressRepository(database.progressDao())
    val playback = PlaybackController(context)
    val uiSounds = UiSounds(context)

    // Settings arrive in milestone 7; until then the spec's defaults.
    val sessionConfig = SessionConfig()

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
