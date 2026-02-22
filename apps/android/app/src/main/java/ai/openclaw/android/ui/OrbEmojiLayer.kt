package ai.openclaw.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * Renders a dynamic emoji centered inside the talk orb. The emoji changes based on agent state
 * and has organic floating/wobble/pulse animations.
 */
@Composable
fun OrbEmojiLayer(
  isListening: Boolean,
  isSpeaking: Boolean,
  hasPendingToolCalls: Boolean,
  statusText: String,
  modifier: Modifier = Modifier,
) {
  val emoji = resolveEmoji(isListening, isSpeaking, hasPendingToolCalls, statusText)

  val infiniteTransition = rememberInfiniteTransition(label = "emoji-anim")

  // Floating: sinusoidal up/down ±6dp over ~3s
  val floatPhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2 * Math.PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "float",
    )
  val floatOffsetDp = sin(floatPhase.toDouble()).toFloat() * 6f // ±6dp

  // Scale pulse: 0.95–1.05 over ~2s
  val scalePulse by
    infiniteTransition.animateFloat(
      initialValue = 0.95f,
      targetValue = 1.05f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "scale-pulse",
    )

  // Wobble rotation: triggered on emoji change, animates ±5° then settles
  val wobbleRotation = remember { Animatable(0f) }
  LaunchedEffect(emoji) {
    wobbleRotation.snapTo(-5f)
    wobbleRotation.animateTo(
      targetValue = 0f,
      animationSpec = tween(durationMillis = 400),
    )
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    AnimatedContent(
      targetState = emoji,
      transitionSpec = {
        scaleIn(animationSpec = tween(150)) togetherWith scaleOut(animationSpec = tween(150))
      },
      label = "emoji-crossfade",
    ) { currentEmoji ->
      Text(
        text = currentEmoji,
        fontSize = 48.sp,
        modifier =
          Modifier.graphicsLayer {
            translationY = floatOffsetDp * density
            scaleX = scalePulse
            scaleY = scalePulse
            rotationZ = wobbleRotation.value
          },
      )
    }
  }
}

/** Picks the emoji for the current agent state (priority order). */
private fun resolveEmoji(
  isListening: Boolean,
  isSpeaking: Boolean,
  hasPendingToolCalls: Boolean,
  statusText: String,
): String {
  val trimmed = statusText.trim()
  val isActive = trimmed.isNotEmpty() && trimmed != "Off"
  return when {
    isListening -> "🎤"
    isSpeaking -> "💬"
    hasPendingToolCalls -> "⚡"
    isActive -> "💭"
    else -> "🎩"
  }
}
