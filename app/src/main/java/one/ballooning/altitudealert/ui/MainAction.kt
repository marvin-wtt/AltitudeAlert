package one.ballooning.altitudealert.ui

import one.ballooning.altitudealert.data.PreferredSource

// ─── Main screen actions ──────────────────────────────────────────────────────

sealed class MainAction {
    object ToggleAlerts : MainAction()
    object NavigateToAdvancedSettings : MainAction()
    object RequestPermissions : MainAction()
    object OpenAppSettings : MainAction()
    data class SetReferenceMode(val mode: ReferenceMode) : MainAction()
    data class SetPreferredSource(val source: PreferredSource) : MainAction()
    data class UpdateBandLowerAltitude(val value: String) : MainAction()
    data class UpdateBandUpperAltitude(val value: String) : MainAction()
    data class UpdateBandLowerFlightLevel(val value: String) : MainAction()
    data class UpdateBandUpperFlightLevel(val value: String) : MainAction()
    data class UpdateQnh(val value: String) : MainAction()
}

// ─── Advanced settings actions ────────────────────────────────────────────────

sealed class AdvancedAction {
    object NavigateBack : AdvancedAction()
    data class SetDistanceThresholdFeet(val value: String) : AdvancedAction()
    data class SetThresholdSoundEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetThresholdVibrationEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetThresholdRepeatEnabled(val enabled: Boolean) : AdvancedAction()
    data class UpdateThresholdRepeatSeconds(val value: String) : AdvancedAction()
    data class SetCrossingSoundEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetCrossingVibrationEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetCrossingRepeatEnabled(val enabled: Boolean) : AdvancedAction()
    data class UpdateCrossingRepeatSeconds(val value: String) : AdvancedAction()
    data class SetWarnOnLowAccuracy(val enabled: Boolean) : AdvancedAction()

    // Max altitude alert settings
    data class SetMaxAltitudeAlertEnabled(val enabled: Boolean) : AdvancedAction()
    data class UpdateMaxAltitudeThreshold(val value: String) : AdvancedAction()
    data class UpdateMaxAltitudeMinAltitude(val value: String) : AdvancedAction()
    data class UpdateMaxAltitudeSilenceMinutes(val value: String) : AdvancedAction()
}