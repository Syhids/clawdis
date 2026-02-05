package ai.openclaw.android.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
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
        is ChatMarkdownBlock.Table -> {
          ChatTable(table = b, textColor = textColor, inlineCodeBg = inlineCodeBg)
        }
      }
    }
  }
}

private sealed interface ChatMarkdownBlock {
  data class Text(val text: String) : ChatMarkdownBlock
  data class Code(val code: String, val language: String?) : ChatMarkdownBlock
  data class InlineImage(val mimeType: String?, val base64: String) : ChatMarkdownBlock
  data class Table(val headers: List<String>, val rows: List<List<String>>, val alignments: List<TableAlignment>) : ChatMarkdownBlock
}

enum class TableAlignment { Left, Center, Right }

private fun splitMarkdown(raw: String): List<ChatMarkdownBlock> {
  if (raw.isEmpty()) return emptyList()

  val out = ArrayList<ChatMarkdownBlock>()
  var idx = 0
  while (idx < raw.length) {
    val fenceStart = raw.indexOf("```", startIndex = idx)
    if (fenceStart < 0) {
      out.addAll(splitTablesAndImages(raw.substring(idx)))
      break
    }

    if (fenceStart > idx) {
      out.addAll(splitTablesAndImages(raw.substring(idx, fenceStart)))
    }

    val langLineStart = fenceStart + 3
    val langLineEnd = raw.indexOf('\n', startIndex = langLineStart).let { if (it < 0) raw.length else it }
    val language = raw.substring(langLineStart, langLineEnd).trim().ifEmpty { null }

    val codeStart = if (langLineEnd < raw.length && raw[langLineEnd] == '\n') langLineEnd + 1 else langLineEnd
    val fenceEnd = raw.indexOf("```", startIndex = codeStart)
    if (fenceEnd < 0) {
      out.addAll(splitTablesAndImages(raw.substring(fenceStart)))
      break
    }
    val code = raw.substring(codeStart, fenceEnd)
    out.add(ChatMarkdownBlock.Code(code = code, language = language))

    idx = fenceEnd + 3
  }

  return out
}

private fun splitTablesAndImages(text: String): List<ChatMarkdownBlock> {
  if (text.isEmpty()) return emptyList()

  val lines = text.lines()
  val out = ArrayList<ChatMarkdownBlock>()
  var textBuffer = StringBuilder()
  var i = 0

  while (i < lines.size) {
    val line = lines[i]

    // Check if this could be the start of a table (header row)
    if (line.contains('|') && i + 1 < lines.size) {
      val separatorLine = lines[i + 1]
      if (isTableSeparator(separatorLine)) {
        // Flush text buffer
        if (textBuffer.isNotEmpty()) {
          out.addAll(splitInlineImages(textBuffer.toString()))
          textBuffer = StringBuilder()
        }

        // Parse table
        val headerCells = parseTableRow(line)
        val alignments = parseAlignments(separatorLine, headerCells.size)

        val rows = ArrayList<List<String>>()
        var j = i + 2
        while (j < lines.size && lines[j].contains('|')) {
          val rowCells = parseTableRow(lines[j])
          if (rowCells.isNotEmpty()) {
            // Pad or trim to match header count
            val normalizedRow = (0 until headerCells.size).map { idx ->
              rowCells.getOrElse(idx) { "" }
            }
            rows.add(normalizedRow)
          }
          j++
        }

        out.add(ChatMarkdownBlock.Table(headers = headerCells, rows = rows, alignments = alignments))
        i = j
        continue
      }
    }

    // Regular line
    if (textBuffer.isNotEmpty()) textBuffer.append("\n")
    textBuffer.append(line)
    i++
  }

  if (textBuffer.isNotEmpty()) {
    out.addAll(splitInlineImages(textBuffer.toString()))
  }

  return out
}

private fun isTableSeparator(line: String): Boolean {
  val trimmed = line.trim()
  if (!trimmed.contains('|')) return false

  // Separator line should only contain |, -, :, and spaces
  val separatorChars = setOf('|', '-', ':', ' ')
  if (!trimmed.all { it in separatorChars }) return false

  // Should have at least one dash
  return trimmed.contains('-')
}

private fun parseTableRow(line: String): List<String> {
  val trimmed = line.trim()

  // Remove leading/trailing pipes if present
  val content = when {
    trimmed.startsWith('|') && trimmed.endsWith('|') -> trimmed.substring(1, trimmed.length - 1)
    trimmed.startsWith('|') -> trimmed.substring(1)
    trimmed.endsWith('|') -> trimmed.substring(0, trimmed.length - 1)
    else -> trimmed
  }

  return content.split('|').map { it.trim() }
}

private fun parseAlignments(separatorLine: String, columnCount: Int): List<TableAlignment> {
  val cells = parseTableRow(separatorLine)
  return (0 until columnCount).map { idx ->
    val cell = cells.getOrElse(idx) { "" }.trim()
    when {
      cell.startsWith(':') && cell.endsWith(':') -> TableAlignment.Center
      cell.endsWith(':') -> TableAlignment.Right
      else -> TableAlignment.Left
    }
  }
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
private fun ChatTable(
  table: ChatMarkdownBlock.Table,
  textColor: Color,
  inlineCodeBg: Color,
) {
  val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
  val rowBg = MaterialTheme.colorScheme.surfaceContainerLow
  val altRowBg = MaterialTheme.colorScheme.surfaceContainer
  val borderColor = MaterialTheme.colorScheme.outlineVariant

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier
        .horizontalScroll(rememberScrollState())
        .clip(RoundedCornerShape(8.dp))
    ) {
      Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        // Header row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(horizontal = 1.dp)
        ) {
          table.headers.forEachIndexed { idx, header ->
            val alignment = table.alignments.getOrElse(idx) { TableAlignment.Left }
            Box(
              modifier = Modifier
                .weight(1f, fill = false)
                .width(IntrinsicSize.Max)
                .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
              Text(
                text = parseInlineMarkdown(header, inlineCodeBg = inlineCodeBg),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = alignment.toTextAlign(),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }

        HorizontalDivider(color = borderColor, thickness = 1.dp)

        // Data rows
        table.rows.forEachIndexed { rowIdx, row ->
          val bg = if (rowIdx % 2 == 0) rowBg else altRowBg
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(bg)
              .padding(horizontal = 1.dp)
          ) {
            row.forEachIndexed { cellIdx, cell ->
              val alignment = table.alignments.getOrElse(cellIdx) { TableAlignment.Left }
              Box(
                modifier = Modifier
                  .weight(1f, fill = false)
                  .width(IntrinsicSize.Max)
                  .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = parseInlineMarkdown(cell, inlineCodeBg = inlineCodeBg),
                  style = MaterialTheme.typography.bodySmall,
                  color = textColor,
                  textAlign = alignment.toTextAlign(),
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }

          if (rowIdx < table.rows.size - 1) {
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
          }
        }
      }
    }
  }
}

private fun TableAlignment.toTextAlign(): TextAlign = when (this) {
  TableAlignment.Left -> TextAlign.Start
  TableAlignment.Center -> TextAlign.Center
  TableAlignment.Right -> TextAlign.End
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
