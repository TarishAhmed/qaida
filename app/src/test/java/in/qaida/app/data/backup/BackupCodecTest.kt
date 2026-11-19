package `in`.qaida.app.data.backup

import `in`.qaida.app.data.db.AnswerLogEntity
import `in`.qaida.app.data.db.BossAttemptEntity
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.data.db.SessionEntity
import `in`.qaida.app.data.db.TierUnlockEntity
import `in`.qaida.app.data.settings.Language
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.ui.theme.ScriptFace
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupCodecTest {
    private val backup = Backup(
        exportedAt = 1_700_000_000_000,
        settings = Settings(Language.ML, Language.EN, ScriptFace.NASKH, 180, 1.15f, true, 21 * 60 + 30, micDeclined = true),
        reviewStates = listOf(ReviewStateEntity("a", "HEAR_PICK", 37.25, 1_699_999_000_000)),
        answers = listOf(
            AnswerLogEntity(1, 1, 1_699_999_000_000, "a", "HEAR_PICK", true, null, 1200, null),
            AnswerLogEntity(2, 1, 1_699_999_100_000, "a", "PRODUCE", null, 1, 5400, 0.231),
        ),
        sessions = listOf(SessionEntity(1, 1_699_998_000_000, null)),
        tierUnlocks = listOf(TierUnlockEntity(1, 1_699_990_000_000), TierUnlockEntity(2, 1_699_995_000_000)),
        bossAttempts = listOf(BossAttemptEntity(1, 1_699_996_000_000)),
    )

    @Test
    fun `round trips exactly`() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun `rejects files that are not Qaida backups or are from a newer app`() {
        assertThrows<BackupFormatException> { BackupCodec.decode("not json") }
        assertThrows<BackupFormatException> { BackupCodec.decode("""{"format":"something-else"}""") }
        val newer = JSONObject(BackupCodec.encode(backup)).put("version", BackupCodec.VERSION + 1).toString()
        assertThrows<BackupFormatException> { BackupCodec.decode(newer) }
        val damaged = JSONObject(BackupCodec.encode(backup)).apply { remove("answers") }.toString()
        assertThrows<BackupFormatException> { BackupCodec.decode(damaged) }
    }

    @Test
    fun `missing optional settings fall back to defaults`() {
        val o = JSONObject(BackupCodec.encode(backup))
        o.getJSONObject("settings").remove("micDeclined")
        o.getJSONObject("settings").put("sessionSeconds", 45)
        val decoded = BackupCodec.decode(o.toString())
        assertEquals(false, decoded.settings.micDeclined)
        assertEquals(90, decoded.settings.sessionSeconds)
    }
}
