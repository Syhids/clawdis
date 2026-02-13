package ai.openclaw.android.intent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import ai.openclaw.android.SecurePrefs
import ai.openclaw.android.intent.models.IntentLaunchRequest
import ai.openclaw.android.intent.models.IntentLaunchResult
import ai.openclaw.android.intent.models.IntentSecurityLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class PendingIntentConfirmation(
  val intent: Intent,
  val request: IntentLaunchRequest,
  val appName: String?,
  val packageName: String?,
  val actionPreview: String,
  val deferred: CompletableDeferred<ConfirmationResult>,
)

enum class ConfirmationResult {
  ALLOW,
  ALLOW_ALWAYS,
  DENY,
}

class IntentBridgeManager(context: Context, prefs: SecurePrefs) {
  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }

  val securityPolicy = IntentSecurityPolicy(prefs)
  val auditLog = IntentAuditLog()
  val queryResolver = IntentQueryResolver(appContext)
  val resultHandler = IntentResultHandler()

  private val _pendingConfirmation = MutableStateFlow<PendingIntentConfirmation?>(null)
  val pendingConfirmation: StateFlow<PendingIntentConfirmation?> = _pendingConfirmation.asStateFlow()

  private var activityLauncher: ((Intent) -> Unit)? = null

  fun attachActivityLauncher(launcher: (Intent) -> Unit) {
    activityLauncher = launcher
  }

  fun detachActivityLauncher() {
    activityLauncher = null
  }

  suspend fun handleLaunch(paramsJson: String?): String {
    val request = parseLaunchRequest(paramsJson)
      ?: return errorResult("INVALID_REQUEST", "invalid or missing params")

    val intent = resolveIntent(request)
      ?: return errorResult("INTENT_UNRESOLVABLE", "could not build intent from params")

    val resolvedPkg = resolvePackageName(intent, request)
    val appName = resolvedPkg?.let { queryResolver.resolveAppName(it) }
    val action = intent.action ?: request.shortcut

    val level = securityPolicy.classify(
      action = intent.action,
      packageName = resolvedPkg,
      flags = request.flags,
    )

    when (level) {
      IntentSecurityLevel.BLOCKED -> {
        auditLog.record(
          IntentAuditEntry(
            timestamp = System.currentTimeMillis(),
            action = intent.action,
            uri = intent.dataString,
            packageName = resolvedPkg,
            appName = appName,
            shortcut = request.shortcut,
            launched = false,
            blocked = true,
          ),
        )
        return errorResult("INTENT_BLOCKED", "this intent is blocked by security policy")
      }
      IntentSecurityLevel.CONFIRM -> {
        val preview = buildActionPreview(request, intent)
        val deferred = CompletableDeferred<ConfirmationResult>()
        _pendingConfirmation.value = PendingIntentConfirmation(
          intent = intent,
          request = request,
          appName = appName,
          packageName = resolvedPkg,
          actionPreview = preview,
          deferred = deferred,
        )
        val result = deferred.await()
        _pendingConfirmation.value = null

        when (result) {
          ConfirmationResult.DENY -> {
            auditLog.record(
              IntentAuditEntry(
                timestamp = System.currentTimeMillis(),
                action = intent.action,
                uri = intent.dataString,
                packageName = resolvedPkg,
                appName = appName,
                shortcut = request.shortcut,
                launched = false,
                denied = true,
              ),
            )
            return errorResult("INTENT_DENIED", "user denied the intent")
          }
          ConfirmationResult.ALLOW_ALWAYS -> {
            securityPolicy.addToWhitelist(intent.action, resolvedPkg)
          }
          ConfirmationResult.ALLOW -> { /* proceed */ }
        }
      }
      IntentSecurityLevel.AUTO -> { /* proceed */ }
    }

    return launchIntent(intent, request, resolvedPkg, appName)
  }

  private fun launchIntent(
    intent: Intent,
    request: IntentLaunchRequest,
    resolvedPkg: String?,
    appName: String?,
  ): String {
    val launcher = activityLauncher
    if (launcher == null) {
      // Fallback: launch with FLAG_ACTIVITY_NEW_TASK from app context
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        appContext.startActivity(intent)
      } catch (e: Throwable) {
        auditLog.record(
          IntentAuditEntry(
            timestamp = System.currentTimeMillis(),
            action = intent.action,
            uri = intent.dataString,
            packageName = resolvedPkg,
            appName = appName,
            shortcut = request.shortcut,
            launched = false,
          ),
        )
        return errorResult("INTENT_LAUNCH_FAILED", e.message ?: "failed to launch intent")
      }
    } else {
      try {
        launcher(intent)
      } catch (e: Throwable) {
        auditLog.record(
          IntentAuditEntry(
            timestamp = System.currentTimeMillis(),
            action = intent.action,
            uri = intent.dataString,
            packageName = resolvedPkg,
            appName = appName,
            shortcut = request.shortcut,
            launched = false,
          ),
        )
        return errorResult("INTENT_LAUNCH_FAILED", e.message ?: "failed to launch intent")
      }
    }

    auditLog.record(
      IntentAuditEntry(
        timestamp = System.currentTimeMillis(),
        action = intent.action,
        uri = intent.dataString,
        packageName = resolvedPkg,
        appName = appName,
        shortcut = request.shortcut,
        launched = true,
      ),
    )

    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("launched", JsonPrimitive(true))
      if (resolvedPkg != null) put("resolvedApp", JsonPrimitive(resolvedPkg))
      if (appName != null) put("appName", JsonPrimitive(appName))
    }.toString()
  }

  fun handleQuery(paramsJson: String?): String {
    val root = parseJsonObject(paramsJson) ?: return errorResult("INVALID_REQUEST", "params required")
    val action = (root["action"] as? JsonPrimitive)?.content
    val uri = (root["uri"] as? JsonPrimitive)?.content
    val pkg = (root["package"] as? JsonPrimitive)?.content
    return queryResolver.queryCanResolve(action, uri, pkg)
  }

  fun handleApps(paramsJson: String?): String {
    val root = parseJsonObject(paramsJson)
    val filter = (root?.get("filter") as? JsonPrimitive)?.content
    return queryResolver.queryInstalledApps(filter)
  }

  fun handleShare(paramsJson: String?): String {
    val root = parseJsonObject(paramsJson) ?: return errorResult("INVALID_REQUEST", "params required")
    val text = (root["text"] as? JsonPrimitive)?.content?.trim().orEmpty()
    val title = (root["title"] as? JsonPrimitive)?.content?.trim().orEmpty()
    val pkg = (root["package"] as? JsonPrimitive)?.content?.trim()

    if (text.isEmpty()) return errorResult("INVALID_REQUEST", "text required for share")

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
      if (title.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, title)
      if (!pkg.isNullOrBlank()) setPackage(pkg)
    }

    val chooserIntent = if (pkg.isNullOrBlank()) {
      Intent.createChooser(shareIntent, title.ifEmpty { "Share" })
    } else {
      shareIntent
    }
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return try {
      val launcher = activityLauncher
      if (launcher != null) {
        launcher(chooserIntent)
      } else {
        appContext.startActivity(chooserIntent)
      }
      buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("launched", JsonPrimitive(true))
      }.toString()
    } catch (e: Throwable) {
      errorResult("SHARE_FAILED", e.message ?: "failed to share")
    }
  }

  private fun resolveIntent(request: IntentLaunchRequest): Intent? {
    if (!request.shortcut.isNullOrBlank()) {
      val shortcutIntent = IntentShortcuts.resolve(request) ?: return null
      // Handle open_app specially: resolve via PackageManager
      if (request.shortcut == "open_app") {
        val pkg = request.`package`?.trim().orEmpty()
        if (pkg.isEmpty()) return null
        return queryResolver.getLaunchIntentForPackage(pkg)
      }
      return shortcutIntent
    }

    // Raw intent from params
    val intent = Intent()
    if (!request.action.isNullOrBlank()) {
      intent.action = request.action
    }
    if (!request.uri.isNullOrBlank()) {
      intent.data = Uri.parse(request.uri)
    }
    if (!request.`package`.isNullOrBlank()) {
      intent.setPackage(request.`package`)
    }
    for ((key, value) in request.extras) {
      intent.putExtra(key, value)
    }
    for (flag in request.flags) {
      val intFlag = flagNameToInt(flag)
      if (intFlag != null) intent.addFlags(intFlag)
    }
    if (intent.action == null && intent.data == null) return null
    return intent
  }

  private fun resolvePackageName(intent: Intent, request: IntentLaunchRequest): String? {
    val explicit = intent.`package`
    if (!explicit.isNullOrBlank()) return explicit
    val resolved = intent.resolveActivity(appContext.packageManager)
    return resolved?.packageName
  }

  private fun parseLaunchRequest(paramsJson: String?): IntentLaunchRequest? {
    val raw = paramsJson?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return try {
      json.decodeFromString<IntentLaunchRequest>(raw)
    } catch (_: Throwable) {
      null
    }
  }

  private fun parseJsonObject(paramsJson: String?): JsonObject? {
    val raw = paramsJson?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return try {
      json.parseToJsonElement(raw) as? JsonObject
    } catch (_: Throwable) {
      null
    }
  }

  private fun buildActionPreview(request: IntentLaunchRequest, intent: Intent): String {
    return when (request.shortcut) {
      "alarm" -> "Set alarm${request.time?.let { " at $it" }.orEmpty()}${request.label?.let { " ($it)" }.orEmpty()}"
      "timer" -> "Set timer${request.seconds?.let { " for ${it}s" }.orEmpty()}${request.label?.let { " ($it)" }.orEmpty()}"
      "navigate" -> "Navigate to ${request.address.orEmpty()}"
      "play_music" -> "Play music: ${request.query.orEmpty()}"
      "email" -> "Send email to ${request.to.orEmpty()}"
      "settings" -> "Open settings: ${request.page ?: "main"}"
      "search_web" -> "Search: ${request.query.orEmpty()}"
      "open_app" -> "Open app: ${request.`package`.orEmpty()}"
      "call" -> "Call ${request.number.orEmpty()}"
      "sms" -> "Send SMS to ${request.number.orEmpty()}"
      else -> {
        val parts = mutableListOf<String>()
        intent.action?.let { parts.add(it) }
        intent.dataString?.let { parts.add(it) }
        parts.joinToString(" ").ifEmpty { "Launch intent" }
      }
    }
  }

  private fun errorResult(code: String, message: String): String {
    return buildJsonObject {
      put("ok", JsonPrimitive(false))
      put("launched", JsonPrimitive(false))
      put("error", JsonPrimitive("$code: $message"))
    }.toString()
  }

  private fun flagNameToInt(name: String): Int? {
    return when (name.uppercase()) {
      "FLAG_ACTIVITY_NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
      "FLAG_ACTIVITY_CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
      "FLAG_ACTIVITY_SINGLE_TOP" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
      "FLAG_GRANT_READ_URI_PERMISSION" -> Intent.FLAG_GRANT_READ_URI_PERMISSION
      else -> null
    }
  }
}
