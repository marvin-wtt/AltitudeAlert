package one.ballooning.altitudealert.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        AltitudeBandCard(uiState, onAction)
                        SourceCard(uiState, onAction)
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
                    AltitudeBandCard(uiState, onAction)
                    SourceCard(uiState, onAction)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Primary altitude readout ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = status.altitudeFeet
                        ?.let { "%,d ft".format(it.toInt()) }
                        ?: "– – –",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    lineHeight = 60.sp,
                )
                status.flightLevel?.let { fl ->
                    Text(
                        text = "FL%03d".format(fl),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Maximum altitude — own row, bracketed by dividers ─────────────
            status.sessionMaxFeet?.let { max ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Maximum altitude",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "%,d ft".format(max.toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider()
            }

            // ── Band position indicator ───────────────────────────────────────
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
                    thresholdFeet = thresholdFeet,
                    markerColor = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    surfaceColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Alert status chip ─────────────────────────────────────────────
            if (uiState.alertsEnabled && overallStatus != null) {
                val (chipContainerColor, chipLabelColor) = when (overallStatus) {
                    AlertStatus.CLEAR -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    AlertStatus.APPROACHING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    AlertStatus.CROSSED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (overallStatus) {
                                AlertStatus.CLEAR -> "Within limits"
                                AlertStatus.APPROACHING -> approachingLabel(uiState)
                                AlertStatus.CROSSED -> crossedLabel(uiState)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (overallStatus) {
                                AlertStatus.CLEAR -> Icons.Outlined.CheckCircle
                                AlertStatus.APPROACHING -> Icons.Outlined.NotificationsActive
                                AlertStatus.CROSSED -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = chipContainerColor,
                        labelColor = chipLabelColor,
                        leadingIconContentColor = chipLabelColor,
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = false),
                )
            }

            // ── GPS / source footer ───────────────────────────────────────────
            if (status.altitudeSource == AltitudeSourceType.GPS) {
                val accStr = status.gpsVerticalAccuracyFeet?.let { " ±${it.toInt()} ft" } ?: ""
                val (gpsLabel, isWarning) = when (status.gpsAccuracyStatus) {
                    GpsAccuracyStatus.LOST -> "GPS signal lost" to true
                    GpsAccuracyStatus.LOW -> "GPS low accuracy$accStr" to uiState.warnOnLowAccuracy
                    else -> "GPS$accStr" to false
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (isWarning) Icons.Outlined.GpsOff else Icons.Outlined.GpsFixed,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isWarning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = gpsLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWarning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── No source warning ─────────────────────────────────────────────
            if (uiState.monitorReadiness == MonitorReadiness.MISSING_ALTITUDE_SOURCE) {
                WarningRow("No altitude source available")
            }
        }
    }
}

/**
 * Band position indicator.
 *
 * Visual zones (left → right across the band):
 *   [ grey ] [ threshold zone ] [ safe centre ] [ threshold zone ] [ grey ]
 *
 * - Grey track: outside the band
 * - Threshold zones: band colour at 25% alpha  ← clearly distinct from centre
 * - Safe centre: band colour at full opacity
 * - Thin divider lines mark the threshold boundaries
 * - Circle marker: onSurface fill with surface ring
 * - Labels anchored below each band edge tick
 */
@Composable
private fun BandIndicator(
    altitudeFeet: Float?,
    lowerFeet: Float,
    upperFeet: Float,
    thresholdFeet: Float,
    markerColor: Color,   // animated status colour for the position marker
    trackColor: Color,
    surfaceColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelArgb = labelColor.toArgb()

    // Zone colours follow the animated status colour (same as marker)
    val safeColor = markerColor
    val approachColor = markerColor.copy(alpha = 0.30f)

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(56.dp)) {
        // ── Coordinate mapping ────────────────────────────────────────────────
        // Margin = 30% of band width each side → band fills ~62% of track width
        val bandRange = (upperFeet - lowerFeet).coerceAtLeast(1f)
        val margin = (bandRange * 0.30f).coerceAtLeast(150f)
        val rangeMin = lowerFeet - margin
        val rangeMax = upperFeet + margin
        val rangeWidth = rangeMax - rangeMin

        fun toX(alt: Float) = ((alt - rangeMin) / rangeWidth * size.width).coerceIn(0f, size.width)

        // ── Layout ────────────────────────────────────────────────────────────
        val trackH = 14.dp.toPx()
        val trackTop = 4.dp.toPx()
        val trackBottom = trackTop + trackH
        val trackCy = trackTop + trackH / 2f

        val xLo = toX(lowerFeet)
        val xHi = toX(upperFeet)
        val xLoT = toX(lowerFeet + thresholdFeet).coerceIn(xLo, xHi)
        val xHiT = toX(upperFeet - thresholdFeet).coerceIn(xLo, xHi)

        // ── Grey track (full width) ───────────────────────────────────────────
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2),
        )

        // ── Lower threshold zone (amber) ──────────────────────────────────────
        if (xLoT > xLo) drawRoundRect(
            color = approachColor,
            topLeft = Offset(xLo, trackTop),
            size = Size(xLoT - xLo, trackH),
            cornerRadius = CornerRadius(trackH / 2),
        )

        // ── Upper threshold zone (amber) ──────────────────────────────────────
        if (xHi > xHiT) drawRoundRect(
            color = approachColor,
            topLeft = Offset(xHiT, trackTop),
            size = Size(xHi - xHiT, trackH),
            cornerRadius = CornerRadius(trackH / 2),
        )

        // ── Safe centre (green) ───────────────────────────────────────────────
        if (xHiT > xLoT) drawRect(
            color = safeColor,
            topLeft = Offset(xLoT, trackTop),
            size = Size(xHiT - xLoT, trackH),
        )

        // ── Dividers between threshold and safe zones ─────────────────────────
        val divW = 1.5.dp.toPx()
        if (xLoT - xLo > 3.dp.toPx()) drawRect(
            color = surfaceColor.copy(alpha = 0.6f),
            topLeft = Offset(xLoT - divW / 2, trackTop),
            size = Size(divW, trackH),
        )
        if (xHi - xHiT > 3.dp.toPx()) drawRect(
            color = surfaceColor.copy(alpha = 0.6f),
            topLeft = Offset(xHiT - divW / 2, trackTop),
            size = Size(divW, trackH),
        )

        // ── Band-edge ticks ───────────────────────────────────────────────────
        val tickTop = trackBottom + 2.dp.toPx()
        val tickH = 4.dp.toPx()
        val tickW = 2.dp.toPx()
        val tickColor = labelColor.copy(alpha = 0.6f)
        drawRect(
            color = tickColor,
            topLeft = Offset(xLo - tickW / 2, tickTop),
            size = Size(tickW, tickH)
        )
        drawRect(
            color = tickColor,
            topLeft = Offset(xHi - tickW / 2, tickTop),
            size = Size(tickW, tickH)
        )

        // ── Position marker ───────────────────────────────────────────────────
        if (altitudeFeet != null) {
            val cx = toX(altitudeFeet)
            val r = trackH / 2f + 2.dp.toPx()
            drawCircle(color = markerColor, radius = r, center = Offset(cx, trackCy))
            drawCircle(color = surfaceColor, radius = r * 0.45f, center = Offset(cx, trackCy))
        }

        // ── Limit labels under ticks ──────────────────────────────────────────
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
                labelPaint
            )
            canvas.nativeCanvas.drawText(
                "%,d ft".format(upperFeet.toInt()),
                xHi,
                labelY,
                labelPaint
            )
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

// ─── Alerts card ──────────────────────────────────────────────────────────────

@Composable
private fun AlertsCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    val isCrossedUnmuted = uiState.alertsEnabled &&
            uiState.liveStatus.alertResult?.overallStatus == AlertStatus.CROSSED &&
            !uiState.crossingAlarmMuted

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Alerts", style = MaterialTheme.typography.titleLarge)

            // ── Status summary — always present ───────────────────────────────
            // Gives the button permanent context; never a lone control.
            val (summaryText, summaryIsError) = when (uiState.monitorReadiness) {
                MonitorReadiness.MISSING_PERMISSIONS ->
                    "Location and notification permissions are required." to true

                MonitorReadiness.MISSING_ALTITUDE_SOURCE ->
                    "No altitude source available." to true

                MonitorReadiness.READY -> when {
                    !uiState.isConfigValid ->
                        "Fix configuration errors before enabling alerts." to true

                    !uiState.alertsEnabled && uiState.liveStatus.altitudeFeet == null ->
                        "Waiting for altitude reading…" to false

                    uiState.alertsEnabled ->
                        "Alerts are active." to false

                    else ->
                        "Alerts are off." to false
                }
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (summaryIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Enable / disable button
            val canEnable = uiState.monitorReadiness == MonitorReadiness.READY &&
                    uiState.isConfigValid && uiState.liveStatus.altitudeFeet != null
            Button(
                onClick = { onAction(MainAction.ToggleAlerts) },
                enabled = uiState.alertsEnabled || canEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = if (uiState.alertsEnabled)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else
                    ButtonDefaults.buttonColors(),
            ) {
                Text(
                    text = if (uiState.alertsEnabled) "Disable alerts" else "Enable alerts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Permission grant / open settings
            if (uiState.monitorReadiness == MonitorReadiness.MISSING_PERMISSIONS) {
                if (!uiState.permissionsPreviouslyDenied) {
                    OutlinedButton(
                        onClick = { onAction(MainAction.RequestPermissions) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Grant permissions") }
                } else {
                    OutlinedButton(
                        onClick = { onAction(MainAction.OpenAppSettings) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open app settings") }
                }
            }

            // Mute — full-width button, only while crossing alarm is active
            if (isCrossedUnmuted) {
                OutlinedButton(
                    onClick = { onAction(MainAction.MuteAlarm) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeOff,
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

// ─── Source card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Source", style = MaterialTheme.typography.titleLarge)

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

@Composable
private fun AltitudeBandCard(uiState: MainUiState, onAction: (MainAction) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Altitude band", style = MaterialTheme.typography.titleLarge)
            Text(
                "Enter feet, or a flight level (e.g. 65 → FL065 = 6,500 ft).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val upperV = uiState.bandUpperAltitudeValidation
            OutlinedTextField(
                value = uiState.bandUpperAltitudeFeet,
                // Raw value stored as-is while typing; FL conversion happens on focus loss only.
                onValueChange = { onAction(MainAction.UpdateBandUpperAltitude(it)) },
                label = { Text("Upper limit") },
                singleLine = true,
                isError = !upperV.isValid,
                supportingText = {
                    val errorMessage = upperV.errorMessage
                    if (errorMessage != null) Text(errorMessage)
                    else flightLevelHint(uiState.bandUpperAltitudeFeet)?.let { Text(it) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (!focus.isFocused)
                            onAction(MainAction.UpdateBandUpperAltitude(flToFeet(uiState.bandUpperAltitudeFeet)))
                    },
            )

            val lowerV = uiState.bandLowerAltitudeValidation
            OutlinedTextField(
                value = uiState.bandLowerAltitudeFeet,
                onValueChange = { onAction(MainAction.UpdateBandLowerAltitude(it)) },
                label = { Text("Lower limit") },
                singleLine = true,
                isError = !lowerV.isValid,
                supportingText = {
                    val errorMessage = lowerV.errorMessage
                    if (errorMessage != null) Text(errorMessage)
                    else flightLevelHint(uiState.bandLowerAltitudeFeet)?.let { Text(it) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (!focus.isFocused)
                            onAction(MainAction.UpdateBandLowerAltitude(flToFeet(uiState.bandLowerAltitudeFeet)))
                    },
            )
        }
    }
}

private fun flToFeet(raw: String): String {
    val n = raw.trim().toIntOrNull()
    return if (n != null && n in 1..600) (n * 100).toString() else raw
}

private fun flightLevelHint(value: String): String? {
    val n = value.trim().toIntOrNull() ?: return null
    if (n !in 1..600) return null
    return "FL%03d = %,d ft".format(n, n * 100)
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