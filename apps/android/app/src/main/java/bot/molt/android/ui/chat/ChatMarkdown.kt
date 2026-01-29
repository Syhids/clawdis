package bot.molt.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatMarkdown(text: String, textColor: Color) {
  val blocks = remember(text) { splitMarkdown(text) }
  val inlineCodeBg = MaterialTheme.colorScheme.surfaceContainerLow

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (b in blocks) {
      when (b) {
        is ChatMarkdownBlock.Text -> {
          val trimmed = b.text.trimEnd()
          if (trimmed.isEmpty()) continue
          Text(
            text = parseInlineMarkdown(trimmed, inlineCodeBg = inlineCodeBg),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
          )
        }
        is ChatMarkdownBlock.Code -> {
          SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            ChatCodeBlock(code = b.code, language = b.language)
          }
        }
        is ChatMarkdownBlock.InlineImage -> {
          InlineBase64Image(base64 = b.base64, mimeType = b.mimeType)
        }
        is ChatMarkdownBlock.Quote -> {
          ChatBlockquote(content = b.content, textColor = textColor)
        }
      }
    }
  }
}

private sealed interface ChatMarkdownBlock {
  data class Text(val text: String) : ChatMarkdownBlock
  data class Code(val code: String, val language: String?) : ChatMarkdownBlock
  data class InlineImage(val mimeType: String?, val base64: String) : ChatMarkdownBlock
  data class Quote(val content: String) : ChatMarkdownBlock
}

private fun splitMarkdown(raw: String): List<ChatMarkdownBlock> {
  if (raw.isEmpty()) return emptyList()

  val out = ArrayList<ChatMarkdownBlock>()
  var idx = 0
  while (idx < raw.length) {
    val fenceStart = raw.indexOf("```", startIndex = idx)
    if (fenceStart < 0) {
      out.addAll(splitBlockquotesAndImages(raw.substring(idx)))
      break
    }

    if (fenceStart > idx) {
      out.addAll(splitBlockquotesAndImages(raw.substring(idx, fenceStart)))
    }

    val langLineStart = fenceStart + 3
    val langLineEnd = raw.indexOf('\n', startIndex = langLineStart).let { if (it < 0) raw.length else it }
    val language = raw.substring(langLineStart, langLineEnd).trim().ifEmpty { null }

    val codeStart = if (langLineEnd < raw.length && raw[langLineEnd] == '\n') langLineEnd + 1 else langLineEnd
    val fenceEnd = raw.indexOf("```", startIndex = codeStart)
    if (fenceEnd < 0) {
      out.addAll(splitBlockquotesAndImages(raw.substring(fenceStart)))
      break
    }
    val code = raw.substring(codeStart, fenceEnd)
    out.add(ChatMarkdownBlock.Code(code = code, language = language))

    idx = fenceEnd + 3
  }

  return out
}

private fun splitBlockquotesAndImages(text: String): List<ChatMarkdownBlock> {
  if (text.isEmpty()) return emptyList()

  val out = ArrayList<ChatMarkdownBlock>()
  val lines = text.lines()
  var i = 0

  while (i < lines.size) {
    val line = lines[i]

    // Check if line starts with blockquote marker
    if (line.trimStart().startsWith(">")) {
      val quoteLines = mutableListOf<String>()

      // Collect consecutive blockquote lines
      while (i < lines.size && lines[i].trimStart().startsWith(">")) {
        val quoteLine = lines[i].trimStart().removePrefix(">").removePrefix(" ")
        quoteLines.add(quoteLine)
        i++
      }

      val quoteContent = quoteLines.joinToString("\n")
      out.add(ChatMarkdownBlock.Quote(content = quoteContent))
    } else {
      // Collect consecutive non-blockquote lines
      val textLines = mutableListOf<String>()
      while (i < lines.size && !lines[i].trimStart().startsWith(">")) {
        textLines.add(lines[i])
        i++
      }

      val textContent = textLines.joinToString("\n")
      out.addAll(splitInlineImages(textContent))
    }
  }

  return out
}

private fun splitInlineImages(text: String): List<ChatMarkdownBlock> {
  if (text.isEmpty()) return emptyList()
  val regex = Regex("data:image/([a-zA-Z0-9+.-]+);base64,([A-Za-z0-9+/=\\n\\r]+)")
  val out = ArrayList<ChatMarkdownBlock>()

  var idx = 0
  while (idx < text.length) {
    val m = regex.find(text, startIndex = idx) ?: break
    val start = m.range.first
    val end = m.range.last + 1
    if (start > idx) out.add(ChatMarkdownBlock.Text(text.substring(idx, start)))

    val mime = "image/" + (m.groupValues.getOrNull(1)?.trim()?.ifEmpty { "png" } ?: "png")
    val b64 = m.groupValues.getOrNull(2)?.replace("\n", "")?.replace("\r", "")?.trim().orEmpty()
    if (b64.isNotEmpty()) {
      out.add(ChatMarkdownBlock.InlineImage(mimeType = mime, base64 = b64))
    }
    idx = end
  }

  if (idx < text.length) out.add(ChatMarkdownBlock.Text(text.substring(idx)))
  return out
}

private fun parseInlineMarkdown(text: String, inlineCodeBg: androidx.compose.ui.graphics.Color): AnnotatedString {
  if (text.isEmpty()) return AnnotatedString("")

  val out = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
      // Bold: **text**
      if (text.startsWith("**", startIndex = i)) {
        val end = text.indexOf("**", startIndex = i + 2)
        if (end > i + 2) {
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(text.substring(i + 2, end))
          }
          i = end + 2
          continue
        }
      }

      // Strikethrough: ~~text~~
      if (text.startsWith("~~", startIndex = i)) {
        val end = text.indexOf("~~", startIndex = i + 2)
        if (end > i + 2) {
          withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            append(text.substring(i + 2, end))
          }
          i = end + 2
          continue
        }
      }

      // Inline code: `code`
      if (text[i] == '`') {
        val end = text.indexOf('`', startIndex = i + 1)
        if (end > i + 1) {
          withStyle(
            SpanStyle(
              fontFamily = FontFamily.Monospace,
              background = inlineCodeBg,
            ),
          ) {
            append(text.substring(i + 1, end))
          }
          i = end + 1
          continue
        }
      }

      // Italic: *text*
      if (text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*')) {
        val end = text.indexOf('*', startIndex = i + 1)
        if (end > i + 1) {
          withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append(text.substring(i + 1, end))
          }
          i = end + 1
          continue
        }
      }

      append(text[i])
      i += 1
    }
  }
  return out
}

@Composable
private fun ChatBlockquote(content: String, textColor: Color) {
  val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
  val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
  val inlineCodeBg = MaterialTheme.colorScheme.surfaceContainerLow

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min)
      .clip(RoundedCornerShape(4.dp))
      .background(backgroundColor),
  ) {
    // Left accent bar
    Box(
      modifier = Modifier
        .width(4.dp)
        .fillMaxHeight()
        .background(accentColor),
    )

    // Quote content
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val lines = content.split("\n")
      for (line in lines) {
        if (line.isNotEmpty()) {
          Text(
            text = parseInlineMarkdown(line, inlineCodeBg = inlineCodeBg),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor.copy(alpha = 0.85f),
            fontStyle = FontStyle.Italic,
          )
        }
      }
    }
  }
}

@Composable
private fun InlineBase64Image(base64: String, mimeType: String?) {
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
      contentDescription = mimeType ?: "image",
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxWidth(),
    )
  } else if (failed) {
    Text(
      text = "Image unavailable",
      modifier = Modifier.padding(vertical = 2.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
