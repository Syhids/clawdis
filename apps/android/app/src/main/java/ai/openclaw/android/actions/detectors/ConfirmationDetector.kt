package ai.openclaw.android.actions.detectors

import ai.openclaw.android.actions.ContentDetector
import ai.openclaw.android.actions.DetectedItem
import ai.openclaw.android.actions.DetectionResult
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.SuggestedAction

/**
 * Detects confirmation questions from the agent ("¿Quieres que…?", "Shall I…?").
 * Produces Sí / No chip actions with CRITICAL priority.
 */
class ConfirmationDetector : ContentDetector {
  private val patterns = listOf(
    Regex("""¿(?:quieres|deseas|procedo|lo hago|sigo|continúo|te parece|lo ejecuto|lo borro|lo envío)""", RegexOption.IGNORE_CASE),
    Regex("""(?:shall I|should I|do you want|want me to|proceed\?|go ahead\?)""", RegexOption.IGNORE_CASE),
    Regex("""¿(?:sí|si) o no\?""", RegexOption.IGNORE_CASE),
    Regex("""¿(?:adelante|continuar|seguir)\?""", RegexOption.IGNORE_CASE),
  )

  override fun detect(text: String): DetectionResult {
    for (pattern in patterns) {
      val match = pattern.find(text)
      if (match != null) {
        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
        val question = sentences.lastOrNull { it.contains("?") } ?: match.value

        return DetectionResult(
          items = listOf(DetectedItem.Confirmation(question = question)),
          actions = listOf(
            SuggestedAction.SendMessage(
              label = "Sí, adelante",
              message = "Sí",
              icon = "✅",
              priority = Priority.CRITICAL,
            ),
            SuggestedAction.SendMessage(
              label = "No",
              message = "No",
              icon = "❌",
              priority = Priority.CRITICAL,
            ),
          ),
        )
      }
    }
    return DetectionResult()
  }
}
