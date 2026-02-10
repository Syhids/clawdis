package ai.openclaw.android.ui.chat.forms

import kotlinx.serialization.json.Json

/**
 * Parses inline form JSON embedded in chat message content.
 * Tolerant of unknown keys so new fields from future gateway versions don't break.
 */
object FormParser {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  /**
   * Parse a JSON string into an [InlineForm], returning null on any failure.
   */
  fun parse(jsonText: String?): InlineForm? {
    if (jsonText.isNullOrBlank()) return null
    return try {
      json.decodeFromString<InlineForm>(jsonText)
    } catch (_: Throwable) {
      null
    }
  }
}
