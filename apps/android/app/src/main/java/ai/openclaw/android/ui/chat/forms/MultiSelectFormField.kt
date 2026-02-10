package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectFormField(
  field: FormField.MultiSelect,
  value: FormValue?,
  error: String?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val selected = (value as? FormValue.ListValue)?.values ?: field.defaultValues

  Column {
    Text(
      text = field.label + if (field.required) " *" else "",
      style = MaterialTheme.typography.labelMedium,
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      field.options.forEach { option ->
        val isSelected = option.id in selected
        FilterChip(
          selected = isSelected,
          onClick = {
            val updated = if (isSelected) {
              selected - option.id
            } else {
              val maxOk = field.maxSelections == null || selected.size < field.maxSelections
              if (maxOk) selected + option.id else selected
            }
            onValueChanged(FormValue.ListValue(updated))
          },
          label = { Text(option.label) },
          enabled = enabled && !option.disabled,
          leadingIcon = option.icon?.let { emoji -> { Text(emoji) } },
        )
      }
    }

    // Error
    error?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
      )
    }

    // Help text
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
