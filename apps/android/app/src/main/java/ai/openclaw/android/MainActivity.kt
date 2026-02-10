package ai.openclaw.android

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ai.openclaw.android.pip.PipEngine
import ai.openclaw.android.ui.RootScreen
import ai.openclaw.android.ui.OpenClawTheme
import ai.openclaw.android.ui.pip.PipContentView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()
  private lateinit var permissionRequester: PermissionRequester
  private lateinit var screenCaptureRequester: ScreenCaptureRequester
  private lateinit var pipEngine: PipEngine

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    WebView.setWebContentsDebuggingEnabled(isDebuggable)
    applyImmersiveMode()
    requestDiscoveryPermissionsIfNeeded()
    requestNotificationPermissionIfNeeded()
    NodeForegroundService.start(this)
    permissionRequester = PermissionRequester(this)
    screenCaptureRequester = ScreenCaptureRequester(this)
    viewModel.camera.attachLifecycleOwner(this)
    viewModel.camera.attachPermissionRequester(permissionRequester)
    viewModel.sms.attachPermissionRequester(permissionRequester)
    viewModel.screenRecorder.attachScreenCaptureRequester(screenCaptureRequester)
    viewModel.screenRecorder.attachPermissionRequester(permissionRequester)

    // Initialize PiP engine
    pipEngine = PipEngine(this).apply {
      talkEnabled = viewModel.talkEnabled
      talkIsListening = viewModel.talkIsListening
      talkIsSpeaking = viewModel.talkIsSpeaking
      chatStreamingText = viewModel.chatStreamingAssistantText
      pendingRunCount = viewModel.pendingRunCount
      isConnected = viewModel.isConnected
      pipEnabled = viewModel.pipEnabled
      pipAutoEnter = viewModel.pipAutoEnter
      pipContentMode = viewModel.pipContentMode
      onToggleTalk = { viewModel.setTalkEnabled(!viewModel.talkEnabled.value) }
      onAbort = { viewModel.abortChat() }
    }
    pipEngine.initialize()

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.preventSleep.collect { enabled ->
          if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          }
        }
      }
    }

    setContent {
      OpenClawTheme {
        val isInPipMode by pipEngine.isInPipMode.collectAsState()
        val resolvedContent by pipEngine.resolvedContent.collectAsState()

        Surface(modifier = Modifier) {
          if (isInPipMode) {
            PipContentView(
              viewModel = viewModel,
              resolvedContent = resolvedContent,
            )
          } else {
            RootScreen(viewModel = viewModel)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    applyImmersiveMode()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      applyImmersiveMode()
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.setForeground(true)
  }

  override fun onStop() {
    viewModel.setForeground(false)
    super.onStop()
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    pipEngine.onUserLeaveHint()
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    pipEngine.onPictureInPictureModeChanged(isInPictureInPictureMode)
  }

  override fun onDestroy() {
    pipEngine.destroy()
    super.onDestroy()
  }

  private fun applyImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
  }

  private fun requestDiscoveryPermissionsIfNeeded() {
    if (Build.VERSION.SDK_INT >= 33) {
      val ok =
        ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.NEARBY_WIFI_DEVICES,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!ok) {
        requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 100)
      }
    } else {
      val ok =
        ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!ok) {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
      }
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < 33) return
    val ok =
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!ok) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
    }
  }
}
