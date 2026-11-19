package `in`.qaida.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as QaidaApp).container
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(Settings())
            QaidaTheme(textScale = settings.textScale) {
                val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
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
