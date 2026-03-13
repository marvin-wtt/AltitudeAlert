package one.ballooning.altitudealert.ui

import one.ballooning.altitudealert.data.model.PreferredSource

sealed class MainAction {
    object ToggleAlerts : MainAction()
    object NavigateToAdvancedSettings : MainAction()
    object RequestPermissions : MainAction()
    object OpenAppSettings : MainAction()
    object MuteAlarm : MainAction()
    data class SetReferenceMode(val mode: ReferenceMode) : MainAction()
    data class SetPreferredSource(val source: PreferredSource) : MainAction()
    data class UpdateBandLowerAltitude(val value: String) : MainAction()
    data class UpdateBandUpperAltitude(val value: String) : MainAction()
    data class UpdateBandLowerFlightLevel(val value: String) : MainAction()
    data class UpdateBandUpperFlightLevel(val value: String) : MainAction()
    data class UpdateQnh(val value: String) : MainAction()
}

sealed class AdvancedAction {
    object NavigateBack : AdvancedAction()
    data class SetDistanceThresholdFeet(val value: String) : AdvancedAction()
    data class SetThresholdAlertEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetSoundEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetVibrateEnabled(val enabled: Boolean) : AdvancedAction()
    data class SetWarnOnLowAccuracy(val enabled: Boolean) : AdvancedAction()
    data class SetMaxAltitudeAlertEnabled(val enabled: Boolean) : AdvancedAction()
    data class UpdateMaxAltitudeThreshold(val value: String) : AdvancedAction()
    data class UpdateMaxAltitudeMinAltitude(val value: String) : AdvancedAction()
    data class UpdateMaxAltitudeSilenceMinutes(val value: String) : AdvancedAction()
}