package ai.openclaw.android.ui.chat.forms

/**
 * Validates field values before form submission.
 * Returns a map of field-id → error-message for any invalid fields.
 */
object FormValidator {

  fun validate(form: InlineForm, values: Map<String, FormValue>): Map<String, String> {
    val errors = mutableMapOf<String, String>()
    for (field in form.fields) {
      val value = values[field.id]
      val error = validateField(field, value)
      if (error != null) errors[field.id] = error
    }
    return errors
  }

  private fun validateField(field: FormField, value: FormValue?): String? {
    // Non-input fields never produce errors.
    if (field is FormField.Separator || field is FormField.StaticText) return null

    // Required check.
    if (field.required && isEmptyValue(value)) {
      return "Required"
    }

    // Field-specific validation.
    return when (field) {
      is FormField.Text -> validateText(field, value)
      is FormField.Number -> validateNumber(field, value)
      is FormField.MultiSelect -> validateMultiSelect(field, value)
      else -> null
    }
  }

  private fun isEmptyValue(value: FormValue?): Boolean {
    return when (value) {
      null -> true
      is FormValue.StringValue -> value.value.isBlank()
      is FormValue.ListValue -> value.values.isEmpty()
      else -> false
    }
  }

  private fun validateText(field: FormField.Text, value: FormValue?): String? {
    val text = (value as? FormValue.StringValue)?.value ?: return null
    if (text.isBlank()) return null // Empty handled by required check.

    field.maxLength?.let { max ->
      if (text.length > max) return "Max $max characters"
    }

    field.validation?.let { validation ->
      validation.pattern?.let { pattern ->
        try {
          if (!Regex(pattern).matches(text)) {
            return validation.errorMessage ?: "Invalid format"
          }
        } catch (_: Throwable) {
          // Bad regex from the agent; skip validation.
        }
      }
    }

    return null
  }

  private fun validateNumber(field: FormField.Number, value: FormValue?): String? {
    val num = (value as? FormValue.NumberValue)?.value ?: return null
    field.min?.let { min -> if (num < min) return "Min value is $min" }
    field.max?.let { max -> if (num > max) return "Max value is $max" }
    return null
  }

  private fun validateMultiSelect(field: FormField.MultiSelect, value: FormValue?): String? {
    val list = (value as? FormValue.ListValue)?.values ?: emptyList()
    if (field.minSelections > 0 && list.size < field.minSelections) {
      return "Select at least ${field.minSelections}"
    }
    field.maxSelections?.let { max ->
      if (list.size > max) return "Select at most $max"
    }
    return null
  }
}
