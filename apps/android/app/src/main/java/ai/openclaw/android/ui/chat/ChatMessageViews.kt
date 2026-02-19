package ai.openclaw.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import ai.openclaw.android.chat.ChatMessage
import ai.openclaw.android.chat.ChatMessageContent
import ai.openclaw.android.chat.ChatPendingToolCall
import ai.openclaw.android.tools.ToolDisplayRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

@Composable
fun ChatMessageBubble(message: ChatMessage) {
  val isUser = message.role.lowercase() == "user"

  // Filter to only displayable content parts (text with content, or base64 images)
  val displayableContent = message.content.filter { part ->
    when (part.type) {
      "text" -> !part.text.isNullOrBlank()
      else -> part.base64 != null
    }
  }

  // Skip rendering entirely if no displayable content
  if (displayableContent.isEmpty()) return

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
        ChatMessageBody(content = displayableContent, textColor = textColor)
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
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Running tools…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
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
            "… +${toolCalls.size - 6} more",
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
  var image by remember(base64) { mutableStateOf<ImageBitmap?>(null) }
  var failed by remember(base64) { mutableStateOf(false) }
  var showFullscreen by remember { mutableStateOf(false) }

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

  val loadedImage = image
  if (loadedImage != null) {
    Image(
      bitmap = loadedImage,
      contentDescription = mimeType ?: "attachment",
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .clickable { showFullscreen = true },
    )

    if (showFullscreen) {
      ImageFullscreenDialog(
        image = loadedImage,
        contentDescription = mimeType ?: "attachment",
        onDismiss = { showFullscreen = false },
      )
    }
  } else if (failed) {
    Text("Unsupported attachment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun ImageFullscreenDialog(
  image: ImageBitmap,
  contentDescription: String,
  onDismiss: () -> Unit,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.92f))
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(0.5f, 5f)
            offset = Offset(
              x = offset.x + pan.x,
              y = offset.y + pan.y,
            )
          }
        },
    ) {
      Image(
        bitmap = image,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y,
          ),
      )

      // Close button
      FilledTonalIconButton(
        onClick = onDismiss,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(16.dp),
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close")
      }

      // Hint text for zoom
      if (scale == 1f && offset == Offset.Zero) {
        Text(
          text = "Pinch to zoom • Tap X to close",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.7f),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp),
        )
      }
    }
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
