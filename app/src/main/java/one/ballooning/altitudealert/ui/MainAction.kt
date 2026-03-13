package one.ballooning.altitudealert.ui

import one.ballooning.altitudealert.data.model.PreferredSource

sealed class MainAction {
    object ToggleAlerts : MainAction()
    object NavigateToAdvancedSettings : MainAction()
    object RequestPermissions : MainAction()
    object OpenAppSettings : MainAction()
    object MuteAlarm : MainAction()
    object AcknowledgeMaxAltitude : MainAction()
    object UnsilenceMaxAltitude : MainAction()
    data class SetPreferredSource(val source: PreferredSource) : MainAction()
    data class UpdateBandLowerAltitude(val value: String) : MainAction()
    data class UpdateBandUpperAltitude(val value: String) : MainAction()
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
    data class UpdateMaxAltitudeAlertThreshold(val value: String) : AdvancedAction()
    data class UpdateMaxAltitudeReactivationThreshold(val value: String) : AdvancedAction()
}