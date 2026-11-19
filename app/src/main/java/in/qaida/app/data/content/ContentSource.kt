package `in`.qaida.app.data.content

import android.content.res.AssetManager
import java.io.FileNotFoundException

/** Read access to the bundled asset tree, so the loader and validator run both on device and in JVM tests. */
interface ContentSource {
    fun readText(path: String): String
    fun exists(path: String): Boolean
}

object ContentPaths {
    const val LETTERS = "content/letters.json"
    const val CONFUSION_SETS = "content/confusion_sets.json"
    const val MAKHRAJ = "content/makhraj.json"
    /** Optional: one boss line per tier. */
    const val BOSS = "content/boss.json"
    const val AUDIO_DIR = "audio"

    fun audio(file: String) = "$AUDIO_DIR/$file"

    /** Optional spoken instruction for a card type, in the instruction language ("en" or "ml"). */
    fun instruction(cardType: String, language: String) = "instr_${cardType.lowercase()}_$language.opus"
}

class AssetContentSource(private val assets: AssetManager) : ContentSource {
    private val listings = HashMap<String, Set<String>>()

    override fun readText(path: String): String =
        assets.open(path).bufferedReader().use { it.readText() }

    override fun exists(path: String): Boolean {
        val dir = path.substringBeforeLast('/', "")
        val names = listings.getOrPut(dir) { assets.list(dir)?.toSet().orEmpty() }
        return path.substringAfterLast('/') in names
    }
}

fun ContentSource.loadPack() = try {
    ContentParser.parse(
        lettersJson = readText(ContentPaths.LETTERS),
        confusionSetsJson = readText(ContentPaths.CONFUSION_SETS),
        makhrajJson = readText(ContentPaths.MAKHRAJ),
        bossJson = if (exists(ContentPaths.BOSS)) readText(ContentPaths.BOSS) else null,
    )
} catch (e: FileNotFoundException) {
    throw ContentFormatException("missing content file: ${e.message}")
}
