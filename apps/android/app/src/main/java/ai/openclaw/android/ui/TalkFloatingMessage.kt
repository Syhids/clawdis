package ai.openclaw.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TalkFloatingMessage(
  message: String?,
  unreadCount: Int,
  onTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = !message.isNullOrBlank(),
    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
    modifier = modifier,
  ) {
    val displayText = message?.let {
      if (it.length > 120) it.take(120) + "…" else it
    }.orEmpty()

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onTap),
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(20.dp),
      ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
          Text(
            text = displayText,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (unreadCount > 1) {
        Badge(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 4.dp),
          containerColor = MaterialTheme.colorScheme.error,
        ) {
          Text("$unreadCount", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
