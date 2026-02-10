### [2026-02-10] Sistema de Acciones Contextuales Inteligentes y Sugerencias de Continuación en Mensajes del Chat (Smart Response Actions & Follow-up Engine)
- **Estado:** propuesta
- **Plataforma:** Android
- **Estimación:** >4h
- **PR:** (pendiente)

Implementar un motor on-device que analice en tiempo real las respuestas del agente, detecte contenido accionable (URLs, bloques de código, comandos, nombres de archivos, dispositivos de domótica, eventos de calendario, datos estructurados, etc.) y renderice una barra de acciones contextuales debajo de cada mensaje con chips interactivos para ejecutar follow-ups con un solo tap, copiar contenido específico, abrir enlaces, re-ejecutar comandos, y sugerir continuaciones inteligentes de la conversación.

**Problema que resuelve:**

Actualmente, cuando el agente responde en el chat, el usuario tiene que:

1. **Leer, interpretar, y actuar manualmente:** Si el agente sugiere un comando (`git pull`), el usuario tiene que leerlo, copiarlo manualmente, y pegarlo en otro lugar o pedirle al agente que lo ejecute. No hay forma de "ejecutar lo que acabas de decir" con un tap.

2. **Copiar fragmentos específicos con fricción:** Si la respuesta contiene un bloque de código, una URL, un número de teléfono, o un dato específico (IP, hash, nombre de archivo), el usuario tiene que seleccionar exactamente el texto correcto. No hay botones de "copiar este bloque" o "abrir esta URL" contextuales.

3. **Formular follow-ups repetitivos:** Después de muchas respuestas, el siguiente paso natural es predecible. Si el agente lista 5 opciones, el usuario suele querer elegir una. Si muestra un error, el usuario suele querer "inténtalo de nuevo" o "más detalles". Si da un resumen, el usuario suele querer "profundiza en X". Actualmente, hay que escribir todo esto manualmente.

4. **Perder el contexto de la acción:** Si el agente menciona un dispositivo de domótica ("la luz del salón está encendida"), no hay forma rápida de decir "apágala" sin escribir el mensaje completo. El contexto del mensaje anterior se pierde en la interfaz.

5. **Falta de integración con el contenido:** URLs no se pueden abrir directamente, archivos mencionados no se pueden previsualizar, comandos sugeridos no se pueden ejecutar — todo requiere pasos intermedios manuales.

6. **Interacciones voice-first incompletas:** En Talk Mode, después de escuchar una respuesta, no hay forma visual rápida de actuar sobre lo que el agente dijo sin tener que formular una nueva petición verbal.

Para Manuel, que interactúa constantemente con el agente para domótica, gestión de calendario, Twitter, email y comandos del sistema, reducir la fricción de los follow-ups multiplicaría la velocidad de interacción.

**Funcionalidades propuestas:**

**1. Motor de análisis de respuestas (ResponseAnalyzer):**

```kotlin
class ResponseAnalyzer {
    // Patrones de detección de contenido accionable
    private val detectors: List<ContentDetector> = listOf(
        UrlDetector(),
        CodeBlockDetector(),
        CommandDetector(),          // Detecta shell commands, git, docker, etc.
        FilePathDetector(),         // Detecta paths como ~/archivo.txt, /tmp/foo
        DeviceDetector(),           // "luces del salón", "cámara", dispositivos conocidos
        CalendarEventDetector(),    // "reunión a las 15:00", "mañana a las 10"
        ListOptionDetector(),       // "1. Opción A  2. Opción B  3. Opción C"
        ErrorDetector(),            // Detecta mensajes de error, excepciones, fallos
        QuestionDetector(),         // Detecta preguntas del agente al usuario
        DataDetector(),             // IPs, emails, teléfonos, hashes, JSON
        ConfirmationDetector(),     // "¿Quieres que lo haga?", "¿Procedo?"
        SuggestionDetector(),       // "Podrías hacer X", "Te recomiendo Y"
    )
    
    fun analyze(message: ChatMessage): ResponseAnalysis {
        if (message.role != "assistant") return ResponseAnalysis.empty()
        
        val text = message.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("\n")
        
        val detectedItems = mutableListOf<DetectedItem>()
        val suggestedActions = mutableListOf<SuggestedAction>()
        
        for (detector in detectors) {
            val results = detector.detect(text)
            detectedItems.addAll(results.items)
            suggestedActions.addAll(results.actions)
        }
        
        // Añadir follow-ups inteligentes basados en el tipo de respuesta
        val followUps = generateFollowUps(text, detectedItems)
        suggestedActions.addAll(followUps)
        
        // Priorizar y limitar (máximo 5 acciones visibles)
        val prioritized = prioritizeActions(suggestedActions).take(5)
        
        return ResponseAnalysis(
            detectedItems = detectedItems,
            suggestedActions = prioritized,
            responseType = classifyResponse(text, detectedItems),
        )
    }
    
    private fun generateFollowUps(
        text: String,
        items: List<DetectedItem>,
    ): List<SuggestedAction> {
        val followUps = mutableListOf<SuggestedAction>()
        
        // Si hay una lista numerada → sugerir elegir opciones
        val listItems = items.filterIsInstance<DetectedItem.NumberedList>()
        if (listItems.isNotEmpty()) {
            for (item in listItems.first().options.take(3)) {
                followUps.add(SuggestedAction.SendMessage(
                    label = item.label.take(30),
                    message = item.label,
                    icon = "📌",
                    priority = Priority.HIGH,
                ))
            }
        }
        
        // Si hay un error → sugerir "reintentar" y "más detalles"
        if (items.any { it is DetectedItem.Error }) {
            followUps.add(SuggestedAction.SendMessage(
                label = "Reintentar",
                message = "Inténtalo de nuevo",
                icon = "🔄",
                priority = Priority.HIGH,
            ))
            followUps.add(SuggestedAction.SendMessage(
                label = "Más detalles",
                message = "Dame más detalles sobre el error",
                icon = "🔍",
                priority = Priority.MEDIUM,
            ))
        }
        
        // Si hay una pregunta de confirmación → sugerir Sí/No
        if (items.any { it is DetectedItem.Confirmation }) {
            followUps.add(SuggestedAction.SendMessage(
                label = "Sí, adelante",
                message = "Sí",
                icon = "✅",
                priority = Priority.CRITICAL,
            ))
            followUps.add(SuggestedAction.SendMessage(
                label = "No",
                message = "No",
                icon = "❌",
                priority = Priority.CRITICAL,
            ))
        }
        
        // Si hay un comando sugerido → "Ejecuta esto"
        val commands = items.filterIsInstance<DetectedItem.Command>()
        if (commands.isNotEmpty()) {
            followUps.add(SuggestedAction.SendMessage(
                label = "Ejecuta",
                message = "Ejecuta: ${commands.first().command}",
                icon = "▶️",
                priority = Priority.HIGH,
            ))
        }
        
        // Si la respuesta es larga → "Resumen"
        if (text.length > 1500) {
            followUps.add(SuggestedAction.SendMessage(
                label = "Resúmelo",
                message = "Resume lo anterior en 3 puntos clave",
                icon = "📋",
                priority = Priority.LOW,
            ))
        }
        
        // Si menciona un dispositivo de domótica → acciones rápidas
        val devices = items.filterIsInstance<DetectedItem.SmartDevice>()
        for (device in devices.take(2)) {
            followUps.add(SuggestedAction.SendMessage(
                label = "${device.action} ${device.shortName}",
                message = "${device.action} ${device.name}",
                icon = device.icon,
                priority = Priority.HIGH,
            ))
        }
        
        return followUps
    }
    
    private fun classifyResponse(
        text: String,
        items: List<DetectedItem>,
    ): ResponseType {
        return when {
            items.any { it is DetectedItem.Confirmation } -> ResponseType.CONFIRMATION
            items.any { it is DetectedItem.Error } -> ResponseType.ERROR
            items.any { it is DetectedItem.NumberedList } -> ResponseType.OPTIONS
            items.any { it is DetectedItem.Command } -> ResponseType.COMMAND
            text.endsWith("?") -> ResponseType.QUESTION
            text.length > 2000 -> ResponseType.LONG_RESPONSE
            else -> ResponseType.INFORMATIONAL
        }
    }
    
    private fun prioritizeActions(actions: List<SuggestedAction>): List<SuggestedAction> {
        return actions.sortedByDescending { it.priority.ordinal }
    }
}
```

**2. Modelos de datos:**

```kotlin
data class ResponseAnalysis(
    val detectedItems: List<DetectedItem>,
    val suggestedActions: List<SuggestedAction>,
    val responseType: ResponseType,
) {
    companion object {
        fun empty() = ResponseAnalysis(emptyList(), emptyList(), ResponseType.INFORMATIONAL)
    }
}

sealed class DetectedItem {
    data class Url(val url: String, val label: String?) : DetectedItem()
    data class CodeBlock(val code: String, val language: String?) : DetectedItem()
    data class Command(val command: String, val isShell: Boolean) : DetectedItem()
    data class FilePath(val path: String) : DetectedItem()
    data class SmartDevice(
        val name: String,
        val shortName: String,
        val action: String,        // "Enciende", "Apaga", "Toggle"
        val icon: String,
    ) : DetectedItem()
    data class CalendarReference(
        val description: String,
        val dateTime: String?,
    ) : DetectedItem()
    data class NumberedList(val options: List<ListOption>) : DetectedItem()
    data class ListOption(val number: Int, val label: String)
    data class Error(val message: String, val type: String?) : DetectedItem()
    data class Confirmation(val question: String) : DetectedItem()
    data class DataItem(
        val value: String,
        val type: DataType,        // IP, EMAIL, PHONE, HASH, JSON
    ) : DetectedItem()
}

enum class DataType { IP, EMAIL, PHONE, HASH, JSON, KEY, TOKEN }

sealed class SuggestedAction {
    abstract val label: String
    abstract val icon: String
    abstract val priority: Priority
    
    // Enviar un mensaje al agente
    data class SendMessage(
        override val label: String,
        val message: String,
        override val icon: String,
        override val priority: Priority,
    ) : SuggestedAction()
    
    // Copiar contenido al portapapeles
    data class CopyContent(
        override val label: String,
        val content: String,
        override val icon: String = "📋",
        override val priority: Priority = Priority.MEDIUM,
    ) : SuggestedAction()
    
    // Abrir URL en el navegador
    data class OpenUrl(
        override val label: String,
        val url: String,
        override val icon: String = "🔗",
        override val priority: Priority = Priority.MEDIUM,
    ) : SuggestedAction()
    
    // Compartir contenido via Android Share Sheet
    data class ShareContent(
        override val label: String,
        val content: String,
        override val icon: String = "📤",
        override val priority: Priority = Priority.LOW,
    ) : SuggestedAction()
    
    // Ejecutar un Quick Command existente
    data class ExecuteQuickCommand(
        override val label: String,
        val quickCommandId: String,
        override val icon: String,
        override val priority: Priority = Priority.HIGH,
    ) : SuggestedAction()
}

enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }
enum class ResponseType { 
    INFORMATIONAL, QUESTION, CONFIRMATION, ERROR, 
    OPTIONS, COMMAND, LONG_RESPONSE 
}
```

**3. Detectores individuales (ContentDetectors):**

```kotlin
interface ContentDetector {
    fun detect(text: String): DetectionResult
}

data class DetectionResult(
    val items: List<DetectedItem> = emptyList(),
    val actions: List<SuggestedAction> = emptyList(),
)

class UrlDetector : ContentDetector {
    private val urlRegex = Regex(
        """https?://[^\s\])"'<>]+""",
        RegexOption.IGNORE_CASE,
    )
    
    override fun detect(text: String): DetectionResult {
        val urls = urlRegex.findAll(text).map { it.value }.distinct().toList()
        val items = urls.map { DetectedItem.Url(url = it, label = extractDomain(it)) }
        val actions = urls.take(2).map { url ->
            SuggestedAction.OpenUrl(
                label = extractDomain(url) ?: "Abrir enlace",
                url = url,
                priority = Priority.MEDIUM,
            )
        }
        return DetectionResult(items = items, actions = actions)
    }
    
    private fun extractDomain(url: String): String? {
        return try {
            java.net.URI(url).host?.removePrefix("www.")
        } catch (_: Throwable) { null }
    }
}

class CodeBlockDetector : ContentDetector {
    private val codeBlockRegex = Regex("""```(\w*)\n([\s\S]*?)```""")
    private val inlineCodeRegex = Regex("""`([^`]+)`""")
    
    override fun detect(text: String): DetectionResult {
        val blocks = codeBlockRegex.findAll(text).toList()
        val items = blocks.map { match ->
            DetectedItem.CodeBlock(
                code = match.groupValues[2].trim(),
                language = match.groupValues[1].ifEmpty { null },
            )
        }
        val actions = blocks.take(2).mapIndexed { idx, match ->
            val code = match.groupValues[2].trim()
            val lang = match.groupValues[1].ifEmpty { "código" }
            SuggestedAction.CopyContent(
                label = if (blocks.size == 1) "Copiar $lang" else "Copiar bloque ${idx + 1}",
                content = code,
                priority = Priority.HIGH,
            )
        }
        return DetectionResult(items = items, actions = actions)
    }
}

class CommandDetector : ContentDetector {
    private val shellPrefixes = listOf(
        "$ ", "# ", "% ", "sudo ", "ssh ", "cd ", "ls ", "cat ", "grep ",
        "git ", "docker ", "npm ", "pnpm ", "yarn ", "pip ", "curl ", "wget ",
        "mkdir ", "rm ", "cp ", "mv ", "chmod ", "chown ", "systemctl ",
        "brew ", "apt ", "adb ", "ffmpeg ",
    )
    
    override fun detect(text: String): DetectionResult {
        val commands = mutableListOf<DetectedItem.Command>()
        
        // Detectar comandos en bloques de código marcados como bash/sh/shell
        val bashBlocks = Regex("""```(?:bash|sh|shell|zsh)\n([\s\S]*?)```""")
            .findAll(text)
        
        for (match in bashBlocks) {
            val lines = match.groupValues[1].trim().lines()
            for (line in lines) {
                val trimmed = line.trim()
                    .removePrefix("$ ")
                    .removePrefix("# ")
                    .removePrefix("% ")
                    .trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    commands.add(DetectedItem.Command(command = trimmed, isShell = true))
                }
            }
        }
        
        // Detectar comandos inline en backticks
        val inlineCommands = Regex("""`([^`]+)`""").findAll(text)
        for (match in inlineCommands) {
            val candidate = match.groupValues[1].trim()
            if (shellPrefixes.any { candidate.startsWith(it) } ||
                candidate.contains("&&") || candidate.contains("|")) {
                val cleaned = candidate
                    .removePrefix("$ ")
                    .removePrefix("# ")
                    .trim()
                commands.add(DetectedItem.Command(command = cleaned, isShell = true))
            }
        }
        
        val actions = commands.take(1).map { cmd ->
            SuggestedAction.SendMessage(
                label = "Ejecutar",
                message = "Ejecuta: ${cmd.command}",
                icon = "▶️",
                priority = Priority.HIGH,
            )
        }
        
        return DetectionResult(items = commands, actions = actions)
    }
}

class ListOptionDetector : ContentDetector {
    private val numberedListRegex = Regex("""(?:^|\n)\s*(\d+)[.)]\s+(.+)""")
    
    override fun detect(text: String): DetectionResult {
        val matches = numberedListRegex.findAll(text).toList()
        if (matches.size < 2) return DetectionResult()  // No es una lista real
        
        val options = matches.map { match ->
            DetectedItem.ListOption(
                number = match.groupValues[1].toIntOrNull() ?: 0,
                label = match.groupValues[2].trim(),
            )
        }
        
        val actions = options.take(4).map { option ->
            SuggestedAction.SendMessage(
                label = "${option.number}. ${option.label.take(25)}",
                message = option.label,
                icon = "📌",
                priority = Priority.HIGH,
            )
        }
        
        return DetectionResult(
            items = listOf(DetectedItem.NumberedList(options)),
            actions = actions,
        )
    }
}

class ConfirmationDetector : ContentDetector {
    private val confirmPatterns = listOf(
        Regex("""¿(?:quieres|deseas|procedo|lo hago|sigo|continúo|te parece)""", RegexOption.IGNORE_CASE),
        Regex("""(?:shall I|should I|do you want|want me to|proceed\?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:¿(?:sí|si) o no\?)""", RegexOption.IGNORE_CASE),
    )
    
    override fun detect(text: String): DetectionResult {
        for (pattern in confirmPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                // Extraer la última oración como pregunta
                val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
                val question = sentences.lastOrNull { it.contains("?") } ?: match.value
                
                return DetectionResult(
                    items = listOf(DetectedItem.Confirmation(question = question)),
                    actions = listOf(
                        SuggestedAction.SendMessage(
                            label = "Sí, adelante",
                            message = "Sí, adelante",
                            icon = "✅",
                            priority = Priority.CRITICAL,
                        ),
                        SuggestedAction.SendMessage(
                            label = "No",
                            message = "No",
                            icon = "❌",
                            priority = Priority.CRITICAL,
                        ),
                    ),
                )
            }
        }
        return DetectionResult()
    }
}

class ErrorDetector : ContentDetector {
    private val errorPatterns = listOf(
        Regex("""(?:error|exception|failed|fallo|falló|no se pudo|unable to|cannot|permission denied)""", RegexOption.IGNORE_CASE),
        Regex("""(?:stack trace|traceback|at .+\(.+:\d+\))""", RegexOption.IGNORE_CASE),
        Regex("""(?:exit code [1-9]\d*|returned non-zero)""", RegexOption.IGNORE_CASE),
    )
    
    override fun detect(text: String): DetectionResult {
        for (pattern in errorPatterns) {
            if (pattern.containsMatchIn(text)) {
                return DetectionResult(
                    items = listOf(DetectedItem.Error(message = text.take(200), type = null)),
                    actions = listOf(
                        SuggestedAction.SendMessage(
                            label = "Reintentar",
                            message = "Inténtalo de nuevo",
                            icon = "🔄",
                            priority = Priority.HIGH,
                        ),
                    ),
                )
            }
        }
        return DetectionResult()
    }
}

class SmartDeviceDetector : ContentDetector {
    // Patrones configurable de dispositivos conocidos
    private val devicePatterns = listOf(
        DevicePattern(
            regex = Regex("""(?:luz|luces|lámpara|lámparas)\s+(?:del?\s+)?(\w+)""", RegexOption.IGNORE_CASE),
            type = "light",
            icon = "💡",
            actions = listOf("Enciende", "Apaga"),
        ),
        DevicePattern(
            regex = Regex("""(?:cámara|camera)\s+(?:del?\s+)?(\w+)""", RegexOption.IGNORE_CASE),
            type = "camera",
            icon = "📷",
            actions = listOf("Foto de"),
        ),
        DevicePattern(
            regex = Regex("""(?:persiana|cortina|blind)\s+(?:del?\s+)?(\w+)""", RegexOption.IGNORE_CASE),
            type = "blind",
            icon = "🪟",
            actions = listOf("Sube", "Baja"),
        ),
        DevicePattern(
            regex = Regex("""(?:termostato|thermostat|calefacción|heating|aire|AC)\b""", RegexOption.IGNORE_CASE),
            type = "thermostat",
            icon = "🌡️",
            actions = listOf("Ajusta"),
        ),
    )
    
    override fun detect(text: String): DetectionResult {
        val devices = mutableListOf<DetectedItem.SmartDevice>()
        
        for (pattern in devicePatterns) {
            val matches = pattern.regex.findAll(text)
            for (match in matches) {
                val location = match.groups[1]?.value ?: ""
                val fullName = match.value.trim()
                devices.add(DetectedItem.SmartDevice(
                    name = fullName,
                    shortName = if (location.isNotEmpty()) location else fullName,
                    action = pattern.actions.first(),
                    icon = pattern.icon,
                ))
            }
        }
        
        val actions = devices.take(2).flatMap { device ->
            listOf(
                SuggestedAction.SendMessage(
                    label = "${device.action} ${device.shortName}",
                    message = "${device.action} las ${device.name}",
                    icon = device.icon,
                    priority = Priority.HIGH,
                ),
            )
        }
        
        return DetectionResult(items = devices, actions = actions)
    }
    
    private data class DevicePattern(
        val regex: Regex,
        val type: String,
        val icon: String,
        val actions: List<String>,
    )
}
```

**4. Componente UI de acciones (ResponseActionsBar):**

```kotlin
@Composable
fun ResponseActionsBar(
    analysis: ResponseAnalysis,
    onAction: (SuggestedAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (analysis.suggestedActions.isEmpty()) return
    
    // Scroll horizontal de chips
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(analysis.suggestedActions) { action ->
            ActionChip(
                action = action,
                onClick = { onAction(action) },
            )
        }
    }
}

@Composable
private fun ActionChip(
    action: SuggestedAction,
    onClick: () -> Unit,
) {
    val containerColor = when (action.priority) {
        Priority.CRITICAL -> MaterialTheme.colorScheme.primaryContainer
        Priority.HIGH -> MaterialTheme.colorScheme.secondaryContainer
        Priority.MEDIUM -> MaterialTheme.colorScheme.surfaceVariant
        Priority.LOW -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when (action.priority) {
        Priority.CRITICAL -> MaterialTheme.colorScheme.onPrimaryContainer
        Priority.HIGH -> MaterialTheme.colorScheme.onSecondaryContainer
        Priority.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
        Priority.LOW -> MaterialTheme.colorScheme.onSurface
    }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = action.icon,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

**5. Integración en ChatMessageBubble:**

```kotlin
// Modificar ChatMessageBubble para incluir la barra de acciones
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    analysis: ResponseAnalysis?,   // Nuevo parámetro
    onAction: (SuggestedAction) -> Unit,  // Nuevo callback
) {
    val isUser = message.role.lowercase() == "user"
    
    Column {
        // Burbuja de mensaje existente
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(/* ... código existente ... */) {
                Box(/* ... */) {
                    ChatMessageBody(content = message.content, textColor = textColor)
                }
            }
        }
        
        // Barra de acciones contextual (solo para mensajes del asistente)
        if (!isUser && analysis != null) {
            ResponseActionsBar(
                analysis = analysis,
                onAction = onAction,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
```

**6. Integración en ChatSheetContent (flujo principal):**

```kotlin
// En ChatSheetContent o ChatMessageListCard
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    viewModel: MainViewModel,
) {
    val analyzer = remember { ResponseAnalyzer() }
    
    // Cache de análisis para evitar re-analizar en cada recomposición
    val analysisCache = remember { mutableMapOf<String, ResponseAnalysis>() }
    
    // Solo analizar el último mensaje del asistente (optimización)
    val lastAssistantIdx = messages.indexOfLast { it.role == "assistant" }
    
    LazyColumn {
        itemsIndexed(messages) { idx, message ->
            val analysis = if (idx == lastAssistantIdx && message.role == "assistant") {
                analysisCache.getOrPut(message.id) {
                    analyzer.analyze(message)
                }
            } else null
            
            ChatMessageBubble(
                message = message,
                analysis = analysis,
                onAction = { action ->
                    handleAction(action, viewModel)
                },
            )
        }
    }
}

private fun handleAction(action: SuggestedAction, viewModel: MainViewModel) {
    when (action) {
        is SuggestedAction.SendMessage -> {
            viewModel.sendChat(
                message = action.message,
                thinking = viewModel.chatThinkingLevel.value,
                attachments = emptyList(),
            )
        }
        is SuggestedAction.CopyContent -> {
            clipboardManager.setText(AnnotatedString(action.content))
            // Mostrar snackbar "Copiado"
        }
        is SuggestedAction.OpenUrl -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
            context.startActivity(intent)
        }
        is SuggestedAction.ShareContent -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, action.content)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir"))
        }
        is SuggestedAction.ExecuteQuickCommand -> {
            // Ejecutar Quick Command por ID
        }
    }
}
```

**7. Configuración de preferencias del usuario:**

```kotlin
data class ResponseActionsConfig(
    val enabled: Boolean = true,
    val showOnlyOnLastMessage: Boolean = true,  // Solo en el último mensaje
    val maxActions: Int = 5,
    val enableSmartDeviceDetection: Boolean = true,
    val enableCommandDetection: Boolean = true,
    val enableConfirmationShortcuts: Boolean = true,
    val enableListOptionChips: Boolean = true,
    val customDeviceNames: Map<String, String> = emptyMap(),  // "salón" -> "living room"
    val hapticFeedback: Boolean = true,
)
```

**8. Pantalla de ejemplo con acciones contextuales:**

```
┌─────────────────────────────────────────────────────────────┐
│                      Chat Session                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                              ┌─────────────────────────────┐│
│                              │ Enciende las luces del salón ││
│                              └─────────────────────────────┘│
│                                                             │
│ ┌──────────────────────────────────────────────────────────┐│
│ │ Hecho, he encendido las luces del salón. Ahora mismo    ││
│ │ tienes estos dispositivos activos:                       ││
│ │                                                          ││
│ │ 1. Luces del salón - Encendidas (100%)                   ││
│ │ 2. Luces del dormitorio - Apagadas                       ││
│ │ 3. Cámara del salón - Activa                             ││
│ │                                                          ││
│ │ ¿Quieres ajustar algo más?                               ││
│ └──────────────────────────────────────────────────────────┘│
│ [✅ Sí] [❌ No] [💡 Luces dormitorio] [📷 Foto salón]      │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ Otro ejemplo después de un error:                           │
│                                                             │
│ ┌──────────────────────────────────────────────────────────┐│
│ │ Error al ejecutar el comando. El servicio `nginx`        ││
│ │ falló con exit code 1:                                   ││
│ │                                                          ││
│ │ ```                                                      ││
│ │ nginx: [emerg] bind() to 0.0.0.0:80 failed              ││
│ │ (98: Address already in use)                             ││
│ │ ```                                                      ││
│ │                                                          ││
│ │ El puerto 80 ya está en uso por otro proceso.            ││
│ │ Puedes ejecutar `sudo lsof -i :80` para ver qué lo      ││
│ │ está usando.                                             ││
│ └──────────────────────────────────────────────────────────┘│
│ [🔄 Reintentar] [▶️ Ejecutar lsof] [📋 Copiar error]       │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ Y después de un resumen largo:                              │
│                                                             │
│ ┌──────────────────────────────────────────────────────────┐│
│ │ Aquí tienes el resumen de tus emails de hoy:             ││
│ │                                                          ││
│ │ 1. AWS - Factura mensual ($23.45)                        ││
│ │ 2. GitHub - PR review request de @colaborador            ││
│ │ 3. Newsletter de Hacker News                             ││
│ │ 4. Banco - Confirmación de transferencia                 ││
│ │                                                          ││
│ │ Los más importantes son el #2 (PR pendiente) y           ││
│ │ el #4 (verificar transferencia).                         ││
│ └──────────────────────────────────────────────────────────┘│
│ [📌 PR de @colaborador] [📌 Ver transferencia] [📋 Resumen] │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Arquitectura propuesta:**

```
┌─────────────────────────────────────────────────────────────┐
│                   ChatMessageBubble                          │
│    (renderiza mensaje + barra de acciones)                   │
├─────────────────────────────────────────────────────────────┤
│                  ResponseActionsBar                          │
│    (scroll horizontal de ActionChips)                        │
├─────────────────────────────────────────────────────────────┤
│                   ResponseAnalyzer                           │
│    (analiza texto → DetectedItems + SuggestedActions)        │
├─────────────────────┬───────────────────────────────────────┤
│   ContentDetectors  │        ActionHandler                   │
│  ┌───────────────┐  │   ┌──────────────────────────────┐    │
│  │ UrlDetector    │  │   │ SendMessage → ChatController │    │
│  │ CodeBlockDet.  │  │   │ CopyContent → Clipboard      │    │
│  │ CommandDet.    │  │   │ OpenUrl → Intent.ACTION_VIEW  │    │
│  │ DeviceDet.     │  │   │ ShareContent → Share Sheet    │    │
│  │ ConfirmDet.    │  │   │ QuickCmd → NodeRuntime        │    │
│  │ ErrorDet.      │  │   └──────────────────────────────┘    │
│  │ ListOptionDet. │  │                                       │
│  │ DataDet.       │  │                                       │
│  └───────────────┘  │                                       │
└─────────────────────┴───────────────────────────────────────┘
```

**Archivos nuevos:**

```
app/src/main/java/ai/openclaw/android/actions/
├── ResponseAnalyzer.kt              // Motor de análisis principal
├── ResponseAnalysis.kt              // Modelos de datos
├── DetectedItem.kt                  // Items detectados
├── SuggestedAction.kt               // Acciones sugeridas
├── ContentDetector.kt               // Interface para detectores
├── ActionHandler.kt                 // Ejecutor de acciones
├── ResponseActionsConfig.kt         // Configuración
├── detectors/
│   ├── UrlDetector.kt
│   ├── CodeBlockDetector.kt
│   ├── CommandDetector.kt
│   ├── ListOptionDetector.kt
│   ├── ConfirmationDetector.kt
│   ├── ErrorDetector.kt
│   ├── SmartDeviceDetector.kt
│   ├── DataDetector.kt
│   └── CalendarReferenceDetector.kt

app/src/main/java/ai/openclaw/android/ui/chat/
├── ResponseActionsBar.kt            // Composable de la barra de acciones
├── ActionChip.kt                    // Chip individual
```

**Archivos modificados:**

- `ChatMessageViews.kt` - Integrar ResponseActionsBar debajo de cada burbuja
- `ChatSheetContent.kt` - Pasar análisis a ChatMessageBubble, manejar callbacks de acción
- `ChatMessageListCard.kt` - Cache de análisis, optimización de re-análisis
- `SettingsSheet.kt` - Sección de configuración para Response Actions
- `SecurePrefs.kt` - Persistir ResponseActionsConfig

**Consideraciones de rendimiento:**

- **Análisis lazy:** Solo analizar el último mensaje del asistente por defecto (`showOnlyOnLastMessage = true`). Los mensajes anteriores no se analizan a menos que el usuario haga scroll hacia arriba y opte por verlos.
- **Cache:** Los resultados del análisis se cachean por `message.id` para evitar re-análisis en recomposiciones.
- **Regex precompilados:** Todos los patrones regex se compilan una sola vez (en el constructor del detector).
- **Async opcional:** Para mensajes muy largos (>5000 chars), el análisis puede moverse a `Dispatchers.Default` para no bloquear el hilo de UI.
- **Sin dependencias externas:** Todo el análisis es puro regex/string matching — sin ML, sin red, sin APIs externas. Funciona offline y es instantáneo.

**Consideraciones de UX:**

- **No intrusivo:** Los chips aparecen como una fila discreta debajo del mensaje, no como un popup ni un modal.
- **Desaparece al enviar:** Cuando el usuario envía un nuevo mensaje (ya sea por chip o escribiendo), los chips del mensaje anterior desaparecen.
- **Haptic feedback:** Vibración sutil al tocar un chip.
- **Animación suave:** Los chips aparecen con una animación `AnimatedVisibility` fade-in desde abajo.
- **Configurable:** Se puede desactivar completamente en Settings.
- **Prioridad visual:** Los chips de prioridad CRITICAL (confirmaciones Sí/No) tienen color más prominente.

**Edge cases:**

- **Mensajes vacíos o solo imágenes:** No se analizan, sin acciones.
- **Mensajes de streaming:** Las acciones solo aparecen cuando el mensaje está completo (`state = "final"`).
- **Múltiples bloques de código:** Se generan acciones de copia para los primeros 2 bloques.
- **Detección falsa de comandos:** Solo detectar en contexto de bloques de código o backticks para reducir falsos positivos.
- **Mensajes del usuario:** Nunca mostrar acciones en mensajes del usuario.
- **Internacionalización:** Los detectores incluyen patrones en español e inglés.

**Por qué es útil para Manuel:**

Como power user que:
- 🏠 **Controla domótica por chat:** "Enciende las luces" → respuesta del agente → chip rápido para apagar o controlar otro dispositivo
- 💻 **Ejecuta comandos del sistema:** Cuando el agente sugiere un comando, un tap lo ejecuta sin copiar/pegar
- ✅ **Confirma acciones frecuentemente:** "¿Quieres que lo haga?" → [Sí] [No] sin escribir
- 📋 **Copia fragmentos de código:** Bloques de código con botón de copiar contextual
- 📧 **Revisa resúmenes:** Listas de emails/tareas → tap en el ítem para profundizar
- 🔄 **Reintentar errores:** Cuando algo falla, un tap para reintentar
- ⚡ **Velocidad ante todo:** Reduce la interacción de "leer → pensar → escribir" a "leer → tap"

Las sugerencias contextuales transforman el chat de un interfaz de texto libre a un **interfaz conversacional asistido**, manteniendo la flexibilidad del texto pero eliminando la fricción de los follow-ups predecibles. Es la diferencia entre usar un terminal en modo texto y un IDE con autocompletado.

**Estimación de tiempo:**
- Modelos de datos (ResponseAnalysis, DetectedItem, SuggestedAction): 1h
- ResponseAnalyzer + lógica de priorización: 1.5h
- ContentDetectors individuales (URL, Code, Command, List, Confirm, Error, Device, Data): 4h
- ActionHandler (ejecutar acciones, clipboard, intents): 1.5h
- ResponseActionsBar + ActionChip (Composables): 2h
- Integración en ChatMessageBubble + ChatSheetContent: 2h
- Cache de análisis + optimización de rendimiento: 1h
- Configuración en Settings + SecurePrefs: 1h
- Animaciones y haptic feedback: 0.5h
- Testing unitario de detectores (regex edge cases): 2.5h
- Testing de integración UI: 1.5h
- Edge cases (streaming, mensajes largos, idiomas): 1.5h
- **Total: ~20h**

