package ai.openclaw.android.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val nightTextColor = Color.White.copy(alpha = 0.5f)
private val dayTextColor = Color.White.copy(alpha = 0.9f)
private val cardBackground = Color.White.copy(alpha = 0.08f)
private val cardBackgroundNight = Color.White.copy(alpha = 0.04f)
private val subtleTextColor = Color.White.copy(alpha = 0.6f)

@Composable
internal fun CompanionDisplayContent(state: CompanionState) {
  val textColor = if (state.isNightMode) nightTextColor else dayTextColor
  val clockAlpha = if (state.isNightMode) 0.5f else 1f

  // Anti burn-in: shift content every 60s
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    while (true) {
      delay(60_000)
      offsetX = (Math.random().toFloat() - 0.5f) * 40f
      offsetY = (Math.random().toFloat() - 0.5f) * 40f
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
        .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Clock
      Text(
        text = state.currentTime,
        fontSize = 72.sp,
        fontWeight = FontWeight.Thin,
        color = textColor,
        modifier = Modifier.alpha(clockAlpha),
      )
      Text(
        text = state.currentDate,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        color = textColor.copy(alpha = textColor.alpha * 0.7f),
      )

      Spacer(modifier = Modifier.height(48.dp))

      // Dashboard cards — hidden at night to reduce burn-in
      AnimatedVisibility(
        visible = !state.isNightMode,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top,
        ) {
          // Status card
          DashboardCard(
            modifier = Modifier.weight(1f),
            isNight = state.isNightMode,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (state.gatewayConnected) Icons.Default.Cloud else Icons.Default.CloudOff,
                contentDescription = if (state.gatewayConnected) "Connected" else "Disconnected",
                tint = if (state.gatewayConnected) Color(0xFF4CAF50) else Color(0xFFFF5722),
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (state.gatewayConnected) "Connected" else "Disconnected",
                color = dayTextColor,
                fontSize = 14.sp,
              )
            }
            if (state.serverName != null) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = state.serverName,
                color = subtleTextColor,
                fontSize = 12.sp,
              )
            }
          }

          // Agent card
          DashboardCard(
            modifier = Modifier.weight(1f),
            isNight = state.isNightMode,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "Agent",
                tint = subtleTextColor,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Last message",
                color = dayTextColor,
                fontSize = 14.sp,
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = state.lastAgentMessage ?: "No messages yet",
              color = if (state.lastAgentMessage != null) subtleTextColor else subtleTextColor.copy(alpha = 0.4f),
              fontSize = 12.sp,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(48.dp))

      // Voice indicator
      VoiceIndicator(state = state)
    }
  }
}

@Composable
private fun DashboardCard(
  modifier: Modifier = Modifier,
  isNight: Boolean,
  content: @Composable () -> Unit,
) {
  val bg = if (isNight) cardBackgroundNight else cardBackground
  Column(
    modifier = modifier
      .widthIn(max = 280.dp)
      .background(bg, RoundedCornerShape(16.dp))
      .padding(16.dp),
  ) {
    content()
  }
}

@Composable
private fun VoiceIndicator(state: CompanionState) {
  val transition = rememberInfiniteTransition(label = "voicePulse")
  val pulseAlpha by transition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulseAlpha",
  )

  val isActive = state.voiceWakeListening || state.talkListening
  val statusLabel = when {
    state.talkSpeaking -> "Speaking…"
    state.talkListening -> "Listening…"
    state.talkEnabled -> state.voiceWakeStatus
    state.voiceWakeListening -> "Say a wake word…"
    else -> state.voiceWakeStatus
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier
      .background(cardBackground, RoundedCornerShape(24.dp))
      .padding(horizontal = 20.dp, vertical = 12.dp),
  ) {
    Icon(
      imageVector = Icons.Default.RecordVoiceOver,
      contentDescription = "Voice",
      tint = if (isActive) Color(0xFF4FC3F7) else subtleTextColor,
      modifier = Modifier
        .size(20.dp)
        .alpha(if (isActive) pulseAlpha else 0.6f),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Text(
      text = statusLabel,
      color = if (isActive) Color(0xFF4FC3F7) else subtleTextColor,
      fontSize = 14.sp,
      textAlign = TextAlign.Center,
    )
  }
}
