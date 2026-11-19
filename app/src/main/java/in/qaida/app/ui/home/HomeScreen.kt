package `in`.qaida.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.qaida.app.R

/** Milestone 3 stand-in: the mastery grid fills the space above the button in milestone 4. */
@Composable
fun HomeScreen(sessionSeconds: Int, onStart: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Box(Modifier.weight(1f))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("start")) {
                Text(stringResource(R.string.home_start, sessionSeconds), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
