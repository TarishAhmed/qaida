package `in`.qaida.app.data.progress

import `in`.qaida.app.data.db.AnswerLogEntity
import `in`.qaida.app.data.db.ProgressDao
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.data.db.SessionEntity
import `in`.qaida.app.domain.CardType
import `in`.qaida.app.domain.ItemKey
import `in`.qaida.app.domain.ReviewState

class ProgressRepository(private val dao: ProgressDao) {

    suspend fun reviewStates(): Map<ItemKey, ReviewState> =
        dao.reviewStates().mapNotNull { e ->
            val type = CardType.entries.firstOrNull { it.name == e.cardType } ?: return@mapNotNull null
            ItemKey(e.letterId, type) to ReviewState(e.stabilityHours, e.lastReviewedAt)
        }.toMap()

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
