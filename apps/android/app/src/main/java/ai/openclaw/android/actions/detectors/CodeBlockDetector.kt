package ai.openclaw.android.actions.detectors

import ai.openclaw.android.actions.ContentDetector
import ai.openclaw.android.actions.DetectedItem
import ai.openclaw.android.actions.DetectionResult
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.SuggestedAction

/**
 * Detects fenced code blocks (```language ... ```) and suggests copy actions.
 */
class CodeBlockDetector : ContentDetector {
  private val codeBlockRegex = Regex("""```(\w*)\n([\s\S]*?)```""")

  override fun detect(text: String): DetectionResult {
    val blocks = codeBlockRegex.findAll(text).toList()
    if (blocks.isEmpty()) return DetectionResult()

    val items = blocks.map { match ->
      DetectedItem.CodeBlock(
        code = match.groupValues[2].trim(),
        language = match.groupValues[1].ifEmpty { null },
      )
    }

    val actions = blocks.take(2).mapIndexed { idx, match ->
      val code = match.groupValues[2].trim()
      val lang = match.groupValues[1].ifEmpty { "bloque" }
      SuggestedAction.CopyContent(
        label = if (blocks.size == 1) "Copiar $lang" else "Copiar bloque ${idx + 1}",
        content = code,
        priority = Priority.HIGH,
      )
    }

    return DetectionResult(items = items, actions = actions)
  }
}
