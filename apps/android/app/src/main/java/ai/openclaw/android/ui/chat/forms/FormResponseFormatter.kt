package ai.openclaw.android.ui.chat.forms

/**
 * Formats a [FormResponse] into a human-readable message and/or structured JSON
 * for sending back to the agent via the existing chat mechanism.
 */
object FormResponseFormatter {

  /**
   * Build a human-readable chat message from the form response.
   * This is what the agent sees as the user message text.
   */
  fun formatMessage(response: FormResponse): String {
    val actionLabel = when (response.action) {
      FormAction.SUBMIT -> "submitted"
      FormAction.CANCEL -> "cancelled"
      FormAction.EXPIRE -> "expired"
      FormAction.BUTTON_TAP -> "button_tap"
    }

    return buildString {
      append("[FORM:${response.formId}:$actionLabel]")
      if (response.action == FormAction.SUBMIT && response.values.isNotEmpty()) {
        append("\n")
        response.values.forEach { (key, value) ->
          append("  $key: ${formatValue(value)}\n")
        }
      }
    }.trimEnd()
  }

  fun formatValue(value: FormValue): String {
    return when (value) {
      is FormValue.StringValue -> value.value
      is FormValue.NumberValue -> {
        if (value.value == value.value.toLong().toDouble()) {
          value.value.toLong().toString()
        } else {
          value.value.toString()
        }
      }
      is FormValue.BooleanValue -> if (value.value) "yes" else "no"
      is FormValue.ListValue -> value.values.joinToString(", ")
      is FormValue.DateValue -> value.isoDate
      is FormValue.TimeValue -> value.time
      is FormValue.ColorValue -> value.hex
      is FormValue.ButtonValue -> value.buttonId
      is FormValue.RatingValue -> "${"★".repeat(value.stars)}${"☆".repeat(5 - value.stars)}"
    }
  }
}
