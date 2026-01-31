package ai.openclaw.android.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent storage for input history across app sessions.
 * Uses SharedPreferences to store the history as a JSON array of strings.
 */
object InputHistoryStore {
  private const val PREFS_NAME = "input_history"
  private const val KEY_HISTORY = "history"
  private const val MAX_HISTORY_SIZE = 50

  private val json = Json { ignoreUnknownKeys = true }

  private fun prefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  /**
   * Load the input history from persistent storage.
   */
  fun load(context: Context): List<String> {
    val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
    return try {
      json.decodeFromString<List<String>>(raw)
    } catch (_: Throwable) {
      emptyList()
    }
  }

  /**
   * Save the input history to persistent storage.
   * Only keeps the last [MAX_HISTORY_SIZE] entries.
   */
  fun save(context: Context, history: List<String>) {
    val trimmed = if (history.size > MAX_HISTORY_SIZE) {
      history.takeLast(MAX_HISTORY_SIZE)
    } else {
      history
    }
    val encoded = json.encodeToString(trimmed)
    prefs(context).edit().putString(KEY_HISTORY, encoded).apply()
  }

  /**
   * Add an entry to history, avoiding consecutive duplicates.
   * Persists immediately.
   */
  fun addEntry(context: Context, currentHistory: MutableList<String>, entry: String): Boolean {
    val trimmed = entry.trim()
    if (trimmed.isEmpty()) return false

    // Avoid consecutive duplicates
    if (currentHistory.isNotEmpty() && currentHistory.last() == trimmed) {
      return false
    }

    currentHistory.add(trimmed)

    // Trim in-memory list if needed
    while (currentHistory.size > MAX_HISTORY_SIZE) {
      currentHistory.removeAt(0)
    }

    // Persist
    save(context, currentHistory)
    return true
  }
}
