package `in`.qaida.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Content tables: rebuilt from assets/content whenever the bundled pack changes.

@Entity(tableName = "letter")
data class LetterEntity(
    @PrimaryKey val id: String,
    /** Position in letters.json, which is the teaching order within a tier. */
    val ordinal: Int,
    val glyph: String,
    val formIsolated: String,
    val formInitial: String,
    val formMedial: String,
    val formFinal: String,
    val nameEn: String,
    val nameMl: String,
    val makhrajId: String,
    val makhrajNoteEn: String,
    val makhrajNoteMl: String,
    val unlockTier: Int,
)

@Entity(tableName = "letter_audio", primaryKeys = ["letterId", "context", "file"])
data class LetterAudioEntity(
    val letterId: String,
    val context: String,
    val voiceId: String,
    val file: String,
)

@Entity(tableName = "letter_confusable", primaryKeys = ["letterId", "otherId"])
data class LetterConfusableEntity(
    val letterId: String,
    val otherId: String,
    val rank: Int,
)

@Entity(tableName = "confusion_set_member", primaryKeys = ["setIndex", "letterId"])
data class ConfusionSetMemberEntity(
    val setIndex: Int,
    val letterId: String,
)

@Entity(tableName = "makhraj")
data class MakhrajEntity(
    @PrimaryKey val id: String,
    val diagram: String,
    val labelEn: String,
    val labelMl: String,
)

@Entity(tableName = "boss_line")
data class BossLineEntity(
    @PrimaryKey val tier: Int,
    val text: String,
    val audio: String?,
)

@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

// Progress tables: user state, never touched by a content import.

@Entity(tableName = "review_state", primaryKeys = ["letterId", "cardType"])
data class ReviewStateEntity(
    val letterId: String,
    val cardType: String,
    val stabilityHours: Double,
    val lastReviewedAt: Long,
)

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
)

@Entity(tableName = "answer_log", indices = [Index("answeredAt"), Index("letterId")])
data class AnswerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val answeredAt: Long,
    val letterId: String,
    val cardType: String,
    /** Graded cards only; null for PRODUCE, which is never graded. */
    val correct: Boolean?,
    /** PRODUCE self-rating: 0 needs work, 1 getting there, 2 sounds right. */
    val selfRating: Int?,
    val responseMs: Long,
    /** Logged for later tuning, never shown in the UI. */
    val alignmentDistance: Double?,
)

/** Tiers the learner has opened. Permanent: decay never re-locks a tier. Tier 1 is implicit. */
@Entity(tableName = "tier_unlock")
data class TierUnlockEntity(
    @PrimaryKey val tier: Int,
    val unlockedAt: Long,
)

/** A tier's boss line has been attempted; the tier shows gold permanently from then on. */
@Entity(tableName = "boss_attempt")
data class BossAttemptEntity(
    @PrimaryKey val tier: Int,
    val attemptedAt: Long,
)

/** Query result: cards answered on one local day (days since the epoch). */
data class DayCount(val day: Long, val count: Int)
