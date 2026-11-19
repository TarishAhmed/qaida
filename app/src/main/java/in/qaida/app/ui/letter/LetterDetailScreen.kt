package `in`.qaida.app.ui.letter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import `in`.qaida.app.AppContainer
import `in`.qaida.app.R
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.ui.common.ScreenScaffold

/** Filled in by milestone 7. */
@Composable
fun LetterDetailScreen(container: AppContainer, letterId: String, settings: Settings, onBack: () -> Unit) {
    ScreenScaffold(stringResource(R.string.letter_title), onBack) {}
}
