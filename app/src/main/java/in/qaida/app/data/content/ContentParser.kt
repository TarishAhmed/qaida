package `in`.qaida.app.data.content

import `in`.qaida.app.domain.BossLine
import `in`.qaida.app.domain.ConfusionSet
import `in`.qaida.app.domain.ContentPack
import `in`.qaida.app.domain.Letter
import `in`.qaida.app.domain.LetterForms
import `in`.qaida.app.domain.Makhraj
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ContentFormatException(message: String) : Exception(message)

/**
 * Parses the three content files into a [ContentPack]. Structural problems (wrong types, missing
 * keys) throw [ContentFormatException]; semantic checks live in [AssetValidator].
 */
object ContentParser {

    fun parse(lettersJson: String, confusionSetsJson: String, makhrajJson: String, bossJson: String? = null) = ContentPack(
        letters = parseArray(lettersJson, "letters.json") { obj, at -> parseLetter(obj, at) },
        confusionSets = parseConfusionSets(confusionSetsJson),
        makhraj = parseArray(makhrajJson, "makhraj.json") { obj, at -> parseMakhraj(obj, at) },
        bossLines = bossJson?.let { json -> parseArray(json, "boss.json") { obj, at -> parseBoss(obj, at) } }.orEmpty(),
    )

    private fun parseBoss(o: JSONObject, at: String) = BossLine(
        tier = o.int("tier", at),
        text = o.str("text", at),
        audio = if (o.has("audio")) o.str("audio", at) else null,
    )

    private fun parseLetter(o: JSONObject, at: String): Letter {
        val forms = o.obj("forms", at)
        val audio = o.obj("audio", at)
        return Letter(
            id = o.str("id", at),
            glyph = o.str("glyph", at),
            forms = LetterForms(
                isolated = forms.str("isolated", "$at.forms"),
                initial = forms.str("initial", "$at.forms"),
                medial = forms.str("medial", "$at.forms"),
                final = forms.str("final", "$at.forms"),
            ),
            nameEn = o.str("nameEn", at),
            nameMl = o.str("nameMl", at),
            makhrajId = o.str("makhrajId", at),
            makhrajNoteEn = o.str("makhrajNoteEn", at),
            makhrajNoteMl = o.str("makhrajNoteMl", at),
            confusableWith = o.strings("confusableWith", at),
            audio = audio.keys().asSequence().associateWith { audio.strings(it, "$at.audio") },
            unlockTier = o.int("unlockTier", at),
        )
    }

    private fun parseMakhraj(o: JSONObject, at: String) = Makhraj(
        id = o.str("id", at),
        diagram = o.str("diagram", at),
        labelEn = o.str("labelEn", at),
        labelMl = o.str("labelMl", at),
    )

    private fun parseConfusionSets(json: String): List<ConfusionSet> {
        val root = array(json, "confusion_sets.json")
        return List(root.length()) { i ->
            val at = "confusion_sets.json[$i]"
            val set = root.optJSONArray(i) ?: throw ContentFormatException("$at: expected an array of letter ids")
            ConfusionSet(List(set.length()) { j -> set.optString(j).ifEmpty { throw ContentFormatException("$at[$j]: expected a letter id") } })
        }
    }

    private fun <T> parseArray(json: String, file: String, item: (JSONObject, String) -> T): List<T> {
        val root = array(json, file)
        return List(root.length()) { i ->
            val o = root.optJSONObject(i) ?: throw ContentFormatException("$file[$i]: expected an object")
            item(o, "$file[$i]")
        }
    }

    private fun array(json: String, file: String) = try {
        JSONArray(json)
    } catch (e: JSONException) {
        throw ContentFormatException("$file: ${e.message}")
    }

    private fun JSONObject.str(key: String, at: String): String {
        val v = opt(key)
        if (v !is String) throw ContentFormatException("$at.$key: expected a string")
        return v
    }

    private fun JSONObject.int(key: String, at: String): Int {
        val v = opt(key)
        if (v !is Int) throw ContentFormatException("$at.$key: expected an integer")
        return v
    }

    private fun JSONObject.obj(key: String, at: String): JSONObject =
        optJSONObject(key) ?: throw ContentFormatException("$at.$key: expected an object")

    private fun JSONObject.strings(key: String, at: String): List<String> {
        val a = optJSONArray(key) ?: throw ContentFormatException("$at.$key: expected an array of strings")
        return List(a.length()) { i ->
            a.opt(i) as? String ?: throw ContentFormatException("$at.$key[$i]: expected a string")
        }
    }
}
