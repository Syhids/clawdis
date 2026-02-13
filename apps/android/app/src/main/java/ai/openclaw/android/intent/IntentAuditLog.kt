package ai.openclaw.android.intent

data class IntentAuditEntry(
  val timestamp: Long,
  val action: String?,
  val uri: String?,
  val packageName: String?,
  val appName: String?,
  val shortcut: String?,
  val launched: Boolean,
  val denied: Boolean = false,
  val blocked: Boolean = false,
)

class IntentAuditLog {
  private val entries = mutableListOf<IntentAuditEntry>()
  private val maxEntries = 100

  @Synchronized
  fun record(entry: IntentAuditEntry) {
    entries.add(entry)
    if (entries.size > maxEntries) {
      entries.removeAt(0)
    }
  }

  @Synchronized
  fun recent(limit: Int = 20): List<IntentAuditEntry> {
    return entries.takeLast(limit.coerceIn(1, maxEntries)).reversed()
  }

  @Synchronized
  fun clear() {
    entries.clear()
  }
}
