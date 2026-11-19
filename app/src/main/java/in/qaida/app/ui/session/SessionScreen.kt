package `in`.qaida.app.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.qaida.app.R
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.ui.theme.ArabicText
import `in`.qaida.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(viewModel: SessionViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            SessionUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            SessionUiState.NothingDue -> NothingDue(onDone)
            is SessionUiState.Card -> CardView(s, viewModel)
            is SessionUiState.Summary -> SummaryView(s, onDone)
        }
    }
}

@Composable
private fun CardView(s: SessionUiState.Card, vm: SessionViewModel) {
    val incorrect = s.feedback as? Feedback.Incorrect
    // After a wrong answer a tap anywhere continues. It is a raw gesture so it adds no semantics; the
    // explicit button below is what TalkBack sees.
    val tapToContinue = if (incorrect != null) {
        Modifier.pointerInput(Unit) { detectTapGestures { vm.continueAfterIncorrect() } }
    } else Modifier

    Column(Modifier.fillMaxSize().systemBarsPadding().then(tapToContinue)) {
        LinearProgressIndicator(
            progress = { s.progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )

        // Upper 60%: the prompt. Only introduction cards show the glyph alongside the sound.
        Box(Modifier.fillMaxWidth().weight(0.6f), contentAlignment = Alignment.Center) {
            if (s.card.isIntro) ArabicText(s.card.target.glyph, 120.sp)
        }

        // Lower 40%: everything the thumb touches.
        Column(
            Modifier.fillMaxWidth().weight(0.4f).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
        ) {
            if (incorrect != null) {
                TextButton(onClick = vm::continueAfterIncorrect, modifier = Modifier.testTag("continue")) {
                    Text(stringResource(R.string.session_tap_to_continue), style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilledTonalIconButton(
                onClick = vm::replay,
                modifier = Modifier.size(72.dp).testTag("replay"),
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.session_replay), Modifier.size(32.dp))
            }
            OptionGrid(s, onPick = vm::answer)
        }
    }
}

@Composable
private fun OptionGrid(s: SessionUiState.Card, onPick: (String) -> Unit) {
    val rows = s.card.options.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (option in row) {
                    OptionTile(option, s, Modifier.weight(1f), onPick)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptionTile(option: CatalogLetter, s: SessionUiState.Card, modifier: Modifier, onPick: (String) -> Unit) {
    val status = LocalStatusColors.current
    val fb = s.feedback
    val isChosen = fb?.chosenId == option.id
    val isAnswer = fb is Feedback.Incorrect && fb.correctId == option.id
    val highlighted = (fb is Feedback.Correct && isChosen) || isAnswer
    val dimmed = fb is Feedback.Incorrect && isChosen

    Surface(
        onClick = { onPick(option.id) },
        enabled = fb == null,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) status.correct else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(88.dp).alpha(if (dimmed) 0.35f else 1f).testTag("option_${option.id}"),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ArabicText(option.glyph, 40.sp, compact = true)
        }
    }
}

@Composable
private fun SummaryView(s: SessionUiState.Summary, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(SessionViewModel.SUMMARY_MS)
        onDone()
    }
    val status = LocalStatusColors.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp).testTag("summary")
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.summary_correct, s.correct, s.answered), style = MaterialTheme.typography.headlineSmall)
        if (s.moved.isNotEmpty()) {
            Text(stringResource(R.string.summary_moved), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in s.moved.distinctBy { it.letter.id }) {
                    Box(
                        Modifier.size(56.dp).background(status.of(m.to), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { ArabicText(m.letter.glyph, 26.sp, compact = true) }
                }
            }
        }
        s.watch?.let {
            Text(stringResource(R.string.summary_watch), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ArabicText(it.glyph, 56.sp)
        }
    }
}

@Composable
private fun NothingDue(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.session_nothing_due), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}
