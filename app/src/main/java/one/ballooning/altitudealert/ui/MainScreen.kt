package one.ballooning.altitudealert.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Altitude Alert") },
                actions = {
                    IconButton(onClick = { onAction(MainAction.NavigateToAdvancedSettings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val scrollState = rememberScrollState()

            if (maxWidth >= 840.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LiveStatusCard(uiState)
                        MonitoringCard(uiState, onAction)
                        BandAlertCard(uiState, onAction)
                        if (uiState.maxAltitudeEnabled) {
                            MaxAltitudeCard(uiState, onAction)
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AltitudeBandCard(uiState, onAction)
                        if (uiState.hasBarometer) {
                            SourceCard(uiState, onAction)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LiveStatusCard(uiState)
                    MonitoringCard(uiState, onAction)
                    BandAlertCard(uiState, onAction)
                    if (uiState.maxAltitudeEnabled) {
                        MaxAltitudeCard(uiState, onAction)
                    }
                    AltitudeBandCard(uiState, onAction)
                    if (uiState.hasBarometer) {
                        SourceCard(uiState, onAction)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardHeader(
    title: String,
    subtitle: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
fun WarningRow(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
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

// ─────────────────────────────────────────────────────────────────────────────
// Live altitude card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveStatusCard(uiState: MainUiState) {
    val status = uiState.liveStatus
    val overallStatus = status.alertResult?.overallStatus

    val statusColor by animateColorAsState(
        targetValue = when (overallStatus) {
            AlertStatus.APPROACHING -> MaterialTheme.colorScheme.tertiary
            AlertStatus.CROSSED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(500),
        label = "statusColor",
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CardHeader("Current altitude")

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = status.altitudeFeet?.let { "%,d ft".format(it.toInt()) } ?: "– – –",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )

                status.flightLevel?.let { fl ->
                    Text(
                        text = "FL%03d".format(fl),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (status.altitudeSource == AltitudeSourceType.GPS) {
                val accStr = status.gpsVerticalAccuracyFeet?.let { " ±${it.toInt()} ft" } ?: ""
                val (gpsLabel, isWarning) = when (status.gpsAccuracyStatus) {
                    GpsAccuracyStatus.LOST -> "GPS signal lost" to true
                    GpsAccuracyStatus.LOW -> "GPS low accuracy$accStr" to uiState.warnOnLowAccuracy
                    else -> "GPS$accStr" to false
                }

                StatusPill(
                    text = gpsLabel,
                    icon = if (isWarning) Icons.Outlined.GpsOff else Icons.Outlined.GpsFixed,
                    containerColor = if (isWarning) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isWarning) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (uiState.monitorReadiness == MonitorReadiness.MISSING_ALTITUDE_SOURCE) {
                WarningRow("No altitude source available")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Monitoring card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonitoringCard(
    uiState: MainUiState,
    onAction: (MainAction) -> Unit,
) {
    val readiness = uiState.monitorReadiness
    val monitoringActive = uiState.alertsEnabled
    val hasAltitude = uiState.liveStatus.altitudeFeet != null

    val canEnable = readiness == MonitorReadiness.READY &&
            uiState.isConfigValid &&
            hasAltitude

    val (statusText, detailText, icon, tint) = when {
        monitoringActive -> Quadruple(
            "Monitoring active",
            "Alerts are currently enabled.",
            Icons.Outlined.NotificationsActive,
            MaterialTheme.colorScheme.primary,
        )

        readiness == MonitorReadiness.MISSING_PERMISSIONS -> Quadruple(
            "Permissions required",
            "Location and notification access must be allowed.",
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
        )

        readiness == MonitorReadiness.MISSING_ALTITUDE_SOURCE -> Quadruple(
            "No altitude source",
            "Barometer or GPS data is required before monitoring can start.",
            Icons.Outlined.GpsOff,
            MaterialTheme.colorScheme.error,
        )

        !uiState.isConfigValid -> Quadruple(
            "Configuration incomplete",
            "Fix the altitude band settings before enabling monitoring.",
            Icons.Default.Warning,
            MaterialTheme.colorScheme.tertiary,
        )

        !hasAltitude -> Quadruple(
            "Waiting for altitude",
            "The app is ready, but no altitude reading is available yet.",
            Icons.Outlined.GpsOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Quadruple(
            "Monitoring off",
            "Everything is ready. You can enable monitoring now.",
            Icons.Outlined.Notifications,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardHeader("Monitoring")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.12f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { onAction(MainAction.ToggleAlerts) },
                enabled = monitoringActive || canEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = if (monitoringActive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    text = if (monitoringActive) "Disable monitoring" else "Enable monitoring",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (readiness == MonitorReadiness.MISSING_PERMISSIONS) {
                OutlinedButton(
                    onClick = {
                        if (!uiState.permissionsPreviouslyDenied) {
                            onAction(MainAction.RequestPermissions)
                        } else {
                            onAction(MainAction.OpenAppSettings)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (!uiState.permissionsPreviouslyDenied) {
                            "Grant permissions"
                        } else {
                            "Open app settings"
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Band alert card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BandAlertCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    val status = uiState.liveStatus
    val overallStatus = status.alertResult?.overallStatus
    val isCrossedUnmuted = uiState.alertsEnabled &&
            overallStatus == AlertStatus.CROSSED &&
            !uiState.crossingAlarmMuted

    val statusColor by animateColorAsState(
        targetValue = when (overallStatus) {
            AlertStatus.APPROACHING -> MaterialTheme.colorScheme.tertiary
            AlertStatus.CROSSED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(500),
        label = "bandStatusColor",
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardHeader("Altitude band")

            if (uiState.isConfigValid) {
                val lowerFeet = remember(uiState.bandLowerAltitudeFeet) {
                    uiState.bandLowerAltitudeFeet.toFloatOrNull() ?: 0f
                }
                val upperFeet = remember(uiState.bandUpperAltitudeFeet) {
                    uiState.bandUpperAltitudeFeet.toFloatOrNull() ?: 0f
                }
                val thresholdFeet = remember(uiState.approachThresholdFeet) {
                    uiState.approachThresholdFeet.toFloatOrNull() ?: 200f
                }

                BandIndicator(
                    altitudeFeet = status.altitudeFeet,
                    lowerFeet = lowerFeet,
                    upperFeet = upperFeet,
                    thresholdFeet = if (uiState.thresholdAlertEnabled) thresholdFeet else 0f,
                    markerColor = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    surfaceColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.alertsEnabled && overallStatus != null) {
                val (chipBg, chipFg, label, icon) = when (overallStatus) {
                    AlertStatus.CLEAR -> Quadruple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        "Within limits",
                        Icons.Outlined.CheckCircle,
                    )

                    AlertStatus.APPROACHING -> Quadruple(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        approachingLabel(uiState),
                        Icons.Outlined.NotificationsActive,
                    )

                    AlertStatus.CROSSED -> Quadruple(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        crossedLabel(uiState),
                        Icons.Default.Warning,
                    )
                }

                StatusPill(
                    text = label,
                    icon = icon,
                    containerColor = chipBg,
                    contentColor = chipFg,
                )
            }

            if (isCrossedUnmuted) {
                OutlinedButton(
                    onClick = { onAction(MainAction.MuteAlarm) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Mute alarm")
                }
            }
        }
    }
}

private fun approachingLabel(uiState: MainUiState): String {
    val result = uiState.liveStatus.alertResult ?: return "Approaching limit"
    val closest = listOfNotNull(result.lower, result.upper)
        .filter { it.status == AlertStatus.APPROACHING }
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

// ─────────────────────────────────────────────────────────────────────────────
// Max altitude card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MaxAltitudeCard(
    uiState: MainUiState,
    onAction: (MainAction) -> Unit,
) {
    val status = uiState.liveStatus
    val maxAltitudeText = status.sessionMaxFeet?.let { "%,d ft".format(it.toInt()) } ?: "–"
    val timestampText = remember(uiState.sessionMaxTimestampMs) {
        uiState.sessionMaxTimestampMs?.let { formatUtcTimestamp(it) } ?: "–"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardHeader("Max altitude")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = maxAltitudeText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = "Recorded at $timestampText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            MaxAltitudeAlarmStatusRow(uiState, onAction)
        }
    }
}

@Composable
private fun MaxAltitudeAlarmStatusRow(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    when {
        uiState.maxAltitudeAlarmActive -> {
            Button(
                onClick = { onAction(MainAction.AcknowledgeMaxAltitude) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Acknowledge alarm", fontWeight = FontWeight.SemiBold)
            }
        }

        uiState.maxAltitudeSilenced -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Alarm silenced", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = false),
                )
                TextButton(onClick = { onAction(MainAction.UnsilenceMaxAltitude) }) {
                    Text("Unsilence")
                }
            }
        }

        else -> {
            StatusPill(
                text = "Alarm armed",
                icon = Icons.Outlined.Notifications,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun formatUtcTimestamp(timestampMs: Long): String {
    val dt = Instant.ofEpochMilli(timestampMs).atOffset(ZoneOffset.UTC)
    val time = DateTimeFormatter.ofPattern("HH:mm").format(dt)
    val date = DateTimeFormatter.ofPattern("dd.MM.yyyy").format(dt)
    return "$time UTC · $date"
}

// ─────────────────────────────────────────────────────────────────────────────
// Source card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardHeader(
                title = "Source",
                subtitle = "Choose the preferred altitude source.",
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.preferredSource == PreferredSource.BAROMETER,
                    onClick = { onAction(MainAction.SetPreferredSource(PreferredSource.BAROMETER)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text("Barometer")
                }

                SegmentedButton(
                    selected = uiState.preferredSource == PreferredSource.GPS,
                    onClick = { onAction(MainAction.SetPreferredSource(PreferredSource.GPS)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text("GPS")
                }
            }

            if (uiState.showQnh) {
                val v = uiState.qnhValidation

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Pressure setting",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OutlinedTextField(
                            value = uiState.qnhHpa,
                            onValueChange = { s ->
                                val valid = s.all { it.isDigit() || it == '.' } &&
                                        s.count { it == '.' } <= 1 &&
                                        s.length <= 7
                                if (valid) {
                                    onAction(MainAction.UpdateQnh(s))
                                }
                            },
                            label = { Text("QNH (hPa)") },
                            singleLine = true,
                            isError = !v.isValid,
                            supportingText = v.errorMessage?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            trailingIcon = {
                                if (uiState.qnhHpa.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onAction(MainAction.UpdateQnh("")) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear QNH",
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )

                        FilledTonalButton(
                            onClick = { onAction(MainAction.UpdateQnh("1013.25")) },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp),
                        ) {
                            Text(
                                text = "STD",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Altitude band settings card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AltitudeBandCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardHeader(
                title = "Altitude band",
                subtitle = "Enter feet or a flight level such as 65 for FL065.",
            )

            val upperV = uiState.bandUpperAltitudeValidation
            OutlinedTextField(
                value = uiState.bandUpperAltitudeFeet,
                onValueChange = { onAction(MainAction.UpdateBandUpperAltitude(it)) },
                label = { Text("Upper limit (ft)") },
                singleLine = true,
                isError = !upperV.isValid,
                supportingText = {
                    val errorMessage = upperV.errorMessage
                    if (errorMessage != null) {
                        Text(errorMessage)
                    } else {
                        flightLevelHint(uiState.bandUpperAltitudeFeet)?.let { Text(it) }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    if (uiState.bandUpperAltitudeFeet.isNotEmpty()) {
                        IconButton(
                            onClick = { onAction(MainAction.UpdateBandUpperAltitude("")) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear upper limit",
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (!focus.isFocused) {
                            onAction(
                                MainAction.UpdateBandUpperAltitude(
                                    flToFeet(uiState.bandUpperAltitudeFeet),
                                ),
                            )
                        }
                    },
            )

            val lowerV = uiState.bandLowerAltitudeValidation
            OutlinedTextField(
                value = uiState.bandLowerAltitudeFeet,
                onValueChange = { onAction(MainAction.UpdateBandLowerAltitude(it)) },
                label = { Text("Lower limit (ft)") },
                singleLine = true,
                isError = !lowerV.isValid,
                supportingText = {
                    val errorMessage = lowerV.errorMessage
                    if (errorMessage != null) {
                        Text(errorMessage)
                    } else {
                        flightLevelHint(uiState.bandLowerAltitudeFeet)?.let { Text(it) }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    if (uiState.bandLowerAltitudeFeet.isNotEmpty()) {
                        IconButton(
                            onClick = { onAction(MainAction.UpdateBandLowerAltitude("")) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear lower limit",
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (!focus.isFocused) {
                            onAction(
                                MainAction.UpdateBandLowerAltitude(
                                    flToFeet(uiState.bandLowerAltitudeFeet),
                                ),
                            )
                        }
                    },
            )
        }
    }
}

private fun flToFeet(raw: String): String {
    val n = raw.trim().toIntOrNull()
    return if (n != null && n in 1..600) {
        (n * 100).toString()
    } else {
        raw
    }
}

private fun flightLevelHint(value: String): String? {
    val n = value.trim().toIntOrNull() ?: return null
    if (n !in 1..600) return null
    return "FL%03d = %,d ft".format(n, n * 100)
}

// ─────────────────────────────────────────────────────────────────────────────
// Band indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BandIndicator(
    altitudeFeet: Float?,
    lowerFeet: Float,
    upperFeet: Float,
    thresholdFeet: Float,
    markerColor: Color,
    trackColor: Color,
    surfaceColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelArgb = labelColor.toArgb()

    val approachColor = markerColor.copy(alpha = 0.30f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        val bandRange = (upperFeet - lowerFeet).coerceAtLeast(1f)
        val margin = (bandRange * 0.30f).coerceAtLeast(150f)
        val rangeMin = lowerFeet - margin
        val rangeMax = upperFeet + margin
        val rangeWidth = rangeMax - rangeMin

        fun toX(alt: Float) =
            ((alt - rangeMin) / rangeWidth * size.width).coerceIn(0f, size.width)

        val trackH = 14.dp.toPx()
        val trackTop = 4.dp.toPx()
        val trackBottom = trackTop + trackH
        val trackCy = trackTop + trackH / 2f

        val xLo = toX(lowerFeet)
        val xHi = toX(upperFeet)
        val xLoT = toX(lowerFeet + thresholdFeet).coerceIn(xLo, xHi)
        val xHiT = toX(upperFeet - thresholdFeet).coerceIn(xLo, xHi)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2),
        )

        if (xLoT > xLo) {
            drawRoundRect(
                color = approachColor,
                topLeft = Offset(xLo, trackTop),
                size = Size(xLoT - xLo, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
        }

        if (xHi > xHiT) {
            drawRoundRect(
                color = approachColor,
                topLeft = Offset(xHiT, trackTop),
                size = Size(xHi - xHiT, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
        }

        if (xHiT > xLoT) {
            drawRect(
                color = markerColor,
                topLeft = Offset(xLoT, trackTop),
                size = Size(xHiT - xLoT, trackH),
            )
        }

        val divW = 1.5.dp.toPx()
        if (xLoT - xLo > 3.dp.toPx()) {
            drawRect(
                color = surfaceColor.copy(alpha = 0.6f),
                topLeft = Offset(xLoT - divW / 2, trackTop),
                size = Size(divW, trackH),
            )
        }
        if (xHi - xHiT > 3.dp.toPx()) {
            drawRect(
                color = surfaceColor.copy(alpha = 0.6f),
                topLeft = Offset(xHiT - divW / 2, trackTop),
                size = Size(divW, trackH),
            )
        }

        val tickTop = trackBottom + 2.dp.toPx()
        val tickH = 4.dp.toPx()
        val tickW = 2.dp.toPx()
        val tickColor = labelColor.copy(alpha = 0.6f)

        drawRect(
            color = tickColor,
            topLeft = Offset(xLo - tickW / 2, tickTop),
            size = Size(tickW, tickH),
        )
        drawRect(
            color = tickColor,
            topLeft = Offset(xHi - tickW / 2, tickTop),
            size = Size(tickW, tickH),
        )

        if (altitudeFeet != null) {
            val cx = toX(altitudeFeet)
            val r = trackH / 2f + 2.dp.toPx()
            drawCircle(color = markerColor, radius = r, center = Offset(cx, trackCy))
            drawCircle(color = surfaceColor, radius = r * 0.45f, center = Offset(cx, trackCy))
        }

        val labelPaint = Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 10.sp.toPx() }
            color = labelArgb
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val labelY = size.height - 1.dp.toPx()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                "%,d ft".format(lowerFeet.toInt()),
                xLo,
                labelY,
                labelPaint,
            )
            canvas.nativeCanvas.drawText(
                "%,d ft".format(upperFeet.toInt()),
                xHi,
                labelY,
                labelPaint,
            )
        }
    }
}

// Small helper because Kotlin does not ship one.
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)