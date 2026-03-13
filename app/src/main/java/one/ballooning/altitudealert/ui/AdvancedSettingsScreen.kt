package one.ballooning.altitudealert.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                        ApproachAlertCard(uiState, onAction)
                        MaxAltitudeAlertCard(uiState, onAction)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AlarmCard(uiState, onAction)
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
                    ApproachAlertCard(uiState, onAction)
                    MaxAltitudeAlertCard(uiState, onAction)
                    AlarmCard(uiState, onAction)
                    DiagnosticsCard(uiState, onAction)
                }
            }
        }
    }
}

// ─── Approach alert card ──────────────────────────────────────────────────────

@Composable
private fun ApproachAlertCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Approach alert", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alert when the altitude comes within the threshold distance of a band edge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                label = "Enable",
                checked = uiState.thresholdAlertEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetThresholdAlertEnabled(it)) },
            )
            if (uiState.thresholdAlertEnabled) {
                val v = uiState.approachThresholdValidation
                OutlinedTextField(
                    value = uiState.approachThresholdFeet,
                    onValueChange = { onAction(AdvancedAction.SetDistanceThresholdFeet(it)) },
                    label = { Text("Threshold distance (ft)") },
                    singleLine = true,
                    isError = !v.isValid,
                    supportingText = v.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Crossing alarm card ──────────────────────────────────────────────────────

@Composable
private fun AlarmCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Alarms", style = MaterialTheme.typography.titleMedium)
            SwitchRow(
                label = "Sound",
                checked = uiState.soundEnabled,
                enabled = uiState.vibrateEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetSoundEnabled(it)) },
            )
            SwitchRow(
                label = "Vibration",
                checked = uiState.vibrateEnabled,
                enabled = uiState.soundEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetVibrateEnabled(it)) },
            )
        }
    }
}

// ─── Max altitude alert card ──────────────────────────────────────────────────

@Composable
private fun MaxAltitudeAlertCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Max altitude alert", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alert when the maximum altitude increases by the exceedance margin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                label = "Enable",
                checked = uiState.maxAltitudeEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetMaxAltitudeAlertEnabled(it)) },
            )
            if (uiState.maxAltitudeEnabled) {
                val threshV = uiState.maxAltitudeThresholdValidation
                OutlinedTextField(
                    value = uiState.maxAltitudeThresholdFeet,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeThreshold(it)) },
                    label = { Text("Exceedance margin (ft)") },
                    singleLine = true,
                    isError = !threshV.isValid,
                    supportingText = threshV.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val minAltV = uiState.maxAltitudeMinAltitudeValidation
                OutlinedTextField(
                    value = uiState.maxAltitudeMinAltitudeFeet,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeMinAltitude(it)) },
                    label = { Text("Minimum altitude to track (ft)") },
                    singleLine = true,
                    isError = !minAltV.isValid,
                    supportingText = minAltV.errorMessage?.let { { Text(it) } }
                        ?: { Text("Avoids triggering near the ground") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val silenceV = uiState.maxAltitudeSilenceValidation
                OutlinedTextField(
                    value = uiState.maxAltitudeSilenceMinutes,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeSilenceMinutes(it)) },
                    label = { Text("Silence duration (minutes)") },
                    singleLine = true,
                    isError = !silenceV.isValid,
                    supportingText = silenceV.errorMessage?.let { { Text(it) } }
                        ?: { Text("How long the notification silence lasts") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Diagnostics card ─────────────────────────────────────────────────────────

@Composable
private fun DiagnosticsCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            SwitchRow(
                label = "Warn on low GPS accuracy",
                checked = uiState.warnOnLowAccuracy,
                onCheckedChange = { onAction(AdvancedAction.SetWarnOnLowAccuracy(it)) },
            )
        }
    }
}

// ─── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}