package ai.openclaw.android.ui.chat.forms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An inline form that the agent can embed within a chat message.
 * Rendered as a native Material 3 card inside the conversation.
 */
@Serializable
data class InlineForm(
  val formId: String,
  val title: String? = null,
  val description: String? = null,
  val fields: List<FormField>,
  val submitLabel: String = "Submit",
  val cancelLabel: String? = "Cancel",
  val layout: FormLayout = FormLayout.VERTICAL,
  val autoSubmit: Boolean = false,
  val persistent: Boolean = false,
  val expiresAtMs: Long? = null,
  val metadata: Map<String, String> = emptyMap(),
)

@Serializable
enum class FormLayout {
  @SerialName("vertical") VERTICAL,
  @SerialName("compact") COMPACT,
  @SerialName("inline") INLINE,
}

@Serializable
sealed class FormField {
  abstract val id: String
  abstract val label: String
  abstract val required: Boolean
  abstract val helpText: String?

  @Serializable
  @SerialName("text")
  data class Text(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val placeholder: String? = null,
    val defaultValue: String? = null,
    val multiline: Boolean = false,
    val maxLines: Int = 1,
    val maxLength: Int? = null,
    val inputType: TextInputType = TextInputType.TEXT,
    val validation: TextValidation? = null,
  ) : FormField()

  @Serializable
  @SerialName("number")
  data class Number(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val defaultValue: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val decimalPlaces: Int = 0,
    val unit: String? = null,
  ) : FormField()

  @Serializable
  @SerialName("slider")
  data class Slider(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val defaultValue: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val unit: String? = null,
    val showValue: Boolean = true,
  ) : FormField()

  @Serializable
  @SerialName("toggle")
  data class Toggle(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val defaultValue: Boolean = false,
  ) : FormField()

  @Serializable
  @SerialName("select")
  data class Select(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val options: List<SelectOption>,
    val defaultValue: String? = null,
    val style: SelectStyle = SelectStyle.DROPDOWN,
  ) : FormField()

  @Serializable
  @SerialName("multiselect")
  data class MultiSelect(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val options: List<SelectOption>,
    val defaultValues: List<String> = emptyList(),
    val minSelections: Int = 0,
    val maxSelections: Int? = null,
  ) : FormField()

  @Serializable
  @SerialName("date")
  data class DatePicker(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val defaultValue: String? = null,
    val minDate: String? = null,
    val maxDate: String? = null,
    val includeTime: Boolean = false,
  ) : FormField()

  @Serializable
  @SerialName("time")
  data class TimePicker(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val defaultValue: String? = null,
    val is24Hour: Boolean = true,
    val minuteInterval: Int = 1,
  ) : FormField()

  @Serializable
  @SerialName("color")
  data class ColorPicker(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val defaultValue: String? = null,
    val presets: List<String>? = null,
  ) : FormField()

  @Serializable
  @SerialName("buttons")
  data class ButtonGroup(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val buttons: List<FormButton>,
    val style: ButtonGroupStyle = ButtonGroupStyle.ROW,
  ) : FormField()

  @Serializable
  @SerialName("rating")
  data class Rating(
    override val id: String,
    override val label: String,
    override val required: Boolean = false,
    override val helpText: String? = null,
    val maxStars: Int = 5,
    val defaultValue: Int = 0,
    val allowHalf: Boolean = false,
  ) : FormField()

  @Serializable
  @SerialName("separator")
  data class Separator(
    override val id: String = "",
    override val label: String = "",
    override val required: Boolean = false,
    override val helpText: String? = null,
    val title: String? = null,
  ) : FormField()

  @Serializable
  @SerialName("static")
  data class StaticText(
    override val id: String = "",
    override val label: String = "",
    override val required: Boolean = false,
    override val helpText: String? = null,
    val text: String,
    val style: StaticTextStyle = StaticTextStyle.INFO,
  ) : FormField()
}

@Serializable
data class SelectOption(
  val id: String,
  val label: String,
  val description: String? = null,
  val icon: String? = null,
  val disabled: Boolean = false,
)

@Serializable
enum class TextInputType {
  @SerialName("text") TEXT,
  @SerialName("email") EMAIL,
  @SerialName("phone") PHONE,
  @SerialName("url") URL,
  @SerialName("password") PASSWORD,
  @SerialName("number") NUMBER,
}

@Serializable
data class TextValidation(
  val pattern: String? = null,
  val errorMessage: String? = null,
)

@Serializable
enum class SelectStyle {
  @SerialName("dropdown") DROPDOWN,
  @SerialName("radio") RADIO,
  @SerialName("chips") CHIPS,
  @SerialName("segmented") SEGMENTED,
}

@Serializable
enum class ButtonGroupStyle {
  @SerialName("row") ROW,
  @SerialName("column") COLUMN,
  @SerialName("wrap") WRAP,
}

@Serializable
enum class StaticTextStyle {
  @SerialName("info") INFO,
  @SerialName("warning") WARNING,
  @SerialName("error") ERROR,
  @SerialName("success") SUCCESS,
}

@Serializable
data class FormButton(
  val id: String,
  val label: String,
  val icon: String? = null,
  val style: FormButtonStyle = FormButtonStyle.OUTLINED,
  val destructive: Boolean = false,
)

@Serializable
enum class FormButtonStyle {
  @SerialName("filled") FILLED,
  @SerialName("outlined") OUTLINED,
  @SerialName("text") TEXT,
  @SerialName("tonal") TONAL,
}

// ---------- Response ----------

@Serializable
data class FormResponse(
  val formId: String,
  val values: Map<String, FormValue>,
  val action: FormAction,
  val submittedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
sealed class FormValue {
  @Serializable @SerialName("string")
  data class StringValue(val value: String) : FormValue()

  @Serializable @SerialName("number")
  data class NumberValue(val value: Double) : FormValue()

  @Serializable @SerialName("boolean")
  data class BooleanValue(val value: Boolean) : FormValue()

  @Serializable @SerialName("list")
  data class ListValue(val values: List<String>) : FormValue()

  @Serializable @SerialName("date")
  data class DateValue(val isoDate: String) : FormValue()

  @Serializable @SerialName("time")
  data class TimeValue(val time: String) : FormValue()

  @Serializable @SerialName("color")
  data class ColorValue(val hex: String) : FormValue()

  @Serializable @SerialName("button")
  data class ButtonValue(val buttonId: String) : FormValue()

  @Serializable @SerialName("rating")
  data class RatingValue(val stars: Int) : FormValue()
}

@Serializable
enum class FormAction {
  @SerialName("submit") SUBMIT,
  @SerialName("cancel") CANCEL,
  @SerialName("expire") EXPIRE,
  @SerialName("button_tap") BUTTON_TAP,
}
