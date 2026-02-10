package ai.openclaw.android.pip

import kotlinx.serialization.Serializable

@Serializable
enum class PipContentMode(val rawValue: String) {
  AUTO("auto"),
  TALK_ORB("talk_orb"),
  CHAT_STREAM("chat_stream"),
  STATUS_ONLY("status_only");

  companion object {
    fun fromRawValue(raw: String?): PipContentMode {
      val trimmed = raw?.trim()?.lowercase().orEmpty()
      return entries.firstOrNull { it.rawValue == trimmed } ?: AUTO
    }
  }
}

@Serializable
data class PipConfig(
  val enabled: Boolean = false,
  val autoEnterOnNavigateAway: Boolean = true,
  val showDuringTalkMode: Boolean = true,
  val showDuringToolExecution: Boolean = true,
  val preferredContent: PipContentMode = PipContentMode.AUTO,
)
