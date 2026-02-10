package ai.openclaw.android.actions.detectors

import ai.openclaw.android.actions.ContentDetector
import ai.openclaw.android.actions.DetectedItem
import ai.openclaw.android.actions.DetectionResult
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.SuggestedAction

/**
 * Detects numbered lists (e.g. "1. Option A\n2. Option B").
 * Produces one chip per option so the user can pick with a single tap.
 */
class ListOptionDetector : ContentDetector {
  private val numberedListRegex = Regex("""(?:^|\n)\s*(\d+)[.)]\s+(.+)""")

  override fun detect(text: String): DetectionResult {
    val matches = numberedListRegex.findAll(text).toList()
    if (matches.size < 2) return DetectionResult() // Not a real list

    val options = matches.map { match ->
      DetectedItem.ListOption(
        number = match.groupValues[1].toIntOrNull() ?: 0,
        label = match.groupValues[2].trim()
          .removeSuffix(".")
          .trim(),
      )
    }

    val actions = options.take(4).map { option ->
      val shortLabel = option.label.take(25).let {
        if (option.label.length > 25) "$it…" else it
      }
      SuggestedAction.SendMessage(
        label = "Opción ${option.number}",
        message = option.label,
        icon = "📌",
        priority = Priority.HIGH,
      )
    }

    return DetectionResult(
      items = listOf(DetectedItem.NumberedList(options)),
      actions = actions,
    )
  }
}
