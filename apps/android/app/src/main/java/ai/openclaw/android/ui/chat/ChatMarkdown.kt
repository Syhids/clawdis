package ai.openclaw.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatMarkdown(text: String, textColor: Color) {
  val blocks = remember(text) { splitMarkdown(text) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (b in blocks) {
      when (b) {
        is ChatMarkdownBlock.Text -> {
          val trimmed = b.text.trimEnd()
          if (trimmed.isEmpty()) continue
          Markdown(
            content = trimmed,
            colors = markdownColor(
              text = textColor,
              codeText = MaterialTheme.colorScheme.onSurface,
              codeBackground = MaterialTheme.colorScheme.surfaceContainerLowest,
              linkText = MaterialTheme.colorScheme.primary,
              tableText = textColor,
            ),
            typography = markdownTypography(
              text = MaterialTheme.typography.bodyMedium,
              h1 = MaterialTheme.typography.headlineSmall,
              h2 = MaterialTheme.typography.titleLarge,
              h3 = MaterialTheme.typography.titleMedium,
              h4 = MaterialTheme.typography.titleSmall,
              h5 = MaterialTheme.typography.labelLarge,
              h6 = MaterialTheme.typography.labelMedium,
              paragraph = MaterialTheme.typography.bodyMedium,
              ordered = MaterialTheme.typography.bodyMedium,
              bullet = MaterialTheme.typography.bodyMedium,
              list = MaterialTheme.typography.bodyMedium,
            ),
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
      }
    }
  }
}

private sealed interface ChatMarkdownBlock {
  data class Text(val text: String) : ChatMarkdownBlock
  data class Code(val code: String, val language: String?) : ChatMarkdownBlock
  data class InlineImage(val mimeType: String?, val base64: String) : ChatMarkdownBlock
}

private fun splitMarkdown(raw: String): List<ChatMarkdownBlock> {
  if (raw.isEmpty()) return emptyList()

  val out = ArrayList<ChatMarkdownBlock>()
  var idx = 0
  while (idx < raw.length) {
    val fenceStart = raw.indexOf("```", startIndex = idx)
    if (fenceStart < 0) {
      out.addAll(splitInlineImages(raw.substring(idx)))
      break
    }

    if (fenceStart > idx) {
      out.addAll(splitInlineImages(raw.substring(idx, fenceStart)))
    }

    val langLineStart = fenceStart + 3
    val langLineEnd = raw.indexOf('\n', startIndex = langLineStart).let { if (it < 0) raw.length else it }
    val language = raw.substring(langLineStart, langLineEnd).trim().ifEmpty { null }

    val codeStart = if (langLineEnd < raw.length && raw[langLineEnd] == '\n') langLineEnd + 1 else langLineEnd
    val fenceEnd = raw.indexOf("```", startIndex = codeStart)
    if (fenceEnd < 0) {
      out.addAll(splitInlineImages(raw.substring(fenceStart)))
      break
    }
    val code = raw.substring(codeStart, fenceEnd)
    out.add(ChatMarkdownBlock.Code(code = code, language = language))

    idx = fenceEnd + 3
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
