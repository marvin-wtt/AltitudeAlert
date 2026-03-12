package one.ballooning.altitudealert.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    uiState: MainUiState,
    onAction: (AdvancedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Advanced settings") },
                navigationIcon = {
                    IconButton(onClick = { onAction(AdvancedAction.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (maxWidth >= 840.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ThresholdCard(uiState, onAction)
                        MaxAltitudeAlertCard(uiState, onAction)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AlarmCard(
                            title            = "Threshold alarm",
                            config           = uiState.thresholdAlarm,
                            repeatValidation = uiState.thresholdRepeatValidation,
                            onSetSound       = { onAction(AdvancedAction.SetThresholdSoundEnabled(it)) },
                            onSetVibration   = { onAction(AdvancedAction.SetThresholdVibrationEnabled(it)) },
                            onSetRepeat      = { onAction(AdvancedAction.SetThresholdRepeatEnabled(it)) },
                            onUpdateRepeat   = { onAction(AdvancedAction.UpdateThresholdRepeatSeconds(it)) },
                        )
                        AlarmCard(
                            title            = "Crossing alarm",
                            config           = uiState.crossingAlarm,
                            repeatValidation = uiState.crossingRepeatValidation,
                            onSetSound       = { onAction(AdvancedAction.SetCrossingSoundEnabled(it)) },
                            onSetVibration   = { onAction(AdvancedAction.SetCrossingVibrationEnabled(it)) },
                            onSetRepeat      = { onAction(AdvancedAction.SetCrossingRepeatEnabled(it)) },
                            onUpdateRepeat   = { onAction(AdvancedAction.UpdateCrossingRepeatSeconds(it)) },
                        )
                        DiagnosticsCard(uiState, onAction)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ThresholdCard(uiState, onAction)
                    MaxAltitudeAlertCard(uiState, onAction)
                    AlarmCard(
                        title            = "Threshold alarm",
                        config           = uiState.thresholdAlarm,
                        repeatValidation = uiState.thresholdRepeatValidation,
                        onSetSound       = { onAction(AdvancedAction.SetThresholdSoundEnabled(it)) },
                        onSetVibration   = { onAction(AdvancedAction.SetThresholdVibrationEnabled(it)) },
                        onSetRepeat      = { onAction(AdvancedAction.SetThresholdRepeatEnabled(it)) },
                        onUpdateRepeat   = { onAction(AdvancedAction.UpdateThresholdRepeatSeconds(it)) },
                    )
                    AlarmCard(
                        title            = "Crossing alarm",
                        config           = uiState.crossingAlarm,
                        repeatValidation = uiState.crossingRepeatValidation,
                        onSetSound       = { onAction(AdvancedAction.SetCrossingSoundEnabled(it)) },
                        onSetVibration   = { onAction(AdvancedAction.SetCrossingVibrationEnabled(it)) },
                        onSetRepeat      = { onAction(AdvancedAction.SetCrossingRepeatEnabled(it)) },
                        onUpdateRepeat   = { onAction(AdvancedAction.UpdateCrossingRepeatSeconds(it)) },
                    )
                    DiagnosticsCard(uiState, onAction)
                }
            }
        }
    }
}

@Composable
private fun ThresholdCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Approach threshold")
            val v = uiState.approachThresholdValidation
            OutlinedTextField(
                value         = uiState.approachThresholdFeet,
                onValueChange = { onAction(AdvancedAction.SetDistanceThresholdFeet(it)) },
                label         = { Text("Distance (ft)") },
                singleLine    = true,
                isError       = !v.isValid,
                supportingText = v.errorMessage?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MaxAltitudeAlertCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Max altitude alert")

            SwitchRow(
                title   = "Enable",
                checked = uiState.maxAltitudeEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetMaxAltitudeAlertEnabled(it)) }
            )

            if (uiState.maxAltitudeEnabled) {
                val threshV = uiState.maxAltitudeThresholdValidation
                OutlinedTextField(
                    value         = uiState.maxAltitudeThresholdFeet,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeThreshold(it)) },
                    label         = { Text("Warn within (ft) of previous max") },
                    singleLine    = true,
                    isError       = !threshV.isValid,
                    supportingText = threshV.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val minAltV = uiState.maxAltitudeMinAltitudeValidation
                OutlinedTextField(
                    value         = uiState.maxAltitudeMinAltitudeFeet,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeMinAltitude(it)) },
                    label         = { Text("Minimum altitude to track (ft)") },
                    singleLine    = true,
                    isError       = !minAltV.isValid,
                    supportingText = minAltV.errorMessage?.let { { Text(it) } }
                        ?: { Text("Avoids triggering near the ground") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val silenceV = uiState.maxAltitudeSilenceValidation
                OutlinedTextField(
                    value         = uiState.maxAltitudeSilenceMinutes,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeSilenceMinutes(it)) },
                    label         = { Text("Silence duration (minutes)") },
                    singleLine    = true,
                    isError       = !silenceV.isValid,
                    supportingText = silenceV.errorMessage?.let { { Text(it) } }
                        ?: { Text("How long the notification silence action lasts") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AlarmCard(
    title: String,
    config: AlarmConfig,
    repeatValidation: ValidationResult,
    onSetSound: (Boolean) -> Unit,
    onSetVibration: (Boolean) -> Unit,
    onSetRepeat: (Boolean) -> Unit,
    onUpdateRepeat: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title)
            SwitchRow("Sound",     config.soundEnabled,     onSetSound)
            SwitchRow("Vibration", config.vibrationEnabled, onSetVibration)
            SwitchRow("Repeat",    config.repeatEnabled,    onSetRepeat)
            if (config.repeatEnabled) {
                OutlinedTextField(
                    value         = config.repeatSeconds,
                    onValueChange = onUpdateRepeat,
                    label         = { Text("Repeat interval (s)") },
                    singleLine    = true,
                    isError       = !repeatValidation.isValid,
                    supportingText = repeatValidation.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Diagnostics")
            SwitchRow(
                title           = "Warn on low GPS accuracy",
                checked         = uiState.warnOnLowAccuracy,
                onCheckedChange = { onAction(AdvancedAction.SetWarnOnLowAccuracy(it)) }
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}