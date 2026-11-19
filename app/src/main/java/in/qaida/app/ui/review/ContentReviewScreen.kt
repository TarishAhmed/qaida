package `in`.qaida.app.ui.review

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.qaida.app.AppContainer
import `in`.qaida.app.R
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.ui.common.ScreenScaffold
import `in`.qaida.app.ui.theme.ArabicText
import `in`.qaida.app.ui.theme.ScriptFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * For a fluent reader: hear every bundled recording next to the letter it is filed under and mark it
 * right or wrong. Recordings from open sources are matched by file name, so some can be misfiled; where a
 * name was ambiguous the same recording is offered under each candidate letter. The export is applied with
 * tools/build_content_pack.py --apply-review.
 */
@Composable
fun ContentReviewScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var letters by remember { mutableStateOf<List<CatalogLetter>>(emptyList()) }
    val verdicts = remember { mutableStateMapOf<String, Boolean>() }
    var reviewer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.contentReady.await()
        // Saved marks first, before anything can be tapped, so a late load never overwrites a fresh mark.
        verdicts.putAll(container.reviewStore.load())
        val catalog = container.contentRepository.catalog()
        letters = catalog
        container.playback.preload(catalog.flatMap { l -> l.isolatedClips.map { it.file } }.distinct())
    }
    fun mark(file: String, ok: Boolean) {
        verdicts[file] = ok
        scope.launch { container.reviewStore.save(verdicts.toMap()) }
    }

    val exported = stringResource(R.string.review_exported)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = container.reviewStore.export(verdicts.toMap(), reviewer, container.clock())
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            message = exported
        }
    }

    // A recording filed under more than one letter was ambiguous by name: say where else it appears.
    val filedUnder = letters.flatMap { l -> l.isolatedClips.map { sourceOf(it.file) to l } }.groupBy({ it.first }, { it.second })
    val total = letters.sumOf { it.isolatedClips.size }

    ScreenScaffold(stringResource(R.string.review_title), onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.review_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.review_progress, verdicts.size, total), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("review_progress"))
                }
            }
            items(letters, key = { it.id }) { l ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ArabicText(l.glyph, 44.sp, face = ScriptFace.NASTALEEQ, compact = true)
                        ArabicText(l.glyph, 36.sp, face = ScriptFace.NASKH, compact = true)
                        Text(l.nameEn, style = MaterialTheme.typography.titleMedium)
                    }
                    l.isolatedClips.forEachIndexed { i, clip ->
                        val others = filedUnder[sourceOf(clip.file)].orEmpty().filter { it.id != l.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { container.playback.play(clip.file) }, modifier = Modifier.testTag("review_play_${clip.file}")) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.review_play, i + 1))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.review_recording_n, i + 1))
                                if (others.isNotEmpty()) {
                                    Text(stringResource(R.string.review_also_under, others.joinToString { it.nameEn }),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            FilterChip(selected = verdicts[clip.file] == true, onClick = { mark(clip.file, true) },
                                label = { Text(stringResource(R.string.review_right)) }, modifier = Modifier.padding(end = 6.dp).testTag("review_ok_${clip.file}"))
                            FilterChip(selected = verdicts[clip.file] == false, onClick = { mark(clip.file, false) },
                                label = { Text(stringResource(R.string.review_wrong_letter)) }, modifier = Modifier.testTag("review_bad_${clip.file}"))
                        }
                    }
                    HorizontalDivider()
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    OutlinedTextField(reviewer, { reviewer = it }, label = { Text(stringResource(R.string.review_reviewer)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { exporter.launch("qaida-review.json") }, modifier = Modifier.fillMaxWidth().testTag("review_export")) {
                        Text(stringResource(R.string.review_export))
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** <letter>_isolated_<voice>.opus: the voice id names the source recording, shared across letters. */
private fun sourceOf(file: String) = file.substringAfterLast('_')
