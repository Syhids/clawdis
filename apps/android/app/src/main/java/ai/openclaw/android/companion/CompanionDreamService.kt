package ai.openclaw.android.companion

import android.service.dreams.DreamService
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import ai.openclaw.android.NodeApp
import ai.openclaw.android.VoiceWakeMode
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CompanionDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateRegistryController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle get() = lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateRegistryController.savedStateRegistry

  private var previousVoiceWakeMode: VoiceWakeMode = VoiceWakeMode.Off
  private var didOverrideVoiceWake = false

  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
  private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

  @Suppress("DEPRECATION")
  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isInteractive = true
    isFullscreen = true
    isScreenBright = !NightModeHelper.isNightMode()

    savedStateRegistryController.performRestore(null)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

    val runtime = (application as NodeApp).runtime

    // Force voice wake to Always while companion is active
    previousVoiceWakeMode = runtime.voiceWakeMode.value
    if (previousVoiceWakeMode != VoiceWakeMode.Always) {
      runtime.setVoiceWakeMode(VoiceWakeMode.Always)
      didOverrideVoiceWake = true
    }

    val composeView = ComposeView(this).apply {
      setContent {
        val connected by runtime.isConnected.collectAsState()
        val serverName by runtime.serverName.collectAsState()
        val voiceListening by runtime.voiceWakeIsListening.collectAsState()
        val voiceStatus by runtime.voiceWakeStatusText.collectAsState()
        val talkEnabled by runtime.talkEnabled.collectAsState()
        val talkListening by runtime.talkIsListening.collectAsState()
        val talkSpeaking by runtime.talkIsSpeaking.collectAsState()
        val lastAgentMessage by runtime.lastAgentMessage.collectAsState()

        var timeText by remember { mutableStateOf(formatTime()) }
        var dateText by remember { mutableStateOf(formatDate()) }
        var isNight by remember { mutableStateOf(NightModeHelper.isNightMode()) }

        LaunchedEffect(Unit) {
          while (true) {
            delay(1_000)
            timeText = formatTime()
            dateText = formatDate()
            val nightNow = NightModeHelper.isNightMode()
            if (nightNow != isNight) {
              isNight = nightNow
              isScreenBright = !nightNow
            }
          }
        }

        CompanionDisplayContent(
          state = CompanionState(
            currentTime = timeText,
            currentDate = dateText,
            isNightMode = isNight,
            gatewayConnected = connected,
            serverName = serverName,
            lastAgentMessage = lastAgentMessage,
            voiceWakeListening = voiceListening,
            voiceWakeStatus = voiceStatus,
            talkEnabled = talkEnabled,
            talkListening = talkListening,
            talkSpeaking = talkSpeaking,
          ),
        )
      }
    }

    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)

    setContentView(composeView)

    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  @Suppress("DEPRECATION")
  override fun onDreamingStarted() {
    super.onDreamingStarted()
    window.decorView.systemUiVisibility =
      View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  }

  override fun onDetachedFromWindow() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

    // Restore previous voice wake mode
    if (didOverrideVoiceWake) {
      val runtime = (application as NodeApp).runtime
      runtime.setVoiceWakeMode(previousVoiceWakeMode)
      didOverrideVoiceWake = false
    }

    super.onDetachedFromWindow()
  }

  private fun formatTime(): String {
    return LocalDateTime.now().format(timeFormatter)
  }

  private fun formatDate(): String {
    return LocalDateTime.now().format(dateFormatter)
  }
}
