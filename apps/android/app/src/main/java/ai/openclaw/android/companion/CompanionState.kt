package ai.openclaw.android.companion

internal data class CompanionState(
  val currentTime: String = "",
  val currentDate: String = "",
  val isNightMode: Boolean = false,
  val gatewayConnected: Boolean = false,
  val serverName: String? = null,
  val lastAgentMessage: String? = null,
  val voiceWakeListening: Boolean = false,
  val voiceWakeStatus: String = "Off",
  val talkEnabled: Boolean = false,
  val talkListening: Boolean = false,
  val talkSpeaking: Boolean = false,
)
