package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerFormField(
  field: FormField.TimePicker,
  value: FormValue?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  val currentTime = (value as? FormValue.TimeValue)?.time ?: field.defaultValue
  var showDialog by remember { mutableStateOf(false) }

  val displayText = currentTime ?: "Select time"

  // Parse initial hour/minute.
  val (initialHour, initialMinute) = remember(currentTime) {
    if (currentTime != null) {
      val parts = currentTime.split(":")
      val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
      val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
      h to m
    } else {
      12 to 0
    }
  }

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
    val timePickerState = rememberTimePickerState(
      initialHour = initialHour,
      initialMinute = initialMinute,
      is24Hour = field.is24Hour,
    )

    AlertDialog(
      onDismissRequest = { showDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            val h = timePickerState.hour
            val m = timePickerState.minute
            onValueChanged(FormValue.TimeValue("%02d:%02d".format(h, m)))
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
      title = { Text(field.label) },
      text = {
        TimePicker(state = timePickerState)
      },
    )
  }
}
