package `in`.qaida.app

import android.content.Context
import android.util.Log
import `in`.qaida.app.data.content.AssetContentSource
import `in`.qaida.app.data.content.ContentRepository
import `in`.qaida.app.data.db.QaidaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency graph. One instance, owned by [QaidaApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = QaidaDatabase.create(context)
    val contentRepository = ContentRepository(AssetContentSource(context.assets), database.contentDao())

    fun start() {
        appScope.launch(Dispatchers.IO) {
            val imported = contentRepository.importIfChanged()
            Log.i(TAG, if (imported == null) "content up to date" else "imported $imported letters")
        }
    }

    private companion object {
        const val TAG = "Qaida"
    }
}
