package ai.openclaw.android.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.openclaw.android.actions.Priority
import ai.openclaw.android.actions.ResponseAnalysis
import ai.openclaw.android.actions.SuggestedAction

/**
 * Horizontal scrollable row of action chips rendered below an assistant message.
 */
@Composable
fun ResponseActionsBar(
  analysis: ResponseAnalysis,
  onAction: (SuggestedAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (analysis.suggestedActions.isEmpty()) return

  AnimatedVisibility(
    visible = true,
    enter = fadeIn() + slideInVertically { it / 2 },
  ) {
    LazyRow(
      modifier = modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
      items(
        items = analysis.suggestedActions,
        key = { it.label },
      ) { action ->
        ActionChip(
          action = action,
          onClick = { onAction(action) },
        )
      }
    }
  }
}

@Composable
private fun ActionChip(
  action: SuggestedAction,
  onClick: () -> Unit,
) {
  val containerColor = when (action.priority) {
    Priority.CRITICAL -> MaterialTheme.colorScheme.primaryContainer
    Priority.HIGH -> MaterialTheme.colorScheme.secondaryContainer
    Priority.MEDIUM -> MaterialTheme.colorScheme.surfaceVariant
    Priority.LOW -> MaterialTheme.colorScheme.surface
  }
  val contentColor = when (action.priority) {
    Priority.CRITICAL -> MaterialTheme.colorScheme.onPrimaryContainer
    Priority.HIGH -> MaterialTheme.colorScheme.onSecondaryContainer
    Priority.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
    Priority.LOW -> MaterialTheme.colorScheme.onSurface
  }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = containerColor,
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = action.icon,
        style = MaterialTheme.typography.labelSmall,
      )
      Text(
        text = action.label,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
