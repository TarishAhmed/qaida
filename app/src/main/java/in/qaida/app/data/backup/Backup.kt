package `in`.qaida.app.data.backup

import `in`.qaida.app.data.db.AnswerLogEntity
import `in`.qaida.app.data.db.BossAttemptEntity
import `in`.qaida.app.data.db.ProgressDao
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.data.db.SessionEntity
import `in`.qaida.app.data.db.TierUnlockEntity
import `in`.qaida.app.data.settings.Language
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.data.settings.SettingsRepository
import `in`.qaida.app.ui.theme.ScriptFace
import org.json.JSONArray
import org.json.JSONObject

class BackupFormatException(message: String) : Exception(message)

/** Everything that is the learner's own: progress tables and settings. Content is not included. */
data class Backup(
    val exportedAt: Long,
    val settings: Settings,
    val reviewStates: List<ReviewStateEntity>,
    val answers: List<AnswerLogEntity>,
    val sessions: List<SessionEntity>,
    val tierUnlocks: List<TierUnlockEntity>,
    val bossAttempts: List<BossAttemptEntity>,
)

/**
 * The JSON backup format, the app's only portability mechanism. Versioned so later releases can still
 * read old files; unknown fields are ignored and missing optional ones take defaults.
 */
object BackupCodec {
    const val FORMAT = "qaida-backup"
    const val VERSION = 1

    fun encode(b: Backup): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", b.exportedAt)
        put("settings", JSONObject().apply {
            put("uiLanguage", b.settings.uiLanguage.tag)
            put("instructionLanguage", b.settings.instructionLanguage.tag)
            put("scriptFace", b.settings.scriptFace.name)
            put("sessionSeconds", b.settings.sessionSeconds)
            put("textScale", b.settings.textScale.toDouble())
            put("reminderEnabled", b.settings.reminderEnabled)
            put("reminderMinuteOfDay", b.settings.reminderMinuteOfDay)
            put("micDeclined", b.settings.micDeclined)
        })
        put("reviewStates", JSONArray(b.reviewStates.map {
            JSONObject().put("letterId", it.letterId).put("cardType", it.cardType)
                .put("stabilityHours", it.stabilityHours).put("lastReviewedAt", it.lastReviewedAt)
        }))
        put("answers", JSONArray(b.answers.map {
            JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("answeredAt", it.answeredAt)
                .put("letterId", it.letterId).put("cardType", it.cardType)
                .put("correct", it.correct ?: JSONObject.NULL).put("selfRating", it.selfRating ?: JSONObject.NULL)
                .put("responseMs", it.responseMs).put("alignmentDistance", it.alignmentDistance ?: JSONObject.NULL)
        }))
        put("sessions", JSONArray(b.sessions.map {
            JSONObject().put("id", it.id).put("startedAt", it.startedAt).put("endedAt", it.endedAt ?: JSONObject.NULL)
        }))
        put("tierUnlocks", JSONArray(b.tierUnlocks.map { JSONObject().put("tier", it.tier).put("unlockedAt", it.unlockedAt) }))
        put("bossAttempts", JSONArray(b.bossAttempts.map { JSONObject().put("tier", it.tier).put("attemptedAt", it.attemptedAt) }))
    }.toString(1)

    fun decode(json: String): Backup {
        val o = try { JSONObject(json) } catch (e: org.json.JSONException) { throw BackupFormatException("not a JSON file") }
        if (o.optString("format") != FORMAT) throw BackupFormatException("not a Qaida backup")
        val version = o.optInt("version", -1)
        if (version !in 1..VERSION) throw BackupFormatException("backup version $version is newer than this app")
        return try {
            val s = o.getJSONObject("settings")
            val d = Settings()
            Backup(
                exportedAt = o.getLong("exportedAt"),
                settings = Settings(
                    uiLanguage = Language.entries.firstOrNull { it.tag == s.optString("uiLanguage") } ?: d.uiLanguage,
                    instructionLanguage = Language.entries.firstOrNull { it.tag == s.optString("instructionLanguage") } ?: d.instructionLanguage,
                    scriptFace = ScriptFace.entries.firstOrNull { it.name == s.optString("scriptFace") } ?: d.scriptFace,
                    sessionSeconds = s.optInt("sessionSeconds", d.sessionSeconds).takeIf { it in Settings.SESSION_LENGTHS } ?: d.sessionSeconds,
                    textScale = s.optDouble("textScale", d.textScale.toDouble()).toFloat().takeIf { it in Settings.TEXT_SCALES } ?: d.textScale,
                    reminderEnabled = s.optBoolean("reminderEnabled", d.reminderEnabled),
                    reminderMinuteOfDay = s.optInt("reminderMinuteOfDay", d.reminderMinuteOfDay).coerceIn(0, 24 * 60 - 1),
                    micDeclined = s.optBoolean("micDeclined", d.micDeclined),
                ),
                reviewStates = o.getJSONArray("reviewStates").objects().map {
                    ReviewStateEntity(it.getString("letterId"), it.getString("cardType"), it.getDouble("stabilityHours"), it.getLong("lastReviewedAt"))
                },
                answers = o.getJSONArray("answers").objects().map {
                    AnswerLogEntity(
                        id = it.getLong("id"), sessionId = it.getLong("sessionId"), answeredAt = it.getLong("answeredAt"),
                        letterId = it.getString("letterId"), cardType = it.getString("cardType"),
                        correct = if (it.isNull("correct")) null else it.getBoolean("correct"),
                        selfRating = if (it.isNull("selfRating")) null else it.getInt("selfRating"),
                        responseMs = it.getLong("responseMs"),
                        alignmentDistance = if (it.isNull("alignmentDistance")) null else it.getDouble("alignmentDistance"),
                    )
                },
                sessions = o.getJSONArray("sessions").objects().map {
                    SessionEntity(it.getLong("id"), it.getLong("startedAt"), if (it.isNull("endedAt")) null else it.getLong("endedAt"))
                },
                tierUnlocks = o.getJSONArray("tierUnlocks").objects().map { TierUnlockEntity(it.getInt("tier"), it.getLong("unlockedAt")) },
                bossAttempts = o.getJSONArray("bossAttempts").objects().map { BossAttemptEntity(it.getInt("tier"), it.getLong("attemptedAt")) },
            )
        } catch (e: org.json.JSONException) {
            throw BackupFormatException("backup is damaged: ${e.message}")
        }
    }

    private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
}

class BackupRepository(private val dao: ProgressDao, private val settings: SettingsRepository) {
    suspend fun export(now: Long): String = BackupCodec.encode(
        Backup(now, settings.current(), dao.reviewStates(), dao.answers(), dao.sessions(), dao.tierUnlocks(), dao.bossAttempts()),
    )

    /** Validates fully before touching anything, then replaces all progress and settings. */
    suspend fun import(json: String) {
        val b = BackupCodec.decode(json)
        dao.replaceProgress(b.reviewStates, b.answers, b.sessions, b.tierUnlocks, b.bossAttempts)
        settings.update { b.settings }
    }
}
