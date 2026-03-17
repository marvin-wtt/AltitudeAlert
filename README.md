# Altitude Alert

An Android app for balloon pilots that monitors altitude in real time and fires configurable alerts when crossing set altitude limits.

## Features

- **Altitude band alerts** — set a lower and upper altitude limit; the app alerts when approaching or crossing either edge
- **Approach alert** — three beeps when within a configurable distance of a band edge
- **Crossing alarm** — continuous two-tone alarm when outside the band; can be muted from the notification or main screen
- **Max altitude alert** — continuous alarm when approaching the balloon's all-time session maximum altitude
- **Dual altitude source** — fuses barometer (when available) and GPS; falls back to GPS-only automatically
- **Altitude units** — display in feet, metres, or flight levels
- **Foreground service** — monitoring continues with the app in the background
- **Persistent config** — all settings survive app restarts

## Requirements

- Android 13 (API 33) or higher
- Location permission (fine + coarse)
- Notification permission
- Barometer sensor is optional; GPS is always used

## Screenshots

_TODO_

## Architecture

Data flows top to bottom through four layers:

```
FusedAltitudeDataSource  ──►  AltitudeMonitor  ──►  MonitorService  ──►  MainViewModel  ──►  UI
      (sensor/GPS)            (pure domain)          (foreground svc)      (StateFlow)       (Compose)
```

- **`FusedAltitudeDataSource`** — merges barometer (`SensorManager`) and GPS (`FusedLocationProviderClient`) into a single `AltitudeReading` flow. GPS readings time out after 10 s of silence.
- **`AltitudeMonitor`** — pure, Android-free domain class. `AlertEngine` classifies each reading as `CLEAR` / `APPROACHING` / `CROSSED` for both limits.
- **`MonitorService`** — bound + started foreground service. Owns the monitor, drives the alarm sound and vibration, and posts status notifications. State is exposed via `MonitorBinder` as `StateFlow`s.
- **`MainViewModel`** — binds to the service, merges binder flows into a single `MainUiState`, and persists config changes immediately via `ConfigRepository`.
- **UI** — two Compose screens (`MainScreen`, `AdvancedSettingsScreen`) switched via an `AppScreen` enum; no Jetpack Navigation dependency.

Dependencies are wired manually — no DI framework is used.

## Package Structure

| Package           | Contents                                                                            |
|-------------------|-------------------------------------------------------------------------------------|
| `data/model`      | `AlertConfig`, `MaxAltitudeConfig`, `AltitudeReading`                               |
| `data/source`     | `AltitudeDataSource` interface + `FusedAltitudeDataSource`                          |
| `data/repository` | `ConfigRepository`, `DataStoreConfigRepository`, `SystemInfoRepository`             |
| `domain`          | `AltitudeMonitor`, `AlertEngine`, `MonitorState`, `AlertResult`                     |
| `service`         | `MonitorService`, `AlarmSoundPlayer`, `AlarmVibrator`                               |
| `notification`    | `MonitorNotification`, `MonitorStatusFormatter`                                     |
| `ui`              | `MainViewModel`, `MainScreen`, `AdvancedSettingsScreen`, `MainAction`, `Validation` |
| `ui/theme`        | Compose theme, colors, typography                                                   |
| `util`            | `AltitudeConverter` (barometric formula, metres ↔ feet, flight levels)              |

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Kotlin Coroutines / Flow
- AndroidX DataStore (config persistence, JSON-serialised via `kotlinx.serialization`)
- Google Play Services Location (`FusedLocationProviderClient`)