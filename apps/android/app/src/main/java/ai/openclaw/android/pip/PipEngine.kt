package ai.openclaw.android.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import ai.openclaw.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class PipEngine(private val activity: Activity) {

  companion object {
    private const val ACTION_TOGGLE_TALK = "ai.openclaw.android.pip.ACTION_TOGGLE_TALK"
    private const val ACTION_ABORT = "ai.openclaw.android.pip.ACTION_ABORT"
    private const val ACTION_EXPAND = "ai.openclaw.android.pip.ACTION_EXPAND"
    private const val REQUEST_TOGGLE_TALK = 100
    private const val REQUEST_ABORT = 101
    private const val REQUEST_EXPAND = 102
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _isInPipMode = MutableStateFlow(false)
  val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

  private val _resolvedContent = MutableStateFlow(ResolvedPipContent.STATUS)
  val resolvedContent: StateFlow<ResolvedPipContent> = _resolvedContent.asStateFlow()

  // External state bindings — set by the host (MainActivity)
  var talkEnabled: StateFlow<Boolean> = MutableStateFlow(false)
  var talkIsListening: StateFlow<Boolean> = MutableStateFlow(false)
  var talkIsSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
  var chatStreamingText: StateFlow<String?> = MutableStateFlow(null)
  var pendingRunCount: StateFlow<Int> = MutableStateFlow(0)
  var isConnected: StateFlow<Boolean> = MutableStateFlow(false)
  var pipEnabled: StateFlow<Boolean> = MutableStateFlow(false)
  var pipAutoEnter: StateFlow<Boolean> = MutableStateFlow(true)
  var pipContentMode: StateFlow<PipContentMode> = MutableStateFlow(PipContentMode.AUTO)

  var onToggleTalk: (() -> Unit)? = null
  var onAbort: (() -> Unit)? = null

  private var updateJob: Job? = null
  private var receiverRegistered = false

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        ACTION_TOGGLE_TALK -> onToggleTalk?.invoke()
        ACTION_ABORT -> onAbort?.invoke()
        ACTION_EXPAND -> expandToFullApp()
      }
    }
  }

  fun initialize() {
    registerReceiver()
    startReactiveUpdates()
  }

  fun destroy() {
    updateJob?.cancel()
    unregisterReceiver()
  }

  fun onUserLeaveHint() {
    if (!pipEnabled.value) return
    if (!pipAutoEnter.value) return
    enterPipMode()
  }

  fun onPictureInPictureModeChanged(isInPip: Boolean) {
    _isInPipMode.value = isInPip
    if (isInPip) {
      resolveContent()
    }
  }

  fun enterPipMode() {
    if (!pipEnabled.value) return
    try {
      val params = buildPipParams()
      activity.enterPictureInPictureMode(params)
    } catch (_: Throwable) {
      // PiP may not be available on all devices/configurations.
    }
  }

  private fun registerReceiver() {
    if (receiverRegistered) return
    val filter = IntentFilter().apply {
      addAction(ACTION_TOGGLE_TALK)
      addAction(ACTION_ABORT)
      addAction(ACTION_EXPAND)
    }
    activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    receiverRegistered = true
  }

  private fun unregisterReceiver() {
    if (!receiverRegistered) return
    try {
      activity.unregisterReceiver(receiver)
    } catch (_: Throwable) {}
    receiverRegistered = false
  }

  private fun startReactiveUpdates() {
    updateJob?.cancel()
    updateJob = scope.launch {
      combine(
        talkEnabled,
        chatStreamingText,
        pendingRunCount,
        isConnected,
        pipContentMode,
        pipEnabled,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        values
      }.distinctUntilChanged()
        .collect {
          resolveContent()
          if (_isInPipMode.value) {
            updatePipParams()
          }
        }
    }
  }

  private fun resolveContent() {
    val mode = pipContentMode.value
    _resolvedContent.value = when (mode) {
      PipContentMode.TALK_ORB -> ResolvedPipContent.TALK_ORB
      PipContentMode.CHAT_STREAM -> ResolvedPipContent.CHAT_STREAM
      PipContentMode.STATUS_ONLY -> ResolvedPipContent.STATUS
      PipContentMode.AUTO -> {
        when {
          talkEnabled.value -> ResolvedPipContent.TALK_ORB
          chatStreamingText.value != null || pendingRunCount.value > 0 -> ResolvedPipContent.CHAT_STREAM
          else -> ResolvedPipContent.STATUS
        }
      }
    }
  }

  private fun buildPipParams(): PictureInPictureParams {
    val content = _resolvedContent.value
    val aspectRatio = when (content) {
      ResolvedPipContent.TALK_ORB -> Rational(1, 1)
      ResolvedPipContent.CHAT_STREAM -> Rational(9, 16)
      ResolvedPipContent.STATUS -> Rational(16, 9)
    }

    val builder = PictureInPictureParams.Builder()
      .setAspectRatio(aspectRatio)
      .setActions(buildRemoteActions())

    if (Build.VERSION.SDK_INT >= 31) {
      builder.setAutoEnterEnabled(pipEnabled.value && pipAutoEnter.value)
      builder.setSeamlessResizeEnabled(true)
    }

    return builder.build()
  }

  private fun updatePipParams() {
    try {
      activity.setPictureInPictureParams(buildPipParams())
    } catch (_: Throwable) {
      // Ignore — activity may not be in a valid state.
    }
  }

  private fun buildRemoteActions(): List<RemoteAction> {
    val actions = mutableListOf<RemoteAction>()

    // 1. Toggle Talk Mode
    val isTalking = talkEnabled.value
    val micIcon = if (isTalking) R.drawable.ic_pip_mic else R.drawable.ic_pip_mic_off
    val micTitle = if (isTalking) "Mic On" else "Mic Off"
    val micDesc = if (isTalking) "Disable Talk Mode" else "Enable Talk Mode"
    actions.add(
      RemoteAction(
        Icon.createWithResource(activity, micIcon),
        micTitle,
        micDesc,
        PendingIntent.getBroadcast(
          activity,
          REQUEST_TOGGLE_TALK,
          Intent(ACTION_TOGGLE_TALK).setPackage(activity.packageName),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
      ),
    )

    // 2. Abort current run
    val hasActivity = pendingRunCount.value > 0 || chatStreamingText.value != null
    val stopAction = RemoteAction(
      Icon.createWithResource(activity, R.drawable.ic_pip_stop),
      "Stop",
      "Abort current run",
      PendingIntent.getBroadcast(
        activity,
        REQUEST_ABORT,
        Intent(ACTION_ABORT).setPackage(activity.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      ),
    )
    stopAction.isEnabled = hasActivity
    actions.add(stopAction)

    // 3. Expand to full app
    actions.add(
      RemoteAction(
        Icon.createWithResource(activity, R.drawable.ic_pip_expand),
        "Expand",
        "Return to full app",
        PendingIntent.getBroadcast(
          activity,
          REQUEST_EXPAND,
          Intent(ACTION_EXPAND).setPackage(activity.packageName),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
      ),
    )

    return actions
  }

  private fun expandToFullApp() {
    val intent = Intent(activity, activity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    activity.startActivity(intent)
  }
}

enum class ResolvedPipContent {
  TALK_ORB,
  CHAT_STREAM,
  STATUS,
}
