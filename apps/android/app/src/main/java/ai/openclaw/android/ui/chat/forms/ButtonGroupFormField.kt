package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonGroupFormField(
  field: FormField.ButtonGroup,
  value: FormValue?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  Column {
    if (field.label.isNotBlank()) {
      Text(
        text = field.label,
        style = MaterialTheme.typography.labelMedium,
      )
    }

    val arrangement = Arrangement.spacedBy(8.dp)

    when (field.style) {
      ButtonGroupStyle.ROW -> {
        FlowRow(
          horizontalArrangement = arrangement,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          field.buttons.forEach { button ->
            FormButtonComposable(
              button = button,
              enabled = enabled,
              onClick = { onValueChanged(FormValue.ButtonValue(button.id)) },
            )
          }
        }
      }
      ButtonGroupStyle.COLUMN -> {
        Column(verticalArrangement = arrangement) {
          field.buttons.forEach { button ->
            FormButtonComposable(
              button = button,
              enabled = enabled,
              onClick = { onValueChanged(FormValue.ButtonValue(button.id)) },
            )
          }
        }
      }
      ButtonGroupStyle.WRAP -> {
        FlowRow(
          horizontalArrangement = arrangement,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          field.buttons.forEach { button ->
            FormButtonComposable(
              button = button,
              enabled = enabled,
              onClick = { onValueChanged(FormValue.ButtonValue(button.id)) },
            )
          }
        }
      }
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

@Composable
private fun FormButtonComposable(
  button: FormButton,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val label = buildString {
    button.icon?.let { append(it); append(" ") }
    append(button.label)
  }

  val destructiveColors = if (button.destructive) {
    ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.error,
      contentColor = MaterialTheme.colorScheme.onError,
    )
  } else {
    null
  }

  when (button.style) {
    FormButtonStyle.FILLED -> {
      Button(
        onClick = onClick,
        enabled = enabled,
        colors = destructiveColors ?: ButtonDefaults.buttonColors(),
      ) {
        Text(label)
      }
    }
    FormButtonStyle.OUTLINED -> {
      OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(
          label,
          color = if (button.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
      }
    }
    FormButtonStyle.TEXT -> {
      TextButton(onClick = onClick, enabled = enabled) {
        Text(
          label,
          color = if (button.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
      }
    }
    FormButtonStyle.TONAL -> {
      FilledTonalButton(onClick = onClick, enabled = enabled) {
        Text(label)
      }
    }
  }
}
