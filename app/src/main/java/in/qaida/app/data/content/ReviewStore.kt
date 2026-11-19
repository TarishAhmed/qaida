package `in`.qaida.app.data.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * A reviewer's verdicts on the bundled recordings, kept in app storage while they work through them and
 * exported as the file tools/build_content_pack.py --apply-review reads.
 */
class ReviewStore(context: Context) {
    private val file = File(context.filesDir, "content_review.json")

    suspend fun load(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyMap()
        val o = JSONObject(file.readText())
        o.keys().asSequence().associateWith { o.getBoolean(it) }
    }

    suspend fun save(verdicts: Map<String, Boolean>) = withContext(Dispatchers.IO) {
        file.writeText(JSONObject(verdicts).toString())
    }

    fun export(verdicts: Map<String, Boolean>, reviewer: String, now: Long): String =
        JSONObject().put("reviewer", reviewer).put("reviewedAt", now).put("clips", JSONObject(verdicts)).toString(1)
}
