package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerFormField(
  field: FormField.DatePicker,
  value: FormValue?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val currentIso = (value as? FormValue.DateValue)?.isoDate ?: field.defaultValue
  var showDialog by remember { mutableStateOf(false) }

  val displayText = currentIso ?: "Select date"

  Column {
    Text(
      text = field.label + if (field.required) " *" else "",
      style = MaterialTheme.typography.labelMedium,
    )
    Button(
      onClick = { showDialog = true },
      enabled = enabled,
    ) {
      Text(displayText)
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

  if (showDialog) {
    val initialMs = currentIso?.let { parseIsoDate(it) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
    DatePickerDialog(
      onDismissRequest = { showDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { ms ->
              onValueChanged(FormValue.DateValue(formatIsoDate(ms)))
            }
            showDialog = false
          },
        ) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDialog = false }) {
          Text("Cancel")
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

private fun parseIsoDate(iso: String): Long? {
  return try {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    fmt.parse(iso)?.time
  } catch (_: Throwable) {
    null
  }
}

private fun formatIsoDate(ms: Long): String {
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  fmt.timeZone = TimeZone.getTimeZone("UTC")
  return fmt.format(Date(ms))
}
