package ai.openclaw.android.ui.chat

import ai.openclaw.android.chat.ChatMessage
import ai.openclaw.android.chat.ChatPendingToolCall

/**
 * Items that can appear in the chat list after grouping.
 */
sealed class ChatListItem {
  data class MessageGroup(
    val role: String,
    val messages: List<ChatMessage>,
    val timestamp: Long?,
  ) : ChatListItem() {
    val key: String
      get() = "group:${role}:${messages.firstOrNull()?.id ?: "empty"}"
  }

  data class StreamingGroup(
    val text: String,
    val startedAt: Long,
  ) : ChatListItem()

  data class TypingIndicator(
    val toolCalls: List<ChatPendingToolCall> = emptyList(),
  ) : ChatListItem()
}

/**
 * Groups consecutive messages with the same role into [ChatListItem.MessageGroup]s.
 * Mirrors the web dashboard's `groupMessages()` logic.
 */
fun groupMessages(messages: List<ChatMessage>): List<ChatListItem.MessageGroup> {
  if (messages.isEmpty()) return emptyList()

  val result = mutableListOf<ChatListItem.MessageGroup>()
  var currentRole: String? = null
  var currentMessages = mutableListOf<ChatMessage>()
  var currentTimestamp: Long? = null

  for (msg in messages) {
    val role = normalizeRoleForGrouping(msg.role)
    if (role == currentRole) {
      currentMessages.add(msg)
      // Use the latest timestamp in the group
      msg.timestampMs?.let { currentTimestamp = it }
    } else {
      if (currentMessages.isNotEmpty() && currentRole != null) {
        result.add(
          ChatListItem.MessageGroup(
            role = currentRole!!,
            messages = currentMessages.toList(),
            timestamp = currentTimestamp,
          ),
        )
      }
      currentRole = role
      currentMessages = mutableListOf(msg)
      currentTimestamp = msg.timestampMs
    }
  }

  if (currentMessages.isNotEmpty() && currentRole != null) {
    result.add(
      ChatListItem.MessageGroup(
        role = currentRole!!,
        messages = currentMessages.toList(),
        timestamp = currentTimestamp,
      ),
    )
  }

  return result
}

private fun normalizeRoleForGrouping(role: String): String {
  return when (role.trim().lowercase()) {
    "user" -> "user"
    "assistant" -> "assistant"
    "tool" -> "assistant" // tool results group with assistant
    else -> role.trim().lowercase()
  }
}
