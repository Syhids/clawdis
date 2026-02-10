package ai.openclaw.android.actions

import ai.openclaw.android.actions.detectors.CodeBlockDetector
import ai.openclaw.android.actions.detectors.CommandDetector
import ai.openclaw.android.actions.detectors.ConfirmationDetector
import ai.openclaw.android.actions.detectors.ErrorDetector
import ai.openclaw.android.actions.detectors.ListOptionDetector
import ai.openclaw.android.actions.detectors.UrlDetector
import ai.openclaw.android.chat.ChatMessage

/**
 * Analyzes assistant messages and produces [ResponseAnalysis] with detected
 * content items and prioritized suggested actions (max 5).
 *
 * All detection is pure regex / string-matching — no ML, no network, no
 * external dependencies.  Results are meant to be cached by message.id.
 */
class ResponseAnalyzer {
  private val detectors: List<ContentDetector> = listOf(
    ConfirmationDetector(),
    ErrorDetector(),
    CommandDetector(),
    CodeBlockDetector(),
    ListOptionDetector(),
    UrlDetector(),
  )

  /**
   * Analyze a single chat message.
   * Returns [ResponseAnalysis.empty] for non-assistant messages.
   */
  fun analyze(message: ChatMessage): ResponseAnalysis {
    if (message.role.lowercase() != "assistant") return ResponseAnalysis.empty()

    val text = message.content
      .filter { it.type == "text" }
      .mapNotNull { it.text }
      .joinToString("\n")
    if (text.isBlank()) return ResponseAnalysis.empty()

    return analyze(text)
  }

  /**
   * Analyze raw text (useful for testing without a full ChatMessage).
   */
  fun analyze(text: String): ResponseAnalysis {
    val allItems = mutableListOf<DetectedItem>()
    val allActions = mutableListOf<SuggestedAction>()

    for (detector in detectors) {
      val result = detector.detect(text)
      allItems.addAll(result.items)
      allActions.addAll(result.actions)
    }

    // Add "Resúmelo" for long responses
    if (text.length > 1500) {
      allActions.add(
        SuggestedAction.SendMessage(
          label = "Resúmelo",
          message = "Resume lo anterior en 3 puntos clave",
          icon = "📋",
          priority = Priority.LOW,
        ),
      )
    }

    val responseType = classifyResponse(text, allItems)
    val prioritized = prioritizeAndLimit(allActions)

    return ResponseAnalysis(
      detectedItems = allItems,
      suggestedActions = prioritized,
      responseType = responseType,
    )
  }

  private fun classifyResponse(
    text: String,
    items: List<DetectedItem>,
  ): ResponseType {
    return when {
      items.any { it is DetectedItem.Confirmation } -> ResponseType.CONFIRMATION
      items.any { it is DetectedItem.Error } -> ResponseType.ERROR
      items.any { it is DetectedItem.NumberedList } -> ResponseType.OPTIONS
      items.any { it is DetectedItem.Command } -> ResponseType.COMMAND
      text.length > 1500 -> ResponseType.LONG_RESPONSE
      else -> ResponseType.INFORMATIONAL
    }
  }

  /**
   * Sort by priority descending, de-duplicate by label, limit to MAX_ACTIONS.
   */
  private fun prioritizeAndLimit(actions: List<SuggestedAction>): List<SuggestedAction> {
    return actions
      .sortedByDescending { it.priority.ordinal }
      .distinctBy { it.label }
      .take(MAX_ACTIONS)
  }

  companion object {
    const val MAX_ACTIONS = 5
  }
}
