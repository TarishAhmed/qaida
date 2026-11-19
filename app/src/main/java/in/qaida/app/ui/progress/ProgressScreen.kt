package `in`.qaida.app.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import `in`.qaida.app.AppContainer
import `in`.qaida.app.R
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.ui.common.ScreenScaffold

/** Filled in by milestone 7. */
@Composable
fun ProgressScreen(container: AppContainer, settings: Settings, onBack: () -> Unit, onLetter: (String) -> Unit) {
    ScreenScaffold(stringResource(R.string.progress_title), onBack) {}
}
