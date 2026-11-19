package `in`.qaida.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.qaida.app.AppContainer
import `in`.qaida.app.R
import `in`.qaida.app.data.settings.Language
import `in`.qaida.app.data.settings.Settings
import `in`.qaida.app.reminder.Reminder
import `in`.qaida.app.ui.common.ScreenScaffold
import `in`.qaida.app.ui.theme.ScriptFace
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun update(f: (Settings) -> Settings) = scope.launch { container.settingsRepository.update(f) }
    var confirmReset by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    fun enableReminder() {
        update { it.copy(reminderEnabled = true) }
        Reminder.schedule(context, settings.reminderMinuteOfDay)
    }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableReminder()
    }

    ScreenScaffold(stringResource(R.string.settings_title), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Section(stringResource(R.string.settings_language)) {
                Choice(listOf(Language.EN to R.string.language_english, Language.ML to R.string.language_malayalam), settings.uiLanguage, "ui_language") { l ->
                    update { it.copy(uiLanguage = l) }
                }
            }
            Section(stringResource(R.string.settings_instruction_language)) {
                Choice(listOf(Language.EN to R.string.language_english, Language.ML to R.string.language_malayalam), settings.instructionLanguage, "instruction_language") { l ->
                    update { it.copy(instructionLanguage = l) }
                }
            }
            Section(stringResource(R.string.settings_script)) {
                Choice(listOf(ScriptFace.NASTALEEQ to R.string.face_nastaleeq, ScriptFace.NASKH to R.string.face_naskh), settings.scriptFace, "script_face") { f ->
                    update { it.copy(scriptFace = f) }
                }
            }
            Section(stringResource(R.string.settings_session_length)) {
                Choice(Settings.SESSION_LENGTHS.map { it to null }, settings.sessionSeconds, "session_length", label = { stringResource(R.string.seconds_n, it) }) { n ->
                    update { it.copy(sessionSeconds = n) }
                }
            }
            Section(stringResource(R.string.settings_text_size)) {
                Choice(
                    Settings.TEXT_SCALES.zip(listOf(R.string.text_size_normal, R.string.text_size_large, R.string.text_size_largest)),
                    settings.textScale, "text_size",
                ) { t -> update { it.copy(textScale = t) } }
            }
            Section(stringResource(R.string.settings_speaking)) {
                SwitchRow(stringResource(R.string.settings_speaking_on), !settings.micDeclined, "speaking") { on ->
                    update { it.copy(micDeclined = !on) }
                }
            }
            Section(stringResource(R.string.settings_reminder)) {
                SwitchRow(stringResource(R.string.settings_reminder_on), settings.reminderEnabled, "reminder") { on ->
                    if (!on) {
                        update { it.copy(reminderEnabled = false) }
                        Reminder.cancel(context)
                    } else if (Build.VERSION.SDK_INT >= 33 && !Reminder.canNotify(context)) {
                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        enableReminder()
                    }
                }
                if (settings.reminderEnabled) {
                    TextButton(onClick = { pickTime = true }) {
                        Text(stringResource(R.string.settings_reminder_time, formatTime(settings.reminderMinuteOfDay)))
                    }
                }
            }
            Section(stringResource(R.string.settings_privacy)) {
                Text(stringResource(R.string.settings_privacy_text), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().testTag("reset")) {
                Text(stringResource(R.string.settings_reset))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        container.database.progressDao().clearProgress()
                        container.recorder.deleteAll()
                    }
                }, modifier = Modifier.testTag("reset_confirm")) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (pickTime) {
        val state = rememberTimePickerState(settings.reminderMinuteOfDay / 60, settings.reminderMinuteOfDay % 60)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    val minute = state.hour * 60 + state.minute
                    update { it.copy(reminderMinuteOfDay = minute) }
                    Reminder.schedule(context, minute)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun formatTime(minuteOfDay: Int) = String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun <T> Choice(
    options: List<Pair<T, Int?>>,
    selected: T,
    tag: String,
    label: @Composable (T) -> String = { "" },
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, res) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                modifier = Modifier.testTag("${tag}_$i"),
            ) { Text(res?.let { stringResource(it) } ?: label(value)) }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(4.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
