package one.ballooning.altitudealert.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import one.ballooning.altitudealert.data.model.PreferredSource
import one.ballooning.altitudealert.domain.AlertStatus
import one.ballooning.altitudealert.domain.AltitudeSourceType
import one.ballooning.altitudealert.domain.GpsAccuracyStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier, topBar = {
            TopAppBar(title = { Text("Altitude Alert") }, actions = {
                IconButton(onClick = { onAction(MainAction.NavigateToAdvancedSettings) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            })
        }) { padding ->
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
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LiveStatusCard(uiState, onAction)
                        AlertsCard(uiState, onAction)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SourceCard(uiState, onAction)
                        AltitudeBandCard(uiState, onAction)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LiveStatusCard(uiState, onAction)
                    AlertsCard(uiState, onAction)
                    SourceCard(uiState, onAction)
                    AltitudeBandCard(uiState, onAction)
                }
            }
        }
    }
}

// ─── Live status card ─────────────────────────────────────────────────────────

@Composable
private fun LiveStatusCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    val status = uiState.liveStatus
    val overallStatus = status.alertResult?.overallStatus

    val accentColor by animateColorAsState(
        targetValue = when (overallStatus) {
            AlertStatus.APPROACHING -> Color(0xFFFFB300)
            AlertStatus.CROSSED -> Color(0xFFE53935)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(600),
        label = "accentColor",
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Altitude ───────────────────────────────────────────────────────
            val altText = when {
                status.flightLevel != null && uiState.referenceMode == ReferenceMode.FLIGHT_LEVEL -> "FL%03d".format(
                    status.flightLevel
                )

                status.altitudeFeet != null -> "%,d ft".format(status.altitudeFeet.toInt())

                else -> "– – –"
            }

            Text(
                text = altText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )

            // ── GPS accuracy (when GPS is active source) ───────────────────────
            if (status.altitudeSource == AltitudeSourceType.GPS) {
                val accStr = status.gpsVerticalAccuracyFeet?.let { " ±${it.toInt()} ft" } ?: ""
                val (label, isWarning) = when (status.gpsAccuracyStatus) {
                    GpsAccuracyStatus.LOST -> "GPS signal lost" to true
                    GpsAccuracyStatus.LOW -> "GPS low accuracy$accStr" to true
                    else -> "GPS$accStr" to false
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GpsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isWarning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWarning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── No source available ────────────────────────────────────────────
            if (uiState.monitorReadiness == MonitorReadiness.MISSING_ALTITUDE_SOURCE) {
                WarningRow("No altitude source available")
            }

            // ── Alert status chips ─────────────────────────────────────────────
            if (uiState.alertsEnabled && overallStatus != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (overallStatus) {
                        AlertStatus.CLEAR -> AssistChip(
                            onClick = {},
                            label = { Text("Within limits") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            })

                        AlertStatus.APPROACHING -> AssistChip(
                            onClick = {},
                            label = { Text(approachingLabel(uiState)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    tint = Color(0xFFFFB300),
                                )
                            })

                        AlertStatus.CROSSED -> {
                            AssistChip(
                                onClick = {},
                                label = { Text(crossedLabel(uiState)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        tint = Color(0xFFE53935),
                                    )
                                })
                            if (!uiState.crossingAlarmMuted) {
                                InputChip(
                                    selected = false,
                                    onClick = { onAction(MainAction.MuteAlarm) },
                                    label = { Text("Mute alarm") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.VolumeOff,
                                            contentDescription = "Mute alarm",
                                            modifier = Modifier.size(InputChipDefaults.IconSize),
                                        )
                                    })
                            }
                        }
                    }
                }
            }

            // ── Session max altitude ───────────────────────────────────────────
            val sessionMaxFeet = status.sessionMaxFeet
            if (sessionMaxFeet != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Session max",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%,d ft".format(sessionMaxFeet.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun approachingLabel(uiState: MainUiState): String {
    val result = uiState.liveStatus.alertResult ?: return "Approaching limit"
    val closest =
        listOfNotNull(result.lower, result.upper).filter { it.status == AlertStatus.APPROACHING }
            .minByOrNull { it.distanceFeet } ?: return "Approaching limit"
    return "Approaching — %,d ft".format(closest.distanceFeet.toInt())
}

private fun crossedLabel(uiState: MainUiState): String {
    val result = uiState.liveStatus.alertResult ?: return "Limit crossed"
    val lowerCrossed = result.lower?.status == AlertStatus.CROSSED
    val upperCrossed = result.upper?.status == AlertStatus.CROSSED
    return when {
        lowerCrossed && upperCrossed -> "Outside band"
        lowerCrossed -> "Below lower limit"
        upperCrossed -> "Above upper limit"
        else -> "Limit crossed"
    }
}

// ─── Alerts card ──────────────────────────────────────────────────────────────

@Composable
private fun AlertsCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Alerts", style = MaterialTheme.typography.titleLarge)

            when (uiState.monitorReadiness) {
                MonitorReadiness.MISSING_PERMISSIONS -> {
                    Text(
                        "Location and notification permissions are required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // Inline grant button — saves opening the dialog first
                    if (!uiState.permissionsPreviouslyDenied) {
                        OutlinedButton(
                            onClick = { onAction(MainAction.RequestPermissions) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Grant permissions") }
                    } else {
                        // Previously denied — system won't show dialog, must go to settings
                        OutlinedButton(
                            onClick = { onAction(MainAction.OpenAppSettings) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open app settings") }
                    }
                }

                MonitorReadiness.MISSING_ALTITUDE_SOURCE -> Text(
                    "No altitude source available — no barometer and location permission not granted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                MonitorReadiness.READY -> {
                    if (!uiState.isConfigValid) {
                        Text(
                            "Fix the configuration errors before enabling alerts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            if (uiState.alertsEnabled) "Alerts are active." else "Alerts are disabled.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Button(
                onClick = { onAction(MainAction.ToggleAlerts) },
                enabled = uiState.alertsEnabled || (uiState.monitorReadiness == MonitorReadiness.READY && uiState.isConfigValid),
                modifier = Modifier.fillMaxWidth(),
                colors = if (uiState.alertsEnabled) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(),
            ) {
                Text(if (uiState.alertsEnabled) "Disable alerts" else "Enable alerts")
            }
        }
    }
}

// ─── Source card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Source", style = MaterialTheme.typography.titleLarge)

            // Source toggle — only shown if barometer is present
            if (uiState.hasBarometer) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.preferredSource == PreferredSource.BAROMETER,
                        onClick = { onAction(MainAction.SetPreferredSource(PreferredSource.BAROMETER)) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Barometer") }
                    SegmentedButton(
                        selected = uiState.preferredSource == PreferredSource.GPS,
                        onClick = { onAction(MainAction.SetPreferredSource(PreferredSource.GPS)) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("GPS") }
                }
            }

            // QNH — only meaningful for barometer source
            if (uiState.showQnh) {
                val v = uiState.qnhValidation
                OutlinedTextField(
                    value = uiState.qnhHpa,
                    onValueChange = { s ->
                        if (s.all { it.isDigit() } && s.length <= 4) onAction(MainAction.UpdateQnh(s))
                    },
                    label = { Text("QNH (hPa)") },
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

// ─── Altitude band card ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AltitudeBandCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Altitude band", style = MaterialTheme.typography.titleLarge)

            // Reference mode
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.referenceMode == ReferenceMode.ALTITUDE,
                    onClick = { onAction(MainAction.SetReferenceMode(ReferenceMode.ALTITUDE)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Altitude") }
                SegmentedButton(
                    selected = uiState.referenceMode == ReferenceMode.FLIGHT_LEVEL,
                    onClick = { onAction(MainAction.SetReferenceMode(ReferenceMode.FLIGHT_LEVEL)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Flight level") }
            }

            // Limit inputs
            if (uiState.referenceMode == ReferenceMode.ALTITUDE) {
                val lowerV = uiState.bandLowerAltitudeValidation
                OutlinedTextField(
                    value = uiState.bandLowerAltitudeFeet,
                    onValueChange = { onAction(MainAction.UpdateBandLowerAltitude(it)) },
                    label = { Text("Lower limit (ft)") },
                    singleLine = true,
                    isError = !lowerV.isValid,
                    supportingText = lowerV.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val upperV = uiState.bandUpperAltitudeValidation
                OutlinedTextField(
                    value = uiState.bandUpperAltitudeFeet,
                    onValueChange = { onAction(MainAction.UpdateBandUpperAltitude(it)) },
                    label = { Text("Upper limit (ft)") },
                    singleLine = true,
                    isError = !upperV.isValid,
                    supportingText = upperV.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val lowerV = uiState.bandLowerFLValidation
                OutlinedTextField(
                    value = uiState.bandLowerFlightLevel,
                    onValueChange = { onAction(MainAction.UpdateBandLowerFlightLevel(it)) },
                    label = { Text("Lower flight level") },
                    singleLine = true,
                    isError = !lowerV.isValid,
                    supportingText = lowerV.errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val upperV = uiState.bandUpperFLValidation
                OutlinedTextField(
                    value = uiState.bandUpperFlightLevel,
                    onValueChange = { onAction(MainAction.UpdateBandUpperFlightLevel(it)) },
                    label = { Text("Upper flight level") },
                    singleLine = true,
                    isError = !upperV.isValid,
                    supportingText = upperV.errorMessage?.let { { Text(it) } },
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

// ─── Shared ───────────────────────────────────────────────────────────────────

@Composable
fun WarningRow(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}