package `in`.qaida.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ContentDao {

    @Query("SELECT COUNT(*) FROM letter")
    abstract suspend fun letterCount(): Int

    @Query("SELECT * FROM letter ORDER BY unlockTier, ordinal")
    abstract suspend fun letters(): List<LetterEntity>

    @Query("SELECT * FROM letter_audio")
    abstract suspend fun allAudio(): List<LetterAudioEntity>

    @Query("SELECT * FROM letter_confusable ORDER BY letterId, rank")
    abstract suspend fun allConfusables(): List<LetterConfusableEntity>

    @Query("SELECT * FROM confusion_set_member")
    abstract suspend fun allSetMembers(): List<ConfusionSetMemberEntity>

    @Query("SELECT * FROM makhraj")
    abstract suspend fun allMakhraj(): List<MakhrajEntity>

    @Query("SELECT * FROM boss_line ORDER BY tier")
    abstract suspend fun bossLines(): List<BossLineEntity>

    @Query("SELECT value FROM content_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): String?

    @Transaction
    open suspend fun replaceAll(
        letters: List<LetterEntity>,
        audio: List<LetterAudioEntity>,
        confusables: List<LetterConfusableEntity>,
        setMembers: List<ConfusionSetMemberEntity>,
        makhraj: List<MakhrajEntity>,
        bossLines: List<BossLineEntity>,
        meta: List<ContentMetaEntity>,
    ) {
        clearLetters(); clearAudio(); clearConfusables(); clearSetMembers(); clearMakhraj(); clearBossLines()
        insertBossLines(bossLines)
        insertLetters(letters)
        insertAudio(audio)
        insertConfusables(confusables)
        insertSetMembers(setMembers)
        insertMakhraj(makhraj)
        upsertMeta(meta)
    }

    @Query("DELETE FROM letter") protected abstract suspend fun clearLetters()
    @Query("DELETE FROM letter_audio") protected abstract suspend fun clearAudio()
    @Query("DELETE FROM letter_confusable") protected abstract suspend fun clearConfusables()
    @Query("DELETE FROM confusion_set_member") protected abstract suspend fun clearSetMembers()
    @Query("DELETE FROM makhraj") protected abstract suspend fun clearMakhraj()
    @Query("DELETE FROM boss_line") protected abstract suspend fun clearBossLines()
    @Insert protected abstract suspend fun insertBossLines(rows: List<BossLineEntity>)

    @Insert protected abstract suspend fun insertLetters(rows: List<LetterEntity>)
    @Insert protected abstract suspend fun insertAudio(rows: List<LetterAudioEntity>)
    @Insert protected abstract suspend fun insertConfusables(rows: List<LetterConfusableEntity>)
    @Insert protected abstract suspend fun insertSetMembers(rows: List<ConfusionSetMemberEntity>)
    @Insert protected abstract suspend fun insertMakhraj(rows: List<MakhrajEntity>)
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertMeta(rows: List<ContentMetaEntity>)
}
