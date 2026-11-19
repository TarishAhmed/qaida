package `in`.qaida.app.data.content

import org.json.JSONObject

/**
 * Whether a fluent reader has confirmed every recording is the letter it is filed under. Recordings from
 * open sources ship unverified; release builds refuse to package until this is complete.
 */
data class ContentVerification(val verified: Set<String>, val clips: Set<String>) {
    val complete get() = clips.isNotEmpty() && verified.containsAll(clips)
    val pending get() = clips - verified

    companion object {
        /** [verificationJson] is content/verification.json, or null when the pack has none. */
        fun of(clips: Set<String>, verificationJson: String?): ContentVerification {
            val verified = verificationJson?.let { json ->
                val o = JSONObject(json).getJSONObject("clips")
                o.keys().asSequence().filter { o.getBoolean(it) }.toSet()
            }.orEmpty()
            return ContentVerification(verified, clips)
        }
    }
}
