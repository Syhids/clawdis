package ai.openclaw.android.ui.pip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PipStatusContent(
  isConnected: Boolean,
  statusText: String,
  talkEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxSize()
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Connection dot
    val dotColor = if (isConnected) Color(0xFF2ECC71) else Color(0xFF9E9E9E)
    Surface(
      modifier = Modifier.size(10.dp),
      shape = CircleShape,
      color = dotColor,
    ) {}

    // Status text
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = if (isConnected) "Connected" else "Offline",
        color = Color.White.copy(alpha = 0.90f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
      if (!isConnected && statusText.isNotBlank() && statusText != "Offline") {
        Text(
          text = statusText,
          color = Color.White.copy(alpha = 0.55f),
          fontSize = 10.sp,
          maxLines = 1,
        )
      }
    }

    // Mic icon
    Icon(
      imageVector = if (talkEnabled) Icons.Default.Mic else Icons.Default.MicOff,
      contentDescription = if (talkEnabled) "Mic on" else "Mic off",
      tint = if (talkEnabled) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.40f),
      modifier = Modifier.size(18.dp),
    )
  }
}
