package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StaticTextFormField(field: FormField.StaticText) {
  val (bg, fg, emoji) = when (field.style) {
    StaticTextStyle.INFO -> Triple(
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
      MaterialTheme.colorScheme.onPrimaryContainer,
      "ℹ️",
    )
    StaticTextStyle.WARNING -> Triple(
      MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
      MaterialTheme.colorScheme.onTertiaryContainer,
      "⚠️",
    )
    StaticTextStyle.ERROR -> Triple(
      MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
      MaterialTheme.colorScheme.onErrorContainer,
      "❌",
    )
    StaticTextStyle.SUCCESS -> Triple(
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
      MaterialTheme.colorScheme.onPrimaryContainer,
      "✅",
    )
  }

  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(8.dp)) {
      Text(emoji)
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = field.text,
        style = MaterialTheme.typography.bodySmall,
        color = fg,
      )
    }
  }
}
