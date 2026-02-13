package ai.openclaw.android.intent

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import ai.openclaw.android.intent.models.IntentLaunchRequest

object IntentShortcuts {

  fun resolve(request: IntentLaunchRequest): Intent? {
    return when (request.shortcut) {
      "alarm" -> buildAlarmIntent(request)
      "timer" -> buildTimerIntent(request)
      "navigate" -> buildNavigateIntent(request)
      "play_music" -> buildPlayMusicIntent(request)
      "email" -> buildEmailIntent(request)
      "settings" -> buildSettingsIntent(request)
      "search_web" -> buildSearchWebIntent(request)
      "open_app" -> buildOpenAppIntent(request)
      "call" -> buildCallIntent(request)
      "sms" -> buildSmsIntent(request)
      else -> null
    }
  }

  val allShortcutNames: List<String> = listOf(
    "alarm", "timer", "navigate", "play_music", "email",
    "settings", "search_web", "open_app", "call", "sms",
  )

  fun actionForShortcut(shortcut: String): String? {
    return when (shortcut) {
      "alarm" -> AlarmClock.ACTION_SET_ALARM
      "timer" -> AlarmClock.ACTION_SET_TIMER
      "navigate" -> Intent.ACTION_VIEW
      "play_music" -> "android.media.action.MEDIA_PLAY_FROM_SEARCH"
      "email" -> Intent.ACTION_SENDTO
      "settings" -> Settings.ACTION_SETTINGS
      "search_web" -> Intent.ACTION_WEB_SEARCH
      "open_app" -> null
      "call" -> Intent.ACTION_DIAL
      "sms" -> Intent.ACTION_SENDTO
      else -> null
    }
  }

  private fun buildAlarmIntent(request: IntentLaunchRequest): Intent {
    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
    val timeParts = request.time?.split(":")
    if (timeParts != null && timeParts.size == 2) {
      val hour = timeParts[0].toIntOrNull()
      val minute = timeParts[1].toIntOrNull()
      if (hour != null) intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
      if (minute != null) intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
    }
    if (!request.label.isNullOrBlank()) {
      intent.putExtra(AlarmClock.EXTRA_MESSAGE, request.label)
    }
    intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    return intent
  }

  private fun buildTimerIntent(request: IntentLaunchRequest): Intent {
    val intent = Intent(AlarmClock.ACTION_SET_TIMER)
    val seconds = request.seconds ?: 0
    if (seconds > 0) {
      intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds)
    }
    if (!request.label.isNullOrBlank()) {
      intent.putExtra(AlarmClock.EXTRA_MESSAGE, request.label)
    }
    intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    return intent
  }

  private fun buildNavigateIntent(request: IntentLaunchRequest): Intent {
    val address = request.address?.trim().orEmpty()
    val encodedAddress = Uri.encode(address)
    val uri = Uri.parse("google.navigation:q=$encodedAddress")
    return Intent(Intent.ACTION_VIEW, uri).apply {
      setPackage("com.google.android.apps.maps")
    }
  }

  private fun buildPlayMusicIntent(request: IntentLaunchRequest): Intent {
    val query = request.query?.trim().orEmpty()
    val provider = request.provider?.trim()?.lowercase().orEmpty()

    if (provider == "spotify" && query.isNotEmpty()) {
      val spotifyIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("spotify:search:${Uri.encode(query)}")
        setPackage("com.spotify.music")
      }
      return spotifyIntent
    }

    return Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
      putExtra("query", query)
    }
  }

  private fun buildEmailIntent(request: IntentLaunchRequest): Intent {
    val to = request.to?.trim().orEmpty()
    val subject = request.subject?.trim().orEmpty()
    val body = request.body?.trim().orEmpty()
    val uri = Uri.parse("mailto:${Uri.encode(to)}")
    return Intent(Intent.ACTION_SENDTO, uri).apply {
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, body)
    }
  }

  private fun buildSettingsIntent(request: IntentLaunchRequest): Intent {
    val page = request.page?.trim()?.lowercase().orEmpty()
    val action = settingsPageToAction(page)
    return Intent(action)
  }

  private fun buildSearchWebIntent(request: IntentLaunchRequest): Intent {
    val query = request.query?.trim().orEmpty()
    return Intent(Intent.ACTION_WEB_SEARCH).apply {
      putExtra("query", query)
    }
  }

  private fun buildOpenAppIntent(request: IntentLaunchRequest): Intent? {
    val pkg = request.`package`?.trim().orEmpty()
    if (pkg.isEmpty()) return null
    // Return a marker intent; IntentBridgeManager will use PackageManager to resolve it.
    return Intent("ai.openclaw.android.intent.OPEN_APP").apply {
      putExtra("target_package", pkg)
    }
  }

  private fun buildCallIntent(request: IntentLaunchRequest): Intent {
    val number = request.number?.trim().orEmpty()
    val uri = if (number.isNotEmpty()) Uri.parse("tel:${Uri.encode(number)}") else Uri.parse("tel:")
    return Intent(Intent.ACTION_DIAL, uri)
  }

  private fun buildSmsIntent(request: IntentLaunchRequest): Intent {
    val number = request.number?.trim().orEmpty()
    val body = request.body?.trim().orEmpty()
    val uri = if (number.isNotEmpty()) Uri.parse("smsto:${Uri.encode(number)}") else Uri.parse("smsto:")
    return Intent(Intent.ACTION_SENDTO, uri).apply {
      putExtra("sms_body", body)
    }
  }

  fun settingsPageToAction(page: String): String {
    return when (page) {
      "wifi" -> Settings.ACTION_WIFI_SETTINGS
      "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
      "display" -> Settings.ACTION_DISPLAY_SETTINGS
      "sound" -> Settings.ACTION_SOUND_SETTINGS
      "battery" -> "android.settings.BATTERY_SAVER_SETTINGS"
      "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
      "security" -> Settings.ACTION_SECURITY_SETTINGS
      "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
      "accounts" -> Settings.ACTION_SYNC_SETTINGS
      "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
      "date" -> Settings.ACTION_DATE_SETTINGS
      "language" -> Settings.ACTION_LOCALE_SETTINGS
      "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
      "nfc" -> Settings.ACTION_NFC_SETTINGS
      "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
      else -> Settings.ACTION_SETTINGS
    }
  }
}
