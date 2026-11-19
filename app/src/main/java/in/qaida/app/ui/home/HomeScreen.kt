package `in`.qaida.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.qaida.app.R
import `in`.qaida.app.domain.LetterStatus
import `in`.qaida.app.domain.Mastery
import `in`.qaida.app.ui.theme.ArabicText
import `in`.qaida.app.ui.theme.LocalStatusColors
import `in`.qaida.app.ui.theme.ScriptFace

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    sessionSeconds: Int,
    face: ScriptFace,
    onStart: () -> Unit,
    onLetter: (String) -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
) {
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            // Upper two-thirds: the mastery grid, one row per tier, read right to left.
            Box(Modifier.fillMaxWidth().weight(2f)) {
                if (!state.loading) MasteryGrid(state.tiers, face, onLetter, Modifier.fillMaxSize())
            }
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!state.loading) {
                    Text(
                        if (state.lettersNeedingReview == 0) stringResource(R.string.home_nothing_due)
                        else pluralStringResource(R.plurals.home_letters_need_review, state.lettersNeedingReview, state.lettersNeedingReview),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("review_count"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    androidx.compose.material3.TextButton(onClick = onProgress, modifier = Modifier.testTag("progress")) {
                        Text(stringResource(R.string.home_progress))
                    }
                    androidx.compose.material3.TextButton(onClick = onSettings, modifier = Modifier.testTag("settings")) {
                        Text(stringResource(R.string.home_settings))
                    }
                }
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("start")) {
                    Text(stringResource(R.string.home_start, sessionSeconds), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun MasteryGrid(
    tiers: List<TierRow>,
    face: ScriptFace,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: Int = 26,
) {
    val status = LocalStatusColors.current
    // Arabic order: the first letter of a tier sits at the right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in tiers) {
                Row(
                    Modifier.fillMaxWidth().weight(1f).alpha(if (row.unlocked) 1f else 0.3f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // A gold edge marks a tier whose boss line has been read: permanent once attempted.
                    Box(
                        Modifier.width(4.dp).fillMaxHeight()
                            .then(if (row.bossAttempted) Modifier.alpha(1f) else Modifier.alpha(0f))
                            .padding(vertical = 4.dp),
                    ) {
                        Surface(Modifier.fillMaxSize(), color = status.gold, shape = RoundedCornerShape(2.dp)) {}
                    }
                    for (cell in row.letters) {
                        LetterCell(cell, row.unlocked, face, glyphSize, Modifier.weight(1f).fillMaxHeight(), onLetter)
                    }
                    // Keep short tiers aligned to the same cell width as full ones.
                    repeat((CELLS_PER_ROW - row.letters.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val CELLS_PER_ROW = 4

@Composable
private fun LetterCell(
    cell: LetterStatus,
    unlocked: Boolean,
    face: ScriptFace,
    glyphSize: Int,
    modifier: Modifier,
    onLetter: (String) -> Unit,
) {
    val status = LocalStatusColors.current
    val description = if (unlocked) masteryLabel(cell.mastery) else stringResource(R.string.mastery_locked)
    Surface(
        onClick = { onLetter(cell.letter.id) },
        shape = RoundedCornerShape(12.dp),
        color = if (unlocked) status.of(cell.mastery) else status.new,
        modifier = modifier.testTag("cell_${cell.letter.id}").semantics { stateDescription = description },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ArabicText(cell.letter.glyph, glyphSize.sp, face = face, compact = true)
        }
    }
}

@Composable
fun masteryLabel(m: Mastery) = stringResource(
    when (m) {
        Mastery.NEW -> R.string.mastery_new
        Mastery.RED -> R.string.mastery_red
        Mastery.AMBER -> R.string.mastery_amber
        Mastery.GREEN -> R.string.mastery_green
        Mastery.GOLD -> R.string.mastery_gold
    },
)
