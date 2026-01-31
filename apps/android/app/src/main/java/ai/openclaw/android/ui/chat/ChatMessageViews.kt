package ai.openclaw.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp

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
  // Check if this is an emoji-only message (1-5 emojis, no other text)
  val emojiOnlyInfo = remember(content) { detectEmojiOnlyMessage(content) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (part in content) {
      when (part.type) {
        "text" -> {
          val text = part.text ?: continue
          if (emojiOnlyInfo != null) {
            // Render emojis in large size without bubble formatting
            Text(
              text = text,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = when (emojiOnlyInfo.emojiCount) {
                  1 -> 48.sp
                  2 -> 40.sp
                  3 -> 36.sp
                  else -> 32.sp
                },
                lineHeight = when (emojiOnlyInfo.emojiCount) {
                  1 -> 56.sp
                  2 -> 48.sp
                  3 -> 44.sp
                  else -> 40.sp
                },
              ),
              color = textColor,
            )
          } else {
            ChatMarkdown(text = text, textColor = textColor)
          }
        }
        else -> {
          val b64 = part.base64 ?: continue
          ChatBase64Image(base64 = b64, mimeType = part.mimeType)
        }
      }
    }
  }
}

/**
 * Detects if a message contains only emojis (1-5 emojis, no other text or attachments).
 * Returns EmojiOnlyInfo if true, null otherwise.
 */
private data class EmojiOnlyInfo(val emojiCount: Int)

private fun detectEmojiOnlyMessage(content: List<ChatMessageContent>): EmojiOnlyInfo? {
  // Must have exactly one text part and no other parts (no images/attachments)
  val textParts = content.filter { it.type == "text" && !it.text.isNullOrBlank() }
  val otherParts = content.filter { it.type != "text" }
  if (textParts.size != 1 || otherParts.isNotEmpty()) return null

  val text = textParts.first().text?.trim() ?: return null
  if (text.isEmpty()) return null

  // Count emojis and check that the message contains only emojis
  val emojiCount = countEmojis(text)
  if (emojiCount == 0 || emojiCount > 5) return null

  // Verify that removing all emojis leaves nothing (only whitespace allowed)
  val withoutEmojis = removeEmojis(text).trim()
  if (withoutEmojis.isNotEmpty()) return null

  return EmojiOnlyInfo(emojiCount = emojiCount)
}

/**
 * Counts the number of emoji grapheme clusters in the text.
 * Uses Unicode properties to detect emojis, including:
 * - Basic emojis (😀, 🎉, ❤️, etc.)
 * - Emoji sequences (👨‍👩‍👧, 🇪🇸, etc.)
 * - Emoji with skin tone modifiers (👋🏻, 👨🏽, etc.)
 */
private fun countEmojis(text: String): Int {
  // Regex pattern for emoji detection
  // Covers: emoji presentation sequences, ZWJ sequences, flags, modifiers
  val emojiPattern = Regex(
    "(?:" +
      // Emoji ZWJ sequences (family, profession emojis)
      "(?:\\p{Emoji}(?:\\p{EMod})?(?:\\x{200D}\\p{Emoji}(?:\\p{EMod})?)+)" +
      "|" +
      // Regional indicator pairs (flags)
      "(?:[\\x{1F1E6}-\\x{1F1FF}]{2})" +
      "|" +
      // Emoji with optional modifier or variation selector
      "(?:\\p{Emoji}(?:\\p{EMod}|\\x{FE0F})?)" +
      ")",
    RegexOption.COMMENTS,
  )

  // Simplified fallback: count using a more compatible pattern
  val simplePattern = Regex("[\\p{So}\\p{Sc}]|[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]")
  val matches = simplePattern.findAll(text)

  // Count grapheme clusters that look like emojis
  var count = 0
  var i = 0
  while (i < text.length) {
    val cp = text.codePointAt(i)
    val charCount = Character.charCount(cp)

    if (isEmojiCodePoint(cp)) {
      count++
      // Skip any following modifiers, variation selectors, or ZWJ sequences
      i += charCount
      while (i < text.length) {
        val nextCp = text.codePointAt(i)
        if (isEmojiModifier(nextCp) || isVariationSelector(nextCp) || isZWJ(nextCp)) {
          i += Character.charCount(nextCp)
          // If ZWJ, also consume the next emoji
          if (isZWJ(nextCp) && i < text.length) {
            val afterZwj = text.codePointAt(i)
            if (isEmojiCodePoint(afterZwj)) {
              i += Character.charCount(afterZwj)
            }
          }
        } else {
          break
        }
      }
    } else if (Character.isWhitespace(cp)) {
      i += charCount
    } else {
      i += charCount
    }
  }

  return count
}

private fun removeEmojis(text: String): String {
  val sb = StringBuilder()
  var i = 0
  while (i < text.length) {
    val cp = text.codePointAt(i)
    val charCount = Character.charCount(cp)

    if (isEmojiCodePoint(cp)) {
      // Skip emoji and any modifiers/ZWJ sequences
      i += charCount
      while (i < text.length) {
        val nextCp = text.codePointAt(i)
        if (isEmojiModifier(nextCp) || isVariationSelector(nextCp) || isZWJ(nextCp)) {
          i += Character.charCount(nextCp)
          if (isZWJ(nextCp) && i < text.length) {
            val afterZwj = text.codePointAt(i)
            if (isEmojiCodePoint(afterZwj)) {
              i += Character.charCount(afterZwj)
            }
          }
        } else {
          break
        }
      }
    } else {
      sb.appendCodePoint(cp)
      i += charCount
    }
  }
  return sb.toString()
}

private fun isEmojiCodePoint(cp: Int): Boolean {
  // Common emoji ranges
  return when {
    // Emoticons
    cp in 0x1F600..0x1F64F -> true
    // Misc symbols and pictographs
    cp in 0x1F300..0x1F5FF -> true
    // Transport and map symbols
    cp in 0x1F680..0x1F6FF -> true
    // Symbols and Pictographs Extended-A
    cp in 0x1FA70..0x1FAFF -> true
    // Supplemental symbols and pictographs
    cp in 0x1F900..0x1F9FF -> true
    // Regional indicator symbols (flags)
    cp in 0x1F1E0..0x1F1FF -> true
    // Dingbats
    cp in 0x2700..0x27BF -> true
    // Misc symbols
    cp in 0x2600..0x26FF -> true
    // Some specific emojis
    cp == 0x2764 -> true // ❤
    cp == 0x2763 -> true // ❣
    cp == 0x2618 -> true // ☘
    cp == 0x2639 -> true // ☹
    cp == 0x263A -> true // ☺
    cp == 0x270C -> true // ✌
    cp == 0x270D -> true // ✍
    cp == 0x270B -> true // ✋
    // Enclosed alphanumerics supplement (some are emoji)
    cp in 0x1F170..0x1F1FF -> true
    else -> false
  }
}

private fun isEmojiModifier(cp: Int): Boolean {
  // Skin tone modifiers
  return cp in 0x1F3FB..0x1F3FF
}

private fun isVariationSelector(cp: Int): Boolean {
  // FE0F is emoji presentation selector, FE0E is text presentation
  return cp == 0xFE0F || cp == 0xFE0E
}

private fun isZWJ(cp: Int): Boolean {
  return cp == 0x200D
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
