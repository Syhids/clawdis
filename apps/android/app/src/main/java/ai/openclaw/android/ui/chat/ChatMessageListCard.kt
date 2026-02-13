package ai.openclaw.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import ai.openclaw.android.chat.ChatMessage
import ai.openclaw.android.chat.ChatPendingToolCall
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
  val coroutineScope = rememberCoroutineScope()

  // Build grouped items from messages
  val groups = remember(messages) { groupMessages(messages) }

  // Build the full list of ChatListItems for the LazyColumn
  val items = remember(groups, pendingRunCount, pendingToolCalls, streamingAssistantText) {
    buildList<ChatListItem> {
      addAll(groups)
      val stream = streamingAssistantText?.trim()
      if (!stream.isNullOrEmpty()) {
        add(ChatListItem.StreamingGroup(text = stream, startedAt = System.currentTimeMillis()))
      }
      if (pendingRunCount > 0 || pendingToolCalls.isNotEmpty()) {
        add(ChatListItem.TypingIndicator(toolCalls = pendingToolCalls))
      }
    }
  }

  // Smart scroll: detect if user is near bottom
  val isNearBottom by remember {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val totalItems = layoutInfo.totalItemsCount
      if (totalItems == 0) return@derivedStateOf true
      val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      // Consider "near bottom" if within 3 items of the end
      lastVisible >= totalItems - 3
    }
  }

  // Track whether there are new messages while scrolled up
  var showNewMessagesIndicator by remember { mutableStateOf(false) }

  // Auto-scroll only if near bottom; otherwise show indicator
  LaunchedEffect(items.size, streamingAssistantText) {
    if (items.isEmpty()) return@LaunchedEffect
    if (isNearBottom) {
      listState.animateScrollToItem(index = items.size - 1)
      showNewMessagesIndicator = false
    } else {
      showNewMessagesIndicator = true
    }
  }

  // Hide indicator when user scrolls to bottom manually
  LaunchedEffect(isNearBottom) {
    if (isNearBottom) {
      showNewMessagesIndicator = false
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
      ) {
        items(
          count = items.size,
          key = { idx ->
            when (val item = items[idx]) {
              is ChatListItem.MessageGroup -> item.key
              is ChatListItem.StreamingGroup -> "stream"
              is ChatListItem.TypingIndicator -> "typing"
            }
          },
        ) { idx ->
          when (val item = items[idx]) {
            is ChatListItem.MessageGroup -> {
              ChatMessageGroupView(group = item)
            }
            is ChatListItem.StreamingGroup -> {
              ChatStreamingGroupView(text = item.text)
            }
            is ChatListItem.TypingIndicator -> {
              ChatTypingGroupView(toolCalls = item.toolCalls)
            }
          }
        }
      }

      // Empty state
      if (messages.isEmpty() && pendingRunCount == 0 && pendingToolCalls.isEmpty() && streamingAssistantText.isNullOrBlank()) {
        EmptyChatHint(modifier = Modifier.align(Alignment.Center))
      }

      // "New messages" floating button
      if (showNewMessagesIndicator) {
        FilledTonalButton(
          onClick = {
            showNewMessagesIndicator = false
            coroutineScope.launch {
              if (items.isNotEmpty()) {
                listState.animateScrollToItem(index = items.size - 1)
              }
            }
          },
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp),
          shape = RoundedCornerShape(999.dp),
        ) {
          Text("New messages")
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp),
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
      text = "Message OpenClaw\u2026",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
