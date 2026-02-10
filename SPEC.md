### [2026-02-10] Sistema de Formularios Nativos Inline y Plantillas de Interacción Estructurada Generados por el Agente (Agent-Generated Inline Forms & Structured Interaction Engine)
- **Estado:** propuesta
- **Plataforma:** Android
- **Estimación:** >4h
- **PR:** (pendiente)

Implementar un sistema que permita al agente OpenClaw definir formularios y elementos interactivos nativos que se renderizan inline dentro de los mensajes del chat, permitiendo al usuario introducir datos estructurados (texto, selecciones, fechas, toggles, sliders, etc.) y enviarlos de vuelta al agente como respuesta tipada, eliminando la necesidad de escribir texto libre para interacciones que requieren datos concretos.

**Problema que resuelve:**

Actualmente, toda la interacción entre el usuario y el agente se realiza a través de texto libre y adjuntos. Esto genera fricciones significativas en múltiples escenarios cotidianos:

1. **Recopilación de datos multi-campo:** Cuando el agente necesita varios datos del usuario (ej: "¿a qué hora quieres la alarma, qué días, y con qué tono?"), el usuario tiene que escribir todo en texto libre, lo cual es ambiguo, propenso a malinterpretaciones, y requiere múltiples turnos de clarificación.

2. **Selección entre opciones:** Cuando el agente presenta opciones ("¿prefieres A, B o C?"), el usuario debe escribir la opción. Con muchas opciones o nombres largos, esto es tedioso y puede resultar en errores de escritura. Los inline buttons de Telegram cubren parcialmente esto, pero la app Android no tiene un equivalente nativo.

3. **Datos con formato específico:** Fechas, horas, rangos numéricos, colores — el usuario debe escribirlos como texto y el agente debe parsearlos, con riesgo de malentendidos (ej: "3/4" ¿es 3 de abril o 4 de marzo?).

4. **Configuración de parámetros:** Ajustar settings del agente, configurar automatizaciones, o parametrizar tareas requiere múltiples mensajes de ida y vuelta cuando un formulario nativo permitiría hacerlo en un solo paso.

5. **Confirmaciones estructuradas:** "¿Confirmas que quieres enviar este email a X con asunto Y?" — actualmente requiere que el usuario lea todo y escriba "sí", cuando podría haber un preview con botón de confirmar/editar/cancelar.

6. **Workflows multi-paso:** Procesos como "crear evento de calendario" requieren nombre, fecha, hora, duración, invitados, descripción — actualmente son 6+ mensajes de ida y vuelta, cuando un formulario unificado lo resolvería en 1 interacción.

7. **Sin paridad con canales ricos:** Telegram, Slack y Discord soportan botones inline, pero la app nativa Android renderiza los mensajes como texto plano sin interactividad más allá de links y código.

Para un power user como Manuel que interactúa constantemente con su agente para domótica, calendario, configuración de sistemas, y gestión de tareas, la capacidad de tener formularios nativos reduciría drásticamente la fricción en interacciones cotidianas.

**Diferencia con el sistema A2UI (Canvas):**

- **A2UI/Canvas:** Es un sistema de renderizado de UI completa en una vista web separada, pensado para aplicaciones complejas con estado, navegación, y múltiples superficies. Requiere que el agente construya una aplicación web completa.
- **Inline Forms:** Son elementos ligeros embebidos directamente en la conversación del chat, renderizados con componentes nativos de Android (Material 3). No requieren un canvas separado, son efímeros (vinculados a un mensaje), y están diseñados para interacciones rápidas de ida y vuelta.

Son complementarios: A2UI para apps complejas, Inline Forms para interacciones rápidas en el flujo de la conversación.

**Funcionalidades propuestas:**

**1. Modelo de datos de formularios:**

```kotlin
@Serializable
data class InlineForm(
    val formId: String,                           // ID único del formulario
    val title: String? = null,                    // Título opcional
    val description: String? = null,              // Descripción/instrucciones
    val fields: List<FormField>,                  // Lista de campos
    val submitLabel: String = "Submit",           // Texto del botón de envío
    val cancelLabel: String? = "Cancel",          // Texto del botón cancelar (null = sin cancelar)
    val layout: FormLayout = FormLayout.VERTICAL, // Disposición de campos
    val autoSubmit: Boolean = false,              // Auto-enviar cuando hay un solo campo (ej: sí/no)
    val persistent: Boolean = false,              // Si el formulario persiste tras enviar o se deshabilita
    val expiresAtMs: Long? = null,                // Expiración opcional
    val metadata: Map<String, String> = emptyMap(), // Metadatos arbitrarios para el agente
)

enum class FormLayout {
    VERTICAL,       // Campos uno debajo de otro (default)
    COMPACT,        // Campos más compactos, agrupados horizontalmente cuando caben
    INLINE,         // Todo en una línea (para formularios de 1-2 campos)
}

@Serializable
sealed class FormField {
    abstract val id: String
    abstract val label: String
    abstract val required: Boolean
    abstract val helpText: String?
    
    @Serializable
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
        val unit: String? = null,           // Ej: "°C", "%", "kg"
    ) : FormField()
    
    @Serializable
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
        val showValue: Boolean = true,      // Mostrar valor actual junto al slider
    ) : FormField()
    
    @Serializable
    data class Toggle(
        override val id: String,
        override val label: String,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val defaultValue: Boolean = false,
    ) : FormField()
    
    @Serializable
    data class Select(
        override val id: String,
        override val label: String,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val options: List<SelectOption>,
        val defaultValue: String? = null,    // ID de la opción por defecto
        val style: SelectStyle = SelectStyle.DROPDOWN,
    ) : FormField()
    
    @Serializable
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
    data class DatePicker(
        override val id: String,
        override val label: String,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val defaultValue: String? = null,     // ISO-8601 date
        val minDate: String? = null,
        val maxDate: String? = null,
        val includeTime: Boolean = false,
    ) : FormField()
    
    @Serializable
    data class TimePicker(
        override val id: String,
        override val label: String,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val defaultValue: String? = null,     // HH:mm
        val is24Hour: Boolean = true,
        val minuteInterval: Int = 1,          // 1, 5, 10, 15, 30
    ) : FormField()
    
    @Serializable
    data class ColorPicker(
        override val id: String,
        override val label: String,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val defaultValue: String? = null,     // #RRGGBB
        val presets: List<String>? = null,    // Colores predefinidos
    ) : FormField()
    
    @Serializable
    data class ButtonGroup(
        override val id: String,
        override val label: String,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val buttons: List<FormButton>,
        val style: ButtonGroupStyle = ButtonGroupStyle.ROW,
    ) : FormField()
    
    @Serializable
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
    data class Separator(
        override val id: String = "",
        override val label: String = "",
        override val required: Boolean = false,
        override val helpText: String? = null,
        val title: String? = null,            // Título de sección
    ) : FormField()
    
    @Serializable
    data class StaticText(
        override val id: String = "",
        override val label: String = "",
        override val required: Boolean = false,
        override val helpText: String? = null,
        val text: String,                     // Texto informativo (soporta Markdown básico)
        val style: StaticTextStyle = StaticTextStyle.INFO,
    ) : FormField()
}

@Serializable
data class SelectOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null,                  // Emoji o nombre de icono Material
    val disabled: Boolean = false,
)

enum class TextInputType { TEXT, EMAIL, PHONE, URL, PASSWORD, NUMBER }

@Serializable
data class TextValidation(
    val pattern: String? = null,              // Regex
    val errorMessage: String? = null,
)

enum class SelectStyle {
    DROPDOWN,       // Menú desplegable
    RADIO,          // Radio buttons
    CHIPS,          // Filter chips de Material 3
    SEGMENTED,      // Segmented button (2-5 opciones)
}

enum class ButtonGroupStyle { ROW, COLUMN, WRAP }

enum class StaticTextStyle { INFO, WARNING, ERROR, SUCCESS }

@Serializable
data class FormButton(
    val id: String,
    val label: String,
    val icon: String? = null,
    val style: FormButtonStyle = FormButtonStyle.OUTLINED,
    val destructive: Boolean = false,
)

enum class FormButtonStyle { FILLED, OUTLINED, TEXT, TONAL }

// Respuesta del formulario
@Serializable
data class FormResponse(
    val formId: String,
    val values: Map<String, FormValue>,
    val action: FormAction,
    val submittedAtMs: Long = System.currentTimeMillis(),
)

@Serializable
sealed class FormValue {
    @Serializable
    data class StringValue(val value: String) : FormValue()
    @Serializable
    data class NumberValue(val value: Double) : FormValue()
    @Serializable
    data class BooleanValue(val value: Boolean) : FormValue()
    @Serializable
    data class ListValue(val values: List<String>) : FormValue()
    @Serializable
    data class DateValue(val isoDate: String) : FormValue()
    @Serializable
    data class TimeValue(val time: String) : FormValue()     // HH:mm
    @Serializable
    data class ColorValue(val hex: String) : FormValue()     // #RRGGBB
    @Serializable
    data class ButtonValue(val buttonId: String) : FormValue()
    @Serializable
    data class RatingValue(val stars: Int) : FormValue()
}

enum class FormAction { SUBMIT, CANCEL, EXPIRE, BUTTON_TAP }
```

**2. Protocolo de comunicación (Gateway ↔ App):**

El agente generaría formularios a través de un nuevo content type en los mensajes del chat:

```kotlin
// Nuevo tipo de contenido para mensajes que contienen formularios
// Se añade como un ChatMessageContent type = "inline_form"
// El contenido del formulario se envía como JSON en el campo `text` del content

// Parsing en ChatController:
private fun parseMessageContent(el: JsonElement): ChatMessageContent? {
    val obj = el.asObjectOrNull() ?: return null
    val type = obj["type"].asStringOrNull() ?: "text"
    return when (type) {
        "text" -> ChatMessageContent(type = "text", text = obj["text"].asStringOrNull())
        "inline_form" -> {
            val formJson = obj["text"].asStringOrNull()
            ChatMessageContent(
                type = "inline_form",
                text = formJson,        // JSON del InlineForm serializado
            )
        }
        else -> ChatMessageContent(
            type = type,
            mimeType = obj["mimeType"].asStringOrNull(),
            fileName = obj["fileName"].asStringOrNull(),
            base64 = obj["content"].asStringOrNull(),
        )
    }
}

// Respuesta: se envía como mensaje de usuario con formato especial
// El ChatController serializa el FormResponse y lo envía como:
// [FORM_RESPONSE:formId] { json_serializado }
// El gateway/agente lo interpreta como respuesta estructurada
```

**3. Renderizado nativo en el chat (FormFieldComposable):**

```kotlin
@Composable
fun InlineFormCard(
    form: InlineForm,
    onSubmit: (FormResponse) -> Unit,
    onCancel: (() -> Unit)?,
    isDisabled: Boolean = false,       // Después de enviar
) {
    val fieldStates = remember(form.formId) {
        mutableStateMapOf<String, FormValue>().apply {
            // Inicializar con valores por defecto
            form.fields.forEach { field ->
                defaultValueFor(field)?.let { put(field.id, it) }
            }
        }
    }
    val validationErrors = remember { mutableStateMapOf<String, String>() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isDisabled) 0.6f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Título
            form.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            
            // Descripción
            form.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            // Campos del formulario
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
                                    // Auto-submit para formularios de un solo botón
                                    if (form.autoSubmit && value is FormValue.ButtonValue) {
                                        submitForm(form, fieldStates, onSubmit)
                                    }
                                },
                                enabled = !isDisabled,
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
                        },
                        enabled = !isDisabled,
                    )
                }
            }
            
            // Botones de acción
            if (!isDisabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (form.cancelLabel != null) {
                        TextButton(onClick = { onCancel?.invoke() }) {
                            Text(form.cancelLabel)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = {
                            val errors = validateForm(form, fieldStates)
                            if (errors.isEmpty()) {
                                submitForm(form, fieldStates, onSubmit)
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
                // Mostrar resumen de la respuesta
                Text(
                    text = "✓ Submitted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
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
```

**4. Composables para cada tipo de campo:**

```kotlin
// Ejemplo: Select con Segmented Button (Material 3)
@Composable
fun SelectFormField(
    field: FormField.Select,
    value: FormValue?,
    error: String?,
    onValueChanged: (FormValue) -> Unit,
    enabled: Boolean,
) {
    Column {
        Text(
            text = field.label + if (field.required) " *" else "",
            style = MaterialTheme.typography.labelMedium,
        )
        
        when (field.style) {
            SelectStyle.SEGMENTED -> {
                // Material 3 Segmented Button
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    field.options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = (value as? FormValue.StringValue)?.value == option.id,
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    field.options.forEach { option ->
                        FilterChip(
                            selected = (value as? FormValue.StringValue)?.value == option.id,
                            onClick = { onValueChanged(FormValue.StringValue(option.id)) },
                            label = { Text(option.label) },
                            enabled = enabled && !option.disabled,
                            leadingIcon = option.icon?.let { emoji ->
                                { Text(emoji) }
                            },
                        )
                    }
                }
            }
            SelectStyle.RADIO -> {
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
                            selected = (value as? FormValue.StringValue)?.value == option.id,
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
                val selectedOption = field.options.find { 
                    it.id == (value as? FormValue.StringValue)?.value 
                }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (enabled) expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedOption?.label ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(field.label) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = enabled,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

// Ejemplo: Rating con estrellas
@Composable
fun RatingFormField(
    field: FormField.Rating,
    value: FormValue?,
    onValueChanged: (FormValue) -> Unit,
    enabled: Boolean,
) {
    Column {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val currentRating = (value as? FormValue.RatingValue)?.stars ?: field.defaultValue
            repeat(field.maxStars) { index ->
                val starIndex = index + 1
                val filled = starIndex <= currentRating
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star $starIndex",
                    tint = if (filled) {
                        Color(0xFFFFB300)  // Amber
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = enabled) {
                            onValueChanged(FormValue.RatingValue(starIndex))
                        },
                )
            }
        }
    }
}

// Ejemplo: Slider con valor visible y unidad
@Composable
fun SliderFormField(
    field: FormField.Slider,
    value: FormValue?,
    onValueChanged: (FormValue) -> Unit,
    enabled: Boolean,
) {
    val currentValue = (value as? FormValue.NumberValue)?.value ?: field.defaultValue
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
            )
            if (field.showValue) {
                val displayValue = if (field.step >= 1.0) {
                    "${currentValue.toInt()}${field.unit ?: ""}"
                } else {
                    "${"%.1f".format(currentValue)}${field.unit ?: ""}"
                }
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Slider(
            value = currentValue.toFloat(),
            onValueChange = { onValueChanged(FormValue.NumberValue(it.toDouble())) },
            valueRange = field.min.toFloat()..field.max.toFloat(),
            steps = if (field.step > 0) {
                ((field.max - field.min) / field.step).toInt() - 1
            } else {
                0
            },
            enabled = enabled,
        )
    }
}
```

**5. Integración con ChatMessageViews:**

```kotlin
// En ChatMessageViews.kt, al renderizar el contenido del mensaje:
@Composable
fun ChatMessageContentView(
    content: ChatMessageContent,
    messageId: String,
    onFormSubmit: (FormResponse) -> Unit,
    submittedForms: Set<String>,
) {
    when (content.type) {
        "text" -> {
            // Renderizado de texto Markdown existente
            ChatMarkdownText(text = content.text ?: "")
        }
        "inline_form" -> {
            val form = content.text?.let { parseInlineForm(it) }
            if (form != null) {
                InlineFormCard(
                    form = form,
                    onSubmit = onFormSubmit,
                    onCancel = {
                        onFormSubmit(FormResponse(
                            formId = form.formId,
                            values = emptyMap(),
                            action = FormAction.CANCEL,
                        ))
                    },
                    isDisabled = form.formId in submittedForms,
                )
            }
        }
        "image" -> {
            // Renderizado de imagen existente
            ChatImageContent(content)
        }
    }
}
```

**6. Envío de respuestas al agente:**

```kotlin
// En ChatController, nuevo método para enviar respuestas de formulario:
fun submitFormResponse(response: FormResponse) {
    val formattedValues = response.values.entries.joinToString(", ") { (key, value) ->
        "$key=${formatFormValue(value)}"
    }
    
    val actionLabel = when (response.action) {
        FormAction.SUBMIT -> "submitted"
        FormAction.CANCEL -> "cancelled"
        FormAction.EXPIRE -> "expired"
        FormAction.BUTTON_TAP -> "button_tap"
    }
    
    // Enviar como mensaje estructurado que el agente puede parsear
    val message = buildString {
        append("[FORM:${response.formId}:${actionLabel}]\n")
        if (response.action == FormAction.SUBMIT) {
            response.values.forEach { (key, value) ->
                append("  $key: ${formatFormValue(value)}\n")
            }
        }
    }
    
    // También enviar como JSON en un attachment para parsing exacto
    val jsonPayload = json.encodeToString(FormResponse.serializer(), response)
    
    sendMessage(
        message = message,
        thinkingLevel = _thinkingLevel.value,
        attachments = listOf(
            OutgoingAttachment(
                type = "form_response",
                mimeType = "application/json",
                fileName = "form_response_${response.formId}.json",
                base64 = java.util.Base64.getEncoder().encodeToString(
                    jsonPayload.toByteArray()
                ),
            ),
        ),
    )
}

private fun formatFormValue(value: FormValue): String {
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
```

**7. Generación de formularios desde el gateway/agente:**

Para que el agente pueda generar formularios, el gateway necesitaría soportar un nuevo content type en los mensajes de chat. El agente generaría el JSON del formulario como parte de su respuesta, usando un formato especial que el gateway convierte en el content type `inline_form`:

```
<!-- El agente generaría algo como: -->
¿Quieres que configure la alarma? Rellena los datos:

<openclaw:form id="alarm-config">
{
  "title": "⏰ Configurar Alarma",
  "fields": [
    {"type": "time", "id": "time", "label": "Hora", "required": true, "is24Hour": true},
    {"type": "select", "id": "repeat", "label": "Repetir", "style": "chips",
     "options": [
       {"id": "once", "label": "Una vez"},
       {"id": "weekdays", "label": "L-V"},
       {"id": "daily", "label": "Diario"},
       {"id": "custom", "label": "Personalizado"}
     ]
    },
    {"type": "text", "id": "label", "label": "Nombre (opcional)", "placeholder": "Alarma mañana"}
  ],
  "submitLabel": "Crear alarma"
}
</openclaw:form>
```

**Casos de uso concretos:**

1. **Crear evento de calendario:**
```
📅 Nuevo Evento
├── 📝 Nombre: [________________]
├── 📅 Fecha:  [date picker]
├── 🕐 Hora:   [time picker]
├── ⏱ Duración: ○ 30min ● 1h ○ 2h ○ Custom
├── 📍 Lugar: [________________]
├── 📧 Invitados: [________________]
└── [Crear] [Cancelar]
```

2. **Configurar el modo nocturno:**
```
🌙 Modo Nocturno
├── 🔆 Brillo luces: ═══●══════ 30%
├── 🌡 Temperatura:  ○ 20°C ● 21°C ○ 22°C ○ 23°C
├── 🔇 Silenciar notificaciones: [ON]
├── ⏰ Apagar luces a las: [23:30]
└── [Activar] [Cancelar]
```

3. **Confirmar envío de email:**
```
📧 Confirmar Email
├── Para: manuel@example.com
├── Asunto: "Reunión mañana"
├── Cuerpo: "Hola, te confirmo la reunión de..."
├── ──────────────────────
├── ⚠️ Se enviará desde tortufiera@gmail.com
└── [✉️ Enviar] [✏️ Editar] [❌ Cancelar]
```

4. **Selección rápida (auto-submit):**
```
¿Qué quieres hacer con la foto?
[📤 Enviar por Telegram] [💾 Guardar] [🗑 Descartar]
```

5. **Valorar respuesta del agente:**
```
¿Te fue útil esta respuesta?
★ ★ ★ ★ ☆
```

**Archivos nuevos:**

```
app/src/main/java/ai/openclaw/android/forms/
├── InlineForm.kt                // Modelos de datos (FormField, FormValue, etc.)
├── FormResponse.kt              // Modelo de respuesta
├── FormParser.kt                // Parser de JSON a InlineForm
├── FormValidator.kt             // Validación de campos

app/src/main/java/ai/openclaw/android/ui/forms/
├── InlineFormCard.kt            // Composable principal del formulario
├── FormFieldComposable.kt       // Dispatcher de renderizado
├── fields/
│   ├── TextFormField.kt
│   ├── NumberFormField.kt
│   ├── SliderFormField.kt
│   ├── ToggleFormField.kt
│   ├── SelectFormField.kt
│   ├── MultiSelectFormField.kt
│   ├── DatePickerFormField.kt
│   ├── TimePickerFormField.kt
│   ├── ColorPickerFormField.kt
│   ├── ButtonGroupFormField.kt
│   ├── RatingFormField.kt
│   ├── SeparatorFormField.kt
│   └── StaticTextFormField.kt
├── FormSubmissionManager.kt     // Gestión de formularios enviados/pendientes
└── FormPreviewScreen.kt         // Preview para testing
```

**Archivos modificados:**

- `ChatController.kt` — Parsing de content type `inline_form`, método `submitFormResponse()`
- `ChatModels.kt` — Extensión de `ChatMessageContent` para soportar formularios
- `ChatMessageViews.kt` — Renderizado de `InlineFormCard` en mensajes del asistente
- `ChatMessageListCard.kt` — Gestión de estado de formularios enviados (`submittedForms`)
- `ChatSheetContent.kt` — Propagación de callbacks de formularios
- `MainViewModel.kt` — Exposición del método de envío de formularios
- `NodeRuntime.kt` — Método `submitFormResponse()` que delega al `ChatController`

**Dependencias:**

No se requieren dependencias nuevas. Todos los componentes UI usan Material 3 nativo ya presente en el proyecto:
- `SegmentedButton`, `FilterChip`, `Slider`, `Switch`, `RadioButton` — ya en Material 3
- `ExposedDropdownMenuBox` — ya en Material 3
- `DatePicker`, `TimePicker` — ya en Material 3

La única dependencia opcional sería:
```kotlin
// FlowRow para layouts responsivos de chips (ya incluida en foundation)
implementation("androidx.compose.foundation:foundation-layout")
```

**Consideraciones de implementación:**

- **Retrocompatibilidad:** Si la app no soporta formularios, el agente incluye un fallback de texto con las mismas preguntas. El gateway podría detectar la versión de la app y enviar formularios solo a versiones que los soporten.
- **Expiración:** Formularios con `expiresAtMs` se deshabilitan automáticamente, mostrando un mensaje de "Formulario expirado".
- **Estado post-envío:** Tras enviar, el formulario se muestra deshabilitado con un indicador "✓ Submitted" y un resumen de los valores enviados.
- **Validación:** La validación se ejecuta en el cliente antes de enviar. Los campos `required` muestran error si están vacíos; los campos `Text` con `validation.pattern` se validan contra la regex.
- **Accesibilidad:** Cada campo usa `contentDescription` apropiado y soporta navegación por teclado.
- **Temas:** Los formularios heredan el tema Material 3 de la app, incluyendo colores dinámicos (Material You) si están habilitados.
- **Rendimiento:** Los formularios se renderizan con `remember` para los estados de campos, evitando recomposiciones innecesarias.
- **Offline:** Los formularios se almacenan como parte del mensaje y se pueden rellenar offline; el envío se encola si no hay conexión.

**Por qué es valioso para Manuel:**

Como power user que:
- ⏰ **Configura alarmas y eventos:** Un formulario con date/time pickers nativos es infinitamente más rápido que escribir "pon una alarma mañana a las 7:30, que suene solo de lunes a viernes"
- 🏠 **Controla domótica:** Ajustar brillo con un slider, temperatura con segmented buttons, on/off con toggles — todo en una sola interacción
- 📧 **Revisa antes de enviar:** Ver un preview estructurado de un email/mensaje antes de confirmar
- ⚙️ **Configura el agente:** Cambiar settings del gateway, ajustar parámetros de skills, todo con formularios tipados
- 🔄 **Reduce turnos de conversación:** Lo que antes eran 5-6 mensajes de ida y vuelta se convierte en 1 formulario + 1 submit

Esto transforma la app de un "cliente de chat con texto libre" a una **interfaz de interacción estructurada** donde el agente puede presentar exactamente los controles que necesita.

**Estimación de tiempo detallada:**
- Modelos de datos (InlineForm, FormField, FormValue, FormResponse): 1.5h
- FormParser + FormValidator: 1.5h
- InlineFormCard composable principal: 2h
- Campos individuales (13 tipos × ~30min cada uno): 6.5h
- Integración con ChatController (parsing, envío): 2h
- Integración con ChatMessageViews (renderizado inline): 1.5h
- FormSubmissionManager (estado de formularios enviados, expiración): 1h
- Testing manual + edge cases (formularios vacíos, expirados, offline): 2h
- Accesibilidad y responsive layout: 1h
- **Total: ~19h**

### [2026-02-10] Sistema de Cifrado Extremo a Extremo con Protocolo Signal y Almacenamiento Seguro (End-to-End Encrypted Chat & Secure Vault)
- **Estado:** propuesta
- **Plataforma:** Android
- **Estimación:** >4h
- **PR:** (pendiente)

Implementar un sistema completo de cifrado extremo a extremo (E2E) para las comunicaciones entre la app Android y el gateway OpenClaw, utilizando el protocolo Signal (Double Ratchet) para cifrar todos los mensajes del chat, adjuntos y datos de contexto del dispositivo. Incluye un vault seguro local para almacenar conversaciones cifradas y claves de sesión, con verificación de identidad del gateway mediante safety numbers.

**Problema que resuelve:**

Actualmente, la comunicación entre la app Android y el gateway OpenClaw presenta varias vulnerabilidades y limitaciones de privacidad:

1. **TLS como única capa de protección:** Los mensajes viajan por WebSocket con TLS (cuando está habilitado), pero TLS solo protege el transporte. El gateway puede leer todos los mensajes en claro, y cualquier compromiso del servidor expone todo el historial de conversaciones. Para un asistente personal que maneja datos íntimos (domótica, calendario, ubicación, mensajes personales), esto es un riesgo significativo.

2. **Sin forward secrecy a nivel de aplicación:** Si la clave TLS se compromete, un atacante que haya capturado tráfico anterior puede descifrar todas las comunicaciones pasadas. El protocolo Signal proporciona forward secrecy mediante el Double Ratchet: cada mensaje usa una clave efímera, y comprometer una clave no expone mensajes anteriores ni futuros.

3. **Trust on First Use (TOFU) sin verificación:** La app implementa TOFU para fingerprints TLS (`GatewayTls.kt`), pero no hay mecanismo para que el usuario verifique la identidad del gateway de forma independiente (como safety numbers que se pueden comparar en persona).

4. **Almacenamiento local inseguro:** Las credenciales se guardan en `EncryptedSharedPreferences` (`SecurePrefs.kt`), pero el historial de mensajes (`ChatController`) solo se mantiene en memoria — no hay persistencia, y si se implementara caché local (propuesta existente de Room DB), los mensajes se almacenarían sin cifrar en la base de datos SQLite.

5. **Adjuntos y contexto sin cifrar:** Las fotos de la cámara, capturas de pantalla, clips de vídeo y datos de ubicación/contexto del dispositivo se envían como base64 en JSON sin cifrado a nivel de aplicación. Un man-in-the-middle que comprometa TLS tendría acceso a contenido multimedia sensible.

6. **Sin aislamiento criptográfico entre sesiones:** Todas las sesiones de chat (main, sub-agentes, isoladas) comparten las mismas credenciales de transporte. No hay separación criptográfica que limite el impacto de un compromiso.

7. **Identidad del dispositivo sin rotación:** El `DeviceIdentityStore` genera un par Ed25519 una vez y lo usa indefinidamente. No hay mecanismo de rotación de claves ni revocación.

Para un power user como Manuel que usa OpenClaw para controlar su casa, acceder a su calendario, enviar mensajes, y compartir su ubicación — todo a través de un servidor que corre en una Raspberry Pi accesible por Tailscale — tener E2E no es un lujo, es una necesidad de higiene de seguridad.

**Funcionalidades propuestas:**

**1. Modelo criptográfico basado en Signal Protocol:**

```kotlin
// Clave de identidad del dispositivo (ya existe en DeviceIdentityStore, se extiende)
@Serializable
data class E2EIdentityKeyPair(
    val publicKey: ByteArray,        // Curve25519 public key
    val privateKey: ByteArray,       // Curve25519 private key
    val ed25519PublicKey: ByteArray,  // Para firmar (compatible con identidad existente)
    val ed25519PrivateKey: ByteArray,
    val createdAtMs: Long,
    val rotatedFromId: String? = null,
)

// Pre-keys para establecer sesiones
@Serializable
data class E2EPreKeyBundle(
    val identityKey: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyId: Int?,
    val oneTimePreKey: ByteArray?,
)

// Estado de sesión Signal (Double Ratchet)
@Serializable
data class E2ESessionState(
    val sessionId: String,
    val remoteIdentityKey: ByteArray,
    val rootKey: ByteArray,
    val sendingChainKey: ByteArray?,
    val receivingChainKey: ByteArray?,
    val sendingRatchetKey: ByteArray?,   // DH ratchet
    val receivingRatchetKey: ByteArray?,
    val previousCounter: Int,
    val messageCounter: Int,
    val skippedMessageKeys: Map<Pair<ByteArray, Int>, ByteArray>,  // Para mensajes fuera de orden
    val createdAtMs: Long,
    val lastActivityMs: Long,
)

// Mensaje cifrado
@Serializable
data class E2EEncryptedMessage(
    val version: Int = 1,
    val senderIdentityKey: ByteArray,
    val senderRatchetKey: ByteArray,     // Ephemeral DH key
    val previousCounter: Int,
    val counter: Int,
    val ciphertext: ByteArray,           // AES-256-GCM
    val mac: ByteArray,                  // HMAC-SHA256
    val timestamp: Long,
    val type: E2EMessageType,
)

enum class E2EMessageType {
    PREKEY_MESSAGE,       // Primer mensaje (incluye pre-key bundle)
    NORMAL_MESSAGE,       // Mensaje normal con ratchet
    KEY_EXCHANGE,         // Renegociación de claves
}

// Safety Number para verificación
data class SafetyNumber(
    val localIdentityKey: ByteArray,
    val remoteIdentityKey: ByteArray,
) {
    fun fingerprint(): String {
        // Genera 60 dígitos (12 grupos de 5) como Signal
        val combined = localIdentityKey + remoteIdentityKey
        val hash = MessageDigest.getInstance("SHA-512").digest(combined)
        val sb = StringBuilder()
        for (i in 0 until 30) {
            val value = ((hash[i * 2].toInt() and 0xFF) shl 8) or (hash[i * 2 + 1].toInt() and 0xFF)
            sb.append(String.format("%05d", value % 100000))
            if ((i + 1) % 5 == 0 && i < 29) sb.append(" ")
        }
        return sb.toString()
    }

    fun qrCodeData(): ByteArray {
        // Datos para generar QR de verificación
        return ByteArray(0) // version byte + identity keys
    }
}
```

**2. Motor de cifrado E2E (E2ECryptoEngine):**

```kotlin
class E2ECryptoEngine(
    private val identityStore: E2EIdentityStore,
    private val sessionStore: E2ESessionStore,
    private val preKeyStore: E2EPreKeyStore,
) {
    companion object {
        private const val AES_KEY_LENGTH = 32  // 256 bits
        private const val HMAC_KEY_LENGTH = 32
        private const val IV_LENGTH = 12       // AES-GCM nonce
        private const val TAG = "E2ECrypto"
    }

    // Inicializar sesión con el gateway (X3DH key agreement)
    suspend fun initializeSession(
        remotePreKeyBundle: E2EPreKeyBundle,
    ): E2ESessionState {
        val localIdentity = identityStore.getIdentityKeyPair()

        // X3DH (Extended Triple Diffie-Hellman)
        val ephemeralKeyPair = generateX25519KeyPair()

        // DH1 = DH(IKa, SPKb)
        val dh1 = x25519(localIdentity.privateKey, remotePreKeyBundle.signedPreKey)
        // DH2 = DH(EKa, IKb)
        val dh2 = x25519(ephemeralKeyPair.privateKey, remotePreKeyBundle.identityKey)
        // DH3 = DH(EKa, SPKb)
        val dh3 = x25519(ephemeralKeyPair.privateKey, remotePreKeyBundle.signedPreKey)

        var masterSecret = dh1 + dh2 + dh3

        // DH4 si hay one-time pre-key
        remotePreKeyBundle.oneTimePreKey?.let { otpk ->
            val dh4 = x25519(ephemeralKeyPair.privateKey, otpk)
            masterSecret += dh4
        }

        // Derivar root key y chain key con HKDF
        val (rootKey, chainKey) = hkdfDerive(
            inputKeyMaterial = masterSecret,
            salt = ByteArray(32),  // zeros
            info = "OpenClawE2E".toByteArray(),
            outputLength = 64,
        )

        val session = E2ESessionState(
            sessionId = UUID.randomUUID().toString(),
            remoteIdentityKey = remotePreKeyBundle.identityKey,
            rootKey = rootKey,
            sendingChainKey = chainKey,
            receivingChainKey = null,
            sendingRatchetKey = ephemeralKeyPair.publicKey,
            receivingRatchetKey = remotePreKeyBundle.signedPreKey,
            previousCounter = 0,
            messageCounter = 0,
            skippedMessageKeys = emptyMap(),
            createdAtMs = System.currentTimeMillis(),
            lastActivityMs = System.currentTimeMillis(),
        )

        sessionStore.saveSession(session)
        return session
    }

    // Cifrar mensaje
    suspend fun encrypt(
        plaintext: ByteArray,
        sessionId: String,
    ): E2EEncryptedMessage {
        val session = sessionStore.getSession(sessionId)
            ?: throw IllegalStateException("No E2E session found")

        // Derivar message key de la chain key
        val (messageKey, nextChainKey) = deriveMessageKey(session.sendingChainKey!!)

        // Separar en encryption key, mac key, iv
        val (encKey, macKey, iv) = splitMessageKey(messageKey)

        // Cifrar con AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        // AAD (Additional Authenticated Data): identity keys + counter
        val aad = session.remoteIdentityKey + 
                  identityStore.getIdentityKeyPair().publicKey +
                  session.messageCounter.toByteArray()
        cipher.updateAAD(aad)

        val ciphertext = cipher.doFinal(plaintext)

        // HMAC sobre todo el mensaje
        val mac = hmacSha256(
            macKey,
            session.sendingRatchetKey!! + ciphertext + session.messageCounter.toByteArray()
        )

        // Actualizar sesión
        val updatedSession = session.copy(
            sendingChainKey = nextChainKey,
            messageCounter = session.messageCounter + 1,
            lastActivityMs = System.currentTimeMillis(),
        )
        sessionStore.saveSession(updatedSession)

        return E2EEncryptedMessage(
            senderIdentityKey = identityStore.getIdentityKeyPair().publicKey,
            senderRatchetKey = session.sendingRatchetKey!!,
            previousCounter = session.previousCounter,
            counter = session.messageCounter,
            ciphertext = ciphertext,
            mac = mac.copyOf(8),  // Primeros 8 bytes del HMAC
            timestamp = System.currentTimeMillis(),
            type = E2EMessageType.NORMAL_MESSAGE,
        )
    }

    // Descifrar mensaje
    suspend fun decrypt(
        encryptedMessage: E2EEncryptedMessage,
        sessionId: String,
    ): ByteArray {
        val session = sessionStore.getSession(sessionId)
            ?: throw IllegalStateException("No E2E session found")

        // Verificar si necesitamos hacer ratchet DH
        val updatedSession = if (
            encryptedMessage.senderRatchetKey.contentEquals(session.receivingRatchetKey ?: ByteArray(0))
        ) {
            session
        } else {
            performDHRatchet(session, encryptedMessage.senderRatchetKey)
        }

        // Manejar mensajes fuera de orden
        val skippedKey = updatedSession.skippedMessageKeys[
            Pair(encryptedMessage.senderRatchetKey, encryptedMessage.counter)
        ]
        val messageKey = if (skippedKey != null) {
            skippedKey
        } else {
            // Avanzar la chain key hasta el counter correcto
            advanceChainToCounter(updatedSession, encryptedMessage.counter)
        }

        val (encKey, macKey, iv) = splitMessageKey(messageKey)

        // Verificar HMAC
        val expectedMac = hmacSha256(
            macKey,
            encryptedMessage.senderRatchetKey + encryptedMessage.ciphertext + encryptedMessage.counter.toByteArray()
        )
        if (!expectedMac.copyOf(8).contentEquals(encryptedMessage.mac)) {
            throw SecurityException("MAC verification failed")
        }

        // Descifrar
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encKey, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val aad = identityStore.getIdentityKeyPair().publicKey +
                  encryptedMessage.senderIdentityKey +
                  encryptedMessage.counter.toByteArray()
        cipher.updateAAD(aad)

        val plaintext = cipher.doFinal(encryptedMessage.ciphertext)

        sessionStore.saveSession(updatedSession.copy(
            lastActivityMs = System.currentTimeMillis(),
        ))

        return plaintext
    }

    // DH Ratchet step
    private suspend fun performDHRatchet(
        session: E2ESessionState,
        newRemoteRatchetKey: ByteArray,
    ): E2ESessionState {
        // Generar nuevo par de claves DH
        val newRatchetKeyPair = generateX25519KeyPair()

        // DH con la nueva clave remota
        val dhOutput = x25519(session.sendingRatchetKey!!, newRemoteRatchetKey)

        // Derivar nuevas root key y receiving chain key
        val (newRootKey1, receivingChainKey) = hkdfDerive(
            inputKeyMaterial = dhOutput,
            salt = session.rootKey,
            info = "OpenClawRatchet".toByteArray(),
            outputLength = 64,
        )

        // Segunda derivación para sending chain key
        val dhOutput2 = x25519(newRatchetKeyPair.privateKey, newRemoteRatchetKey)
        val (newRootKey2, sendingChainKey) = hkdfDerive(
            inputKeyMaterial = dhOutput2,
            salt = newRootKey1,
            info = "OpenClawRatchet".toByteArray(),
            outputLength = 64,
        )

        return session.copy(
            rootKey = newRootKey2,
            sendingChainKey = sendingChainKey,
            receivingChainKey = receivingChainKey,
            sendingRatchetKey = newRatchetKeyPair.publicKey,
            receivingRatchetKey = newRemoteRatchetKey,
            previousCounter = session.messageCounter,
            messageCounter = 0,
        )
    }

    // Primitivas criptográficas
    private fun x25519(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        val privKeySpec = XECPrivateKeySpec(NamedParameterSpec.X25519, privateKey)
        val pubKeySpec = XECPublicKeySpec(NamedParameterSpec.X25519, BigInteger(1, publicKey))
        val kf = KeyFactory.getInstance("X25519")
        keyAgreement.init(kf.generatePrivate(privKeySpec))
        keyAgreement.doPhase(kf.generatePublic(pubKeySpec), true)
        return keyAgreement.generateSecret()
    }

    private fun generateX25519KeyPair(): KeyPairData {
        val kpg = KeyPairGenerator.getInstance("X25519")
        val keyPair = kpg.generateKeyPair()
        return KeyPairData(
            publicKey = (keyPair.public as XECPublicKey).u.toByteArray(),
            privateKey = (keyPair.private as XECPrivateKey).scalar.orElseThrow().clone(),
        )
    }

    private fun hkdfDerive(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): Pair<ByteArray, ByteArray> {
        // HKDF-SHA256 extract + expand
        val prk = hmacSha256(salt, inputKeyMaterial)
        val t1 = hmacSha256(prk, info + byteArrayOf(1))
        val t2 = hmacSha256(prk, t1 + info + byteArrayOf(2))
        return t1.copyOf(32) to t2.copyOf(32)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun deriveMessageKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = hmacSha256(chainKey, byteArrayOf(0x01))
        val nextChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        return messageKey to nextChainKey
    }

    private fun splitMessageKey(messageKey: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val derived = hkdfDerive(messageKey, ByteArray(32), "OpenClawMK".toByteArray(), 80)
        val encKey = derived.first  // 32 bytes
        val macKey = derived.second.copyOfRange(0, 32)
        val iv = derived.second.copyOfRange(0, 12) // 12 bytes for GCM nonce
        return Triple(encKey, macKey, iv)
    }

    private data class KeyPairData(val publicKey: ByteArray, val privateKey: ByteArray)
}
```

**3. Secure Vault — Almacenamiento local cifrado (E2ESecureVault):**

```kotlin
class E2ESecureVault(
    private val context: Context,
) {
    private val masterKeyAlias = "openclaw_e2e_master"

    // Derivar clave maestra de Android Keystore
    private fun getMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (keyStore.containsAlias(masterKeyAlias)) {
            return (keyStore.getEntry(masterKeyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                masterKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)  // Accesible sin biometría
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    // Cifrar datos para almacenar localmente
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // IV (12) + ciphertext
        return iv + ciphertext
    }

    // Descifrar datos almacenados
    fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    // Base de datos cifrada para sesiones y claves
    fun createEncryptedDatabase(): SupportSQLiteOpenHelper.Factory {
        val passphrase = getOrCreateDatabasePassphrase()
        return SupportFactory(passphrase)  // SQLCipher
    }

    private fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences("e2e_vault", Context.MODE_PRIVATE)
        val stored = prefs.getString("db_passphrase_enc", null)
        if (stored != null) {
            return decrypt(Base64.decode(stored, Base64.DEFAULT))
        }
        // Generar nueva passphrase aleatoria
        val passphrase = ByteArray(32)
        SecureRandom().nextBytes(passphrase)
        val encrypted = encrypt(passphrase)
        prefs.edit().putString(
            "db_passphrase_enc",
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        ).apply()
        return passphrase
    }
}

// Room Database cifrada con SQLCipher
@Database(
    entities = [
        E2ESessionEntity::class,
        E2EPreKeyEntity::class,
        E2ESignedPreKeyEntity::class,
        E2ESkippedMessageKeyEntity::class,
        E2EEncryptedMessageEntity::class,
    ],
    version = 1,
)
abstract class E2EDatabase : RoomDatabase() {
    abstract fun sessionDao(): E2ESessionDao
    abstract fun preKeyDao(): E2EPreKeyDao
    abstract fun messageDao(): E2EMessageDao

    companion object {
        fun create(context: Context, vault: E2ESecureVault): E2EDatabase {
            return Room.databaseBuilder(context, E2EDatabase::class.java, "openclaw_e2e.db")
                .openHelperFactory(vault.createEncryptedDatabase())
                .build()
        }
    }
}

@Entity(tableName = "e2e_sessions")
data class E2ESessionEntity(
    @PrimaryKey val sessionId: String,
    val remoteIdentityKeyB64: String,
    val stateJson: String,  // Cifrado con vault
    val createdAtMs: Long,
    val lastActivityMs: Long,
    val verified: Boolean = false,
    val safetyNumberB64: String? = null,
)

@Entity(tableName = "e2e_messages")
data class E2EEncryptedMessageEntity(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val direction: String,  // "sent" | "received"
    val encryptedPayloadB64: String,
    val timestampMs: Long,
    val decryptedPreviewB64: String? = null,  // Cifrado con vault para búsqueda local
)
```

**4. Integración con GatewaySession — Capa de cifrado transparente:**

```kotlin
class E2EGatewayBridge(
    private val cryptoEngine: E2ECryptoEngine,
    private val vault: E2ESecureVault,
    private val config: E2EConfig,
) {
    private var activeSessionId: String? = null

    // Wrappea el envío de mensajes para cifrar transparentemente
    suspend fun wrapOutgoingMessage(
        method: String,
        paramsJson: String,
    ): String {
        if (!config.enabled || !isE2EMethod(method)) {
            return paramsJson
        }

        val sessionId = activeSessionId
            ?: throw IllegalStateException("E2E session not established")

        val plaintext = paramsJson.toByteArray(Charsets.UTF_8)
        val encrypted = cryptoEngine.encrypt(plaintext, sessionId)

        return buildJsonObject {
            put("e2e", JsonPrimitive(true))
            put("version", JsonPrimitive(encrypted.version))
            put("type", JsonPrimitive(encrypted.type.name))
            put("senderKey", JsonPrimitive(Base64.encodeToString(encrypted.senderIdentityKey, Base64.NO_WRAP)))
            put("ratchetKey", JsonPrimitive(Base64.encodeToString(encrypted.senderRatchetKey, Base64.NO_WRAP)))
            put("counter", JsonPrimitive(encrypted.counter))
            put("previousCounter", JsonPrimitive(encrypted.previousCounter))
            put("ciphertext", JsonPrimitive(Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)))
            put("mac", JsonPrimitive(Base64.encodeToString(encrypted.mac, Base64.NO_WRAP)))
            put("ts", JsonPrimitive(encrypted.timestamp))
        }.toString()
    }

    // Unwrappea mensajes recibidos para descifrar transparentemente
    suspend fun unwrapIncomingMessage(payloadJson: String): String {
        if (!config.enabled) return payloadJson

        val root = Json.parseToJsonElement(payloadJson) as? JsonObject
            ?: return payloadJson
        val isE2E = (root["e2e"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
            ?: return payloadJson
        if (!isE2E) return payloadJson

        val sessionId = activeSessionId
            ?: throw IllegalStateException("E2E session not established")

        val encrypted = E2EEncryptedMessage(
            senderIdentityKey = Base64.decode((root["senderKey"] as JsonPrimitive).content, Base64.DEFAULT),
            senderRatchetKey = Base64.decode((root["ratchetKey"] as JsonPrimitive).content, Base64.DEFAULT),
            counter = (root["counter"] as JsonPrimitive).content.toInt(),
            previousCounter = (root["previousCounter"] as JsonPrimitive).content.toInt(),
            ciphertext = Base64.decode((root["ciphertext"] as JsonPrimitive).content, Base64.DEFAULT),
            mac = Base64.decode((root["mac"] as JsonPrimitive).content, Base64.DEFAULT),
            timestamp = (root["ts"] as JsonPrimitive).content.toLong(),
            type = E2EMessageType.valueOf((root["type"] as JsonPrimitive).content),
        )

        val plaintext = cryptoEngine.decrypt(encrypted, sessionId)
        return String(plaintext, Charsets.UTF_8)
    }

    // Negociación E2E durante el connect
    suspend fun negotiateE2E(session: GatewaySession): Boolean {
        // 1. Solicitar pre-key bundle del gateway
        val bundleJson = session.request("e2e.preKeyBundle", null)
        val bundle = parsePreKeyBundle(bundleJson)

        // 2. Verificar firma del signed pre-key
        if (!verifySignedPreKey(bundle)) {
            throw SecurityException("Signed pre-key verification failed")
        }

        // 3. Inicializar sesión Signal
        val e2eSession = cryptoEngine.initializeSession(bundle)
        activeSessionId = e2eSession.sessionId

        // 4. Enviar nuestro pre-key message al gateway
        val ack = cryptoEngine.encrypt(
            "E2E_HANDSHAKE_ACK".toByteArray(),
            e2eSession.sessionId,
        )
        session.request("e2e.establish", serializeEncryptedMessage(ack))

        return true
    }

    private fun isE2EMethod(method: String): Boolean {
        return method in setOf("chat.send", "chat.history", "node.event")
    }
}
```

**5. Configuración y UI de E2E:**

```kotlin
@Serializable
data class E2EConfig(
    val enabled: Boolean = false,
    val autoNegotiate: Boolean = true,        // Negociar E2E automáticamente al conectar
    val requireE2E: Boolean = false,          // Rechazar conexiones sin E2E
    val storeEncryptedHistory: Boolean = true, // Guardar historial cifrado localmente
    val maxSkippedMessages: Int = 500,         // Máximo de message keys guardadas (fuera de orden)
    val sessionRotationIntervalMs: Long = 7 * 24 * 3600 * 1000L, // Rotar sesión cada 7 días
    val keyRotationEnabled: Boolean = true,
)
```

**UI de Verificación de Seguridad (SecurityVerificationSheet):**

```
┌─────────────────────────────────────────────────────────────┐
│ 🔒 End-to-End Encryption                           [Close] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Status: ● Active                                            │
│ Session established: Today 09:15                            │
│ Messages encrypted: 247                                     │
│ Key rotations: 3                                            │
│                                                             │
│ ─────────────────────────────────────────────────────────── │
│                                                             │
│ SAFETY NUMBER                                               │
│ Compare this number with the one shown on your gateway:     │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  12345 67890 12345 67890 12345 67890                    │ │
│ │  12345 67890 12345 67890 12345 67890                    │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [📷 Scan QR Code]              [⬜ Show QR Code]            │
│                                                             │
│ ─────────────────────────────────────────────────────────── │
│                                                             │
│ VERIFICATION STATUS                                         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Gateway "jarpi"                                         │ │
│ │ Identity Key: ab3f...c7d2                               │ │
│ │ ○ Not verified                    [Mark as Verified]    │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─────────────────────────────────────────────────────────── │
│                                                             │
│ ▼ ADVANCED                                                  │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ [✓] Auto-negotiate E2E on connect                      │ │
│ │ [ ] Require E2E (reject unencrypted connections)       │ │
│ │ [✓] Store encrypted message history locally            │ │
│ │ [✓] Auto-rotate session keys (every 7 days)            │ │
│ │                                                         │ │
│ │ [🔄 Force Key Rotation]     [🗑️ Clear E2E Data]       │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ SESSION HISTORY                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Feb 10, 09:15 — Active (247 messages)                  │ │
│ │ Feb 3, 11:30 — Rotated (1,203 messages)                │ │
│ │ Jan 27, 08:00 — Initial session                        │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Indicador de cifrado en el chat:**

```
┌─────────────────────────────────────────────────────────────┐
│ 🔒 jarpi                    ● Connected            [⚙️]    │
│ ──────────────────────────────────────────────────────────── │
│                                                             │
│ [🔒 Messages are end-to-end encrypted. Tap to learn more]  │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 👤 Enciende las luces del salón                        │ │
│ │                                            10:23 🔒    │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🤖 Luces del salón encendidas.                         │ │
│ │                                            10:23 🔒    │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

El candado 🔒 aparece junto a cada mensaje indicando que fue cifrado E2E.

**6. Protocolo de negociación E2E con el Gateway:**

```
App                                    Gateway
 │                                        │
 │──── connect (with e2e_supported cap) ──→│
 │                                        │
 │←──── connect.ok (e2e_available) ───────│
 │                                        │
 │──── e2e.preKeyBundle.request ──────────→│
 │                                        │
 │←──── e2e.preKeyBundle (IK, SPK, OPK) ──│
 │                                        │
 │   [X3DH key agreement]                │
 │                                        │
 │──── e2e.establish (PreKey Message) ────→│
 │                                        │
 │   [Gateway performs X3DH]              │
 │                                        │
 │←──── e2e.established (ack) ────────────│
 │                                        │
 │   [Double Ratchet active]              │
 │                                        │
 │═══╦ chat.send (E2E encrypted) ════════→│
 │   ║ chat event (E2E encrypted) ←═══════│
 │   ║ node.event (E2E encrypted) ════════→│
 │   ╚════════════════════════════════════│
```

**Arquitectura propuesta:**

```
┌─────────────────────────────────────────────────────────────┐
│                     SecuritySheet UI                        │
│  (verificación, safety numbers, configuración)              │
├─────────────────────────────────────────────────────────────┤
│                    E2EGatewayBridge                          │
│  (capa transparente: cifra/descifra mensajes)               │
├─────────────────────────────────────────────────────────────┤
│                    E2ECryptoEngine                           │
│  (Signal Protocol: X3DH + Double Ratchet)                   │
├─────────────────────┬───────────────────────────────────────┤
│  E2EIdentityStore   │    E2ESessionStore                    │
│  (Curve25519 keys)  │    (ratchet states)                   │
├─────────────────────┤                                       │
│  E2EPreKeyStore     │    E2ESkippedKeyStore                 │
│  (pre-keys)         │    (out-of-order keys)                │
├─────────────────────┴───────────────────────────────────────┤
│                    E2ESecureVault                            │
│  (Android Keystore + SQLCipher encrypted DB)                │
└─────────────────────────────────────────────────────────────┘
```

**Archivos nuevos:**

```
app/src/main/java/ai/openclaw/android/e2e/
├── E2EConfig.kt                    // Configuración
├── E2ECryptoEngine.kt              // Motor Signal Protocol
├── E2EGatewayBridge.kt             // Integración transparente
├── E2ESecureVault.kt               // Android Keystore + SQLCipher
├── E2EDatabase.kt                  // Room DB cifrada
├── models/
│   ├── E2EIdentityKeyPair.kt       // Claves de identidad
│   ├── E2EPreKeyBundle.kt          // Pre-key bundle
│   ├── E2ESessionState.kt          // Estado Double Ratchet
│   ├── E2EEncryptedMessage.kt      // Mensaje cifrado
│   └── SafetyNumber.kt             // Safety numbers
├── stores/
│   ├── E2EIdentityStore.kt         // Persistencia de identity keys
│   ├── E2ESessionStore.kt          // Persistencia de sesiones
│   ├── E2EPreKeyStore.kt           // Persistencia de pre-keys
│   └── E2ESkippedKeyStore.kt       // Claves de mensajes fuera de orden
├── crypto/
│   ├── X25519.kt                   // Curve25519 DH
│   ├── HKDF.kt                     // HKDF-SHA256
│   ├── AESGCMCipher.kt             // AES-256-GCM
│   └── HMACUtils.kt                // HMAC-SHA256

app/src/main/java/ai/openclaw/android/ui/e2e/
├── SecuritySheet.kt                // UI principal de E2E
├── SafetyNumberView.kt             // Visualización de safety numbers
├── QRVerificationSheet.kt          // Escaneo/display de QR
├── E2EStatusIndicator.kt           // Candado en mensajes
└── E2ESettingsSection.kt           // Sección en Settings
```

**Archivos modificados:**

- `GatewaySession.kt` — Integrar E2EGatewayBridge en request/handleMessage para cifrar/descifrar transparentemente
- `ChatController.kt` — Mostrar estado de cifrado por mensaje
- `ChatModels.kt` — Añadir campo `encrypted: Boolean` a ChatMessage
- `NodeRuntime.kt` — Inicializar E2ECryptoEngine, negociar E2E post-connect
- `DeviceIdentityStore.kt` — Extender con Curve25519 keys (además de Ed25519 existente)
- `SecurePrefs.kt` — Persistir E2EConfig
- `SettingsSheet.kt` — Enlace a SecuritySheet
- `ChatMessageViews.kt` — Mostrar 🔒 en mensajes cifrados
- `ChatSheetContent.kt` — Banner "Messages are E2E encrypted"
- `build.gradle.kts` — Dependencias (signal-protocol-java o libsodium, SQLCipher, ZXing para QR)

**Dependencias:**

```kotlin
// Signal Protocol (o implementación propia con libsodium)
implementation("org.whispersystems:signal-protocol-android:2.8.1")
// Alternativa: usar BouncyCastle ya incluido + implementación manual

// SQLCipher para Room Database cifrada
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")

// QR code generation y escaneo
implementation("com.google.zxing:core:3.5.2")
implementation("com.journeyapps:zxing-android-embedded:4.3.0")

// CameraX para escaneo de QR (ya puede estar parcialmente incluido)
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
```

**Consideraciones de implementación:**

- **Compatibilidad con gateway:** Requiere que el gateway OpenClaw implemente el lado servidor del protocolo Signal. Esto puede hacerse como feature flag (`e2e_supported` cap) — si el gateway no lo soporta, la app funciona sin E2E como hasta ahora.

- **Rendimiento:** X25519 y AES-256-GCM son extremadamente rápidos en hardware moderno. El overhead de cifrado es <1ms por mensaje. La negociación X3DH inicial toma <50ms.

- **Fallback graceful:** Si E2E no está disponible (gateway antiguo, error de negociación), la app muestra un indicador claro "⚠️ Not encrypted" y funciona normalmente. Opcionalmente, `requireE2E=true` rechaza conexiones sin cifrado.

- **Mensajes fuera de orden:** El protocolo Signal maneja esto nativamente con skipped message keys. Se almacenan hasta `maxSkippedMessages` (500 por defecto) claves de mensajes saltados.

- **Rotación de claves:** Cada DH ratchet step rota automáticamente las claves. Adicionalmente, se puede forzar una renegociación completa periódicamente.

- **Pérdida de sesión:** Si la app pierde el estado de sesión (reinstalación, clear data), se renegocia automáticamente. Los mensajes anteriores no son recuperables (forward secrecy by design).

- **Adjuntos grandes:** Los adjuntos (fotos, vídeos) se cifran con una clave AES efímera, y esa clave se envía dentro del mensaje E2E. Esto evita cifrar todo el binario con el ratchet.

- **Multi-dispositivo:** Cada dispositivo tiene su propia sesión E2E con el gateway. El gateway actúa como hub y descifra/recifra para cada dispositivo (modelo similar a Signal multi-device pre-sealed-sender).

**Por qué es útil para Manuel:**

Como power user que:

- 🏠 **Controla su casa con OpenClaw:** Comandos de domótica (luces, cámaras, cerraduras) pasan por el gateway. Un compromiso del transporte daría control de su hogar a un atacante.

- 📷 **Envía fotos/vídeos por la cámara del móvil:** Imágenes capturadas por la cámara del Tapo o del teléfono viajan sin cifrado E2E. Con E2E, ni siquiera un compromiso del servidor las expone.

- 📍 **Comparte ubicación:** La ubicación precisa del dispositivo se envía al gateway. E2E protege esta información sensible.

- 🔑 **Accede via Tailscale desde fuera:** La Pi es accesible por Tailscale, lo que añade una capa de red pero no cifrado a nivel de aplicación. E2E es la defensa en profundidad.

- 🛡️ **Es técnico y valora la privacidad:** Ha establecido explícitamente reglas de privacidad ("NUNCA subir datos privados a servidores externos"). E2E es la expresión técnica natural de esa filosofía.

- 📱 **Usa el asistente para datos personales:** Calendario, mensajes, notas — todo pasa por el canal de comunicación. Forward secrecy garantiza que ni un compromiso futuro expone conversaciones pasadas.

**Estimación de tiempo:**

- Primitivas criptográficas (X25519, HKDF, AES-GCM, HMAC): 3h
- E2ECryptoEngine (X3DH + Double Ratchet): 6h
- E2ESecureVault (Android Keystore + SQLCipher): 2h
- E2EDatabase (Room entities, DAOs): 2h
- E2EGatewayBridge (integración transparente): 3h
- Negociación E2E durante connect: 2h
- Stores (Identity, Session, PreKey, SkippedKey): 2h
- SecuritySheet UI: 2h
- Safety Numbers + QR verification: 2h
- E2EStatusIndicator + candado en mensajes: 1h
- E2ESettingsSection: 1h
- Integración con ChatController + GatewaySession: 2h
- Testing criptográfico + edge cases: 3h
- Manejo de errores + fallback graceful: 2h
- **Total: ~33h**

### [2026-02-10] Sistema de Gestión Visual de Skills del Agente con Tienda ClawdHub Integrada, Instalación y Configuración desde la App (Agent Skills Manager & ClawdHub Store)
- **Estado:** propuesta
- **Plataforma:** Android
- **Estimación:** >4h
- **PR:** (pendiente)

Implementar un gestor completo de skills del agente OpenClaw directamente desde la app Android, con navegación por skills instalados, tienda ClawdHub integrada para descubrir e instalar nuevos skills, configuración por skill, y monitorización del uso de cada skill en tiempo real.

**Problema que resuelve:**

OpenClaw funciona con un sistema de "skills" — módulos que dan al agente capacidades específicas (gog para Google, bird para Twitter, weather para clima, etc.). Actualmente, gestionar estos skills requiere:

1. **Acceso SSH al servidor:** Para ver qué skills están instalados, el usuario tiene que hacer SSH al servidor y explorar `~/clawd/skills/` o `~/clawdbot/skills/`. No hay visibilidad desde el móvil.

2. **CLI para instalar/actualizar:** Instalar un skill nuevo requiere ejecutar `clawdhub install <skill>` en la terminal del servidor. Actualizar requiere `clawdhub update`. No hay forma de hacerlo desde la app.

3. **Sin descubrimiento:** No hay manera de navegar el catálogo de skills disponibles en ClawdHub (clawdhub.com) desde la app. El usuario tiene que ir al navegador, buscar, y luego volver a la terminal para instalar.

4. **Configuración dispersa:** Cada skill puede tener configuración (API keys, preferencias, etc.) que se almacena en distintos archivos. No hay una interfaz unificada para configurar skills.

5. **Sin visibilidad del uso:** No hay forma de saber qué skills se usan más, cuáles fallan, o cuándo se usó cada uno por última vez. Esta información está distribuida en los logs del gateway.

6. **Sin gestión de dependencias:** Algunos skills requieren herramientas externas (CLI, tokens, APIs). No hay visibilidad de si estas dependencias están satisfechas.

7. **SKILL.md no accesible:** Cada skill tiene un `SKILL.md` con instrucciones detalladas de uso, pero el usuario no puede consultarlo fácilmente. Tiene que pedirle al agente que lo lea.

Para un power user como Manuel que tiene múltiples skills instalados y quiere mantener su setup optimizado, tener un gestor visual directamente en la app es esencial.

**Funcionalidades propuestas:**

**1. Modelo de datos:**

```kotlin
@Serializable
data class InstalledSkill(
    val name: String,                          // "weather", "bird", "gog"
    val displayName: String?,                  // "Weather", "Bird (Twitter/X)"
    val description: String?,                  // Descripción corta del SKILL.md
    val version: String?,                      // Versión instalada (si disponible)
    val location: String,                      // Path en el servidor: "~/clawd/skills/weather"
    val hasSkillMd: Boolean,                   // Si tiene SKILL.md
    val skillMdContent: String? = null,        // Contenido del SKILL.md (lazy loaded)
    val scripts: List<SkillScript> = emptyList(), // Scripts disponibles
    val configFiles: List<String> = emptyList(),  // Archivos de configuración
    val dependencies: List<SkillDependency> = emptyList(), // Dependencias externas
    val lastUsedAt: Long? = null,              // Última vez que el agente invocó este skill
    val usageCount: Int = 0,                   // Veces usado en las últimas 24h
    val healthStatus: SkillHealthStatus = SkillHealthStatus.UNKNOWN,
    val installedAt: Long? = null,
    val updatedAt: Long? = null,
    val source: SkillSource = SkillSource.LOCAL,
)

@Serializable
data class SkillScript(
    val name: String,                          // "generate_image.py", "gcal"
    val path: String,                          // Path completo
    val language: String?,                     // "python", "bash", "node"
    val description: String?,                  // Si se puede extraer del script
)

@Serializable
data class SkillDependency(
    val name: String,                          // "bird CLI", "gog CLI", "ffmpeg"
    val type: DependencyType,                  // CLI, API_KEY, SERVICE, PYTHON_PACKAGE
    val satisfied: Boolean,                    // Si la dependencia está disponible
    val checkCommand: String?,                 // Comando para verificar: "which bird"
    val installHint: String?,                  // Cómo instalar: "brew install bird"
)

enum class DependencyType { CLI, API_KEY, SERVICE, PYTHON_PACKAGE, NODE_PACKAGE }

enum class SkillHealthStatus {
    HEALTHY,       // Todo OK, dependencias satisfechas
    DEGRADED,      // Funcional pero con warnings (dependencia opcional faltante)
    BROKEN,        // Dependencia crítica faltante
    UNKNOWN,       // No se ha verificado
}

enum class SkillSource {
    LOCAL,         // Creado localmente
    CLAWDHUB,      // Instalado desde ClawdHub
    BUILTIN,       // Viene incluido con OpenClaw
}

@Serializable
data class ClawdHubSkill(
    val name: String,
    val displayName: String?,
    val description: String,
    val author: String?,
    val version: String,
    val downloads: Int?,
    val rating: Float?,
    val tags: List<String> = emptyList(),
    val readme: String? = null,                // README del paquete
    val skillMdPreview: String? = null,        // Preview del SKILL.md
    val dependencies: List<String> = emptyList(),
    val lastPublished: Long?,
    val isInstalled: Boolean = false,
    val installedVersion: String? = null,
    val hasUpdate: Boolean = false,
)

@Serializable
data class SkillUsageEntry(
    val skillName: String,
    val timestamp: Long,
    val sessionKey: String,
    val action: String?,                       // Qué hizo: "read SKILL.md", "exec script"
    val duration: Long?,                       // Milisegundos
    val success: Boolean,
)
```

**2. Comunicación con el Gateway (via node invoke):**

El Skills Manager necesita comunicarse con el gateway para obtener información del filesystem y ejecutar comandos. Se implementaría como una extensión del protocolo de nodo con un nuevo namespace `skills.*`:

```kotlin
// Nuevos comandos de nodo para gestión de skills
enum class OpenClawSkillCommand(val rawValue: String) {
    List("skills.list"),             // Listar skills instalados
    Info("skills.info"),             // Detalles de un skill
    ReadSkillMd("skills.readMd"),    // Leer SKILL.md
    Install("skills.install"),       // Instalar desde ClawdHub
    Update("skills.update"),         // Actualizar skill
    Uninstall("skills.uninstall"),   // Desinstalar skill
    Search("skills.search"),         // Buscar en ClawdHub
    CheckHealth("skills.health"),    // Verificar dependencias
    Usage("skills.usage"),           // Estadísticas de uso
    ;

    companion object {
        const val NamespacePrefix: String = "skills."
    }
}
```

Alternativa sin cambios en el protocolo — usar `agent.request` para pedirle al agente que ejecute los comandos y devuelva JSON estructurado:

```kotlin
class SkillsGatewayBridge(
    private val session: GatewaySession,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listInstalledSkills(): List<InstalledSkill> {
        // Ejecuta en el gateway: ls skills/ + parse SKILL.md headers
        val response = session.request(
            "chat.send",
            buildJsonObject {
                put("sessionKey", JsonPrimitive("__skills_manager__"))
                put("message", JsonPrimitive(
                    """List all installed skills in JSON format. For each skill provide:
                    name, description (from SKILL.md first line), location path, 
                    whether SKILL.md exists, list of scripts, and any config files.
                    Return ONLY valid JSON array."""
                ))
                put("thinking", JsonPrimitive("off"))
                put("internal", JsonPrimitive(true))
            }.toString()
        )
        return json.decodeFromString(response)
    }

    // Alternativa más eficiente: usar exec directamente
    suspend fun listSkillsDirect(): List<InstalledSkill> {
        val scriptResult = session.request(
            "exec.run",
            buildJsonObject {
                put("command", JsonPrimitive("""
                    for dir in ~/clawd/skills/*/; do
                        name=$(basename "${'$'}dir")
                        has_md=$([ -f "${'$'}dir/SKILL.md" ] && echo "true" || echo "false")
                        desc=""
                        if [ -f "${'$'}dir/SKILL.md" ]; then
                            desc=$(head -5 "${'$'}dir/SKILL.md" | grep -i "description" | head -1 | sed 's/.*description[: ]*//')
                        fi
                        scripts=$(find "${'$'}dir/scripts" -type f 2>/dev/null | while read f; do echo "$(basename ${'$'}f)"; done | tr '\n' ',')
                        echo "{\"name\":\"${'$'}name\",\"location\":\"${'$'}dir\",\"hasSkillMd\":${'$'}has_md,\"description\":\"${'$'}desc\",\"scripts\":\"${'$'}scripts\"}"
                    done
                """.trimIndent()))
            }.toString()
        )
        // Parse NDJSON response
        return scriptResult.lines()
            .filter { it.startsWith("{") }
            .mapNotNull { line ->
                try { json.decodeFromString<InstalledSkill>(line) }
                catch (_: Throwable) { null }
            }
    }

    suspend fun installSkill(name: String): Result<String> {
        val response = session.request(
            "exec.run",
            buildJsonObject {
                put("command", JsonPrimitive("cd ~/clawd && clawdhub install $name 2>&1"))
                put("timeout", JsonPrimitive(60))
            }.toString()
        )
        return Result.success(response)
    }

    suspend fun updateSkill(name: String): Result<String> {
        val response = session.request(
            "exec.run",
            buildJsonObject {
                put("command", JsonPrimitive("cd ~/clawd && clawdhub update $name 2>&1"))
                put("timeout", JsonPrimitive(60))
            }.toString()
        )
        return Result.success(response)
    }

    suspend fun searchClawdHub(query: String): List<ClawdHubSkill> {
        val response = session.request(
            "exec.run",
            buildJsonObject {
                put("command", JsonPrimitive("cd ~/clawd && clawdhub search '$query' --json 2>&1"))
                put("timeout", JsonPrimitive(30))
            }.toString()
        )
        return json.decodeFromString(response)
    }

    suspend fun readSkillMd(skillName: String): String? {
        val response = session.request(
            "exec.run",
            buildJsonObject {
                put("command", JsonPrimitive("cat ~/clawd/skills/$skillName/SKILL.md 2>/dev/null || cat ~/clawdbot/skills/$skillName/SKILL.md 2>/dev/null"))
            }.toString()
        )
        return response.takeIf { it.isNotBlank() }
    }

    suspend fun checkSkillHealth(skillName: String): SkillHealthStatus {
        // Lee el SKILL.md, extrae dependencias, y verifica cada una
        val response = session.request(
            "exec.run",
            buildJsonObject {
                put("command", JsonPrimitive("""
                    skill_dir="${'$'}(find ~/clawd/skills ~/clawdbot/skills -maxdepth 1 -name "$skillName" -type d 2>/dev/null | head -1)"
                    [ -z "${'$'}skill_dir" ] && echo "NOT_FOUND" && exit 1
                    
                    # Check for common dependencies
                    health="HEALTHY"
                    if grep -qi "bird\|twitter" "${'$'}skill_dir/SKILL.md" 2>/dev/null; then
                        which bird >/dev/null 2>&1 || health="DEGRADED"
                    fi
                    if grep -qi "ffmpeg" "${'$'}skill_dir/SKILL.md" 2>/dev/null; then
                        which ffmpeg >/dev/null 2>&1 || health="BROKEN"
                    fi
                    if grep -qi "gog\|google" "${'$'}skill_dir/SKILL.md" 2>/dev/null; then
                        which gog >/dev/null 2>&1 || health="DEGRADED"
                    fi
                    echo "${'$'}health"
                """.trimIndent()))
            }.toString()
        )
        return when (response.trim()) {
            "HEALTHY" -> SkillHealthStatus.HEALTHY
            "DEGRADED" -> SkillHealthStatus.DEGRADED
            "BROKEN" -> SkillHealthStatus.BROKEN
            else -> SkillHealthStatus.UNKNOWN
        }
    }
}
```

**3. Pantalla principal de Skills (SkillsScreen):**

```
┌─────────────────────────────────────────────────────────────┐
│ ← Skills Manager                              [🔍] [🏪]    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ INSTALLED (14 skills)                    [↻ Check updates]  │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🐦 bird                                     ● Healthy  │ │
│ │    Twitter/X CLI - Read, search, post         v1.2.3    │ │
│ │    Last used: 2h ago · Used 12x today                   │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 📧 gog                                      ● Healthy  │ │
│ │    Google Workspace - Gmail, Calendar, Drive  v2.0.1    │ │
│ │    Last used: 35 min ago · Used 8x today                │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🌤️ weather                                   ● Healthy  │ │
│ │    Weather forecasts (no API key needed)      v1.0.0    │ │
│ │    Last used: 6h ago · Used 2x today                    │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🍌 nano-banana-pro                           ○ Degraded │ │
│ │    Image generation via Gemini 3 Pro          v1.1.0    │ │
│ │    ⚠️ API key may need refresh                          │ │
│ │    Last used: Yesterday · Used 0x today                 │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🎬 video-frames                              ● Healthy  │ │
│ │    Extract frames/clips from videos           v1.0.0    │ │
│ │    Last used: 3 days ago · Used 0x today                │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🔍 summarize                                 ● Healthy  │ │
│ │    Summarize URLs, podcasts, videos           v1.0.2    │ │
│ │    Last used: Yesterday · Used 1x today                 │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🐙 github                                    ● Healthy  │ │
│ │    GitHub CLI integration                     v1.3.0    │ │
│ │    Last used: Today 10:30 · Used 5x today              │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 💡 coding-agent                              ● Healthy  │ │
│ │    Run Codex, Claude Code, etc.               v1.0.1    │ │
│ │    Last used: 2 days ago · Used 0x today                │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🎞️ gifgrep                                   ● Healthy  │ │
│ │    Search GIF providers                       v1.0.0    │ │
│ │    Last used: 5 days ago · Used 0x today                │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── USAGE SUMMARY (Last 24h) ─────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🐦 bird ████████████████████ 12                         │ │
│ │ 📧 gog  ████████████████ 8                              │ │
│ │ 🐙 github ██████████ 5                                  │ │
│ │ 🌤️ weather ████ 2                                       │ │
│ │ 🔍 summarize ██ 1                                       │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**4. Detalle de un Skill (SkillDetailScreen):**

```
┌─────────────────────────────────────────────────────────────┐
│ ← bird                                    [↻ Update] [⋮]  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 🐦 bird - Twitter/X CLI                                    │
│ Twitter/X CLI for reading, searching, and posting           │
│ via cookies or Sweetistics.                                 │
│                                                             │
│ Version: 1.2.3 · Source: ClawdHub                           │
│ Location: ~/clawd/skills/bird                               │
│ Installed: Jan 27, 2026 · Updated: Feb 5, 2026             │
│                                                             │
│ ● Health: Healthy                                           │
│   ✓ bird CLI available                                      │
│   ✓ Twitter cookies valid                                   │
│   ✓ Chromium session active                                 │
│                                                             │
│ ─── USAGE ──────────────────────────────────────────────── │
│ Today: 12 invocations · Avg response: 2.3s                 │
│ This week: 67 invocations                                   │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Lun Mar Mié Jue Vie Sáb Dom                            │ │
│ │  8   12  15   9   7   3   2     ← heatmap semanal      │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── SKILL.MD ───────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ # Bird Skill                                            │ │
│ │                                                         │ │
│ │ Twitter/X CLI for reading, searching, and posting.      │ │
│ │                                                         │ │
│ │ ## Prerequisites                                        │ │
│ │ - `bird` CLI installed (steipete/tap/bird)              │ │
│ │ - Valid Twitter cookies in environment                   │ │
│ │                                                         │ │
│ │ ## Commands                                             │ │
│ │ ```bash                                                 │ │
│ │ bird timeline -n 20      # Get timeline                 │ │
│ │ bird search "query" -n 5 # Search tweets               │ │
│ │ bird tweet "message"     # Post tweet                   │ │
│ │ ```                                                     │ │
│ │                                            [Show full ↓] │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── SCRIPTS ────────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 📄 refresh-twitter.sh              bash     [View]      │ │
│ │ 📄 twitter-cookies.js              node     [View]      │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── DEPENDENCIES ───────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ✓ bird CLI              /usr/local/bin/bird    v0.9.2  │ │
│ │ ✓ Chromium              systemd service        active  │ │
│ │ ✓ Twitter Cookies       Environment vars       valid   │ │
│ │ ✓ x11vnc (optional)     /usr/bin/x11vnc       v0.9.16 │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── RECENT INVOCATIONS ─────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 14:23  bird timeline -n 20              ✓ 1.8s  main   │ │
│ │ 13:45  bird search "AI news" -n 5       ✓ 2.1s  main   │ │
│ │ 12:00  bird timeline -n 10              ✓ 1.5s  main   │ │
│ │ 10:30  bird tweet "Buenos días..."      ✓ 3.2s  main   │ │
│ │                                         [Show all →]    │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── ACTIONS ────────────────────────────────────────────── │
│ [🔄 Update to v1.2.4]  [🔧 Run Health Check]              │
│ [📋 Copy SKILL.MD]     [🗑️ Uninstall]                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**5. Tienda ClawdHub (ClawdHubStoreScreen):**

```
┌─────────────────────────────────────────────────────────────┐
│ ← ClawdHub Store                                    [🔍]   │
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🔍 Search skills...                                     │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ CATEGORIES                                                  │
│ [All] [Social] [Productivity] [Media] [Dev Tools] [IoT]    │
│                                                             │
│ ─── FEATURED ───────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🆕 home-assistant        Smart Home Integration  v2.1   │ │
│ │    Control HA devices via OpenClaw agent                 │ │
│ │    ⭐ 4.8 · 1.2k downloads            [Install]         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🆕 spotify               Music Control          v1.5   │ │
│ │    Control Spotify playback and playlists                │ │
│ │    ⭐ 4.5 · 890 downloads             [Install]         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 📦 obsidian              Note-taking Bridge      v1.2   │ │
│ │    Sync and search Obsidian vaults                       │ │
│ │    ⭐ 4.7 · 650 downloads             [Install]         │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── RECENTLY UPDATED ───────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🐦 bird                  Twitter/X CLI          v1.2.4  │ │
│ │    Updated 2 days ago                 [✓ Installed]      │ │
│ │    🔄 Update available (v1.2.3 → v1.2.4)                │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 📧 gog                   Google Workspace       v2.0.2  │ │
│ │    Updated 1 week ago                 [✓ Installed]      │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── POPULAR ────────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 📝 notion                Notion Integration      v1.8   │ │
│ │    Read, create, and update Notion pages                 │ │
│ │    ⭐ 4.6 · 2.3k downloads            [Install]         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 📊 grafana               Dashboard Monitoring    v1.1   │ │
│ │    Query Grafana dashboards and alerts                   │ │
│ │    ⭐ 4.3 · 420 downloads             [Install]         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🏠 homekit               HomeKit Bridge          v0.9   │ │
│ │    Control HomeKit devices directly                      │ │
│ │    ⭐ 4.1 · 380 downloads             [Install]         │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**6. Detalle de skill en ClawdHub (ClawdHubSkillDetailSheet):**

```
┌─────────────────────────────────────────────────────────────┐
│ home-assistant                                      [Close] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 🏠 home-assistant                                           │
│ Smart Home Integration for OpenClaw                         │
│                                                             │
│ Author: @clawdhub-official · v2.1.0                         │
│ Published: Feb 8, 2026 · 1.2k downloads                    │
│ ⭐⭐⭐⭐⭐ 4.8 (48 ratings)                                   │
│                                                             │
│ Tags: [smart-home] [iot] [automation] [home-assistant]     │
│                                                             │
│ ─── DESCRIPTION ────────────────────────────────────────── │
│ Control your Home Assistant instance through your           │
│ OpenClaw agent. Supports:                                   │
│ - Listing and controlling devices (lights, switches,        │
│   thermostats, covers, etc.)                                │
│ - Running automations and scenes                            │
│ - Querying sensor states and history                        │
│ - Creating and managing automations                         │
│                                                             │
│ ─── REQUIREMENTS ───────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ • Home Assistant instance (local or Nabu Casa)          │ │
│ │ • Long-lived access token from HA                       │ │
│ │ • Network access to HA API (http://ha-ip:8123)         │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ─── CHANGELOG ──────────────────────────────────────────── │
│ v2.1.0 - Added support for climate entities                 │
│ v2.0.0 - Rewritten with REST API (was websocket)           │
│ v1.5.0 - Added scene support                               │
│                                                [Show more ↓]│
│                                                             │
│ ─── PREVIEW ────────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ # Home Assistant Skill                                  │ │
│ │                                                         │ │
│ │ ## Setup                                                │ │
│ │ 1. Get a long-lived access token from HA Settings       │ │
│ │ 2. Set HA_URL and HA_TOKEN in your environment          │ │
│ │                                                         │ │
│ │ ## Usage                                                │ │
│ │ "Turn on the living room lights"                        │ │
│ │ "What's the temperature in the bedroom?"                │ │
│ │ "Run the good night scene"                              │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│           [📦 Install home-assistant]                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**7. ViewModel y lógica de negocio:**

```kotlin
class SkillsViewModel(
    private val bridge: SkillsGatewayBridge,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _installedSkills = MutableStateFlow<List<InstalledSkill>>(emptyList())
    val installedSkills: StateFlow<List<InstalledSkill>> = _installedSkills.asStateFlow()

    private val _storeSkills = MutableStateFlow<List<ClawdHubSkill>>(emptyList())
    val storeSkills: StateFlow<List<ClawdHubSkill>> = _storeSkills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedSkill = MutableStateFlow<InstalledSkill?>(null)
    val selectedSkill: StateFlow<InstalledSkill?> = _selectedSkill.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterCategory = MutableStateFlow<String?>(null)
    val filterCategory: StateFlow<String?> = _filterCategory.asStateFlow()

    // Filtered and sorted skills
    val displayedSkills: StateFlow<List<InstalledSkill>> = combine(
        _installedSkills, _searchQuery, _filterCategory
    ) { skills, query, category ->
        skills.filter { skill ->
            val matchesQuery = query.isBlank() ||
                skill.name.contains(query, ignoreCase = true) ||
                skill.description?.contains(query, ignoreCase = true) == true
            val matchesCategory = category == null || skill.matchesCategory(category)
            matchesQuery && matchesCategory
        }.sortedByDescending { it.usageCount }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadInstalledSkills() {
        scope.launch {
            _isLoading.value = true
            try {
                _installedSkills.value = bridge.listSkillsDirect()

                // Check health for each skill in background
                _installedSkills.value.forEach { skill ->
                    launch {
                        val health = bridge.checkSkillHealth(skill.name)
                        _installedSkills.value = _installedSkills.value.map {
                            if (it.name == skill.name) it.copy(healthStatus = health) else it
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSkillDetail(name: String) {
        scope.launch {
            val skill = _installedSkills.value.find { it.name == name } ?: return@launch
            val md = bridge.readSkillMd(name)
            _selectedSkill.value = skill.copy(skillMdContent = md)
        }
    }

    fun installSkill(name: String) {
        scope.launch {
            _installProgress.value = InstallProgress(name, InstallState.DOWNLOADING)
            try {
                val result = bridge.installSkill(name)
                _installProgress.value = InstallProgress(name, InstallState.COMPLETED)
                delay(2000)
                _installProgress.value = null
                loadInstalledSkills() // Refresh list
            } catch (e: Exception) {
                _installProgress.value = InstallProgress(name, InstallState.FAILED, e.message)
            }
        }
    }

    fun updateSkill(name: String) {
        scope.launch {
            _installProgress.value = InstallProgress(name, InstallState.UPDATING)
            try {
                bridge.updateSkill(name)
                _installProgress.value = InstallProgress(name, InstallState.COMPLETED)
                delay(2000)
                _installProgress.value = null
                loadInstalledSkills()
            } catch (e: Exception) {
                _installProgress.value = InstallProgress(name, InstallState.FAILED, e.message)
            }
        }
    }

    fun updateAllSkills() {
        scope.launch {
            _installProgress.value = InstallProgress("all", InstallState.UPDATING)
            try {
                bridge.updateAllSkills()
                _installProgress.value = InstallProgress("all", InstallState.COMPLETED)
                delay(2000)
                _installProgress.value = null
                loadInstalledSkills()
            } catch (e: Exception) {
                _installProgress.value = InstallProgress("all", InstallState.FAILED, e.message)
            }
        }
    }

    fun searchStore(query: String) {
        _searchQuery.value = query
        scope.launch {
            try {
                val results = bridge.searchClawdHub(query)
                // Mark installed skills
                val installedNames = _installedSkills.value.map { it.name }.toSet()
                _storeSkills.value = results.map { skill ->
                    skill.copy(
                        isInstalled = skill.name in installedNames,
                        installedVersion = _installedSkills.value
                            .find { it.name == skill.name }?.version,
                        hasUpdate = skill.name in installedNames &&
                            _installedSkills.value.find { it.name == skill.name }
                                ?.version != skill.version,
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun uninstallSkill(name: String) {
        scope.launch {
            try {
                bridge.uninstallSkill(name)
                loadInstalledSkills()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

data class InstallProgress(
    val skillName: String,
    val state: InstallState,
    val errorMessage: String? = null,
)

enum class InstallState {
    DOWNLOADING,
    INSTALLING,
    UPDATING,
    COMPLETED,
    FAILED,
}
```

**Arquitectura propuesta:**

```
┌─────────────────────────────────────────────────────────────┐
│                       SkillsScreen                          │
│  (Lista de skills instalados + barra de búsqueda)          │
├───────────────┬─────────────────────────────────────────────┤
│ SkillDetail   │    ClawdHubStoreScreen                      │
│ Screen        │    (Tienda de skills)                       │
├───────────────┴─────────────────────────────────────────────┤
│                    SkillsViewModel                           │
│  (CRUD, búsqueda, instalación, health checks)              │
├─────────────────────────────────────────────────────────────┤
│                  SkillsGatewayBridge                         │
│  (Comunicación con gateway via exec/request)               │
├─────────────────────────────────────────────────────────────┤
│                     GatewaySession                          │
│  (WebSocket al gateway OpenClaw)                            │
└─────────────────────────────────────────────────────────────┘
```

**Archivos nuevos:**

```
app/src/main/java/ai/openclaw/android/skills/
├── InstalledSkill.kt                // Modelos de datos
├── ClawdHubSkill.kt                 // Modelo para skills del store
├── SkillsGatewayBridge.kt           // Comunicación con gateway
├── SkillsViewModel.kt               // ViewModel principal

app/src/main/java/ai/openclaw/android/ui/skills/
├── SkillsScreen.kt                  // Lista principal de skills
├── SkillCard.kt                     // Card individual de skill
├── SkillDetailScreen.kt             // Vista detallada de un skill
├── SkillMdViewer.kt                 // Renderizador de SKILL.md (markdown)
├── SkillHealthBadge.kt              // Badge de estado de salud
├── SkillUsageChart.kt               // Gráfico de uso (barras/heatmap)
├── ClawdHubStoreScreen.kt           // Tienda de ClawdHub
├── ClawdHubSkillCard.kt             // Card de skill en la tienda
├── ClawdHubSkillDetailSheet.kt      // Detalle en bottom sheet
├── InstallProgressDialog.kt         // Diálogo de progreso de instalación
├── SkillSearchBar.kt                // Barra de búsqueda con filtros
└── SkillCategoryChips.kt            // Chips de categorías
```

**Archivos modificados:**

- `RootScreen.kt` — Añadir botón de acceso a Skills Manager (nuevo OverlayIconButton)
- `SettingsSheet.kt` — Enlace a Skills Manager en la sección de configuración
- `NodeRuntime.kt` — Inicializar SkillsGatewayBridge
- `MainViewModel.kt` — Exponer SkillsViewModel
- `build.gradle.kts` — Dependencias para charts (MPAndroidChart o Compose Charts)

**Dependencias nuevas:**

```kotlin
// Gráficos para visualización de uso
implementation("io.github.bytebeats:compose-charts:0.2.1")

// Markdown rendering para SKILL.md (si no existe ya)
// Puede reusar el renderizador de ChatMarkdown existente
```

**Comunicación con el gateway — dos estrategias:**

**Estrategia A: Via exec (funciona con gateway actual sin cambios):**
- Ejecutar scripts shell en el servidor para listar skills, leer SKILL.md, verificar dependencias
- Usar `clawdhub` CLI para buscar/instalar/actualizar
- Parsing de output a JSON en la app

**Estrategia B: Nuevo namespace de nodo (requiere cambios en OpenClaw):**
- Definir comandos `skills.list`, `skills.install`, etc. en el protocolo
- El gateway responde con JSON estructurado nativamente
- Más robusto pero requiere PRs al core de OpenClaw

La implementación inicial usaría Estrategia A (funciona sin cambios en OpenClaw), con migración futura a Estrategia B.

**Integración con el flujo existente:**

1. El botón "Skills" aparece en la toolbar lateral (junto a Chat, Talk Mode, Settings)
2. Al abrir, carga la lista de skills del servidor
3. Tap en un skill → detalle con SKILL.md, scripts, dependencias, uso
4. Botón "Store" → abre la tienda ClawdHub
5. Instalación/actualización muestra progreso con diálogo
6. Desinstalación requiere confirmación
7. Health checks se ejecutan en background al abrir la pantalla

**Consideraciones de UX:**

- **Lazy loading:** SKILL.md y detalles se cargan bajo demanda
- **Caché:** Lista de skills instalados se cachea en SharedPreferences
- **Offline:** Muestra la última lista conocida cuando no hay conexión
- **Feedback:** Haptic feedback en acciones de instalar/actualizar/desinstalar
- **Empty state:** Pantalla vacía con CTA para explorar la tienda
- **Actualizaciones:** Badge rojo en el botón si hay updates disponibles

**Diferencia con propuestas existentes:**

- **Remote File Browser:** Navegación genérica de archivos. Skills Manager es específico para skills con UI dedicada, health checks, tienda integrada, y estadísticas de uso.
- **Gateway Admin Console:** Enfocado en monitorización y configuración del gateway. Skills Manager es sobre gestión del ecosistema de skills.
- **Dynamic Node Command Extensions:** Plugin API para extender el nodo. Skills Manager gestiona skills del agente (server-side).

**Por qué es útil para Manuel:**

Como power user que:
- 🔧 **Mantiene múltiples skills:** Necesita saber cuáles están actualizados, cuáles fallan
- 📦 **Instala nuevos skills frecuentemente:** Descubrir e instalar desde ClawdHub sin SSH
- 🔍 **Depura problemas:** Saber si un skill falla por dependencia faltante o configuración errónea
- 📊 **Optimiza su setup:** Ver qué skills usa más y cuáles podría desinstalar
- 📱 **Prefiere el móvil:** Gestionar todo desde la app sin abrir terminal

El Skills Manager convierte la gestión de skills de una tarea de terminal/SSH a una experiencia visual e intuitiva integrada en la app.

**Estimación de tiempo:**
- Modelos de datos (InstalledSkill, ClawdHubSkill, etc.): 1h
- SkillsGatewayBridge (comunicación con gateway): 3h
- SkillsViewModel (lógica de negocio): 2.5h
- SkillsScreen + SkillCard: 2h
- SkillDetailScreen + SkillMdViewer: 3h
- SkillHealthBadge + health checks: 1.5h
- SkillUsageChart (gráficos de uso): 2h
- ClawdHubStoreScreen + ClawdHubSkillCard: 2.5h
- ClawdHubSkillDetailSheet: 1.5h
- InstallProgressDialog + acciones: 1.5h
- Búsqueda + filtros + categorías: 1.5h
- Integración con RootScreen/Settings: 1h
- Caché + persistencia: 1h
- Testing + edge cases: 2.5h
- **Total: ~27h**

### [2026-02-10] Dashboard de Cámaras IP con Visor RTSP Nativo y Control del Agente (Live IP Camera Dashboard)
- **Estado:** propuesta
- **Plataforma:** Android
- **Estimación:** >4h
- **PR:** (pendiente)

Implementar un dashboard de cámaras IP integrado en la app Android que permita visualizar feeds RTSP en vivo de cámaras de red domésticas (Tapo, Hikvision, Dahua, ONVIF genéricas), tomar snapshots y grabaciones desde la app, y exponer las cámaras como capacidad de nodo para que el agente pueda solicitar capturas bajo demanda sin depender de ffmpeg en el servidor.

**Problema que resuelve:**

Actualmente, para ver la cámara Tapo C200 del salón (o cualquier cámara IP), Manuel necesita:

1. **App oficial del fabricante (Tapo):** Interfaz lenta, llena de publicidad y funciones innecesarias. Cada fabricante tiene su propia app, fragmentando la experiencia.

2. **Captura via ffmpeg en el servidor:** La única forma actual de obtener un frame de la cámara es ejecutar `ffmpeg -rtsp_transport tcp -i "rtsp://..." -frames:v 1 /tmp/snapshot.jpg` en la Pi, convertir a base64, y enviar por Telegram. Es funcional pero lento (~3-5s por snapshot) y requiere que el gateway esté activo.

3. **Sin visión directa desde el móvil:** No hay forma de echar un vistazo rápido al salón desde el teléfono sin salir de OpenClaw. La app actual maneja la cámara del teléfono (CameraCaptureManager) pero no tiene concepto de cámaras de red externas.

4. **Sin integración inteligente:** No hay alertas de movimiento procesadas por el agente, no hay grabación automática, no hay forma de decir "muéstrame el salón" y verlo directamente en la app.

5. **Dependencia del servidor:** Toda interacción con las cámaras pasa por la Pi. Si el gateway está caído o la Pi se reinicia, no hay acceso a las cámaras. El teléfono podría conectarse directamente cuando está en la misma red WiFi.

Para un power user de domótica como Manuel que ya tiene cámaras IP configuradas, tener un visor nativo dentro de OpenClaw elimina la necesidad de apps de terceros y convierte su asistente en un verdadero centro de control del hogar.

**Funcionalidades propuestas:**

**1. Modelo de datos de cámaras:**

```kotlin
@Serializable
data class IpCamera(
    val id: String = UUID.randomUUID().toString(),
    val name: String,                          // "Salón", "Entrada", "Terraza"
    val rtspUrl: String,                       // rtsp://user:pass@ip:port/stream
    val rtspUrlLow: String? = null,            // Stream de baja resolución (stream2)
    val thumbnailBase64: String? = null,       // Última miniatura capturada
    val lastSeenAtMs: Long? = null,            // Última vez que respondió
    val enabled: Boolean = true,
    val order: Int = 0,                        // Orden en el dashboard
    val settings: CameraSettings = CameraSettings(),
)

@Serializable
data class CameraSettings(
    val transport: RtspTransport = RtspTransport.TCP,
    val autoRefreshThumbnailMs: Long = 30_000,  // Cada 30s en dashboard
    val motionDetection: Boolean = false,        // Detección de movimiento on-device
    val motionSensitivity: Float = 0.5f,         // 0.0 - 1.0
    val motionNotify: Boolean = false,           // Enviar notificación al agente
    val recordOnMotion: Boolean = false,         // Grabar clip al detectar movimiento
    val maxRecordDurationMs: Long = 30_000,      // Duración máxima del clip
    val preferLowStream: Boolean = true,         // Usar stream2 para dashboard
    val audioEnabled: Boolean = false,           // Habilitar audio en el stream
)

enum class RtspTransport { TCP, UDP }
```

**2. Motor RTSP (RtspStreamEngine):**

```kotlin
class RtspStreamEngine(
    private val scope: CoroutineScope,
) {
    sealed class StreamState {
        object Idle : StreamState()
        object Connecting : StreamState()
        data class Playing(val width: Int, val height: Int, val fps: Float) : StreamState()
        data class Error(val message: String) : StreamState()
    }

    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private var streamJob: Job? = null
    private var decoder: MediaCodec? = null
    private var surface: Surface? = null

    /**
     * Conecta al stream RTSP y decodifica frames H.264/H.265.
     * Usa ExoPlayer internamente para manejo robusto de RTSP.
     */
    fun startStream(
        rtspUrl: String,
        transport: RtspTransport = RtspTransport.TCP,
        surface: Surface? = null,
        onFrame: ((Bitmap) -> Unit)? = null,
    ) {
        stopStream()
        _state.value = StreamState.Connecting

        streamJob = scope.launch(Dispatchers.IO) {
            try {
                val player = ExoPlayer.Builder(context)
                    .build()
                    .apply {
                        val rtspSource = RtspMediaSource.Factory()
                            .setForceUseRtpTcp(transport == RtspTransport.TCP)
                            .setTimeoutMs(10_000)
                            .createMediaSource(MediaItem.fromUri(rtspUrl))
                        setMediaSource(rtspSource)
                        prepare()
                        playWhenReady = true
                    }

                // Si necesitamos frames como Bitmap (para thumbnails/motion detection)
                if (onFrame != null) {
                    // Usar ImageReader para capturar frames
                    // ...
                }

                _state.value = StreamState.Playing(
                    width = player.videoFormat?.width ?: 0,
                    height = player.videoFormat?.height ?: 0,
                    fps = player.videoFormat?.frameRate ?: 0f,
                )
            } catch (e: Exception) {
                _state.value = StreamState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        _state.value = StreamState.Idle
        _currentFrame.value = null
    }

    /**
     * Captura un snapshot del frame actual.
     * Si no hay stream activo, conecta brevemente para capturar 1 frame.
     */
    suspend fun captureSnapshot(
        rtspUrl: String,
        transport: RtspTransport = RtspTransport.TCP,
        maxWidth: Int = 1920,
        quality: Int = 85,
    ): CameraSnapshot {
        val bitmap = if (_state.value is StreamState.Playing && _currentFrame.value != null) {
            _currentFrame.value!!
        } else {
            // Conexión rápida para snapshot único
            captureOneFrame(rtspUrl, transport, timeoutMs = 8_000)
        }

        val scaled = JpegSizeLimiter.scaleDown(bitmap, maxWidth)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        return CameraSnapshot(
            base64 = base64,
            width = scaled.width,
            height = scaled.height,
            timestampMs = System.currentTimeMillis(),
            mimeType = "image/jpeg",
        )
    }

    /**
     * Graba un clip del stream a un archivo MP4.
     */
    suspend fun recordClip(
        rtspUrl: String,
        transport: RtspTransport = RtspTransport.TCP,
        durationMs: Long = 10_000,
        includeAudio: Boolean = false,
        outputFile: File,
    ): RecordedClip {
        // Usa ExoPlayer + Muxer para grabar directamente
        // sin re-encoding (remux H.264 → MP4)
        // ...
    }
}

data class CameraSnapshot(
    val base64: String,
    val width: Int,
    val height: Int,
    val timestampMs: Long,
    val mimeType: String,
)

data class RecordedClip(
    val file: File,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)
```

**3. Detector de movimiento on-device (MotionDetector):**

```kotlin
class MotionDetector(
    private val sensitivity: Float = 0.5f,
    private val minAreaPercent: Float = 0.02f,
) {
    private var referenceFrame: Bitmap? = null
    private var lastMotionAtMs: Long = 0
    private val cooldownMs = 5_000L

    data class MotionResult(
        val detected: Boolean,
        val changePercent: Float,    // 0.0 - 1.0
        val regions: List<Rect>,     // Zonas con movimiento
        val timestampMs: Long,
    )

    /**
     * Compara el frame actual con el de referencia usando
     * diferencia absoluta de píxeles en escala de grises.
     * Liviano y sin dependencias ML.
     */
    fun analyze(frame: Bitmap): MotionResult {
        val now = System.currentTimeMillis()
        val ref = referenceFrame

        if (ref == null) {
            referenceFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
            return MotionResult(false, 0f, emptyList(), now)
        }

        // Escalar a resolución baja para eficiencia
        val scale = 160f / maxOf(frame.width, frame.height)
        val smallCurrent = Bitmap.createScaledBitmap(
            frame,
            (frame.width * scale).toInt(),
            (frame.height * scale).toInt(),
            true,
        )
        val smallRef = Bitmap.createScaledBitmap(
            ref,
            (ref.width * scale).toInt(),
            (ref.height * scale).toInt(),
            true,
        )

        var changedPixels = 0
        val totalPixels = smallCurrent.width * smallCurrent.height
        val threshold = ((1f - sensitivity) * 80 + 20).toInt()  // 20-100

        for (y in 0 until smallCurrent.height) {
            for (x in 0 until smallCurrent.width) {
                val c1 = smallCurrent.getPixel(x, y)
                val c2 = smallRef.getPixel(x, y)
                val diff = maxOf(
                    Math.abs(Color.red(c1) - Color.red(c2)),
                    Math.abs(Color.green(c1) - Color.green(c2)),
                    Math.abs(Color.blue(c1) - Color.blue(c2)),
                )
                if (diff > threshold) changedPixels++
            }
        }

        val changePercent = changedPixels.toFloat() / totalPixels
        val detected = changePercent > minAreaPercent &&
            (now - lastMotionAtMs > cooldownMs)

        if (detected) {
            lastMotionAtMs = now
        }

        // Actualizar frame de referencia gradualmente
        referenceFrame = frame.copy(Bitmap.Config.ARGB_8888, false)

        return MotionResult(
            detected = detected,
            changePercent = changePercent,
            regions = emptyList(),  // Simplificado, se pueden añadir regiones
            timestampMs = now,
        )
    }

    fun reset() {
        referenceFrame = null
    }
}
```

**4. Gestión de cámaras (IpCameraStore):**

```kotlin
class IpCameraStore(private val prefs: SecurePrefs) {
    // Las credenciales RTSP se almacenan en EncryptedSharedPreferences
    // (ya usado por SecurePrefs)

    private val _cameras = MutableStateFlow<List<IpCamera>>(emptyList())
    val cameras: StateFlow<List<IpCamera>> = _cameras.asStateFlow()

    fun loadAll(): List<IpCamera> { ... }
    fun save(camera: IpCamera) { ... }
    fun delete(cameraId: String) { ... }
    fun reorder(orderedIds: List<String>) { ... }

    /**
     * Sincroniza cámaras desde la config del gateway.
     * Si el gateway tiene cámaras configuradas (como en TOOLS.md),
     * las importa automáticamente.
     */
    suspend fun syncFromGateway(session: GatewaySession) {
        // El gateway puede exponer cámaras conocidas via config.get
        // o un endpoint dedicado "cameras.list"
    }
}
```

**5. Capacidad de nodo para cámaras IP (IpCameraNodeCapability):**

```kotlin
/**
 * Expone las cámaras IP como comando de nodo invocable por el agente.
 * Esto permite al agente pedir snapshots de cámaras IP sin pasar por
 * ffmpeg en el servidor — el teléfono conecta directamente al RTSP.
 *
 * Comandos:
 *   ip_camera.list    → Lista de cámaras configuradas
 *   ip_camera.snap    → Captura un frame de una cámara específica
 *   ip_camera.status  → Estado de conexión de cada cámara
 */
class IpCameraNodeCapability(
    private val store: IpCameraStore,
    private val engine: RtspStreamEngine,
) {
    suspend fun handleInvoke(command: String, paramsJson: String?): GatewaySession.InvokeResult {
        return when (command) {
            "ip_camera.list" -> {
                val cameras = store.cameras.value.map { cam ->
                    buildJsonObject {
                        put("id", JsonPrimitive(cam.id))
                        put("name", JsonPrimitive(cam.name))
                        put("enabled", JsonPrimitive(cam.enabled))
                        put("lastSeenAtMs", JsonPrimitive(cam.lastSeenAtMs ?: 0))
                    }
                }
                GatewaySession.InvokeResult.ok(
                    JsonArray(cameras).toString()
                )
            }
            "ip_camera.snap" -> {
                val params = parseParams(paramsJson)
                val cameraId = params?.get("cameraId")?.asStringOrNull()
                    ?: params?.get("name")?.asStringOrNull()
                val camera = findCamera(cameraId)
                    ?: return GatewaySession.InvokeResult.error(
                        "CAMERA_NOT_FOUND",
                        "No camera found with id or name: $cameraId"
                    )

                try {
                    val snapshot = engine.captureSnapshot(
                        rtspUrl = camera.rtspUrl,
                        transport = camera.settings.transport,
                    )
                    GatewaySession.InvokeResult.ok(
                        buildJsonObject {
                            put("base64", JsonPrimitive(snapshot.base64))
                            put("mimeType", JsonPrimitive(snapshot.mimeType))
                            put("width", JsonPrimitive(snapshot.width))
                            put("height", JsonPrimitive(snapshot.height))
                            put("cameraName", JsonPrimitive(camera.name))
                        }.toString()
                    )
                } catch (e: Exception) {
                    GatewaySession.InvokeResult.error(
                        "CAMERA_UNAVAILABLE",
                        "Failed to capture from ${camera.name}: ${e.message}"
                    )
                }
            }
            "ip_camera.status" -> {
                // ... estado de cada cámara
                GatewaySession.InvokeResult.ok("{}")
            }
            else -> GatewaySession.InvokeResult.error(
                "INVALID_REQUEST",
                "Unknown ip_camera command: $command"
            )
        }
    }
}
```

**6. Dashboard UI (CameraDashboardScreen):**

```
┌─────────────────────────────────────────────────────────────┐
│ ← 📷 Cameras                                     [⚙️] [+] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ ████████████████████ │  │ ████████████████████ │        │
│  │ ████ SALÓN █████████ │  │ ████ ENTRADA ██████ │        │
│  │ ████████████████████ │  │ ██ (Sin conexión) ██ │        │
│  │ ████████████████████ │  │ ████████████████████ │        │
│  │                      │  │                      │        │
│  │ ● Live    HD  18:45  │  │ ○ Offline     18:30  │        │
│  └──────────────────────┘  └──────────────────────┘        │
│                                                             │
│  ┌──────────────────────┐                                   │
│  │ ████████████████████ │                                   │
│  │ ████ TERRAZA ███████ │                                   │
│  │ ████████████████████ │                                   │
│  │ ████████████████████ │                                   │
│  │                      │                                   │
│  │ ● Live    SD  18:45  │                                   │
│  └──────────────────────┘                                   │
│                                                             │
│ RECENT EVENTS                                    [See all]  │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🔴 18:42  Salón        Movement detected                │ │
│ │ 📸 18:30  Entrada      Agent snapshot requested         │ │
│ │ 🔴 17:55  Terraza      Movement detected                │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**7. Visor fullscreen (CameraFullscreenView):**

```
┌─────────────────────────────────────────────────────────────┐
│ ← Salón                              ● REC    HD  30fps    │
│                                                             │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████ LIVE RTSP STREAM █████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│ ████████████████████████████████████████████████████████████ │
│                                                             │
│ ┌────────────────────────────────────────────────────────── │
│ │ [📷 Snap]  [🔴 Record]  [🔊 Audio]  [⛶ Fullscreen]     │ │
│ │                                                          │ │
│ │ [🔍 Zoom]  [💬 Ask Agent]  [HD/SD]                      │ │
│ └────────────────────────────────────────────────────────── │
└─────────────────────────────────────────────────────────────┘
```

**8. Editor de cámara (CameraEditorSheet):**

```
┌─────────────────────────────────────────────────────────────┐
│ Add Camera                                          [Save]  │
├─────────────────────────────────────────────────────────────┤
│ Name                                                        │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Salón                                                   │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ RTSP URL (High Quality)                                     │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ rtsp://tapota:****@192.168.1.50:554/stream1             │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ RTSP URL (Low Quality) — optional                           │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ rtsp://tapota:****@192.168.1.50:554/stream2             │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ Transport                                                   │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ● TCP (more reliable)    ○ UDP (lower latency)          │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [🧪 Test Connection]                                        │
│   ✅ Connected — 1920x1080 @ 15fps                         │
│                                                             │
│ ▼ MOTION DETECTION                                          │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ [ ] Enable motion detection                             │ │
│ │ Sensitivity: ████████░░ 80%                             │ │
│ │ [✓] Notify agent on motion                              │ │
│ │ [ ] Record clip on motion (max 30s)                     │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ▼ ADVANCED                                                  │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Thumbnail refresh: [30] seconds                         │ │
│ │ [✓] Use low quality stream for dashboard                │ │
│ │ [ ] Enable audio                                        │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [🗑️ Delete Camera]                                          │
└─────────────────────────────────────────────────────────────┘
```

**Arquitectura propuesta:**

```
┌─────────────────────────────────────────────────────────────┐
│                   CameraDashboardScreen                      │
│  (grid de miniaturas + eventos recientes)                   │
├─────────────────────────────────────────────────────────────┤
│                   CameraFullscreenView                       │
│  (visor RTSP en vivo, controles de grabación, zoom)         │
├─────────────────────────────────────────────────────────────┤
│                   CameraDashboardViewModel                   │
│  (state management, thumbnail refresh, eventos)             │
├─────────────────────┬───────────────────────────────────────┤
│    IpCameraStore    │        RtspStreamEngine                │
│  (persistencia      │  (ExoPlayer RTSP, decodificación,     │
│   EncryptedPrefs)   │   captura de frames, grabación)       │
├─────────────────────┼───────────────────────────────────────┤
│   MotionDetector    │   IpCameraNodeCapability               │
│  (diff de frames    │  (ip_camera.list/snap/status           │
│   on-device)        │   como comandos de nodo)               │
├─────────────────────┴───────────────────────────────────────┤
│                      NodeRuntime                             │
│  (registro de capacidad + routing de invoke)                │
└─────────────────────────────────────────────────────────────┘
```

**Archivos nuevos:**

```
app/src/main/java/ai/openclaw/android/camera/
├── IpCamera.kt                     // Modelos de datos
├── IpCameraStore.kt                // Persistencia (EncryptedSharedPrefs)
├── RtspStreamEngine.kt             // Motor RTSP con ExoPlayer
├── MotionDetector.kt               // Detección de movimiento por diff de frames
├── IpCameraNodeCapability.kt       // Comandos de nodo (ip_camera.*)

app/src/main/java/ai/openclaw/android/ui/camera/
├── CameraDashboardScreen.kt        // Grid de cámaras con miniaturas
├── CameraThumbnailCard.kt          // Card de cada cámara en el grid
├── CameraFullscreenView.kt         // Visor RTSP fullscreen con controles
├── CameraEditorSheet.kt            // Editor/adición de cámaras
├── CameraEventsLog.kt              // Log de eventos (movimiento, snapshots)
├── CameraDashboardViewModel.kt     // ViewModel
```

**Archivos modificados:**

- `NodeRuntime.kt` — Registrar IpCameraNodeCapability, añadir comandos ip_camera.* a buildInvokeCommands(), añadir capacidad "ip_camera" a buildCapabilities()
- `RootScreen.kt` — Añadir botón de acceso al dashboard de cámaras (icono de cámara de vigilancia en la barra de acciones)
- `SettingsSheet.kt` — Sección "IP Cameras" para gestión de cámaras
- `MainViewModel.kt` — Exponer estados de cámaras IP
- `build.gradle.kts` — Dependencia de ExoPlayer con módulo RTSP
- `AndroidManifest.xml` — Permisos de red (ya concedidos por INTERNET)

**Dependencias:**

```kotlin
// ExoPlayer con soporte RTSP (Google/AndroidX Media3)
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("androidx.media3:media3-exoplayer-rtsp:1.5.1")
implementation("androidx.media3:media3-ui:1.5.1")

// Para grabación/remux a MP4
implementation("androidx.media3:media3-muxer:1.5.1")
implementation("androidx.media3:media3-transformer:1.5.1")
```

**Consideraciones de implementación:**

- **Rendimiento:** ExoPlayer maneja RTSP de forma eficiente con decodificación por hardware. Las miniaturas del dashboard se capturan del stream de baja resolución (stream2) para ahorrar ancho de banda.
- **Red local:** Cuando el teléfono está en la misma WiFi que las cámaras, conecta directamente (sin pasar por la Pi). Latencia sub-segundo típica.
- **Seguridad:** Las credenciales RTSP se almacenan en EncryptedSharedPreferences (ya usadas por SecurePrefs). Las URLs con contraseñas nunca se exponen en logs ni en la UI (se enmascaran con `****`).
- **Batería:** El dashboard solo refresca thumbnails cuando está visible. El stream fullscreen se desconecta al salir. La detección de movimiento solo se activa si el usuario lo habilita explícitamente.
- **Privacidad:** Todo es local. Los streams RTSP van directo del dispositivo a la cámara. Las fotos/clips se almacenan solo en el dispositivo. Nada sale a servidores externos (respetando la regla de privacidad de Manuel).
- **Acceso remoto:** Cuando el teléfono está fuera de casa, el acceso a las cámaras puede pasar por Tailscale (las IPs internas son accesibles via Tailscale si la Pi está en la misma red).
- **Compatibilidad:** ExoPlayer soporta RTSP con H.264, H.265/HEVC, y MJPEG — cubre la inmensa mayoría de cámaras IP del mercado (Tapo, Hikvision, Dahua, Reolink, Eufy, cámaras ONVIF genéricas).
- **Interacción con el agente:** El botón "Ask Agent" en el visor fullscreen envía el snapshot actual al chat con un prompt como "Analiza esta imagen de la cámara [nombre]", permitiendo al agente describir lo que ve.

**Integración con el agente (flujo completo):**

1. Manuel dice: "Muéstrame el salón"
2. El agente invoca `ip_camera.snap` con `name: "Salón"` via el nodo Android
3. El teléfono conecta directamente al RTSP de la Tapo, captura un frame
4. El frame se devuelve como base64 al agente (sin pasar por ffmpeg en la Pi)
5. El agente lo analiza y responde: "Todo tranquilo, se ve el sofá y la mesa"

Alternativa proactiva con detección de movimiento:
1. MotionDetector detecta cambio significativo en el frame
2. La app envía evento al agente: "Movimiento detectado en cámara Salón a las 18:42"
3. El agente puede pedir un snapshot para analizar: "Detecto movimiento en el salón. He tomado una foto: se ve a alguien entrando por la puerta."

**Flujo de primer uso:**

1. Usuario accede desde RootScreen → botón 📹 o desde Settings → IP Cameras
2. Dashboard vacío con CTA: "Add your first camera"
3. Formulario de añadir cámara:
   - Nombre descriptivo
   - URL RTSP (con botón "Test Connection")
   - Opcionalmente stream de baja resolución
4. Al guardar, se muestra la miniatura en el dashboard
5. Tap en la miniatura → visor fullscreen con stream en vivo

**Diferencia con la capacidad actual de cámara:**

| Aspecto | `camera.snap` (actual) | IP Camera Dashboard (propuesta) |
|---------|----------------------|-------------------------------|
| Cámara | Cámara del teléfono | Cámaras de red domésticas |
| Conexión | CameraX local | RTSP via red |
| Visor | No hay visor | Dashboard + fullscreen |
| Streaming | Solo snapshot/clip | Live streaming continuo |
| Detección | No | Movimiento on-device |
| Grabación | Clip puntual | Grabación manual + automática |
| Agente | Via nodo existente | Nuevo nodo ip_camera.* |
| Servidor | No necesario | No necesario (directo) |

**Por qué es útil para Manuel:**

- 🏠 **Ya tiene una Tapo C200:** La infraestructura existe, solo falta el visor integrado. Actualmente depende de ffmpeg en la Pi o de la app Tapo.
- 📱 **Todo en una app:** No necesita abrir la app de Tapo para echar un vistazo al salón. OpenClaw se convierte en el panel central de su casa inteligente.
- 🤖 **Integración con el agente:** "¿Qué se ve en el salón?" se resuelve directo desde el teléfono, sin delay del servidor.
- 🔒 **Privacidad total:** Los streams nunca salen de la red local (o de Tailscale). Cumple 100% con la regla de no subir nada a servidores externos.
- 🔔 **Alertas inteligentes:** Detección de movimiento + agente = notificaciones contextuales ("Hay alguien en el salón y no estás en casa").
- 📹 **Escalable:** Puede añadir más cámaras en el futuro (entrada, terraza, etc.) y gestionarlas todas desde un solo lugar.

**Estimación de tiempo:**
- Modelos de datos + IpCameraStore: 1.5h
- RtspStreamEngine (ExoPlayer RTSP, frame capture): 4h
- MotionDetector (diff de frames): 2h
- IpCameraNodeCapability (comandos de nodo): 2h
- CameraDashboardScreen + thumbnails: 3h
- CameraFullscreenView (visor live, controles): 3h
- CameraEditorSheet (formulario + test connection): 2h
- Integración NodeRuntime (capabilities, invoke routing): 1.5h
- Grabación de clips (remux a MP4): 2h
- CameraEventsLog + notificaciones: 1.5h
- Testing + edge cases (timeouts, reconexión, rotación): 3h
- **Total: ~25.5h**

### [2026-02-10] Command Palette Universal con Búsqueda Fuzzy, Acciones Contextuales y Navegación Instantánea (Universal Command Palette & Action Launcher)
- **Estado:** propuesta
- **Plataforma:** Android
- **Estimación:** >4h
- **PR:** (pendiente)

Implementar un Command Palette al estilo VS Code / Raycast / macOS Spotlight accesible desde cualquier pantalla de la app mediante un gesto o acceso rápido, que permita buscar y ejecutar cualquier acción disponible en la app con búsqueda fuzzy, acciones contextuales, historial de comandos, y extensibilidad para integrar Quick Commands, sesiones, herramientas del agente, navegación y configuraciones — todo desde un único punto de entrada con teclado-first UX.

**Problema que resuelve:**

A medida que la app OpenClaw crece en funcionalidad (chat, Talk Mode, Canvas, sesiones, configuraciones, Quick Commands, etc.), la cantidad de acciones disponibles se fragmenta entre múltiples pantallas, menús y estados:

1. **Fragmentación de acciones:** Para cambiar de sesión hay que abrir el selector de sesiones. Para activar Talk Mode hay que ir a Settings o usar un toggle. Para cambiar el nivel de thinking hay que abrir el chat y tocar el botón. Para conectar a un gateway hay que ir a Settings. Cada acción vive en su propia esquina de la UI.

2. **Navegación lenta con muchas sesiones:** A medida que se acumulan sesiones (main, sub-agents, isoladas), encontrar una sesión específica requiere abrir el selector, hacer scroll, y buscar visualmente. No hay forma rápida de saltar directamente a "la sesión donde estaba debuggeando X".

3. **Quick Commands dispersos:** Si Manuel tiene 10+ Quick Commands configurados, encontrar el correcto en la lista actual requiere scroll. Un fuzzy search tipo "dom" → "Domótica: enciende salón" sería instantáneo.

4. **Sin atajos de teclado unificados:** En dispositivos con teclado (tablets, DeX, Chromebooks), no hay un punto de entrada global tipo Ctrl+K para acceder a todo. Los power users esperan este patrón.

5. **Configuraciones enterradas:** Cambiar de gateway, ajustar el voice wake mode, o toggle el canvas debug status requiere navegar a Settings y buscar la opción correcta entre secciones. Un Command Palette permite hacerlo en 2 keystrokes.

6. **Sin contextualización:** Las acciones disponibles no cambian según el contexto. Si estoy viendo el Canvas, las acciones más relevantes deberían ser "snapshot", "reload", "navigate". Si estoy en el chat, deberían ser "switch session", "change thinking", "abort".

Para un power user técnico como Manuel que interactúa con múltiples sesiones, Quick Commands, y configuraciones de domótica, un Command Palette eliminaría la fricción de descubrir y ejecutar acciones, convirtiendo la app en una herramienta tan eficiente como un IDE.

**Funcionalidades propuestas:**

**1. Modelo de datos del Command Palette:**

```kotlin
@Serializable
data class PaletteCommand(
    val id: String,
    val title: String,                           // "Switch to session: main"
    val subtitle: String? = null,                // "Last active 2 min ago"
    val category: CommandCategory,               // NAVIGATION, SESSION, SETTING, QUICK_COMMAND, etc.
    val icon: PaletteIcon,                       // Emoji o MaterialIcon
    val keywords: List<String> = emptyList(),    // Términos adicionales para búsqueda
    val shortcut: KeyboardShortcut? = null,      // Ctrl+K, etc.
    val contextRelevance: Set<AppContext> = emptySet(),  // En qué contextos es más relevante
    val lastUsedAt: Long? = null,                // Para ranking por recencia
    val useCount: Int = 0,                       // Para ranking por frecuencia
    val isAvailable: Boolean = true,             // Gris si no disponible (ej: no conectado)
    val unavailableReason: String? = null,        // "Requires gateway connection"
)

enum class CommandCategory(val displayName: String, val sortOrder: Int) {
    RECENT("Recientes", 0),
    SESSION("Sesiones", 1),
    QUICK_COMMAND("Quick Commands", 2),
    NAVIGATION("Navegación", 3),
    TALK_MODE("Talk Mode", 4),
    CHAT("Chat", 5),
    CANVAS("Canvas", 6),
    GATEWAY("Gateway", 7),
    SETTING("Configuración", 8),
    AGENT("Agente", 9),
    SYSTEM("Sistema", 10),
}

enum class AppContext {
    CHAT_OPEN,
    CHAT_CLOSED,
    TALK_MODE_ACTIVE,
    TALK_MODE_INACTIVE,
    CANVAS_VISIBLE,
    CANVAS_HIDDEN,
    CONNECTED,
    DISCONNECTED,
    SETTINGS_OPEN,
}

sealed class PaletteIcon {
    data class Emoji(val emoji: String) : PaletteIcon()
    data class Material(val name: String) : PaletteIcon()
}

data class KeyboardShortcut(
    val key: String,                              // "K", "T", "S", etc.
    val modifiers: Set<Modifier> = setOf(Modifier.CTRL),
) {
    enum class Modifier { CTRL, SHIFT, ALT, META }

    fun displayString(): String {
        val mods = modifiers.joinToString("+") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return if (mods.isEmpty()) key else "$mods+$key"
    }
}
```

**2. Motor de búsqueda fuzzy (FuzzySearchEngine):**

```kotlin
class FuzzySearchEngine {
    /**
     * Implementación de fuzzy matching inspirada en fzf/Sublime Text:
     * - Matching de caracteres no-contiguos ("stm" → "SeTTings Mode")
     * - Bonus por match al inicio de palabra
     * - Bonus por match de caracteres consecutivos
     * - Penalización por gaps largos
     * - Case-insensitive con bonus por case match exacto
     */
    fun score(query: String, target: String): FuzzyScore {
        if (query.isEmpty()) return FuzzyScore(matched = true, score = 0, highlights = emptyList())
        
        val queryLower = query.lowercase()
        val targetLower = target.lowercase()
        
        var queryIdx = 0
        var bestScore = Int.MIN_VALUE
        val bestHighlights = mutableListOf<IntRange>()
        
        // Multi-pass: try matching from each possible start position
        fun matchFrom(startIdx: Int): Pair<Int, List<Int>>? {
            var qi = 0
            var ti = startIdx
            var score = 0
            var lastMatchIdx = -1
            var consecutiveBonus = 0
            val matchIndices = mutableListOf<Int>()
            
            while (qi < queryLower.length && ti < targetLower.length) {
                if (queryLower[qi] == targetLower[ti]) {
                    matchIndices.add(ti)
                    
                    // Bonus por inicio de palabra
                    val isWordStart = ti == 0 || !targetLower[ti - 1].isLetterOrDigit()
                    if (isWordStart) score += 10
                    
                    // Bonus por consecutivos
                    if (lastMatchIdx == ti - 1) {
                        consecutiveBonus += 5
                        score += consecutiveBonus
                    } else {
                        consecutiveBonus = 0
                    }
                    
                    // Bonus por case match exacto
                    if (query[qi] == target[ti]) score += 1
                    
                    // Penalización por gap
                    if (lastMatchIdx >= 0) {
                        val gap = ti - lastMatchIdx - 1
                        score -= gap * 2
                    }
                    
                    lastMatchIdx = ti
                    qi++
                }
                ti++
            }
            
            return if (qi == queryLower.length) score to matchIndices else null
        }
        
        // Intentar desde múltiples posiciones y elegir el mejor score
        for (start in targetLower.indices) {
            if (targetLower[start] != queryLower[0]) continue
            val result = matchFrom(start) ?: continue
            if (result.first > bestScore) {
                bestScore = result.first
                bestHighlights.clear()
                bestHighlights.addAll(collapseToRanges(result.second))
            }
        }
        
        if (bestScore == Int.MIN_VALUE) {
            return FuzzyScore(matched = false, score = 0, highlights = emptyList())
        }
        
        return FuzzyScore(matched = true, score = bestScore, highlights = bestHighlights)
    }
    
    /**
     * Buscar y rankear resultados con scoring combinado:
     * - Fuzzy match score
     * - Bonus por frecuencia de uso (useCount)
     * - Bonus por recencia (lastUsedAt)
     * - Bonus por relevancia contextual
     */
    fun search(
        query: String,
        commands: List<PaletteCommand>,
        currentContext: Set<AppContext>,
        maxResults: Int = 20,
    ): List<ScoredCommand> {
        if (query.isEmpty()) {
            // Sin query: mostrar recientes + contextuales
            return commands
                .filter { it.isAvailable }
                .sortedWith(
                    compareByDescending<PaletteCommand> { cmd ->
                        cmd.contextRelevance.intersect(currentContext).size
                    }.thenByDescending { it.lastUsedAt ?: 0L }
                      .thenByDescending { it.useCount }
                )
                .take(maxResults)
                .map { ScoredCommand(it, 0, emptyList()) }
        }
        
        return commands.mapNotNull { cmd ->
            // Buscar en título, subtítulo, y keywords
            val titleScore = score(query, cmd.title)
            val subtitleScore = cmd.subtitle?.let { score(query, it) }
            val keywordScores = cmd.keywords.map { score(query, it) }
            
            val bestScore = listOfNotNull(
                titleScore.takeIf { it.matched },
                subtitleScore?.takeIf { it.matched },
                *keywordScores.filter { it.matched }.toTypedArray(),
            ).maxByOrNull { it.score } ?: return@mapNotNull null
            
            // Ajustar score por contexto, recencia y frecuencia
            var adjustedScore = bestScore.score
            adjustedScore += cmd.contextRelevance.intersect(currentContext).size * 5
            adjustedScore += (cmd.useCount.coerceAtMost(50)) / 5
            cmd.lastUsedAt?.let { lastUsed ->
                val hoursSince = (System.currentTimeMillis() - lastUsed) / 3_600_000.0
                if (hoursSince < 1) adjustedScore += 8
                else if (hoursSince < 24) adjustedScore += 4
            }
            if (!cmd.isAvailable) adjustedScore -= 100
            
            ScoredCommand(cmd, adjustedScore, bestScore.highlights)
        }
        .sortedByDescending { it.score }
        .take(maxResults)
    }
    
    private fun collapseToRanges(indices: List<Int>): List<IntRange> {
        if (indices.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = indices[0]
        var end = indices[0]
        for (i in 1 until indices.size) {
            if (indices[i] == end + 1) {
                end = indices[i]
            } else {
                ranges.add(start..end)
                start = indices[i]
                end = indices[i]
            }
        }
        ranges.add(start..end)
        return ranges
    }
}

data class FuzzyScore(
    val matched: Boolean,
    val score: Int,
    val highlights: List<IntRange>,
)

data class ScoredCommand(
    val command: PaletteCommand,
    val score: Int,
    val highlights: List<IntRange>,
)
```

**3. Proveedor de comandos (CommandProvider):**

```kotlin
class CommandProvider(
    private val runtime: NodeRuntime,
    private val scope: CoroutineScope,
) {
    private val _commands = MutableStateFlow<List<PaletteCommand>>(emptyList())
    val commands: StateFlow<List<PaletteCommand>> = _commands.asStateFlow()
    
    private val usageStore = CommandUsageStore()
    
    /**
     * Recolecta todos los comandos disponibles de múltiples fuentes.
     * Se ejecuta cada vez que cambia el estado de la app.
     */
    fun refresh(context: Set<AppContext>) {
        scope.launch {
            val allCommands = buildList {
                addAll(navigationCommands())
                addAll(sessionCommands())
                addAll(chatCommands())
                addAll(talkModeCommands())
                addAll(canvasCommands())
                addAll(gatewayCommands())
                addAll(settingsCommands())
                addAll(quickCommands())
                addAll(agentCommands())
                addAll(systemCommands())
            }
            
            // Enriquecer con datos de uso
            val enriched = allCommands.map { cmd ->
                val usage = usageStore.getUsage(cmd.id)
                cmd.copy(
                    lastUsedAt = usage?.lastUsedAt,
                    useCount = usage?.count ?: 0,
                )
            }
            
            _commands.value = enriched
        }
    }
    
    private fun navigationCommands(): List<PaletteCommand> = listOf(
        PaletteCommand(
            id = "nav:chat",
            title = "Abrir Chat",
            category = CommandCategory.NAVIGATION,
            icon = PaletteIcon.Emoji("💬"),
            keywords = listOf("chat", "mensaje", "conversar"),
            shortcut = KeyboardShortcut("C", setOf(KeyboardShortcut.Modifier.CTRL)),
            contextRelevance = setOf(AppContext.CHAT_CLOSED),
        ),
        PaletteCommand(
            id = "nav:settings",
            title = "Abrir Configuración",
            category = CommandCategory.NAVIGATION,
            icon = PaletteIcon.Emoji("⚙️"),
            keywords = listOf("settings", "config", "ajustes", "opciones"),
            shortcut = KeyboardShortcut(",", setOf(KeyboardShortcut.Modifier.CTRL)),
        ),
        PaletteCommand(
            id = "nav:canvas",
            title = "Ver Canvas",
            category = CommandCategory.NAVIGATION,
            icon = PaletteIcon.Emoji("🖼️"),
            keywords = listOf("canvas", "pantalla", "vista", "a2ui"),
        ),
    )
    
    private suspend fun sessionCommands(): List<PaletteCommand> {
        val sessions = runtime.chatSessions.value
        val currentKey = runtime.chatSessionKey.value
        val isConnected = runtime.isConnected.value
        
        return sessions.map { session ->
            val isCurrent = session.key == currentKey
            PaletteCommand(
                id = "session:${session.key}",
                title = if (isCurrent) "📍 ${session.displayName ?: session.key} (actual)" 
                        else session.displayName ?: session.key,
                subtitle = session.updatedAtMs?.let { 
                    "Última actividad: ${formatRelativeTime(it)}" 
                },
                category = CommandCategory.SESSION,
                icon = PaletteIcon.Emoji(if (isCurrent) "📍" else "💬"),
                keywords = listOf("sesión", "session", session.key) + 
                           (session.displayName?.split(" ") ?: emptyList()),
                isAvailable = isConnected,
                unavailableReason = if (!isConnected) "Requiere conexión al gateway" else null,
            )
        }
    }
    
    private fun chatCommands(): List<PaletteCommand> {
        val isConnected = runtime.isConnected.value
        return listOf(
            PaletteCommand(
                id = "chat:send_thinking_off",
                title = "Thinking: Off",
                category = CommandCategory.CHAT,
                icon = PaletteIcon.Emoji("🧠"),
                keywords = listOf("thinking", "pensar", "razonar"),
                contextRelevance = setOf(AppContext.CHAT_OPEN),
                isAvailable = isConnected,
            ),
            PaletteCommand(
                id = "chat:send_thinking_low",
                title = "Thinking: Low",
                category = CommandCategory.CHAT,
                icon = PaletteIcon.Emoji("🧠"),
                keywords = listOf("thinking", "pensar", "razonar", "bajo"),
                contextRelevance = setOf(AppContext.CHAT_OPEN),
                isAvailable = isConnected,
            ),
            PaletteCommand(
                id = "chat:send_thinking_medium",
                title = "Thinking: Medium",
                category = CommandCategory.CHAT,
                icon = PaletteIcon.Emoji("🧠"),
                keywords = listOf("thinking", "pensar", "razonar", "medio"),
                contextRelevance = setOf(AppContext.CHAT_OPEN),
                isAvailable = isConnected,
            ),
            PaletteCommand(
                id = "chat:send_thinking_high",
                title = "Thinking: High",
                category = CommandCategory.CHAT,
                icon = PaletteIcon.Emoji("🧠"),
                keywords = listOf("thinking", "pensar", "razonar", "alto"),
                contextRelevance = setOf(AppContext.CHAT_OPEN),
                isAvailable = isConnected,
            ),
            PaletteCommand(
                id = "chat:abort",
                title = "Abortar respuesta actual",
                category = CommandCategory.CHAT,
                icon = PaletteIcon.Emoji("⛔"),
                keywords = listOf("abort", "cancel", "stop", "parar", "detener"),
                shortcut = KeyboardShortcut(".", setOf(KeyboardShortcut.Modifier.CTRL)),
                contextRelevance = setOf(AppContext.CHAT_OPEN),
                isAvailable = isConnected && runtime.pendingRunCount.value > 0,
                unavailableReason = if (runtime.pendingRunCount.value == 0) "Sin operaciones pendientes" else null,
            ),
            PaletteCommand(
                id = "chat:refresh",
                title = "Refrescar chat",
                category = CommandCategory.CHAT,
                icon = PaletteIcon.Emoji("🔄"),
                keywords = listOf("refresh", "reload", "actualizar", "recargar"),
                contextRelevance = setOf(AppContext.CHAT_OPEN),
                isAvailable = isConnected,
            ),
        )
    }
    
    private fun talkModeCommands(): List<PaletteCommand> {
        val talkEnabled = runtime.talkEnabled.value
        val isConnected = runtime.isConnected.value
        return listOf(
            PaletteCommand(
                id = "talk:toggle",
                title = if (talkEnabled) "Desactivar Talk Mode" else "Activar Talk Mode",
                category = CommandCategory.TALK_MODE,
                icon = PaletteIcon.Emoji(if (talkEnabled) "🔇" else "🎙️"),
                keywords = listOf("talk", "voz", "hablar", "micrófono", "mic"),
                shortcut = KeyboardShortcut("T", setOf(KeyboardShortcut.Modifier.CTRL)),
                isAvailable = isConnected,
            ),
        )
    }
    
    private fun canvasCommands(): List<PaletteCommand> = listOf(
        PaletteCommand(
            id = "canvas:snapshot",
            title = "Capturar snapshot del Canvas",
            category = CommandCategory.CANVAS,
            icon = PaletteIcon.Emoji("📸"),
            keywords = listOf("screenshot", "captura", "foto", "snapshot"),
            contextRelevance = setOf(AppContext.CANVAS_VISIBLE),
            isAvailable = runtime.isConnected.value,
        ),
        PaletteCommand(
            id = "canvas:reload",
            title = "Recargar Canvas",
            category = CommandCategory.CANVAS,
            icon = PaletteIcon.Emoji("🔄"),
            keywords = listOf("reload", "recargar", "refrescar"),
            contextRelevance = setOf(AppContext.CANVAS_VISIBLE),
        ),
        PaletteCommand(
            id = "canvas:debug_toggle",
            title = if (runtime.canvasDebugStatusEnabled.value) "Ocultar debug del Canvas" 
                    else "Mostrar debug del Canvas",
            category = CommandCategory.CANVAS,
            icon = PaletteIcon.Emoji("🐛"),
            keywords = listOf("debug", "depurar", "estado"),
        ),
    )
    
    private fun gatewayCommands(): List<PaletteCommand> {
        val isConnected = runtime.isConnected.value
        val gateways = runtime.gateways.value
        
        return buildList {
            if (isConnected) {
                add(PaletteCommand(
                    id = "gw:disconnect",
                    title = "Desconectar del gateway",
                    category = CommandCategory.GATEWAY,
                    icon = PaletteIcon.Emoji("🔌"),
                    keywords = listOf("disconnect", "desconectar", "offline"),
                ))
                add(PaletteCommand(
                    id = "gw:reconnect",
                    title = "Reconectar al gateway",
                    category = CommandCategory.GATEWAY,
                    icon = PaletteIcon.Emoji("🔁"),
                    keywords = listOf("reconnect", "reconectar", "reiniciar conexión"),
                ))
            }
            
            for (gw in gateways) {
                add(PaletteCommand(
                    id = "gw:connect:${gw.stableId}",
                    title = "Conectar a ${gw.displayName ?: gw.host}",
                    subtitle = "${gw.host}:${gw.port}",
                    category = CommandCategory.GATEWAY,
                    icon = PaletteIcon.Emoji("📡"),
                    keywords = listOf("connect", "conectar", gw.host, gw.displayName ?: ""),
                ))
            }
        }
    }
    
    private fun settingsCommands(): List<PaletteCommand> = listOf(
        PaletteCommand(
            id = "set:camera_toggle",
            title = if (runtime.cameraEnabled.value) "Desactivar cámara" else "Activar cámara",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("📷"),
            keywords = listOf("camera", "cámara", "foto"),
        ),
        PaletteCommand(
            id = "set:location_off",
            title = "Ubicación: Off",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("📍"),
            keywords = listOf("location", "ubicación", "gps"),
        ),
        PaletteCommand(
            id = "set:location_foreground",
            title = "Ubicación: Solo en primer plano",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("📍"),
            keywords = listOf("location", "ubicación", "gps", "foreground"),
        ),
        PaletteCommand(
            id = "set:location_always",
            title = "Ubicación: Siempre",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("📍"),
            keywords = listOf("location", "ubicación", "gps", "always", "siempre"),
        ),
        PaletteCommand(
            id = "set:prevent_sleep_toggle",
            title = if (runtime.preventSleep.value) "Permitir suspensión" else "Evitar suspensión",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("☕"),
            keywords = listOf("sleep", "suspender", "pantalla", "wakelock"),
        ),
        PaletteCommand(
            id = "set:voice_wake_off",
            title = "Voice Wake: Off",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("🗣️"),
            keywords = listOf("wake", "despertar", "voz", "activación"),
        ),
        PaletteCommand(
            id = "set:voice_wake_foreground",
            title = "Voice Wake: Solo en primer plano",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("🗣️"),
            keywords = listOf("wake", "despertar", "voz", "foreground"),
        ),
        PaletteCommand(
            id = "set:voice_wake_always",
            title = "Voice Wake: Siempre",
            category = CommandCategory.SETTING,
            icon = PaletteIcon.Emoji("🗣️"),
            keywords = listOf("wake", "despertar", "voz", "always"),
        ),
    )
    
    private suspend fun quickCommands(): List<PaletteCommand> {
        // Los Quick Commands se obtienen del gateway
        if (!runtime.isConnected.value) return emptyList()
        
        try {
            val res = runtime.operatorRequest("quickcommands.list", "{}")
            val root = Json.parseToJsonElement(res) as? JsonObject ?: return emptyList()
            val commands = root["commands"] as? JsonArray ?: return emptyList()
            
            return commands.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val description = (obj["description"] as? JsonPrimitive)?.content
                
                PaletteCommand(
                    id = "qc:$id",
                    title = name,
                    subtitle = description,
                    category = CommandCategory.QUICK_COMMAND,
                    icon = PaletteIcon.Emoji("⚡"),
                    keywords = listOf("quick", "comando", "rápido") + name.split(" "),
                )
            }
        } catch (_: Throwable) {
            return emptyList()
        }
    }
    
    private fun agentCommands(): List<PaletteCommand> {
        val isConnected = runtime.isConnected.value
        return listOf(
            PaletteCommand(
                id = "agent:status",
                title = "Ver estado del agente",
                subtitle = "Modelo, tokens, sesión activa",
                category = CommandCategory.AGENT,
                icon = PaletteIcon.Emoji("📊"),
                keywords = listOf("status", "estado", "info", "modelo", "tokens"),
                isAvailable = isConnected,
            ),
            PaletteCommand(
                id = "agent:send_message",
                title = "Enviar mensaje rápido al agente",
                subtitle = "Abre un mini-composer inline",
                category = CommandCategory.AGENT,
                icon = PaletteIcon.Emoji("✉️"),
                keywords = listOf("enviar", "mensaje", "send", "message", "preguntar"),
                isAvailable = isConnected,
            ),
        )
    }
    
    private fun systemCommands(): List<PaletteCommand> = listOf(
        PaletteCommand(
            id = "sys:copy_diagnostics",
            title = "Copiar info de diagnóstico",
            category = CommandCategory.SYSTEM,
            icon = PaletteIcon.Emoji("📋"),
            keywords = listOf("debug", "diagnóstico", "copiar", "info", "versión"),
        ),
        PaletteCommand(
            id = "sys:foreground_toggle",
            title = if (runtime.isForeground.value) "Enviar a background" else "Traer a foreground",
            category = CommandCategory.SYSTEM,
            icon = PaletteIcon.Emoji("📱"),
            keywords = listOf("foreground", "background", "primer plano", "segundo plano"),
        ),
    )
    
    fun recordUsage(commandId: String) {
        usageStore.recordUsage(commandId)
    }
}
```

**4. Almacenamiento de uso (CommandUsageStore):**

```kotlin
class CommandUsageStore(private val prefs: SharedPreferences) {
    data class Usage(val count: Int, val lastUsedAt: Long)
    
    private val cache = ConcurrentHashMap<String, Usage>()
    
    fun getUsage(commandId: String): Usage? {
        return cache.getOrPut(commandId) {
            val count = prefs.getInt("palette_usage_count_$commandId", 0)
            val lastUsed = prefs.getLong("palette_usage_last_$commandId", 0L)
            if (count > 0) Usage(count, lastUsed) else return null
        }
    }
    
    fun recordUsage(commandId: String) {
        val current = getUsage(commandId)
        val newCount = (current?.count ?: 0) + 1
        val now = System.currentTimeMillis()
        cache[commandId] = Usage(newCount, now)
        prefs.edit()
            .putInt("palette_usage_count_$commandId", newCount)
            .putLong("palette_usage_last_$commandId", now)
            .apply()
    }
}
```

**5. Ejecutor de comandos (CommandExecutor):**

```kotlin
class CommandExecutor(
    private val runtime: NodeRuntime,
    private val provider: CommandProvider,
) {
    /**
     * Ejecuta un comando del palette. Cada categoría tiene su propio
     * handling, desde navegación simple hasta llamadas al gateway.
     */
    suspend fun execute(command: PaletteCommand): ExecutionResult {
        provider.recordUsage(command.id)
        
        val parts = command.id.split(":", limit = 3)
        val prefix = parts[0]
        val action = parts.getOrNull(1) ?: ""
        val param = parts.getOrNull(2)
        
        return when (prefix) {
            "nav" -> executeNavigation(action)
            "session" -> executeSessionSwitch(action + (param?.let { ":$it" } ?: ""))
            "chat" -> executeChatAction(action)
            "talk" -> executeTalkAction(action)
            "canvas" -> executeCanvasAction(action)
            "gw" -> executeGatewayAction(action, param)
            "set" -> executeSettingAction(action)
            "qc" -> executeQuickCommand(param ?: action)
            "agent" -> executeAgentAction(action)
            "sys" -> executeSystemAction(action)
            else -> ExecutionResult.Error("Unknown command prefix: $prefix")
        }
    }
    
    private fun executeNavigation(action: String): ExecutionResult {
        return when (action) {
            "chat" -> ExecutionResult.Navigate(NavigationTarget.CHAT)
            "settings" -> ExecutionResult.Navigate(NavigationTarget.SETTINGS)
            "canvas" -> ExecutionResult.Navigate(NavigationTarget.CANVAS)
            else -> ExecutionResult.Error("Unknown navigation: $action")
        }
    }
    
    private fun executeSessionSwitch(sessionKey: String): ExecutionResult {
        runtime.switchChatSession(sessionKey)
        return ExecutionResult.Success("Sesión cambiada a $sessionKey")
    }
    
    private fun executeChatAction(action: String): ExecutionResult {
        return when {
            action.startsWith("send_thinking_") -> {
                val level = action.removePrefix("send_thinking_")
                runtime.setChatThinkingLevel(level)
                ExecutionResult.Success("Thinking: $level")
            }
            action == "abort" -> {
                runtime.abortChat()
                ExecutionResult.Success("Operación abortada")
            }
            action == "refresh" -> {
                runtime.refreshChat()
                ExecutionResult.Success("Chat refrescado")
            }
            else -> ExecutionResult.Error("Unknown chat action: $action")
        }
    }
    
    private fun executeTalkAction(action: String): ExecutionResult {
        return when (action) {
            "toggle" -> {
                val newState = !runtime.talkEnabled.value
                runtime.setTalkEnabled(newState)
                ExecutionResult.Success(if (newState) "Talk Mode activado" else "Talk Mode desactivado")
            }
            else -> ExecutionResult.Error("Unknown talk action: $action")
        }
    }
    
    private fun executeCanvasAction(action: String): ExecutionResult {
        return when (action) {
            "snapshot" -> {
                // Trigger snapshot via runtime
                ExecutionResult.Success("Snapshot capturado")
            }
            "reload" -> {
                runtime.canvas.reload()
                ExecutionResult.Success("Canvas recargado")
            }
            "debug_toggle" -> {
                val newState = !runtime.canvasDebugStatusEnabled.value
                runtime.setCanvasDebugStatusEnabled(newState)
                ExecutionResult.Success(if (newState) "Debug visible" else "Debug oculto")
            }
            else -> ExecutionResult.Error("Unknown canvas action: $action")
        }
    }
    
    private fun executeGatewayAction(action: String, param: String?): ExecutionResult {
        return when (action) {
            "disconnect" -> {
                runtime.disconnect()
                ExecutionResult.Success("Desconectado")
            }
            "reconnect" -> {
                runtime.refreshGatewayConnection()
                ExecutionResult.Success("Reconectando…")
            }
            "connect" -> {
                val stableId = param ?: return ExecutionResult.Error("Missing gateway id")
                val gw = runtime.gateways.value.find { it.stableId == stableId }
                    ?: return ExecutionResult.Error("Gateway not found")
                runtime.connect(gw)
                ExecutionResult.Success("Conectando a ${gw.displayName ?: gw.host}…")
            }
            else -> ExecutionResult.Error("Unknown gateway action: $action")
        }
    }
    
    private fun executeSettingAction(action: String): ExecutionResult {
        return when (action) {
            "camera_toggle" -> {
                val newState = !runtime.cameraEnabled.value
                runtime.setCameraEnabled(newState)
                ExecutionResult.Success(if (newState) "Cámara activada" else "Cámara desactivada")
            }
            "location_off" -> {
                runtime.setLocationMode(LocationMode.Off)
                ExecutionResult.Success("Ubicación: Off")
            }
            "location_foreground" -> {
                runtime.setLocationMode(LocationMode.Foreground)
                ExecutionResult.Success("Ubicación: Foreground")
            }
            "location_always" -> {
                runtime.setLocationMode(LocationMode.Always)
                ExecutionResult.Success("Ubicación: Always")
            }
            "prevent_sleep_toggle" -> {
                val newState = !runtime.preventSleep.value
                runtime.setPreventSleep(newState)
                ExecutionResult.Success(if (newState) "Suspensión bloqueada" else "Suspensión permitida")
            }
            "voice_wake_off" -> {
                runtime.setVoiceWakeMode(VoiceWakeMode.Off)
                ExecutionResult.Success("Voice Wake: Off")
            }
            "voice_wake_foreground" -> {
                runtime.setVoiceWakeMode(VoiceWakeMode.Foreground)
                ExecutionResult.Success("Voice Wake: Foreground")
            }
            "voice_wake_always" -> {
                runtime.setVoiceWakeMode(VoiceWakeMode.Always)
                ExecutionResult.Success("Voice Wake: Always")
            }
            else -> ExecutionResult.Error("Unknown setting: $action")
        }
    }
    
    private suspend fun executeQuickCommand(commandId: String): ExecutionResult {
        return try {
            runtime.operatorRequest("quickcommands.execute", """{"id":"$commandId"}""")
            ExecutionResult.Success("Quick Command ejecutado")
        } catch (e: Throwable) {
            ExecutionResult.Error("Error: ${e.message}")
        }
    }
    
    private fun executeAgentAction(action: String): ExecutionResult {
        return when (action) {
            "status" -> ExecutionResult.Navigate(NavigationTarget.AGENT_STATUS)
            "send_message" -> ExecutionResult.ShowInlineComposer
            else -> ExecutionResult.Error("Unknown agent action: $action")
        }
    }
    
    private fun executeSystemAction(action: String): ExecutionResult {
        return when (action) {
            "copy_diagnostics" -> ExecutionResult.CopyToClipboard(runtime.buildDiagnosticsString())
            "foreground_toggle" -> {
                val newState = !runtime.isForeground.value
                runtime.setForeground(newState)
                ExecutionResult.Success(if (newState) "Primer plano" else "Segundo plano")
            }
            else -> ExecutionResult.Error("Unknown system action: $action")
        }
    }
}

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Error(val message: String) : ExecutionResult()
    data class Navigate(val target: NavigationTarget) : ExecutionResult()
    data object ShowInlineComposer : ExecutionResult()
    data class CopyToClipboard(val text: String) : ExecutionResult()
}

enum class NavigationTarget {
    CHAT, SETTINGS, CANVAS, AGENT_STATUS,
}
```

**6. UI del Command Palette (CommandPaletteSheet):**

```
┌─────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🔍  Type a command...                              [×]  │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ RECIENTES                                                   │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 💬  Switch to session: debug-agent                      │ │
│ │     Last active 5 min ago                               │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🧠  Thinking: High                                     │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ ⚡  Domótica: Luces salón                              │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ SESIONES                                                    │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 📍  main (actual)                                       │ │
│ │     Last active just now                                │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 💬  cron-mejoras-app                                    │ │
│ │     Last active 2h ago                                  │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ CONFIGURACIÓN                                               │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 📷  Activar cámara                                      │ │
│ │ 🗣️  Voice Wake: Siempre                                │ │
│ │ 📍  Ubicación: Solo en primer plano                     │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Con búsqueda activa ("dom"):**

```
┌─────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🔍  dom                                            [×]  │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ 3 results                                                   │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ⚡  [Dom]ótica: Luces salón                             │ │
│ │     Quick Command · Used 23 times                       │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ ⚡  [Dom]ótica: Estado casa                             │ │
│ │     Quick Command · Used 8 times                        │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ ⚡  [Dom]ótica: Modo nocturno                           │ │
│ │     Quick Command · Used 5 times                        │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**Con resultado de acción (feedback inline):**

```
┌─────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ✅  Talk Mode activado                                  │ │
│ └─────────────────────────────────────────────────────────┘ │
│              (se cierra automáticamente tras 1.5s)          │
└─────────────────────────────────────────────────────────────┘
```

**7. Composable del Command Palette:**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteSheet(
    viewModel: CommandPaletteViewModel,
    onDismiss: () -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val executionResult by viewModel.lastResult.collectAsState()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        viewModel.refresh()
        focusRequester.requestFocus()
    }
    
    // Auto-dismiss after successful action
    LaunchedEffect(executionResult) {
        when (val result = executionResult) {
            is ExecutionResult.Success -> {
                delay(1200)
                onDismiss()
            }
            is ExecutionResult.Navigate -> {
                onNavigate(result.target)
                delay(300)
                onDismiss()
            }
            else -> {}
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        windowInsets = WindowInsets(0),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .imePadding()
        ) {
            // Search input
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Buscar comando…") },
                leadingIcon = { Text("🔍") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, "Limpiar")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // Ejecutar primer resultado
                        results.firstOrNull()?.let { viewModel.execute(it.command) }
                    }
                ),
            )
            
            // Execution feedback
            executionResult?.let { result ->
                AnimatedVisibility(visible = true) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = when (result) {
                            is ExecutionResult.Success -> MaterialTheme.colorScheme.primaryContainer
                            is ExecutionResult.Error -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = when (result) {
                                is ExecutionResult.Success -> "✅ ${result.message}"
                                is ExecutionResult.Error -> "❌ ${result.message}"
                                is ExecutionResult.CopyToClipboard -> "📋 Copiado al portapapeles"
                                else -> ""
                            },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            
            // Results list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                val grouped = results.groupBy { it.command.category }
                
                for ((category, commands) in grouped) {
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                text = category.displayName.uppercase(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    
                    items(commands) { scored ->
                        CommandResultItem(
                            scored = scored,
                            query = query,
                            onClick = { viewModel.execute(scored.command) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandResultItem(
    scored: ScoredCommand,
    query: String,
    onClick: () -> Unit,
) {
    val command = scored.command
    val alpha = if (command.isAvailable) 1f else 0.4f
    
    ListItem(
        modifier = Modifier
            .clickable(enabled = command.isAvailable) { onClick() }
            .alpha(alpha),
        headlineContent = {
            // Texto con highlights de fuzzy match
            HighlightedText(
                text = command.title,
                highlights = scored.highlights,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Column {
                command.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                command.unavailableReason?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        leadingContent = {
            when (val icon = command.icon) {
                is PaletteIcon.Emoji -> Text(icon.emoji, style = MaterialTheme.typography.titleLarge)
                is PaletteIcon.Material -> Icon(
                    imageVector = /* resolve by name */ Icons.Default.Star,
                    contentDescription = null,
                )
            }
        },
        trailingContent = {
            command.shortcut?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = it.displayString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun HighlightedText(
    text: String,
    highlights: List<IntRange>,
    style: TextStyle,
) {
    val annotated = buildAnnotatedString {
        var lastEnd = 0
        for (range in highlights.sortedBy { it.first }) {
            if (range.first > lastEnd) {
                append(text.substring(lastEnd, range.first))
            }
            withStyle(SpanStyle(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )) {
                append(text.substring(range.first, range.last + 1))
            }
            lastEnd = range.last + 1
        }
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
    Text(text = annotated, style = style)
}
```

**8. ViewModel del Command Palette:**

```kotlin
class CommandPaletteViewModel(
    private val provider: CommandProvider,
    private val executor: CommandExecutor,
    private val searchEngine: FuzzySearchEngine = FuzzySearchEngine(),
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    
    private val _results = MutableStateFlow<List<ScoredCommand>>(emptyList())
    val results: StateFlow<List<ScoredCommand>> = _results.asStateFlow()
    
    private val _lastResult = MutableStateFlow<ExecutionResult?>(null)
    val lastResult: StateFlow<ExecutionResult?> = _lastResult.asStateFlow()
    
    private val _currentContext = MutableStateFlow<Set<AppContext>>(emptySet())
    
    init {
        viewModelScope.launch {
            combine(query, provider.commands, _currentContext) { q, cmds, ctx ->
                searchEngine.search(q, cmds, ctx)
            }.collect { scored ->
                _results.value = scored
            }
        }
    }
    
    fun refresh() {
        provider.refresh(_currentContext.value)
    }
    
    fun setQuery(query: String) {
        _query.value = query
        _lastResult.value = null
    }
    
    fun setContext(context: Set<AppContext>) {
        _currentContext.value = context
    }
    
    fun execute(command: PaletteCommand) {
        viewModelScope.launch {
            _lastResult.value = executor.execute(command)
        }
    }
}
```

**9. Integración con teclado físico (KeyboardShortcutHandler):**

```kotlin
@Composable
fun CommandPaletteKeyboardHandler(
    onOpenPalette: () -> Unit,
    commands: List<PaletteCommand>,
    executor: CommandExecutor,
) {
    // Interceptar atajos globales de teclado
    val scope = rememberCoroutineScope()
    
    Box(modifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        
        // Ctrl+K o Ctrl+P → Abrir palette
        if ((event.isCtrlPressed) && 
            (event.key == Key.K || event.key == Key.P)) {
            onOpenPalette()
            return@onPreviewKeyEvent true
        }
        
        // Buscar atajos directos de comandos
        val shortcut = KeyboardShortcut(
            key = event.key.keyCode.toChar().uppercase(),
            modifiers = buildSet {
                if (event.isCtrlPressed) add(KeyboardShortcut.Modifier.CTRL)
                if (event.isShiftPressed) add(KeyboardShortcut.Modifier.SHIFT)
                if (event.isAltPressed) add(KeyboardShortcut.Modifier.ALT)
                if (event.isMetaPressed) add(KeyboardShortcut.Modifier.META)
            }
        )
        
        val matchedCommand = commands.firstOrNull { 
            it.shortcut == shortcut && it.isAvailable 
        }
        
        if (matchedCommand != null) {
            scope.launch { executor.execute(matchedCommand) }
            return@onPreviewKeyEvent true
        }
        
        false
    }) {}
}
```

**10. Trigger de apertura (Gesto de swipe-down o FAB):**

```kotlin
// En RootScreen.kt - Detectar gesto de doble-tap en la barra de estado
// o un FAB flotante sutil
@Composable
fun CommandPaletteTrigger(
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    // Opción 1: FAB minimalista en esquina
    SmallFloatingActionButton(
        onClick = onOpen,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Text("⌘", style = MaterialTheme.typography.labelLarge)
    }
    
    // Opción 2: Double-tap en StatusPill
    // Se integra en StatusPill.kt existente con:
    // Modifier.pointerInput(Unit) {
    //     detectTapGestures(onDoubleTap = { onOpen() })
    // }
}
```

**Arquitectura propuesta:**

```
┌─────────────────────────────────────────────────────────────┐
│                    RootScreen                                │
│  (intercepta teclado global + trigger de apertura)          │
├─────────────────────────────────────────────────────────────┤
│                    CommandPaletteSheet                       │
│  (ModalBottomSheet con TextField + resultados)              │
├─────────────────────────────────────────────────────────────┤
│                    CommandPaletteViewModel                   │
│  (state management, query → results pipeline)               │
├─────────────────────┬───────────────────────────────────────┤
│   CommandProvider    │         FuzzySearchEngine             │
│  (recopila comandos │   (fuzzy match + scoring +            │
│   de todas las      │    ranking contextual)                │
│   fuentes)          │                                       │
├─────────────────────┤                                       │
│   CommandExecutor   │                                       │
│  (ejecuta comandos  │                                       │
│   según prefijo)    │                                       │
├─────────────────────┼───────────────────────────────────────┤
│  CommandUsageStore  │         NodeRuntime                    │
│  (persistencia de   │   (fuente de estado + acciones)       │
│   uso/recencia)     │                                       │
└─────────────────────┴───────────────────────────────────────┘
```

**Archivos nuevos:**

```
app/src/main/java/ai/openclaw/android/palette/
├── PaletteCommand.kt                // Modelos de datos (Command, Category, Icon, Shortcut)
├── FuzzySearchEngine.kt             // Motor de búsqueda fuzzy con scoring
├── CommandProvider.kt               // Recopilación de comandos de múltiples fuentes
├── CommandExecutor.kt               // Ejecución de comandos por categoría
├── CommandUsageStore.kt             // Persistencia de frecuencia/recencia

app/src/main/java/ai/openclaw/android/ui/palette/
├── CommandPaletteSheet.kt           // BottomSheet principal del palette
├── CommandResultItem.kt             // Card de cada resultado
├── HighlightedText.kt              // Texto con highlights de fuzzy match
├── CommandPaletteViewModel.kt       // ViewModel con reactive pipeline
├── CommandPaletteTrigger.kt         // FAB o gesto para abrir
├── KeyboardShortcutHandler.kt       // Handler global de atajos de teclado
```

**Archivos modificados:**

- `RootScreen.kt` - Integrar trigger de apertura + keyboard handler global
- `StatusPill.kt` - Añadir double-tap para abrir palette (opcional)
- `MainViewModel.kt` - Exponer estado de CommandPaletteViewModel
- `NodeRuntime.kt` - Exponer `operatorRequest` para Quick Commands lookup
- `SettingsSheet.kt` - Opción para configurar trigger del palette

**Dependencias:**

```kotlin
// No requiere dependencias externas nuevas.
// Todo se implementa con Compose Foundation + Material3 existentes.
// FuzzySearchEngine es implementación propia (sin librerías).
```

**Características clave del diseño:**

1. **Ranking inteligente combinado:** El score final combina fuzzy match quality + frecuencia de uso + recencia + relevancia contextual. Un comando que usas 50 veces al día aparece primero aunque el match sea parcial.

2. **Context-aware:** Los comandos se reordenan según el estado actual de la app. Si el chat está abierto, "Thinking: High" y "Abort" aparecen primero. Si estás viendo el Canvas, "Snapshot" sube.

3. **Zero-config:** Funciona out of the box con todos los comandos existentes de la app. No requiere configuración. Los Quick Commands del gateway se cargan automáticamente.

4. **Keyboard-first pero touch-friendly:** Funciona perfecto con teclado físico (Ctrl+K, Enter para ejecutar, flechas para navegar), pero también es completamente usable con touch (BottomSheet, scroll, tap).

5. **Feedback inline:** Los resultados de ejecución se muestran dentro del palette con auto-dismiss, sin interrumpir el flujo.

6. **Extensible:** Añadir nuevas categorías de comandos solo requiere un nuevo método en CommandProvider. El sistema de prefijos en IDs hace que el routing sea trivial.

7. **Performant:** La búsqueda fuzzy se ejecuta en coroutines con `combine()` reactivo. Cada keystroke produce resultados instantáneos sin lag perceptible para <500 comandos.

**Casos de uso concretos para Manuel:**

- **"dom"** → Aparecen todos los Quick Commands de domótica, ordenados por frecuencia
- **"ses debug"** → Saltar directamente a la sesión "debug-agent" sin abrir el selector
- **Ctrl+T** → Toggle Talk Mode sin tocar la pantalla (con teclado Bluetooth o DeX)
- **"think high"** → Cambiar thinking a High sin abrir el chat ni el dropdown
- **"gateway"** → Ver opciones de conexión/desconexión rápidamente
- **"cam"** → Toggle cámara sin ir a Settings y buscar la opción
- **"abort"** → Parar una respuesta lenta sin tener que navegar al chat

**Por qué es útil para un power user:**

El Command Palette es el patrón de interacción más eficiente inventado para apps complejas. Lo usan VS Code, Raycast, Slack, Notion, Linear, Arc, Figma, y prácticamente toda herramienta diseñada para power users. Reduce cualquier acción de "navegar → buscar → click" a "Ctrl+K → escribir 2-3 letras → Enter". Para alguien como Manuel que interactúa con múltiples sesiones, Quick Commands, configuraciones y herramientas a lo largo del día, la diferencia en velocidad de interacción es transformacional.

**Estimación de tiempo:**

- Modelos de datos (PaletteCommand, Category, Icon, Shortcut): 1h
- FuzzySearchEngine con scoring avanzado: 3h
- CommandProvider (recopilación de todas las fuentes): 3h
- CommandExecutor (ejecución por categoría): 2h
- CommandUsageStore (persistencia): 0.5h
- CommandPaletteSheet UI + BottomSheet: 3h
- CommandResultItem + HighlightedText: 1.5h
- CommandPaletteViewModel (reactive pipeline): 1.5h
- KeyboardShortcutHandler (atajos globales): 2h
- Trigger de apertura (FAB + double-tap): 1h
- Integración en RootScreen + MainViewModel: 1.5h
- Quick Commands fetch desde gateway: 1h
- Testing + edge cases (rendimiento, edge queries, contextos): 3h
- **Total: ~24h**
