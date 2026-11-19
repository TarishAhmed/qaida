package `in`.qaida.app.ui.letter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.qaida.app.AppContainer
import `in`.qaida.app.R
import `in`.qaida.app.data.settings.Language
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.domain.CardType
import `in`.qaida.app.domain.CatalogLetter
import `in`.qaida.app.domain.Progression
import `in`.qaida.app.domain.SelfRating
import `in`.qaida.app.ui.common.ScreenScaffold
import `in`.qaida.app.ui.common.SvgDiagram
import `in`.qaida.app.ui.produce.ProduceController
import `in`.qaida.app.ui.produce.ProduceControls
import `in`.qaida.app.ui.produce.ProduceResult
import `in`.qaida.app.ui.theme.ArabicText
import `in`.qaida.app.ui.theme.ScriptFace

/** Reference, not drill: the letter in both faces, its forms, how it is made, every voice, and its history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterDetailScreen(container: AppContainer, letterId: String, settings: Settings, onBack: () -> Unit) {
    var letter by remember { mutableStateOf<CatalogLetter?>(null) }
    var history by remember { mutableStateOf<List<Double>>(emptyList()) }
    LaunchedEffect(letterId) {
        container.contentReady.await()
        val catalog = container.contentRepository.catalog()
        val l = catalog.firstOrNull { it.id == letterId } ?: return@LaunchedEffect
        container.playback.preload(l.isolatedClips.map { it.file })
        history = Progression.history(container.database.progressDao().answersFor(letterId).mapNotNull { a ->
            val type = CardType.entries.firstOrNull { it.name == a.cardType } ?: return@mapNotNull null
            Progression.LoggedAnswer(type, a.answeredAt, a.correct, a.selfRating?.let { r -> SelfRating.entries.firstOrNull { it.code == r } })
        })
        letter = l
    }
    val ml = settings.uiLanguage == Language.ML
    val l = letter
    ScreenScaffold(if (l == null) stringResource(R.string.letter_title) else if (ml) l.nameMl else l.nameEn, onBack) {
        if (l == null) return@ScreenScaffold
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                for (face in listOf(ScriptFace.NASTALEEQ, ScriptFace.NASKH)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArabicText(l.glyph, 96.sp, face = face, compact = true, modifier = Modifier.testTag("glyph_${face.name}"))
                        Text(stringResource(if (face == ScriptFace.NASTALEEQ) R.string.face_nastaleeq else R.string.face_naskh),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Heading(stringResource(R.string.letter_forms))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for ((form, label) in listOf(
                    l.forms.isolated to R.string.form_isolated, l.forms.initial to R.string.form_initial,
                    l.forms.medial to R.string.form_medial, l.forms.final to R.string.form_final,
                )) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArabicText(form, 40.sp, face = settings.scriptFace)
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            l.makhraj?.let { m ->
                Heading(stringResource(R.string.letter_makhraj))
                SvgDiagram(m.diagram, Modifier.size(160.dp).testTag("makhraj"))
                Text(if (ml) m.noteMl else m.noteEn, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Heading(stringResource(R.string.letter_voices))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                l.isolatedClips.forEachIndexed { i, clip ->
                    AssistChip(
                        onClick = { container.playback.play(clip.file) },
                        label = { Text(stringResource(R.string.letter_voice_n, i + 1)) },
                        modifier = Modifier.testTag("voice_$i"),
                    )
                }
            }

            Heading(stringResource(R.string.letter_say_it))
            val scope = rememberCoroutineScope()
            val produce = remember(l.id) { ProduceController(container, scope, l.isolatedClips.firstOrNull()) }
            val ps by produce.state.collectAsStateWithLifecycle()
            ProduceControls(produce, hasReference = l.isolatedClips.isNotEmpty(), onMicDeclined = {}, modifier = Modifier.fillMaxWidth())
            ProduceResult(ps, Modifier.fillMaxWidth())

            if (history.size >= 2) {
                Heading(stringResource(R.string.letter_history))
                Sparkline(history, Modifier.fillMaxWidth().height(56.dp))
            }
        }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().semantics { heading() })

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    val colour = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.letter_history_description, values.size)
    Canvas(modifier.semantics { contentDescription = description }) {
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * step, size.height * (1 - v.toFloat()))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, colour, style = Stroke(width = 2.dp.toPx()))
    }
}
