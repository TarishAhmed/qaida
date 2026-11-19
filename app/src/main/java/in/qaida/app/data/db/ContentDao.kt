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

    @Query("SELECT * FROM letter_audio WHERE letterId = :letterId")
    abstract suspend fun audioFor(letterId: String): List<LetterAudioEntity>

    @Query("SELECT otherId FROM letter_confusable WHERE letterId = :letterId ORDER BY rank")
    abstract suspend fun confusablesFor(letterId: String): List<String>

    @Query("SELECT value FROM content_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): String?

    @Transaction
    open suspend fun replaceAll(
        letters: List<LetterEntity>,
        audio: List<LetterAudioEntity>,
        confusables: List<LetterConfusableEntity>,
        setMembers: List<ConfusionSetMemberEntity>,
        makhraj: List<MakhrajEntity>,
        meta: List<ContentMetaEntity>,
    ) {
        clearLetters(); clearAudio(); clearConfusables(); clearSetMembers(); clearMakhraj()
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

    @Insert protected abstract suspend fun insertLetters(rows: List<LetterEntity>)
    @Insert protected abstract suspend fun insertAudio(rows: List<LetterAudioEntity>)
    @Insert protected abstract suspend fun insertConfusables(rows: List<LetterConfusableEntity>)
    @Insert protected abstract suspend fun insertSetMembers(rows: List<ConfusionSetMemberEntity>)
    @Insert protected abstract suspend fun insertMakhraj(rows: List<MakhrajEntity>)
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertMeta(rows: List<ContentMetaEntity>)
}
