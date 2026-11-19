package `in`.qaida.app

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import `in`.qaida.app.data.settings.Language
import `in`.qaida.app.ui.common.withLocale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.ui.home.HomeScreen
import `in`.qaida.app.ui.home.HomeViewModel
import `in`.qaida.app.ui.letter.LetterDetailScreen
import `in`.qaida.app.ui.progress.ProgressScreen
import `in`.qaida.app.ui.session.SessionScreen
import `in`.qaida.app.ui.session.SessionViewModel
import `in`.qaida.app.ui.settings.SettingsScreen
import `in`.qaida.app.ui.theme.QaidaTheme

/** Home is the hub; every other screen is pushed onto and popped off this stack. */
sealed interface Screen {
    data object Home : Screen
    data class Session(val runId: Long) : Screen
    data class Letter(val letterId: String) : Screen
    data object Progress : Screen
    data object Settings : Screen
}

/** Screens as strings; a running session is not restored, it simply drops off the stack. */
private val StackSaver = listSaver<SnapshotStateList<Screen>, String>(
    save = { stack ->
        stack.mapNotNull {
            when (it) {
                Screen.Home -> "home"
                is Screen.Letter -> "letter:${it.letterId}"
                Screen.Progress -> "progress"
                Screen.Settings -> "settings"
                is Screen.Session -> null
            }
        }
    },
    restore = { saved ->
        saved.mapNotNull {
            when {
                it == "home" -> Screen.Home
                it.startsWith("letter:") -> Screen.Letter(it.removePrefix("letter:"))
                it == "progress" -> Screen.Progress
                it == "settings" -> Screen.Settings
                else -> null
            }
        }.ifEmpty { listOf(Screen.Home) }.toMutableStateList()
    },
)

class MainActivity : ComponentActivity() {
    private var appliedLanguage: Language? = null

    override fun attachBaseContext(newBase: Context) {
        // The UI language is a setting, not the system locale. One small DataStore read at startup.
        val language = runBlocking { (newBase.applicationContext as QaidaApp).container.settingsRepository.current().uiLanguage }
        appliedLanguage = language
        super.attachBaseContext(newBase.withLocale(language.tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as QaidaApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(Settings())
            LaunchedEffect(Unit) {
                container.settingsRepository.settings.map { it.uiLanguage }.distinctUntilChanged().collect { lang ->
                    // DataStore may emit off the main thread; recreate() must not be.
                    if (lang != appliedLanguage) runOnUiThread { recreate() }
                }
            }
            QaidaTheme(textScale = settings.textScale) {
                // Saveable so a language change (which recreates the activity) keeps the learner where they were.
                val stack = rememberSaveable(saver = StackSaver) { mutableStateListOf<Screen>(Screen.Home) }
                val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                BackHandler(enabled = stack.size > 1, onBack = pop)

                when (val top = stack.last()) {
                    Screen.Home -> {
                        val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
                        HomeScreen(
                            viewModel = vm,
                            sessionSeconds = settings.sessionSeconds,
                            face = settings.scriptFace,
                            onStart = { stack.add(Screen.Session(System.nanoTime())) },
                            onLetter = { stack.add(Screen.Letter(it)) },
                            onProgress = { stack.add(Screen.Progress) },
                            onSettings = { stack.add(Screen.Settings) },
                        )
                    }
                    is Screen.Session -> {
                        // Keyed by run so every Start builds a fresh session.
                        val vm: SessionViewModel = viewModel(
                            key = "session-${top.runId}",
                            factory = viewModelFactory { initializer { SessionViewModel(container) } },
                        )
                        SessionScreen(vm, face = settings.scriptFace, onDone = { stack.remove(top) })
                    }
                    is Screen.Letter -> LetterDetailScreen(container, top.letterId, settings, onBack = pop)
                    Screen.Progress -> ProgressScreen(container, settings, onBack = pop, onLetter = { stack.add(Screen.Letter(it)) })
                    Screen.Settings -> SettingsScreen(container, settings, onBack = pop)
                }
            }
        }
    }
}
