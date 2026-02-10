package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun NumberFormField(
  field: FormField.Number,
  value: FormValue?,
  error: String?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val current = (value as? FormValue.NumberValue)?.value ?: field.defaultValue

  val textValue = current?.let { n ->
    if (field.decimalPlaces == 0 && n == n.toLong().toDouble()) {
      n.toLong().toString()
    } else {
      "%.${field.decimalPlaces}f".format(n)
    }
  } ?: ""

  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = textValue,
        onValueChange = { raw ->
          val parsed = raw.toDoubleOrNull()
          if (parsed != null) {
            onValueChanged(FormValue.NumberValue(parsed))
          } else if (raw.isEmpty()) {
            // Allow clearing the field.
            onValueChanged(FormValue.NumberValue(field.defaultValue ?: 0.0))
          }
        },
        label = {
          Text(field.label + if (field.required) " *" else "")
        },
        modifier = Modifier.weight(1f),
        enabled = enabled,
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = when {
          error != null -> { { Text(error, color = MaterialTheme.colorScheme.error) } }
          field.helpText != null -> { { Text(field.helpText) } }
          else -> null
        },
      )

      field.unit?.let { unit ->
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = unit,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
