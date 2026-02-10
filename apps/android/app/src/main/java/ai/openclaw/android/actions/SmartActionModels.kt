package ai.openclaw.android.actions

/**
 * Result of analyzing an assistant message for actionable content.
 */
data class ResponseAnalysis(
  val detectedItems: List<DetectedItem>,
  val suggestedActions: List<SuggestedAction>,
  val responseType: ResponseType,
) {
  companion object {
    fun empty() = ResponseAnalysis(emptyList(), emptyList(), ResponseType.INFORMATIONAL)
  }
}

/**
 * Items detected within the assistant's response text.
 */
sealed class DetectedItem {
  data class Url(val url: String, val label: String?) : DetectedItem()
  data class CodeBlock(val code: String, val language: String?) : DetectedItem()
  data class Command(val command: String) : DetectedItem()
  data class NumberedList(val options: List<ListOption>) : DetectedItem()
  data class ListOption(val number: Int, val label: String)
  data class Error(val message: String) : DetectedItem()
  data class Confirmation(val question: String) : DetectedItem()
}

/**
 * Actions suggested to the user, rendered as chips.
 */
sealed class SuggestedAction {
  abstract val label: String
  abstract val icon: String
  abstract val priority: Priority

  /** Send a message to the agent on tap. */
  data class SendMessage(
    override val label: String,
    val message: String,
    override val icon: String,
    override val priority: Priority,
  ) : SuggestedAction()

  /** Copy content to clipboard on tap. */
  data class CopyContent(
    override val label: String,
    val content: String,
    override val icon: String = "📋",
    override val priority: Priority = Priority.MEDIUM,
  ) : SuggestedAction()

  /** Open a URL in the browser on tap. */
  data class OpenUrl(
    override val label: String,
    val url: String,
    override val icon: String = "🔗",
    override val priority: Priority = Priority.MEDIUM,
  ) : SuggestedAction()
}

enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

enum class ResponseType {
  INFORMATIONAL,
  CONFIRMATION,
  ERROR,
  OPTIONS,
  COMMAND,
  LONG_RESPONSE,
}
