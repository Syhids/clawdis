package ai.openclaw.android.actions.detectors

import ai.openclaw.android.actions.ContentDetector
import ai.openclaw.android.actions.DetectedItem
import ai.openclaw.android.actions.DetectionResult
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.SuggestedAction

/**
 * Detects URLs (http/https) in the message text.
 * Suggests "Abrir enlace" chips for the first 2 URLs found.
 */
class UrlDetector : ContentDetector {
  private val urlRegex = Regex(
    """https?://[^\s\])"'<>,]+""",
    RegexOption.IGNORE_CASE,
  )

  override fun detect(text: String): DetectionResult {
    val urls = urlRegex.findAll(text)
      .map { it.value.trimEnd('.', ',', ')', ']') }
      .distinct()
      .toList()
    if (urls.isEmpty()) return DetectionResult()

    val items = urls.map { DetectedItem.Url(url = it, label = extractDomain(it)) }

    val actions = urls.take(2).map { url ->
      SuggestedAction.OpenUrl(
        label = extractDomain(url) ?: "Abrir enlace",
        url = url,
        priority = Priority.MEDIUM,
      )
    }

    return DetectionResult(items = items, actions = actions)
  }

  private fun extractDomain(url: String): String? {
    return try {
      java.net.URI(url).host?.removePrefix("www.")
    } catch (_: Throwable) {
      null
    }
  }
}
