package ai.openclaw.android.ui.chat

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stores draft messages per session key.
 * Drafts are persisted to SharedPreferences so they survive app restarts.
 */
class DraftStore(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val json = Json { ignoreUnknownKeys = true }

  // In-memory cache for fast access
  private val cache = mutableMapOf<String, String>()

  init {
    // Load existing drafts from preferences
    loadFromPrefs()
  }

  /**
   * Get the draft for a session, or empty string if none.
   */
  fun get(sessionKey: String): String {
    return cache[sessionKey].orEmpty()
  }

  /**
   * Set the draft for a session.
   * Pass empty string to clear the draft.
   */
  fun set(sessionKey: String, draft: String) {
    val trimmed = draft.trim()
    if (trimmed.isEmpty()) {
      cache.remove(sessionKey)
    } else {
      cache[sessionKey] = trimmed
    }
    saveToPrefs()
  }

  /**
   * Clear the draft for a session (e.g., after sending).
   */
  fun clear(sessionKey: String) {
    cache.remove(sessionKey)
    saveToPrefs()
  }

  private fun loadFromPrefs() {
    try {
      val raw = prefs.getString(KEY_DRAFTS, null) ?: return
      val obj = json.parseToJsonElement(raw).jsonObject
      for ((key, value) in obj) {
        val text = value.jsonPrimitive.contentOrNull
        if (!text.isNullOrEmpty()) {
          cache[key] = text
        }
      }
    } catch (_: Throwable) {
      // Ignore parse errors, start fresh
    }
  }

  private fun saveToPrefs() {
    val obj = buildJsonObject {
      for ((key, value) in cache) {
        put(key, JsonPrimitive(value))
      }
    }
    prefs.edit {
      putString(KEY_DRAFTS, obj.toString())
    }
  }

  companion object {
    private const val PREFS_NAME = "chat_drafts"
    private const val KEY_DRAFTS = "drafts"
    private const val MAX_DRAFTS = 50

    @Volatile
    private var instance: DraftStore? = null

    fun getInstance(context: Context): DraftStore {
      return instance ?: synchronized(this) {
        instance ?: DraftStore(context.applicationContext).also { instance = it }
      }
    }
  }
}
