package ai.openclaw.android.actions.detectors

import ai.openclaw.android.actions.ContentDetector
import ai.openclaw.android.actions.DetectedItem
import ai.openclaw.android.actions.DetectionResult
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.SuggestedAction

/**
 * Detects error messages, exceptions, and failure indicators.
 * Suggests "Reintentar" and "Más detalles" chips.
 */
class ErrorDetector : ContentDetector {
  private val errorPatterns = listOf(
    Regex("""(?:error|exception|failed|fallo|falló|no se pudo|unable to|cannot|permission denied)""", RegexOption.IGNORE_CASE),
    Regex("""(?:stack trace|traceback|at .+\(.+:\d+\))""", RegexOption.IGNORE_CASE),
    Regex("""(?:exit code [1-9]\d*|returned non-zero)""", RegexOption.IGNORE_CASE),
    Regex("""(?:ENOENT|EACCES|EPERM|ETIMEDOUT|ECONNREFUSED)"""),
  )

  // Avoid false positives: if the message is about fixing an error (not reporting one)
  private val falsePositivePatterns = listOf(
    Regex("""(?:no errors?|sin errores?|error free|no issues)""", RegexOption.IGNORE_CASE),
  )

  override fun detect(text: String): DetectionResult {
    // Skip if it looks like a false positive
    for (fp in falsePositivePatterns) {
      if (fp.containsMatchIn(text)) return DetectionResult()
    }

    for (pattern in errorPatterns) {
      if (pattern.containsMatchIn(text)) {
        return DetectionResult(
          items = listOf(DetectedItem.Error(message = text.take(200))),
          actions = listOf(
            SuggestedAction.SendMessage(
              label = "Reintentar",
              message = "Inténtalo de nuevo",
              icon = "🔄",
              priority = Priority.HIGH,
            ),
            SuggestedAction.SendMessage(
              label = "Más detalles",
              message = "Dame más detalles sobre el error",
              icon = "🔍",
              priority = Priority.MEDIUM,
            ),
          ),
        )
      }
    }
    return DetectionResult()
  }
}
