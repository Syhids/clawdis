package ai.openclaw.android

import android.net.Uri

/**
 * Represents content shared to OpenClaw from other apps via share intent.
 */
sealed class SharedContent {
  /**
   * Text shared from another app.
   */
  data class Text(val text: String) : SharedContent()

  /**
   * One or more images shared from another app.
   */
  data class Images(val uris: List<Uri>) : SharedContent()
}
