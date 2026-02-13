package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Renders an inline form as a Material 3 Card embedded in the chat.
 */
@Composable
fun InlineFormCard(
  form: InlineForm,
  onSubmit: (FormResponse) -> Unit,
  onCancel: (() -> Unit)?,
  isDisabled: Boolean = false,
) {
  val fieldStates: SnapshotStateMap<String, FormValue> = remember(form.formId) {
    buildList {
      form.fields.forEach { field ->
        defaultValueFor(field)?.let { add(field.id to it) }
      }
    }.toMutableStateMap()
  }

  val validationErrors: SnapshotStateMap<String, String> = remember(form.formId) {
    listOf<Pair<String, String>>().toMutableStateMap()
  }

  // Expiration.
  var isExpired by remember(form.formId) { mutableStateOf(false) }
  LaunchedEffect(form.expiresAtMs) {
    val expiresAt = form.expiresAtMs ?: return@LaunchedEffect
    val now = System.currentTimeMillis()
    if (now >= expiresAt) {
      isExpired = true
      return@LaunchedEffect
    }
    kotlinx.coroutines.delay(expiresAt - now)
    isExpired = true
    onSubmit(
      FormResponse(
        formId = form.formId,
        values = emptyMap(),
        action = FormAction.EXPIRE,
      ),
    )
  }

  val effectiveDisabled = isDisabled || isExpired

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .alpha(if (effectiveDisabled) 0.6f else 1f),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Title
      form.title?.let { title ->
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }

      // Description
      form.description?.let { desc ->
        Text(
          text = desc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Fields
      val layout = form.layout
      if (layout == FormLayout.INLINE && form.fields.size <= 2) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          form.fields.forEach { field ->
            Box(modifier = Modifier.weight(1f)) {
              FormFieldComposable(
                field = field,
                value = fieldStates[field.id],
                error = validationErrors[field.id],
                onValueChanged = { value ->
                  fieldStates[field.id] = value
                  validationErrors.remove(field.id)
                  if (form.autoSubmit && value is FormValue.ButtonValue) {
                    doSubmit(form, fieldStates, onSubmit)
                  }
                },
                enabled = !effectiveDisabled,
              )
            }
          }
        }
      } else {
        form.fields.forEach { field ->
          FormFieldComposable(
            field = field,
            value = fieldStates[field.id],
            error = validationErrors[field.id],
            onValueChanged = { value ->
              fieldStates[field.id] = value
              validationErrors.remove(field.id)
              if (form.autoSubmit && value is FormValue.ButtonValue) {
                doSubmit(form, fieldStates, onSubmit)
              }
            },
            enabled = !effectiveDisabled,
          )
        }
      }

      // Action buttons
      if (!effectiveDisabled) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (form.cancelLabel != null && onCancel != null) {
            TextButton(onClick = { onCancel() }) {
              Text(form.cancelLabel)
            }
            Spacer(modifier = Modifier.width(8.dp))
          }
          Button(
            onClick = {
              val errors = FormValidator.validate(form, fieldStates)
              if (errors.isEmpty()) {
                doSubmit(form, fieldStates, onSubmit)
              } else {
                validationErrors.clear()
                validationErrors.putAll(errors)
              }
            },
          ) {
            Text(form.submitLabel)
          }
        }
      } else {
        // Submitted / expired indicator
        val statusText = if (isExpired) "⏰ Expired" else "✓ Submitted"
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelSmall,
          color = if (isExpired) {
            MaterialTheme.colorScheme.onSurfaceVariant
          } else {
            MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.align(Alignment.End),
        )
      }
    }
  }
}

@Composable
fun FormFieldComposable(
  field: FormField,
  value: FormValue?,
  error: String?,
  onValueChanged: (FormValue) -> Unit,
  enabled: Boolean,
) {
  when (field) {
    is FormField.Text -> TextFormField(field, value, error, onValueChanged, enabled)
    is FormField.Number -> NumberFormField(field, value, error, onValueChanged, enabled)
    is FormField.Slider -> SliderFormField(field, value, onValueChanged, enabled)
    is FormField.Toggle -> ToggleFormField(field, value, onValueChanged, enabled)
    is FormField.Select -> SelectFormField(field, value, error, onValueChanged, enabled)
    is FormField.MultiSelect -> MultiSelectFormField(field, value, error, onValueChanged, enabled)
    is FormField.DatePicker -> DatePickerFormField(field, value, onValueChanged, enabled)
    is FormField.TimePicker -> TimePickerFormField(field, value, onValueChanged, enabled)
    is FormField.ColorPicker -> ColorPickerFormField(field, value, onValueChanged, enabled)
    is FormField.ButtonGroup -> ButtonGroupFormField(field, value, onValueChanged, enabled)
    is FormField.Rating -> RatingFormField(field, value, onValueChanged, enabled)
    is FormField.Separator -> SeparatorFormField(field)
    is FormField.StaticText -> StaticTextFormField(field)
  }
}

private fun doSubmit(
  form: InlineForm,
  values: Map<String, FormValue>,
  onSubmit: (FormResponse) -> Unit,
) {
  onSubmit(
    FormResponse(
      formId = form.formId,
      values = values,
      action = FormAction.SUBMIT,
    ),
  )
}

private fun defaultValueFor(field: FormField): FormValue? {
  return when (field) {
    is FormField.Text -> field.defaultValue?.let { FormValue.StringValue(it) }
    is FormField.Number -> field.defaultValue?.let { FormValue.NumberValue(it) }
    is FormField.Slider -> FormValue.NumberValue(field.defaultValue)
    is FormField.Toggle -> FormValue.BooleanValue(field.defaultValue)
    is FormField.Select -> field.defaultValue?.let { FormValue.StringValue(it) }
    is FormField.MultiSelect ->
      if (field.defaultValues.isNotEmpty()) FormValue.ListValue(field.defaultValues)
      else null
    is FormField.DatePicker -> field.defaultValue?.let { FormValue.DateValue(it) }
    is FormField.TimePicker -> field.defaultValue?.let { FormValue.TimeValue(it) }
    is FormField.ColorPicker -> field.defaultValue?.let { FormValue.ColorValue(it) }
    is FormField.Rating ->
      if (field.defaultValue > 0) FormValue.RatingValue(field.defaultValue) else null
    is FormField.ButtonGroup -> null
    is FormField.Separator -> null
    is FormField.StaticText -> null
  }
}
