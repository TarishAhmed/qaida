package `in`.qaida.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import `in`.qaida.app.ui.theme.ScriptFace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Language(val tag: String) { EN("en"), ML("ml") }

data class Settings(
    /** UI strings; independent of the system locale. */
    val uiLanguage: Language = Language.EN,
    /** Spoken instruction audio; independent of the UI language. */
    val instructionLanguage: Language = Language.EN,
    val scriptFace: ScriptFace = ScriptFace.NASTALEEQ,
    val sessionSeconds: Int = 90,
    val textScale: Float = 1f,
    val reminderEnabled: Boolean = false,
    /** Minutes after midnight, local time. */
    val reminderMinuteOfDay: Int = 19 * 60,
    /** The learner declined the microphone: sessions leave out PRODUCE cards until it is granted. */
    val micDeclined: Boolean = false,
) {
    companion object {
        val SESSION_LENGTHS = listOf(60, 90, 180)
        val TEXT_SCALES = listOf(1f, 1.15f, 1.3f)
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val uiLanguage = stringPreferencesKey("ui_language")
        val instructionLanguage = stringPreferencesKey("instruction_language")
        val scriptFace = stringPreferencesKey("script_face")
        val sessionSeconds = intPreferencesKey("session_seconds")
        val textScale = floatPreferencesKey("text_scale")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val micDeclined = booleanPreferencesKey("mic_declined")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            uiLanguage = Language.entries.firstOrNull { it.tag == this[Keys.uiLanguage] } ?: d.uiLanguage,
            instructionLanguage = Language.entries.firstOrNull { it.tag == this[Keys.instructionLanguage] } ?: d.instructionLanguage,
            scriptFace = ScriptFace.entries.firstOrNull { it.name == this[Keys.scriptFace] } ?: d.scriptFace,
            sessionSeconds = this[Keys.sessionSeconds]?.takeIf { it in Settings.SESSION_LENGTHS } ?: d.sessionSeconds,
            textScale = this[Keys.textScale]?.takeIf { it in Settings.TEXT_SCALES } ?: d.textScale,
            reminderEnabled = this[Keys.reminderEnabled] ?: d.reminderEnabled,
            reminderMinuteOfDay = this[Keys.reminderMinute]?.coerceIn(0, 24 * 60 - 1) ?: d.reminderMinuteOfDay,
            micDeclined = this[Keys.micDeclined] ?: d.micDeclined,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.uiLanguage] = s.uiLanguage.tag
            prefs[Keys.instructionLanguage] = s.instructionLanguage.tag
            prefs[Keys.scriptFace] = s.scriptFace.name
            prefs[Keys.sessionSeconds] = s.sessionSeconds
            prefs[Keys.textScale] = s.textScale
            prefs[Keys.reminderEnabled] = s.reminderEnabled
            prefs[Keys.reminderMinute] = s.reminderMinuteOfDay
            prefs[Keys.micDeclined] = s.micDeclined
        }
    }
}
