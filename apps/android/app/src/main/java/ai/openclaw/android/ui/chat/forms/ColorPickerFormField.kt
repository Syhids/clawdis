package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerFormField(
  field: FormField.ColorPicker,
  value: FormValue?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val currentHex = (value as? FormValue.ColorValue)?.hex ?: field.defaultValue

  val colors = field.presets ?: defaultColorPresets

  Column {
    Text(
      text = field.label + if (field.required) " *" else "",
      style = MaterialTheme.typography.labelMedium,
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(top = 4.dp),
    ) {
      colors.forEach { hex ->
        val color = parseHexColor(hex)
        val isSelected = hex.equals(currentHex, ignoreCase = true)
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .then(
              if (isSelected) {
                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
              } else {
                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
              },
            )
            .clickable(enabled = enabled) {
              onValueChanged(FormValue.ColorValue(hex))
            },
        )
      }
    }

    currentHex?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )
    }

    field.helpText?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
      )
    }
  }
}

private fun parseHexColor(hex: String): Color {
  val cleaned = hex.removePrefix("#")
  return try {
    val rgb = cleaned.toLong(16)
    Color(0xFF000000 or rgb)
  } catch (_: Throwable) {
    Color.Gray
  }
}

private val defaultColorPresets = listOf(
  "#F44336", "#E91E63", "#9C27B0", "#673AB7",
  "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
  "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
  "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
)
