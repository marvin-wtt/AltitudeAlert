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
                        CrossingAlarmCard(uiState, onAction)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MaxAltitudeAlertCard(uiState, onAction)
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
                    CrossingAlarmCard(uiState, onAction)
                    MaxAltitudeAlertCard(uiState, onAction)
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
                "Three beeps when the altitude comes within the threshold distance of a band edge.",
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
private fun CrossingAlarmCard(uiState: MainUiState, onAction: (AdvancedAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Crossing alarm", style = MaterialTheme.typography.titleMedium)
            Text(
                "A continuous two-tone alarm sounds when the altitude exceeds the band limit. " +
                        "Mute it from the notification or the main screen. " +
                        "The alarm resets automatically when the altitude returns inside the band.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            SwitchRow(
                label = "Sound",
                checked = uiState.soundEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetSoundEnabled(it)) },
            )
            SwitchRow(
                label = "Vibration",
                checked = uiState.vibrateEnabled,
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
                "Continuous alarm when the balloon nears its maximum altitude. " +
                        "Alarm fires when within the alert threshold of the maximum altitude, " +
                        "and re-arms after descending below the reactivation threshold.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow(
                label = "Enable",
                checked = uiState.maxAltitudeEnabled,
                onCheckedChange = { onAction(AdvancedAction.SetMaxAltitudeAlertEnabled(it)) },
            )
            if (uiState.maxAltitudeEnabled) {
                val alertV = uiState.maxAltitudeAlertThresholdValidation
                OutlinedTextField(
                    value = uiState.maxAltitudeAlertThresholdFeet,
                    onValueChange = { onAction(AdvancedAction.UpdateMaxAltitudeAlertThreshold(it)) },
                    label = { Text("Alert threshold (ft below max)") },
                    singleLine = true,
                    isError = !alertV.isValid,
                    supportingText = alertV.errorMessage?.let { { Text(it) } }
                        ?: { Text("Alarm fires when within this distance of the maximum altitude") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val reactV = uiState.maxAltitudeReactivationThresholdValidation
                OutlinedTextField(
                    value = uiState.maxAltitudeReactivationThresholdFeet,
                    onValueChange = {
                        onAction(
                            AdvancedAction.UpdateMaxAltitudeReactivationThreshold(
                                it
                            )
                        )
                    },
                    label = { Text("Reactivation threshold (ft below max)") },
                    singleLine = true,
                    isError = !reactV.isValid,
                    supportingText = reactV.errorMessage?.let { { Text(it) } }
                        ?: { Text("Alarm re-arms after descending this far below the max") },
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}