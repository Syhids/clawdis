# Companion Display Mode — Implementation Spec

## Overview

Transform the Android device into a smart ambient display (like a Nest Hub) when charging, using Android's DreamService API. The screen shows an ambient clock, contextual dashboard cards, and enables always-on voice interaction.

## Architecture

### Core Components

1. **CompanionDreamService** — Android DreamService that activates when device is charging/docked
2. **CompanionDisplayScreen** — Compose UI for the ambient display
3. **CompanionDisplayViewModel** — State management for the companion display
4. **CompanionPrefs** — Preferences for companion display settings (integrated into SecurePrefs)
5. **ChargingReceiver** — BroadcastReceiver to detect charging state
6. **NightModeHelper** — Utility for automatic night mode (23:00-07:00)

### Files to Create

All new files under `app/src/main/java/ai/openclaw/android/companion/`:

1. `CompanionDreamService.kt` — The DreamService implementation
2. `CompanionDisplayContent.kt` — Composable UI for the ambient display
3. `CompanionState.kt` — Data classes for companion display state
4. `ChargingReceiver.kt` — BroadcastReceiver for charging detection
5. `NightModeHelper.kt` — Night mode logic

### Files to Modify

1. `AndroidManifest.xml` — Register DreamService, ChargingReceiver, dream metadata
2. `SecurePrefs.kt` — Add companion display preferences
3. `NodeRuntime.kt` — Expose companion-relevant state (gateway status, last agent message)
4. `MainViewModel.kt` — Add companion display toggle
5. `ui/SettingsSheet.kt` — Add Companion Display section in settings
6. `res/xml/companion_dream.xml` — Dream service metadata (NEW)

## Implementation Details

### 1. CompanionDreamService (`companion/CompanionDreamService.kt`)

```kotlin
// Extends android.service.dreams.DreamService
// - Sets isInteractive = true (allows touch)
// - Sets isFullscreen = true
// - Sets screenBright based on night mode (dim at night)
// - Uses ComposeView for the UI
// - Accesses NodeRuntime from (application as NodeApp).runtime
// - Forces VoiceWake to Always mode while dream is active
// - Restores original VoiceWake mode when dream ends
```

Key behaviors:

- Anti burn-in: Shifts content position slightly every 60 seconds (random ±20dp offset)
- Night mode: Between 23:00-07:00, sets screenBright = false (dim), uses minimal color scheme
- When dream starts: enable voice wake if not already enabled
- When dream ends: restore previous voice wake state

### 2. CompanionDisplayContent (`companion/CompanionDisplayContent.kt`)

Compose UI with:

**Clock Section (top center)**

- Large time display (72sp, thin weight)
- Date below (20sp)
- Anti burn-in: content shifts position slightly every minute

**Dashboard Cards (center, horizontal scrollable row)**

- **Status Card**: Gateway connection status, server name
- **Agent Card**: Last assistant message from chat (truncated to 3 lines)
- Cards use semi-transparent dark backgrounds, rounded corners
- Material3 styling

**Voice Indicator (bottom center)**

- Shows voice wake status: "Listening..." / "Say 'openclaw'..."
- Subtle pulsing animation when listening
- Talk Mode orb when talk mode is active

**Night Mode**

- Reduced brightness via screenBright = false
- Muted colors (dimmer text, darker backgrounds)
- Clock only (hide dashboard cards at night to reduce burn-in)

### 3. CompanionState (`companion/CompanionState.kt`)

```kotlin
data class CompanionState(
  val currentTime: String,
  val currentDate: String,
  val isNightMode: Boolean,
  val gatewayConnected: Boolean,
  val serverName: String?,
  val lastAgentMessage: String?,
  val voiceWakeListening: Boolean,
  val voiceWakeStatus: String,
  val talkEnabled: Boolean,
  val talkListening: Boolean,
  val talkSpeaking: Boolean,
)
```

### 4. ChargingReceiver (`companion/ChargingReceiver.kt`)

- Listens for ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED
- When power connected + companion mode enabled in prefs → no-op (DreamService handles this natively via Android Daydream settings)
- This receiver is optional — mainly for future use if we want to auto-launch the dream

### 5. Settings Integration

Add to SettingsSheet, in a new "Companion Display" section after "Screen":

```
Companion Display
├── Enable Companion Display (Switch) — enables the DreamService
├── Description: "Turns your phone into a smart ambient display when charging. Configure in Android Settings > Display > Screen saver."
└── Open Screen Saver Settings (Button) — opens Android's daydream/screensaver settings
```

Add to SecurePrefs:

```kotlin
private val _companionEnabled = MutableStateFlow(prefs.getBoolean("companion.enabled", false))
val companionEnabled: StateFlow<Boolean> = _companionEnabled

fun setCompanionEnabled(value: Boolean) {
  prefs.edit { putBoolean("companion.enabled", value) }
  _companionEnabled.value = value
}
```

### 6. AndroidManifest.xml Changes

```xml
<!-- DreamService -->
<service
    android:name=".companion.CompanionDreamService"
    android:exported="true"
    android:permission="android.permission.BIND_DREAM_SERVICE">
    <intent-filter>
        <action android:name="android.service.dreams.DreamService" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="android.service.dream"
        android:resource="@xml/companion_dream" />
</service>
```

### 7. Dream Metadata (`res/xml/companion_dream.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<dream xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="ai.openclaw.android.MainActivity" />
```

## Anti Burn-in Strategy

- Content position shifts by random ±20dp every 60 seconds
- Night mode (23:00-07:00): only clock visible, minimal brightness
- Clock uses thin font weight to reduce pixel area
- All UI elements use dark backgrounds with light text

## Voice Integration

When companion display is active:

- VoiceWake is forced to Always mode (overriding user setting temporarily)
- Wake words trigger normally → command sent to gateway
- Talk Mode button visible and functional
- If Talk Mode is active, the Talk Orb overlay appears

## Night Mode Logic

```kotlin
fun isNightMode(): Boolean {
    val hour = LocalTime.now().hour
    return hour >= 23 || hour < 7
}
```

Night mode effects:

- screenBright = false (system dim)
- Hide dashboard cards
- Reduce clock opacity to 0.5f
- Muted color scheme

## Key Design Decisions

1. **DreamService over Activity** — Native Android API for screen savers, properly handles charging detection, integrates with system settings
2. **Compose in DreamService** — Use ComposeView inside the service window for consistent UI
3. **Force VoiceWake** — The whole point is hands-free interaction; temporarily override user setting
4. **No separate ViewModel** — DreamService accesses NodeRuntime directly (it's a service, not an Activity with lifecycle concerns)
5. **Prefs in SecurePrefs** — Keep all preferences centralized

## Estimated LOC

- CompanionDreamService.kt: ~180 lines
- CompanionDisplayContent.kt: ~350 lines
- CompanionState.kt: ~30 lines
- ChargingReceiver.kt: ~40 lines
- NightModeHelper.kt: ~25 lines
- Modified files: ~80 lines total additions
- res/xml/companion_dream.xml: ~5 lines

Total: ~710 lines new code
