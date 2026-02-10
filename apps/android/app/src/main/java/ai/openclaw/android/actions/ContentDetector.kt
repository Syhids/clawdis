package ai.openclaw.android.actions

/**
 * Interface for individual content detectors that scan message text.
 */
interface ContentDetector {
  fun detect(text: String): DetectionResult
}

/**
 * Result from a single detector: detected items + suggested actions.
 */
data class DetectionResult(
  val items: List<DetectedItem> = emptyList(),
  val actions: List<SuggestedAction> = emptyList(),
)
