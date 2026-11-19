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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.qaida.app.R
import `in`.qaida.app.domain.AnswerOption
import `in`.qaida.app.domain.CardType
import `in`.qaida.app.domain.Prompt
import `in`.qaida.app.ui.theme.ArabicText
import `in`.qaida.app.ui.theme.LocalStatusColors
import `in`.qaida.app.ui.theme.ScriptFace
import `in`.qaida.app.ui.theme.ArabicLine
import `in`.qaida.app.ui.common.SvgDiagram
import `in`.qaida.app.ui.produce.ProduceController
import `in`.qaida.app.ui.produce.ProduceControls
import `in`.qaida.app.ui.produce.ProducePhase
import `in`.qaida.app.ui.produce.ProduceResult
import `in`.qaida.app.ui.produce.SelfRatingRow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(viewModel: SessionViewModel, face: ScriptFace, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            SessionUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            SessionUiState.NothingDue -> NothingDue(onDone)
            is SessionUiState.Boss -> BossView(s, viewModel, face)
            is SessionUiState.Card -> if (s.produce != null) ProduceCardView(s, s.produce, viewModel, face) else CardView(s, viewModel, face)
            is SessionUiState.Summary -> SummaryView(s, face, onDone)
        }
    }
}

@Composable
private fun instruction(type: CardType) = stringResource(
    when (type) {
        CardType.HEAR_PICK -> R.string.card_hear_pick
        CardType.SEE_PICK -> R.string.card_see_pick
        CardType.AB_SAME_DIFF -> R.string.card_ab_same_diff
        CardType.FORM_PICK -> R.string.card_form_pick
        CardType.HARAKAH_PICK -> R.string.card_harakah_pick
        CardType.PRODUCE -> R.string.card_produce
    },
)

@Composable
private fun CardView(s: SessionUiState.Card, vm: SessionViewModel, face: ScriptFace) {
    val card = s.card
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

        // Upper area: the prompt. Nothing here is interactive.
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                instruction(card.item.cardType),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("instruction"),
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when (val p = card.prompt) {
                    is Prompt.Read -> ArabicText(p.text, 120.sp, face = face, modifier = Modifier.testTag("prompt_text"))
                    is Prompt.Listen -> card.hintGlyph?.let { ArabicText(it, 120.sp, face = face, modifier = Modifier.testTag("hint")) }
                    is Prompt.Speak -> ArabicText(p.text, 120.sp, face = face)
                }
            }
        }

        // Lower area: everything the thumb touches.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (incorrect != null) {
                TextButton(onClick = vm::continueAfterIncorrect, modifier = Modifier.testTag("continue")) {
                    Text(stringResource(R.string.session_tap_to_continue), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val hasPromptAudio = card.prompt is Prompt.Listen || card.hintClip != null
            if (hasPromptAudio) {
                FilledTonalIconButton(
                    onClick = vm::playPrompt,
                    enabled = s.canPlayPrompt,
                    modifier = Modifier.size(72.dp).testTag("replay"),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.session_replay), Modifier.size(32.dp))
                }
            }
            FeedbackAnnouncement(s)
            OptionGrid(s, face, vm)
            if (card.item.cardType == CardType.SEE_PICK) {
                Button(
                    onClick = vm::confirmSelection,
                    enabled = s.selectedKey != null && s.feedback == null,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("confirm"),
                ) { Text(stringResource(R.string.session_choose)) }
            }
        }
    }
}

/**
 * PRODUCE: hear the reference, say it, and compare. What can appear: the glyph, the articulation diagram,
 * two aligned waveforms and a plain statement of durations. What never appears: a score, a pass/fail,
 * a red mark, a buzzer, or the word wrong. The learner rates their own attempt.
 */
@Composable
private fun ProduceCardView(s: SessionUiState.Card, produce: ProduceController, vm: SessionViewModel, face: ScriptFace) {
    val ps by produce.state.collectAsStateWithLifecycle()
    val language = LocalContext.current.resources.configuration.locales[0].language
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {}, gapSize = 0.dp)
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(instruction(CardType.PRODUCE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("instruction"))
            ArabicText(s.card.target.glyph, 96.sp, face = face)
            if (ps.phase == ProducePhase.IDLE) {
                s.card.target.makhraj?.let { m ->
                    SvgDiagram(m.diagram, Modifier.size(120.dp))
                    Text(if (language == "ml") m.noteMl else m.noteEn, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            } else {
                ProduceResult(ps, Modifier.fillMaxWidth())
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProduceControls(produce, hasReference = true, onMicDeclined = vm::micDeclined, modifier = Modifier.fillMaxWidth())
            if (ps.phase == ProducePhase.DONE) SelfRatingRow(vm::rateProduce, Modifier.fillMaxWidth())
            TextButton(onClick = vm::skipProduce, modifier = Modifier.testTag("produce_skip")) { Text(stringResource(R.string.produce_skip)) }
        }
    }
}

/** A completed tier's line of mushaf text: the payoff. Recording is optional and nothing is measured against it. */
@Composable
private fun BossView(s: SessionUiState.Boss, vm: SessionViewModel, face: ScriptFace) {
    val ps by s.produce.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).testTag("boss")) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.boss_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ArabicLine(s.line.text, face)
            if (ps.phase == ProducePhase.RECORDING || ps.phase == ProducePhase.ANALYSING) ProduceResult(ps.copy(analysis = null))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ProduceControls(s.produce, hasReference = s.hasReference, onMicDeclined = {}, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.bossDone(attempted = true) }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("boss_done")) {
                Text(stringResource(R.string.boss_done))
            }
            TextButton(onClick = { vm.bossDone(attempted = false) }) { Text(stringResource(R.string.boss_later)) }
        }
    }
}

/** Speaks the result to TalkBack users; sighted learners see it in the tile colours. */
@Composable
private fun FeedbackAnnouncement(s: SessionUiState.Card) {
    val fb = s.feedback
    val correctIndex = s.card.options.indexOfFirst { it.key == s.card.correctKey } + 1
    val message = when (fb) {
        null -> ""
        is Feedback.Correct -> stringResource(R.string.a11y_correct)
        is Feedback.Incorrect -> stringResource(R.string.a11y_answer_was, correctIndex)
    }
    Box(Modifier.size(1.dp).semantics {
        liveRegion = LiveRegionMode.Polite
        if (message.isNotEmpty()) contentDescription = message
    })
}

@Composable
private fun OptionGrid(s: SessionUiState.Card, face: ScriptFace, vm: SessionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in s.card.options.withIndex().chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((index, option) in row) {
                    OptionTile(option, index, s, face, Modifier.weight(1f), vm)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OptionTile(option: AnswerOption, index: Int, s: SessionUiState.Card, face: ScriptFace, modifier: Modifier, vm: SessionViewModel) {
    val status = LocalStatusColors.current
    val fb = s.feedback
    val isChosen = fb?.chosenKey == option.key
    val isAnswer = fb is Feedback.Incorrect && fb.correctKey == option.key
    val highlighted = (fb is Feedback.Correct && isChosen) || isAnswer
    val dimmed = fb is Feedback.Incorrect && isChosen
    val selected = fb == null && s.selectedKey == option.key
    val soundLabel = stringResource(R.string.session_sound_n, index + 1)
    // TalkBack hears the position, never the letter: reading the glyph aloud would give the answer away.
    val optionLabel = stringResource(R.string.session_option_n, index + 1)
    val selectedLabel = stringResource(R.string.session_selected)

    Surface(
        onClick = { if (option.kind == AnswerOption.Kind.CLIP) vm.playOption(option.key) else vm.answer(option.key) },
        enabled = fb == null,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) status.correct else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.height(88.dp).alpha(if (dimmed) 0.35f else 1f).testTag("option_${option.key}")
            .semantics {
                when (option.kind) {
                    AnswerOption.Kind.CLIP -> contentDescription = soundLabel
                    AnswerOption.Kind.GLYPH -> contentDescription = optionLabel
                    else -> Unit
                }
                if (selected) stateDescription = selectedLabel
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (option.kind) {
                AnswerOption.Kind.GLYPH -> ArabicText(option.text.orEmpty(), 40.sp, face = face, compact = true, modifier = Modifier.clearAndSetSemantics {})
                AnswerOption.Kind.CLIP -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("${index + 1}", style = MaterialTheme.typography.titleLarge)
                }
                AnswerOption.Kind.SAME -> Text(stringResource(R.string.session_same), style = MaterialTheme.typography.titleMedium)
                AnswerOption.Kind.DIFFERENT -> Text(stringResource(R.string.session_different), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SummaryView(s: SessionUiState.Summary, face: ScriptFace, onDone: () -> Unit) {
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
        if (s.newTier) Text(stringResource(R.string.summary_new_tier), color = MaterialTheme.colorScheme.primary)
        if (s.moved.isNotEmpty()) {
            Text(stringResource(R.string.summary_moved), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in s.moved) {
                    Box(
                        Modifier.size(56.dp).background(status.of(m.to), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { ArabicText(m.letter.glyph, 26.sp, face = face, compact = true) }
                }
            }
        }
        s.watch?.let {
            Text(stringResource(R.string.summary_watch), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ArabicText(it.glyph, 56.sp, face = face)
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
