package `in`.qaida.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProgressDao {

    @Query("SELECT * FROM review_state")
    suspend fun reviewStates(): List<ReviewStateEntity>

    @Upsert
    suspend fun upsertReviewState(state: ReviewStateEntity)

    @Insert
    suspend fun insertAnswer(answer: AnswerLogEntity)

    @Query("SELECT COUNT(*) FROM answer_log")
    suspend fun answerCount(): Int

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE session SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)
}
