package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun TextFormField(
  field: FormField.Text,
  value: FormValue?,
  error: String?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val current = (value as? FormValue.StringValue)?.value ?: field.defaultValue.orEmpty()

  Column {
    OutlinedTextField(
      value = current,
      onValueChange = { onValueChanged(FormValue.StringValue(it)) },
      label = {
        Text(field.label + if (field.required) " *" else "")
      },
      placeholder = field.placeholder?.let { { Text(it) } },
      modifier = Modifier.fillMaxWidth(),
      enabled = enabled,
      isError = error != null,
      singleLine = !field.multiline,
      maxLines = if (field.multiline) field.maxLines.coerceAtLeast(2) else 1,
      keyboardOptions = keyboardOptionsFor(field.inputType),
      visualTransformation = if (field.inputType == TextInputType.PASSWORD) {
        PasswordVisualTransformation()
      } else {
        VisualTransformation.None
      },
      supportingText = when {
        error != null -> { { Text(error, color = MaterialTheme.colorScheme.error) } }
        field.helpText != null -> { { Text(field.helpText) } }
        else -> null
      },
    )
  }
}

private fun keyboardOptionsFor(inputType: TextInputType): KeyboardOptions {
  return when (inputType) {
    TextInputType.TEXT -> KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Done,
    )
    TextInputType.EMAIL -> KeyboardOptions(
      keyboardType = KeyboardType.Email,
      imeAction = ImeAction.Done,
    )
    TextInputType.PHONE -> KeyboardOptions(
      keyboardType = KeyboardType.Phone,
      imeAction = ImeAction.Done,
    )
    TextInputType.URL -> KeyboardOptions(
      keyboardType = KeyboardType.Uri,
      imeAction = ImeAction.Done,
    )
    TextInputType.PASSWORD -> KeyboardOptions(
      keyboardType = KeyboardType.Password,
      imeAction = ImeAction.Done,
    )
    TextInputType.NUMBER -> KeyboardOptions(
      keyboardType = KeyboardType.Number,
      imeAction = ImeAction.Done,
    )
  }
}
