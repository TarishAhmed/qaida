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

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE session SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)
}
