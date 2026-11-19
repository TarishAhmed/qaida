package `in`.qaida.app.data.progress

import `in`.qaida.app.data.db.AnswerLogEntity
import `in`.qaida.app.data.db.BossAttemptEntity
import `in`.qaida.app.data.db.ProgressDao
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.data.db.SessionEntity
import `in`.qaida.app.data.db.TierUnlockEntity
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.domain.Progression
import `in`.qaida.app.domain.CardType
import `in`.qaida.app.domain.ItemKey
import `in`.qaida.app.domain.ReviewState
import `in`.qaida.app.domain.SelfRating

class ProgressRepository(private val dao: ProgressDao) {

    suspend fun reviewStates(): Map<ItemKey, ReviewState> =
        dao.reviewStates().mapNotNull { e ->
            val type = CardType.entries.firstOrNull { it.name == e.cardType } ?: return@mapNotNull null
            ItemKey(e.letterId, type) to ReviewState(e.stabilityHours, e.lastReviewedAt)
        }.toMap()

    /** Unlocked tiers, after opening any that the learner has just earned. Tier 1 is always included. */
    suspend fun refreshUnlockedTiers(catalog: List<CatalogLetter>, states: Map<ItemKey, ReviewState>, now: Long): Set<Int> {
        val stored = dao.unlockedTiers().toSet()
        val added = Progression.newlyUnlocked(catalog, states, stored, now)
        if (added.isNotEmpty()) dao.insertTierUnlocks(added.map { TierUnlockEntity(it, now) })
        return stored + added + 1
    }

    suspend fun bossAttemptedTiers(): Set<Int> = dao.bossAttemptedTiers().toSet()

    suspend fun markBossAttempted(tier: Int, now: Long) = dao.insertBossAttempt(BossAttemptEntity(tier, now))

    /** Persists a PRODUCE self-rating. alignmentDistance is kept for tuning only and never shown. */
    suspend fun recordSelfRated(
        sessionId: Long,
        item: ItemKey,
        newState: ReviewState,
        rating: SelfRating,
        alignmentDistance: Double?,
        responseMs: Long,
        now: Long,
    ) {
        dao.upsertReviewState(
            ReviewStateEntity(item.letterId, item.cardType.name, newState.stabilityHours, newState.lastReviewedAt ?: now),
        )
        dao.insertAnswer(
            AnswerLogEntity(
                sessionId = sessionId, answeredAt = now, letterId = item.letterId, cardType = item.cardType.name,
                correct = null, selfRating = rating.code, responseMs = responseMs, alignmentDistance = alignmentDistance,
            ),
        )
    }

    suspend fun startSession(now: Long): Long = dao.insertSession(SessionEntity(startedAt = now, endedAt = null))

    suspend fun endSession(sessionId: Long, now: Long) = dao.endSession(sessionId, now)

    /** Persists a graded answer: the new scheduler state and one answer-log row. */
    suspend fun recordGraded(
        sessionId: Long,
        item: ItemKey,
        newState: ReviewState,
        correct: Boolean,
        responseMs: Long,
        now: Long,
    ) {
        dao.upsertReviewState(
            ReviewStateEntity(item.letterId, item.cardType.name, newState.stabilityHours, newState.lastReviewedAt ?: now),
        )
        dao.insertAnswer(
            AnswerLogEntity(
                sessionId = sessionId, answeredAt = now, letterId = item.letterId, cardType = item.cardType.name,
                correct = correct, selfRating = null, responseMs = responseMs, alignmentDistance = null,
            ),
        )
    }
}
