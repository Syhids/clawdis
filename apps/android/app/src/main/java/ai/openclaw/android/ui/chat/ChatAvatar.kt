package ai.openclaw.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Circular chat avatar (40dp). Shows emoji directly or a single-letter initial as fallback.
 * Assistant avatars appear left-aligned, user avatars right-aligned (handled by the caller).
 */
@Composable
fun ChatAvatar(
  role: String,
  assistantName: String? = null,
  assistantEmoji: String? = null,
  modifier: Modifier = Modifier,
) {
  val normalized = role.trim().lowercase()
  val isUser = normalized == "user"

  // Determine display content: emoji or initial letter
  val displayText: String
  val isEmoji: Boolean

  if (!isUser && !assistantEmoji.isNullOrBlank()) {
    displayText = assistantEmoji
    isEmoji = true
  } else {
    displayText = when (normalized) {
      "user" -> "U"
      "assistant" -> (assistantName?.firstOrNull()?.uppercaseChar()?.toString() ?: "A")
      "tool" -> "\u2699" // gear emoji
      else -> "?"
    }
    isEmoji = false
  }

  val bgColor = if (isUser) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.tertiaryContainer
  }

  val textColor = if (isUser) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onTertiaryContainer
  }

  Box(
    modifier = modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(bgColor),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = displayText,
      fontSize = if (isEmoji) 20.sp else 16.sp,
      color = textColor,
      textAlign = TextAlign.Center,
    )
  }
}
