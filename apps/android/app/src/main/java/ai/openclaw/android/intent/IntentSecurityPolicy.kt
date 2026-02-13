package ai.openclaw.android.intent

import android.content.Intent
import ai.openclaw.android.SecurePrefs
import ai.openclaw.android.intent.models.IntentSecurityLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class IntentSecurityPolicy(private val prefs: SecurePrefs) {

  companion object {
    private const val WHITELIST_KEY = "intent.userWhitelist"

    private val autoActions = setOf(
      Intent.ACTION_VIEW,
      Intent.ACTION_DIAL,
      Intent.ACTION_WEB_SEARCH,
      "android.provider.Settings.ACTION_SETTINGS",
      "android.settings.SETTINGS",
      "android.settings.WIFI_SETTINGS",
      "android.settings.BLUETOOTH_SETTINGS",
      "android.settings.DISPLAY_SETTINGS",
      "android.settings.SOUND_SETTINGS",
      "android.settings.BATTERY_SAVER_SETTINGS",
      "android.settings.INTERNAL_STORAGE_SETTINGS",
      "android.settings.SECURITY_SETTINGS",
      "android.settings.LOCATION_SOURCE_SETTINGS",
      "android.settings.SYNC_SETTINGS",
      "android.settings.ACCESSIBILITY_SETTINGS",
      "android.settings.DATE_SETTINGS",
      "android.settings.LOCALE_SETTINGS",
      "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
      "android.settings.NFC_SETTINGS",
      "android.settings.AIRPLANE_MODE_SETTINGS",
      "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS",
      "android.intent.action.POWER_USAGE_SUMMARY",
    )

    private val confirmActions = setOf(
      Intent.ACTION_SEND,
      Intent.ACTION_SENDTO,
      "android.provider.AlarmClock.ACTION_SET_ALARM",
      "android.provider.AlarmClock.ACTION_SET_TIMER",
      Intent.ACTION_INSERT,
      Intent.ACTION_CALL,
      "android.media.action.MEDIA_PLAY_FROM_SEARCH",
    )

    private val blockedFlags = setOf(
      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
      Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
    )

    private val blockedPackages = setOf(
      "com.android.settings.password",
    )

    fun isAutoAction(action: String?): Boolean = action != null && autoActions.contains(action)

    fun isConfirmAction(action: String?): Boolean = action != null && confirmActions.contains(action)
  }

  private val json = Json { ignoreUnknownKeys = true }

  fun classify(action: String?, packageName: String?, flags: List<String>): IntentSecurityLevel {
    if (packageName != null && blockedPackages.contains(packageName)) {
      return IntentSecurityLevel.BLOCKED
    }

    val parsedFlags = flags.mapNotNull { flagNameToInt(it) }
    if (parsedFlags.any { blockedFlags.contains(it) }) {
      return IntentSecurityLevel.BLOCKED
    }

    val userWhitelist = loadUserWhitelist()
    val key = whitelistKey(action, packageName)
    if (key != null && userWhitelist.contains(key)) {
      return IntentSecurityLevel.AUTO
    }

    if (isAutoAction(action)) {
      return IntentSecurityLevel.AUTO
    }

    if (isConfirmAction(action)) {
      return IntentSecurityLevel.CONFIRM
    }

    // Default: any unrecognized action launched by package requires confirmation
    if (packageName != null) {
      return IntentSecurityLevel.CONFIRM
    }

    return IntentSecurityLevel.CONFIRM
  }

  fun addToWhitelist(action: String?, packageName: String?) {
    val key = whitelistKey(action, packageName) ?: return
    val current = loadUserWhitelist().toMutableSet()
    current.add(key)
    saveUserWhitelist(current)
  }

  fun loadUserWhitelist(): Set<String> {
    val raw = prefs.getString(WHITELIST_KEY) ?: return emptySet()
    return try {
      val array = json.parseToJsonElement(raw) as? JsonArray ?: return emptySet()
      array.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { s -> s.isNotEmpty() } }.toSet()
    } catch (_: Throwable) {
      emptySet()
    }
  }

  fun clearWhitelist() {
    prefs.remove(WHITELIST_KEY)
  }

  private fun saveUserWhitelist(entries: Set<String>) {
    val array = JsonArray(entries.sorted().map { JsonPrimitive(it) })
    prefs.putString(WHITELIST_KEY, array.toString())
  }

  private fun whitelistKey(action: String?, packageName: String?): String? {
    val a = action?.trim().orEmpty()
    val p = packageName?.trim().orEmpty()
    if (a.isEmpty() && p.isEmpty()) return null
    return "$a|$p"
  }

  private fun flagNameToInt(name: String): Int? {
    return when (name.uppercase()) {
      "FLAG_GRANT_PERSISTABLE_URI_PERMISSION" -> Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
      "FLAG_GRANT_PREFIX_URI_PERMISSION" -> Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
      "FLAG_GRANT_READ_URI_PERMISSION" -> Intent.FLAG_GRANT_READ_URI_PERMISSION
      "FLAG_GRANT_WRITE_URI_PERMISSION" -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      "FLAG_ACTIVITY_NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
      "FLAG_ACTIVITY_CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
      else -> null
    }
  }
}
