package ai.openclaw.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TalkOrbOverlay(
  seamColor: Color,
  statusText: String,
  isListening: Boolean,
  isSpeaking: Boolean,
  rmsLevel: Float = 0f,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "talk-orb")
  val t by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1500, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "pulse",
    )

  // Animate the RMS level with a spring for smooth transitions
  val animatedRms by animateFloatAsState(
    targetValue = if (isListening) rmsLevel else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow,
    ),
    label = "rms",
  )

  val trimmed = statusText.trim()
  val showStatus = trimmed.isNotEmpty() && trimmed != "Off"
  val phase =
    when {
      isSpeaking -> "Speaking"
      isListening -> "Listening"
      else -> "Thinking"
    }

  Column(
    modifier = modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.size(360.dp)) {
        val center = this.center
        val baseRadius = size.minDimension * 0.30f

        // RMS-reactive ring (innermost, visible when listening and detecting audio)
        if (isListening && animatedRms > 0.05f) {
          val rmsRingScale = 1.0f + (animatedRms * 0.15f)
          val rmsAlpha = (animatedRms * 0.6f).coerceIn(0.1f, 0.5f)
          val strokeWidth = (2f + animatedRms * 4f).dp
          drawCircle(
            color = seamColor.copy(alpha = rmsAlpha),
            radius = baseRadius * rmsRingScale,
            center = center,
            style = Stroke(width = strokeWidth.toPx()),
          )
        }

        val ring1 = 1.05f + (t * 0.25f)
        val ring2 = 1.20f + (t * 0.55f)
        val ringAlpha1 = (1f - t) * 0.34f
        val ringAlpha2 = (1f - t) * 0.22f

        drawCircle(
          color = seamColor.copy(alpha = ringAlpha1),
          radius = baseRadius * ring1,
          center = center,
          style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
          color = seamColor.copy(alpha = ringAlpha2),
          radius = baseRadius * ring2,
          center = center,
          style = Stroke(width = 3.dp.toPx()),
        )

        // Main orb with RMS-reactive size when listening
        val orbScale = if (isListening) 1.0f + (animatedRms * 0.08f) else 1.0f
        drawCircle(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  seamColor.copy(alpha = 0.92f),
                  seamColor.copy(alpha = 0.40f),
                  Color.Black.copy(alpha = 0.56f),
                ),
              center = center,
              radius = baseRadius * orbScale * 1.35f,
            ),
          radius = baseRadius * orbScale,
          center = center,
        )

        drawCircle(
          color = seamColor.copy(alpha = 0.34f),
          radius = baseRadius * orbScale,
          center = center,
          style = Stroke(width = 1.dp.toPx()),
        )
      }
    }

    if (showStatus) {
      Surface(
        color = Color.Black.copy(alpha = 0.40f),
        shape = CircleShape,
      ) {
        Text(
          text = trimmed,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          color = Color.White.copy(alpha = 0.92f),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
    } else {
      Text(
        text = phase,
        color = Color.White.copy(alpha = 0.80f),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
