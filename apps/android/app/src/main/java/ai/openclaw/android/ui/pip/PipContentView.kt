package ai.openclaw.android.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ai.openclaw.android.MainViewModel
import ai.openclaw.android.pip.ResolvedPipContent

@Composable
fun PipContentView(
  viewModel: MainViewModel,
  resolvedContent: ResolvedPipContent,
  modifier: Modifier = Modifier,
) {
  val isConnected by viewModel.isConnected.collectAsState()
  val statusText by viewModel.statusText.collectAsState()
  val talkEnabled by viewModel.talkEnabled.collectAsState()
  val talkIsListening by viewModel.talkIsListening.collectAsState()
  val talkIsSpeaking by viewModel.talkIsSpeaking.collectAsState()
  val talkStatusText by viewModel.talkStatusText.collectAsState()
  val seamColorArgb by viewModel.seamColorArgb.collectAsState()
  val seamColor = Color(seamColorArgb)
  val streamingText by viewModel.chatStreamingAssistantText.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val pendingRunCount by viewModel.pendingRunCount.collectAsState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black),
    contentAlignment = Alignment.Center,
  ) {
    when (resolvedContent) {
      ResolvedPipContent.TALK_ORB -> PipTalkOrbContent(
        seamColor = seamColor,
        statusText = talkStatusText,
        isListening = talkIsListening,
        isSpeaking = talkIsSpeaking,
      )
      ResolvedPipContent.CHAT_STREAM -> PipChatStreamContent(
        streamingText = streamingText,
        pendingToolCalls = pendingToolCalls,
        pendingRunCount = pendingRunCount,
      )
      ResolvedPipContent.STATUS -> PipStatusContent(
        isConnected = isConnected,
        statusText = statusText,
        talkEnabled = talkEnabled,
      )
    }
  }
}
