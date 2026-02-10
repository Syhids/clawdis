package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SliderFormField(
  field: FormField.Slider,
  value: FormValue?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val currentValue = (value as? FormValue.NumberValue)?.value ?: field.defaultValue

  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = field.label,
        style = MaterialTheme.typography.labelMedium,
      )
      if (field.showValue) {
        val displayValue = if (field.step >= 1.0) {
          "${currentValue.toInt()}${field.unit.orEmpty()}"
        } else {
          "${"%.1f".format(currentValue)}${field.unit.orEmpty()}"
        }
        Text(
          text = displayValue,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    Slider(
      value = currentValue.toFloat(),
      onValueChange = { onValueChanged(FormValue.NumberValue(it.toDouble())) },
      valueRange = field.min.toFloat()..field.max.toFloat(),
      steps = if (field.step > 0 && field.max > field.min) {
        (((field.max - field.min) / field.step).toInt() - 1).coerceAtLeast(0)
      } else {
        0
      },
      enabled = enabled,
    )

    field.helpText?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
