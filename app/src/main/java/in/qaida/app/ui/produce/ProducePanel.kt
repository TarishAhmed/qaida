package `in`.qaida.app.ui.produce

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.qaida.app.R
import `in`.qaida.app.domain.SelfRating
import `in`.qaida.app.dsp.Analysis
import `in`.qaida.app.dsp.Confidence
import java.util.Locale

/** Measurements shown when confidence is OK. Never a score, a verdict, or a red mark. */
@Composable
fun ProduceResult(state: ProduceState, modifier: Modifier = Modifier) {
    val a = state.analysis
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.phase == ProducePhase.RECORDING -> Text(stringResource(R.string.produce_recording), color = MaterialTheme.colorScheme.primary)
            state.phase == ProducePhase.ANALYSING -> Text(stringResource(R.string.produce_listening), color = MaterialTheme.colorScheme.onSurfaceVariant)
            a == null -> Unit
            a.confidence == Confidence.LOW -> Text(
                stringResource(R.string.produce_low_confidence),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("produce_low"),
            )
            else -> {
                AlignedWaveforms(a, Modifier.fillMaxWidth().height(120.dp))
                Text(
                    stringResource(R.string.produce_held, seconds(a.userDurationMs), seconds(a.referenceDurationMs)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("produce_held"),
                )
            }
        }
    }
}

private fun seconds(ms: Int) = String.format(Locale.US, "%.1f", ms / 1000.0)

/** The learner's speech above, the reference below, on the reference's timeline via the DTW path. */
@Composable
private fun AlignedWaveforms(a: Analysis, modifier: Modifier) {
    val user = MaterialTheme.colorScheme.primary
    val ref = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.produce_waveforms)
    Canvas(modifier.semantics { contentDescription = description }) {
        val n = a.referenceEnvelope.size
        if (n == 0) return@Canvas
        val peak = maxOf(a.referenceEnvelope.max(), a.alignedUserEnvelope.maxOrNull() ?: 0.0, 1e-6)
        val half = size.height / 2
        val bar = size.width / n
        for (j in 0 until n) {
            val x = j * bar + bar / 2
            val u = (a.alignedUserEnvelope[j] / peak * (half - 4)).toFloat()
            val r = (a.referenceEnvelope[j] / peak * (half - 4)).toFloat()
            drawLine(user, Offset(x, half / 2 - u / 2), Offset(x, half / 2 + u / 2), strokeWidth = maxOf(1f, bar * 0.7f))
            drawLine(ref, Offset(x, half + half / 2 - r / 2), Offset(x, half + half / 2 + r / 2), strokeWidth = maxOf(1f, bar * 0.7f))
        }
    }
}

/**
 * Reference, record and A/B controls. The microphone permission is asked for here, the first time the
 * learner taps record, never earlier. [onMicDeclined] lets the caller drop speaking practice.
 */
@Composable
fun ProduceControls(
    controller: ProduceController,
    hasReference: Boolean,
    onMicDeclined: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.startRecording() else onMicDeclined()
    }
    val recording = state.phase == ProducePhase.RECORDING
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (hasReference) {
            FilledTonalIconButton(onClick = controller::playReference, modifier = Modifier.size(64.dp).testTag("produce_reference"), shape = CircleShape) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.produce_reference))
            }
        }
        FilledIconButton(
            onClick = {
                when {
                    recording -> controller.stopRecording()
                    controller.hasPermission() -> controller.startRecording()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = state.phase != ProducePhase.ANALYSING,
            modifier = Modifier.size(80.dp).testTag("produce_record"),
            shape = CircleShape,
        ) {
            if (recording) StopSquare() else Icon(MicIcon, contentDescription = stringResource(R.string.produce_record), Modifier.size(36.dp))
        }
        if (state.hasRecording && state.phase == ProducePhase.DONE) {
            OutlinedButton(onClick = controller::playAB, modifier = Modifier.testTag("produce_ab")) {
                Text(stringResource(if (state.nextIsUser || !hasReference) R.string.produce_play_yours else R.string.produce_play_reference))
            }
        }
    }
}

@Composable
private fun StopSquare() {
    val description = stringResource(R.string.produce_stop)
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) { drawRect(Color.White) }
}

/** Three self-ratings. Neutral styling throughout: the learner judges, the app does not. */
@Composable
fun SelfRatingRow(onRate: (SelfRating) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((rating, label) in listOf(
            SelfRating.NEEDS_WORK to R.string.produce_needs_work,
            SelfRating.GETTING_THERE to R.string.produce_getting_there,
            SelfRating.SOUNDS_RIGHT to R.string.produce_sounds_right,
        )) {
            FilledTonalButton(onClick = { onRate(rating) }, modifier = Modifier.weight(1f).height(64.dp).testTag("rate_${rating.name}")) {
                Text(stringResource(label), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Material's core icon set has no microphone; a plain one, drawn here. */
val MicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 14f)
        curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
        verticalLineTo(5f)
        curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
        curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
        verticalLineTo(11f)
        curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
        close()
        moveTo(17.3f, 11f)
        curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
        curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
        horizontalLineTo(5f)
        curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
        verticalLineTo(21f)
        horizontalLineTo(13f)
        verticalLineTo(17.72f)
        curveTo(16.28f, 17.24f, 19f, 14.42f, 19f, 11f)
        horizontalLineTo(17.3f)
        close()
    }.build()
}
