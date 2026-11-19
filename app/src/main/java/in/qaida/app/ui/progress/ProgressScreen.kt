package `in`.qaida.app.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.qaida.app.AppContainer
import `in`.qaida.app.R
import `in`.qaida.app.data.backup.BackupFormatException
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.domain.LetterStatus
import `in`.qaida.app.domain.Progression
import `in`.qaida.app.ui.common.ScreenScaffold
import `in`.qaida.app.ui.home.MasteryGrid
import `in`.qaida.app.ui.home.TierRow
import `in`.qaida.app.ui.theme.ArabicText
import `in`.qaida.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

private data class ProgressData(
    val tiers: List<TierRow>,
    val weakest: List<LetterStatus>,
    val totalAnswered: Int,
    /** Cards answered on each of the last 30 days, oldest first. */
    val last30: List<Int>,
)

private const val DAY_MS = 86_400_000L

@Composable
fun ProgressScreen(container: AppContainer, settings: Settings, onBack: () -> Unit, onLetter: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ProgressData?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        container.contentReady.await()
        val now = container.clock()
        val catalog = container.contentRepository.catalog()
        val states = container.progressRepository.reviewStates()
        val unlocked = container.progressRepository.refreshUnlockedTiers(catalog, states, now)
        val bosses = container.progressRepository.bossAttemptedTiers()
        val statuses = Progression.status(catalog, states, unlocked, now)
        val dao = container.database.progressDao()
        val offset = TimeZone.getDefault().getOffset(now).toLong()
        val today = (now + offset) / DAY_MS
        val perDay = dao.answersPerDay(now - 31 * DAY_MS, offset).associate { it.day to it.count }
        data = ProgressData(
            tiers = statuses.groupBy { it.letter.unlockTier }.toSortedMap().map { (t, l) -> TierRow(t, t in unlocked, t in bosses, l) },
            weakest = statuses.filter { it.unlocked && it.recall > 0 }.sortedBy { it.recall }.take(5),
            totalAnswered = dao.answerCount(),
            last30 = (29 downTo 0).map { perDay[today - it] ?: 0 },
        )
    }

    val saved = stringResource(R.string.progress_exported)
    val restored = stringResource(R.string.progress_imported)
    val notBackup = stringResource(R.string.progress_import_invalid)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = container.backupRepository.export(container.clock())
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            message = saved
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                container.backupRepository.import(json ?: throw BackupFormatException("empty"))
                reload++
                restored
            } catch (e: BackupFormatException) {
                notBackup
            }
        }
    }

    ScreenScaffold(stringResource(R.string.progress_title), onBack) {
        val d = data ?: return@ScreenScaffold
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MasteryGrid(d.tiers, settings.scriptFace, onLetter, Modifier.fillMaxWidth().height((d.tiers.size * 64).dp), glyphSize = 30)

            if (d.weakest.isNotEmpty()) {
                Heading(stringResource(R.string.progress_weakest))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val status = LocalStatusColors.current
                    for (w in d.weakest) {
                        Surface(
                            onClick = { onLetter(w.letter.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = status.of(w.mastery),
                            modifier = Modifier.size(56.dp).testTag("weak_${w.letter.id}"),
                        ) { Box(contentAlignment = Alignment.Center) { ArabicText(w.letter.glyph, 26.sp, face = settings.scriptFace, compact = true) } }
                    }
                }
            }

            Text(
                pluralStringResource(R.plurals.progress_total_answered, d.totalAnswered, d.totalAnswered),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("total_answered"),
            )

            Heading(stringResource(R.string.progress_last_30))
            HeatGrid(d.last30)

            Heading(stringResource(R.string.progress_backup))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { exporter.launch("qaida-backup.json") }, modifier = Modifier.weight(1f).testTag("export")) {
                    Text(stringResource(R.string.progress_export))
                }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f).testTag("import")) {
                    Text(stringResource(R.string.progress_import))
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("backup_message")) }
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })

/** Thirty cells, oldest first in rows of ten; shade is cards answered that day, never days opened. */
@Composable
private fun HeatGrid(counts: List<Int>) {
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val peak = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in counts.chunked(10)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (n in row) {
                    val label = pluralStringResource(R.plurals.progress_day_cards, n, n)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(if (n == 0) empty else accent.copy(alpha = 0.25f + 0.75f * n / peak), RoundedCornerShape(4.dp))
                            .semantics { contentDescription = label },
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
