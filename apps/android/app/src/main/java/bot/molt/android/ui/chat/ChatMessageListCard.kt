package bot.molt.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import bot.molt.android.chat.ChatMessage
import bot.molt.android.chat.ChatPendingToolCall
import kotlinx.coroutines.launch

@Composable
fun ChatMessageListCard(
  messages: List<ChatMessage>,
  pendingRunCount: Int,
  pendingToolCalls: List<ChatPendingToolCall>,
  streamingAssistantText: String?,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  // Calculate total item count (messages + indicator bubbles)
  val totalItemCount by remember(messages.size, pendingRunCount, pendingToolCalls.size, streamingAssistantText) {
    derivedStateOf {
      messages.size +
        (if (pendingRunCount > 0) 1 else 0) +
        (if (pendingToolCalls.isNotEmpty()) 1 else 0) +
        (if (!streamingAssistantText.isNullOrBlank()) 1 else 0)
    }
  }

  // Track if user is near the bottom (within 2 items of the end)
  val isNearBottom by remember {
    derivedStateOf {
      val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      val total = listState.layoutInfo.totalItemsCount
      total <= 0 || lastVisible >= total - 2
    }
  }

  // Track last auto-scrolled count to detect new content
  var lastAutoScrolledCount by remember { mutableIntStateOf(0) }

  // Auto-scroll only when near bottom
  LaunchedEffect(totalItemCount, isNearBottom) {
    if (totalItemCount <= 0) return@LaunchedEffect
    if (isNearBottom || lastAutoScrolledCount == 0) {
      listState.animateScrollToItem(index = totalItemCount - 1)
      lastAutoScrolledCount = totalItemCount
    }
  }

  // Show scroll-to-bottom FAB when not near bottom
  val showScrollToBottom by remember {
    derivedStateOf {
      val total = listState.layoutInfo.totalItemsCount
      if (total <= 3) return@derivedStateOf false
      !isNearBottom
    }
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
      ) {
        items(count = messages.size, key = { idx -> messages[idx].id }) { idx ->
          ChatMessageBubble(message = messages[idx])
        }

        if (pendingRunCount > 0) {
          item(key = "typing") {
            ChatTypingIndicatorBubble()
          }
        }

        if (pendingToolCalls.isNotEmpty()) {
          item(key = "tools") {
            ChatPendingToolsBubble(toolCalls = pendingToolCalls)
          }
        }

        val stream = streamingAssistantText?.trim()
        if (!stream.isNullOrEmpty()) {
          item(key = "stream") {
            ChatStreamingAssistantBubble(text = stream)
          }
        }
      }

      if (messages.isEmpty() && pendingRunCount == 0 && pendingToolCalls.isEmpty() && streamingAssistantText.isNullOrBlank()) {
        EmptyChatHint(modifier = Modifier.align(Alignment.Center))
      }

      // Scroll to bottom FAB
      if (showScrollToBottom) {
        FilledTonalIconButton(
          onClick = {
            scope.launch {
              if (totalItemCount > 0) {
                listState.animateScrollToItem(index = totalItemCount - 1)
              }
            }
          },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp)
            .size(40.dp),
          shape = CircleShape,
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
        ) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Scroll to bottom",
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.alpha(0.7f),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = Icons.Default.ArrowCircleDown,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "Message Clawd…",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
