package `in`.qaida.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ProgressDao {

    @Query("SELECT * FROM review_state")
    suspend fun reviewStates(): List<ReviewStateEntity>

    @Upsert
    suspend fun upsertReviewState(state: ReviewStateEntity)

    @Insert
    suspend fun insertAnswer(answer: AnswerLogEntity)

    @Query("SELECT tier FROM tier_unlock")
    suspend fun unlockedTiers(): List<Int>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertTierUnlocks(rows: List<TierUnlockEntity>)

    @Query("SELECT tier FROM boss_attempt")
    suspend fun bossAttemptedTiers(): List<Int>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertBossAttempt(row: BossAttemptEntity)

    @Query("DELETE FROM review_state") suspend fun clearReviewStates()
    @Query("DELETE FROM answer_log") suspend fun clearAnswers()
    @Query("DELETE FROM session") suspend fun clearSessions()
    @Query("DELETE FROM tier_unlock") suspend fun clearTierUnlocks()
    @Query("DELETE FROM boss_attempt") suspend fun clearBossAttempts()

    /** Wipes all learner progress. Content tables are untouched. */
    @Transaction
    suspend fun clearProgress() {
        clearReviewStates(); clearAnswers(); clearSessions(); clearTierUnlocks(); clearBossAttempts()
    }

    @Query("SELECT COUNT(*) FROM answer_log")
    suspend fun answerCount(): Int

    @Query("SELECT * FROM answer_log ORDER BY answeredAt, id")
    suspend fun answers(): List<AnswerLogEntity>

    @Query("SELECT * FROM answer_log WHERE letterId = :letterId ORDER BY answeredAt, id")
    suspend fun answersFor(letterId: String): List<AnswerLogEntity>

    /** Cards answered per local day since [sinceMs]; [offsetMs] shifts UTC to local time. */
    @Query("SELECT (answeredAt + :offsetMs) / 86400000 AS day, COUNT(*) AS count FROM answer_log WHERE answeredAt >= :sinceMs GROUP BY day")
    suspend fun answersPerDay(sinceMs: Long, offsetMs: Long): List<DayCount>

    @Query("SELECT * FROM session ORDER BY id") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM tier_unlock ORDER BY tier") suspend fun tierUnlocks(): List<TierUnlockEntity>
    @Query("SELECT * FROM boss_attempt ORDER BY tier") suspend fun bossAttempts(): List<BossAttemptEntity>

    @Insert suspend fun insertReviewStates(rows: List<ReviewStateEntity>)
    @Insert suspend fun insertAnswers(rows: List<AnswerLogEntity>)
    @Insert suspend fun insertSessions(rows: List<SessionEntity>)
    @Insert suspend fun insertBossAttempts(rows: List<BossAttemptEntity>)

    /** Replaces all progress with a backup, atomically: a failed import leaves the old progress intact. */
    @Transaction
    suspend fun replaceProgress(
        reviewStates: List<ReviewStateEntity>,
        answers: List<AnswerLogEntity>,
        sessions: List<SessionEntity>,
        tierUnlocks: List<TierUnlockEntity>,
        bossAttempts: List<BossAttemptEntity>,
    ) {
        clearProgress()
        insertReviewStates(reviewStates)
        insertSessions(sessions)
        insertAnswers(answers)
        insertTierUnlocks(tierUnlocks)
        insertBossAttempts(bossAttempts)
    }

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE session SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)
}
