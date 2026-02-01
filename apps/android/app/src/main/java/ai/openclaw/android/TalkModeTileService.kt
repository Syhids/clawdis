package ai.openclaw.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for toggling Talk Mode.
 *
 * This allows power users to enable/disable Talk Mode directly from the notification shade
 * without opening the app — perfect for quick voice commands to control home automation,
 * spawn sub-agents, or interact with OpenClaw hands-free.
 *
 * The tile shows:
 * - Active (green icon): Talk Mode is on, ready for voice commands
 * - Inactive (gray icon): Talk Mode is off
 * - Unavailable (dim): No gateway connected or mic permission missing
 */
class TalkModeTileService : TileService() {
  private var scope: CoroutineScope? = null

  override fun onStartListening() {
    super.onStartListening()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope?.launch {
      val app = application as? NodeApp ?: return@launch
      val runtime = app.runtime

      combine(
        runtime.talkEnabled,
        runtime.isConnected,
        runtime.talkStatusText,
      ) { enabled, connected, status ->
        Triple(enabled, connected, status)
      }.collect { (enabled, connected, status) ->
        updateTile(enabled = enabled, connected = connected, status = status)
      }
    }
  }

  override fun onStopListening() {
    scope?.cancel()
    scope = null
    super.onStopListening()
  }

  override fun onClick() {
    super.onClick()
    val app = application as? NodeApp ?: return
    val runtime = app.runtime

    val isConnected = runtime.isConnected.value
    if (!isConnected) {
      // Can't enable Talk Mode without a gateway connection
      return
    }

    val hasMicPermission = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasMicPermission) {
      // Need to open the app to request permission
      val intent = android.content.Intent(this, MainActivity::class.java).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
          android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("request_mic_permission", true)
      }
      val pendingIntent = android.app.PendingIntent.getActivity(
        this,
        0,
        intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
      )
      @Suppress("DEPRECATION")
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivityAndCollapse(pendingIntent)
      } else {
        startActivityAndCollapse(intent)
      }
      return
    }

    // Toggle Talk Mode
    val currentlyEnabled = runtime.talkEnabled.value
    runtime.setTalkEnabled(!currentlyEnabled)

    // Ensure foreground service is running when enabling
    if (!currentlyEnabled) {
      NodeForegroundService.start(this)
    }
  }

  private fun updateTile(enabled: Boolean, connected: Boolean, status: String) {
    val tile = qsTile ?: return

    val hasMicPermission = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    when {
      !connected -> {
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = "Talk Mode"
        tile.subtitle = "Not connected"
      }
      !hasMicPermission -> {
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Talk Mode"
        tile.subtitle = "Tap to grant mic"
      }
      enabled -> {
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Talk Mode"
        tile.subtitle = when {
          status.contains("Listening", ignoreCase = true) -> "Listening…"
          status.contains("Speaking", ignoreCase = true) -> "Speaking…"
          status.contains("Thinking", ignoreCase = true) -> "Thinking…"
          else -> "On"
        }
      }
      else -> {
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Talk Mode"
        tile.subtitle = "Off"
      }
    }

    // Use the app icon for the tile
    tile.icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
    tile.updateTile()
  }
}
