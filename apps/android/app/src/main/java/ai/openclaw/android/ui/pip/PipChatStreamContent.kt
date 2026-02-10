package ai.openclaw.android.ui.pip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.android.chat.ChatPendingToolCall

@Composable
fun PipChatStreamContent(
  streamingText: String?,
  pendingToolCalls: List<ChatPendingToolCall>,
  pendingRunCount: Int,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(10.dp)
      .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Pending tool calls
    if (pendingToolCalls.isNotEmpty()) {
      pendingToolCalls.forEach { toolCall ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            imageVector = Icons.Default.Build,
            contentDescription = "Tool",
            tint = Color(0xFFF1C40F),
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = toolCall.name,
            color = Color(0xFFF1C40F),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // Streaming text
    val displayText = streamingText
    if (!displayText.isNullOrBlank()) {
      Text(
        text = displayText,
        color = Color.White.copy(alpha = 0.90f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        maxLines = 12,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
      )
    } else if (pendingToolCalls.isEmpty() && pendingRunCount > 0) {
      Text(
        text = "Processing…",
        color = Color.White.copy(alpha = 0.60f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )
    } else if (pendingToolCalls.isEmpty()) {
      Text(
        text = "Idle",
        color = Color.White.copy(alpha = 0.40f),
        fontSize = 12.sp,
      )
    }
  }
}
