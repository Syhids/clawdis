package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SelectFormField(
  field: FormField.Select,
  value: FormValue?,
  error: String?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val selectedId = (value as? FormValue.StringValue)?.value

  Column {
    when (field.style) {
      SelectStyle.SEGMENTED -> {
        Text(
          text = field.label + if (field.required) " *" else "",
          style = MaterialTheme.typography.labelMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          field.options.forEachIndexed { index, option ->
            SegmentedButton(
              selected = selectedId == option.id,
              onClick = { onValueChanged(FormValue.StringValue(option.id)) },
              shape = SegmentedButtonDefaults.itemShape(
                index = index,
                count = field.options.size,
              ),
              enabled = enabled && !option.disabled,
            ) {
              option.icon?.let { emoji ->
                Text(emoji, modifier = Modifier.padding(end = 4.dp))
              }
              Text(option.label)
            }
          }
        }
      }

      SelectStyle.CHIPS -> {
        Text(
          text = field.label + if (field.required) " *" else "",
          style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          field.options.forEach { option ->
            FilterChip(
              selected = selectedId == option.id,
              onClick = { onValueChanged(FormValue.StringValue(option.id)) },
              label = { Text(option.label) },
              enabled = enabled && !option.disabled,
              leadingIcon = option.icon?.let { emoji -> { Text(emoji) } },
            )
          }
        }
      }

      SelectStyle.RADIO -> {
        Text(
          text = field.label + if (field.required) " *" else "",
          style = MaterialTheme.typography.labelMedium,
        )
        field.options.forEach { option ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clickable(enabled = enabled && !option.disabled) {
                onValueChanged(FormValue.StringValue(option.id))
              }
              .padding(vertical = 2.dp),
          ) {
            RadioButton(
              selected = selectedId == option.id,
              onClick = { onValueChanged(FormValue.StringValue(option.id)) },
              enabled = enabled && !option.disabled,
            )
            Column {
              Text(option.label, style = MaterialTheme.typography.bodyMedium)
              option.description?.let {
                Text(
                  it,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      SelectStyle.DROPDOWN -> {
        var expanded by remember { mutableStateOf(false) }
        val selectedOption = field.options.find { it.id == selectedId }
        ExposedDropdownMenuBox(
          expanded = expanded,
          onExpandedChange = { if (enabled) expanded = it },
        ) {
          OutlinedTextField(
            value = selectedOption?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(field.label + if (field.required) " *" else "") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
            isError = error != null,
          )
          ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
          ) {
            field.options.forEach { option ->
              DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                  onValueChanged(FormValue.StringValue(option.id))
                  expanded = false
                },
                enabled = !option.disabled,
              )
            }
          }
        }
      }
    }

    // Error message
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
