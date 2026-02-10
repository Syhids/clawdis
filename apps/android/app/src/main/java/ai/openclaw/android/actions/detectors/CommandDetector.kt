package ai.openclaw.android.actions.detectors

import ai.openclaw.android.actions.ContentDetector
import ai.openclaw.android.actions.DetectedItem
import ai.openclaw.android.actions.DetectionResult
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.SuggestedAction

/**
 * Detects shell commands in code blocks (```bash / ```sh) and inline backtick commands.
 * Suggests an "Ejecutar" chip for the first detected command.
 */
class CommandDetector : ContentDetector {
  private val shellPrefixes = listOf(
    "sudo ", "ssh ", "cd ", "ls ", "cat ", "grep ",
    "git ", "docker ", "npm ", "pnpm ", "yarn ", "pip ", "curl ", "wget ",
    "mkdir ", "rm ", "cp ", "mv ", "chmod ", "chown ", "systemctl ",
    "brew ", "apt ", "adb ", "ffmpeg ", "python ", "node ",
  )

  private val bashBlockRegex = Regex("""```(?:bash|sh|shell|zsh)\n([\s\S]*?)```""")
  private val inlineCodeRegex = Regex("""`([^`]{3,80})`""")

  override fun detect(text: String): DetectionResult {
    val commands = mutableListOf<DetectedItem.Command>()

    // Commands inside ```bash blocks
    for (match in bashBlockRegex.findAll(text)) {
      val lines = match.groupValues[1].trim().lines()
      for (line in lines) {
        val trimmed = line.trim()
          .removePrefix("$ ")
          .removePrefix("# ")
          .removePrefix("% ")
          .trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
          commands.add(DetectedItem.Command(command = trimmed))
        }
      }
    }

    // Inline backtick commands
    for (match in inlineCodeRegex.findAll(text)) {
      val candidate = match.groupValues[1].trim()
      val looksLikeCommand = shellPrefixes.any { candidate.startsWith(it) } ||
        candidate.contains("&&") ||
        candidate.contains(" | ")
      if (looksLikeCommand) {
        val cleaned = candidate
          .removePrefix("$ ")
          .removePrefix("# ")
          .trim()
        if (commands.none { it.command == cleaned }) {
          commands.add(DetectedItem.Command(command = cleaned))
        }
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
