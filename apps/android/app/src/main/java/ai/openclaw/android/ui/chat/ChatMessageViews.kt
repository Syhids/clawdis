package ai.openclaw.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import ai.openclaw.android.chat.ChatMessage
import ai.openclaw.android.chat.ChatMessageContent
import ai.openclaw.android.chat.ChatPendingToolCall
import ai.openclaw.android.tools.ToolDisplayRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Grouped Message Views ──────────────────────────────────────────────────

/**
 * Renders a message group: stacked bubbles + footer (name · timestamp).
 */
@Composable
fun ChatMessageGroupView(group: ChatListItem.MessageGroup) {
  val isUser = group.role == "user"

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Bottom,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(0.85f),
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
      // Stacked message bubbles
      for ((index, message) in group.messages.withIndex()) {
        if (index > 0) {
          Spacer(modifier = Modifier.height(3.dp))
        }
        GroupedBubble(message = message, isUser = isUser)
      }

      // Footer: sender name · timestamp
      Spacer(modifier = Modifier.height(4.dp))
      GroupFooter(
        role = group.role,
        timestamp = group.timestamp,
        isUser = isUser,
      )
    }
  }
}

/**
 * A single bubble within a group (no avatar, tighter spacing).
 */
@Composable
private fun GroupedBubble(message: ChatMessage, isUser: Boolean) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    color = Color.Transparent,
  ) {
    Box(
      modifier = Modifier
        .background(bubbleBackground(isUser))
        .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
      val textColor = textColorOverBubble(isUser)
      ChatMessageBody(content = message.content, textColor = textColor)
    }
  }
}

/**
 * Footer showing sender name and formatted timestamp.
 * Format: "JARVIS · 14:30" or "You · 14:31"
 */
@Composable
private fun GroupFooter(role: String, timestamp: Long?, isUser: Boolean) {
  val senderName = if (isUser) "You" else "JARVIS"
  val timeString = timestamp?.let { formatTimestamp(it) }

  val label = if (timeString != null) {
    "$senderName \u00B7 $timeString"
  } else {
    senderName
  }

  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.alpha(0.7f),
  )
}

/**
 * Streaming group: streaming bubble, displayed as assistant.
 */
@Composable
fun ChatStreamingGroupView(text: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.Bottom,
  ) {
    Column(modifier = Modifier.fillMaxWidth(0.85f)) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
          ChatMarkdown(text = text, textColor = MaterialTheme.colorScheme.onSurface)
        }
      }
    }
  }
}

/**
 * Typing indicator showing dot pulse + optional tool calls.
 */
@Composable
fun ChatTypingGroupView(toolCalls: List<ChatPendingToolCall>) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.Bottom,
  ) {
    Column(modifier = Modifier.fillMaxWidth(0.85f)) {
      if (toolCalls.isNotEmpty()) {
        ChatPendingToolsBubble(toolCalls = toolCalls)
      } else {
        ChatTypingIndicatorBubble()
      }
    }
  }
}

// ─── Timestamp Formatting ───────────────────────────────────────────────────

private fun formatTimestamp(timestampMs: Long): String {
  val date = Date(timestampMs)
  val format = SimpleDateFormat("HH:mm", Locale.getDefault())
  return format.format(date)
}

// ─── Legacy Composables (kept for compatibility) ────────────────────────────

@Composable
fun ChatMessageBubble(message: ChatMessage) {
  val isUser = message.role.lowercase() == "user"

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      color = Color.Transparent,
      modifier = Modifier.fillMaxWidth(0.92f),
    ) {
      Box(
        modifier =
          Modifier
            .background(bubbleBackground(isUser))
            .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        val textColor = textColorOverBubble(isUser)
        ChatMessageBody(content = message.content, textColor = textColor)
      }
    }
  }
}

@Composable
private fun ChatMessageBody(content: List<ChatMessageContent>, textColor: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (part in content) {
      when (part.type) {
        "text" -> {
          val text = part.text ?: continue
          ChatMarkdown(text = text, textColor = textColor)
        }
        else -> {
          val b64 = part.base64 ?: continue
          ChatBase64Image(base64 = b64, mimeType = part.mimeType)
        }
      }
    }
  }
}

@Composable
fun ChatTypingIndicatorBubble() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DotPulse()
        Text("Thinking\u2026", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
fun ChatPendingToolsBubble(toolCalls: List<ChatPendingToolCall>) {
  val context = LocalContext.current
  val displays =
    remember(toolCalls, context) {
      toolCalls.map { ToolDisplayRegistry.resolve(context, it.name, it.args) }
    }
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Running tools\u2026", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        for (display in displays.take(6)) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              "${display.emoji} ${display.label}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontFamily = FontFamily.Monospace,
            )
            display.detailLine?.let { detail ->
              Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
        if (toolCalls.size > 6) {
          Text(
            "\u2026 +${toolCalls.size - 6} more",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun ChatStreamingAssistantBubble(text: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        ChatMarkdown(text = text, textColor = MaterialTheme.colorScheme.onSurface)
      }
    }
  }
}

@Composable
private fun bubbleBackground(isUser: Boolean): Brush {
  return if (isUser) {
    Brush.linearGradient(
      colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
    )
  } else {
    Brush.linearGradient(
      colors = listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerHigh),
    )
  }
}

@Composable
private fun textColorOverBubble(isUser: Boolean): Color {
  return if (isUser) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onSurface
  }
}

@Composable
private fun ChatBase64Image(base64: String, mimeType: String?) {
  var image by remember(base64) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
  var failed by remember(base64) { mutableStateOf(false) }

  LaunchedEffect(base64) {
    failed = false
    image =
      withContext(Dispatchers.Default) {
        try {
          val bytes = Base64.decode(base64, Base64.DEFAULT)
          val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
          bitmap.asImageBitmap()
        } catch (_: Throwable) {
          null
        }
      }
    if (image == null) failed = true
  }

  if (image != null) {
    Image(
      bitmap = image!!,
      contentDescription = mimeType ?: "attachment",
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxWidth(),
    )
  } else if (failed) {
    Text("Unsupported attachment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun DotPulse() {
  Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    PulseDot(alpha = 0.38f)
    PulseDot(alpha = 0.62f)
    PulseDot(alpha = 0.90f)
  }
}

@Composable
private fun PulseDot(alpha: Float) {
  Surface(
    modifier = Modifier.size(6.dp).alpha(alpha),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  ) {}
}

@Composable
fun ChatCodeBlock(code: String, language: String?) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = code.trimEnd(),
      modifier = Modifier.padding(10.dp),
      fontFamily = FontFamily.Monospace,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
