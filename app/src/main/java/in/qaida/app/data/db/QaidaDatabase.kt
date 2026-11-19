package `in`.qaida.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    version = 1,
    entities = [
        LetterEntity::class,
        LetterAudioEntity::class,
        LetterConfusableEntity::class,
        ConfusionSetMemberEntity::class,
        MakhrajEntity::class,
        ContentMetaEntity::class,
        ReviewStateEntity::class,
        SessionEntity::class,
        AnswerLogEntity::class,
    ],
)
abstract class QaidaDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        fun create(context: Context): QaidaDatabase =
            Room.databaseBuilder(context, QaidaDatabase::class.java, "qaida.db").build()
    }
}
