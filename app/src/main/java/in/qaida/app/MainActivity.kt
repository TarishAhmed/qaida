package `in`.qaida.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.qaida.app.ui.home.HomeScreen
import `in`.qaida.app.ui.session.SessionScreen
import `in`.qaida.app.ui.session.SessionViewModel
import `in`.qaida.app.ui.theme.QaidaTheme

/** Home is the hub; every other screen is pushed onto and popped off this stack. */
sealed interface Screen {
    data object Home : Screen
    data class Session(val runId: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as QaidaApp).container
        setContent {
            QaidaTheme {
                val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
                BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

                when (val top = stack.last()) {
                    Screen.Home -> HomeScreen(
                        sessionSeconds = (container.sessionConfig.durationMs / 1000).toInt(),
                        onStart = { stack.add(Screen.Session(System.nanoTime())) },
                    )
                    is Screen.Session -> {
                        // Keyed by run so every Start builds a fresh session.
                        val vm: SessionViewModel = viewModel(
                            key = "session-${top.runId}",
                            factory = viewModelFactory { initializer { SessionViewModel(container) } },
                        )
                        SessionScreen(vm, onDone = { stack.remove(top) })
                    }
                }
            }
        }
    }
}
