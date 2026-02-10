package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RatingFormField(
  field: FormField.Rating,
  value: FormValue?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val currentRating = (value as? FormValue.RatingValue)?.stars ?: field.defaultValue

  Column {
    Text(
      text = field.label,
      style = MaterialTheme.typography.labelMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      repeat(field.maxStars) { index ->
        val starIndex = index + 1
        val filled = starIndex <= currentRating
        Icon(
          imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = "Star $starIndex",
          tint = if (filled) {
            Color(0xFFFFB300) // Amber
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
          modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled) {
              onValueChanged(FormValue.RatingValue(starIndex))
            },
        )
      }
    }

    field.helpText?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
